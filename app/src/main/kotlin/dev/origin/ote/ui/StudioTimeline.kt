package dev.origin.ote.ui

import dev.origin.ote.model.CanvasSpec
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class StudioEasing { LINEAR, EASE_IN_OUT, EASE_IN, EASE_OUT }

data class StudioPose(val scale: Float = 1f, val rotation: Float = 0f,
                      val x: Float = 0f, val y: Float = 0f, val alpha: Float = 1f) : java.io.Serializable

data class StudioKeyframe(val frame: Int, val pose: StudioPose,
                         val easing: StudioEasing = StudioEasing.LINEAR) : java.io.Serializable

data class StudioFrameDuration(val frame: Int, val durationMs: Int) : java.io.Serializable

/** Pure timeline math shared by preview/export. Frame indices always refer to the source. */
object StudioTimeline {
    fun basePose(edit: StudioTransform) = StudioPose(edit.scale, edit.rotation, edit.x, edit.y, edit.alpha)

    fun withPose(edit: StudioTransform, pose: StudioPose): StudioTransform = edit.copy(
        scale = pose.scale, rotation = pose.rotation, x = pose.x, y = pose.y, alpha = pose.alpha)

    fun frameIndices(total: Int, edit: StudioTransform): List<Int> {
        require(total in 1..240) { "studio_frame_count_invalid" }
        val last = edit.trimEnd ?: total-1
        require(edit.trimStart in 0 until total && last in edit.trimStart until total) { "studio_trim_invalid" }
        require(edit.excludedFrames.size <= total && edit.excludedFrames.all { it in 0 until total }) { "studio_excluded_frame_invalid" }
        val excluded = edit.excludedFrames.toSet()
        return (edit.trimStart..last).filter { it !in excluded }
    }

    fun outputCanvas(source: CanvasSpec, edit: StudioTransform): CanvasSpec = CanvasSpec(
        edit.outputWidth ?: source.width, edit.outputHeight ?: source.height)

    fun durationAt(edit: StudioTransform, frame: Int, sourceDuration: Int): Int =
        edit.frameDurations.lastOrNull { it.frame == frame }?.durationMs
            ?: if (edit.preserveSourceTiming) sourceDuration else (1000f/edit.fps).roundToInt().coerceAtLeast(5)

    fun poseAt(edit: StudioTransform, frame: Int): StudioPose {
        val keys = edit.keyframes.sortedBy { it.frame }
        if (keys.isEmpty()) return basePose(edit)
        if (frame <= keys.first().frame) return keys.first().pose
        if (frame >= keys.last().frame) return keys.last().pose
        val next = keys.indexOfFirst { it.frame > frame }
        val a = keys[next-1]; val b = keys[next]
        val raw = (frame-a.frame).toFloat()/(b.frame-a.frame)
        val t = eased(raw, a.easing)
        fun blend(x: Float, y: Float) = x+(y-x)*t
        return StudioPose(blend(a.pose.scale,b.pose.scale), blend(a.pose.rotation,b.pose.rotation),
            blend(a.pose.x,b.pose.x), blend(a.pose.y,b.pose.y), blend(a.pose.alpha,b.pose.alpha))
    }

    fun eased(value: Float, easing: StudioEasing): Float {
        val x = value.coerceIn(0f,1f)
        return when (easing) {
            StudioEasing.LINEAR -> x
            StudioEasing.EASE_IN_OUT -> x*x*(3-2*x)
            StudioEasing.EASE_IN -> x*x
            StudioEasing.EASE_OUT -> 1-(1-x)*(1-x)
        }
    }

    fun fit(source: CanvasSpec, output: CanvasSpec): Float = min(
        output.width.toFloat()/source.width, output.height.toFloat()/source.height)

    fun sourceAnchor(source: CanvasSpec, edit: StudioTransform): Pair<Float,Float> =
        (edit.sourceAnchorX ?: source.anchorX ?: source.width/2f) to
            (edit.sourceAnchorY ?: source.anchorY ?: source.height/2f)

    fun transformedAnchor(source: CanvasSpec, edit: StudioTransform, pose: StudioPose): Pair<Float,Float> {
        val output=outputCanvas(source,edit); val anchor=sourceAnchor(source,edit)
        val factor=fit(source,output)*pose.scale
        val dx=(anchor.first-source.width/2f)*factor
        val dy=(anchor.second-source.height/2f)*factor
        val r=Math.toRadians(pose.rotation.toDouble())
        return (output.width/2f + dx*cos(r)-dy*sin(r)+pose.x*output.width).toFloat() to
            (output.height/2f + dx*sin(r)+dy*cos(r)+pose.y*output.height).toFloat()
    }

    fun centerAnchor(source: CanvasSpec, edit: StudioTransform): StudioTransform {
        val point=transformedAnchor(source,edit,basePose(edit))
        val output=outputCanvas(source,edit)
        return edit.copy(x=(edit.x-(point.first-output.width/2f)/output.width).coerceIn(-1f,1f),
            y=(edit.y-(point.second-output.height/2f)/output.height).coerceIn(-1f,1f))
    }

    fun validate(source: CanvasSpec, total: Int, edit: StudioTransform) {
        val output=outputCanvas(source,edit)
        require(output.width in 1..1260 && output.height in 1..2800) { "studio_canvas_outside_profile" }
        require(edit.fps in 1..120) { "studio_fps_invalid" }
        require(edit.keyframes.size<=240 && edit.keyframes.map { it.frame }.distinct().size==edit.keyframes.size) { "studio_keyframes_invalid" }
        require(edit.keyframes.all { it.frame in 0 until total }) { "studio_keyframe_index_invalid" }
        require(edit.frameDurations.size<=240 && edit.frameDurations.all { it.frame in 0 until total && it.durationMs in 5..10_000 }) { "studio_frame_duration_invalid" }
        val poses=listOf(basePose(edit))+edit.keyframes.map { it.pose }
        require(poses.all { p -> p.scale.isFinite() && p.scale in .1f..3f && p.rotation.isFinite() && p.rotation in -180f..180f &&
            p.x.isFinite() && p.x in -1f..1f && p.y.isFinite() && p.y in -1f..1f && p.alpha.isFinite() && p.alpha in 0f..1f }) { "studio_pose_invalid" }
        val anchor=sourceAnchor(source,edit)
        require(anchor.first.isFinite() && anchor.second.isFinite() && anchor.first in 0f..source.width.toFloat() && anchor.second in 0f..source.height.toFloat()) { "studio_anchor_invalid" }
        val frames=frameIndices(total,edit)
        require(frames.isNotEmpty()) { "studio_no_output_frames" }
        require(output.width.toLong()*output.height*frames.size<=240_000_000L) { "studio_pixel_budget_exceeded" }
    }
}
