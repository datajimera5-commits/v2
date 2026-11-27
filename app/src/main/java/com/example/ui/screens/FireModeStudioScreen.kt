package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.FireOrderStatus
import com.example.data.local.GenerationEntity
import com.example.data.local.LicenseStatus
import com.example.data.local.UserSessionPrefs
import com.example.ui.components.FireIgniteButton
import com.example.ui.components.GlassCard
import com.example.ui.components.ResultPreviewDialog
import com.example.ui.components.ShimmerSkeleton
import com.example.ui.components.StatusBadge
import com.example.ui.components.performSafeHaptic
import com.example.ui.theme.BackgroundObsidian
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FireStudioUiState
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireModeStudioScreen(
    uiState: FireStudioUiState,
    session: UserSessionPrefs,
    onPromptChange: (String) -> Unit,
    onMagicEnhance: () -> Unit,
    onAddReferenceUris: (List<Uri>) -> Unit,
    onRemoveReferenceUri: (Uri) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onIgnite: () -> Unit,
    onPollOrder: () -> Unit,
    onDismissOrder: () -> Unit,
    onOpenResultModal: () -> Unit,
    onCloseResultModal: () -> Unit,
    onDownloadAsset: (GenerationEntity) -> Unit,
    onUpscaleAsset: (GenerationEntity) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraNotice by remember { mutableStateOf<String?>(null) }

    // Zero-permission Photo Picker for up to 10 reference images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddReferenceUris(uris)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            onAddReferenceUris(listOf(uri))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempCameraUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraNotice = "Camera permission denied. Use Gallery Photo Picker instead."
        }
    }

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
                .testTag("fire_mode_studio_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Studio Bar: ONLY Seedance 2.5 (Fire Mode) is available
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FireOrange.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.65f)),
                    modifier = Modifier.testTag("seedance_exclusive_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = "Seedance 2.5 Fire Mode",
                            tint = FireOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.fire_mode_badge),
                            style = MaterialTheme.typography.labelLarge,
                            color = FireOrange
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        performSafeHaptic(haptics, session.hapticsEnabled)
                        showSettingsSheet = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fire_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Video settings",
                        tint = TextOffWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${uiState.aspectRatio} · ${uiState.durationSeconds}s", color = TextOffWhite)
                }
            }

            // Prompt Card + Magic Enhance
            GlassCard(accentColor = FireOrange, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Director Prompt",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextOffWhite
                    )
                    Surface(
                        onClick = {
                            performSafeHaptic(haptics, session.hapticsEnabled)
                            onMagicEnhance()
                        },
                        enabled = !uiState.isEnhancing,
                        shape = RoundedCornerShape(10.dp),
                        color = ElectricBlue.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("magic_enhance_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = "Magic Enhance prompt",
                                tint = ElectricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (uiState.isEnhancing) "Enhancing…" else stringResource(R.string.magic_enhance),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.isEnhancing) {
                    ShimmerSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = uiState.prompt,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                "Describe camera movement, lighting, action, and subject continuity for Seedance 2.5…",
                                color = TextMuted
                            )
                        },
                        minLines = 4,
                        maxLines = 7,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FireOrange,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fire_prompt_input")
                    )
                }

                AnimatedVisibility(visible = !uiState.enhanceNotice.isNullOrBlank()) {
                    Text(
                        text = uiState.enhanceNotice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricBlue,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Reference Images Strip (Up to 10 images)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reference Images",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextOffWhite
                        )
                        Text(
                            text = "${uiState.referenceUris.size}/10 frames attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                performSafeHaptic(haptics, session.hapticsEnabled)
                                val remaining = (10 - uiState.referenceUris.size).coerceAtLeast(1)
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                                cameraNotice = null
                            },
                            enabled = uiState.referenceUris.size < 10,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pick_reference_images_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Pick reference images",
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", color = ElectricBlue)
                        }

                        OutlinedButton(
                            onClick = {
                                performSafeHaptic(haptics, session.hapticsEnabled)
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) {
                                    val uri = createTempCameraUri(context)
                                    pendingCameraUri = uri
                                    takePictureLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = uiState.referenceUris.size < 10,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("capture_reference_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Capture reference photo",
                                tint = TextOffWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!cameraNotice.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cameraNotice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonError
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.referenceUris.isEmpty()) {
                    Surface(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceElevated.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Tap Gallery or Camera to attach up to 10 Seedance 2.5 references",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(uiState.referenceUris, key = { it.toString() }) { uri ->
                            Box(modifier = Modifier.size(86.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Reference frame thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = BackgroundObsidian.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(32.dp)
                                        .clickable { onRemoveReferenceUri(uri) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove reference image",
                                            tint = TextOffWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CrimsonError.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonError,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // IGNITE Button
            val loadingText = when (uiState.activeOrder?.status) {
                FireOrderStatus.UPLOADING -> "Uploading Assets…"
                else -> "Seedance 2.5 is rendering…"
            }
            FireIgniteButton(
                text = stringResource(R.string.fire_mode_ignite),
                isLoading = uiState.isGenerating,
                loadingLabel = loadingText,
                onClick = {
                    performSafeHaptic(haptics, session.hapticsEnabled)
                    onIgnite()
                }
            )

            // Active Order Process Card (replicates vp_fire_mode.py live status card)
            uiState.activeOrder?.let { order ->
                ActiveFireOrderCard(
                    order = order,
                    elapsedSeconds = uiState.elapsedSeconds,
                    onPoll = onPollOrder,
                    onOpenResult = onOpenResultModal,
                    onDismiss = onDismissOrder
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceDark,
            contentColor = TextOffWhite
        ) {
            FireSettingsSheetContent(
                aspectRatio = uiState.aspectRatio,
                durationSeconds = uiState.durationSeconds,
                isTrial = session.licenseStatus == LicenseStatus.TRIAL,
                onAspectRatioSelect = onAspectRatioChange,
                onDurationSelect = onDurationChange,
                onClose = { showSettingsSheet = false }
            )
        }
    }

    if (uiState.showResultModal && uiState.activeOrder != null) {
        ResultPreviewDialog(
            entity = uiState.activeOrder,
            hapticsEnabled = session.hapticsEnabled,
            onDismiss = onCloseResultModal,
            onDownload = onDownloadAsset,
            onUpscale = onUpscaleAsset
        )
    }
}

@Composable
private fun ActiveFireOrderCard(
    order: GenerationEntity,
    elapsedSeconds: Int,
    onPoll: () -> Unit,
    onOpenResult: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassCard(
        accentColor = if (order.status == FireOrderStatus.READY || order.status == FireOrderStatus.DOWNLOADED) EmeraldSuccess else FireOrange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_fire_order_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.status.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextOffWhite
                )
                Text(
                    text = "Order ${order.orderUid} · Elapsed ${formatElapsed(elapsedSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            StatusBadge(status = order.status)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (order.status == FireOrderStatus.UPLOADING || order.status == FireOrderStatus.RENDERING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = FireOrange,
                trackColor = SurfaceElevated
            )
        }

        if (order.adminMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = order.adminMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (order.status == FireOrderStatus.READY || order.status == FireOrderStatus.DOWNLOADED) {
                Button(
                    onClick = onOpenResult,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_fire_result_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = "View rendered video modal",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Result", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onPoll,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh order status",
                        tint = ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Status", color = ElectricBlue)
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Dismiss", color = TextMuted)
            }
        }
    }
}

@Composable
private fun FireSettingsSheetContent(
    aspectRatio: String,
    durationSeconds: Int,
    isTrial: Boolean,
    onAspectRatioSelect: (String) -> Unit,
    onDurationSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val durationSteps = if (isTrial) listOf(5, 10, 15) else listOf(5, 10, 15, 30)
    val clampedDur = if (isTrial && durationSeconds > 15) 15 else durationSeconds
    val currentStepIndex = durationSteps.indexOf(clampedDur).let { if (it >= 0) it else 1 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Fire Mode Settings · Seedance 2.5",
            style = MaterialTheme.typography.titleLarge,
            color = TextOffWhite
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("16:9", "9:16", "1:1").forEach { ratio ->
                    FilterChip(
                        selected = aspectRatio == ratio,
                        onClick = { onAspectRatioSelect(ratio) },
                        label = { Text(ratio) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FireOrange.copy(alpha = 0.22f),
                            selectedLabelColor = FireOrange
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("aspect_ratio_${ratio.replace(":", "_")}")
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isTrial) "Duration (Trial Max 15s)" else "Duration",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Text(
                    text = "${clampedDur}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = FireOrange
                )
            }
            Slider(
                value = currentStepIndex.toFloat(),
                onValueChange = { raw ->
                    val idx = raw.toInt().coerceIn(0, durationSteps.lastIndex)
                    onDurationSelect(durationSteps[idx])
                },
                valueRange = 0f..durationSteps.lastIndex.toFloat(),
                steps = (durationSteps.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = FireOrange,
                    activeTrackColor = FireOrange
                ),
                modifier = Modifier.testTag("fire_duration_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("5s (15 Cr)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text("10s (30 Cr)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text("15s (40 Cr)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                if (!isTrial) {
                    Text("30s (60 Cr)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Engine: Seedance 2.5 (Fire Mode) · Cloud Render Pipeline Active",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(12.dp)
            )
        }

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = FireOrange),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
        ) {
            Text("Apply Studio Settings", fontWeight = FontWeight.Bold)
        }
    }
}

private fun createTempCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File(dir, "ref_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun formatElapsed(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
