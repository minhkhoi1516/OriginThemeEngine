package dev.origin.ote.root

import dev.origin.ote.model.CompatibilityResult
import dev.origin.ote.model.RootState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Read sequencing fixtures, not an Android root or device-compatibility test. */
class RootReadinessCheckerTest {
    private class Fixture {
        val calls = mutableListOf<String>()
        var root = RootState(available = true, checked = true, detail = "uid_0_verified")
        var supported = true
        var onProbe: () -> Unit = {}
        var onCompatibility: () -> Unit = {}
        var onStatus: () -> Unit = {}
        val checker = RootReadinessChecker(
            probe = { calls += "probe"; onProbe(); root },
            latestRoot = { root },
            inspectCompatibility = {
                calls += "compatibility"; onCompatibility()
                CompatibilityResult(supported, "fixture-profile")
            },
            readCurrentStatus = { calls += "status"; onStatus(); "fixture-status" },
        )
    }

    @Test fun unavailableSuStopsBeforeAnyDeviceInspection() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.root = RootState(checked = true, detail = "su_not_found")
        val result = fixture.checker.check()
        assertEquals(RootCheckOutcome.ROOT_UNAVAILABLE, result.outcome)
        assertEquals(listOf("probe"), fixture.calls)
        assertNull(result.compatibility)
        assertNull(result.currentStatus)
    }

    @Test fun unverifiedAvailabilityFlagIsNotAccepted() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.root = RootState(available = true, checked = false)
        assertEquals(RootCheckOutcome.ROOT_UNAVAILABLE, fixture.checker.check().outcome)
        assertEquals(listOf("probe"), fixture.calls)
    }

    @Test fun unsupportedProfileSkipsTheStatusRead() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.supported = false
        val result = fixture.checker.check()
        assertEquals(RootCheckOutcome.UNSUPPORTED, result.outcome)
        assertEquals(false, result.compatibility?.supported)
        assertNull(result.currentStatus)
        assertEquals(listOf("probe", "compatibility"), fixture.calls)
    }

    @Test fun rootLossDuringCompatibilityDiscardsTheProfile() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.onCompatibility = { fixture.root = RootState(checked = true, detail = "uid_0_not_verified") }
        val result = fixture.checker.check()
        assertEquals(RootCheckOutcome.ROOT_UNAVAILABLE, result.outcome)
        assertFalse(result.root.available)
        assertNull(result.compatibility)
        assertNull(result.currentStatus)
        assertEquals(listOf("probe", "compatibility"), fixture.calls)
    }

    @Test fun rootLossDuringStatusCannotReturnVerified() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.onStatus = { fixture.root = RootState(checked = true, detail = "uid_0_not_verified") }
        val result = fixture.checker.check()
        assertEquals(RootCheckOutcome.ROOT_UNAVAILABLE, result.outcome)
        assertNull(result.compatibility)
        assertNull(result.currentStatus)
    }

    @Test fun verifiedResultRequiresAllReadsAndFinalRootProof() = runBlocking<Unit> {
        val fixture = Fixture()
        val result = fixture.checker.check()
        assertEquals(RootCheckOutcome.VERIFIED, result.outcome)
        assertEquals(true, result.compatibility?.supported)
        assertEquals("fixture-status", result.currentStatus)
        assertEquals(listOf("probe", "compatibility", "status"), fixture.calls)
    }

    @Test fun cancellationIsPropagatedBeforeFurtherReads() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.onProbe = { throw CancellationException("fixture") }
        assertFailsWith<CancellationException> { fixture.checker.check() }
        assertEquals(listOf("probe"), fixture.calls)
    }

    @Test fun statusReadFailureCannotProduceAResult() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.onStatus = { throw IOException("fixture") }
        assertFailsWith<IOException> { fixture.checker.check() }
        assertEquals(listOf("probe", "compatibility", "status"), fixture.calls)
    }
}
