package org.rigbyfoundation.nuggetvpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetAmber
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetOrange

@Composable
fun PowerButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isConnected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isConnected) 0.4f else 0f,
        animationSpec = tween(700),
        label = "glow"
    )

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val connectingRotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isConnected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(500),
        label = "bgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = tween(500),
        label = "iconColor"
    )

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NuggetOrange.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Main button
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isConnected) 24.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = if (isConnected) NuggetOrange.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f),
                    spotColor = if (isConnected) NuggetOrange.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f)
                )
                .clip(CircleShape)
                .background(
                    if (isConnected) {
                        Brush.linearGradient(colors = listOf(NuggetAmber, NuggetOrange))
                    } else {
                        Brush.linearGradient(colors = listOf(bgColor, bgColor))
                    }
                )
                .then(
                    if (isConnected) Modifier else Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        CircleShape
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Pulse overlay when connected
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = pulseAlpha))
                )
            }

            Icon(
                imageVector = Icons.Default.Power,
                contentDescription = "Toggle VPN",
                tint = iconColor,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}
