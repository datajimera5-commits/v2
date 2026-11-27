package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.GenerationEntity
import com.example.data.local.GenerationType
import com.example.ui.theme.BackgroundObsidian
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

@Composable
fun ResultPreviewDialog(
    entity: GenerationEntity,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownload: (GenerationEntity) -> Unit,
    onUpscale: (GenerationEntity) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var isPlayingPreview by remember { mutableStateOf(true) }

    val transition = rememberInfiniteTransition(label = "preview_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("result_preview_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entity.outputTitle.ifBlank { entity.modelName },
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            color = TextOffWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${entity.orderUid} · ${entity.aspectRatio}" +
                                if (entity.durationSeconds > 0) " · ${entity.durationSeconds}s" else "",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = FireOrange
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_result_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close result modal",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Studio Playback / Output Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BackgroundObsidian),
                    contentAlignment = Alignment.Center
                ) {
                    if (entity.type == GenerationType.IMAGE && entity.remoteMediaUrl.isNotBlank()) {
                        AsyncImage(
                            model = entity.remoteMediaUrl,
                            contentDescription = "Generated studio image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_fire_mode_banner),
                            contentDescription = "Render studio preview frame",
                            contentScale = ContentScale.Crop,
                            alpha = 0.52f,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (entity.type == GenerationType.FIRE_VIDEO || entity.type == GenerationType.MOTION) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                            val activePhase = if (isPlayingPreview) phase else 0.5f
                            val path = Path()
                            val midY = size.height * 0.58f
                            val amp = size.height * 0.16f
                            for (x in 0..size.width.toInt() step 8) {
                                val t = x / size.width
                                val y = midY + sin((t * 8f + activePhase).toDouble()).toFloat() * amp
                                if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = path,
                                brush = Brush.horizontalGradient(listOf(FireOrange, CyanGlow, ElectricBlue)),
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawCircle(
                                color = FireOrange.copy(alpha = 0.75f),
                                radius = 4.dp.toPx(),
                                center = Offset(size.width * 0.5f, midY)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (entity.type == GenerationType.FIRE_VIDEO) {
                            Surface(
                                shape = CircleShape,
                                color = FireOrange.copy(alpha = 0.9f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (entity.remoteMediaUrl.isNotBlank()) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(entity.remoteMediaUrl))
                                                )
                                            }
                                        } else {
                                            isPlayingPreview = !isPlayingPreview
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingPreview && entity.remoteMediaUrl.isBlank()) {
                                            Icons.Rounded.Pause
                                        } else {
                                            Icons.Rounded.PlayArrow
                                        },
                                        contentDescription = "Play video",
                                        tint = TextOffWhite,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundObsidian.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(
                                text = when {
                                    entity.remoteMediaUrl.isNotBlank() -> "Video Stream Ready · Tap Play or Download"
                                    entity.type == GenerationType.IMAGE -> "Concept Frame Ready"
                                    else -> "Seedance 2.5 Output Ready"
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = TextOffWhite,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(status = entity.status)
                    if (entity.isUpscaled) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricBlue.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "4K Upscaled",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = ElectricBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entity.outputSummary.ifBlank { entity.prompt },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (entity.localFilePath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Saved: ${entity.localFilePath}",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            performSafeHaptic(haptics, hapticsEnabled)
                            onDownload(entity)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("modal_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download output",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            performSafeHaptic(haptics, hapticsEnabled)
                            val shareText = buildString {
                                append("VidoPro AI (${entity.modelName})\nOrder: ${entity.orderUid}\nPrompt: ${entity.prompt}")
                                if (entity.remoteMediaUrl.isNotBlank()) {
                                    append("\nVideo: ${entity.remoteMediaUrl}")
                                }
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "VidoPro AI Studio Render")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Generation"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("modal_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share output",
                            tint = TextOffWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = TextOffWhite)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        performSafeHaptic(haptics, hapticsEnabled)
                        onUpscale(entity)
                    },
                    enabled = !entity.isUpscaled,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("modal_upscale_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Upscale output to 4K",
                        tint = if (entity.isUpscaled) TextMuted else ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (entity.isUpscaled) "4K Upscale Applied" else "Upscale to 4K (-15 Credits)",
                        color = if (entity.isUpscaled) TextMuted else ElectricBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
