package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.GenerationEntity
import com.example.data.local.LicenseStatus
import com.example.data.local.UserSessionPrefs
import com.example.ui.components.CreditPill
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.performSafeHaptic
import com.example.ui.theme.BackgroundObsidian
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    session: UserSessionPrefs,
    recentGenerations: List<GenerationEntity>,
    onOpenFireStudio: () -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenMotionStudio: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectGeneration: (GenerationEntity) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("dashboard_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: User Avatar, Credit Balance, Settings
            item {
                DashboardHeader(
                    session = session,
                    haptics = haptics,
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings
                )
            }

            // Hero Feature Card: Fire Mode (Seedance 2.5)
            item {
                FireModeHeroCard(
                    onClick = {
                        performSafeHaptic(haptics, session.hapticsEnabled)
                        onOpenFireStudio()
                    }
                )
            }

            // The 3 Pillars Section
            item {
                SectionHeader(
                    title = "Creation Pillars",
                    subtitle = "Select a pro studio workspace"
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillarNavigationCard(
                        title = "Image Generation",
                        subtitle = "Concept art, lighting studies & high-res reference frames",
                        badge = "STUDIO",
                        icon = Icons.Rounded.Image,
                        accent = ElectricBlue,
                        testTag = "pillar_image_card",
                        onClick = {
                            performSafeHaptic(haptics, session.hapticsEnabled)
                            onOpenImageStudio()
                        }
                    )
                    PillarNavigationCard(
                        title = "Video Generation (Fire Mode)",
                        subtitle = "Powered exclusively by Seedance 2.5 · up to 10 image refs",
                        badge = "HERO",
                        icon = Icons.Rounded.LocalFireDepartment,
                        accent = FireOrange,
                        testTag = "pillar_fire_video_card",
                        onClick = {
                            performSafeHaptic(haptics, session.hapticsEnabled)
                            onOpenFireStudio()
                        }
                    )
                    PillarNavigationCard(
                        title = "Motion Control",
                        subtitle = "3D camera choreography, dolly, orbit & keyframe velocity",
                        badge = "CAMERA",
                        icon = Icons.Rounded.ControlCamera,
                        accent = CyanGlow,
                        testTag = "pillar_motion_card",
                        onClick = {
                            performSafeHaptic(haptics, session.hapticsEnabled)
                            onOpenMotionStudio()
                        }
                    )
                }
            }

            // Recent Studio Activity
            item {
                SectionHeader(
                    title = "Recent Studio Activity",
                    subtitle = if (recentGenerations.isEmpty()) "Your Seedance 2.5 & studio renders appear here" else "${recentGenerations.size} saved generation(s)"
                )
            }

            if (recentGenerations.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FireOrange.copy(alpha = 0.14f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.MovieCreation,
                                        contentDescription = "Empty studio activity",
                                        tint = FireOrange
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready for your first Fire Mode cut",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextOffWhite
                                )
                                Text(
                                    text = "Tap Video Generation (Fire Mode) to attach reference frames and ignite Seedance 2.5.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            } else {
                items(recentGenerations.take(5), key = { it.id }) { item ->
                    GlassCard(
                        onClick = { onSelectGeneration(item) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.outputTitle.ifBlank { item.modelName },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextOffWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            StatusBadge(status = item.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    session: UserSessionPrefs,
    haptics: HapticFeedback,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = {
                    performSafeHaptic(haptics, session.hapticsEnabled)
                    onOpenProfile()
                },
                shape = CircleShape,
                color = SurfaceElevated,
                border = BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.6f)),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("header_profile_avatar")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_user_avatar),
                    contentDescription = "Creator profile avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column {
                Text(
                    text = "VidoPro AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextOffWhite
                )
                Text(
                    text = when {
                        session.licenseStatus == LicenseStatus.ACTIVE -> session.planId.ifBlank { "Pro Plan Active" }
                        session.licenseStatus == LicenseStatus.TRIAL -> "Free Trial"
                        else -> "Unlicensed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlue
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CreditPill(
                credits = session.credits,
                onClick = {
                    performSafeHaptic(haptics, session.hapticsEnabled)
                    onOpenProfile()
                }
            )
            Surface(
                shape = CircleShape,
                color = SurfaceElevated,
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = {
                        performSafeHaptic(haptics, session.hapticsEnabled)
                        onOpenSettings()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("header_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Studio settings",
                        tint = TextOffWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun FireModeHeroCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark,
        border = BorderStroke(1.2.dp, FireOrange.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .testTag("hero_fire_mode_card")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_fire_mode_banner),
                contentDescription = "Seedance 2.5 Fire Mode studio banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                BackgroundObsidian.copy(alpha = 0.94f),
                                BackgroundObsidian.copy(alpha = 0.72f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FireOrange.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = FireOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "SEEDANCE 2.5 · FIRE MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = FireOrange
                        )
                    }
                }

                Column {
                    Text(
                        text = "Cinema Video Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextOffWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Multi-reference video synthesis up to 30s with Magic Enhance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth(0.84f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Open Fire Studio",
                        style = MaterialTheme.typography.labelLarge,
                        color = FireOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Launch Fire Mode",
                        tint = FireOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PillarNavigationCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(
        accentColor = accent,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextOffWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open $title",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
