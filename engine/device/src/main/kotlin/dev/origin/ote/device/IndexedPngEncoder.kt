package dev.origin.ote.device

import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Writes actual PNG color type 3, depth 8, PLTE and tRNS.
 *
 * Android Bitmap.compress(PNG) does not promise indexed PNG. The researched
 * native fingerprint decoder has a palette path, so we encode it explicitly.
 * Palette construction is deterministic and lossy: the most common ARGB4444
 * buckets get weighted mean colors, then remaining buckets map to their nearest
 * alpha-aware, premultiplied color. Index 0 is always fully transparent.
 *
 * No Android types are used here; the encoder can be tested on the JVM.
 */
object IndexedPngEncoder {
    const val MAX_PIXELS: Long = 16_000_000L
    private const val BUCKETS = 65_536
    private const val IDAT_CHUNK_SIZE = 32_768

    fun encode(
        width: Int,
        height: Int,
        argb: IntArray,
        output: OutputStream,
        checkCancelled: () -> Unit = {},
    ) {
        require(width in 1..4096 && height in 1..4096) { "png_dimensions_out_of_range" }
        val pixels = width.toLong() * height
        require(pixels <= MAX_PIXELS && pixels == argb.size.toLong()) { "png_pixel_count_invalid" }
        checkCancelled()

        val histogram = IntArray(BUCKETS)
        val alphaSum = LongArray(BUCKETS)
        val redSum = LongArray(BUCKETS)
        val greenSum = LongArray(BUCKETS)
        val blueSum = LongArray(BUCKETS)
        for (i in argb.indices) {
            if ((i and 16_383) == 0) checkCancelled()
            val value = argb[i]
            val alpha = value ushr 24
            if (alpha == 0) continue
            val bucket = bucket(value)
            histogram[bucket]++
            alphaSum[bucket] += alpha.toLong()
            redSum[bucket] += ((value ushr 16) and 255).toLong()
            greenSum[bucket] += ((value ushr 8) and 255).toLong()
            blueSum[bucket] += (value and 255).toLong()
        }
        val occupied = histogram.indices.filter { histogram[it] != 0 }
        val ranked = occupied.sortedWith(compareByDescending<Int> { histogram[it] }.thenBy { it })
        val selected = ranked.take(255)
        val palette = IntArray(256)
        val paletteBytes = ByteArray(256 * 3)
        val transparency = ByteArray(256)
        val map = IntArray(BUCKETS) { -1 }
        fun mean(sum: Long, count: Int): Int = ((sum + count / 2) / count).toInt()
        fun color(id: Int): Int {
            val count = histogram[id]
            return (mean(alphaSum[id], count) shl 24) or
                (mean(redSum[id], count) shl 16) or
                (mean(greenSum[id], count) shl 8) or mean(blueSum[id], count)
        }
        for ((i, id) in selected.withIndex()) {
            val index = i + 1
            val value = color(id)
            palette[index] = value
            paletteBytes[index * 3] = (value ushr 16).toByte()
            paletteBytes[index * 3 + 1] = (value ushr 8).toByte()
            paletteBytes[index * 3 + 2] = value.toByte()
            transparency[index] = (value ushr 24).toByte()
            map[id] = index
        }
        val premultiplied = Array(selected.size + 1) { index -> components(palette[index]) }
        for ((position, id) in occupied.withIndex()) {
            if ((position and 255) == 0) checkCancelled()
            if (map[id] >= 0) continue
            val candidate = components(color(id))
            var bestIndex = 0
            var bestDistance = Int.MAX_VALUE
            for (index in premultiplied.indices) {
                val existing = premultiplied[index]
                val da = candidate[0] - existing[0]
                val dr = candidate[1] - existing[1]
                val dg = candidate[2] - existing[2]
                val db = candidate[3] - existing[3]
                val distance = 2 * da * da + dr * dr + dg * dg + db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            map[id] = bestIndex
        }

        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        val header = java.io.ByteArrayOutputStream(13)
        DataOutputStream(header).apply {
            writeInt(width); writeInt(height)
            writeByte(8); writeByte(3); writeByte(0); writeByte(0); writeByte(0)
        }
        writeChunk(output, "IHDR", header.toByteArray())
        writeChunk(output, "PLTE", paletteBytes)
        writeChunk(output, "tRNS", transparency)
        val sink = IdatSink(output)
        val deflater = Deflater(6)
        try {
            val compressed = DeflaterOutputStream(sink, deflater, IDAT_CHUNK_SIZE)
            val row = ByteArray(width + 1) // PNG filter None (0), followed by indices.
            for (y in 0 until height) {
                checkCancelled()
                for (x in 0 until width) {
                    val value = argb[y * width + x]
                    row[x + 1] = if (value ushr 24 == 0) 0.toByte() else map[bucket(value)].toByte()
                }
                compressed.write(row)
            }
            compressed.finish()
            sink.finish()
        } finally {
            deflater.end()
        }
        writeChunk(output, "IEND", ByteArray(0))
    }

    private fun bucket(value: Int): Int =
        ((value ushr 28) shl 12) or (((value ushr 20) and 15) shl 8) or
            (((value ushr 12) and 15) shl 4) or ((value ushr 4) and 15)

    private fun components(color: Int): IntArray {
        val alpha = color ushr 24
        return intArrayOf(alpha, ((color ushr 16) and 255) * alpha / 255,
            ((color ushr 8) and 255) * alpha / 255, (color and 255) * alpha / 255)
    }

    private fun writeChunk(output: OutputStream, type: String, bytes: ByteArray, length: Int = bytes.size) {
        val kind = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(kind); update(bytes, 0, length) }
        DataOutputStream(output).apply {
            writeInt(length); write(kind); write(bytes, 0, length); writeInt(crc.value.toInt())
        }
    }

    private class IdatSink(private val output: OutputStream) : OutputStream() {
        private val buffer = ByteArray(IDAT_CHUNK_SIZE)
        private var used = 0
        override fun write(value: Int) {
            buffer[used++] = value.toByte()
            if (used == buffer.size) finish()
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var pos = offset
            var remaining = length
            while (remaining > 0) {
                val amount = minOf(remaining, buffer.size - used)
                bytes.copyInto(buffer, used, pos, pos + amount)
                used += amount; pos += amount; remaining -= amount
                if (used == buffer.size) finish()
            }
        }
        fun finish() {
            if (used > 0) {
                IndexedPngEncoder.writeChunk(output, "IDAT", buffer, used)
                used = 0
            }
        }
        override fun flush() = finish()
        override fun close() = finish() // Never closes the caller's ZIP entry/stream.
    }
}
