package dev.origin.ote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import dev.origin.ote.R
import dev.origin.ote.model.CanvasSpec
import dev.origin.ote.model.FrameSpec
import dev.origin.ote.model.ThemeItem
import dev.origin.ote.pack.PackCodec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToInt

/** Bakes the same timeline evaluated by preview into actual RGBA PNGs, without root. */
class StudioExporter(private val context: Context, private val codec: PackCodec) {
    suspend fun export(item: ThemeItem, edit: StudioTransform, destination: Uri) {
        val manifest=item.manifest
        val sourceCanvas=manifest.canvas
        StudioTimeline.validate(sourceCanvas,manifest.frames.size,edit)
        val outputCanvas=StudioTimeline.outputCanvas(sourceCanvas,edit)
        val indices=StudioTimeline.frameIndices(manifest.frames.size,edit)
        val durations=indices.map { StudioTimeline.durationAt(edit,it,manifest.frames[it].durationMs) }
        require(durations.all { it in 5..10_000 } && durations.sumOf { it.toLong() }<=120_000) { "studio_timeline_budget_exceeded" }
        val work=File(context.cacheDir,"studio-${UUID.randomUUID()}")
        check(work.mkdir())
        val ready=File(context.cacheDir,"studio-ready-${UUID.randomUUID()}.otepack")
        var readyOwned=false
        try {
            val framesDirectory=File(work,"frames").apply { check(mkdir()) }
            val root=File(item.directory).canonicalFile
            val outputFrames=indices.mapIndexed { outputIndex,sourceIndex ->
                currentCoroutineContext().ensureActive()
                val frame=manifest.frames[sourceIndex]
                val sourceFile=File(root,frame.path)
                require(sourceFile.canonicalPath.startsWith(root.path+File.separator) && !Files.isSymbolicLink(sourceFile.toPath())) { "studio_source_path_invalid" }
                val bytes=readFrame(sourceFile)
                require(sha256(bytes).equals(frame.sha256,ignoreCase=true)) { "frame_checksum_changed" }
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true;inScaled=false }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth==sourceCanvas.width && bounds.outHeight==sourceCanvas.height) { "studio_source_canvas_changed" }
                val source=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inScaled=false }))
                val result=Bitmap.createBitmap(outputCanvas.width,outputCanvas.height,Bitmap.Config.ARGB_8888)
                try {
                    val pose=StudioTimeline.poseAt(edit,sourceIndex)
                    Canvas(result).drawBitmap(source,matrix(sourceCanvas,outputCanvas,pose),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha=(pose.alpha*255).roundToInt()
                    })
                    val path="frames/frame_${outputIndex.toString().padStart(5,'0')}.png"
                    val file=File(framesDirectory,path.substringAfter('/'))
                    file.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG,100,it)) }
                    FrameSpec(path,durations[outputIndex],sha256(file))
                } finally { source.recycle();result.recycle() }
            }
            // Version1 has one artwork anchor. Describe the first baked frame;
            // later keyframes deliberately move the pixels, never the hardware sensor.
            val anchor=StudioTimeline.transformedAnchor(sourceCanvas,edit,StudioTimeline.poseAt(edit,indices.first()))
            require(anchor.first in 0f..outputCanvas.width.toFloat() && anchor.second in 0f..outputCanvas.height.toFloat()) { "studio_first_anchor_outside_canvas" }
            val transformed=manifest.copy(id=UUID.randomUUID().toString(),
                title=context.getString(R.string.studio_title_format,manifest.title).take(120),
                canvas=outputCanvas.copy(anchorX=anchor.first,anchorY=anchor.second),
                frames=outputFrames,thumbnail=outputFrames[outputFrames.size/2].path)
            File(work,"manifest.json").writeText(Json { prettyPrint=true;encodeDefaults=true }.encodeToString(transformed))
            codec.validateDirectory(work)
            check(ready.createNewFile());readyOwned=true
            ready.outputStream().use { codec.exportPack(work,it) }
            requireNotNull(context.contentResolver.openOutputStream(destination,"wt")).use { output -> ready.inputStream().use { it.copyTo(output) } }
        } finally {
            check(work.parentFile?.canonicalFile==context.cacheDir.canonicalFile)
            work.deleteRecursively()
            if(readyOwned) ready.delete()
        }
    }

    private fun matrix(source: CanvasSpec,output: CanvasSpec,pose: StudioPose):Matrix=Matrix().apply {
        postTranslate(-source.width/2f,-source.height/2f)
        val factor=StudioTimeline.fit(source,output)*pose.scale
        postScale(factor,factor)
        postRotate(pose.rotation)
        postTranslate(output.width/2f+pose.x*output.width,output.height/2f+pose.y*output.height)
    }

    private suspend fun readFrame(file:File):ByteArray {
        require(file.isFile && file.length() in 1..MAX_FRAME_BYTES) { "frame_size_invalid" }
        val output=ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer=ByteArray(32768)
            while(true) {
                currentCoroutineContext().ensureActive()
                val count=input.read(buffer);if(count<0)break
                require(output.size().toLong()+count<=MAX_FRAME_BYTES) { "frame_size_invalid" }
                output.write(buffer,0,count)
            }
        }
        return output.toByteArray()
    }
    private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sha256(file:File):String=file.inputStream().use { input ->
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(32768)
        while(true) { val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count) }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private companion object { const val MAX_FRAME_BYTES=32L*1024*1024 }
}
