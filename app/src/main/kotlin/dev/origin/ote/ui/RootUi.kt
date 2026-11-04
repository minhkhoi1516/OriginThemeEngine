package dev.origin.ote.ui

import androidx.annotation.StringRes
import dev.origin.ote.R
import dev.origin.ote.model.RootState

@StringRes
fun rootStatusText(root: RootState): Int = when {
    !root.checked -> R.string.root_not_checked
    root.available -> R.string.root_available
    root.detail == "su_not_found" -> R.string.root_su_missing_title
    root.detail == "su_start_denied" -> R.string.root_su_denied_title
    else -> R.string.root_unavailable
}

@StringRes
fun rootDetailText(root: RootState): Int = when {
    !root.checked -> R.string.root_lazy_explanation
    root.available -> R.string.root_verified_help
    root.detail == "su_not_found" -> R.string.root_su_missing_help
    root.detail == "su_start_denied" -> R.string.root_su_denied_help
    root.detail == "su_exec_format_error" -> R.string.root_su_invalid_help
    root.detail == "su_start_failed" -> R.string.root_su_start_help
    root.detail.startsWith("timeout_") -> R.string.root_timeout_help
    root.detail.startsWith("cancelled_") -> R.string.root_cancelled_help
    else -> R.string.root_uid_help
}

@StringRes
fun historyActionText(action: String): Int? = when (action.uppercase(java.util.Locale.ROOT)) {
    "CATALOG_SCAN" -> R.string.history_catalog_scan
    "ROOT_CHECK" -> R.string.history_root_check
    "CREATE_PRESET" -> R.string.history_create_preset
    "IMPORT", "IMPORT_PNG", "IMPORT_GIF" -> R.string.import_action
    "EXPORT", "STUDIO_EXPORT" -> R.string.export_pack
    "APPLY" -> R.string.apply_effect
    "BACKUP" -> R.string.backup
    "RESTORE" -> R.string.restore
    "BACKUP_EXPORT" -> R.string.export_restore_point
    "DIAGNOSTICS_EXPORT" -> R.string.export_diagnostics
    "DELETE" -> R.string.delete_theme
    "FAVORITE" -> R.string.favorites
    else -> null
}
