package dev.origin.ote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.origin.ote.R
import dev.origin.ote.model.ThemeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private object FrameCache {
    val memory=object:LruCache<String,Bitmap>(16*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap):Int=value.allocationByteCount
    }
    fun load(file:File):Bitmap? {
        val key="${file.absolutePath}:${file.length()}:${file.lastModified()}"
        synchronized(memory) { memory.get(key)?.let { return it } }
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true;inScaled=false }
        BitmapFactory.decodeFile(file.path,options)
        if(options.outWidth<=0 || options.outHeight<=0 || options.outWidth.toLong()*options.outHeight>16_777_216L)return null
        options.inSampleSize=1
        while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2
        options.inJustDecodeBounds=false
        return BitmapFactory.decodeFile(file.path,options)?.also { synchronized(memory) { memory.put(key,it) } }
    }
}

@Composable
fun ThemePreview(item:ThemeItem,playing:Boolean,modifier:Modifier=Modifier,transform:StudioTransform?=null,
                 frameSelection:Int?=null,onFrameChanged:((Int)->Unit)?=null,showCenter:Boolean=false) {
    val owner=LocalLifecycleOwner.current
    var frame by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember(item.id) { mutableStateOf(false) }
    var lastFrameIndex by remember(item.id) { mutableIntStateOf(-1) }
    val transformState by rememberUpdatedState(transform)
    val frameChanged by rememberUpdatedState(onFrameChanged)
    val indices=remember(item.id,transform?.trimStart,transform?.trimEnd,transform?.excludedFrames) {
        runCatching { StudioTimeline.frameIndices(item.manifest.frames.size,transform?:StudioTransform()) }.getOrDefault(emptyList())
    }
    LaunchedEffect(item.id,playing,owner,frameSelection,indices) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val manifest=item.manifest
            if(manifest.frames.isEmpty()) return@repeatOnLifecycle
            var index=when {
                !playing && frameSelection!=null -> frameSelection.coerceIn(manifest.frames.indices)
                lastFrameIndex in indices -> lastFrameIndex
                indices.isNotEmpty() -> if(playing)indices.first() else indices[indices.size/2]
                else -> return@repeatOnLifecycle
            }
            while(isActive) {
                val started=SystemClock.elapsedRealtime()
                val source=manifest.frames[index]
                frame=withContext(Dispatchers.IO) {
                    try {
                        val root=File(item.directory).canonicalPath+File.separator
                        val file=File(item.directory,source.path)
                        if(file.canonicalPath.startsWith(root))FrameCache.load(file) else null
                    } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                    catch(_:Exception) { null }
                }
                decodeFailed=frame==null;lastFrameIndex=index;frameChanged?.invoke(index)
                if(!playing || indices.isEmpty())break
                val duration=transformState?.let { StudioTimeline.durationAt(it,index,source.durationMs) }?:source.durationMs
                delay((duration-(SystemClock.elapsedRealtime()-started)).coerceAtLeast(1))
                val position=indices.indexOf(index)
                index=indices[(position+1).mod(indices.size)]
            }
        }
    }
    val output=StudioTimeline.outputCanvas(item.manifest.canvas,transform?:StudioTransform())
    val pose=transform?.let { StudioTimeline.poseAt(it,lastFrameIndex.coerceAtLeast(0)) }?:StudioPose()
    BoxWithConstraints(modifier.background(Color.Black)) {
        val density=LocalDensity.current
        val width=with(density) { maxWidth.toPx() };val height=with(density) { maxHeight.toPx() }
        val screenFit=minOf(width/output.width,height/output.height)
        val sourceFit=StudioTimeline.fit(item.manifest.canvas,output)
        Box(Modifier.align(Alignment.Center).size(
            with(density) { (output.width*screenFit).toDp() },with(density) { (output.height*screenFit).toDp() }).clipToBounds()) {
            frame?.let { bitmap ->
                Image(bitmap.asImageBitmap(),contentDescription=stringResource(R.string.animation_preview),
                    modifier=Modifier.align(Alignment.Center).size(
                        with(density) { (item.manifest.canvas.width*sourceFit*screenFit).toDp() },
                        with(density) { (item.manifest.canvas.height*sourceFit*screenFit).toDp() }).graphicsLayer {
                        scaleX=pose.scale;scaleY=pose.scale;rotationZ=pose.rotation;alpha=pose.alpha
                        translationX=pose.x*output.width*screenFit;translationY=pose.y*output.height*screenFit
                    },contentScale=ContentScale.Fit)
            } ?: if(decodeFailed) Text(stringResource(R.string.preview_decode_error),Modifier.align(Alignment.Center),color=Color.White)
                else if(indices.isEmpty()) Text(stringResource(R.string.timeline_no_frames),Modifier.align(Alignment.Center),color=Color.White)
                else CircularProgressIndicator(Modifier.align(Alignment.Center))
            if(showCenter) Canvas(Modifier.fillMaxSize()) {
                val d=7.dp.toPx();val c=Offset(size.width/2,size.height/2)
                drawLine(Color.White.copy(alpha=.65f),Offset(c.x-d,c.y),Offset(c.x+d,c.y),1.dp.toPx())
                drawLine(Color.White.copy(alpha=.65f),Offset(c.x,c.y-d),Offset(c.x,c.y+d),1.dp.toPx())
            }
        }
    }
}
