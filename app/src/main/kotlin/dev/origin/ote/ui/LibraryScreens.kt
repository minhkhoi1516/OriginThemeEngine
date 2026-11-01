package dev.origin.ote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.origin.ote.R
import dev.origin.ote.model.*

@Composable
fun ThemeFacts(item: ThemeItem) {
    Text(stringResource(R.string.theme_facts, item.manifest.canvas.width, item.manifest.canvas.height,
        item.manifest.frames.size, item.manifest.frames.sumOf { it.durationMs.toLong() } / 1000.0),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(kind: ThemeKind, viewModel: OteViewModel, onPreview: (ThemeItem) -> Unit,
                  onApply: (ThemeItem) -> Unit, onDelete: (ThemeItem) -> Unit, onExport: (ThemeItem) -> Unit,
                  onImportPack: () -> Unit, onImportPng: () -> Unit, onImportGif: () -> Unit) {
    val all by viewModel.themes.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val root by viewModel.rootState.collectAsStateWithLifecycle()
    val compatibility by viewModel.compatibility.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var importMenu by remember { mutableStateOf(false) }
    val themes = all.filter { it.kind == kind && (!favoriteOnly || it.favorite) &&
        (it.title.contains(query, true) || it.author.contains(query, true)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (kind == ThemeKind.AOD) R.string.aod_library else R.string.fingerprint_library),
            style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(stringResource(R.string.search_themes)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = favoriteOnly, onClick = { favoriteOnly = !favoriteOnly },
                label = { Text(stringResource(R.string.favorites)) }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp)) })
            IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, stringResource(R.string.change_layout)) }
            Spacer(Modifier.weight(1f))
            Box {
                FilledTonalButton(onClick = { importMenu = true }, enabled = !busy) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.import_action)) }
                DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_pack)) }, onClick = { importMenu = false; onImportPack() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_png)) }, onClick = { importMenu = false; onImportPng() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_gif)) }, onClick = { importMenu = false; onImportGif() })
                }
            }
        }
        if (kind == ThemeKind.FINGERPRINT) Text(stringResource(R.string.fp_apply_experimental), style = MaterialTheme.typography.bodySmall)
        if (kind == ThemeKind.AOD) Text(stringResource(R.string.aod_apply_unavailable), style = MaterialTheme.typography.bodySmall)
        else if (!root.available || compatibility?.supported != true)
            Text(stringResource(R.string.verify_root_first), style = MaterialTheme.typography.bodySmall)
        if (themes.isEmpty()) {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Collections, null, Modifier.size(36.dp))
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.library_empty_description))
                if (kind == ThemeKind.FINGERPRINT) TextButton(onClick = viewModel::createPreset, enabled = !busy) { Text(stringResource(R.string.create_soft_pulse)) }
            }
        } else if (grid) {
            LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(themes, key = { it.id }) { item -> ThemeCard(item, prefs, !busy && kind == ThemeKind.FINGERPRINT && root.available && compatibility?.supported == true,
                    busy, onPreview, onApply, onDelete, onExport, { viewModel.favorite(item.id) }) }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(themes, key = { it.id }) { item -> ThemeCard(item, prefs, !busy && kind == ThemeKind.FINGERPRINT && root.available && compatibility?.supported == true,
                    busy, onPreview, onApply, onDelete, onExport, { viewModel.favorite(item.id) }) }
            }
        }
    }
}

@Composable
private fun ThemeCard(item: ThemeItem, prefs: AppPreferences, canApply: Boolean, busy: Boolean,
                      onPreview: (ThemeItem) -> Unit, onApply: (ThemeItem) -> Unit, onDelete: (ThemeItem) -> Unit,
                      onExport: (ThemeItem) -> Unit, onFavorite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
        ThemePreview(item, false, Modifier.fillMaxWidth().height(148.dp).clip(MaterialTheme.shapes.medium).clickable { onPreview(item) })
        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.author.isNotBlank()) Text(item.author, style = MaterialTheme.typography.bodySmall)
        ThemeFacts(item)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFavorite, enabled = !busy) { Icon(if (item.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, stringResource(R.string.favorite)) }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.preview)) }, onClick = { menu = false; onPreview(item) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_pack)) }, onClick = { menu = false; onExport(item) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_theme)) }, onClick = { menu = false; onDelete(item) })
                }
            }
        }
        if (item.kind == ThemeKind.FINGERPRINT) Button(onClick = { onApply(item) }, enabled = canApply, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_effect)) }
    }
}

@Composable
fun DashboardScreen(viewModel: OteViewModel, onNavigate: (String) -> Unit, onPreview: (ThemeItem) -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    val root by viewModel.rootState.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.make_it_yours), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.dashboard_subtitle))
                Text(viewModel.device.model, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.device_facts, viewModel.device.sdk, viewModel.device.build), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Security, null)
                    Text(stringResource(when { !root.checked -> R.string.root_not_checked; root.available -> R.string.root_available; else -> R.string.root_unavailable }), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.root_lazy_explanation))
                TextButton(onClick = { onNavigate("root") }) { Text(stringResource(R.string.open_root_center)) }
                current?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onNavigate("aod") }, Modifier.weight(1f)) { Icon(Icons.Outlined.Nightlight, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.aod)) }
                FilledTonalButton(onClick = { onNavigate("fingerprint") }, Modifier.weight(1f)) { Icon(Icons.Outlined.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.fingerprint_short)) }
            }
        }
        item { Text(stringResource(R.string.recent_themes), style = MaterialTheme.typography.titleLarge) }
        if (themes.isEmpty()) item { Text(stringResource(R.string.no_recent_themes)) }
        items(themes.take(4), key = { it.id }) { item ->
            OteSurface(prefs.style, prefs.reducedMotion, Modifier.fillMaxWidth().clickable { onPreview(item) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ThemePreview(item, false, Modifier.size(72.dp).clip(MaterialTheme.shapes.small))
                    Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); ThemeFacts(item) }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
    }
}
