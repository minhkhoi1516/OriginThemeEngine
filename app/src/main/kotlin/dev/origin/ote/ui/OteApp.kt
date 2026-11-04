package dev.origin.ote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import dev.origin.ote.R
import dev.origin.ote.model.ThemeItem
import dev.origin.ote.model.ThemeKind

private data class Destination(val route: String, val title: Int, val icon: ImageVector)
private sealed interface PendingAction {
    data class Apply(val item: ThemeItem) : PendingAction
    data class Delete(val item: ThemeItem) : PendingAction
    data object Backup : PendingAction
    data object Restore : PendingAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OteApp(viewModel: OteViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val nav = rememberNavController()
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var importKind by rememberSaveable { mutableStateOf(ThemeKind.FINGERPRINT) }
    var studioExport by rememberSaveable { mutableStateOf<Pair<String, StudioTransform>?>(null) }
    LaunchedEffect(Unit) {
        viewModel.notices.collect { notice ->
            val message = context.getString(notice.message) + (notice.detail?.let { "\n$it" } ?: "")
            snackbar.showSnackbar(message)
        }
    }
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importTheme) }
    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) viewModel.importPngs(uris, importKind) }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importGif(it, importKind) } }
    val packExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exportId; if (uri != null && id != null) viewModel.export(id, uri); exportId = null
    }
    val studioExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val request = studioExport
        if (uri != null && request != null) viewModel.exportStudio(request.first, request.second, uri)
        studioExport = null
    }
    val diagnosticsExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::exportDiagnostics) }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::exportBackup) }
    val destinations = remember { listOf(
        Destination("home", R.string.workspaces_title, Icons.Outlined.Home),
        Destination("apply", R.string.apply_center, Icons.Outlined.CheckCircle),
        Destination("root", R.string.root_short, Icons.Outlined.Security), Destination("settings", R.string.settings, Icons.Outlined.Settings),
    ) }
    val backStack by nav.currentBackStackEntryAsState()
    fun navigate(route: String) = nav.navigate(route) {
        if (route in destinations.map { it.route }) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
        launchSingleTop = true
    }
    OteTheme(preferences) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, navigationIcon = {
                if (backStack?.destination?.route !in destinations.map { it.route }) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                }
            }, actions = {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }) },
            bottomBar = { NavigationBar { destinations.forEach { destination ->
                NavigationBarItem(selected = (backStack?.destination?.route == destination.route ||
                    destination.route == "home" && backStack?.destination?.route !in listOf("apply", "root", "settings")),
                    onClick = { navigate(destination.route) }, icon = { Icon(destination.icon, stringResource(destination.title)) },
                    label = { Text(stringResource(destination.title), maxLines = 1, style = MaterialTheme.typography.labelSmall) })
            } } }, snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(nav, startDestination = "home", Modifier.padding(padding),
                enterTransition = { fadeIn(tween(if (preferences.reducedMotion) 0 else 180)) },
                exitTransition = { fadeOut(tween(if (preferences.reducedMotion) 0 else 120)) }) {
                composable("home") { WorkspaceChooser(viewModel, onOpen = ::navigate) }
                for ((route, kind) in listOf("aod" to ThemeKind.AOD, "fingerprint" to ThemeKind.FINGERPRINT)) {
                    composable(route) { LibraryScreen(kind, viewModel, onPreview = { previewId = it.id },
                        onApply = { reviewId = it.id; navigate("apply") }, onDelete = { pending = PendingAction.Delete(it) },
                        onExport = { exportId = it.id; packExporter.launch("${it.id}.otepack") },
                        onImportPack = { packPicker.launch(arrayOf("*/*")) },
                        onImportPng = { importKind = kind; pngPicker.launch(arrayOf("image/png")) },
                        onImportGif = { importKind = kind; gifPicker.launch(arrayOf("image/gif")) },
                        onStudio = { editorId = null; navigate("studio_$route") },
                        onSources = { navigate("sources_$route") }) }
                    composable("sources_$route") { DeviceSourcesScreen(kind, viewModel) }
                    composable("studio_$route") { StudioScreen(viewModel, kind, editorId,
                        onExport = { id, edit -> studioExport = id to edit; studioExporter.launch("studio-${id}.otepack") },
                        onImportPng = { importKind = kind; pngPicker.launch(arrayOf("image/png")) },
                        onImportGif = { importKind = kind; gifPicker.launch(arrayOf("image/gif")) }) }
                }
                composable("apply") { ApplyReviewScreen(viewModel, reviewId,
                    onSelect = { reviewId = it }, onConfirm = { pending = PendingAction.Apply(it) },
                    onRoot = { navigate("root") }) }
                composable("root") { RootScreen(viewModel, onBackup = { pending = PendingAction.Backup },
                    onRestore = { pending = PendingAction.Restore }, onExport = { diagnosticsExporter.launch("ote-diagnostics.txt") },
                    onExportBackup = { backupExporter.launch("ote-restore-point.zip") }) }
                composable("settings") { SettingsScreen(viewModel) }
            }
        }
        themes.firstOrNull { it.id == previewId }?.let { item ->
            ModalBottomSheet(onDismissRequest = { previewId = null }) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    ThemePreview(item, playing = true, Modifier.fillMaxWidth().height(340.dp))
                    ThemeFacts(item)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            editorId = item.id; previewId = null
                            navigate(if(item.kind == ThemeKind.AOD) "studio_aod" else "studio_fingerprint")
                        }) { Text(stringResource(R.string.open_workspace_studio)) }
                        OutlinedButton(onClick = { reviewId = item.id; previewId = null; navigate("apply") }) {
                            Text(stringResource(R.string.review_apply))
                        }
                    }
                    Text(item.manifest.description, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { exportId = item.id; packExporter.launch("${item.id}.otepack") }, enabled = !busy) {
                        Text(stringResource(R.string.export_pack))
                    }
                }
            }
        }
        pending?.let { request ->
            val deleting = request is PendingAction.Delete
            AlertDialog(onDismissRequest = { pending = null },
                title = { Text(stringResource(if (deleting) R.string.delete_theme else R.string.confirm_root_action)) },
                text = { Text(stringResource(when (request) {
                    is PendingAction.Delete -> R.string.delete_explanation
                    PendingAction.Restore -> R.string.restore_explanation
                    else -> R.string.root_action_explanation
                })) },
                confirmButton = { TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (request) {
                        is PendingAction.Apply -> viewModel.apply(request.item.id)
                        is PendingAction.Delete -> viewModel.delete(request.item.id)
                        PendingAction.Backup -> viewModel.backup()
                        PendingAction.Restore -> viewModel.restore()
                    }
                    pending = null
                }, enabled = !busy) { Text(stringResource(R.string.continue_action)) } },
                dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
}
