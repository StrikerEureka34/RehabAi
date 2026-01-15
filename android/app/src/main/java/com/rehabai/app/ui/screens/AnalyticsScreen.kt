package com.rehabai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabai.app.domain.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    report: SessionReport,
    onBack: () -> Unit
) {
    val analytics = report.analytics
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview Card
            item {
                OverviewCard(report = report, analytics = analytics)
            }
            
            // Quality Breakdown Card
            item {
                QualityBreakdownCard(analytics = analytics)
            }
            
            // Time Analysis Card
            item {
                TimeAnalysisCard(analytics = analytics)
            }
            
            // Issues Summary Card
            if (analytics.issueFrequency.isNotEmpty()) {
                item {
                    IssuesSummaryCard(analytics = analytics)
                }
            }
            
            // Rep-by-Rep Details
            item {
                Text(
                    text = "Rep-by-Rep Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            items(report.reps) { repDetail ->
                RepDetailCard(repDetail = repDetail, exerciseId = report.exercise.id)
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun OverviewCard(
    report: SessionReport,
    analytics: SessionAnalytics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = report.exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(report.startTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Overall Grade
                GradeBadge(
                    grade = analytics.overallGrade,
                    label = analytics.overallLabel,
                    score = analytics.averageTotal
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AnalyticsStat(
                    value = "${analytics.validReps}/${report.totalReps}",
                    label = "Valid Reps"
                )
                AnalyticsStat(
                    value = "${report.durationMinutes.roundToInt()} min",
                    label = "Duration"
                )
                AnalyticsStat(
                    value = "${analytics.averageTotal.roundToInt()}%",
                    label = "Avg Score"
                )
            }
        }
    }
}

@Composable
fun GradeBadge(
    grade: String,
    label: String,
    score: Float
) {
    val backgroundColor = when {
        grade.startsWith("A") -> Color(0xFF14532D)
        grade.startsWith("B") -> Color(0xFF1E3A5F)
        grade.startsWith("C") -> Color(0xFF78350F)
        grade.startsWith("D") -> Color(0xFF7F1D1D)
        else -> Color(0xFF7F1D1D)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = grade,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun AnalyticsStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
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
fun QualityBreakdownCard(analytics: SessionAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quality Components",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Component bars with weights
            QualityComponentBar(
                label = "Symmetry",
                score = analytics.averageSymmetry,
                weight = 25,
                description = "Balance between left and right"
            )
            
            QualityComponentBar(
                label = "Range of Motion",
                score = analytics.averageRom,
                weight = 30,
                description = "Movement depth and extension"
            )
            
            QualityComponentBar(
                label = "Control",
                score = analytics.averageControl,
                weight = 20,
                description = "Speed and consistency"
            )
            
            QualityComponentBar(
                label = "Form",
                score = analytics.averageForm,
                weight = 25,
                description = "Proper technique"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Distribution chart
            Text(
                text = "Quality Distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            QualityDistributionRow(
                excellent = analytics.excellentCount,
                good = analytics.goodCount,
                fair = analytics.fairCount,
                needsWork = analytics.needsWorkCount,
                poor = analytics.poorCount
            )
        }
    }
}

@Composable
fun QualityComponentBar(
    label: String,
    score: Float,
    weight: Int,
    description: String
) {
    val color = when {
        score >= 85 -> Color(0xFF4ADE80)
        score >= 70 -> Color(0xFF60A5FA)
        score >= 55 -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }
    
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${score.roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "${weight}% weight",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = (score / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun QualityDistributionRow(
    excellent: Int,
    good: Int,
    fair: Int,
    needsWork: Int,
    poor: Int
) {
    val total = (excellent + good + fair + needsWork + poor).coerceAtLeast(1)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        if (excellent > 0) {
            Box(
                modifier = Modifier
                    .weight(excellent.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFF4ADE80))
            )
        }
        if (good > 0) {
            Box(
                modifier = Modifier
                    .weight(good.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFF60A5FA))
            )
        }
        if (fair > 0) {
            Box(
                modifier = Modifier
                    .weight(fair.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFFFBBF24))
            )
        }
        if (needsWork > 0) {
            Box(
                modifier = Modifier
                    .weight(needsWork.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFFF97316))
            )
        }
        if (poor > 0) {
            Box(
                modifier = Modifier
                    .weight(poor.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFFEF4444))
            )
        }
    }
    
    Spacer(modifier = Modifier.height(4.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DistributionLegend("Excellent", excellent, Color(0xFF4ADE80))
        DistributionLegend("Good", good, Color(0xFF60A5FA))
        DistributionLegend("Fair", fair, Color(0xFFFBBF24))
        DistributionLegend("Needs Work", needsWork + poor, Color(0xFFEF4444))
    }
}

@Composable
fun DistributionLegend(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TimeAnalysisCard(analytics: SessionAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Timing Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimeMetric(
                    value = String.format("%.1fs", analytics.averageRepDuration),
                    label = "Avg Duration"
                )
                TimeMetric(
                    value = String.format("%.1fs", analytics.fastestRep),
                    label = "Fastest"
                )
                TimeMetric(
                    value = String.format("%.1fs", analytics.slowestRep),
                    label = "Slowest"
                )
                TimeMetric(
                    value = String.format("%.0fs", analytics.totalActiveTime),
                    label = "Active Time"
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Consistency indicator
            val variance = analytics.slowestRep - analytics.fastestRep
            val consistencyLabel = when {
                variance < 0.5f -> "Very Consistent"
                variance < 1.0f -> "Consistent"
                variance < 2.0f -> "Moderate Variance"
                else -> "High Variance"
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Tempo Consistency: ",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = consistencyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        variance < 1.0f -> Color(0xFF4ADE80)
                        variance < 2.0f -> Color(0xFFFBBF24)
                        else -> Color(0xFFEF4444)
                    }
                )
            }
        }
    }
}

@Composable
fun TimeMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
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
fun IssuesSummaryCard(analytics: SessionAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Common Issues",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            analytics.issueFrequency.entries
                .sortedByDescending { it.value }
                .take(5)
                .forEach { (issue, count) ->
                    IssueRow(issue = issue, count = count, total = analytics.validReps)
                }
        }
    }
}

@Composable
fun IssueRow(issue: String, count: Int, total: Int) {
    val percentage = if (total > 0) (count.toFloat() / total * 100).roundToInt() else 0
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFBBF24))
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = issue,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = "$count ($percentage%)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RepDetailCard(
    repDetail: RepDetail,
    exerciseId: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    val gradeColor = when {
        repDetail.qualityBreakdown.totalScore >= 85 -> Color(0xFF4ADE80)
        repDetail.qualityBreakdown.totalScore >= 70 -> Color(0xFF60A5FA)
        repDetail.qualityBreakdown.totalScore >= 55 -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Rep number badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(gradeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${repDetail.repNumber}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = gradeColor
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "Rep ${repDetail.repNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${String.format("%.1f", repDetail.durationSeconds)}s • ${repDetail.qualityBreakdown.getGrade()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Score
                    Text(
                        text = "${repDetail.qualityBreakdown.totalScore.roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Quality breakdown
                Text(
                    text = "Quality Breakdown",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                RepQualityRow("Symmetry", repDetail.qualityBreakdown.symmetryScore, repDetail.qualityBreakdown.symmetryDetails)
                RepQualityRow("Range of Motion", repDetail.qualityBreakdown.rangeOfMotionScore, repDetail.qualityBreakdown.romDetails)
                RepQualityRow("Control", repDetail.qualityBreakdown.controlScore, repDetail.qualityBreakdown.controlDetails)
                RepQualityRow("Form", repDetail.qualityBreakdown.formScore, repDetail.qualityBreakdown.formDetails)
                
                // Angle measurements
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Angle Measurements",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                AngleMeasurementsGrid(angles = repDetail.angleMetrics, exerciseId = exerciseId)
                
                // Timing
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimingChip("Eccentric", "${String.format("%.1f", repDetail.eccentricDuration)}s")
                    TimingChip("Concentric", "${String.format("%.1f", repDetail.concentricDuration)}s")
                    TimingChip("Total", "${String.format("%.1f", repDetail.durationSeconds)}s")
                }
                
                // Issues
                if (repDetail.remarks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Issues Noted",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    repDetail.remarks.forEach { remark ->
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
                                text = remark,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepQualityRow(label: String, score: Float, detail: String) {
    val color = when {
        score >= 85 -> Color(0xFF4ADE80)
        score >= 70 -> Color(0xFF60A5FA)
        score >= 55 -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Row {
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " • ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${score.roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun AngleMeasurementsGrid(angles: AngleMetrics, exerciseId: String) {
    val relevantAngles = when (exerciseId) {
        "squat" -> listOf(
            Triple("L Knee", angles.leftKneeAngle, angles.kneeSymmetryScore),
            Triple("R Knee", angles.rightKneeAngle, angles.kneeSymmetryScore),
            Triple("L Hip", angles.leftHipAngle, angles.hipSymmetryScore),
            Triple("R Hip", angles.rightHipAngle, angles.hipSymmetryScore)
        )
        "shoulder_press", "lateral_raise" -> listOf(
            Triple("L Elbow", angles.leftElbowAngle, angles.elbowSymmetryScore),
            Triple("R Elbow", angles.rightElbowAngle, angles.elbowSymmetryScore),
            Triple("L Shoulder", angles.leftShoulderAngle, angles.shoulderSymmetryScore),
            Triple("R Shoulder", angles.rightShoulderAngle, angles.shoulderSymmetryScore)
        )
        "lunge" -> listOf(
            Triple("L Knee", angles.leftKneeAngle, angles.kneeSymmetryScore),
            Triple("R Knee", angles.rightKneeAngle, angles.kneeSymmetryScore),
            Triple("L Hip", angles.leftHipAngle, angles.hipSymmetryScore),
            Triple("R Hip", angles.rightHipAngle, angles.hipSymmetryScore)
        )
        else -> listOf(
            Triple("L Knee", angles.leftKneeAngle, angles.kneeSymmetryScore),
            Triple("R Knee", angles.rightKneeAngle, angles.kneeSymmetryScore)
        )
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        relevantAngles.forEach { (label, angle, _) ->
            AngleChip(label = label, angle = angle)
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // ROM Summary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ROM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${angles.primaryRomDegrees.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${angles.actualMinAngle.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Max",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${angles.actualMaxAngle.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Expected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${angles.expectedMinAngle.roundToInt()}°-${angles.expectedMaxAngle.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AngleChip(label: String, angle: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${angle.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TimingChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
