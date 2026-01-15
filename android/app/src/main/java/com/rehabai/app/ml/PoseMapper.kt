package com.rehabai.app.ml

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.rehabai.app.session.Keypoint

object PoseMapper {
    
    private val landmarkNames = mapOf(
        PoseLandmark.NOSE to "nose",
        PoseLandmark.LEFT_EYE_INNER to "left_eye",
        PoseLandmark.RIGHT_EYE_INNER to "right_eye",
        PoseLandmark.LEFT_EAR to "left_ear",
        PoseLandmark.RIGHT_EAR to "right_ear",
        PoseLandmark.LEFT_SHOULDER to "left_shoulder",
        PoseLandmark.RIGHT_SHOULDER to "right_shoulder",
        PoseLandmark.LEFT_ELBOW to "left_elbow",
        PoseLandmark.RIGHT_ELBOW to "right_elbow",
        PoseLandmark.LEFT_WRIST to "left_wrist",
        PoseLandmark.RIGHT_WRIST to "right_wrist",
        PoseLandmark.LEFT_HIP to "left_hip",
        PoseLandmark.RIGHT_HIP to "right_hip",
        PoseLandmark.LEFT_KNEE to "left_knee",
        PoseLandmark.RIGHT_KNEE to "right_knee",
        PoseLandmark.LEFT_ANKLE to "left_ankle",
        PoseLandmark.RIGHT_ANKLE to "right_ankle"
    )
    
    fun mapPoseToKeypoints(pose: Pose, imageWidth: Int, imageHeight: Int): List<Keypoint> {
        return landmarkNames.mapNotNull { (landmarkType, name) ->
            val landmark = pose.getPoseLandmark(landmarkType)
            if (landmark != null) {
                Keypoint(
                    name = name,
                    x = landmark.position.x / imageWidth,
                    y = landmark.position.y / imageHeight,
                    score = landmark.inFrameLikelihood
                )
            } else null
        }
    }
    
    fun getSkeletonConnections(): List<Pair<Int, Int>> = listOf(
        PoseLandmark.NOSE to PoseLandmark.LEFT_EYE_INNER,
        PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE_INNER,
        PoseLandmark.LEFT_EYE_INNER to PoseLandmark.LEFT_EAR,
        PoseLandmark.RIGHT_EYE_INNER to PoseLandmark.RIGHT_EAR,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
    )
    
    fun getLandmarkPosition(pose: Pose, landmarkType: Int): PointF? {
        val landmark = pose.getPoseLandmark(landmarkType)
        return if (landmark != null && landmark.inFrameLikelihood > 0.3f) {
            landmark.position
        } else null
    }
}
