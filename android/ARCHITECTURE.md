# RehabAI Android App - Architecture & Implementation Guide

## Overview

This is a production-grade physiotherapy exercise guidance app built with modern Android architecture. It mirrors the web app's functionality with proper Android lifecycle handling.

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/rehabai/app/
│   │   │   ├── MainActivity.kt           # Entry point
│   │   │   ├── RehabAIApplication.kt     # Application class
│   │   │   ├── SessionRepository.kt      # Session state holder
│   │   │   ├── domain/
│   │   │   │   ├── Models.kt             # Data classes
│   │   │   │   └── Exercises.kt          # Exercise definitions
│   │   │   ├── session/
│   │   │   │   ├── ExerciseSessionState.kt   # Session state machine
│   │   │   │   └── SessionController.kt      # Business logic
│   │   │   ├── ml/
│   │   │   │   ├── PoseAnalyzer.kt       # ML Kit integration
│   │   │   │   └── PoseMapper.kt         # Keypoint mapping
│   │   │   └── ui/
│   │   │       ├── theme/
│   │   │       │   └── Theme.kt          # Dark theme
│   │   │       ├── navigation/
│   │   │       │   └── Navigation.kt     # Nav host
│   │   │       ├── viewmodel/
│   │   │       │   ├── SessionViewModel.kt
│   │   │       │   └── ExerciseSelectionViewModel.kt
│   │   │       └── screens/
│   │   │           ├── ExerciseSelectionScreen.kt
│   │   │           └── SessionScreen.kt
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Architecture Decisions

### State Machine

The session follows these states (matching web app):

```
POSITIONING → VERIFYING → READY → ACTIVE → COMPLETED
                  ↓
             UNCERTAINTY
```

- **POSITIONING**: User needs to be visible in frame
- **VERIFYING**: Pose detected, confirming position (15 frames)
- **READY**: Confirmed, waiting for movement
- **ACTIVE**: Counting reps, providing feedback
- **COMPLETED**: Session ended

### ViewModel Ownership

```kotlin
SessionViewModel
├── ExerciseSessionState (owned)
├── SessionController (processing)
├── UiState (exposed as StateFlow)
└── PoseData (for skeleton overlay)
```

The ViewModel:
- Owns the session state
- Survives configuration changes
- Processes frames off the UI thread
- Exposes immutable state via StateFlow

### ML Inference Pipeline

```
CameraX → ImageAnalysis → PoseAnalyzer → ML Kit → SessionController → ViewModel
         (background)    (throttled)    (async)   (pure functions)   (UI thread)
```

Key decisions:
1. **Throttled to ~15 FPS** - Prevents thermal issues during long sessions
2. **STRATEGY_KEEP_ONLY_LATEST** - Drops frames if processing is slow
3. **Accurate model** - Better accuracy over speed for medical use
4. **Atomic processing flag** - Prevents concurrent processing

### Form Validation (Same as Web App)

The app validates form identically to the web version:

**Critical errors (rep not counted):**
- Squat: knee_diff > 35°, hip_diff > 25°
- Shoulder Press: elbow_diff > 50°, shoulder_diff > 40°
- Lunge: elbow_diff > 60°, avg_hip < 140°, min_knee < 70°
- Lateral Raise: shoulder_diff > 50°, avg_shoulder < 30°, avg_elbow < 100°

**Minor errors (warning only):**
- Squat: knee_diff > 20°, avg_knee > 140° during eccentric
- Shoulder Press: elbow_diff > 25°, shoulder_diff > 20°
- Lunge: min_knee > 145° during eccentric
- Lateral Raise: shoulder_diff > 20°

### Rep Counting Logic

Uses cycle-based counting (same as web):

```kotlin
1. Detect ECCENTRIC phase → set seenEccentric = true, start rep timer
2. Detect CONCENTRIC phase (after eccentric) → complete rep
3. If currentRepInvalid is true → skip counting, reset flag
4. Minimum 800ms between reps
```

## Lifecycle Handling

### Camera Lifecycle

```kotlin
// Camera is bound to LifecycleOwner
cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
```

When app is backgrounded:
1. CameraX automatically stops camera
2. PoseAnalyzer stops receiving frames
3. Session state is preserved in ViewModel
4. On resume, camera restarts automatically

### Session Survival

```kotlin
// SessionRepository holds state
class SessionRepository {
    private val _currentSession = MutableStateFlow<ExerciseSessionState?>(null)
    // Survives backgrounding
}

// ViewModel survives configuration changes
class SessionViewModel(application: Application) : AndroidViewModel(application)
```

### Screen Lock Handling

The manifest includes:
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
android:configChanges="orientation|screenSize|keyboardHidden"
```

## Performance Considerations

### Frame Throttling

```kotlin
private val minFrameIntervalMs = 66L  // ~15 FPS

if (now - lastProcessTime.get() < minFrameIntervalMs) {
    imageProxy.close()
    return
}
```

### Thermal Management

1. Accurate model runs at lower FPS than Lightning
2. Backpressure strategy drops frames
3. Processing happens on background executor

### Memory Management

```kotlin
// Angle buffer has fixed size
val angleBuffer = ArrayDeque<FloatArray>(SEQUENCE_LENGTH)

// Pose data is overwritten, not accumulated
_poseData.value = pose
```

## Critical Implementation Cautions

### Lifecycle Traps

1. **Never hold camera references** - Let CameraX manage lifecycle
2. **Close detector on dispose** - `analyzer?.close()` in DisposableEffect
3. **Don't process in background when paused** - Atomic flag prevents this

### Performance Pitfalls

1. **Don't process every frame** - Throttle to prevent thermal issues
2. **Don't block UI thread** - Use viewModelScope.launch
3. **Don't accumulate history** - Fixed-size buffers only

### UX Anti-Patterns Avoided

1. **No flickering feedback** - MIN_FEEDBACK_INTERVAL_MS = 1000
2. **No false rep counts** - Form validation rejects bad reps
3. **No jarring state changes** - Stable state requires multiple frames
4. **No AI jargon** - Human-phrased feedback only

## Testing the App

1. Open the project in Android Studio
2. Sync Gradle files
3. Connect a device or start an emulator
4. Run the app
5. Grant camera permission when prompted
6. Select an exercise
7. Position yourself in frame
8. Start exercising

## Dependencies

- **CameraX 1.3.1** - Camera management
- **ML Kit Pose Detection 18.0.0-beta3** - Pose detection
- **Compose BOM 2023.10.01** - UI framework
- **Accompanist Permissions 0.32.0** - Permission handling
- **Kotlin Coroutines 1.7.3** - Async processing

## Key Files Reference

| File | Purpose |
|------|---------|
| `ExerciseSessionState.kt` | Session state machine (matches web `ExerciseSession`) |
| `SessionController.kt` | Processing logic (matches web `process_frame`) |
| `PoseAnalyzer.kt` | Camera frame → ML Kit integration |
| `SessionViewModel.kt` | UI state management |
| `SessionScreen.kt` | Main exercise UI with camera |
