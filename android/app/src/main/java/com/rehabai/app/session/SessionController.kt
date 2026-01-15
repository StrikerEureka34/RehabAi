package com.rehabai.app.session

import com.rehabai.app.domain.*
import kotlin.math.abs
import kotlin.math.atan2

class SessionController {
    
    fun processFrame(
        session: ExerciseSessionState,
        keypoints: List<Keypoint>,
        onFeedback: (Feedback) -> Unit
    ): FrameResult {
        val angles = extractFeatures(keypoints)
        
        if (session.angleBuffer.size >= ExerciseSessionState.SEQUENCE_LENGTH) {
            session.angleBuffer.removeFirst()
        }
        session.angleBuffer.addLast(angles)
        session.incrementFrameCount()
        
        // Track angles for quality analysis during active session
        if (session.state == SessionState.ACTIVE && session.movement.seenEccentric) {
            session.trackAngles(angles)
        }
        
        val validKeypoints = keypoints.count { it.score > 0.3f }
        
        updateConfirmation(session, validKeypoints)
        
        when (session.state) {
            SessionState.POSITIONING -> {
                if (validKeypoints >= ExerciseSessionState.POSE_VISIBILITY_THRESHOLD) {
                    session.transitionTo(SessionState.VERIFYING)
                }
            }
            SessionState.VERIFYING -> {
                // Handled by updateConfirmation
            }
            SessionState.READY -> {
                determineMovementPhase(session, angles)
                if (session.frameCount > 5) {
                    session.transitionTo(SessionState.ACTIVE)
                }
            }
            SessionState.ACTIVE -> {
                val prevPhase = session.movement.currentPhase
                determineMovementPhase(session, angles)
                
                // Track phase transitions for duration tracking
                if (prevPhase == MovementPhase.ECCENTRIC && 
                    session.movement.currentPhase == MovementPhase.CONCENTRIC) {
                    session.onEccentricComplete()
                }
            }
            else -> {}
        }
        
        val qualityScore = 0.5f
        
        var repCompleted = false
        if (session.state == SessionState.ACTIVE) {
            repCompleted = checkRepCompletion(session, qualityScore)
        }
        
        val feedback = generateFeedback(session, angles, qualityScore)
        if (feedback != null) {
            session.recordFeedback(feedback)
            onFeedback(feedback)
        }
        
        return FrameResult(
            sessionState = session.state,
            exerciseConfirmed = session.belief.confirmed,
            movementPhase = if (session.state == SessionState.ACTIVE) session.movement.currentPhase else null,
            qualityScore = if (session.belief.confirmed) qualityScore else null,
            repCount = session.movement.repCount,
            repCompleted = repCompleted,
            feedback = feedback,
            inTransition = session.movement.inTransition
        )
    }
    
    private fun updateConfirmation(session: ExerciseSessionState, validKeypoints: Int) {
        if (session.belief.confirmed) return
        
        if (validKeypoints >= ExerciseSessionState.POSE_VISIBILITY_THRESHOLD) {
            session.belief.confirmationFrames++
            if (session.belief.confirmationFrames >= ExerciseSessionState.CONFIRMATION_FRAME_COUNT) {
                session.belief.confirmed = true
                session.belief.lockedExercise = session.exercise.id
                session.transitionTo(SessionState.READY)
            }
        } else {
            session.belief.confirmationFrames = maxOf(0, session.belief.confirmationFrames - 1)
        }
    }
    
    private fun determineMovementPhase(session: ExerciseSessionState, angles: FloatArray): MovementPhase {
        val currentPhase = when (session.exercise.id) {
            "squat" -> {
                val kneeAngle = (angles[0] + angles[1]) / 2
                when {
                    kneeAngle > 145 -> MovementPhase.CONCENTRIC
                    kneeAngle < 130 -> MovementPhase.ECCENTRIC
                    else -> MovementPhase.TRANSITION
                }
            }
            "shoulder_press" -> {
                val elbowAngle = (angles[4] + angles[5]) / 2
                when {
                    elbowAngle > 140 -> MovementPhase.CONCENTRIC
                    elbowAngle < 110 -> MovementPhase.ECCENTRIC
                    else -> MovementPhase.TRANSITION
                }
            }
            "lunge" -> {
                val minKnee = minOf(angles[0], angles[1])
                session.movement.minKneeSeen = minOf(session.movement.minKneeSeen, minKnee)
                session.movement.maxKneeSeen = maxOf(session.movement.maxKneeSeen, minKnee)
                
                val midThreshold = (session.movement.minKneeSeen + session.movement.maxKneeSeen) / 2
                
                if (session.movement.maxKneeSeen - session.movement.minKneeSeen < 15) {
                    when {
                        minKnee > 140 -> MovementPhase.CONCENTRIC
                        minKnee < 125 -> MovementPhase.ECCENTRIC
                        else -> MovementPhase.TRANSITION
                    }
                } else {
                    when {
                        minKnee > midThreshold + 10 -> MovementPhase.CONCENTRIC
                        minKnee < midThreshold - 10 -> MovementPhase.ECCENTRIC
                        else -> MovementPhase.TRANSITION
                    }
                }
            }
            "lateral_raise" -> {
                val shoulderAngle = (angles[6] + angles[7]) / 2
                when {
                    shoulderAngle > 60 -> MovementPhase.CONCENTRIC
                    shoulderAngle < 40 -> MovementPhase.ECCENTRIC
                    else -> MovementPhase.TRANSITION
                }
            }
            else -> MovementPhase.TRANSITION
        }
        
        if (currentPhase == session.movement.currentPhase) {
            session.movement.phaseFrames++
            session.movement.stableStateFrames++
        } else {
            session.movement.lastPhase = session.movement.currentPhase
            session.movement.currentPhase = currentPhase
            session.movement.phaseFrames = 1
            session.movement.stableStateFrames = 0
        }
        
        session.movement.inTransition = currentPhase == MovementPhase.TRANSITION || 
            session.movement.phaseFrames < ExerciseSessionState.STATE_STABILITY_FRAMES
        
        return currentPhase
    }
    
    private fun checkRepCompletion(session: ExerciseSessionState, qualityScore: Float): Boolean {
        val now = System.currentTimeMillis()
        
        if (now - session.movement.lastRepTime < ExerciseSessionState.MIN_REP_INTERVAL_MS) {
            return false
        }
        
        if (session.movement.currentPhase == MovementPhase.ECCENTRIC) {
            if (!session.movement.seenEccentric) {
                session.movement.seenEccentric = true
                session.startRep()
            }
        }
        
        if (session.movement.seenEccentric &&
            session.movement.currentPhase == MovementPhase.CONCENTRIC &&
            session.movement.phaseFrames >= 2) {
            
            session.movement.lastRepTime = now
            session.movement.seenEccentric = false
            
            if (session.movement.currentRepInvalid) {
                session.movement.currentRepInvalid = false
                session.movement.invalidReason = ""
                return false
            }
            
            session.movement.repCount++
            session.completeRep(qualityScore)
            return true
        }
        
        return false
    }
    
    private fun generateFeedback(
        session: ExerciseSessionState, 
        angles: FloatArray, 
        qualityScore: Float
    ): Feedback? {
        if (!session.shouldGiveFeedback()) return null
        
        val exerciseId = session.exercise.id
        
        return when (session.state) {
            SessionState.POSITIONING -> {
                Feedback(
                    message = FeedbackMessages.get(exerciseId, "positioning"),
                    type = FeedbackType.GUIDANCE
                )
            }
            SessionState.VERIFYING -> {
                val progress = (session.belief.confirmationFrames.toFloat() / 
                    ExerciseSessionState.CONFIRMATION_FRAME_COUNT * 100).toInt()
                when {
                    progress > 70 -> Feedback("Almost ready...", FeedbackType.GUIDANCE)
                    progress > 30 -> Feedback("Hold still...", FeedbackType.GUIDANCE)
                    else -> null
                }
            }
            SessionState.READY -> {
                Feedback(
                    message = FeedbackMessages.get(exerciseId, "ready"),
                    type = FeedbackType.GUIDANCE
                )
            }
            SessionState.ACTIVE -> {
                generateActiveFeedback(session, angles)
            }
            else -> null
        }
    }
    
    private fun generateActiveFeedback(session: ExerciseSessionState, angles: FloatArray): Feedback? {
        val exerciseId = session.exercise.id
        
        when (exerciseId) {
            "squat" -> {
                val kneeDiff = abs(angles[0] - angles[1])
                val hipDiff = abs(angles[2] - angles[3])
                val avgKnee = (angles[0] + angles[1]) / 2
                
                if (kneeDiff > 35) {
                    session.invalidateCurrentRep("Major knee asymmetry")
                    return Feedback("Rep not counted. Keep both knees aligned.", FeedbackType.WARNING)
                }
                if (hipDiff > 25) {
                    session.invalidateCurrentRep("Major hip asymmetry")
                    return Feedback("Rep not counted. Even out your stance.", FeedbackType.WARNING)
                }
                if (session.movement.currentPhase == MovementPhase.ECCENTRIC && avgKnee > 140) {
                    session.addRepRemark("Depth insufficient")
                    return Feedback(FeedbackMessages.get(exerciseId, "go_lower"), FeedbackType.CORRECTIVE)
                }
                if (kneeDiff > 20) {
                    session.addRepRemark("Knees misaligned")
                    return Feedback(FeedbackMessages.get(exerciseId, "align_knees"), FeedbackType.CORRECTIVE)
                }
            }
            
            "shoulder_press" -> {
                val elbowDiff = abs(angles[4] - angles[5])
                val shoulderDiff = abs(angles[6] - angles[7])
                
                if (elbowDiff > 50) {
                    session.invalidateCurrentRep("Using only one arm")
                    return Feedback("Rep not counted. Use both arms together.", FeedbackType.WARNING)
                }
                if (shoulderDiff > 40) {
                    session.invalidateCurrentRep("Arms moving at different rates")
                    return Feedback("Rep not counted. Move both arms at the same rate.", FeedbackType.WARNING)
                }
                if (elbowDiff > 25) {
                    session.addRepRemark("Arms uneven")
                    return Feedback(FeedbackMessages.get(exerciseId, "arms_uneven"), FeedbackType.CORRECTIVE)
                }
                if (shoulderDiff > 20) {
                    session.addRepRemark("Asymmetric movement")
                    return Feedback(FeedbackMessages.get(exerciseId, "keep_symmetry"), FeedbackType.CORRECTIVE)
                }
            }
            
            "lunge" -> {
                val minKnee = minOf(angles[0], angles[1])
                val maxKnee = maxOf(angles[0], angles[1])
                val avgHip = (angles[2] + angles[3]) / 2
                val elbowDiff = abs(angles[4] - angles[5])
                
                if (elbowDiff > 60) {
                    session.invalidateCurrentRep("Arms not controlled")
                    return Feedback("Rep not counted. Keep arms steady.", FeedbackType.WARNING)
                }
                if (maxKnee - minKnee < 10 && session.movement.currentPhase == MovementPhase.ECCENTRIC) {
                    session.invalidateCurrentRep("No proper lunge")
                    return Feedback("Rep not counted. Step forward into a lunge.", FeedbackType.WARNING)
                }
                if (avgHip < 140) {
                    session.invalidateCurrentRep("Torso leaning too much")
                    return Feedback("Rep not counted. Keep torso upright.", FeedbackType.WARNING)
                }
                if (minKnee < 70) {
                    session.invalidateCurrentRep("Knee too far forward")
                    return Feedback("Rep not counted. Front knee shouldn't pass toes.", FeedbackType.WARNING)
                }
                if (session.movement.currentPhase == MovementPhase.ECCENTRIC && minKnee > 145) {
                    session.addRepRemark("Depth insufficient")
                    return Feedback(FeedbackMessages.get(exerciseId, "go_lower"), FeedbackType.CORRECTIVE)
                }
            }
            
            "lateral_raise" -> {
                val avgShoulder = (angles[6] + angles[7]) / 2
                val shoulderDiff = abs(angles[6] - angles[7])
                val avgElbow = (angles[4] + angles[5]) / 2
                
                if (shoulderDiff > 50) {
                    session.invalidateCurrentRep("Using only one arm")
                    return Feedback("Rep not counted. Raise both arms together.", FeedbackType.WARNING)
                }
                if (session.movement.currentPhase == MovementPhase.CONCENTRIC && avgShoulder < 30) {
                    session.invalidateCurrentRep("Arms barely raised")
                    return Feedback("Rep not counted. Raise arms to shoulder height.", FeedbackType.WARNING)
                }
                if (avgElbow < 100) {
                    session.invalidateCurrentRep("Arms too bent")
                    return Feedback("Rep not counted. Straighten your arms.", FeedbackType.WARNING)
                }
                if (shoulderDiff > 20) {
                    session.addRepRemark("Arms uneven")
                    return Feedback(FeedbackMessages.get(exerciseId, "arms_uneven"), FeedbackType.CORRECTIVE)
                }
            }
        }
        
        // Positive feedback after rep
        if (session.movement.repCount > 0 && 
            session.movement.phaseFrames <= 8 && 
            session.movement.currentPhase == MovementPhase.CONCENTRIC) {
            return Feedback(
                "Good! ${session.movement.repCount} of ${session.exercise.targetReps}",
                FeedbackType.POSITIVE
            )
        }
        
        // Periodic encouragement
        if (session.frameCount % 90 == 0) {
            return Feedback("Keep going, good form.", FeedbackType.GUIDANCE)
        }
        
        return null
    }
    
    private fun extractFeatures(keypoints: List<Keypoint>): FloatArray {
        val angles = FloatArray(16)
        
        val leftShoulder = getKeypoint(keypoints, "left_shoulder")
        val rightShoulder = getKeypoint(keypoints, "right_shoulder")
        val leftElbow = getKeypoint(keypoints, "left_elbow")
        val rightElbow = getKeypoint(keypoints, "right_elbow")
        val leftWrist = getKeypoint(keypoints, "left_wrist")
        val rightWrist = getKeypoint(keypoints, "right_wrist")
        val leftHip = getKeypoint(keypoints, "left_hip")
        val rightHip = getKeypoint(keypoints, "right_hip")
        val leftKnee = getKeypoint(keypoints, "left_knee")
        val rightKnee = getKeypoint(keypoints, "right_knee")
        val leftAnkle = getKeypoint(keypoints, "left_ankle")
        val rightAnkle = getKeypoint(keypoints, "right_ankle")
        
        angles[0] = calculateAngle(leftHip, leftKnee, leftAnkle)
        angles[1] = calculateAngle(rightHip, rightKnee, rightAnkle)
        angles[2] = calculateAngle(leftShoulder, leftHip, leftKnee)
        angles[3] = calculateAngle(rightShoulder, rightHip, rightKnee)
        angles[4] = calculateAngle(leftShoulder, leftElbow, leftWrist)
        angles[5] = calculateAngle(rightShoulder, rightElbow, rightWrist)
        angles[6] = calculateAngle(leftElbow, leftShoulder, leftHip)
        angles[7] = calculateAngle(rightElbow, rightShoulder, rightHip)
        angles[8] = calculateAngle(leftShoulder, leftHip, rightHip)
        angles[9] = calculateAngle(rightShoulder, rightHip, leftHip)
        angles[10] = calculateAngle(leftHip, leftShoulder, rightShoulder)
        angles[11] = calculateAngle(rightHip, rightShoulder, leftShoulder)
        angles[12] = calculateAngle(leftKnee, leftHip, rightHip)
        angles[13] = calculateAngle(rightKnee, rightHip, leftHip)
        angles[14] = calculateAngle(leftWrist, leftElbow, leftShoulder)
        angles[15] = calculateAngle(rightWrist, rightElbow, rightShoulder)
        
        return angles
    }
    
    private fun getKeypoint(keypoints: List<Keypoint>, name: String): Pair<Float, Float>? {
        val kp = keypoints.find { it.name == name }
        return if (kp != null && kp.score > 0.3f) Pair(kp.x, kp.y) else null
    }
    
    private fun calculateAngle(p1: Pair<Float, Float>?, p2: Pair<Float, Float>?, p3: Pair<Float, Float>?): Float {
        if (p1 == null || p2 == null || p3 == null) return 0f
        
        val radians = atan2(p3.second - p2.second, p3.first - p2.first) -
            atan2(p1.second - p2.second, p1.first - p2.first)
        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        angle = abs(angle)
        if (angle > 180) angle = 360 - angle
        return angle
    }
}

data class Keypoint(
    val name: String,
    val x: Float,
    val y: Float,
    val score: Float
)

data class FrameResult(
    val sessionState: SessionState,
    val exerciseConfirmed: Boolean,
    val movementPhase: MovementPhase?,
    val qualityScore: Float?,
    val repCount: Int,
    val repCompleted: Boolean,
    val feedback: Feedback?,
    val inTransition: Boolean
)
