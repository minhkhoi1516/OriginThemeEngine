package dev.origin.ote.pack

import dev.origin.ote.model.ImportedTheme
import dev.origin.ote.model.ThemeManifest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.Inflater

/** Bounded, data-only .otepack v1 codec. Input/output streams remain caller-owned. */
class PackCodec(private val limits: PackLimits = PackLimits()) {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
        explicitNulls = false
    }

    /** Destination must be a new child of an existing app-controlled directory. */
    fun importPack(input: InputStream, destination: File): ImportedTheme {
        val parent = destination.absoluteFile.parentFile
            ?: throw PackException("Destination parent required")
        requirePack(parent.isDirectory, "Destination parent is not a directory")
        val realParent = parent.canonicalFile
        val root = File(realParent, destination.name)
        requirePack(root.name.isNotBlank() && root.name != "." && root.name != "..", "Invalid destination")
        requirePack(!Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS), "Destination already exists")
        requirePack(root.mkdir(), "Cannot create destination")
        val created = mutableListOf(root)
        try {
            val spool = File(root, ".ote-import.zip")
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newOutputStream(spool.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                created += spool
                copyLimited(input, output, limits.maxArchiveBytes, digest)
            }
            val archiveHash = digest.digest().hex()
            val central = inspectZip(spool)
            val importedManifest = ZipFile(spool, StandardCharsets.UTF_8).use { zip ->
                val entries = zip.entries().asSequence().toList()
                requirePack(entries.size == central.size, "ZIP directory mismatch")
                entries.forEach { entry ->
                    val declared = central[entry.name] ?: throw PackException("ZIP entry mismatch")
                    requirePack(entry.size == declared.size && entry.crc == declared.crc, "ZIP metadata mismatch")
                }
                val manifestEntry = zip.getEntry(MANIFEST) ?: throw PackException("manifest.json is required")
                val manifest = parseManifest(readEntry(zip, manifestEntry, limits.maxManifestBytes))
                val allowed = declaredFiles(manifest)
                val actual = entries.filterNot { it.isDirectory }.map { it.name }.toSet()
                requirePack(actual == allowed, "Pack contains missing or undeclared files")
                entries.filter { it.isDirectory }.forEach { directory ->
                    requirePack(allowed.any { it.startsWith(directory.name) }, "Undeclared ZIP directory")
                }
                var expanded = 0L
                for (name in allowed.sorted()) {
                    val entry = zip.getEntry(name) ?: throw PackException("Declared file missing")
                    val target = safeFile(root, name)
                    createParents(root, target.parentFile, created)
                    Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        created += target
                        val crc = CRC32()
                        val count = zip.getInputStream(entry).use { stream ->
                            copyLimited(stream, output, entryLimit(name), crc = crc)
                        }
                        requirePack(count == entry.size && crc.value == entry.crc, "ZIP CRC/size mismatch")
                        expanded += count
                        requirePack(expanded <= limits.maxExpandedBytes, "Expanded pack is too large")
                    }
                }
                validateDirectory(root)
                manifest
            }
            requirePack(spool.delete(), "Cannot remove import staging ZIP")
            return ImportedTheme(importedManifest, root, archiveHash)
        } catch (failure: Exception) {
            // Delete only paths created by this invocation; do not traverse or
            // recursively delete unknown contents if another writer intervened.
            if (!Files.isSymbolicLink(root.toPath())) {
                created.asReversed().forEach { path ->
                    if (!Files.isSymbolicLink(path.toPath()) && path.canonicalPath.startsWith(realParent.path + File.separator)) {
                        path.delete()
                    }
                }
            }
            if (failure is PackException) throw failure
            throw PackException("Pack import failed", failure)
        }
    }

    /** Metadata only; use validateDirectory before applying or exporting assets. */
    fun readManifest(directory: File): ThemeManifest {
        val file = safeFile(directory.canonicalFile, MANIFEST)
        requireRegular(file)
        requirePack(file.length() <= limits.maxManifestBytes, "Manifest too large")
        return parseManifest(file.readBytes())
    }

    /** Fresh validation of all declared frame hashes and PNG dimensions. */
    fun validateDirectory(directory: File): ThemeManifest {
        requirePack(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()), "Invalid theme directory")
        val root = directory.canonicalFile
        val manifest = readManifest(root)
        var total = safeFile(root, MANIFEST).length()
        for (frame in manifest.frames) {
            val file = safeFile(root, frame.path)
            requireRegular(file)
            requirePack(file.length() in 1..limits.maxEntryBytes, "Frame file size exceeds limit")
            checkPng(file, manifest.canvas.width, manifest.canvas.height)
            requirePack(sha256(file) == frame.sha256, "Frame checksum mismatch")
            total += file.length()
        }
        manifest.thumbnail?.let { path ->
            val file = safeFile(root, path)
            requireRegular(file)
            requirePack(file.length() in 1..limits.maxEntryBytes, "Thumbnail too large")
            checkPng(file)
            if (path !in manifest.frames.map { it.path }) total += file.length()
        }
        requirePack(total <= limits.maxExpandedBytes, "Expanded pack is too large")
        return manifest
    }

    /** Deterministic file order/timestamps; validates source again before export. */
    fun exportPack(themeDirectory: File, output: OutputStream) {
        val root = themeDirectory.canonicalFile
        val manifest = validateDirectory(themeDirectory)
        // Close the ZIP/deflater but not the caller's SAF stream. Bound actual
        // compressed output too, so an exported pack is importable by this codec.
        val bounded = object : OutputStream() {
            var written = 0L
            override fun write(value: Int) {
                requirePack(written + 1 <= limits.maxArchiveBytes, "Export exceeds archive byte limit")
                output.write(value); written++
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                requirePack(written + length <= limits.maxArchiveBytes, "Export exceeds archive byte limit")
                output.write(bytes, offset, length); written += length
            }
            override fun flush() { output.flush() }
            override fun close() { flush() }
        }
        val expectedHashes = manifest.frames.associate { it.path to it.sha256 }
        ZipOutputStream(bounded).use { archive ->
            for (name in declaredFiles(manifest).sorted()) {
                val entry = ZipEntry(name).apply { time = 0L }
                archive.putNextEntry(entry)
                if (name == MANIFEST) {
                    val normalized = json.encodeToString(manifest).toByteArray(StandardCharsets.UTF_8)
                    requirePack(normalized.size.toLong() <= limits.maxManifestBytes, "Normalized manifest exceeds limit")
                    archive.write(normalized)
                } else {
                    val file = safeFile(root, name)
                    requireRegular(file)
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { copyLimited(it, archive, limits.maxEntryBytes, digest) }
                    expectedHashes[name]?.let { requirePack(it == digest.digest().hex(), "Source frame changed during export") }
                }
                archive.closeEntry()
            }
        }
    }

    private fun parseManifest(data: ByteArray): ThemeManifest {
        requirePack(data.size.toLong() <= limits.maxManifestBytes, "Manifest too large")
        val text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()
        StrictJsonKeys.check(text)
        val objectValue = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) {
            throw PackException("Invalid manifest JSON", e)
        }
        requirePack("schemaVersion" in objectValue, "Explicit schemaVersion is required")
        val manifest = try { json.decodeFromString<ThemeManifest>(text) } catch (e: Exception) {
            throw PackException("Unsupported manifest fields", e)
        }
        validateManifest(manifest)
        return manifest
    }

    private fun validateManifest(m: ThemeManifest) {
        requirePack(m.schemaVersion == 1, "Unsupported pack schema version")
        requirePack(m.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")), "Invalid theme id")
        requirePack(m.title.isNotBlank() && m.title.length <= 120, "Invalid title")
        requirePack(m.author.length <= 120 && m.version.length in 1..40 && m.description.length <= 4000, "Metadata too large")
        val canvas = m.canvas
        requirePack(canvas.width in 1..limits.maxCanvasSide && canvas.height in 1..limits.maxCanvasSide, "Canvas side exceeds limit")
        val pixels = canvas.width.toLong() * canvas.height
        requirePack(pixels <= limits.maxCanvasPixels, "Canvas pixel budget exceeded")
        requirePack((canvas.anchorX == null) == (canvas.anchorY == null), "Both anchor coordinates are required")
        canvas.anchorX?.let { requirePack(it.isFinite() && it >= 0 && it <= canvas.width, "Invalid anchor X") }
        canvas.anchorY?.let { requirePack(it.isFinite() && it >= 0 && it <= canvas.height, "Invalid anchor Y") }
        requirePack(m.frames.size in 1..limits.maxFrames, "Frame count exceeds limit")
        requirePack(pixels * m.frames.size <= limits.maxTotalDecodedPixels, "Total animation pixel budget exceeded")
        requirePack(m.frames.map { it.path }.toSet().size == m.frames.size, "Duplicate frame paths")
        var duration = 0L
        m.frames.forEach {
            validatePath(it.path, false)
            requirePack(it.path.endsWith(".png", ignoreCase = true), "Only PNG animation frames are supported")
            requirePack(it.sha256.matches(Regex("[0-9a-f]{64}")), "Invalid SHA-256")
            requirePack(it.durationMs in limits.minFrameDurationMs..limits.maxFrameDurationMs, "Invalid frame duration")
            duration += it.durationMs
        }
        requirePack(duration <= limits.maxTimelineMs, "Animation timeline is too long")
        m.thumbnail?.let {
            validatePath(it, false)
            requirePack(it.endsWith(".png", ignoreCase = true), "Thumbnail must be PNG")
        }
        requirePack(m.compatibility.sdkMin in 23..99, "Invalid minimum SDK")
        m.compatibility.sdkMax?.let { requirePack(it in m.compatibility.sdkMin..99, "Invalid maximum SDK") }
        requirePack(m.compatibility.models.size <= 32 && m.compatibility.models.all { it.length in 1..64 }, "Invalid model list")
        requirePack((m.compatibility.profile?.length ?: 0) <= 80, "Invalid profile id")
    }

    private fun declaredFiles(m: ThemeManifest): Set<String> = buildSet {
        add(MANIFEST)
        addAll(m.frames.map { it.path })
        m.thumbnail?.let(::add)
    }

    private fun entryLimit(name: String) = if (name == MANIFEST) limits.maxManifestBytes else limits.maxEntryBytes

    private fun checkPng(file: File, expectedWidth: Int? = null, expectedHeight: Int? = null) {
        val header = ByteArray(33)
        file.inputStream().use { stream ->
            var read = 0
            while (read < header.size) {
                val count = stream.read(header, read, header.size - read)
                requirePack(count > 0, "Truncated PNG")
                read += count
            }
        }
        requirePack(header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE), "Frame is not PNG")
        requirePack(bigInt(header, 8) == 13 && String(header, 12, 4, StandardCharsets.US_ASCII) == "IHDR", "Invalid PNG header")
        val width = bigInt(header, 16)
        val height = bigInt(header, 20)
        requirePack(width in 1..limits.maxCanvasSide && height in 1..limits.maxCanvasSide && width.toLong() * height <= limits.maxCanvasPixels, "PNG dimensions exceed limit")
        requirePack(expectedWidth == null || width == expectedWidth, "PNG width differs from canvas")
        requirePack(expectedHeight == null || height == expectedHeight, "PNG height differs from canvas")
        val crc = CRC32().apply { update(header, 12, 17) }.value
        requirePack(crc == bigInt(header, 29).toLong().and(0xffffffffL), "PNG header CRC mismatch")
        val depth = header[24].toInt().and(255)
        val type = header[25].toInt().and(255)
        val validDepth = when (type) {
            0 -> depth in setOf(1, 2, 4, 8, 16)
            2, 4, 6 -> depth in setOf(8, 16)
            3 -> depth in setOf(1, 2, 4, 8)
            else -> false
        }
        requirePack(validDepth && header[26] == 0.toByte() && header[27] == 0.toByte() && header[28] == 0.toByte(), "PNG must be valid and noninterlaced")
        validatePngChunks(file, width, height, depth, type)
    }

    private fun validatePngChunks(file: File, width: Int, height: Int, depth: Int, type: Int) {
        val channels = when (type) { 0, 3 -> 1; 2 -> 3; 4 -> 2; 6 -> 4; else -> throw PackException("Invalid PNG color type") }
        val rowLength = (width.toLong() * channels * depth + 7) / 8 + 1
        val expectedInflated = rowLength * height
        var inflated = 0L
        var nextFilter = 0L
        var sawPalette = false
        var sawData = false
        var dataEnded = false
        var ended = false
        val inputBuffer = ByteArray(64 * 1024)
        val outputBuffer = ByteArray(64 * 1024)
        val inflater = Inflater()
        try {
            java.io.DataInputStream(file.inputStream()).use { stream ->
                stream.readFully(ByteArray(33)) // IHDR already validated above.
                while (!ended) {
                    val length = try { stream.readInt() } catch (e: Exception) { throw PackException("Truncated PNG chunks", e) }
                    requirePack(length >= 0 && length.toLong() <= limits.maxEntryBytes, "Invalid PNG chunk length")
                    val nameBytes = ByteArray(4); stream.readFully(nameBytes)
                    requirePack(nameBytes.all { it.toInt() in 65..90 || it.toInt() in 97..122 }, "Invalid PNG chunk type")
                    val name = String(nameBytes, StandardCharsets.US_ASCII)
                    requirePack(name != "IHDR" && name !in setOf("acTL", "fcTL", "fdAT"), "Animated/duplicate-header PNG is not a frame")
                    if (nameBytes[0].toInt() in 65..90) requirePack(name in setOf("PLTE", "IDAT", "IEND"), "Unknown critical PNG chunk")
                    if (sawData && name != "IDAT") dataEnded = true
                    if (name == "PLTE") {
                        requirePack(!sawData && !sawPalette && length in 3..768 && length % 3 == 0, "Invalid PNG palette")
                        sawPalette = true
                    }
                    if (name == "IDAT") {
                        requirePack(!dataEnded && (type != 3 || sawPalette), "Invalid PNG data order")
                        sawData = true
                    }
                    val checksum = CRC32().apply { update(nameBytes) }
                    var remaining = length
                    while (remaining > 0) {
                        val count = minOf(remaining, inputBuffer.size)
                        stream.readFully(inputBuffer, 0, count)
                        checksum.update(inputBuffer, 0, count)
                        if (name == "IDAT") {
                            requirePack(!inflater.finished(), "Trailing PNG compressed data")
                            inflater.setInput(inputBuffer, 0, count)
                            while (!inflater.needsInput() && !inflater.finished()) {
                                val n = try { inflater.inflate(outputBuffer) } catch (e: Exception) { throw PackException("Invalid PNG deflate data", e) }
                                requirePack(!inflater.needsDictionary(), "PNG dictionary is not supported")
                                if (n == 0) {
                                    requirePack(inflater.needsInput() || inflater.finished(), "Stalled PNG decompression")
                                } else {
                                    requirePack(inflated + n <= expectedInflated, "PNG decoded data exceeds dimensions")
                                    while (nextFilter < inflated + n) {
                                        requirePack(outputBuffer[(nextFilter - inflated).toInt()].toInt().and(255) <= 4, "Invalid PNG row filter")
                                        nextFilter += rowLength
                                    }
                                    inflated += n
                                }
                            }
                            requirePack(!inflater.finished() || inflater.remaining == 0, "Trailing PNG deflate bytes")
                        }
                        remaining -= count
                    }
                    val actualCrc = stream.readInt().toLong().and(0xffffffffL)
                    requirePack(actualCrc == checksum.value, "PNG chunk CRC mismatch")
                    if (name == "IEND") {
                        requirePack(length == 0 && sawData && inflater.finished() && inflated == expectedInflated, "Incomplete PNG image")
                        ended = true
                        requirePack(stream.read() == -1, "Trailing bytes after PNG end")
                    }
                }
            }
        } finally { inflater.end() }
    }

    private fun readEntry(zip: ZipFile, entry: ZipEntry, max: Long): ByteArray {
        requirePack(!entry.isDirectory && entry.size in 1..max, "Invalid manifest entry size")
        val output = java.io.ByteArrayOutputStream()
        val crc = CRC32()
        val count = zip.getInputStream(entry).use { copyLimited(it, output, max, crc = crc) }
        requirePack(count == entry.size && crc.value == entry.crc, "Manifest ZIP CRC mismatch")
        return output.toByteArray()
    }

    private fun safeFile(root: File, path: String): File {
        validatePath(path, false)
        val file = File(root, path)
        var component: File? = file
        while (component != null && component != root) {
            requirePack(!Files.isSymbolicLink(component.toPath()), "Symbolic link is not allowed")
            component = component.parentFile
        }
        requirePack(file.canonicalPath.startsWith(root.canonicalPath + File.separator), "Path escapes theme directory")
        return file
    }

    private fun createParents(root: File, parent: File, created: MutableList<File>) {
        if (parent == root) return
        createParents(root, parent.parentFile, created)
        requirePack(!Files.isSymbolicLink(parent.toPath()), "Symbolic link directory")
        if (!parent.exists()) {
            requirePack(parent.mkdir(), "Cannot create asset directory")
            created += parent
        } else requirePack(parent.isDirectory, "Asset parent is not a directory")
    }

    private fun requireRegular(file: File) {
        requirePack(file.isFile && !Files.isSymbolicLink(file.toPath()), "Missing or linked asset")
    }

    /** Inspect central directory attributes unavailable through java.util.zip. */
    private fun inspectZip(file: File): Map<String, CentralEntry> = RandomAccessFile(file, "r").use { raf ->
        requirePack(raf.length() in 22..limits.maxArchiveBytes, "Invalid ZIP length")
        val tailSize = minOf(raf.length(), 65557L).toInt()
        val tail = ByteArray(tailSize)
        raf.seek(raf.length() - tailSize); raf.readFully(tail)
        val end = (tail.size - 22 downTo 0).firstOrNull {
            littleInt(tail, it) == 0x06054b50 && it + 22 + littleShort(tail, it + 20) == tail.size
        } ?: throw PackException("ZIP end directory missing")
        requirePack(littleShort(tail, end + 4) == 0 && littleShort(tail, end + 6) == 0, "Split ZIP is not an otepack")
        val count = littleShort(tail, end + 10)
        requirePack(count == littleShort(tail, end + 8) && count in 1..limits.maxEntries, "ZIP entry count exceeds limit")
        val directorySize = littleUnsignedInt(tail, end + 12)
        val directoryOffset = littleUnsignedInt(tail, end + 16)
        val endOffset = raf.length() - tailSize + end
        requirePack(directoryOffset + directorySize == endOffset && directoryOffset > 0, "ZIP64 or invalid ZIP directory")
        raf.seek(directoryOffset)
        val result = linkedMapOf<String, CentralEntry>()
        var expanded = 0L
        repeat(count) {
            val header = ByteArray(46); raf.readFully(header)
            requirePack(littleInt(header, 0) == 0x02014b50, "Invalid ZIP central header")
            val flags = littleShort(header, 8)
            val method = littleShort(header, 10)
            val compressed = littleUnsignedInt(header, 20)
            val size = littleUnsignedInt(header, 24)
            val nameSize = littleShort(header, 28)
            val extraSize = littleShort(header, 30)
            val commentSize = littleShort(header, 32)
            val disk = littleShort(header, 34)
            val external = littleUnsignedInt(header, 38)
            val local = littleUnsignedInt(header, 42)
            requirePack(flags and 1 == 0 && disk == 0 && method in setOf(0, 8), "Encrypted, split or unsupported ZIP entry")
            requirePack(nameSize in 1..1024 && local < directoryOffset, "Invalid ZIP entry offset/name")
            val nameBytes = ByteArray(nameSize); raf.readFully(nameBytes)
            val name = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(nameBytes)).toString()
            val directory = name.endsWith('/')
            validatePath(name, directory)
            requirePack(name !in result, "Duplicate ZIP entry")
            val unixHost = header[5].toInt().and(255) == 3
            val type = (external ushr 16).toInt() and 0xf000
            requirePack(!unixHost || type in setOf(0, 0x8000, 0x4000), "ZIP symbolic/special files are forbidden")
            requirePack(!unixHost || type != 0x4000 || directory, "ZIP file type mismatch")
            requirePack(directory && size == 0L || !directory && size in 1..entryLimit(name), "ZIP entry size exceeds limit")
            requirePack(compressed <= limits.maxArchiveBytes && (compressed > 0 || size == 0L), "Invalid compressed entry size")
            requirePack(size <= 65536 || size / maxOf(compressed, 1) <= limits.maxCompressionRatio, "ZIP expansion ratio exceeds limit")
            expanded += size
            requirePack(expanded <= limits.maxExpandedBytes, "Expanded pack is too large")
            val resume = raf.filePointer + extraSize + commentSize
            requirePack(resume <= endOffset, "ZIP metadata exceeds directory")
            // Cross-check each local header's name/method/flags. No data outside
            // the local-file region is accepted, even for descriptor-based ZIPs.
            raf.seek(local)
            val localHeader = ByteArray(30); raf.readFully(localHeader)
            requirePack(littleInt(localHeader, 0) == 0x04034b50, "Invalid ZIP local header")
            requirePack(littleShort(localHeader, 6) == flags && littleShort(localHeader, 8) == method, "ZIP local flags/method mismatch")
            val localNameSize = littleShort(localHeader, 26)
            val localExtraSize = littleShort(localHeader, 28)
            requirePack(localNameSize == nameBytes.size, "ZIP local name mismatch")
            val localName = ByteArray(localNameSize); raf.readFully(localName)
            requirePack(localName.contentEquals(nameBytes), "ZIP local name mismatch")
            requirePack(local + 30 + localNameSize + localExtraSize + compressed <= directoryOffset, "ZIP payload overlaps directory")
            raf.seek(resume)
            result[name] = CentralEntry(size, littleUnsignedInt(header, 16))
        }
        requirePack(raf.filePointer == endOffset, "ZIP directory size mismatch")
        result
    }

    private fun validatePath(path: String, directory: Boolean) {
        requirePack(path.isNotEmpty() && path.length <= 240 && path.toByteArray(StandardCharsets.UTF_8).size <= 1024, "Invalid ZIP path length")
        requirePack(path.none { it == '\\' || it == ':' || it.code < 32 || it.code == 127 }, "Unsafe ZIP path")
        val trimmed = if (directory) path.removeSuffix("/") else path
        requirePack(!trimmed.startsWith('/') && !trimmed.endsWith('/'), "Absolute or malformed ZIP path")
        val components = trimmed.split('/')
        requirePack(components.all { it.isNotEmpty() && it != "." && it != ".." && !it.startsWith('.') }, "Unsafe ZIP path component")
    }

    companion object {
        private const val MANIFEST = "manifest.json"
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        fun sha256(file: File): String = file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = stream.read(buffer); if (n < 0) break; if (n > 0) digest.update(buffer, 0, n) }
            digest.digest().hex()
        }
        private fun bigInt(b: ByteArray, p: Int) = java.nio.ByteBuffer.wrap(b, p, 4).int
        private fun littleInt(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8) or ((b[p+2].toInt() and 255) shl 16) or ((b[p+3].toInt() and 255) shl 24)
        private fun littleShort(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8)
        private fun littleUnsignedInt(b: ByteArray, p: Int) = littleInt(b, p).toLong() and 0xffffffffL
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun requirePack(value: Boolean, message: String) { if (!value) throw PackException(message) }
        private fun copyLimited(input: InputStream, output: OutputStream, max: Long, digest: MessageDigest? = null, crc: CRC32? = null): Long {
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                requirePack(total <= max, "Input exceeds byte limit")
                digest?.update(buffer, 0, count); crc?.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
            return total
        }
    }
    private data class CentralEntry(val size: Long, val crc: Long)
}

class PackException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

data class PackLimits(
    val maxArchiveBytes: Long = 128L * 1024 * 1024,
    val maxExpandedBytes: Long = 256L * 1024 * 1024,
    val maxEntryBytes: Long = 32L * 1024 * 1024,
    val maxManifestBytes: Long = 256L * 1024,
    val maxEntries: Int = 512,
    val maxFrames: Int = 240,
    val maxCanvasSide: Int = 4096,
    val maxCanvasPixels: Long = 16_000_000,
    val maxTotalDecodedPixels: Long = 240_000_000,
    val minFrameDurationMs: Int = 5,
    val maxFrameDurationMs: Int = 10_000,
    val maxTimelineMs: Long = 120_000,
    val maxCompressionRatio: Long = 300,
)
