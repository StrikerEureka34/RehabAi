package com.rehabai.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rehabai.app.domain.ExerciseRecording
import com.rehabai.app.domain.Exercises
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onExerciseSelected: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var recordings by remember { mutableStateOf<Map<String, List<ExerciseRecording>>>(emptyMap()) }
    var selectedRecording by remember { mutableStateOf<ExerciseRecording?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordingToDelete by remember { mutableStateOf<ExerciseRecording?>(null) }
    
    // Load recordings on first composition
    LaunchedEffect(Unit) {
        recordings = loadRecordings(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    TextButton(onClick = onExerciseSelected) {
                        Text("New Exercise")
                    }
                }
            )
        }
    ) { padding ->
        if (recordings.isEmpty()) {
            EmptyDashboard(
                onStartExercise = onExerciseSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary stats
                item {
                    DashboardSummaryCard(recordings = recordings)
                }
                
                // Recordings by exercise
                Exercises.list().forEach { exercise ->
                    val exerciseRecordings = recordings[exercise.id] ?: emptyList()
                    if (exerciseRecordings.isNotEmpty()) {
                        item {
                            ExerciseRecordingsSection(
                                exerciseName = exercise.name,
                                recordings = exerciseRecordings.take(2), // Keep only last 2
                                onView = { recording ->
                                    viewRecording(context, recording)
                                },
                                onDelete = { recording ->
                                    recordingToDelete = recording
                                    showDeleteDialog = true
                                },
                                onDownload = { recording ->
                                    scope.launch {
                                        downloadRecording(context, recording)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog && recordingToDelete != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    recordingToDelete = null
                },
                title = { Text("Delete Recording") },
                text = { 
                    Text("Are you sure you want to delete this recording? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            recordingToDelete?.let { recording ->
                                scope.launch {
                                    deleteRecording(context, recording)
                                    recordings = loadRecordings(context)
                                }
                            }
                            showDeleteDialog = false
                            recordingToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        recordingToDelete = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun EmptyDashboard(
    onStartExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Recordings Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Complete an exercise session to see your recordings here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onStartExercise) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Exercise")
        }
    }
}

@Composable
fun DashboardSummaryCard(
    recordings: Map<String, List<ExerciseRecording>>
) {
    val totalRecordings = recordings.values.sumOf { it.size }
    val totalReps = recordings.values.flatten().sumOf { it.repsCompleted }
    val avgQuality = recordings.values.flatten()
        .map { it.averageQuality }
        .average()
        .takeIf { !it.isNaN() }?.toInt() ?: 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    value = "$totalRecordings",
                    label = "Sessions"
                )
                SummaryStatItem(
                    value = "$totalReps",
                    label = "Total Reps"
                )
                SummaryStatItem(
                    value = "$avgQuality%",
                    label = "Avg Quality"
                )
            }
        }
    }
}

@Composable
fun SummaryStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExerciseRecordingsSection(
    exerciseName: String,
    recordings: List<ExerciseRecording>,
    onView: (ExerciseRecording) -> Unit,
    onDelete: (ExerciseRecording) -> Unit,
    onDownload: (ExerciseRecording) -> Unit
) {
    Column {
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        recordings.forEach { recording ->
            RecordingCard(
                recording = recording,
                onView = { onView(recording) },
                onDelete = { onDelete(recording) },
                onDownload = { onDownload(recording) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun RecordingCard(
    recording: ExerciseRecording,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Recording info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(recording.recordedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${recording.repsCompleted} reps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(recording.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    QualityBadge(quality = recording.averageQuality)
                }
            }
            
            // Actions
            Row {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@Composable
fun QualityBadge(quality: Int) {
    val (text, color) = when {
        quality >= 85 -> "A" to Color(0xFF4ADE80)
        quality >= 70 -> "B" to Color(0xFF60A5FA)
        quality >= 55 -> "C" to Color(0xFFFBBF24)
        else -> "D" to Color(0xFFEF4444)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$quality%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private suspend fun loadRecordings(context: Context): Map<String, List<ExerciseRecording>> {
    return withContext(Dispatchers.IO) {
        val recordingsDir = File(context.filesDir, "recordings")
        if (!recordingsDir.exists()) {
            return@withContext emptyMap()
        }
        
        val recordings = mutableMapOf<String, MutableList<ExerciseRecording>>()
        
        recordingsDir.listFiles()?.forEach { exerciseDir ->
            if (exerciseDir.isDirectory) {
                val exerciseId = exerciseDir.name
                val exerciseName = Exercises.get(exerciseId)?.name ?: exerciseId
                
                val exerciseRecordings = exerciseDir.listFiles()
                    ?.filter { it.extension == "mp4" }
                    ?.mapNotNull { file ->
                        // Parse metadata from companion .meta file
                        val metaFile = File(file.parent, "${file.nameWithoutExtension}.meta")
                        parseRecordingMetadata(file, metaFile, exerciseId, exerciseName)
                    }
                    ?.sortedByDescending { it.recordedAt }
                    ?.take(2) // Keep only last 2
                    ?: emptyList()
                
                if (exerciseRecordings.isNotEmpty()) {
                    recordings[exerciseId] = exerciseRecordings.toMutableList()
                }
            }
        }
        
        recordings
    }
}

private fun parseRecordingMetadata(
    file: File,
    metaFile: File,
    exerciseId: String,
    exerciseName: String
): ExerciseRecording {
    // Default values
    var reps = 0
    var quality = 0
    var duration = 0
    
    if (metaFile.exists()) {
        try {
            metaFile.readLines().forEach { line ->
                val parts = line.split("=")
                if (parts.size == 2) {
                    when (parts[0]) {
                        "reps" -> reps = parts[1].toIntOrNull() ?: 0
                        "quality" -> quality = parts[1].toIntOrNull() ?: 0
                        "duration" -> duration = parts[1].toIntOrNull() ?: 0
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }
    
    return ExerciseRecording(
        id = file.nameWithoutExtension,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        recordedAt = file.lastModified(),
        durationSeconds = duration,
        filePath = file.absolutePath,
        thumbnailPath = null,
        repsCompleted = reps,
        averageQuality = quality,
        fileSize = file.length()
    )
}

private fun viewRecording(context: Context, recording: ExerciseRecording) {
    try {
        val file = File(recording.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Recording not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open recording: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun deleteRecording(context: Context, recording: ExerciseRecording) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(recording.filePath)
            val metaFile = File(file.parent, "${file.nameWithoutExtension}.meta")
            
            file.delete()
            metaFile.delete()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Recording deleted", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun downloadRecording(context: Context, recording: ExerciseRecording) {
    withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(recording.filePath)
            if (!sourceFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Recording not found", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            
            val fileName = "${recording.exerciseName}_${
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date(recording.recordedAt))
            }.mp4"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RehabAI")
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } else {
                // Legacy approach for older Android versions
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val rehabDir = File(moviesDir, "RehabAI")
                rehabDir.mkdirs()
                
                val destFile = File(rehabDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Downloaded to Movies/RehabAI", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Helper object for saving recordings from exercise sessions
 */
object RecordingManager {
    
    suspend fun saveRecording(
        context: Context,
        exerciseId: String,
        videoFile: File,
        repsCompleted: Int,
        averageQuality: Int,
        durationSeconds: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val recordingsDir = File(context.filesDir, "recordings/$exerciseId")
                recordingsDir.mkdirs()
                
                // Remove old recordings if more than 2
                val existingFiles = recordingsDir.listFiles()
                    ?.filter { it.extension == "mp4" }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                
                if (existingFiles.size >= 2) {
                    // Delete oldest recordings beyond the limit
                    existingFiles.drop(1).forEach { old ->
                        old.delete()
                        File(old.parent, "${old.nameWithoutExtension}.meta").delete()
                    }
                }
                
                // Generate unique filename
                val timestamp = System.currentTimeMillis()
                val destFile = File(recordingsDir, "$timestamp.mp4")
                val metaFile = File(recordingsDir, "$timestamp.meta")
                
                // Copy video file
                videoFile.copyTo(destFile, overwrite = true)
                
                // Write metadata
                metaFile.writeText("""
                    reps=$repsCompleted
                    quality=$averageQuality
                    duration=$durationSeconds
                """.trimIndent())
                
                // Delete source file
                videoFile.delete()
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
