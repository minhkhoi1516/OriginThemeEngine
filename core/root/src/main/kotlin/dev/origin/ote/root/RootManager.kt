package dev.origin.ote.root

import dev.origin.ote.model.RootState
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** The output of the local SU client; timeout means the device operation is uncertain. */
data class RootResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
) {
    val succeeded: Boolean get() = exitCode == 0 && !timedOut
}

/** Injectable only for host fixtures; the Android application uses the default launcher. */
fun interface ProcessStarter {
    fun start(arguments: List<String>): Process
}

/**
 * Demand-only SU client. Construction and observing [state] never starts a process.
 *
 * [execute] is an INTERNAL trusted-command boundary, not an API for pack scripts.
 * Theme packs must never supply command text. Quote every interpolated argument
 * with [quote], even when a caller currently uses a generated private filename.
 *
 * Each invocation checks UID 0 in the SAME privileged shell before the requested
 * command. The previous result is not treated as proof that temporary root remains.
 */
class RootManager(
    private val starter: ProcessStarter = ProcessStarter { ProcessBuilder(it).start() },
    private val suBinary: String = "su",
    private val identityBinary: String = "/system/bin/id",
    private val outputLimitBytes: Int = 64 * 1024,
) {
    private val executionMutex = Mutex()
    private val mutableState = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = mutableState.asStateFlow()

    init {
        require(outputLimitBytes in 1024..1024 * 1024)
        require(suBinary.isNotBlank() && '\u0000' !in suBinary)
        require(identityBinary.isNotBlank() && '\u0000' !in identityBinary)
    }

    /** Explicitly requests SU and returns only evidence from this invocation. */
    suspend fun probe(): RootState {
        // Numeric version codes are intentionally not mapped to a framework:
        // forks reuse them. Only explicit product strings provide evidence.
        val command = buildString {
            append("printf '%s\\n' '__OTE_SU_VERSION__'\n")
            append(quote(suBinary)).append(" -v 2>/dev/null || :\n")
            append("printf '%s\\n' '__OTE_SU_CODE__'\n")
            append(quote(suBinary)).append(" -V 2>/dev/null || :\n")
        }
        val result = execute(command)
        if (result.succeeded && mutableState.value.available) {
            mutableState.value = mutableState.value.copy(
                manager = frameworkFromEvidence(result.stdout),
                detail = "uid_0_verified",
            )
        }
        return mutableState.value
    }

    /**
     * Timeout/cancellation destroys only the local client. A root daemon or child
     * may have already received the operation. Callers MUST re-inspect the fixed
     * target/journal before retrying writes; this function never retries a command.
     */
    suspend fun execute(command: String, timeoutMs: Long = 15_000): RootResult {
        require(command.isNotBlank() && command.length <= 256 * 1024 && '\u0000' !in command)
        require(timeoutMs in 1..300_000)
        return executionMutex.withLock {
            val nonce = "__OTE_UID0_${UUID.randomUUID().toString().replace("-", "")}__"
            val wrapped = buildString {
                append("ote_uid=\$(").append(quote(identityBinary)).append(" -u 2>/dev/null) || exit 126\n")
                append("[ \"\$ote_uid\" = 0 ] || exit 126\n")
                append("printf '%s\\n' ").append(quote(nonce)).append('\n')
                append("{\n").append(command).append("\n}\n")
            }
            var startedProcess: Process? = null
            val process = try {
                withContext(Dispatchers.IO) {
                    starter.start(listOf(suBinary, "-c", wrapped)).also { startedProcess = it }
                }
            } catch (cancelled: CancellationException) {
                // A cancellation can happen after start() returned but before
                // withContext delivers its result to this coroutine.
                startedProcess?.let { stopLocalClient(it) }
                unavailable("cancelled_operation_state_unknown")
                throw cancelled
            } catch (error: IOException) {
                val (code, reason) = suLaunchFailure(error)
                unavailable(reason)
                return@withLock RootResult(code, "", reason, false)
            } catch (_: SecurityException) {
                unavailable("su_start_denied")
                return@withLock RootResult(126, "", "su_start_denied", false)
            }
            val out = BoundedCapture(outputLimitBytes)
            val err = BoundedCapture(outputLimitBytes)
            try {
                coroutineScope {
                    // Poll the two pipes concurrently. Reading only available
                    // bytes avoids waiting forever for EOF held by a descendant.
                    val stdout = async(Dispatchers.IO) { drain(process.inputStream, process, out) }
                    val stderr = async(Dispatchers.IO) { drain(process.errorStream, process, err) }
                    try {
                        val finished = runInterruptible(Dispatchers.IO) {
                            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                        }
                        if (!finished) {
                            stopLocalClient(process)
                            unavailable("timeout_operation_state_unknown")
                            stdout.cancelAndJoin()
                            stderr.cancelAndJoin()
                            return@coroutineScope RootResult(
                                124, stripAttestation(out.text(), nonce),
                                appendStatus(err.text(), "ote_client_timeout_operation_state_unknown"), true,
                            )
                        }
                        stdout.await()
                        stderr.await()
                        val rawOut = out.text()
                        val attested = rawOut.lineSequence().any { it == nonce }
                        val code = process.exitValue()
                        if (!attested) {
                            unavailable("uid_0_not_verified")
                            return@coroutineScope RootResult(
                                if (code == 0) 126 else code,
                                stripAttestation(rawOut, nonce),
                                appendStatus(err.text(), "ote_uid_0_not_verified"), false,
                            )
                        }
                        mutableState.value = mutableState.value.copy(
                            available = true, checked = true, detail = "uid_0_verified",
                        )
                        RootResult(code, stripAttestation(rawOut, nonce), err.text(), false)
                    } finally {
                        stdout.cancelAndJoin()
                        stderr.cancelAndJoin()
                    }
                }
            } catch (cancelled: CancellationException) {
                unavailable("cancelled_operation_state_unknown")
                throw cancelled
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    stopLocalClient(process)
                    runCatching { process.outputStream.close() }
                    runCatching { process.inputStream.close() }
                    runCatching { process.errorStream.close() }
                }
            }
        }
    }

    private fun unavailable(reason: String) {
        mutableState.value = mutableState.value.copy(available = false, checked = true, detail = reason)
    }

    private suspend fun drain(stream: InputStream, process: Process, capture: BoundedCapture) {
        val buffer = ByteArray(8192)
        var emptyPollsAfterExit = 0
        while (true) {
            coroutineContext.ensureActive()
            val available = try { stream.available() } catch (_: IOException) { return }
            if (available > 0) {
                val count = try { stream.read(buffer, 0, minOf(buffer.size, available)) }
                catch (_: IOException) { return }
                if (count < 0) return
                capture.append(buffer, count)
                emptyPollsAfterExit = 0
            } else if (!process.isAlive) {
                // Allow the Java process reaper a short chance to move pipe
                // tail bytes into its in-memory stream; never wait on a child.
                if (++emptyPollsAfterExit >= 3) return
                delay(5)
            } else {
                delay(10)
            }
        }
    }

    private suspend fun stopLocalClient(process: Process) = withContext(NonCancellable + Dispatchers.IO) {
        if (process.isAlive) {
            runCatching { process.destroy() }
            val exited = runCatching { process.waitFor(150, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            if (!exited) runCatching { process.destroyForcibly() }
        }
    }

    companion object {
        /** POSIX single-argument quoting. Do not use JSON quoting for a shell. */
        fun quote(value: String): String {
            require('\u0000' !in value) { "NUL is not a shell argument" }
            return "'" + value.replace("'", "'\\''") + "'"
        }

        internal fun frameworkFromEvidence(output: String): String {
            val text = output.lowercase()
            return when {
                "resukisu" in text -> "ReSukiSU"
                "bakasu" in text -> "BakaSU"
                "kernelsu next" in text || "kernelsu-next" in text -> "KernelSU Next"
                "kernelsu" in text -> "KernelSU"
                "apatch" in text -> "APatch"
                "magisksu" in text || "magisk" in text -> "Magisk"
                else -> "Unknown"
            }
        }

        private fun stripAttestation(output: String, nonce: String): String {
            val start = if (output.startsWith(nonce + "\n")) nonce.length + 1 else -1
            if (start >= 0) return output.substring(start)
            return output.replace(nonce + "\n", "").replace(nonce, "")
        }

        private fun appendStatus(existing: String, status: String): String =
            if (existing.isBlank()) status else "$existing\n$status"
    }
}

private class BoundedCapture(private val limit: Int) {
    private val data = java.io.ByteArrayOutputStream()
    private var truncated = false

    @Synchronized fun append(bytes: ByteArray, count: Int) {
        val keep = minOf(count, limit - data.size())
        if (keep > 0) data.write(bytes, 0, keep)
        if (keep < count) truncated = true
    }

    @Synchronized fun text(): String = data.toString(StandardCharsets.UTF_8.name()) +
        if (truncated) "\n[ote_output_truncated]" else ""
}
