package dev.origin.ote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.origin.ote.R
import dev.origin.ote.model.*
import java.text.DateFormat
import java.util.Date

@Composable
fun RootScreen(viewModel: OteViewModel, onBackup: () -> Unit, onRestore: () -> Unit, onExport: () -> Unit, onExportBackup: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val root by viewModel.rootState.collectAsStateWithLifecycle()
    val compatibility by viewModel.compatibility.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var technicalDetails by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.root_center), style = MaterialTheme.typography.headlineMedium) }
        item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(36.dp))
                Text(stringResource(rootStatusText(root)), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(rootDetailText(root)))
                if (root.checked) {
                    Text(stringResource(R.string.root_manager_format,
                        if (root.manager == "Unknown") stringResource(R.string.root_manager_unknown) else root.manager))
                    TextButton(onClick = { technicalDetails = !technicalDetails }) { Text(stringResource(R.string.technical_details)) }
                    if (technicalDetails && root.detail.isNotBlank()) Text(root.detail, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = viewModel::checkRoot, enabled = !busy) { Text(stringResource(R.string.check_root)) }
            }
        }
        item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.compatibility), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(when {
                    root.checked && !root.available -> R.string.compatibility_root_required
                    compatibility?.supported == true -> R.string.profile_supported
                    compatibility?.supported == false -> R.string.profile_unsupported
                    else -> R.string.compatibility_not_checked
                }))
                if (technicalDetails) compatibility?.checks?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.aod_apply_unavailable), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.fp_apply_experimental), style = MaterialTheme.typography.bodySmall)
            }
        }
        current?.let { status -> item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.current_resource), style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onBackup, enabled = root.available && compatibility?.supported == true && !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup))
                }
                OutlinedButton(onClick = onRestore, enabled = root.available && !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.restore))
                }
            }
            Text(stringResource(R.string.restore_explanation), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.temporary_root_note), style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedButton(onClick = onExportBackup, enabled = !busy) { Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_restore_point)) }
            OutlinedButton(onClick = onExport, enabled = !busy) { Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_diagnostics)) }
        }
        item { Text(stringResource(R.string.operation_history), style = MaterialTheme.typography.titleLarge) }
        if (history.isEmpty()) item { Text(stringResource(R.string.history_empty)) }
        items(history.take(100), key = { it.id }) { entry ->
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val actionLabel = historyActionText(entry.action)
                    Text(if (actionLabel != null) stringResource(actionLabel) else entry.action,
                        modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleSmall)
                    // Older builds logged check completion as success even if SU failed.
                    val legacyRootCheck = entry.action == "ROOT_CHECK" && entry.status.equals("SUCCESS", true) && entry.detail.isBlank()
                    val status = if (legacyRootCheck) "UNCERTAIN" else entry.status.uppercase(java.util.Locale.ROOT)
                    val label = when (status) {
                        "SUCCESS", "COMPLETED" -> stringResource(R.string.success)
                        "VERIFIED" -> stringResource(R.string.verified)
                        "UNCERTAIN" -> stringResource(R.string.uncertain)
                        "FAILED", "ERROR" -> stringResource(R.string.failed)
                        "UNSUPPORTED" -> stringResource(R.string.status_unsupported)
                        else -> entry.status
                    }
                    Text(label, color = when (status) {
                        "SUCCESS", "COMPLETED", "VERIFIED" -> MaterialTheme.colorScheme.primary
                        "UNCERTAIN" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    })
                }
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall)
                if (technicalDetails && entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: OteViewModel) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    fun update(transform: (AppPreferences) -> AppPreferences) = viewModel.preferences(transform(preferences))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium) }
        item {
            OteSurface(preferences.style, preferences.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge)
                for ((style, label) in listOf(UiStyle.MIUIX to R.string.style_miuix, UiStyle.MATERIAL to R.string.style_material, UiStyle.GLASS to R.string.style_glass)) {
                    ChoiceRow(stringResource(label), preferences.style == style) { update { it.copy(style = style) } }
                }
                Text(stringResource(R.string.glass_explanation), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ui_implementation_notice), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OteSurface(preferences.style, preferences.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.color_mode), style = MaterialTheme.typography.titleLarge)
                for ((mode, label) in listOf(ColorMode.SYSTEM to R.string.follow_system, ColorMode.LIGHT to R.string.light_mode, ColorMode.DARK to R.string.dark_mode)) {
                    ChoiceRow(stringResource(label), preferences.colorMode == mode) { update { it.copy(colorMode = mode) } }
                }
                ToggleRow(stringResource(R.string.dynamic_color), preferences.dynamicColor) { checked -> update { it.copy(dynamicColor = checked) } }
                ToggleRow(stringResource(R.string.reduce_effects), preferences.reducedMotion) { checked -> update { it.copy(reducedMotion = checked) } }
            }
        }
        item {
            OteSurface(preferences.style, preferences.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
                for ((tag, label) in listOf("system" to R.string.follow_system, "vi" to R.string.language_vietnamese, "ja" to R.string.language_japanese, "en" to R.string.language_english)) {
                    ChoiceRow(stringResource(label), preferences.language == tag) { update { it.copy(language = tag) } }
                }
            }
        }
        item {
            OteSurface(preferences.style, preferences.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.feature_status), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ToggleRow(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = selected, onCheckedChange = onChange)
    }
}
