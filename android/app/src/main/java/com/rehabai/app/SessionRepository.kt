package com.rehabai.app

import com.rehabai.app.domain.Exercise
import com.rehabai.app.domain.Exercises
import com.rehabai.app.domain.SessionReport
import com.rehabai.app.domain.SessionSummary
import com.rehabai.app.session.ExerciseSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepository {
    
    private val _currentSession = MutableStateFlow<ExerciseSessionState?>(null)
    val currentSession: StateFlow<ExerciseSessionState?> = _currentSession.asStateFlow()
    
    private val _lastReport = MutableStateFlow<SessionReport?>(null)
    val lastReport: StateFlow<SessionReport?> = _lastReport.asStateFlow()
    
    private val _lastSummary = MutableStateFlow<SessionSummary?>(null)
    val lastSummary: StateFlow<SessionSummary?> = _lastSummary.asStateFlow()
    
    fun startSession(exerciseId: String): ExerciseSessionState? {
        val exercise = Exercises.get(exerciseId) ?: return null
        val sessionId = "session_${System.currentTimeMillis()}"
        val session = ExerciseSessionState(sessionId, exercise)
        _currentSession.value = session
        return session
    }
    
    fun getSession(): ExerciseSessionState? = _currentSession.value
    
    fun endSession(): Pair<SessionSummary, SessionReport>? {
        val session = _currentSession.value ?: return null
        
        val summary = session.generateSummary()
        val report = session.generateReport()
        
        _lastSummary.value = summary
        _lastReport.value = report
        _currentSession.value = null
        
        return Pair(summary, report)
    }
    
    fun clearSession() {
        _currentSession.value = null
    }
    
    fun getExercises(): List<Exercise> = Exercises.list()
}
