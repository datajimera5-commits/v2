package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.GenerationEntity
import com.example.data.local.UserSessionPrefs
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerSkeleton
import com.example.ui.components.performSafeHaptic
import com.example.ui.theme.BackgroundObsidian
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ImageStudioUiState
import com.example.ui.viewmodel.MotionStudioUiState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ImageStudioScreen(
    uiState: ImageStudioUiState,
    session: UserSessionPrefs,
    onPromptChange: (String) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onOpenEntityModal: (GenerationEntity) -> Unit,
    onBack: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val styles = listOf("Cinematic", "Photorealistic", "Cyber Neon", "Product Studio")
    val ratios = listOf("1:1", "16:9", "9:16", "4:3")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("image_studio_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("image_studio_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to dashboard",
                        tint = TextOffWhite
                    )
                }
                SectionHeader(
                    title = "Image Generation",
                    subtitle = "Standard AI Image Studio · 10 Credits per render"
                )
            }

            GlassCard(accentColor = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.prompt,
                    onValueChange = onPromptChange,
                    label = { Text("Image Prompt") },
                    placeholder = { Text("Describe subject, lens, atmosphere, and composition…", color = TextMuted) },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextOffWhite,
                        unfocusedTextColor = TextOffWhite
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Visual Style", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        FilterChip(
                            selected = uiState.stylePreset == style,
                            onClick = { onStyleChange(style) },
                            label = { Text(style) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                selectedLabelColor = ElectricBlue
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Aspect Ratio", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ratios.forEach { ratio ->
                        FilterChip(
                            selected = uiState.aspectRatio == ratio,
                            onClick = { onAspectRatioChange(ratio) },
                            label = { Text(ratio) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                selectedLabelColor = ElectricBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = CrimsonError
                )
            }

            Button(
                onClick = {
                    performSafeHaptic(haptics, session.hapticsEnabled)
                    onGenerate()
                },
                enabled = !uiState.isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("generate_image_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = "Generate image",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isGenerating) "Synthesizing Frame…" else "Generate Image (10 Credits)",
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.isGenerating) {
                ShimmerSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            } else if (uiState.result != null) {
                GlassCard(
                    accentColor = ElectricBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_result_card")
                ) {
                    Text(
                        text = uiState.result.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextOffWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.result.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    uiState.latestEntity?.let { entity ->
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onOpenEntityModal(entity) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Export / Upscale Preview", color = ElectricBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MotionControlScreen(
    uiState: MotionStudioUiState,
    session: UserSessionPrefs,
    onSubjectChange: (String) -> Unit,
    onCameraMoveChange: (String) -> Unit,
    onIntensityChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onGenerateMotionPlan: () -> Unit,
    onSendToFireMode: (String, Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val cameraMoves = listOf("Orbit 360", "Dolly Zoom", "Crane Up", "Pan Sweep", "FPV Tracking")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("motion_control_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "Motion Control",
                subtitle = "Camera vector choreography & Seedance 2.5 motion lock"
            )

            // Live Camera Trajectory Canvas
            GlassCard(accentColor = CyanGlow, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trajectory Curve · ${uiState.cameraMove}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextOffWhite
                    )
                    Text(
                        text = "${uiState.intensity}% · ${uiState.durationSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanGlow
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BackgroundObsidian)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        val w = size.width
                        val h = size.height
                        val amp = (uiState.intensity / 100f) * (h * 0.35f)
                        val gridColor = SurfaceCardBorder.copy(alpha = 0.45f)
                        for (i in 1..3) {
                            val y = h * (i / 4f)
                            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                        }
                        val path = Path()
                        val steps = 48
                        for (i in 0..steps) {
                            val t = i / steps.toFloat()
                            val x = w * t
                            val y = when (uiState.cameraMove) {
                                "Orbit 360" -> h * 0.5f + sin(t * Math.PI * 2.0).toFloat() * amp
                                "Dolly Zoom" -> h * 0.78f - (t * t) * amp * 1.4f
                                "Crane Up" -> h * 0.8f - sin(t * Math.PI * 0.5).toFloat() * amp * 1.6f
                                "FPV Tracking" -> h * 0.5f + (sin(t * Math.PI * 3.0) * cos(t * Math.PI)).toFloat() * amp
                                else -> h * 0.5f - sin(t * Math.PI).toFloat() * amp
                            }.coerceIn(8f, h - 8f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(listOf(ElectricBlue, CyanGlow, FireOrange)),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Camera Vector", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cameraMoves.forEach { move ->
                        FilterChip(
                            selected = uiState.cameraMove == move,
                            onClick = { onCameraMoveChange(move) },
                            label = { Text(move) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanGlow.copy(alpha = 0.22f),
                                selectedLabelColor = CyanGlow
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Motion Intensity", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Text("${uiState.intensity}%", style = MaterialTheme.typography.labelLarge, color = CyanGlow)
                }
                Slider(
                    value = uiState.intensity.toFloat(),
                    onValueChange = { onIntensityChange(it.toInt()) },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(thumbColor = CyanGlow, activeTrackColor = CyanGlow)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 30).forEach { sec ->
                        FilterChip(
                            selected = uiState.durationSeconds == sec,
                            onClick = { onDurationChange(sec) },
                            label = { Text("${sec}s") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.subjectPrompt,
                    onValueChange = onSubjectChange,
                    label = { Text("Scene Subject") },
                    placeholder = { Text("e.g. Neon-lit cyberpunk coupe drifting through rain…", color = TextMuted) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextOffWhite,
                        unfocusedTextColor = TextOffWhite
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("motion_subject_input")
                )
            }

            Button(
                onClick = {
                    performSafeHaptic(haptics, session.hapticsEnabled)
                    onGenerateMotionPlan()
                },
                enabled = !uiState.isPlanning,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("generate_motion_plan_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ControlCamera,
                    contentDescription = "Generate motion choreography",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isPlanning) "Computing Trajectory…" else "Build Motion Choreography",
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.isPlanning) {
                ShimmerSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                )
            } else if (uiState.result != null) {
                GlassCard(accentColor = FireOrange, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.result.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextOffWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.result.choreographySummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            performSafeHaptic(haptics, session.hapticsEnabled)
                            onSendToFireMode(uiState.result.suggestedFirePrompt, uiState.durationSeconds)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FireOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("send_motion_to_fire_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = "Send to Fire Mode",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send to Seedance 2.5 Fire Mode", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
