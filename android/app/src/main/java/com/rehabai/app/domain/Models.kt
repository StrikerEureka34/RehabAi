package com.rehabai.app.domain

enum class SessionState {
    POSITIONING,
    VERIFYING,
    READY,
    ACTIVE,
    UNCERTAINTY,
    COMPLETED
}

enum class MovementPhase {
    IDLE,
    ECCENTRIC,
    CONCENTRIC,
    TRANSITION,
    HOLD
}

enum class FeedbackType {
    GUIDANCE,
    POSITIVE,
    CORRECTIVE,
    WARNING
}

data class Exercise(
    val id: String,
    val name: String,
    val description: String,
    val targetReps: Int,
    val muscleGroups: List<String>
)

data class Feedback(
    val message: String,
    val type: FeedbackType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Detailed angle measurements captured during a rep.
 * All angles are in degrees.
 */
data class AngleMetrics(
    // Knee angles (important for squats, lunges)
    val leftKneeAngle: Float = 0f,
    val rightKneeAngle: Float = 0f,
    val kneeSymmetryScore: Float = 0f,  // 0-1, 1 = perfect symmetry
    
    // Hip angles (posture assessment)
    val leftHipAngle: Float = 0f,
    val rightHipAngle: Float = 0f,
    val hipSymmetryScore: Float = 0f,
    
    // Elbow angles (important for shoulder press, lateral raise)
    val leftElbowAngle: Float = 0f,
    val rightElbowAngle: Float = 0f,
    val elbowSymmetryScore: Float = 0f,
    
    // Shoulder angles (arm positioning)
    val leftShoulderAngle: Float = 0f,
    val rightShoulderAngle: Float = 0f,
    val shoulderSymmetryScore: Float = 0f,
    
    // ROM (Range of Motion) - difference between max and min angles during rep
    val primaryRomDegrees: Float = 0f,  // Main movement ROM
    val expectedMinAngle: Float = 0f,   // Expected minimum for this exercise
    val expectedMaxAngle: Float = 0f,   // Expected maximum for this exercise
    val actualMinAngle: Float = 0f,     // Achieved minimum
    val actualMaxAngle: Float = 0f      // Achieved maximum
)

/**
 * Quality breakdown with weighted scoring components
 */
data class QualityBreakdown(
    val symmetryScore: Float = 0f,      // 0-100: How symmetric was the movement
    val rangeOfMotionScore: Float = 0f, // 0-100: Did they achieve full ROM
    val controlScore: Float = 0f,       // 0-100: Was the movement controlled (not too fast)
    val formScore: Float = 0f,          // 0-100: Were key angles in acceptable range
    val totalScore: Float = 0f,         // 0-100: Weighted average
    
    // Breakdown explanation
    val symmetryDetails: String = "",
    val romDetails: String = "",
    val controlDetails: String = "",
    val formDetails: String = ""
) {
    companion object {
        // Weights for final score calculation
        const val SYMMETRY_WEIGHT = 0.25f
        const val ROM_WEIGHT = 0.30f
        const val CONTROL_WEIGHT = 0.20f
        const val FORM_WEIGHT = 0.25f
        
        fun calculate(
            symmetry: Float,
            rom: Float,
            control: Float,
            form: Float,
            symmetryDetail: String = "",
            romDetail: String = "",
            controlDetail: String = "",
            formDetail: String = ""
        ): QualityBreakdown {
            val total = (symmetry * SYMMETRY_WEIGHT + 
                        rom * ROM_WEIGHT + 
                        control * CONTROL_WEIGHT + 
                        form * FORM_WEIGHT)
            return QualityBreakdown(
                symmetryScore = symmetry,
                rangeOfMotionScore = rom,
                controlScore = control,
                formScore = form,
                totalScore = total,
                symmetryDetails = symmetryDetail,
                romDetails = romDetail,
                controlDetails = controlDetail,
                formDetails = formDetail
            )
        }
    }
    
    fun getGrade(): String = when {
        totalScore >= 90 -> "A+"
        totalScore >= 85 -> "A"
        totalScore >= 80 -> "A-"
        totalScore >= 75 -> "B+"
        totalScore >= 70 -> "B"
        totalScore >= 65 -> "B-"
        totalScore >= 60 -> "C+"
        totalScore >= 55 -> "C"
        totalScore >= 50 -> "C-"
        totalScore >= 40 -> "D"
        else -> "F"
    }
    
    fun getLabel(): String = when {
        totalScore >= 85 -> "Excellent"
        totalScore >= 70 -> "Good"
        totalScore >= 55 -> "Fair"
        totalScore >= 40 -> "Needs Work"
        else -> "Poor"
    }
}

data class RepDetail(
    val repNumber: Int,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Float,
    val qualityScore: Float,
    val qualityLabel: String,
    val remarks: List<String>,
    
    // Enhanced analytics
    val qualityBreakdown: QualityBreakdown = QualityBreakdown(),
    val angleMetrics: AngleMetrics = AngleMetrics(),
    val eccentricDuration: Float = 0f,  // Time going down
    val concentricDuration: Float = 0f, // Time coming up
    val wasInvalidated: Boolean = false,
    val invalidReason: String = ""
)

/**
 * Aggregated statistics for all reps in a session
 */
data class SessionAnalytics(
    val totalReps: Int = 0,
    val validReps: Int = 0,
    val invalidReps: Int = 0,
    
    // Quality distribution
    val excellentCount: Int = 0,
    val goodCount: Int = 0,
    val fairCount: Int = 0,
    val needsWorkCount: Int = 0,
    val poorCount: Int = 0,
    
    // Score averages
    val averageSymmetry: Float = 0f,
    val averageRom: Float = 0f,
    val averageControl: Float = 0f,
    val averageForm: Float = 0f,
    val averageTotal: Float = 0f,
    
    // Time metrics
    val averageRepDuration: Float = 0f,
    val fastestRep: Float = 0f,
    val slowestRep: Float = 0f,
    val totalActiveTime: Float = 0f,
    
    // Angle analysis
    val bestRom: Float = 0f,
    val worstRom: Float = 0f,
    
    // Common issues (issue -> count)
    val issueFrequency: Map<String, Int> = emptyMap(),
    
    // Improvement areas (most frequent issues)
    val topImprovementAreas: List<String> = emptyList(),
    
    // Grade
    val overallGrade: String = "N/A",
    val overallLabel: String = "Not assessed"
)

data class SessionReport(
    val sessionId: String,
    val exercise: Exercise,
    val startTime: Long,
    val durationMinutes: Float,
    val totalReps: Int,
    val averageQuality: Int,
    val reps: List<RepDetail>,
    
    // Enhanced analytics
    val analytics: SessionAnalytics = SessionAnalytics()
)

data class SessionSummary(
    val totalReps: Int,
    val targetReps: Int,
    val averageQuality: Int,
    val durationSeconds: Int,
    
    // Enhanced summary
    val validReps: Int = 0,
    val overallGrade: String = "N/A",
    val overallLabel: String = "Not assessed",
    val completionPercentage: Float = 0f,
    val topIssue: String? = null
)

/**
 * Video recording metadata for dashboard
 */
data class ExerciseRecording(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val recordedAt: Long,
    val durationSeconds: Int,
    val filePath: String,
    val thumbnailPath: String? = null,
    val repsCompleted: Int,
    val averageQuality: Int,
    val fileSize: Long = 0L
)
