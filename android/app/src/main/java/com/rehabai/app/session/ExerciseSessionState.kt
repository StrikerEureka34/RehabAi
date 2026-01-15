package com.rehabai.app.session

import com.rehabai.app.domain.*
import java.util.ArrayDeque
import kotlin.math.abs

data class BeliefState(
    var confirmationFrames: Int = 0,
    var confirmed: Boolean = false,
    var lockedExercise: String? = null,
    var qualityBelief: Float = 0.5f,
    var lastFeedbackTime: Long = 0L
)

data class MovementState(
    var currentPhase: MovementPhase = MovementPhase.IDLE,
    var phaseFrames: Int = 0,
    var lastPhase: MovementPhase = MovementPhase.IDLE,
    var repCount: Int = 0,
    var lastRepTime: Long = 0L,
    var inTransition: Boolean = false,
    var stableStateFrames: Int = 0,
    var currentRepStart: Long? = null,
    var currentRepInvalid: Boolean = false,
    var invalidReason: String = "",
    var seenEccentric: Boolean = false,
    var minKneeSeen: Float = 180f,
    var maxKneeSeen: Float = 0f,
    
    // For tracking rep phases duration
    var eccentricStartTime: Long? = null,
    var eccentricEndTime: Long? = null,
    var concentricStartTime: Long? = null
)

/**
 * Tracks angle measurements during a single rep
 */
data class RepAngleTracker(
    var minPrimaryAngle: Float = Float.MAX_VALUE,
    var maxPrimaryAngle: Float = Float.MIN_VALUE,
    var sumLeftKnee: Float = 0f,
    var sumRightKnee: Float = 0f,
    var sumLeftHip: Float = 0f,
    var sumRightHip: Float = 0f,
    var sumLeftElbow: Float = 0f,
    var sumRightElbow: Float = 0f,
    var sumLeftShoulder: Float = 0f,
    var sumRightShoulder: Float = 0f,
    var frameCount: Int = 0,
    
    // Track max differences for symmetry
    var maxKneeDiff: Float = 0f,
    var maxHipDiff: Float = 0f,
    var maxElbowDiff: Float = 0f,
    var maxShoulderDiff: Float = 0f
) {
    fun addFrame(angles: FloatArray) {
        if (angles.size < 16) return
        frameCount++
        
        // angles[0-1] = knee, [2-3] = hip, [4-5] = elbow, [6-7] = shoulder
        sumLeftKnee += angles[0]
        sumRightKnee += angles[1]
        sumLeftHip += angles[2]
        sumRightHip += angles[3]
        sumLeftElbow += angles[4]
        sumRightElbow += angles[5]
        sumLeftShoulder += angles[6]
        sumRightShoulder += angles[7]
        
        maxKneeDiff = maxOf(maxKneeDiff, abs(angles[0] - angles[1]))
        maxHipDiff = maxOf(maxHipDiff, abs(angles[2] - angles[3]))
        maxElbowDiff = maxOf(maxElbowDiff, abs(angles[4] - angles[5]))
        maxShoulderDiff = maxOf(maxShoulderDiff, abs(angles[6] - angles[7]))
    }
    
    fun updatePrimaryAngle(angle: Float) {
        minPrimaryAngle = minOf(minPrimaryAngle, angle)
        maxPrimaryAngle = maxOf(maxPrimaryAngle, angle)
    }
    
    fun getAverages(): AngleMetrics {
        val count = if (frameCount > 0) frameCount.toFloat() else 1f
        
        val avgLeftKnee = sumLeftKnee / count
        val avgRightKnee = sumRightKnee / count
        val avgLeftHip = sumLeftHip / count
        val avgRightHip = sumRightHip / count
        val avgLeftElbow = sumLeftElbow / count
        val avgRightElbow = sumRightElbow / count
        val avgLeftShoulder = sumLeftShoulder / count
        val avgRightShoulder = sumRightShoulder / count
        
        // Symmetry scores: 1.0 = perfect, 0.0 = 50+ degree difference
        val kneeSymmetry = (1f - (maxKneeDiff / 50f)).coerceIn(0f, 1f)
        val hipSymmetry = (1f - (maxHipDiff / 40f)).coerceIn(0f, 1f)
        val elbowSymmetry = (1f - (maxElbowDiff / 50f)).coerceIn(0f, 1f)
        val shoulderSymmetry = (1f - (maxShoulderDiff / 40f)).coerceIn(0f, 1f)
        
        return AngleMetrics(
            leftKneeAngle = avgLeftKnee,
            rightKneeAngle = avgRightKnee,
            kneeSymmetryScore = kneeSymmetry,
            leftHipAngle = avgLeftHip,
            rightHipAngle = avgRightHip,
            hipSymmetryScore = hipSymmetry,
            leftElbowAngle = avgLeftElbow,
            rightElbowAngle = avgRightElbow,
            elbowSymmetryScore = elbowSymmetry,
            leftShoulderAngle = avgLeftShoulder,
            rightShoulderAngle = avgRightShoulder,
            shoulderSymmetryScore = shoulderSymmetry,
            primaryRomDegrees = if (maxPrimaryAngle > minPrimaryAngle) 
                maxPrimaryAngle - minPrimaryAngle else 0f,
            actualMinAngle = if (minPrimaryAngle != Float.MAX_VALUE) minPrimaryAngle else 0f,
            actualMaxAngle = if (maxPrimaryAngle != Float.MIN_VALUE) maxPrimaryAngle else 0f
        )
    }
    
    fun reset() {
        minPrimaryAngle = Float.MAX_VALUE
        maxPrimaryAngle = Float.MIN_VALUE
        sumLeftKnee = 0f
        sumRightKnee = 0f
        sumLeftHip = 0f
        sumRightHip = 0f
        sumLeftElbow = 0f
        sumRightElbow = 0f
        sumLeftShoulder = 0f
        sumRightShoulder = 0f
        frameCount = 0
        maxKneeDiff = 0f
        maxHipDiff = 0f
        maxElbowDiff = 0f
        maxShoulderDiff = 0f
    }
}

class ExerciseSessionState(
    val sessionId: String,
    val exercise: Exercise
) {
    var state: SessionState = SessionState.POSITIONING
        private set
    
    val belief = BeliefState()
    val movement = MovementState()
    val repDetails = mutableListOf<RepDetail>()
    val feedbackHistory = mutableListOf<Feedback>()
    val currentRepRemarks = mutableListOf<String>()
    val angleBuffer = ArrayDeque<FloatArray>(SEQUENCE_LENGTH)
    
    // Enhanced tracking
    val currentRepAngles = RepAngleTracker()
    
    var frameCount: Int = 0
        private set
    
    val startTime: Long = System.currentTimeMillis()
    
    companion object {
        const val SEQUENCE_LENGTH = 40
        const val CONFIRMATION_FRAME_COUNT = 15
        const val POSE_VISIBILITY_THRESHOLD = 6
        const val MIN_FEEDBACK_INTERVAL_MS = 1000L
        const val MIN_REP_INTERVAL_MS = 800L
        const val STATE_STABILITY_FRAMES = 2
        
        // Expected ROM for each exercise
        val EXPECTED_ROM = mapOf(
            "squat" to Pair(90f, 170f),       // Knee angle range
            "shoulder_press" to Pair(70f, 170f), // Elbow angle range
            "lunge" to Pair(80f, 170f),       // Knee angle range
            "lateral_raise" to Pair(20f, 90f)  // Shoulder angle range
        )
    }
    
    fun incrementFrameCount() { frameCount++ }
    
    fun transitionTo(newState: SessionState) {
        state = newState
    }
    
    fun shouldGiveFeedback(): Boolean {
        val now = System.currentTimeMillis()
        if (now - belief.lastFeedbackTime < MIN_FEEDBACK_INTERVAL_MS) return false
        if (state == SessionState.UNCERTAINTY) return false
        return true
    }
    
    fun recordFeedback(feedback: Feedback) {
        belief.lastFeedbackTime = System.currentTimeMillis()
        feedbackHistory.add(feedback)
    }
    
    fun trackAngles(angles: FloatArray) {
        currentRepAngles.addFrame(angles)
        
        // Track primary angle based on exercise
        val primaryAngle = when (exercise.id) {
            "squat" -> (angles[0] + angles[1]) / 2  // Knee angle
            "shoulder_press" -> (angles[4] + angles[5]) / 2  // Elbow angle
            "lunge" -> minOf(angles[0], angles[1])  // Min knee angle
            "lateral_raise" -> (angles[6] + angles[7]) / 2  // Shoulder angle
            else -> angles[0]
        }
        currentRepAngles.updatePrimaryAngle(primaryAngle)
    }
    
    fun startRep() {
        movement.currentRepStart = System.currentTimeMillis()
        movement.eccentricStartTime = System.currentTimeMillis()
        currentRepRemarks.clear()
        currentRepAngles.reset()
        movement.currentRepInvalid = false
        movement.invalidReason = ""
    }
    
    fun onEccentricComplete() {
        movement.eccentricEndTime = System.currentTimeMillis()
        movement.concentricStartTime = System.currentTimeMillis()
    }
    
    fun completeRep(qualityScore: Float): RepDetail {
        val now = System.currentTimeMillis()
        val start = movement.currentRepStart ?: now
        val duration = (now - start) / 1000f
        
        // Calculate phase durations
        val eccentricDur = if (movement.eccentricStartTime != null && movement.eccentricEndTime != null) {
            (movement.eccentricEndTime!! - movement.eccentricStartTime!!) / 1000f
        } else duration / 2f
        
        val concentricDur = if (movement.concentricStartTime != null) {
            (now - movement.concentricStartTime!!) / 1000f
        } else duration / 2f
        
        // Get angle metrics
        val angleMetrics = currentRepAngles.getAverages().let { metrics ->
            val expectedRange = EXPECTED_ROM[exercise.id] ?: Pair(90f, 170f)
            metrics.copy(
                expectedMinAngle = expectedRange.first,
                expectedMaxAngle = expectedRange.second
            )
        }
        
        // Calculate quality breakdown
        val qualityBreakdown = calculateQualityBreakdown(angleMetrics, duration, eccentricDur, concentricDur)
        
        val detail = RepDetail(
            repNumber = movement.repCount,
            startTime = start,
            endTime = now,
            durationSeconds = duration,
            qualityScore = qualityBreakdown.totalScore / 100f,
            qualityLabel = qualityBreakdown.getLabel(),
            remarks = currentRepRemarks.toList(),
            qualityBreakdown = qualityBreakdown,
            angleMetrics = angleMetrics,
            eccentricDuration = eccentricDur,
            concentricDuration = concentricDur,
            wasInvalidated = false,
            invalidReason = ""
        )
        
        repDetails.add(detail)
        currentRepRemarks.clear()
        currentRepAngles.reset()
        movement.currentRepStart = null
        movement.eccentricStartTime = null
        movement.eccentricEndTime = null
        movement.concentricStartTime = null
        return detail
    }
    
    private fun calculateQualityBreakdown(
        metrics: AngleMetrics,
        duration: Float,
        eccentricDur: Float,
        concentricDur: Float
    ): QualityBreakdown {
        // 1. Symmetry Score (based on exercise type)
        val symmetryScore: Float
        val symmetryDetail: String
        when (exercise.id) {
            "squat" -> {
                symmetryScore = ((metrics.kneeSymmetryScore + metrics.hipSymmetryScore) / 2 * 100)
                symmetryDetail = "Knee diff: ${(1 - metrics.kneeSymmetryScore) * 50}°"
            }
            "shoulder_press", "lateral_raise" -> {
                symmetryScore = ((metrics.elbowSymmetryScore + metrics.shoulderSymmetryScore) / 2 * 100)
                symmetryDetail = "Arm diff: ${(1 - metrics.shoulderSymmetryScore) * 40}°"
            }
            "lunge" -> {
                symmetryScore = metrics.hipSymmetryScore * 100
                symmetryDetail = "Hip alignment: ${symmetryScore.toInt()}%"
            }
            else -> {
                symmetryScore = 70f
                symmetryDetail = "N/A"
            }
        }
        
        // 2. Range of Motion Score
        val expectedRange = EXPECTED_ROM[exercise.id] ?: Pair(90f, 170f)
        val expectedRom = expectedRange.second - expectedRange.first
        val actualRom = metrics.primaryRomDegrees
        val romRatio = (actualRom / expectedRom).coerceIn(0f, 1.2f)  // Allow up to 120%
        val romScore = when {
            romRatio >= 0.9f -> 100f
            romRatio >= 0.8f -> 85f + (romRatio - 0.8f) * 150f
            romRatio >= 0.7f -> 70f + (romRatio - 0.7f) * 150f
            romRatio >= 0.5f -> 50f + (romRatio - 0.5f) * 100f
            else -> romRatio * 100f
        }
        val romDetail = "ROM: ${actualRom.toInt()}° / ${expectedRom.toInt()}° expected"
        
        // 3. Control Score (based on rep duration and consistency)
        val idealDuration = when (exercise.id) {
            "squat" -> 3.0f
            "shoulder_press" -> 2.5f
            "lunge" -> 3.5f
            "lateral_raise" -> 2.0f
            else -> 2.5f
        }
        val durationRatio = duration / idealDuration
        val controlScore = when {
            durationRatio in 0.7f..1.3f -> 100f
            durationRatio in 0.5f..0.7f || durationRatio in 1.3f..1.5f -> 80f
            durationRatio < 0.5f -> 60f  // Too fast
            else -> 70f  // Too slow
        }
        val phaseBalance = if (eccentricDur > 0 && concentricDur > 0) {
            val ratio = eccentricDur / concentricDur
            if (ratio in 0.7f..1.5f) "balanced" else if (ratio < 0.7f) "rushing down" else "slow return"
        } else "N/A"
        val controlDetail = "${duration}s duration, $phaseBalance"
        
        // 4. Form Score (based on remarks/issues)
        val issueCount = currentRepRemarks.size
        val formScore = when {
            issueCount == 0 -> 100f
            issueCount == 1 -> 80f
            issueCount == 2 -> 65f
            issueCount == 3 -> 50f
            else -> 35f
        }
        val formDetail = if (issueCount == 0) "No form issues" else "${issueCount} issue(s) noted"
        
        return QualityBreakdown.calculate(
            symmetry = symmetryScore,
            rom = romScore,
            control = controlScore,
            form = formScore,
            symmetryDetail = symmetryDetail,
            romDetail = romDetail,
            controlDetail = controlDetail,
            formDetail = formDetail
        )
    }
    
    fun addRepRemark(remark: String) {
        if (remark !in currentRepRemarks) {
            currentRepRemarks.add(remark)
        }
    }
    
    fun invalidateCurrentRep(reason: String) {
        movement.currentRepInvalid = true
        movement.invalidReason = reason
    }
    
    fun generateAnalytics(): SessionAnalytics {
        if (repDetails.isEmpty()) {
            return SessionAnalytics()
        }
        
        val validReps = repDetails.filter { !it.wasInvalidated }
        val issueMap = mutableMapOf<String, Int>()
        
        var excellentCount = 0
        var goodCount = 0
        var fairCount = 0
        var needsWorkCount = 0
        var poorCount = 0
        
        var sumSymmetry = 0f
        var sumRom = 0f
        var sumControl = 0f
        var sumForm = 0f
        var sumTotal = 0f
        var totalDuration = 0f
        var minDuration = Float.MAX_VALUE
        var maxDuration = 0f
        var maxRom = 0f
        var minRom = Float.MAX_VALUE
        var sumRomValue = 0f
        
        validReps.forEach { rep ->
            // Quality distribution
            when {
                rep.qualityBreakdown.totalScore >= 85 -> excellentCount++
                rep.qualityBreakdown.totalScore >= 70 -> goodCount++
                rep.qualityBreakdown.totalScore >= 55 -> fairCount++
                rep.qualityBreakdown.totalScore >= 40 -> needsWorkCount++
                else -> poorCount++
            }
            
            // Score sums
            sumSymmetry += rep.qualityBreakdown.symmetryScore
            sumRom += rep.qualityBreakdown.rangeOfMotionScore
            sumControl += rep.qualityBreakdown.controlScore
            sumForm += rep.qualityBreakdown.formScore
            sumTotal += rep.qualityBreakdown.totalScore
            
            // Duration
            totalDuration += rep.durationSeconds
            minDuration = minOf(minDuration, rep.durationSeconds)
            maxDuration = maxOf(maxDuration, rep.durationSeconds)
            
            // ROM
            val rom = rep.angleMetrics.primaryRomDegrees
            sumRomValue += rom
            maxRom = maxOf(maxRom, rom)
            minRom = minOf(minRom, rom)
            
            // Issues
            rep.remarks.forEach { remark ->
                issueMap[remark] = (issueMap[remark] ?: 0) + 1
            }
        }
        
        val count = validReps.size.toFloat()
        val avgTotal = sumTotal / count
        
        return SessionAnalytics(
            totalReps = repDetails.size,
            validReps = validReps.size,
            invalidReps = repDetails.size - validReps.size,
            excellentCount = excellentCount,
            goodCount = goodCount,
            fairCount = fairCount,
            needsWorkCount = needsWorkCount,
            poorCount = poorCount,
            averageSymmetry = sumSymmetry / count,
            averageRom = sumRom / count,
            averageControl = sumControl / count,
            averageForm = sumForm / count,
            averageTotal = avgTotal,
            averageRepDuration = totalDuration / count,
            fastestRep = if (minDuration != Float.MAX_VALUE) minDuration else 0f,
            slowestRep = maxDuration,
            totalActiveTime = totalDuration,
            bestRom = maxRom,
            worstRom = if (minRom != Float.MAX_VALUE) minRom else 0f,
            issueFrequency = issueMap.toMap(),
            topImprovementAreas = issueMap.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key },
            overallGrade = QualityBreakdown(totalScore = avgTotal).getGrade(),
            overallLabel = QualityBreakdown(totalScore = avgTotal).getLabel()
        )
    }
    
    fun generateReport(): SessionReport {
        val analytics = generateAnalytics()
        val avgQuality = if (repDetails.isEmpty()) 0 
            else (repDetails.map { it.qualityScore }.average() * 100).toInt()
        
        val durationMinutes = (System.currentTimeMillis() - startTime) / 60000f
        
        return SessionReport(
            sessionId = sessionId,
            exercise = exercise,
            startTime = startTime,
            durationMinutes = durationMinutes,
            totalReps = repDetails.size,
            averageQuality = avgQuality,
            reps = repDetails.toList(),
            analytics = analytics
        )
    }
    
    fun generateSummary(): SessionSummary {
        val analytics = generateAnalytics()
        val validReps = repDetails.count { !it.wasInvalidated }
        
        return SessionSummary(
            totalReps = movement.repCount,
            targetReps = exercise.targetReps,
            averageQuality = analytics.averageTotal.toInt(),
            durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt(),
            validReps = validReps,
            overallGrade = analytics.overallGrade,
            overallLabel = analytics.overallLabel,
            completionPercentage = (movement.repCount.toFloat() / exercise.targetReps * 100).coerceIn(0f, 100f),
            topIssue = analytics.topImprovementAreas.firstOrNull()
        )
    }
}
