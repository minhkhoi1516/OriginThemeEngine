package dev.origin.ote.ui

import dev.origin.ote.model.CanvasSpec
import org.junit.Assert.*
import org.junit.Test

/** Pure timeline geometry tests. No device, root, graphics decoder or fake success. */
class StudioTimelineTest {
    @Test fun trimAndExcludedFramesRetainSourceIndicesInOrder() {
        val edit=StudioTransform(trimStart=2,trimEnd=7,excludedFrames=listOf(3,6))
        assertEquals(listOf(2,4,5,7),StudioTimeline.frameIndices(10,edit))
    }
    @Test fun perFrameDelayOverridesBothSourceAndUniformTiming() {
        assertEquals(33,StudioTimeline.durationAt(StudioTransform(),4,80))
        val source=StudioTransform(preserveSourceTiming=true)
        assertEquals(80,StudioTimeline.durationAt(source,4,80))
        assertEquals(125,StudioTimeline.durationAt(source.copy(frameDurations=listOf(StudioFrameDuration(4,125))),4,80))
    }
    @Test fun linearKeyframesInterpolateAllPoseChannels() {
        val edit=StudioTransform(keyframes=listOf(
            StudioKeyframe(0,StudioPose(.5f,-90f,-1f,0f,0f)),
            StudioKeyframe(10,StudioPose(1.5f,90f,1f,1f,1f))))
        val midpoint=StudioTimeline.poseAt(edit,5)
        assertEquals(1f,midpoint.scale,.00001f);assertEquals(0f,midpoint.rotation,.00001f)
        assertEquals(0f,midpoint.x,.00001f);assertEquals(.5f,midpoint.y,.00001f)
        assertEquals(.5f,midpoint.alpha,.00001f)
    }
    @Test fun outgoingEaseCurveChangesActualFramePose() {
        val edit=StudioTransform(keyframes=listOf(
            StudioKeyframe(0,StudioPose(x=0f),StudioEasing.EASE_IN),
            StudioKeyframe(10,StudioPose(x=1f))))
        assertEquals(.25f,StudioTimeline.poseAt(edit,5).x,.00001f)
        assertEquals(0f,StudioTimeline.poseAt(edit,-5).x,.00001f)
        assertEquals(1f,StudioTimeline.poseAt(edit,50).x,.00001f)
    }
    @Test fun resizeRecenterMovesDeclaredArtworkPointToNewCanvasCenter() {
        val source=CanvasSpec(1260,2800,630f,2300f)
        val edit=StudioTimeline.centerAnchor(source,StudioTransform(outputWidth=1080,outputHeight=1920))
        val point=StudioTimeline.transformedAnchor(source,edit,StudioTimeline.basePose(edit))
        assertEquals(540f,point.first,.001f);assertEquals(960f,point.second,.001f)
        assertEquals(-.32142857f,edit.y,.0001f)
    }
    @Test fun explicitArtworkAnchorAndRotationAreIncludedInRecentering() {
        val source=CanvasSpec(16,16)
        val edit=StudioTimeline.centerAnchor(source,StudioTransform(sourceAnchorX=12f,sourceAnchorY=8f,rotation=90f))
        assertEquals(0f,edit.x,.0001f);assertEquals(-.25f,edit.y,.0001f)
        val point=StudioTimeline.transformedAnchor(source,edit,StudioTimeline.basePose(edit))
        assertEquals(8f,point.first,.001f);assertEquals(8f,point.second,.001f)
    }
    @Test fun allExcludedOutputAndExcessivePixelBudgetAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StudioTimeline.validate(CanvasSpec(16,16),2,StudioTransform(excludedFrames=listOf(0,1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudioTimeline.validate(CanvasSpec(1260,2800),70,StudioTransform())
        }
    }
    @Test fun duplicateKeyframeIndicesAndInvalidAnchorAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StudioTimeline.validate(CanvasSpec(16,16),2,StudioTransform(keyframes=listOf(StudioKeyframe(0,StudioPose()),StudioKeyframe(0,StudioPose()))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudioTimeline.validate(CanvasSpec(16,16),2,StudioTransform(sourceAnchorY=17f))
        }
    }
}
