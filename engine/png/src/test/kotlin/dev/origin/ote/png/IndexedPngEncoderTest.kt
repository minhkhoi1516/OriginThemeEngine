package dev.origin.ote.png

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.Random
import java.util.zip.CRC32
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexedPngEncoderTest {
    @Test fun `actual indexed PNG decodes with transparency and exact simple colors`() {
        val pixels = intArrayOf(0x00abcdef, 0xffff0000.toInt(), 0xff00ff00.toInt(),
            0xff0000ff.toInt(), 0x80123456.toInt(), 0x01112233)
        val bytes = encoded(3, 2, pixels)
        val chunks = parseChunks(bytes)
        val ihdr = chunks.first { it.first == "IHDR" }.second
        assertEquals(8, ihdr[8].toInt())
        assertEquals(3, ihdr[9].toInt())
        assertEquals(768, chunks.single { it.first == "PLTE" }.second.size)
        assertEquals(256, chunks.single { it.first == "tRNS" }.second.size)
        assertEquals(0, chunks.single { it.first == "tRNS" }.second[0].toInt())
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(3, image.width); assertEquals(2, image.height)
        for (i in pixels.indices) {
            val expected = if (pixels[i] ushr 24 == 0) 0 else pixels[i]
            assertEquals(expected, image.getRGB(i % 3, i / 3))
        }
    }

    @Test fun `fully transparent frame remains transparent`() {
        val bytes = encoded(64, 32, IntArray(64 * 32) { 0x00ffffff })
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        for (y in 0 until image.height) for (x in 0 until image.width) {
            assertEquals(0, image.getRGB(x, y) ushr 24)
        }
        parseChunks(bytes)
    }

    @Test fun `large indexed stream has valid multiple IDAT chunks and opaque decode`() {
        val random = Random(7218)
        val pixels = IntArray(512 * 256) {
            val index = random.nextInt(256)
            val red = (index ushr 4) * 17
            val green = (index and 15) * 17
            val blue = (red + green) and 255
            (255 shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val bytes = encoded(512, 256, pixels)
        val chunks = parseChunks(bytes)
        assertTrue(chunks.count { it.first == "IDAT" } > 1)
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        val colors = HashSet<Int>()
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = image.getRGB(x, y)
            assertEquals(255, color ushr 24)
            colors += color
        }
        assertTrue(colors.size in 200..255)
    }

    @Test fun `encoding is deterministic and does not close destination`() {
        val pixels = IntArray(80 * 80) { i ->
            val alpha = (i * 7) and 255
            (alpha shl 24) or (((i * 11) and 255) shl 16) or
                (((i * 13) and 255) shl 8) or ((i * 17) and 255)
        }
        val stream = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        IndexedPngEncoder.encode(80, 80, pixels, stream)
        assertFalse(stream.closed)
        assertContentEquals(stream.toByteArray(), encoded(80, 80, pixels))
        stream.write(42)
        assertEquals(42, stream.toByteArray().last().toInt())
    }

    @Test fun `invalid dimensions and cancellation are rejected`() {
        assertFailsWith<IllegalArgumentException> { encoded(0, 1, IntArray(0)) }
        assertFailsWith<IllegalArgumentException> { encoded(4097, 1, IntArray(4097)) }
        assertFailsWith<IllegalArgumentException> { encoded(2, 2, IntArray(3)) }
        var checks = 0
        assertFailsWith<InterruptedException> {
            IndexedPngEncoder.encode(512, 128, IntArray(512 * 128) { 0xffabcdef.toInt() }, ByteArrayOutputStream()) {
                checks++
                if (checks == 8) throw InterruptedException("cancel fixture")
            }
        }
        assertEquals(8, checks)
    }

    private fun encoded(width: Int, height: Int, pixels: IntArray): ByteArray =
        ByteArrayOutputStream().also { IndexedPngEncoder.encode(width, height, pixels, it) }.toByteArray()

    /** Checks CRCs and framing independently, while ImageIO checks PNG interoperability. */
    private fun parseChunks(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(8).also(input::readFully)
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), magic)
        val chunks = ArrayList<Pair<String, ByteArray>>()
        while (input.available() > 0) {
            val size = input.readInt()
            assertTrue(size in 0..32_768 || size == 768 || size == 256)
            val typeBytes = ByteArray(4).also(input::readFully)
            val type = typeBytes.toString(Charsets.US_ASCII)
            val payload = ByteArray(size).also(input::readFully)
            val recorded = input.readInt().toLong() and 0xffff_ffffL
            val calculated = CRC32().apply { update(typeBytes); update(payload) }.value
            assertEquals(recorded, calculated)
            chunks += type to payload
        }
        assertEquals("IHDR", chunks.first().first)
        assertEquals("IEND", chunks.last().first)
        return chunks
    }
}
