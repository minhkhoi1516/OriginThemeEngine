package dev.origin.ote.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.origin.ote.R
import dev.origin.ote.data.ThemeRepository
import dev.origin.ote.device.DeviceEngine
import dev.origin.ote.model.*
import dev.origin.ote.pack.PackCodec
import dev.origin.ote.root.RootManager
import dev.origin.ote.root.RootCheckOutcome
import dev.origin.ote.root.RootReadinessChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class Notice(@StringRes val message: Int, val detail: String? = null)
data class StudioTransform(val fps: Int = 30, val scale: Float = 1f, val rotation: Float = 0f,
                           val x: Float = 0f, val y: Float = 0f, val alpha: Float = 1f,
                           val outputWidth: Int? = null, val outputHeight: Int? = null,
                           val trimStart: Int = 0, val trimEnd: Int? = null,
                           val sourceAnchorX: Float? = null, val sourceAnchorY: Float? = null,
                           val keyframes: List<StudioKeyframe> = emptyList(),
                           val frameDurations: List<StudioFrameDuration> = emptyList(),
                           val preserveSourceTiming: Boolean = false,
                           val excludedFrames: List<Int> = emptyList()) : java.io.Serializable

@HiltViewModel
class OteViewModel @Inject constructor(
    private val repository: ThemeRepository,
    private val engine: DeviceEngine,
    private val catalogService: dev.origin.ote.device.DeviceCatalogService,
    private val root: RootManager,
    private val codec: PackCodec,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val themes = repository.themes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())
    val device: DeviceInfo = engine.deviceInfo()
    val rootState = root.state
    private val _compatibility = MutableStateFlow<CompatibilityResult?>(null)
    val compatibility = _compatibility.asStateFlow()
    private val _current = MutableStateFlow<String?>(null)
    val current = _current.asStateFlow()
    private val _catalog = MutableStateFlow<dev.origin.ote.root.DeviceCatalog?>(null)
    val catalog = _catalog.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val messages = Channel<Notice>(Channel.BUFFERED)
    val notices = messages.receiveAsFlow()

    private fun action(name: String, success: Int? = R.string.operation_done, recordSuccess: Boolean = true, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                if (recordSuccess) repository.log(name, "SUCCESS", "")
                if (success != null) messages.send(Notice(success))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                // Persist error type only, not document URIs or source filenames.
                runCatching { repository.log(name, "FAILED", error.javaClass.simpleName) }
                messages.send(Notice(R.string.operation_failed, error.message?.take(300)))
            } finally {
                _busy.value = false
            }
        }
    }

    fun importTheme(uri: Uri) = action("IMPORT", recordSuccess = false) { repository.importTheme(uri) }
    fun importPngs(uris: List<Uri>, kind: ThemeKind) = action("IMPORT_PNG", recordSuccess = false) {
        if (uris.isNotEmpty()) repository.importPngs(uris, context.getString(R.string.imported_sequence), kind)
    }
    fun importGif(uri: Uri, kind: ThemeKind) = action("IMPORT_GIF", recordSuccess = false) {
        repository.importGif(uri, context.getString(R.string.imported_gif), kind)
    }
    fun export(id: String, uri: Uri) = action("EXPORT", R.string.export_done, recordSuccess = false) { repository.export(id, uri) }
    fun delete(id: String) = action("DELETE", recordSuccess = false) { repository.delete(id) }
    fun favorite(id: String) = action("FAVORITE") { repository.favorite(id) }
    fun createPreset() = action("CREATE_PRESET", recordSuccess = false) { repository.createSoftPulsePreset() }
    fun createPreset(kind: dev.origin.ote.data.PresetKind) = action("CREATE_PRESET", recordSuccess = false) { repository.createPreset(kind) }
    fun preferences(value: AppPreferences) {
        viewModelScope.launch {
            try {
                repository.setPreferences(value)
                val target = if (value.language == "system") android.os.LocaleList.getEmptyLocaleList()
                    else android.os.LocaleList.forLanguageTags(value.language)
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                if (localeManager.applicationLocales != target) localeManager.applicationLocales = target
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { messages.send(Notice(R.string.operation_failed, error.message?.take(300))) }
        }
    }
    fun scanDeviceSources() = action("CATALOG_SCAN") {
        _catalog.value = null
        _catalog.value = catalogService.scan()
    }
    fun checkRoot() = action("ROOT_CHECK", success = null, recordSuccess = false) {
        _compatibility.value = null
        _current.value = null
        val result = RootReadinessChecker(root::probe, { root.state.value },
            engine::compatibility, engine::currentStatus).check()
        _compatibility.value = result.compatibility
        _current.value = result.currentStatus
        when (result.outcome) {
            RootCheckOutcome.ROOT_UNAVAILABLE -> {
                repository.log("ROOT_CHECK", "FAILED", result.root.detail.ifBlank { "root_not_checked" })
                messages.send(Notice(rootDetailText(result.root)))
            }
            RootCheckOutcome.UNSUPPORTED -> {
                repository.log("ROOT_CHECK", "UNSUPPORTED", "device_profile_not_verified")
                messages.send(Notice(R.string.profile_unsupported))
            }
            RootCheckOutcome.VERIFIED -> {
                repository.log("ROOT_CHECK", "VERIFIED", "uid_0_and_profile_verified")
                messages.send(Notice(R.string.root_check_complete))
            }
        }
    }
    fun apply(id: String) = action("APPLY", recordSuccess = false) {
        _current.value = engine.apply(id)
        root.probe() // Detect temporary-root loss after the transaction.
    }
    fun backup() = action("BACKUP", recordSuccess = false) { _current.value = engine.backup() }
    fun restore() = action("RESTORE", recordSuccess = false) { _current.value = engine.restore() }
    fun exportBackup(uri: Uri) = action("BACKUP_EXPORT", R.string.export_done, recordSuccess = false) { engine.exportLatestBackup(uri) }
    fun exportStudio(id: String, transform: StudioTransform, uri: Uri) = action("STUDIO_EXPORT", R.string.export_done) {
        val item = requireNotNull(repository.get(id))
        StudioExporter(context, codec).export(item, transform, uri)
    }
    fun exportDiagnostics(uri: Uri) = action("DIAGNOSTICS_EXPORT", R.string.export_done) {
        val text = buildString {
            appendLine("Origin Theme Engine diagnostics")
            appendLine("Model=${device.model}; SDK=${device.sdk}; Build=${device.build}")
            appendLine("Root checked=${rootState.value.checked}; available=${rootState.value.available}; manager=${rootState.value.manager}")
            appendLine("Root reason=${rootState.value.detail.ifBlank { "not_checked" }}")
            appendLine("Profile=${_compatibility.value?.profileId ?: "unverified"}")
            appendLine("Compatibility=${_compatibility.value?.supported?.toString() ?: "not_checked"}")
            // Deliberately exclude content URIs, account identifiers, raw su output and file paths.
            history.value.take(50).forEach { appendLine("${it.timestamp} ${it.action} ${it.status}") }
        }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
            it.write(text.toByteArray(Charsets.UTF_8))
        }
    }
}
