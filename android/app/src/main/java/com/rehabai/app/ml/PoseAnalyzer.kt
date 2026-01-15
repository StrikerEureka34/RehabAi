package com.rehabai.app.ml

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PoseAnalyzer(
    private val onPoseDetected: (Pose, Int, Int) -> Unit,
    private val onNoPoseDetected: () -> Unit
) : ImageAnalysis.Analyzer {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    private val lastProcessTime = AtomicLong(0)
    
    // Throttle to ~15 FPS to prevent thermal issues
    private val minFrameIntervalMs = 66L
    
    private val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
        .build()
    
    private val detector: PoseDetector = PoseDetection.getClient(options)
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        
        // Throttle frame rate
        if (now - lastProcessTime.get() < minFrameIntervalMs) {
            imageProxy.close()
            return
        }
        
        // Skip if still processing previous frame
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }
        
        lastProcessTime.set(now)
        
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        val imageWidth = inputImage.width
        val imageHeight = inputImage.height
        
        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                if (pose.allPoseLandmarks.isNotEmpty()) {
                    onPoseDetected(pose, imageWidth, imageHeight)
                } else {
                    onNoPoseDetected()
                }
            }
            .addOnFailureListener {
                onNoPoseDetected()
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }
    
    fun close() {
        detector.close()
    }
}
