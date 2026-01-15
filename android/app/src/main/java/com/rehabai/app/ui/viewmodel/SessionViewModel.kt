package com.rehabai.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.pose.Pose
import com.rehabai.app.RehabAIApplication
import com.rehabai.app.domain.*
import com.rehabai.app.ml.PoseMapper
import com.rehabai.app.session.ExerciseSessionState
import com.rehabai.app.session.SessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val sessionState: SessionState = SessionState.POSITIONING,
    val isConfirmed: Boolean = false,
    val repCount: Int = 0,
    val targetReps: Int = 10,
    val currentFeedback: Feedback? = null,
    val movementPhase: MovementPhase? = null,
    val progress: Float = 0f,
    val isSessionActive: Boolean = false,
    val showCompletionDialog: Boolean = false,
    val summary: SessionSummary? = null,
    val report: SessionReport? = null,
    val imageWidth: Int = 640,
    val imageHeight: Int = 480
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as RehabAIApplication).sessionRepository
    private val controller = SessionController()
    
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private val _currentExercise = MutableStateFlow<Exercise?>(null)
    val currentExercise: StateFlow<Exercise?> = _currentExercise.asStateFlow()
    
    private val _poseData = MutableStateFlow<Pose?>(null)
    val poseData: StateFlow<Pose?> = _poseData.asStateFlow()
    
    private var session: ExerciseSessionState? = null
    
    fun startSession(exerciseId: String) {
        session = repository.startSession(exerciseId)
        session?.let { s ->
            _currentExercise.value = s.exercise
            _uiState.value = SessionUiState(
                sessionState = s.state,
                targetReps = s.exercise.targetReps,
                isSessionActive = true
            )
        }
    }
    
    fun processFrame(pose: Pose, imageWidth: Int, imageHeight: Int) {
        val currentSession = session ?: return
        
        viewModelScope.launch {
            val keypoints = PoseMapper.mapPoseToKeypoints(pose, imageWidth, imageHeight)
            
            val result = controller.processFrame(currentSession, keypoints) { feedback ->
                // Feedback callback - already handled in result
            }
            
            _poseData.value = pose
            
            _uiState.value = _uiState.value.copy(
                sessionState = result.sessionState,
                isConfirmed = result.exerciseConfirmed,
                repCount = result.repCount,
                movementPhase = result.movementPhase,
                currentFeedback = result.feedback ?: _uiState.value.currentFeedback,
                progress = result.repCount.toFloat() / currentSession.exercise.targetReps,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )
        }
    }
    
    fun onNoPoseDetected() {
        if (_uiState.value.isSessionActive && _uiState.value.sessionState == SessionState.ACTIVE) {
            // Don't immediately show "step into frame" - wait a bit
            // This prevents flickering during brief detection gaps
        }
    }
    
    fun endSession() {
        val result = repository.endSession()
        result?.let { (summary, report) ->
            _uiState.value = _uiState.value.copy(
                isSessionActive = false,
                showCompletionDialog = true,
                summary = summary,
                report = report
            )
        }
        session = null
        _poseData.value = null
    }
    
    fun dismissCompletionDialog() {
        _uiState.value = _uiState.value.copy(showCompletionDialog = false)
    }
    
    fun resetState() {
        session = null
        _poseData.value = null
        _currentExercise.value = null
        _uiState.value = SessionUiState()
    }
}
