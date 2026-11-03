package dev.origin.ote.root

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Host process fixtures only: UID/SU are mocked, not proof of Android root. */
class RootManagerTest {
    private class Fixture : AutoCloseable {
        val directory: Path = Files.createTempDirectory("ote-root-fixture-")
        val uid: Path = directory.resolve("uid-state")
        val identity: Path = directory.resolve("uid helper's file")
        val starts = AtomicInteger()
        val manager: RootManager

        init {
            Files.writeString(uid, "0\n")
            Files.writeString(identity, "#!/bin/sh\ncat ${RootManager.quote(uid.toString())}\n")
            check(identity.toFile().setExecutable(true))
            manager = RootManager(
                starter = ProcessStarter { arguments ->
                    starts.incrementAndGet()
                    check(arguments.size == 3 && arguments[1] == "-c")
                    ProcessBuilder("/bin/sh", "-c", arguments[2]).start()
                },
                identityBinary = identity.toString(),
                outputLimitBytes = 4096,
            )
        }

        override fun close() {
            // The test owns this freshly-created directory, never device files.
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun constructionDoesNotRequestSu() {
        Fixture().use { fixture ->
            assertEquals(0, fixture.starts.get())
            assertFalse(fixture.manager.state.value.checked)
            assertFalse(fixture.manager.state.value.available)
        }
    }

    @Test fun successRequiresSameInvocationUidProofAndRemovesInternalMarker() = runBlocking {
        Fixture().use { fixture ->
            val result = fixture.manager.execute("printf '%s' 'hello'")
            assertEquals(0, result.exitCode)
            assertEquals("hello", result.stdout)
            assertEquals("", result.stderr)
            assertTrue(result.succeeded)
            assertTrue(fixture.manager.state.value.available)
            assertEquals("uid_0_verified", fixture.manager.state.value.detail)
        }
    }

    @Test fun nonRootIdentityStopsBeforePayload() = runBlocking {
        Fixture().use { fixture ->
            Files.writeString(fixture.uid, "2000\n")
            val payload = fixture.directory.resolve("must-not-exist")
            val result = fixture.manager.execute("printf 'bad' > ${RootManager.quote(payload.toString())}")
            assertFalse(result.succeeded)
            assertFalse(Files.exists(payload))
            assertFalse(fixture.manager.state.value.available)
            assertEquals("uid_0_not_verified", fixture.manager.state.value.detail)
        }
    }

    @Test fun lossOfTemporaryRootIsNotHiddenByPreviousSuccess() = runBlocking {
        Fixture().use { fixture ->
            assertTrue(fixture.manager.execute(":").succeeded)
            Files.writeString(fixture.uid, "2000\n")
            assertFalse(fixture.manager.execute(":").succeeded)
            assertFalse(fixture.manager.state.value.available)
            Files.writeString(fixture.uid, "0\n")
            assertTrue(fixture.manager.execute(":").succeeded)
            assertTrue(fixture.manager.state.value.available)
            assertEquals(3, fixture.starts.get())
        }
    }

    @Test fun failureOfPayloadDoesNotPretendRootWasDenied() = runBlocking {
        Fixture().use { fixture ->
            val result = fixture.manager.execute("exit 17")
            assertEquals(17, result.exitCode)
            assertFalse(result.succeeded)
            assertTrue(fixture.manager.state.value.available)
        }
    }

    @Test fun stdoutAndStderrAreDrainedBeyondCaptureLimitWithoutDeadlock() = runBlocking {
        withTimeout(10_000) {
            Fixture().use { fixture ->
                val result = fixture.manager.execute(
                    "head -c 200000 /dev/zero | tr '\\000' O\n" +
                        "head -c 200000 /dev/zero | tr '\\000' E >&2",
                )
                assertTrue(result.succeeded)
                assertTrue(result.stdout.contains("[ote_output_truncated]"))
                assertTrue(result.stderr.contains("[ote_output_truncated]"))
                assertTrue(result.stdout.length < 4200)
                assertTrue(result.stderr.length < 4200)
            }
        }
    }

    @Test fun timeoutReturnsUncertainStatusWithoutWaitingForInheritedPipeEof() = runBlocking {
        withTimeout(3_000) {
            Fixture().use { fixture ->
                val result = fixture.manager.execute("sleep 5", timeoutMs = 100)
                assertTrue(result.timedOut)
                assertEquals(124, result.exitCode)
                assertTrue(result.stderr.contains("operation_state_unknown"))
                assertFalse(fixture.manager.state.value.available)
            }
        }
    }

    @Test fun coroutineCancellationInvalidatesStateAndAllowsNextExplicitCheck() = runBlocking {
        withTimeout(3_000) {
            Fixture().use { fixture ->
                val job = async { fixture.manager.execute("sleep 5") }
                delay(100)
                job.cancelAndJoin()
                assertEquals("cancelled_operation_state_unknown", fixture.manager.state.value.detail)
                assertFalse(fixture.manager.state.value.available)
                assertTrue(fixture.manager.execute(":").succeeded)
            }
        }
    }

    @Test fun missingSuClientIsARealFailure() = runBlocking {
        val manager = RootManager(starter = ProcessStarter { throw IOException("fixture unavailable") })
        val result = manager.execute(":")
        assertEquals(127, result.exitCode)
        assertFalse(result.succeeded)
        assertEquals("su_start_failed", manager.state.value.detail)
        assertFalse(result.stderr.contains("fixture unavailable"))
    }

    @Test fun exitZeroWithoutAttestationNeverMeansRootSuccess() = runBlocking {
        val manager = RootManager(starter = ProcessStarter {
            ProcessBuilder("/bin/sh", "-c", "printf '0\\n'; exit 0").start()
        })
        val result = manager.execute(":")
        assertEquals(126, result.exitCode)
        assertFalse(result.succeeded)
        assertFalse(manager.state.value.available)
    }

    // assertFailsWith returns the exception; force Unit so JUnit 4 sees JVM void.
    @Test fun quoteTreatsMetacharactersAsOneLiteralArgument() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val marker = fixture.directory.resolve("injected")
            val value = "text' \$(touch $marker) `printf ignored`\nnext line"
            val result = fixture.manager.execute("printf '%s' ${RootManager.quote(value)}")
            assertTrue(result.succeeded)
            assertEquals(value, result.stdout)
            assertFalse(Files.exists(marker))
        }
        assertFailsWith<IllegalArgumentException> { RootManager.quote("bad\u0000argument") }
    }

    @Test fun frameworkDetectionRequiresExplicitProductEvidence() {
        assertEquals("Unknown", RootManager.frameworkFromEvidence("31000\n"))
        assertEquals("Magisk", RootManager.frameworkFromEvidence("29.0:MAGISKSU"))
        assertEquals("KernelSU", RootManager.frameworkFromEvidence("v1.0.3:KernelSU"))
        assertEquals("KernelSU Next", RootManager.frameworkFromEvidence("KernelSU-Next 1.2"))
        assertEquals("APatch", RootManager.frameworkFromEvidence("APatch 11000"))
        assertEquals("BakaSU", RootManager.frameworkFromEvidence("BakaSU 1.0"))
        assertEquals("ReSukiSU", RootManager.frameworkFromEvidence("ReSukiSU 1.0"))
    }
}
