package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.GenerationEntity
import com.example.data.local.GenerationType
import com.example.data.local.LicenseStatus
import com.example.data.local.UserSessionPrefs
import com.example.data.security.SecurityStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.performSafeHaptic
import com.example.ui.theme.BackgroundObsidian
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.TextSecondary

import com.example.data.network.RemotePlan
import com.example.data.network.RemoteTicket
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun ProfileAndSubscriptionScreen(
    session: UserSessionPrefs,
    securityStatus: SecurityStatus,
    plans: List<RemotePlan>,
    tickets: List<RemoteTicket>,
    isSyncing: Boolean,
    generations: List<GenerationEntity>,
    onSyncAccount: () -> Unit,
    onRequestDeviceReset: () -> Unit,
    onClaimReferral: (String) -> Unit,
    onSubmitTicket: (String, String) -> Unit,
    onSelectGeneration: (GenerationEntity) -> Unit,
    onDeleteGeneration: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var referralInput by remember { mutableStateOf("") }
    var ticketSubject by remember { mutableStateOf("") }
    var ticketMessage by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<GenerationType?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    val filteredHistory = remember(generations, selectedFilter) {
        if (selectedFilter == null) generations else generations.filter { it.type == selectedFilter }
    }

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
                .testTag("profile_subscription_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plan Status Card
            item {
                val isPro = session.licenseStatus == LicenseStatus.ACTIVE
                GlassCard(
                    accentColor = if (isPro) EmeraldSuccess else FireOrange,
                    modifier = Modifier.fillMaxWidth()
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
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.65f)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_user_avatar),
                                    contentDescription = "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = when {
                                            session.userName.isNotBlank() -> session.userName
                                            isPro -> "Pro Plan Active"
                                            else -> "Free Trial"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextOffWhite
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = "Verified plan",
                                        tint = if (isPro) EmeraldSuccess else FireOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                val planLabel = session.planId.ifBlank { if (isPro) "PRO" else "TRIAL" }
                                val subIdLabel = session.effectiveSubscriptionId
                                Text(
                                    text = "⚡ ${session.credits} Coins · $planLabel · ID $subIdLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                if (session.licenseKey.isNotBlank()) {
                                    Text(
                                        text = "Key: ${session.licenseKey}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricBlue
                                    )
                                }
                                if (session.expiryDate.isNotBlank()) {
                                    Text(
                                        text = "Valid until: ${session.expiryDate.take(16).replace("T", " ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                performSafeHaptic(haptics, session.hapticsEnabled)
                                onSyncAccount()
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = FireOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sync_account_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = TextOffWhite,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing…", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = "Sync account",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Coins & Plan", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Settings", color = TextOffWhite)
                        }
                    }
                }
            }

            // Device Management Card
            item {
                GlassCard(accentColor = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = "Locked Device UUID",
                            tint = ElectricBlue
                        )
                        Text(
                            text = "Device Management",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextOffWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SUBSCRIPTION ID (ADMIN PANEL)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FireOrange
                                )
                                Text(
                                    text = session.effectiveSubscriptionId,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextOffWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Subscription ID", session.effectiveSubscriptionId))
                                    copiedNotice = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("copy_subscription_id_profile_button")
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HARDWARE DEVICE_UUID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = session.deviceUuid,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextOffWhite
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Device UUID", session.deviceUuid))
                                    copiedNotice = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy Device UUID",
                                    tint = ElectricBlue
                                )
                            }
                        }
                    }
                    if (copiedNotice) {
                        Text(
                            text = "ID copied to clipboard.",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Security Shield",
                            tint = if (securityStatus.isCleanDevice) EmeraldSuccess else FireOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = securityStatus.summaryLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            performSafeHaptic(haptics, session.hapticsEnabled)
                            onRequestDeviceReset()
                        },
                        enabled = !session.deviceResetRequested,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("reset_device_button")
                    ) {
                        Text(
                            text = if (session.deviceResetRequested) {
                                "Reset Requested (Pending Admin Approval)"
                            } else {
                                "Reset Device (Admin Approval Required)"
                            },
                            color = if (session.deviceResetRequested) TextMuted else TextOffWhite
                        )
                    }
                }
            }

            // Referral System Card
            item {
                GlassCard(accentColor = CyanGlow, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CardGiftcard,
                            contentDescription = "Referrals",
                            tint = CyanGlow
                        )
                        Text(
                            text = "Invite Friends & Earn Credits",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextOffWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your Code: ${session.referralCode} · ${session.referralCount} claimed (+100 Credits each)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Join VidoPro AI Seedance 2.5 Studio! Use referral code ${session.referralCode} for +100 Credits."
                                )
                            }
                            context.startActivity(Intent.createChooser(share, "Share Referral Code"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("share_referral_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share referral code",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Invite Code", color = CyanGlow)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = referralInput,
                            onValueChange = { referralInput = it },
                            placeholder = { Text("Enter friend's code", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedTextColor = TextOffWhite,
                                unfocusedTextColor = TextOffWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("referral_code_input")
                        )
                        Button(
                            onClick = {
                                onClaimReferral(referralInput)
                                referralInput = ""
                            },
                            enabled = referralInput.trim().length >= 5,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("claim_referral_button")
                        ) {
                            Text("Claim", color = BackgroundObsidian, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Live Plans from Control Center (/sub/plans)
            if (plans.isNotEmpty()) {
                item {
                    GlassCard(accentColor = FireOrange, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Subscription Plans",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextOffWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        plans.forEach { plan ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (plan.planId == session.planId) EmeraldSuccess else SurfaceCardBorder
                                ),
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
                                            text = "${plan.durationDays}d · $allowance · ${plan.maxDevices} device(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = if (plan.price <= 0.0) "Free" else "₹${plan.price.toInt()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Support Tickets Card (synced with Admin Panel Tickets tab)
            item {
                GlassCard(accentColor = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SupportAgent,
                            contentDescription = "Support Tickets",
                            tint = ElectricBlue
                        )
                        Text(
                            text = "Support Tickets",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextOffWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ticketSubject,
                        onValueChange = { ticketSubject = it },
                        placeholder = { Text("Subject (e.g. Coins / Plan / Render)", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ticket_subject_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ticketMessage,
                        onValueChange = { ticketMessage = it },
                        placeholder = { Text("Describe your issue for support…", color = TextMuted) },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ticket_message_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onSubmitTicket(ticketSubject, ticketMessage)
                            ticketSubject = ""
                            ticketMessage = ""
                        },
                        enabled = ticketSubject.isNotBlank() && ticketMessage.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("submit_ticket_button")
                    ) {
                        Text("Submit Support Ticket", fontWeight = FontWeight.Bold)
                    }

                    if (tickets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        tickets.take(4).forEach { t ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(1.dp, SurfaceCardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = t.subject,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextOffWhite
                                        )
                                        Text(
                                            text = t.status.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (t.status == "replied") EmeraldSuccess else FireOrange
                                        )
                                    }
                                    if (t.reply.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Reply: ${t.reply}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // History Section + Filter Chips
            item {
                SectionHeader(
                    title = "Generation History",
                    subtitle = "Cloud & local studio archive (${filteredHistory.size})"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == GenerationType.FIRE_VIDEO,
                        onClick = { selectedFilter = GenerationType.FIRE_VIDEO },
                        label = { Text("Fire Video") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FireOrange.copy(alpha = 0.22f),
                            selectedLabelColor = FireOrange
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == GenerationType.IMAGE,
                        onClick = { selectedFilter = GenerationType.IMAGE },
                        label = { Text("Image") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == GenerationType.MOTION,
                        onClick = { selectedFilter = GenerationType.MOTION },
                        label = { Text("Motion") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            if (filteredHistory.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No generations in this category yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                // 2-column grid rendered cleanly inside LazyColumn
                val chunked = filteredHistory.chunked(2)
                items(chunked.size) { rowIdx ->
                    val pair = chunked[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { item ->
                            HistoryGridTile(
                                entity = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectGeneration(item) },
                                onDelete = { onDeleteGeneration(item.id) }
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HistoryGridTile(
    entity: GenerationEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        accentColor = when (entity.type) {
            GenerationType.FIRE_VIDEO -> FireOrange
            GenerationType.IMAGE -> ElectricBlue
            GenerationType.MOTION -> CyanGlow
        },
        onClick = onClick,
        modifier = modifier.testTag("history_tile_${entity.id}"),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(status = entity.status)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete generation",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = entity.outputTitle.ifBlank { entity.modelName },
            style = MaterialTheme.typography.titleSmall,
            color = TextOffWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entity.prompt,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${entity.aspectRatio}" + if (entity.durationSeconds > 0) " · ${entity.durationSeconds}s" else "",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
