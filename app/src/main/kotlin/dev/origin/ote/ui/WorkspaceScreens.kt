package dev.origin.ote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.origin.ote.R
import dev.origin.ote.model.ThemeItem
import dev.origin.ote.model.ThemeKind

@Composable
fun WorkspaceChooser(viewModel: OteViewModel, onOpen: (String) -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text(stringResource(R.string.workspaces_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.workspaces_subtitle))
        }
        for (kind in ThemeKind.entries.reversed()) item {
            val aod = kind == ThemeKind.AOD
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Icon(if (aod) Icons.Outlined.Nightlight else Icons.Outlined.Fingerprint, null, Modifier.size(36.dp))
                Text(stringResource(if (aod) R.string.aod else R.string.fingerprint_library), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (aod) R.string.workspace_aod_description else R.string.workspace_fp_description))
                Text(stringResource(R.string.workspace_project_count, themes.count { it.kind == kind }), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onOpen(if (aod) "aod" else "fingerprint") }) { Text(stringResource(R.string.open_workspace)) }
            }
        }
        item { Text(stringResource(R.string.workspace_flow), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun DeviceSourcesScreen(kind: ThemeKind, viewModel: OteViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val selected = catalog?.entries?.filter { it.kind == kind }.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.device_sources), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(if (kind == ThemeKind.AOD) R.string.aod_source_help else R.string.fp_source_help))
            Button(onClick = viewModel::scanDeviceSources, enabled = !busy) { Text(stringResource(R.string.scan_device_sources)) }
            Text(stringResource(R.string.catalog_scope), style = MaterialTheme.typography.bodySmall)
        }
        if (catalog == null) item { Text(stringResource(R.string.catalog_not_scanned)) }
        else {
            if (catalog?.limited == true) item { Text(stringResource(R.string.catalog_limited)) }
            if (selected.isEmpty()) item { Text(stringResource(R.string.catalog_no_candidates)) }
            items(selected, key = { it.path }) { source ->
                OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.catalog_bytes, source.bytes))
                    Text(stringResource(R.string.catalog_candidate_only), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** The review page is not a privileged capability. DeviceEngine revalidates on confirmation. */
@Composable
fun ApplyReviewScreen(viewModel: OteViewModel, selectedId: String?, onSelect: (String) -> Unit,
                      onConfirm: (ThemeItem) -> Unit, onRoot: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    val root by viewModel.rootState.collectAsStateWithLifecycle()
    val compatibility by viewModel.compatibility.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val selected = themes.firstOrNull { it.id == selectedId }
    var kind by rememberSaveable(selected?.kind) { mutableStateOf(selected?.kind ?: ThemeKind.AOD) }
    var acknowledged by rememberSaveable(selectedId) { mutableStateOf(false) }
    val ready = selected != null && selected.kind == kind && kind == ThemeKind.FINGERPRINT &&
        root.checked && root.available && compatibility?.supported == true && acknowledged && !busy
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.apply_center), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.apply_review_intro))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (choice in ThemeKind.entries.reversed()) FilterChip(selected = choice == kind,
                    onClick = { kind = choice; acknowledged = false },
                    label = { Text(stringResource(if(choice == ThemeKind.AOD) R.string.aod else R.string.fingerprint_short)) })
            }
        }
        items(themes.filter { it.kind == kind }, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = item.id == selectedId, onClick = { onSelect(item.id) })
                Text(item.title, Modifier.weight(1f))
            }
        }
        if (selected == null || selected.kind != kind) item { Text(stringResource(R.string.apply_choose_project)) }
        else item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Text(selected.title, style = MaterialTheme.typography.titleLarge)
                ThemePreview(selected, playing = true, Modifier.fillMaxWidth().height(260.dp))
                ThemeFacts(selected)
                Text(stringResource(R.string.preview_scope), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (kind == ThemeKind.AOD) item {
            Text(stringResource(R.string.aod_apply_research_status))
        } else item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fp_apply_steps), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(rootStatusText(root)))
                Text(stringResource(if (compatibility?.supported == true) R.string.profile_supported else R.string.verify_root_first))
                OutlinedButton(onClick = onRoot) { Text(stringResource(R.string.open_root_center)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !busy)
                    Text(stringResource(R.string.fp_none_acknowledgement), Modifier.weight(1f))
                }
                Button(onClick = { selected?.let(onConfirm) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.apply_after_review))
                }
                Text(stringResource(R.string.apply_backup_automatic), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
