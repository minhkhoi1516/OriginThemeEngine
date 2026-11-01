package dev.origin.ote.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import dev.origin.ote.model.CanvasSpec
import dev.origin.ote.model.CompatibilitySpec
import dev.origin.ote.model.FrameSpec
import dev.origin.ote.model.ThemeKind
import dev.origin.ote.model.ThemeManifest
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** These names describe original geometry, never bundled anime or store artwork. */
enum class PresetKind(val displayName: String) {
    SNOW_BLOOM("Snow Bloom"),
    WHITE_FEATHER("White Feather"),
    SAKURA_PETALS("Sakura Petals"),
    ANIME_EYE_REVEAL("Anime Eye Reveal"),
    MANGA_TRANSITION("Manga Transition"),
    CRYSTAL_BURST("Crystal Burst"),
    SOFT_PULSE("Soft Pulse"),
    AURORA("Aurora"),
    BUTTERFLY("Butterfly"),
}

/**
 * Local Canvas generators, with no source files, root commands, or network calls.
 *
 * The generated manifest uses an artwork anchor at the center of its own canvas;
 * this is not a claim about the physical coordinates of any fingerprint sensor.
 * create() owns only a newly created output directory. Repository import must
 * still validate this directory through PackCodec before committing it to Room.
 */
object ProceduralPresets {
    const val SIZE = 512
    const val FRAME_COUNT = 32
    const val FRAME_DELAY_MS = 33

    suspend fun create(directory: File, kind: PresetKind): ThemeManifest = withContext(Dispatchers.Default) {
        val parent = requireNotNull(directory.parentFile).canonicalFile
        require(parent.isDirectory && !Files.isSymbolicLink(directory.toPath())) { "preset_output_invalid" }
        require(directory.canonicalFile.parentFile == parent) { "preset_output_invalid" }
        check(directory.mkdir()) { "preset_output_exists" }
        val ownedPath = directory.canonicalFile
        try {
            val framesDirectory = File(directory, "frames")
            check(framesDirectory.mkdir()) { "preset_frames_directory_failed" }
            val frames = ArrayList<FrameSpec>(FRAME_COUNT)
            var totalBytes = 0L
            for (index in 0 until FRAME_COUNT) {
                currentCoroutineContext().ensureActive()
                val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                val relative = "frames/frame_${index.toString().padStart(4, '0')}.png"
                val output = File(directory, relative)
                try {
                    render(kind, index, FRAME_COUNT, Canvas(bitmap))
                    Files.newOutputStream(output.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "preset_png_failed" }
                    }
                } finally {
                    bitmap.recycle()
                }
                check(output.length() in 1..(8L * 1024 * 1024)) { "preset_frame_size_invalid" }
                totalBytes += output.length()
                check(totalBytes <= 128L * 1024 * 1024) { "preset_size_budget_exceeded" }
                frames += FrameSpec(relative, FRAME_DELAY_MS, sha256(output))
            }
            val manifest = ThemeManifest(
                id = UUID.randomUUID().toString(),
                title = kind.displayName,
                author = "OTE",
                kind = ThemeKind.FINGERPRINT,
                canvas = CanvasSpec(SIZE, SIZE, SIZE / 2f, SIZE / 2f),
                frames = frames,
                compatibility = CompatibilitySpec(profile = "pd2453-fingerprint-v1"),
                thumbnail = frames[FRAME_COUNT / 2].path,
                description = "Original locally generated ${kind.displayName} geometry. $FRAME_COUNT transparent PNG frames, ${SIZE}x$SIZE, ${FRAME_DELAY_MS}ms per frame. No third-party artwork. Native rendering has not been inferred from generation alone.",
            )
            Files.newOutputStream(File(directory, "manifest.json").toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                it.write(Json { encodeDefaults = true; prettyPrint = true }.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            }
            manifest
        } catch (error: Throwable) {
            // This invocation proved mkdir created the root. Do not traverse a
            // replacement symlink or delete an arbitrary caller-supplied folder.
            if (!Files.isSymbolicLink(directory.toPath()) && directory.canonicalFile == ownedPath &&
                directory.parentFile?.canonicalFile == parent) {
                directory.deleteRecursively()
            }
            throw error
        }
    }

    /** Paint one frame on a caller-owned transparent canvas; does not clear it. */
    fun render(kind: PresetKind, index: Int, count: Int, canvas: Canvas) {
        require(count in 3..240 && index in 0 until count) { "preset_frame_index_invalid" }
        require(canvas.width > 0 && canvas.height > 0) { "preset_canvas_invalid" }
        // Exact empty endpoints prevent a frozen visible last frame after a pass.
        if (index == 0 || index == count - 1) return
        val progress = index / (count - 1f)
        val alpha = envelope(progress)
        saved(canvas) {
            canvas.translate(canvas.width / 2f, canvas.height / 2f)
            val scale = min(canvas.width, canvas.height) / SIZE.toFloat()
            canvas.scale(scale, scale)
            when (kind) {
                PresetKind.SNOW_BLOOM -> snow(canvas, progress, alpha)
                PresetKind.WHITE_FEATHER -> feathers(canvas, progress, alpha)
                PresetKind.SAKURA_PETALS -> sakura(canvas, progress, alpha)
                PresetKind.ANIME_EYE_REVEAL -> eye(canvas, progress, alpha)
                PresetKind.MANGA_TRANSITION -> manga(canvas, progress, alpha)
                PresetKind.CRYSTAL_BURST -> crystal(canvas, progress, alpha)
                PresetKind.SOFT_PULSE -> pulse(canvas, progress, alpha)
                PresetKind.AURORA -> aurora(canvas, progress, alpha)
                PresetKind.BUTTERFLY -> butterflies(canvas, progress, alpha)
            }
        }
    }

    private fun snow(c: Canvas, t: Float, fade: Float) {
        glow(c, 20f + 140f * t, rgb(151, 224, 255), fade * .16f)
        for (i in 0 until 18) {
            val a = angle(i, 18) + t * .72f + sin(i * 1.7f) * .12f
            val radius = 22f + (145f + 38f * unit(i)) * ease(t)
            saved(c) {
                c.translate(cos(a) * radius, sin(a) * radius)
                c.rotate(i * 37f + t * 100f)
                val arm = 5f + 7f * unit(i + 39)
                val p = line(rgb(211, 245, 255), fade * (.5f + .5f * unit(i + 8)), 1.3f)
                repeat(6) {
                    c.drawLine(0f, 0f, 0f, -arm, p)
                    c.drawLine(0f, -arm * .6f, -arm * .24f, -arm * .82f, p)
                    c.drawLine(0f, -arm * .6f, arm * .24f, -arm * .82f, p)
                    c.rotate(60f)
                }
            }
        }
        c.drawCircle(0f, 0f, 14 + t * 185, line(rgb(177, 226, 255), fade * .6f, 1.2f))
        sparkles(c, t, fade, rgb(228, 249, 255), 22)
    }

    private fun feathers(c: Canvas, t: Float, fade: Float) {
        for (i in 0 until 7) {
            val a = angle(i, 7) + .65f * t
            val radius = 28 + 143 * ease(t)
            saved(c) {
                c.translate(cos(a) * radius, sin(a) * radius)
                c.rotate(a * 180f / PI.toFloat() + 65 + sin(t * 7 + i) * 12)
                c.scale(.75f + .35f * unit(i), .75f + .25f * unit(i + 5))
                val shape = Path().apply {
                    moveTo(0f, 39f)
                    cubicTo(-36f, 18f, -30f, -28f, 0f, -44f)
                    cubicTo(27f, -12f, 27f, 13f, 0f, 39f)
                    close()
                }
                val p = fill(rgb(241, 238, 255), fade * .92f).apply {
                    shader = LinearGradient(-25f, -35f, 25f, 35f,
                        intArrayOf(rgb(255, 255, 255), rgb(214, 209, 242), rgb(240, 249, 255)), null, Shader.TileMode.CLAMP)
                }
                c.drawPath(shape, p)
                val detail = line(rgb(135, 151, 188), fade * .7f, .8f)
                c.drawLine(0f, 35f, 0f, -39f, detail)
                for (v in -3..3) {
                    val y = v * 8f
                    c.drawLine(0f, y + 5, -17f * (1 - abs(v) * .12f), y - 6, detail)
                    c.drawLine(0f, y + 5, 17f * (1 - abs(v) * .12f), y - 6, detail)
                }
            }
        }
        sparkles(c, t, fade * .7f, rgb(248, 242, 255), 18)
    }

    private fun sakura(c: Canvas, t: Float, fade: Float) {
        glow(c, 24 + t * 145, rgb(255, 155, 199), fade * .14f)
        for (i in 0 until 16) {
            val a = angle(i, 16) + t * .8f
            val radius = 12 + (130 + 60 * unit(i)) * ease(t)
            saved(c) {
                c.translate(cos(a) * radius, sin(a) * radius + t * t * 25)
                c.rotate(i * 81f + t * (60 + i * 3))
                c.scale(.6f + unit(i + 21) * .5f, .6f + unit(i + 2) * .4f)
                val petal = Path().apply {
                    moveTo(0f, 23f)
                    cubicTo(-27f, 7f, -28f, -20f, -6f, -23f)
                    lineTo(0f, -15f); lineTo(6f, -23f)
                    cubicTo(29f, -19f, 24f, 8f, 0f, 23f); close()
                }
                c.drawPath(petal, fill(rgb(255, 191, 215), fade * (.6f + .35f * unit(i))))
                c.drawPath(petal, line(rgb(255, 226, 236), fade * .65f, .9f))
                c.drawLine(0f, 18f, 0f, -10f, line(rgb(211, 123, 168), fade * .5f, .75f))
            }
        }
        sparkles(c, t, fade * .6f, rgb(255, 221, 236), 15)
    }

    private fun eye(c: Canvas, t: Float, fade: Float) {
        val opening = sin(PI * ease(t).toDouble()).toFloat().coerceAtLeast(.05f)
        val lid = 67 * opening
        val outline = Path().apply {
            moveTo(-179f, 0f); cubicTo(-95f, -lid * 1.8f, 93f, -lid * 1.7f, 179f, 0f)
            cubicTo(85f, lid * 1.5f, -94f, lid * 1.4f, -179f, 0f); close()
        }
        saved(c) {
            c.clipPath(outline)
            c.drawPath(outline, fill(rgb(233, 246, 255), fade * .93f))
            val iris = fill(rgb(126, 178, 255), fade).apply {
                shader = RadialGradient(0f, -10f, 68f,
                    intArrayOf(rgb(17, 30, 74), rgb(101, 174, 244), rgb(185, 229, 255), rgb(40, 63, 116)),
                    floatArrayOf(0f, .45f, .78f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(0f, 0f, 65f, iris)
            repeat(24) { i ->
                val a = angle(i, 24) + t * .15f
                c.drawLine(cos(a) * 29, sin(a) * 29, cos(a) * 58, sin(a) * 58,
                    line(rgb(206, 234, 255), fade * .38f, 1.4f))
            }
            c.drawOval(RectF(-19f, -45f, 19f, 45f), fill(rgb(16, 25, 59), fade))
            c.drawOval(RectF(-31f, -41f, -3f, -17f), fill(Color.WHITE, fade * .92f))
            c.drawCircle(27f, 29f, 7f, fill(Color.WHITE, fade * .8f))
        }
        c.drawPath(outline, line(rgb(180, 205, 251), fade, 3.3f))
        c.drawPath(Path().apply { moveTo(-180f, -1f); cubicTo(-95f, -lid * 1.8f, 93f, -lid * 1.7f, 180f, -1f) },
            line(rgb(237, 242, 255), fade, 4.5f))
        c.drawLine(162f, -10f, 183f, -30f, line(rgb(222, 232, 255), fade, 2.5f))
        c.drawLine(-158f, -13f, -177f, -31f, line(rgb(222, 232, 255), fade, 2.5f))
    }

    private fun manga(c: Canvas, t: Float, fade: Float) {
        saved(c) {
            c.rotate(-12f)
            for (i in 0 until 3) {
                val lag = i * .09f
                val position = ((t - lag) / .76f).coerceIn(0f, 1f)
                val x = -250 + ease(position) * 500
                val y = -72 + i * 64f
                val panel = RectF(x - 110, y - 39, x + 110, y + 16)
                c.drawRect(panel, fill(if (i == 1) rgb(151, 166, 203) else rgb(241, 242, 255), fade * .86f))
                c.drawRect(panel, line(rgb(242, 247, 255), fade, 1.3f))
                val hatch = line(rgb(29, 33, 55), fade * .55f, 1.5f)
                for (h in 0 until 13) c.drawLine(x - 105 + h * 17f, y - 36, x - 122 + h * 17f, y + 13, hatch)
            }
        }
        for (i in 0 until 28) {
            val a = angle(i, 28)
            val inner = 135 + 20 * sin(i.toFloat())
            val outer = 190 + 37 * unit(i + 7)
            val length = .45f + .55f * sin(PI * t).toFloat()
            c.drawLine(cos(a) * inner, sin(a) * inner, cos(a) * (inner + (outer - inner) * length),
                sin(a) * (inner + (outer - inner) * length), line(rgb(231, 238, 255), fade * .6f, 1.3f))
        }
    }

    private fun crystal(c: Canvas, t: Float, fade: Float) {
        glow(c, 30 + t * 145, rgb(156, 205, 255), fade * .18f)
        for (i in 0 until 12) {
            val a = angle(i, 12) + .13f * sin(t * 4)
            val radius = 12 + (110 + 55 * unit(i + 20)) * ease(t)
            saved(c) {
                c.translate(cos(a) * radius, sin(a) * radius)
                c.rotate(a * 180f / PI.toFloat() + 90)
                val length = 28 + unit(i + 12) * 28
                val shape = Path().apply {
                    moveTo(0f, -length); lineTo(15f, -5f); lineTo(9f, 23f)
                    lineTo(-8f, 20f); lineTo(-16f, -5f); close()
                }
                c.drawPath(shape, fill(rgb(178, 218, 255), fade * .55f))
                c.drawPath(Path().apply { moveTo(0f, -length); lineTo(0f, 16f); lineTo(15f, -5f); close() },
                    fill(rgb(245, 251, 255), fade * .75f))
                c.drawPath(shape, line(rgb(221, 240, 255), fade, 1.2f))
                c.drawLine(-16f, -5f, 15f, -5f, line(rgb(116, 157, 229), fade * .8f, 1f))
            }
        }
        sparkles(c, t, fade, rgb(220, 244, 255), 24)
    }

    private fun pulse(c: Canvas, t: Float, fade: Float) {
        for (i in 0 until 4) {
            val phase = ((t * 1.1f - i * .13f) / .8f).coerceIn(0f, 1f)
            val radius = 21 + ease(phase) * 185
            val strength = fade * (1 - phase) * (.8f - i * .12f)
            if (phase > 0) {
                c.drawCircle(0f, 0f, radius, line(rgb(158, 225, 255), strength, 2.7f - i * .4f))
                c.drawCircle(0f, 0f, radius + 5, line(rgb(224, 239, 255), strength * .3f, .9f))
            }
        }
        glow(c, 30 + 45 * sin(PI * t).toFloat(), rgb(181, 218, 255), fade * .25f)
    }

    private fun aurora(c: Canvas, t: Float, fade: Float) {
        saved(c) {
            c.rotate(-23f + t * 22)
            for (band in 0 until 4) {
                val offset = (band - 1.5f) * 27
                val wave = sin(t * 5 + band).toFloat() * 35
                val ribbon = Path().apply {
                    moveTo(-218f, offset + wave)
                    cubicTo(-106f, -125 + offset - wave, 82f, 131 + offset + wave, 220f, offset - wave)
                    lineTo(220f, offset - wave + 16)
                    cubicTo(77f, 149 + offset + wave, -104f, -106 + offset - wave, -218f, offset + wave + 16)
                    close()
                }
                val p = fill(rgb(126, 246, 229), fade * (.38f + band * .06f)).apply {
                    shader = LinearGradient(-220f, 0f, 220f, 0f,
                        intArrayOf(rgb(170, 144, 255), rgb(123, 224, 248), rgb(161, 255, 202)), null, Shader.TileMode.CLAMP)
                }
                c.drawPath(ribbon, p)
            }
        }
        sparkles(c, t, fade * .75f, rgb(218, 243, 255), 24)
    }

    private fun butterflies(c: Canvas, t: Float, fade: Float) {
        for (i in 0 until 8) {
            val a = angle(i, 8) + t * .45f
            val radius = 24 + (128 + unit(i + 10) * 34) * ease(t)
            val flap = .23f + .77f * abs(sin(t * 17 + i * 1.3f))
            saved(c) {
                c.translate(cos(a) * radius, sin(a) * radius)
                c.rotate(a * 180f / PI.toFloat() + 90)
                c.scale(.7f + .35f * unit(i + 13), .7f + .35f * unit(i + 13))
                for (side in intArrayOf(-1, 1)) {
                    saved(c) {
                        c.scale(side * flap, 1f)
                        val wings = Path().apply {
                            moveTo(0f, 0f); cubicTo(11f, -38f, 40f, -37f, 31f, -5f)
                            cubicTo(54f, 21f, 21f, 33f, 0f, 7f); close()
                        }
                        c.drawPath(wings, fill(if (i % 2 == 0) rgb(218, 208, 255) else rgb(190, 231, 255), fade * .82f))
                        c.drawPath(wings, line(rgb(251, 246, 255), fade, 1f))
                        c.drawLine(2f, 2f, 27f, -19f, line(rgb(141, 139, 193), fade * .6f, .7f))
                        c.drawLine(2f, 5f, 26f, 14f, line(rgb(141, 139, 193), fade * .6f, .7f))
                    }
                }
                c.drawOval(RectF(-2f, -17f, 2f, 18f), fill(rgb(246, 243, 255), fade))
                c.drawLine(-1f, -14f, -7f, -23f, line(rgb(238, 232, 255), fade, .9f))
                c.drawLine(1f, -14f, 7f, -23f, line(rgb(238, 232, 255), fade, .9f))
            }
        }
        sparkles(c, t, fade * .55f, rgb(236, 226, 255), 13)
    }

    private fun sparkles(c: Canvas, t: Float, fade: Float, color: Int, count: Int) {
        for (i in 0 until count) {
            val a = angle(i, count) + t * .42f + unit(i + 100) * .3f
            val radius = 27 + ease(t) * (95 + unit(i + 33) * 108)
            val intensity = fade * (.35f + .65f * abs(sin(i * 2.17f + t * 9)))
            val x = cos(a) * radius
            val y = sin(a) * radius
            val size = 1.2f + unit(i + 11) * 1.8f
            val p = line(color, intensity, .8f)
            c.drawLine(x - size, y, x + size, y, p)
            c.drawLine(x, y - size, x, y + size, p)
        }
    }

    private fun glow(c: Canvas, radius: Float, color: Int, alpha: Float) {
        val p = fill(color, alpha).apply {
            shader = RadialGradient(0f, 0f, radius,
                intArrayOf(color, Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(0f, 0f, radius, p)
    }

    private fun fill(color: Int, alpha: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        style = Paint.Style.FILL
    }
    private fun line(color: Int, alpha: Float, width: Float) = fill(color, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)
    private fun angle(index: Int, count: Int): Float = (index * 2.0 * PI / count).toFloat()
    private fun ease(value: Float): Float = value.coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) * (1 - it) }
    private fun envelope(t: Float): Float {
        val enter = (t / .16f).coerceIn(0f, 1f)
        val exit = ((1 - t) / .25f).coerceIn(0f, 1f)
        return enter * enter * (3 - 2 * enter) * exit * exit * (3 - 2 * exit)
    }
    private fun unit(seed: Int): Float {
        val mixed = (seed.toLong() * 1_103_515_245L + 12_345L) and 0x7fffffff
        return mixed / 2_147_483_647f
    }
    private inline fun saved(canvas: Canvas, block: () -> Unit) {
        val checkpoint = canvas.save()
        try { block() } finally { canvas.restoreToCount(checkpoint) }
    }
    private fun sha256(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val size = stream.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
