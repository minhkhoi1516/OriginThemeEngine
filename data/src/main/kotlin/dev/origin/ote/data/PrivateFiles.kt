package dev.origin.ote.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest

internal object PrivateFiles {
    fun safeRelative(path: String): String {
        require(path.isNotEmpty() && path.length <= 512) { "Invalid archive path" }
        require(!path.startsWith('/') && !path.contains('\\') && !path.contains(':') && path.none(Char::isISOControl)) {
            "Unsafe archive path"
        }
        val segments = path.trimEnd('/').split('/')
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) { "Unsafe archive segments" }
        return path
    }

    fun copyBounded(input: InputStream, output: OutputStream, limit: Long, cancel: () -> Unit = {}): Long {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            cancel()
            val n = input.read(buffer)
            if (n < 0) return total
            total += n
            require(total <= limit) { "Input exceeds size limit" }
            output.write(buffer, 0, n)
        }
    }

    fun sha256(file: File): String = file.inputStream().use { input ->
        val hash = MessageDigest.getInstance("SHA-256")
        val data = ByteArray(32 * 1024)
        while (true) {
            val n = input.read(data)
            if (n < 0) break
            hash.update(data, 0, n)
        }
        hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Only call on a newly created, direct child of the passed app-private root. */
    fun removeOwnedTree(directory: File, root: File) {
        require(directory.parentFile?.canonicalFile == root.canonicalFile) { "Cleanup outside private root" }
        fun remove(file: File) {
            if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory) {
                require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
                file.listFiles()?.forEach(::remove)
            }
            if (file.exists() || Files.isSymbolicLink(file.toPath())) {
                check(file.delete()) { "Private-file cleanup failed" }
            }
        }
        remove(directory)
    }
}
