package dev.origin.ote.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateFilesTest {
    @Test fun rejectsUntrustedArchivePaths() {
        listOf("../frame.png", "/frame.png", "C:/frame.png", "frames\\file.png",
            "frames/../file.png", "frames//file.png", "frames/./file.png", "a\nfile.png", "a\u0000.png")
            .forEach { path -> assertThrows(IllegalArgumentException::class.java) { PrivateFiles.safeRelative(path) } }
        assertEquals("frames/001.png", PrivateFiles.safeRelative("frames/001.png"))
    }

    @Test fun boundedCopyDoesNotWriteExcessInput() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            PrivateFiles.copyBounded(ByteArrayInputStream(ByteArray(100)), output, 32)
        }
        assertEquals(0, output.size())
        val exact = ByteArrayOutputStream()
        assertEquals(32L, PrivateFiles.copyBounded(ByteArrayInputStream(ByteArray(32)), exact, 32))
        assertEquals(32, exact.size())
    }

    @Test fun copyHonorsCancellationBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            PrivateFiles.copyBounded(ByteArrayInputStream(ByteArray(32)), output, 32) { throw CancellationException("cancel") }
        }
        assertEquals(0, output.size())
    }

    @Test fun privateCleanupDoesNotFollowSymlinkTargets() {
        val root = Files.createTempDirectory("ote-cleanup-test").toFile()
        try {
            val outside = root.resolve("outside").apply { mkdir() }
            val keep = outside.resolve("keep.txt").apply { writeText("keep") }
            val owned = root.resolve("owned").apply { mkdir() }
            Files.createSymbolicLink(owned.resolve("link").toPath(), outside.toPath())
            PrivateFiles.removeOwnedTree(owned, root)
            assertFalse(owned.exists())
            assertTrue(keep.exists())
            assertThrows(IllegalArgumentException::class.java) { PrivateFiles.removeOwnedTree(outside, owned) }
        } finally { root.deleteRecursively() }
    }

    @Test fun checksumMatchesKnownSha256() {
        val root = Files.createTempDirectory("ote-hash-test").toFile()
        try {
            val file = root.resolve("sample").apply { writeText("abc") }
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PrivateFiles.sha256(file))
        } finally { root.deleteRecursively() }
    }
}
