package com.rehabai.app.domain

object Exercises {
    
    val all = mapOf(
        "squat" to Exercise(
            id = "squat",
            name = "Squat",
            description = "Lower body strengthening",
            targetReps = 10,
            muscleGroups = listOf("Quadriceps", "Glutes", "Hamstrings")
        ),
        "shoulder_press" to Exercise(
            id = "shoulder_press",
            name = "Shoulder Press",
            description = "Upper body shoulder exercise",
            targetReps = 12,
            muscleGroups = listOf("Deltoids", "Triceps", "Upper Back")
        ),
        "lunge" to Exercise(
            id = "lunge",
            name = "Lunge",
            description = "Lower body balance exercise",
            targetReps = 8,
            muscleGroups = listOf("Quadriceps", "Glutes", "Hamstrings")
        ),
        "lateral_raise" to Exercise(
            id = "lateral_raise",
            name = "Lateral Raise",
            description = "Shoulder isolation exercise",
            targetReps = 12,
            muscleGroups = listOf("Deltoids", "Trapezius")
        )
    )
    
    fun get(id: String): Exercise? = all[id]
    
    fun list(): List<Exercise> = all.values.toList()
}

object FeedbackMessages {
    
    private val messages = mapOf(
        "squat" to mapOf(
            "positioning" to "Stand with feet shoulder-width apart.",
            "ready" to "Ready when you are.",
            "go_lower" to "Go slightly lower.",
            "align_knees" to "Keep knees aligned.",
            "uneven_stance" to "Even out your stance.",
            "lean_forward" to "Keep your chest up."
        ),
        "shoulder_press" to mapOf(
            "positioning" to "Hold weights at shoulder height.",
            "ready" to "Ready when you are.",
            "extend_fully" to "Extend arms fully overhead.",
            "keep_symmetry" to "Move both arms at the same rate.",
            "arms_uneven" to "Keep arms at equal height.",
            "shrugging" to "Relax your shoulders."
        ),
        "lunge" to mapOf(
            "positioning" to "Stand upright with feet together.",
            "ready" to "Ready when you are.",
            "go_lower" to "Go slightly lower.",
            "upright_torso" to "Keep torso upright.",
            "knee_past_toes" to "Front knee shouldn't pass toes.",
            "step_wider" to "Step out wider."
        ),
        "lateral_raise" to mapOf(
            "positioning" to "Stand with arms at your sides.",
            "ready" to "Ready when you are.",
            "raise_higher" to "Raise arms to shoulder height.",
            "not_too_high" to "Don't go above shoulder level.",
            "arms_uneven" to "Raise both arms at the same rate.",
            "bent_elbows" to "Keep slight bend in elbows."
        )
    )
    
    fun get(exerciseId: String, key: String): String {
        return messages[exerciseId]?.get(key) ?: "Position yourself in frame."
    }
}
