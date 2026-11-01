package dev.origin.ote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.origin.ote.model.*
import dev.origin.ote.pack.PackCodec
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android graphics + content IO integration; does not request SU or alter device resources. */
@RunWith(AndroidJUnit4::class)
class StudioExporterTest {
    @Test
    fun recenterMovesBothPixelsAndDeclaredAnchorAndExportsValidPack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "studio-test-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val source = File(root, "source").apply { check(mkdir()) }
            val png = File(source, "frame.png")
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.setPixel(12, 8, Color.RED)
            png.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val hash = MessageDigest.getInstance("SHA-256").digest(png.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val manifest = ThemeManifest(id = "studio-test", title = "Test", kind = ThemeKind.FINGERPRINT,
                canvas = CanvasSpec(16, 16, 12f, 8f), frames = listOf(FrameSpec("frame.png", 33, hash)))
            val item = ThemeItem("test", "Test", "", ThemeKind.FINGERPRINT, source.path, false, 0L, manifest)
            val output = File(root, "result.otepack")
            val codec = PackCodec()
            StudioExporter(context, codec).export(item, StudioTransform(x = -.25f), Uri.fromFile(output))
            val imported = output.inputStream().use { codec.importPack(it, File(root, "imported")) }
            assertEquals(8f, imported.manifest.canvas.anchorX!!, .001f)
            assertEquals(8f, imported.manifest.canvas.anchorY!!, .001f)
            val rendered = BitmapFactory.decodeFile(File(imported.directory, imported.manifest.frames.single().path).path)
            try {
                assertEquals(Color.RED, rendered.getPixel(8, 8))
                assertEquals(0, Color.alpha(rendered.getPixel(12, 8)))
                assertEquals(33, imported.manifest.frames.single().durationMs)
            } finally { rendered.recycle() }
        } finally { root.deleteRecursively() }
    }
    @Test
    fun resizeTrimVariableDelayAndKeyframesBakeActualPixels() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"studio-resize-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val source=File(root,"source").apply { check(mkdir()) }
            val frames=listOf(Color.RED,Color.GREEN,Color.BLUE).mapIndexed { index,color ->
                val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                val file=File(source,"frame$index.png")
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
                FrameSpec(file.name,33,MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
            }
            val manifest=ThemeManifest(id="resize-test",title="Resize",kind=ThemeKind.FINGERPRINT,canvas=CanvasSpec(16,16),frames=frames)
            val item=ThemeItem("test","Resize","",ThemeKind.FINGERPRINT,source.path,false,0L,manifest)
            val edit=StudioTransform(fps=25,outputWidth=32,outputHeight=32,trimStart=1,trimEnd=2,
                keyframes=listOf(StudioKeyframe(1,StudioPose()),StudioKeyframe(2,StudioPose(x=.25f))),
                frameDurations=listOf(StudioFrameDuration(2,125)))
            val output=File(root,"resized.otepack");val codec=PackCodec()
            StudioExporter(context,codec).export(item,edit,Uri.fromFile(output))
            val imported=output.inputStream().use { codec.importPack(it,File(root,"imported")) }
            assertEquals(32,imported.manifest.canvas.width);assertEquals(32,imported.manifest.canvas.height)
            assertEquals(listOf(40,125),imported.manifest.frames.map { it.durationMs })
            assertEquals(16f,imported.manifest.canvas.anchorX!!,.001f)
            val first=BitmapFactory.decodeFile(File(imported.directory,imported.manifest.frames[0].path).path)
            val second=BitmapFactory.decodeFile(File(imported.directory,imported.manifest.frames[1].path).path)
            try {
                assertEquals(Color.GREEN,first.getPixel(16,16))
                assertEquals(0,Color.alpha(second.getPixel(0,16)))
                assertEquals(Color.BLUE,second.getPixel(16,16))
            } finally { first.recycle();second.recycle() }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun changedSourceChecksumDoesNotTruncateChosenDestination() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"studio-checksum-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val source=File(root,"source").apply { check(mkdir()) }
            val image=File(source,"frame.png")
            val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
            val manifest=ThemeManifest(id="checksum-test",title="Checksum",kind=ThemeKind.FINGERPRINT,canvas=CanvasSpec(8,8),
                frames=listOf(FrameSpec("frame.png",33,"0".repeat(64))))
            val item=ThemeItem("test","Checksum","",ThemeKind.FINGERPRINT,source.path,false,0L,manifest)
            val destination=File(root,"existing.otepack").apply { writeText("keep-existing-bytes") }
            var rejected=false
            try { StudioExporter(context,PackCodec()).export(item,StudioTransform(),Uri.fromFile(destination)) }
            catch(_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected);assertEquals("keep-existing-bytes",destination.readText())
        } finally { root.deleteRecursively() }
    }

}
