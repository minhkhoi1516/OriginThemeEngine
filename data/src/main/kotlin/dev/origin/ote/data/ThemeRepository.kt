package dev.origin.ote.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.origin.ote.model.AppPreferences
import dev.origin.ote.model.ColorMode
import dev.origin.ote.model.ImportedTheme
import dev.origin.ote.model.OperationEntry
import dev.origin.ote.model.ThemeItem
import dev.origin.ote.model.ThemeKind
import dev.origin.ote.model.ThemeManifest
import dev.origin.ote.model.UiStyle
import dev.origin.ote.pack.PackCodec
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.oteSettings by preferencesDataStore(name = "ote-preferences")

/** App-private library and SAF boundaries. This layer never executes SU. */
@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OteDatabase,
    private val codec: PackCodec,
) {
    private val dao = database.dao()
    private val json = Json { encodeDefaults = true }
    private val mutation = Mutex()
    private val assetImporter = AssetImporter(context)
    private val libraryRoot = File(context.filesDir, "theme-library").also { root ->
        require(!Files.isSymbolicLink(root.toPath())) { "Private library must not be a symlink" }
        check(root.isDirectory || root.mkdir()) { "Cannot create private theme library" }
    }
    private val importRoot = File(context.cacheDir, "ote-imports").also { root ->
        require(!Files.isSymbolicLink(root.toPath())) { "Private import cache must not be a symlink" }
        check(root.isDirectory || root.mkdir()) { "Cannot create private import cache" }
    }

    val themes: Flow<List<ThemeItem>> = dao.observeThemes().map { rows -> rows.map(::item) }.flowOn(Dispatchers.IO)
    val history: Flow<List<OperationEntry>> = dao.observeHistory().map { rows ->
        rows.map { OperationEntry(it.id, it.timestamp, it.action, it.status, it.detail) }
    }
    val preferences: Flow<AppPreferences> = context.oteSettings.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { settings ->
        AppPreferences(
            style = enumOr(settings[STYLE], UiStyle.MIUIX),
            colorMode = enumOr(settings[COLOR_MODE], ColorMode.SYSTEM),
            language = settings[LANGUAGE]?.takeIf { it in LANGUAGES } ?: "system",
            reducedMotion = settings[REDUCED_MOTION] ?: false,
            dynamicColor = settings[DYNAMIC_COLOR] ?: true,
        )
    }

    /** Internal UUIDs only; no metadata or user-supplied filename enters a filesystem path. */
    fun directory(id: String): File {
        require(ID.matches(id) && UUID.fromString(id).toString() == id) { "Invalid library ID" }
        val result = File(libraryRoot, id)
        require(result.canonicalFile.parentFile == libraryRoot.canonicalFile && !Files.isSymbolicLink(result.toPath())) {
            "Library directory escapes private storage"
        }
        return result
    }

    suspend fun get(id: String): ThemeItem? = withContext(Dispatchers.IO) {
        directory(id)
        dao.get(id)?.let(::item)
    }

    /** Backtick alias retained for the design contract; UI should call importTheme. */
    suspend fun `import`(uri: Uri): String = importTheme(uri)

    suspend fun importTheme(uri: Uri): String = importOperation("IMPORT") { work, destination ->
        require(uri.scheme == "content") { "Select a document using Android's Storage Access Framework" }
        val input = File(work, "input.bin")
        val coroutine = currentCoroutineContext()
        context.contentResolver.openInputStream(uri)?.use { source ->
            input.outputStream().use { out -> PrivateFiles.copyBounded(source, out, ARCHIVE_LIMIT) { coroutine.ensureActive() } }
        } ?: error("Selected document cannot be opened")
        val title = documentTitle(uri)
        val magic = ByteArray(8)
        input.inputStream().use { it.read(magic) }
        when {
            magic.take(2) == listOf(0x50.toByte(), 0x4b.toByte()) -> {
                AssetImporter.CheckedArchive(input).use { archive ->
                    if (archive.has("manifest.json")) return@importOperation input.inputStream().use { codec.importPack(it, destination) }
                }
                val generated = assetImporter.archive(input, title, File(work, "converted"))
                adoptGenerated(generated, work, destination)
            }
            magic.copyOfRange(0, 3).contentEquals(byteArrayOf(71, 73, 70)) -> {
                val generated = assetImporter.gifFile(input, title, ThemeKind.FINGERPRINT, File(work, "converted"))
                adoptGenerated(generated, work, destination)
            }
            else -> {
                val generated = assetImporter.image(input, title, ThemeKind.FINGERPRINT, File(work, "converted"))
                adoptGenerated(generated, work, destination)
            }
        }
    }

    suspend fun importPngs(uris: List<Uri>, title: String, kind: ThemeKind): String = importOperation("IMPORT_PNG") { work, destination ->
        val generated = assetImporter.pngs(uris, title, kind, File(work, "converted"))
        adoptGenerated(generated, work, destination)
    }

    suspend fun importGif(uri: Uri, title: String, kind: ThemeKind): String = importOperation("IMPORT_GIF") { work, destination ->
        val generated = assetImporter.gif(uri, title, kind, File(work, "converted"))
        adoptGenerated(generated, work, destination)
    }

    suspend fun createPreset(kind: PresetKind): String = importOperation("CREATE_PRESET") { work, destination ->
        val directory=File(work,"converted")
        val manifest=ProceduralPresets.create(directory,kind)
        adoptGenerated(AssetImporter.Generated(directory,manifest),work,destination)
    }

    suspend fun createSoftPulsePreset(): String = importOperation("CREATE_PRESET") { work, destination ->
        val generated = assetImporter.softPulse(File(work, "converted"))
        adoptGenerated(generated, work, destination)
    }

    suspend fun export(id: String, uri: Uri): Unit = withContext(Dispatchers.IO) {
        mutation.withLock {
            require(uri.scheme == "content") { "Select an export destination using the document picker" }
            val row = dao.get(id) ?: error("Theme no longer exists")
            val source = directory(row.id)
            val work = newWorkDirectory()
            try {
                // Validate and finish the ZIP in private cache before opening a destination.
                val ready = File(work, "export.otepack")
                ready.outputStream().use { codec.exportPack(source, it) }
                require(ready.length() <= ARCHIVE_LIMIT) { "Export exceeds archive size limit" }
                val coroutine = currentCoroutineContext()
                coroutine.ensureActive()
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    ready.inputStream().use { PrivateFiles.copyBounded(it, out, ARCHIVE_LIMIT) { coroutine.ensureActive() } }
                    out.flush()
                } ?: error("Export document cannot be opened")
                log("EXPORT", "SUCCESS", "Theme pack exported through the document picker")
            } finally { cleanup(work) }
        }
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        mutation.withLock {
            val row = dao.get(id) ?: error("Theme no longer exists")
            val source = directory(row.id)
            val trash = File(libraryRoot, ".deleted-${UUID.randomUUID()}")
            val moved = if (source.exists()) {
                require(source.isDirectory) { "Private theme directory is invalid" }
                check(source.renameTo(trash)) { "Cannot stage private theme removal" }
                true
            } else false
            try {
                database.withTransaction { dao.delete(row.id) }
            } catch (error: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val rowMayRemain = runCatching { dao.get(row.id) != null }.getOrDefault(true)
                    if (moved && rowMayRemain) check(trash.renameTo(source)) { "Cannot restore private library directory" }
                }
                throw error
            }
            withContext(NonCancellable + Dispatchers.IO) {
                if (moved) PrivateFiles.removeOwnedTree(trash, libraryRoot)
            }
            log("DELETE", "SUCCESS", "Theme removed from OTE library only; active system resources were not changed")
        }
    }

    suspend fun favorite(id: String) = withContext(Dispatchers.IO) {
        directory(id)
        require(dao.get(id) != null) { "Theme no longer exists" }
        dao.toggleFavorite(id)
    }

    suspend fun setPreferences(p: AppPreferences) {
        require(p.language in LANGUAGES) { "Unsupported app language" }
        context.oteSettings.edit { data ->
            data[STYLE] = p.style.name
            data[COLOR_MODE] = p.colorMode.name
            data[LANGUAGE] = p.language
            data[REDUCED_MOTION] = p.reducedMotion
            data[DYNAMIC_COLOR] = p.dynamicColor
        }
    }

    suspend fun log(action: String, status: String, detail: String) {
        val clean = detail
            .replace(Regex("(?:content|file)://\\S+"), "<document>")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>")
            .replace(Regex("(?i)(token|password|authorization)\\s*[:=]\\s*\\S+"), "\$1=<redacted>")
            .filter { it == '\n' || !it.isISOControl() }.take(2000)
        database.withTransaction {
            dao.insertOperation(OperationEntity(timestamp = System.currentTimeMillis(), action = action.take(64), status = status.take(32), detail = clean))
            dao.pruneHistory()
        }
    }

    private suspend fun importOperation(action: String, importer: suspend (File, File) -> ImportedTheme): String = withContext(Dispatchers.IO) {
        mutation.withLock {
            val id = UUID.randomUUID().toString()
            val destination = directory(id)
            check(!destination.exists())
            val work = newWorkDirectory()
            var committed = false
            try {
                val imported = importer(work, destination)
                currentCoroutineContext().ensureActive()
                val manifest = imported.manifest
                database.withTransaction {
                    dao.insert(ThemeEntity(id = id, title = manifest.title, author = manifest.author,
                        kind = manifest.kind.name, manifestJson = json.encodeToString(manifest),
                        createdAt = System.currentTimeMillis(), packSha256 = imported.packSha256))
                    dao.insertOperation(OperationEntity(timestamp = System.currentTimeMillis(), action = action,
                        status = "SUCCESS", detail = "Imported ${manifest.frames.size} verified ${manifest.kind.name} frames"))
                    dao.pruneHistory()
                }
                committed = true
                id
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    // If cancellation raced the Room return, do not delete a committed row's assets.
                    try {
                        val keepAssets = committed || runCatching { dao.get(id) != null }.getOrDefault(true)
                        if (!keepAssets && destination.exists()) PrivateFiles.removeOwnedTree(destination, libraryRoot)
                    } finally { cleanup(work) }
                }
            }
        }
    }

    private fun adoptGenerated(generated: AssetImporter.Generated, work: File, destination: File): ImportedTheme {
        val ready = File(work, "generated.otepack")
        ready.outputStream().use { codec.exportPack(generated.directory, it) }
        require(ready.length() <= ARCHIVE_LIMIT) { "Converted animation exceeds archive size limit" }
        return ready.inputStream().use { codec.importPack(it, destination) }
    }

    private fun newWorkDirectory(): File {
        val result = File(importRoot, UUID.randomUUID().toString())
        check(result.mkdir()) { "Cannot create private import workspace" }
        require(result.canonicalFile.parentFile == importRoot.canonicalFile && !Files.isSymbolicLink(result.toPath()))
        return result
    }

    private suspend fun cleanup(work: File) = withContext(NonCancellable + Dispatchers.IO) {
        if (work.exists()) PrivateFiles.removeOwnedTree(work, importRoot)
    }

    private fun item(row: ThemeEntity): ThemeItem = ThemeItem(row.id, row.title, row.author,
        ThemeKind.valueOf(row.kind), directory(row.id).absolutePath, row.favorite, row.createdAt,
        json.decodeFromString<ThemeManifest>(row.manifestJson))

    private fun documentTitle(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.')?.filterNot(Char::isISOControl)?.take(120)?.ifBlank { null }
    }.getOrNull() ?: "Imported animation"

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    companion object {
        private const val ARCHIVE_LIMIT = 128L * 1024 * 1024
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val LANGUAGES = setOf("system", "en", "vi", "ja", "en-US", "vi-VN", "ja-JP")
        private val STYLE = stringPreferencesKey("ui_style")
        private val COLOR_MODE = stringPreferencesKey("color_mode")
        private val LANGUAGE = stringPreferencesKey("app_language")
        private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
