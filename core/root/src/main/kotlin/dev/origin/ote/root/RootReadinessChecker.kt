package dev.origin.ote.root

import dev.origin.ote.model.CompatibilityResult
import dev.origin.ote.model.RootState

enum class RootCheckOutcome { VERIFIED, ROOT_UNAVAILABLE, UNSUPPORTED }

data class RootReadinessResult(
    val outcome: RootCheckOutcome,
    val root: RootState,
    val compatibility: CompatibilityResult? = null,
    val currentStatus: String? = null,
)

/** Coordinates explicit read-only checks; an unavailable SU client is never a success. */
class RootReadinessChecker(
    private val probe: suspend () -> RootState,
    private val latestRoot: () -> RootState,
    private val inspectCompatibility: suspend () -> CompatibilityResult,
    private val readCurrentStatus: suspend () -> String,
) {
    suspend fun check(): RootReadinessResult {
        val initial = probe()
        if (!initial.checked || !initial.available) return unavailable(initial)
        val compatibility = inspectCompatibility()
        val afterCompatibility = latestRoot()
        if (!afterCompatibility.checked || !afterCompatibility.available) return unavailable(afterCompatibility)
        if (!compatibility.supported) {
            return RootReadinessResult(RootCheckOutcome.UNSUPPORTED, afterCompatibility, compatibility)
        }
        val status = readCurrentStatus()
        val finalRoot = latestRoot()
        if (!finalRoot.checked || !finalRoot.available) return unavailable(finalRoot)
        return RootReadinessResult(RootCheckOutcome.VERIFIED, finalRoot, compatibility, status)
    }

    private fun unavailable(root: RootState) = RootReadinessResult(RootCheckOutcome.ROOT_UNAVAILABLE, root)
}
