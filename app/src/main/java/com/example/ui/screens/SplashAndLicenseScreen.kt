package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LicenseStatus
import com.example.data.local.UserSessionPrefs
import com.example.ui.components.GlassCard
import com.example.ui.components.ShimmerSkeleton
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

@Composable
fun SplashAndLicenseScreen(
    splashFinished: Boolean,
    session: UserSessionPrefs,
    plans: List<com.example.data.network.RemotePlan>,
    isLoading: Boolean,
    bannerMessage: String?,
    onDismissBanner: () -> Unit,
    onActivateLicense: (String, String) -> Unit,
    onStartTrial: (String, String) -> Unit,
    onRetryUnblocked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundObsidian)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_splash_hero),
            contentDescription = "VidoPro AI atmospheric backdrop",
            contentScale = ContentScale.Crop,
            alpha = 0.42f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BackgroundObsidian.copy(alpha = 0.65f),
                            BackgroundObsidian.copy(alpha = 0.90f),
                            BackgroundObsidian
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = Pair(splashFinished, session.licenseStatus),
                label = "splash_license_gate"
            ) { (ready, status) ->
                if (!ready) {
                    SplashBrandingView()
                } else {
                    when (status) {
                        LicenseStatus.BLOCKED -> AccessDeniedView(
                            subscriptionId = session.effectiveSubscriptionId,
                            deviceUuid = session.deviceUuid,
                            whatsappOwner = session.whatsappOwner,
                            adminMessage = session.blockedMessage,
                            bannerMessage = bannerMessage,
                            onDismissBanner = onDismissBanner,
                            onResetToActivate = onRetryUnblocked
                        )
                        LicenseStatus.UNLICENSED -> ActivateLicenseView(
                            subscriptionId = session.effectiveSubscriptionId,
                            deviceUuid = session.deviceUuid,
                            initialName = session.userName,
                            initialPhone = session.userPhone,
                            whatsappOwner = session.whatsappOwner,
                            plans = plans,
                            isLoading = isLoading,
                            bannerMessage = bannerMessage,
                            onDismissBanner = onDismissBanner,
                            onActivate = onActivateLicense,
                            onStartTrial = onStartTrial
                        )
                        LicenseStatus.ACTIVE, LicenseStatus.TRIAL -> SplashBrandingView()
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashBrandingView() {
    val pulseTransition = rememberInfiniteTransition(label = "logo_pulse")
    val scale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .testTag("splash_branding_view")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(118.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .scale(scale)
                    .alpha(glowAlpha)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(FireOrange.copy(alpha = 0.45f), ElectricBlue.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceDark,
                border = BorderStroke(1.5.dp, FireOrange.copy(alpha = 0.65f)),
                modifier = Modifier.size(86.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = "VidoPro AI Logo",
                        tint = FireOrange,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "VidoPro AI",
            style = MaterialTheme.typography.displaySmall,
            color = TextOffWhite
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.splash_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(28.dp))
        ShimmerSkeleton(
            modifier = Modifier
                .width(210.dp)
                .height(6.dp),
            cornerRadius = 99.dp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.splash_verifying),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun ActivateLicenseView(
    subscriptionId: String,
    deviceUuid: String,
    initialName: String,
    initialPhone: String,
    whatsappOwner: String,
    plans: List<com.example.data.network.RemotePlan>,
    isLoading: Boolean,
    bannerMessage: String?,
    onDismissBanner: () -> Unit,
    onActivate: (String, String) -> Unit,
    onStartTrial: (String, String) -> Unit
) {
    var userIdInput by remember { mutableStateOf("") }
    var licenseInput by remember { mutableStateOf("") }
    var trialNameInput by remember(initialName) { mutableStateOf(initialName) }
    var trialPhoneInput by remember(initialPhone) { mutableStateOf(initialPhone) }

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .testTag("activate_license_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = ElectricBlue.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f)),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = "License Lock",
                    tint = ElectricBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.license_activate_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextOffWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.license_activate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        if (!bannerMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            GateStatusBanner(
                message = bannerMessage,
                onDismiss = onDismissBanner
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(accentColor = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SUBSCRIPTION ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = FireOrange
                        )
                        Text(
                            text = subscriptionId.ifBlank { "Generating ID…" },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextOffWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { copyTextToClipboard(context, "VidoPro Subscription ID", subscriptionId) },
                        modifier = Modifier.testTag("copy_subscription_id_activate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Subscription ID",
                            tint = FireOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = "Hardware Device ID",
                    tint = ElectricBlue,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOCKED HARDWARE DEVICE UUID",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = deviceUuid.ifBlank { "Generating UUID…" },
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = { copyTextToClipboard(context, "VidoPro Device UUID", deviceUuid) },
                    modifier = Modifier.testTag("copy_device_id_activate_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy Device UUID",
                        tint = ElectricBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = userIdInput,
                onValueChange = { userIdInput = it },
                label = { Text("Subscription / User ID (If Issued)") },
                placeholder = { Text("e.g. DLH-1042 or $subscriptionId") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextOffWhite,
                    unfocusedTextColor = TextOffWhite,
                    focusedLabelColor = ElectricBlue,
                    unfocusedLabelColor = TextMuted
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_id_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = licenseInput,
                onValueChange = { licenseInput = it },
                label = { Text(stringResource(R.string.license_key_label)) },
                placeholder = { Text("e.g. VP-PRO-2026-FIRE") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextOffWhite,
                    unfocusedTextColor = TextOffWhite,
                    focusedLabelColor = ElectricBlue,
                    unfocusedLabelColor = TextMuted
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("license_key_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onActivate(licenseInput, userIdInput) },
                enabled = !isLoading && licenseInput.trim().length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("activate_license_button")
            ) {
                Text(
                    text = if (isLoading) "Verifying License…" else stringResource(R.string.license_activate_button),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = trialNameInput,
                    onValueChange = { trialNameInput = it },
                    label = { Text("Your Name") },
                    placeholder = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FireOrange,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextOffWhite,
                        unfocusedTextColor = TextOffWhite,
                        focusedLabelColor = FireOrange,
                        unfocusedLabelColor = TextMuted
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trial_name_input")
                )
                OutlinedTextField(
                    value = trialPhoneInput,
                    onValueChange = { trialPhoneInput = it },
                    label = { Text("10-Digit Mobile *") },
                    placeholder = { Text("98XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FireOrange,
                        unfocusedBorderColor = SurfaceCardBorder,
                        focusedTextColor = TextOffWhite,
                        unfocusedTextColor = TextOffWhite,
                        focusedLabelColor = FireOrange,
                        unfocusedLabelColor = TextMuted
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trial_phone_input")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { onStartTrial(trialNameInput, trialPhoneInput) },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("start_trial_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RocketLaunch,
                    contentDescription = "Start Pro Trial",
                    tint = FireOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.license_trial_button),
                    color = FireOrange,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (plans.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            GlassCard(accentColor = FireOrange, modifier = Modifier.fillMaxWidth()) {
                val context = LocalContext.current
                Text(
                    text = "AVAILABLE STUDIO PLANS",
                    style = MaterialTheme.typography.labelSmall,
                    color = FireOrange
                )
                Spacer(modifier = Modifier.height(10.dp))
                plans.take(4).forEach { plan ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plan.name + if (plan.badge.isNotBlank()) " · ${plan.badge}" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextOffWhite
                                )
                                val allowance = when {
                                    plan.genLimit < 0 -> "Unlimited gens"
                                    plan.genLimit > 0 -> "${plan.genLimit} gens"
                                    plan.coinsPerCycle > 0 -> "${plan.coinsPerCycle} coins"
                                    else -> "Pro studio access"
                                }
                                Text(
                                    text = "${plan.durationDays} days · $allowance · ${plan.maxDevices} device(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = if (plan.price <= 0.0) "Free" else "₹${plan.price.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (whatsappOwner.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val cleanPhone = whatsappOwner.filter { it.isDigit() }
                            val msg = Uri.encode("Hi! I want a VidoPro AI subscription key. My Device UUID: $deviceUuid")
                            val url = if (cleanPhone.isNotBlank()) {
                                "https://wa.me/$cleanPhone?text=$msg"
                            } else {
                                "https://wa.me/?text=$msg"
                            }
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = "Get License on WhatsApp",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Get License Key on WhatsApp", color = EmeraldSuccess)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessDeniedView(
    subscriptionId: String,
    deviceUuid: String,
    whatsappOwner: String,
    adminMessage: String,
    bannerMessage: String?,
    onDismissBanner: () -> Unit,
    onResetToActivate: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .testTag("access_denied_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = CrimsonError.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.5f)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = "Access Denied",
                    tint = CrimsonError,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.access_denied_title),
            style = MaterialTheme.typography.headlineMedium,
            color = CrimsonError
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sub ID: $subscriptionId · Device: $deviceUuid",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            IconButton(
                onClick = { copyTextToClipboard(context, "VidoPro Subscription ID", subscriptionId) },
                modifier = Modifier.testTag("copy_device_id_blocked_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy Blocked Subscription ID",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!bannerMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            GateStatusBanner(
                message = bannerMessage,
                onDismiss = onDismissBanner
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        GlassCard(accentColor = CrimsonError, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ADMINISTRATOR NOTICE",
                style = MaterialTheme.typography.labelSmall,
                color = CrimsonError
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = adminMessage.ifBlank {
                    "Your license has been suspended or blocked on this device."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextOffWhite
            )
            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    val cleanPhone = whatsappOwner.filter { it.isDigit() }
                    val encoded = Uri.encode("Hello VidoPro Support, my Subscription ID $subscriptionId (Device $deviceUuid) is blocked. Please assist.")
                    val url = if (cleanPhone.isNotBlank()) {
                        "https://wa.me/$cleanPhone?text=$encoded"
                    } else {
                        "https://wa.me/?text=$encoded"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    runCatching { context.startActivity(intent) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("contact_support_whatsapp_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                    contentDescription = "WhatsApp Support",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.contact_support_whatsapp), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onResetToActivate,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("retry_license_gate_button")
            ) {
                Text("Enter Different License Key", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun GateStatusBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CrimsonError.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("license_gate_error_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextOffWhite,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss message",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied: $value", Toast.LENGTH_SHORT).show()
}
