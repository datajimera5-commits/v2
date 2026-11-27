package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.FireOrderStatus
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FireOrange
import com.example.ui.theme.FireRed
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.VioletRetry

fun performSafeHaptic(haptics: HapticFeedback, enabled: Boolean, type: HapticFeedbackType = HapticFeedbackType.LongPress) {
    if (enabled) {
        haptics.performHapticFeedback(type)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (accentColor != null) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.55f),
                SurfaceCardBorder.copy(alpha = 0.75f),
                accentColor.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                SurfaceCardBorder.copy(alpha = 0.65f)
            )
        )
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            SurfaceElevated.copy(alpha = 0.90f),
            SurfaceDark.copy(alpha = 0.94f)
        )
    )

    val shape = RoundedCornerShape(cornerRadius)
    val baseMod = modifier
        .clip(shape)
        .background(backgroundBrush)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }

    Box(modifier = baseMod) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            SurfaceElevated.copy(alpha = 0.55f),
            Color(0xFF334155).copy(alpha = 0.85f),
            SurfaceElevated.copy(alpha = 0.55f)
        ),
        start = Offset(translate, 0f),
        end = Offset(translate + 280f, 180f)
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun CreditPill(
    credits: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("credit_balance_pill")
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = SurfaceElevated,
        border = BorderStroke(1.dp, FireOrange.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Credits",
                tint = FireOrange,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "$credits Credits",
                style = MaterialTheme.typography.labelLarge,
                color = TextOffWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusBadge(
    status: FireOrderStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        FireOrderStatus.UPLOADING, FireOrderStatus.RENDERING -> FireOrange
        FireOrderStatus.HOLD -> CyanGlow
        FireOrderStatus.RETRY -> VioletRetry
        FireOrderStatus.CANCELLED, FireOrderStatus.FAILED -> CrimsonError
        FireOrderStatus.READY, FireOrderStatus.DOWNLOADED -> EmeraldSuccess
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FireIgniteButton(
    text: String,
    isLoading: Boolean,
    loadingLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "fire_ignite_button"
) {
    val gradient = Brush.horizontalGradient(
        colors = if (isLoading) {
            listOf(Color(0xFF9A3412), Color(0xFF7C2D12))
        } else {
            listOf(FireOrange, FireRed, Color(0xFFDC2626))
        }
    )

    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = TextOffWhite,
            disabledContentColor = TextOffWhite.copy(alpha = 0.85f)
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TextOffWhite,
                    strokeWidth = 2.2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextOffWhite
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = "Ignite Fire Mode",
                    tint = TextOffWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextOffWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextOffWhite
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
