package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.LicenseStatus
import com.example.data.local.UserSessionPrefs
import com.example.data.security.SecurityStatus
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    session: UserSessionPrefs,
    securityStatus: SecurityStatus,
    onDismiss: () -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onResetLicenseGate: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark,
        contentColor = TextOffWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("settings_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Studio Settings",
                style = MaterialTheme.typography.titleLarge,
                color = TextOffWhite
            )

            // Haptics Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Haptic Feedback",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextOffWhite
                    )
                    Text(
                        text = "Tactile pulse on Ignite, Enhance & completion",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = session.hapticsEnabled,
                    onCheckedChange = onToggleHaptics,
                    colors = SwitchDefaults.colors(checkedThumbColor = TextOffWhite, checkedTrackColor = FireOrange),
                    modifier = Modifier.testTag("haptics_toggle_switch")
                )
            }

            HorizontalDivider(color = SurfaceCardBorder)

            Text(
                text = "Account & Protection",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = "Account protection status",
                        tint = EmeraldSuccess
                    )
                    Column {
                        Text(
                            text = if (session.licenseStatus == LicenseStatus.ACTIVE) {
                                "Pro License · Bound to ${session.deviceUuid}"
                            } else {
                                "Free Trial · ${session.deviceUuid}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextOffWhite
                        )
                        Text(
                            text = "${securityStatus.summaryLabel} · Seedance 2.5 Cloud Ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onResetLicenseGate()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("switch_license_button")
            ) {
                Text("Switch / Deactivate License", color = TextOffWhite)
            }
        }
    }
}
