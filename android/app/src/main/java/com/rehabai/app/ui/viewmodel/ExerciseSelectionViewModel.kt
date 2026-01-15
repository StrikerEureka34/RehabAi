package com.rehabai.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rehabai.app.RehabAIApplication
import com.rehabai.app.domain.Exercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExerciseSelectionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as RehabAIApplication).sessionRepository
    
    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises.asStateFlow()
    
    private val _selectedExercise = MutableStateFlow<Exercise?>(null)
    val selectedExercise: StateFlow<Exercise?> = _selectedExercise.asStateFlow()
    
    init {
        loadExercises()
    }
    
    private fun loadExercises() {
        _exercises.value = repository.getExercises()
    }
    
    fun selectExercise(exercise: Exercise) {
        _selectedExercise.value = exercise
    }
    
    fun clearSelection() {
        _selectedExercise.value = null
    }
}
