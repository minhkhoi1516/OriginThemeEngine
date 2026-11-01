package dev.origin.ote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import dev.origin.ote.model.CanvasSpec
import dev.origin.ote.model.CompatibilitySpec
import dev.origin.ote.model.FrameSpec
import dev.origin.ote.model.ThemeKind
import dev.origin.ote.model.ThemeManifest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Converts image sources into app-private PNG assets; never executes archive content. */
internal class AssetImporter(private val context: Context) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    data class Generated(val directory: File, val manifest: ThemeManifest)

    suspend fun softPulse(directory: File): Generated {
        val store = FrameStore(directory)
        for (index in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            try {
                if (index in 1..14) {
                    val progress = index / 15f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(116, 216, 255)
                        style = Paint.Style.STROKE
                        strokeWidth = 3.5f
                        alpha = (220 * kotlin.math.sin(progress * Math.PI)).toInt().coerceIn(0, 255)
                    }
                    val canvas = Canvas(bitmap)
                    canvas.drawCircle(128f, 128f, 18 + progress * 95, paint)
                    paint.strokeWidth = 1.6f
                    paint.alpha /= 2
                    canvas.drawCircle(128f, 128f, 10 + progress * 76, paint)
                }
                store.append(bitmap, 33)
            } finally { bitmap.recycle() }
        }
        return store.finish("Soft Pulse", ThemeKind.FINGERPRINT,
            "Original procedural preset generated locally by OTE. 16 transparent PNG frames, 256x256, 33ms each; no third-party artwork.")
    }

    fun image(file: File, title: String, kind: ThemeKind, directory: File): Generated {
        val store = FrameStore(directory)
        file.inputStream().use { store.append(it, 33) }
        return store.finish(title, kind, "Single image imported as one PNG frame with a default 33ms duration.")
    }

    suspend fun pngs(uris: List<Uri>, title: String, kind: ThemeKind, directory: File): Generated {
        require(uris.isNotEmpty() && uris.size <= MAX_FRAMES) { "Select between 1 and 240 PNG files" }
        val ordered = uris.map { it to displayName(it).take(512) }
            .sortedWith { a, b -> naturalCompare(a.second, b.second) }.map { it.first }
        val store = FrameStore(directory)
        ordered.forEach { uri ->
            currentCoroutineContext().ensureActive()
            require(uri.scheme == "content") { "Use Android's document picker" }
            context.contentResolver.openInputStream(uri)?.use { store.append(it, 33) }
                ?: error("Document cannot be opened")
        }
        return store.finish(title, kind, "PNG sequence imported in filename order; default frame delay 33ms. Adjust timing in Studio before applying.")
    }

    suspend fun gif(uri: Uri, title: String, kind: ThemeKind, directory: File): Generated {
        require(uri.scheme == "content") { "Use Android's document picker" }
        val data = context.contentResolver.openInputStream(uri)?.use { input -> readLimited(input, ENTRY_LIMIT) }
            ?: error("GIF document cannot be opened")
        return gifBytes(data, title, kind, directory)
    }

    suspend fun gifFile(file: File, title: String, kind: ThemeKind, directory: File): Generated =
        file.inputStream().use { gifBytes(readLimited(it, ENTRY_LIMIT), title, kind, directory) }

    private suspend fun gifBytes(data: ByteArray, title: String, kind: ThemeKind, directory: File): Generated {
        val parser = GifHeaderParser().setData(data)
        val header = try { parser.parseHeader() } finally { parser.clear() }
        require(header.status == GifDecoder.STATUS_OK) { "Invalid GIF header" }
        validateCanvas(header.width, header.height)
        require(header.numFrames in 1..MAX_FRAMES) { "GIF exceeds frame limit" }
        require(header.width.toLong() * header.height * header.numFrames <= PIXEL_LIMIT) { "GIF exceeds decoded-pixel budget" }
        val provider = object : GifDecoder.BitmapProvider {
            override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap = Bitmap.createBitmap(width, height, config)
            override fun release(bitmap: Bitmap) { bitmap.recycle() }
            override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
            override fun release(bytes: ByteArray) = Unit
            override fun obtainIntArray(size: Int): IntArray = IntArray(size)
            override fun release(array: IntArray) = Unit
        }
        val decoder = StandardGifDecoder(provider, header, ByteBuffer.wrap(data))
        val store = FrameStore(directory)
        try {
            repeat(header.numFrames) {
                currentCoroutineContext().ensureActive()
                decoder.advance()
                val bitmap = decoder.nextFrame ?: error("GIF frame cannot be decoded")
                try {
                    require(decoder.status == GifDecoder.STATUS_OK) { "Incomplete GIF frame" }
                    val delay = if (decoder.nextDelay <= 0) 33 else decoder.nextDelay.coerceIn(5, 10_000)
                    store.append(bitmap, delay)
                } finally { bitmap.recycle() }
            }
        } finally { decoder.clear() }
        return store.finish(title, kind, "GIF decoded to PNG frames. Zero delays use 33ms; other delays are bounded to 5..10000ms. GIF repeat count is not an engine-loop command.")
    }

    suspend fun archive(file: File, title: String, directory: File): Generated {
        CheckedArchive(file).use { outer ->
            // Verified official sample's exact nested path; no fuzzy system-path inference.
            if (outer.has("screenclock/screenclock.zip")) {
                val nested = File(directory.parentFile, "nested-screenclock.zip")
                outer.open("screenclock/screenclock.zip").use { input ->
                    nested.outputStream().use { PrivateFiles.copyBounded(input, it, ARCHIVE_LIMIT) }
                }
                CheckedArchive(nested).use { return aod(it, title, directory) }
            }
            if (outer.has("manifest.xml") && outer.has("1080/config.json")) {
                return aod(outer, title, directory)
            }
            if (outer.has("animation.xml")) return fingerprint(outer, title, directory)
            val images = outer.names.filter { it.endsWith(".png", ignoreCase = true) }.sortedWith(::naturalCompare)
            require(images.isNotEmpty()) { "Unsupported archive: use .otepack, PNG frames, native fingerprint ZIP or verified pattern4 AOD" }
            require(images.size <= MAX_FRAMES) { "Too many PNG frames" }
            val store = FrameStore(directory)
            images.forEach {
                currentCoroutineContext().ensureActive()
                outer.open(it).use { input -> store.append(input, 33) }
            }
            return store.finish(title, ThemeKind.FINGERPRINT,
                "Raw PNG archive; natural filename ordering and default 33ms delay. Source preview videos/GIFs and executable files were not imported. Calibrate the artwork anchor before applying.")
        }
    }

    private suspend fun fingerprint(source: CheckedArchive, title: String, directory: File): Generated {
        val attrs = source.open("animation.xml").use { parseRoot(readLimited(it, XML_LIMIT), "animation") }
        val count = attrs["count"]?.toIntOrNull() ?: error("Fingerprint count is required")
        val delay = attrs["duration"]?.toIntOrNull() ?: error("Fingerprint frame duration is required")
        val width = attrs["width"]?.toIntOrNull() ?: error("Fingerprint width is required")
        val height = attrs["height"]?.toIntOrNull() ?: error("Fingerprint height is required")
        validateCanvas(width, height)
        require(count in 1..MAX_FRAMES && delay in 5..10_000) { "Fingerprint timeline is outside limits" }
        val texture = attrs["texture"] ?: error("PNG texture sequence is required; atlas-only packages are unsupported")
        require(texture.windowed(3).count { it == "{n}" } == 1) { "Texture must contain exactly one {n} frame marker" }
        val store = FrameStore(directory)
        for (index in 1..count) {
            currentCoroutineContext().ensureActive()
            val name = PrivateFiles.safeRelative(texture.replace("{n}", index.toString()))
            require(name.endsWith(".png", ignoreCase = true)) { "Only PNG frame sequences are supported" }
            source.open(name).use { store.append(it, delay) }
        }
        require(store.width == width && store.height == height) { "Native XML and frame canvas differ" }
        return store.finish(title, ThemeKind.FINGERPRINT,
            "PNG animation extracted from native animation.xml. Atlas resources and third-party scripts are not executed or imported.",
            CompatibilitySpec(profile = "pd2453-fingerprint-v1"))
    }

    private suspend fun aod(source: CheckedArchive, title: String, directory: File): Generated {
        val root = source.open("manifest.xml").use { parseRoot(readLimited(it, XML_LIMIT), "aod") }
        require(root["pattern"] == "4") { "Only the verified AOD pattern4 animation can be extracted" }
        val config = source.open("1080/config.json").use { readLimited(it, XML_LIMIT).decodeToString() }
        checkJsonDepth(config)
        val document = Json.parseToJsonElement(config).jsonObject
        val background = document["backgroundWall"]?.jsonObject ?: error("AOD backgroundWall is required")
        val images = background["image"]?.jsonArray ?: error("AOD image sequence is required")
        val delays = background["duration"]?.jsonArray ?: error("AOD frame durations are required")
        require(images.size in 1..MAX_FRAMES && images.size == delays.size) { "Invalid AOD frame timeline" }
        val store = FrameStore(directory)
        images.forEachIndexed { index, value ->
            currentCoroutineContext().ensureActive()
            val path = PrivateFiles.safeRelative("1080/" + value.jsonPrimitive.content)
            val delay = delays[index].jsonPrimitive.content.toIntOrNull() ?: error("Invalid AOD duration")
            source.open(path).use { store.append(it, delay) }
        }
        val name = document["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: title
        return store.finish(name, ThemeKind.AOD,
            "Preview animation extracted from verified pattern4 /1080 resources. JPEG-byte PNG filenames were transcoded to real PNG. Clock, battery, original layout and 1440 branch are not applied by this MVP.",
            CompatibilitySpec(profile = "originos6-pattern4-preview"))
    }

    private inner class FrameStore(val directory: File) {
        var width: Int = 0; private set
        var height: Int = 0; private set
        private var pixels = 0L
        private var bytes = 0L
        private var time = 0L
        private val frames = mutableListOf<FrameSpec>()

        init { check(directory.mkdir()) { "Cannot create new conversion directory" }; check(File(directory, "frames").mkdir()) }

        fun append(input: InputStream, delay: Int) {
            val data = readLimited(input, ENTRY_LIMIT)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            validateCanvas(options.outWidth, options.outHeight)
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
                ?: error("Image cannot be decoded")
            try { append(bitmap, delay) } finally { bitmap.recycle() }
        }

        fun append(bitmap: Bitmap, delay: Int) {
            require(frames.size < MAX_FRAMES && delay in 5..10_000) { "Animation exceeds timeline limits" }
            validateCanvas(bitmap.width, bitmap.height)
            if (frames.isEmpty()) { width = bitmap.width; height = bitmap.height }
            require(bitmap.width == width && bitmap.height == height) { "All frames must use the same canvas" }
            pixels += width.toLong() * height
            time += delay
            require(pixels <= PIXEL_LIMIT && time <= 120_000) { "Animation exceeds decoded-pixel or timeline budget" }
            val path = "frames/frame_%04d.png".format(frames.size)
            val file = File(directory, path)
            file.outputStream().use { raw ->
                val limited = LimitedOutput(raw, ENTRY_LIMIT)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, limited)) { "PNG encoding failed" }
                limited.flush()
            }
            bytes += file.length()
            require(bytes <= EXPANDED_LIMIT) { "Converted images exceed total size limit" }
            frames += FrameSpec(path, delay, PrivateFiles.sha256(file))
        }

        fun finish(title: String, kind: ThemeKind, description: String,
                   compatibility: CompatibilitySpec = CompatibilitySpec()): Generated {
            require(frames.isNotEmpty()) { "No frames imported" }
            val manifest = ThemeManifest(id = UUID.randomUUID().toString(), title = title.take(120).ifBlank { "Imported animation" },
                kind = kind, canvas = CanvasSpec(width, height), frames = frames.toList(), compatibility = compatibility,
                thumbnail = frames[frames.size / 2].path, description = description)
            File(directory, "manifest.json").writeText(json.encodeToString(manifest))
            return Generated(directory, manifest)
        }
    }

    internal class CheckedArchive(file: File) : AutoCloseable {
        private val zip = ZipFile(file)
        val names: List<String>

        init {
            try {
                require(file.length() <= ARCHIVE_LIMIT) { "Archive exceeds size limit" }
                val entries = zip.entries().asSequence().toList()
                require(entries.size <= 2048) { "Archive has too many entries" }
                val seen = mutableSetOf<String>()
                var expanded = 0L
                entries.forEach { entry ->
                    PrivateFiles.safeRelative(entry.name)
                    require(seen.add(entry.name)) { "Archive contains duplicate paths" }
                    require(entry.size in 0..EXPANDED_LIMIT) { "Unknown or excessive entry size" }
                    expanded += entry.size
                    require(expanded <= EXPANDED_LIMIT) { "Archive exceeds expanded size budget" }
                    require(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED) { "Unsupported ZIP compression" }
                }
                names = entries.filterNot { it.isDirectory }.map { it.name }
            } catch (e: Throwable) { zip.close(); throw e }
        }

        fun has(name: String) = name in names
        fun open(name: String): InputStream {
            PrivateFiles.safeRelative(name)
            val entry = zip.getEntry(name) ?: error("Referenced frame is missing")
            require(!entry.isDirectory && entry.size in 0..ARCHIVE_LIMIT) { "Invalid referenced archive entry" }
            return zip.getInputStream(entry)
        }
        override fun close() = zip.close()
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun parseRoot(bytes: ByteArray, expected: String): Map<String, String> {
        val text = bytes.decodeToString()
        require(!text.contains("<!DOCTYPE", ignoreCase = true) && !text.contains("<!ENTITY", ignoreCase = true)) { "XML entities are unsupported" }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(text.reader())
        while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) parser.next()
        require(parser.eventType == XmlPullParser.START_TAG && parser.name == expected) { "Unexpected native XML root" }
        return (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
    }

    companion object {
        private const val MAX_FRAMES = 240
        private const val ENTRY_LIMIT = 32L * 1024 * 1024
        private const val XML_LIMIT = 256L * 1024
        private const val ARCHIVE_LIMIT = 128L * 1024 * 1024
        private const val EXPANDED_LIMIT = 256L * 1024 * 1024
        private const val PIXEL_LIMIT = 240_000_000L

        private fun validateCanvas(width: Int, height: Int) {
            require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 16_000_000) { "Image canvas is outside limits" }
        }

        private fun readLimited(input: InputStream, limit: Long): ByteArray = ByteArrayOutputStream().use { out ->
            PrivateFiles.copyBounded(input, out, limit)
            out.toByteArray()
        }

        /** Bound recursive JSON nesting before invoking the JSON decoder. */
        private fun checkJsonDepth(text: String) {
            var depth = 0
            var string = false
            var escaped = false
            for (char in text) {
                if (string) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') string = false
                } else when (char) {
                    '"' -> string = true
                    '{', '[' -> { depth++; require(depth <= 32) { "AOD JSON nesting exceeds limit" } }
                    '}', ']' -> { depth--; require(depth >= 0) { "Malformed AOD JSON" } }
                }
            }
            require(!string && depth == 0) { "Malformed AOD JSON" }
        }

        /** Numeric filename sorting without converting arbitrarily large tokens to Int. */
        internal fun naturalCompare(a: String, b: String): Int {
            var ai = 0; var bi = 0
            while (ai < a.length && bi < b.length) {
                if (a[ai].isDigit() && b[bi].isDigit()) {
                    val startA = ai; val startB = bi
                    while (ai < a.length && a[ai].isDigit()) ai++
                    while (bi < b.length && b[bi].isDigit()) bi++
                    val na = a.substring(startA, ai).trimStart('0').ifEmpty { "0" }
                    val nb = b.substring(startB, bi).trimStart('0').ifEmpty { "0" }
                    val cmp = if (na.length != nb.length) na.length.compareTo(nb.length) else na.compareTo(nb)
                    if (cmp != 0) return cmp
                } else {
                    val cmp = a[ai].lowercaseChar().compareTo(b[bi].lowercaseChar())
                    if (cmp != 0) return cmp
                    ai++; bi++
                }
            }
            return if (ai == a.length && bi == b.length) a.compareTo(b) else (a.length - ai).compareTo(b.length - bi)
        }
    }

    private class LimitedOutput(private val delegate: OutputStream, private val limit: Long) : OutputStream() {
        private var count = 0L
        override fun write(value: Int) { require(++count <= limit) { "Encoded PNG exceeds entry limit" }; delegate.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            count += length
            require(count <= limit) { "Encoded PNG exceeds entry limit" }
            delegate.write(bytes, offset, length)
        }
        override fun flush() = delegate.flush()
    }
}
