package com.rehabai.app.ui.screens

import android.Manifest
import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.rehabai.app.domain.FeedbackType
import com.rehabai.app.domain.SessionState
import com.rehabai.app.ml.PoseAnalyzer
import com.rehabai.app.ml.PoseMapper
import com.rehabai.app.ui.viewmodel.SessionUiState
import com.rehabai.app.ui.viewmodel.SessionViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SessionScreen(
    exerciseId: String,
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onSessionComplete: () -> Unit,
    onViewAnalytics: (com.rehabai.app.domain.SessionReport) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentExercise by viewModel.currentExercise.collectAsState()
    val poseData by viewModel.poseData.collectAsState()
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    LaunchedEffect(exerciseId) {
        viewModel.startSession(exerciseId)
    }
    
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentExercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endSession()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cameraPermissionState.status.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CameraPreviewWithPose(
                        viewModel = viewModel,
                        poseData = poseData
                    )
                    
                    // State badge
                    StateBadge(
                        state = uiState.sessionState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    )
                }
                
                SessionInfoPanel(
                    uiState = uiState,
                    exerciseName = currentExercise?.name ?: "",
                    onEndSession = { viewModel.endSession() }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission required")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
        
        if (uiState.showCompletionDialog) {
            CompletionDialog(
                summary = uiState.summary,
                report = uiState.report,
                onDismiss = {
                    viewModel.dismissCompletionDialog()
                    onSessionComplete()
                },
                onViewAnalytics = {
                    uiState.report?.let { report ->
                        viewModel.dismissCompletionDialog()
                        onViewAnalytics(report)
                    }
                },
                onStartNew = {
                    viewModel.dismissCompletionDialog()
                    viewModel.resetState()
                    onBack()
                }
            )
        }
    }
}

@Composable
fun CameraPreviewWithPose(
    viewModel: SessionViewModel,
    poseData: Pose?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var analyzer by remember { mutableStateOf<PoseAnalyzer?>(null) }
    
    DisposableEffect(Unit) {
        onDispose {
            analyzer?.close()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { pv ->
                    previewView = pv
                    startCamera(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        previewView = pv,
                        onPoseDetected = { pose, w, h ->
                            viewModel.processFrame(pose, w, h)
                        },
                        onNoPoseDetected = {
                            viewModel.onNoPoseDetected()
                        },
                        onAnalyzerCreated = { a -> analyzer = a }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Skeleton overlay with actual image dimensions
        poseData?.let { pose ->
            val uiState by viewModel.uiState.collectAsState()
            SkeletonOverlay(
                pose = pose,
                imageWidth = uiState.imageWidth,
                imageHeight = uiState.imageHeight,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SkeletonOverlay(
    pose: Pose,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    val connections = remember { PoseMapper.getSkeletonConnections() }
    val lineColor = Color(0xFF60A5FA)
    val pointColor = Color(0xFFFACC15)
    
    // Use actual image dimensions, with fallback for safety
    val actualImageWidth = if (imageWidth > 0) imageWidth.toFloat() else 640f
    val actualImageHeight = if (imageHeight > 0) imageHeight.toFloat() else 480f
    
    Canvas(modifier = modifier) {
        val scaleX = size.width / actualImageWidth
        val scaleY = size.height / actualImageHeight
        
        // Draw connections
        connections.forEach { (startType, endType) ->
            val startLandmark = pose.getPoseLandmark(startType)
            val endLandmark = pose.getPoseLandmark(endType)
            
            if (startLandmark != null && endLandmark != null &&
                startLandmark.inFrameLikelihood > 0.3f && 
                endLandmark.inFrameLikelihood > 0.3f) {
                
                // Mirror horizontally for front camera and scale to canvas size
                val startX = size.width - (startLandmark.position.x * scaleX)
                val startY = startLandmark.position.y * scaleY
                val endX = size.width - (endLandmark.position.x * scaleX)
                val endY = endLandmark.position.y * scaleY
                
                drawLine(
                    color = lineColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 8f
                )
            }
        }
        
        // Draw keypoints
        pose.allPoseLandmarks.forEach { landmark ->
            if (landmark.inFrameLikelihood > 0.3f) {
                val x = size.width - (landmark.position.x * scaleX)
                val y = landmark.position.y * scaleY
                
                drawCircle(
                    color = Color(0xFF1A1A2E),
                    radius = 14f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = pointColor,
                    radius = 10f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun StateBadge(
    state: SessionState,
    modifier: Modifier = Modifier
) {
    val (text, containerColor, contentColor) = when (state) {
        SessionState.POSITIONING -> Triple("Positioning", Color(0xFF2D2D44), Color(0xFF94A3B8))
        SessionState.VERIFYING -> Triple("Verifying", Color(0xFF78350F), Color(0xFFFBBF24))
        SessionState.READY -> Triple("Ready", Color(0xFF1E3A5F), Color(0xFF60A5FA))
        SessionState.ACTIVE -> Triple("Active", Color(0xFF14532D), Color(0xFF4ADE80))
        SessionState.UNCERTAINTY -> Triple("Confirm", Color(0xFF78350F), Color(0xFFFBBF24))
        SessionState.COMPLETED -> Triple("Complete", Color(0xFF14532D), Color(0xFF4ADE80))
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

@Composable
fun SessionInfoPanel(
    uiState: SessionUiState,
    exerciseName: String,
    onEndSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "Reps",
                value = "${uiState.repCount}",
                subtext = "of ${uiState.targetReps}"
            )
            StatItem(
                label = "Progress",
                value = "${(uiState.progress * 100).toInt()}%",
                subtext = null
            )
        }
        
        // Progress bar
        LinearProgressIndicator(
            progress = uiState.progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        
        // Feedback card
        FeedbackCard(
            message = uiState.currentFeedback?.message ?: "Position yourself in front of the camera.",
            type = uiState.currentFeedback?.type ?: FeedbackType.GUIDANCE
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // End session button
        Button(
            onClick = onEndSession,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text("End Session")
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    subtext: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtext != null) {
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FeedbackCard(
    message: String,
    type: FeedbackType
) {
    val (iconColor, backgroundColor) = when (type) {
        FeedbackType.GUIDANCE -> Color(0xFF60A5FA) to Color(0xFF1E3A5F)
        FeedbackType.POSITIVE -> Color(0xFF4ADE80) to Color(0xFF14532D)
        FeedbackType.CORRECTIVE -> Color(0xFFFBBF24) to Color(0xFF78350F)
        FeedbackType.WARNING -> Color(0xFFEF4444) to Color(0xFF7F1D1D)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun CompletionDialog(
    summary: com.rehabai.app.domain.SessionSummary?,
    report: com.rehabai.app.domain.SessionReport?,
    onDismiss: () -> Unit,
    onViewAnalytics: () -> Unit,
    onStartNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Session Complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                summary?.let { s ->
                    // Overall Grade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    s.overallGrade.startsWith("A") -> Color(0xFF14532D)
                                    s.overallGrade.startsWith("B") -> Color(0xFF1E3A5F)
                                    s.overallGrade.startsWith("C") -> Color(0xFF78350F)
                                    else -> Color(0xFF7F1D1D)
                                }
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = s.overallGrade,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = s.overallLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CompletionStatItem(
                            value = "${s.totalReps}/${s.targetReps}",
                            label = "Reps"
                        )
                        CompletionStatItem(
                            value = "${s.completionPercentage.toInt()}%",
                            label = "Completion"
                        )
                        val mins = s.durationSeconds / 60
                        val secs = s.durationSeconds % 60
                        CompletionStatItem(
                            value = "$mins:${secs.toString().padStart(2, '0')}",
                            label = "Duration"
                        )
                    }
                    
                    // Quality breakdown from report
                    report?.analytics?.let { analytics ->
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Quality Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        QualityBar("Symmetry", analytics.averageSymmetry)
                        QualityBar("Range of Motion", analytics.averageRom)
                        QualityBar("Control", analytics.averageControl)
                        QualityBar("Form", analytics.averageForm)
                        
                        // Rep distribution
                        if (analytics.totalReps > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Rep Quality Distribution",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QualityCountChip("Excellent", analytics.excellentCount, Color(0xFF4ADE80))
                                QualityCountChip("Good", analytics.goodCount, Color(0xFF60A5FA))
                                QualityCountChip("Fair", analytics.fairCount, Color(0xFFFBBF24))
                                QualityCountChip("Needs Work", analytics.needsWorkCount, Color(0xFFEF4444))
                            }
                        }
                        
                        // Top improvement areas
                        if (analytics.topImprovementAreas.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Areas for Improvement",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            analytics.topImprovementAreas.forEach { area ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFFBBF24))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = area,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onViewAnalytics) {
                    Text("View Details")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onStartNew) {
                    Text("New Session")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun CompletionStatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QualityBar(label: String, score: Float) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${score.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = (score / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                score >= 85 -> Color(0xFF4ADE80)
                score >= 70 -> Color(0xFF60A5FA)
                score >= 55 -> Color(0xFFFBBF24)
                else -> Color(0xFFEF4444)
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun QualityCountChip(label: String, count: Int, color: Color) {
    if (count > 0) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onPoseDetected: (Pose, Int, Int) -> Unit,
    onNoPoseDetected: () -> Unit,
    onAnalyzerCreated: (PoseAnalyzer) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val executor = Executors.newSingleThreadExecutor()
    
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        
        val analyzer = PoseAnalyzer(
            onPoseDetected = onPoseDetected,
            onNoPoseDetected = onNoPoseDetected
        )
        onAnalyzerCreated(analyzer)
        
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, analyzer) }
        
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }, ContextCompat.getMainExecutor(context))
}
