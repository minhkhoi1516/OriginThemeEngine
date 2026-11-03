package dev.origin.ote.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.origin.ote.model.ThemeKind
import dev.origin.ote.model.ThemeManifest
import dev.origin.ote.png.IndexedPngEncoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Builds a data-only native PNG animation ZIP. It never calls SU or mutates a theme. */
class NativeFpBuilder {
    suspend fun build(directory: File, manifest: ThemeManifest, output: File): String =
        withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val checkCancelled = { context.ensureActive() }
            require(manifest.schemaVersion == 1 && manifest.kind == ThemeKind.FINGERPRINT) { "fp_manifest_unsupported" }
            val width = manifest.canvas.width
            val height = manifest.canvas.height
            require(width in 1..1260 && height in 1..2800) { "fp_canvas_outside_profile" }
            require(manifest.frames.size in 1..240) { "fp_frame_count_invalid" }
            require(width.toLong() * height * manifest.frames.size <= 240_000_000L) { "fp_pixel_budget_exceeded" }
            val duration = manifest.frames.first().durationMs
            require(duration in 5..10_000 && manifest.frames.all { it.durationMs == duration }) { "fp_uniform_timing_required" }
            require(duration.toLong() * manifest.frames.size <= 120_000L) { "fp_timeline_too_long" }
            // Artwork anchoring is not sensor geometry. Never silently treat a
            // fullscreen source anchor near the bottom as its image center.
            val anchorX = manifest.canvas.anchorX
            val anchorY = manifest.canvas.anchorY
            require((anchorX == null || anchorX.isFinite() && abs(anchorX - width / 2f) <= 0.5f) &&
                (anchorY == null || anchorY.isFinite() && abs(anchorY - height / 2f) <= 0.5f)) {
                "fp_anchor_recenter_required"
            }
            require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) { "fp_source_directory_invalid" }
            val root = directory.canonicalFile
            require(output.parentFile?.isDirectory == true && !Files.isSymbolicLink(output.toPath())) { "fp_output_invalid" }
            require(!output.canonicalFile.toPath().startsWith(root.toPath())) { "fp_output_inside_source" }
            var created = false
            try {
                Files.newOutputStream(output.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { raw ->
                    created = true
                    ZipOutputStream(raw).use { zip ->
                        val xml = """<?xml version="1.0" encoding="utf-8"?>
<animation type="18" name="ote" texture="frames/frame_{n}.png" count="${manifest.frames.size}" duration="$duration" width="$width" height="$height"/>
""".toByteArray(Charsets.UTF_8)
                        zip.putNextEntry(entry("animation.xml"))
                        zip.write(xml)
                        zip.closeEntry()
                        for ((index, frame) in manifest.frames.withIndex()) {
                            checkCancelled()
                            val source = frameFile(root, frame.path)
                            require(frame.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "fp_frame_hash_invalid" }
                            val bytes = readBounded(source, checkCancelled)
                            require(sha(bytes).equals(frame.sha256, ignoreCase = true)) { "fp_frame_checksum_mismatch" }
                            require(bytes.size >= 33 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) { "fp_png_required" }
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            require(bounds.outWidth == width && bounds.outHeight == height) { "fp_frame_canvas_mismatch" }
                            val options = BitmapFactory.Options().apply {
                                inScaled = false
                                inPremultiplied = false // Encode straight-alpha PNG channels, not a drawn/composited bitmap.
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                ?: throw IllegalArgumentException("fp_png_decode_failed")
                            try {
                                require(bitmap.width == width && bitmap.height == height) { "fp_frame_canvas_mismatch" }
                                val pixels = IntArray(width * height)
                                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                                zip.putNextEntry(entry("frames/frame_${index + 1}.png"))
                                IndexedPngEncoder.encode(width, height, pixels, zip, checkCancelled)
                                zip.closeEntry()
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
                checkCancelled()
                // Structural validation here checks every generated ZIP CRC by
                // streaming entries; the palette writer has separate JVM tests.
                java.util.zip.ZipFile(output).use { zip ->
                    require(zip.size() == manifest.frames.size + 1) { "fp_zip_count_mismatch" }
                    zip.entries().asSequence().forEach { entry ->
                        checkCancelled()
                        zip.getInputStream(entry).use { input ->
                            val crc = java.util.zip.CRC32()
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                checkCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                crc.update(buffer, 0, count); total += count
                            }
                            require(total == entry.size && crc.value == entry.crc) { "fp_zip_crc_mismatch" }
                        }
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                output.inputStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            } catch (error: Throwable) {
                // CREATE_NEW proved this call created the leaf. Never touch an
                // existing output, input frame, arbitrary directory or root path.
                if (created) output.delete()
                throw error
            }
        }

    private fun entry(name: String) = ZipEntry(name).apply { time = 0L }

    private fun frameFile(root: File, path: String): File {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && '\u0000' !in path && ':' !in path) { "fp_frame_path_invalid" }
        val components = path.split('/')
        require(components.none { it.isBlank() || it == "." || it == ".." }) { "fp_frame_path_invalid" }
        var current = root
        for (component in components) {
            current = File(current, component)
            require(!Files.isSymbolicLink(current.toPath())) { "fp_frame_symlink_rejected" }
        }
        val resolved = current.canonicalFile
        require(resolved.toPath().startsWith(root.toPath()) && resolved.isFile) { "fp_frame_path_invalid" }
        require(resolved.length() in 1..MAX_FRAME_BYTES.toLong()) { "fp_frame_size_invalid" }
        return resolved
    }

    private fun readBounded(file: File, checkCancelled: () -> Unit): ByteArray {
        val output = ByteArrayOutputStream(minOf(file.length(), MAX_FRAME_BYTES.toLong()).toInt())
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= MAX_FRAME_BYTES) { "fp_frame_size_invalid" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val MAX_FRAME_BYTES = 32 * 1024 * 1024
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}
