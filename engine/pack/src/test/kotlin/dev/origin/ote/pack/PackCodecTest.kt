package dev.origin.ote.pack

import dev.origin.ote.model.CanvasSpec
import dev.origin.ote.model.FrameSpec
import dev.origin.ote.model.ThemeKind
import dev.origin.ote.model.ThemeManifest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PackCodecTest {
    private val scratch = Files.createTempDirectory("ote-pack-test-").toFile()
    private val codec = PackCodec()
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    private val frame = png()
    private val manifest = ThemeManifest(
        id = "snow-1", title = "Snow", kind = ThemeKind.FINGERPRINT,
        canvas = CanvasSpec(1, 1, .5f, .5f),
        frames = listOf(FrameSpec("frames/a.png", 33, sha(frame))),
    )

    @After fun cleanup() { scratch.deleteRecursively() }

    @Test fun validPackImportsAndExportsWithoutClosingCallerStreams() {
        val bytes = archive()
        val input = object : ByteArrayInputStream(bytes) {
            var closedByCodec = false
            override fun close() { closedByCodec = true; super.close() }
        }
        val imported = codec.importPack(input, File(scratch, "first"))
        assertFalse(input.closedByCodec)
        assertEquals(sha(bytes), imported.packSha256)
        assertEquals(manifest, imported.manifest)
        assertArrayEquals(frame, File(imported.directory, "frames/a.png").readBytes())
        assertFalse(File(imported.directory, ".ote-import.zip").exists())
        val exported = object : ByteArrayOutputStream() {
            var closedByCodec = false
            override fun close() { closedByCodec = true; super.close() }
        }
        codec.exportPack(imported.directory, exported)
        assertFalse(exported.closedByCodec)
        val again = codec.importPack(ByteArrayInputStream(exported.toByteArray()), File(scratch, "second"))
        assertEquals(manifest, again.manifest)
        assertEquals(manifest, codec.validateDirectory(again.directory))
    }

    @Test fun exportIsDeterministic() {
        val imported = codec.importPack(ByteArrayInputStream(archive()), File(scratch, "item"))
        fun export() = ByteArrayOutputStream().also { codec.exportPack(imported.directory, it) }.toByteArray()
        assertArrayEquals(export(), export())
    }

    @Test fun rejectsParentTraversalAndCleansOnlyCreatedDestination() {
        val outside = File(scratch, "untouched.txt").apply { writeText("keep") }
        rejects(archive(extra = mapOf("../untouched.txt" to byteArrayOf(1))))
        assertEquals("keep", outside.readText())
    }

    @Test fun rejectsAbsoluteWindowsAndBackslashPaths() {
        listOf("/tmp/oops", "C:/oops", "frames\\a.png", "a//b.png", "a/./b.png", "a/../b.png", ".private/x.png").forEachIndexed { i, path ->
            rejects(archive(extra = mapOf(path to byteArrayOf(1))), "reject-$i")
        }
    }

    @Test fun rejectsUndeclaredScriptEvenWithSafeFilename() {
        rejects(archive(extra = mapOf("install.sh" to "echo unsafe".toByteArray())))
    }

    @Test fun requiresExplicitSupportedSchemaVersion() {
        val text = json.encodeToString(manifest)
        rejects(archive(manifestText = text.replace("\"schemaVersion\":1,", "")), "missing")
        rejects(archive(manifestText = text.replace("\"schemaVersion\":1", "\"schemaVersion\":2")), "future")
        rejects(archive(manifestText = text.dropLast(1) + ",\"execute\":\"anything\"}"), "unknown")
    }

    @Test fun duplicateJsonKeysIncludingEscapedKeysAreRejected() {
        val normal = json.encodeToString(manifest)
        rejects(archive(manifestText = normal.replaceFirst("{", "{\"title\":\"ambiguous\",")), "duplicate")
        rejects(archive(manifestText = normal.replaceFirst("{", "{\"\\u0074itle\":\"ambiguous\",")), "escaped")
    }

    @Test fun rejectsExcessiveJsonNesting() {
        rejects(archive(manifestText = "[".repeat(40) + "0" + "]".repeat(40)))
    }

    @Test fun rejectsHashMismatchAndDimensionMismatch() {
        rejects(archive(manifest.copy(frames = listOf(manifest.frames.first().copy(sha256 = "0".repeat(64))))), "hash")
        rejects(archive(manifest.copy(canvas = CanvasSpec(2, 1))), "size")
    }

    @Test fun rejectsMissingFrameAndThumbnail() {
        rejects(zip(mapOf("manifest.json" to json.encodeToString(manifest).toByteArray())), "frame")
        rejects(archive(manifest.copy(thumbnail = "thumbnail.png")), "thumbnail")
    }

    @Test fun rejectsZeroDurationAndFrameCountBudget() {
        rejects(archive(manifest.copy(frames = listOf(manifest.frames.first().copy(durationMs = 0)))), "zero")
        val many = (0..240).map { manifest.frames.first().copy(path = "frames/$it.png") }
        rejects(archive(manifest.copy(frames = many)), "count")
    }

    @Test fun rejectsDecodedPixelBudgetBeforeExtractingFrames() {
        val large = manifest.copy(canvas = CanvasSpec(4000, 4000), frames = (0 until 16).map {
            manifest.frames.first().copy(path = "frames/$it.png")
        })
        rejects(archive(large))
    }

    @Test fun compressedAndExpandedLimitsAreEnforced() {
        rejects(archive(), "compressed", PackCodec(PackLimits(maxArchiveBytes = 32)))
        rejects(archive(), "expanded", PackCodec(PackLimits(maxExpandedBytes = 64)))
        rejects(archive(), "manifest", PackCodec(PackLimits(maxManifestBytes = 32)))
    }

    @Test fun rejectsDuplicateZipEntriesEvenIfTheirLocalNamesMatch() {
        val original = archive(extra = mapOf("frames/b.png" to frame))
        val renamed = replaceBytes(original, "frames/b.png".toByteArray(), "frames/a.png".toByteArray())
        rejects(renamed)
    }

    @Test fun rejectsUnixSymlinkAttributes() {
        val bytes = archive().clone()
        val header = centralOffsets(bytes).last()
        bytes[header + 5] = 3 // Unix creator.
        putLittleInt(bytes, header + 38, 0xa1ff0000.toInt())
        rejects(bytes)
    }

    @Test fun rejectsEncryptedEntryFlags() {
        val bytes = archive().clone()
        val central = centralOffsets(bytes).last()
        val local = littleInt(bytes, central + 42)
        bytes[central + 8] = (bytes[central + 8].toInt() or 1).toByte()
        bytes[local + 6] = (bytes[local + 6].toInt() or 1).toByte()
        rejects(bytes)
    }

    @Test fun verifiesZipCrcRatherThanTrustingCentralMetadata() {
        val bytes = archive().clone()
        val central = centralOffsets(bytes).last()
        putLittleInt(bytes, central + 16, littleInt(bytes, central + 16) xor 1)
        rejects(bytes)
    }

    @Test fun rejectsPngDataBombEvenWithCorrectDeclaredHashAndChunkCrc() {
        val bomb = png(raw = ByteArray(50) { 0 })
        val forged = manifest.copy(frames = listOf(manifest.frames.first().copy(sha256 = sha(bomb))))
        rejects(archive(forged, frameBytes = bomb))
    }

    @Test fun rejectsInvalidPngRowFilterDespiteValidHashAndCrc() {
        val wrong = png(raw = byteArrayOf(5, 1, 2, 3, 4))
        val forged = manifest.copy(frames = listOf(manifest.frames.first().copy(sha256 = sha(wrong))))
        rejects(archive(forged, frameBytes = wrong))
    }

    @Test fun rejectsTruncatedPngAndBadChunkCrc() {
        val wrong = frame.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val forged = manifest.copy(frames = listOf(manifest.frames.first().copy(sha256 = sha(wrong))))
        rejects(archive(forged, frameBytes = wrong), "crc")
        val short = frame.copyOf(40)
        rejects(archive(manifest.copy(frames = listOf(manifest.frames.first().copy(sha256 = sha(short)))), frameBytes = short), "truncated")
    }

    @Test fun refusesExistingDestinationWithoutRemovingIt() {
        val existing = File(scratch, "existing").apply { mkdir() }
        val marker = File(existing, "important.txt").apply { writeText("keep") }
        assertThrows(PackException::class.java) { codec.importPack(ByteArrayInputStream(archive()), existing) }
        assertEquals("keep", marker.readText())
    }

    @Test fun refusesDestinationSymlinkWithoutFollowingIt() {
        val real = File(scratch, "real").apply { mkdir() }
        val link = File(scratch, "link")
        Files.createSymbolicLink(link.toPath(), real.toPath())
        assertThrows(PackException::class.java) { codec.importPack(ByteArrayInputStream(archive()), link) }
        assertTrue(real.isDirectory)
        assertTrue(Files.isSymbolicLink(link.toPath()))
    }

    @Test fun freshValidationAndExportRejectChangedAsset() {
        val item = codec.importPack(ByteArrayInputStream(archive()), File(scratch, "item"))
        File(item.directory, "frames/a.png").writeBytes(png(raw = byteArrayOf(0, 0, 0, 0, 0)))
        assertThrows(PackException::class.java) { codec.validateDirectory(item.directory) }
        assertThrows(PackException::class.java) { codec.exportPack(item.directory, ByteArrayOutputStream()) }
    }

    @Test fun freshValidationRejectsAssetReplacedWithSymlink() {
        val item = codec.importPack(ByteArrayInputStream(archive()), File(scratch, "item"))
        val target = File(scratch, "elsewhere.png").apply { writeBytes(frame) }
        val asset = File(item.directory, "frames/a.png").apply { delete() }
        Files.createSymbolicLink(asset.toPath(), target.toPath())
        assertThrows(PackException::class.java) { codec.validateDirectory(item.directory) }
    }

    private fun rejects(bytes: ByteArray, name: String = "rejected", parser: PackCodec = codec) {
        val destination = File(scratch, name)
        assertThrows(PackException::class.java) { parser.importPack(ByteArrayInputStream(bytes), destination) }
        assertFalse("Failed import should clean its own newly created destination", destination.exists())
    }

    private fun archive(m: ThemeManifest = manifest, manifestText: String = json.encodeToString(m), extra: Map<String, ByteArray> = emptyMap(), frameBytes: ByteArray = frame): ByteArray =
        zip(linkedMapOf("manifest.json" to manifestText.toByteArray(), "frames/a.png" to frameBytes) + extra)

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { buffer ->
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun sha(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun png(raw: ByteArray = byteArrayOf(0, 255.toByte(), 0, 0, 255.toByte())): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(name: String, data: ByteArray) {
            val type = name.toByteArray()
            val stream = DataOutputStream(output)
            stream.writeInt(data.size); stream.write(type); stream.write(data)
            stream.writeInt(CRC32().apply { update(type); update(data) }.value.toInt())
        }
        val header = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).apply { writeInt(1); writeInt(1); write(byteArrayOf(8, 6, 0, 0, 0)) }
        }.toByteArray()
        chunk("IHDR", header)
        val compressed = ByteArrayOutputStream().also { buffer -> DeflaterOutputStream(buffer).use { it.write(raw) } }.toByteArray()
        chunk("IDAT", compressed); chunk("IEND", ByteArray(0))
        return output.toByteArray()
    }

    private fun centralOffsets(bytes: ByteArray): List<Int> = (0..bytes.size-4).filter { littleInt(bytes, it) == 0x02014b50 }
    private fun littleInt(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8) or ((b[p+2].toInt() and 255) shl 16) or ((b[p+3].toInt() and 255) shl 24)
    private fun putLittleInt(b: ByteArray, p: Int, value: Int) { for (i in 0..3) b[p+i] = (value ushr (i*8)).toByte() }
    private fun replaceBytes(bytes: ByteArray, old: ByteArray, replacement: ByteArray): ByteArray {
        require(old.size == replacement.size)
        return bytes.clone().also { result ->
            for (offset in 0..bytes.size-old.size) if (bytes.copyOfRange(offset, offset+old.size).contentEquals(old)) replacement.copyInto(result, offset)
        }
    }
}
