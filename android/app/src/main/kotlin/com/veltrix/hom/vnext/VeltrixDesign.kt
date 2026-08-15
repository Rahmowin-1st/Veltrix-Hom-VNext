package com.veltrix.hom.vnext

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class VeltrixEffectPolicy(
    val reducedMotion: Boolean,
    val highContrast: Boolean,
)

object VeltrixColors {
    val Ink = Color(0xFF15233D)
    val InkMuted = Color(0xFF60708C)
    val Sky = Color(0xFF4B7DFF)
    val SkyDeep = Color(0xFF3156C8)
    val Ice = Color(0xFFF5F8FF)
    val Mint = Color(0xFF41C7A2)
    val Amber = Color(0xFFFFB650)
    val Error = Color(0xFFB4233D)
    val Glass = Color(0xB8FFFFFF)
    val GlassStrong = Color(0xE8FFFFFF)
    val GlassShadow = Color(0x1A173A76)
    val Scrim = Color(0x4A10213D)
}

private val VeltrixScheme = lightColorScheme(
    primary = VeltrixColors.Sky,
    onPrimary = Color.White,
    secondary = VeltrixColors.Mint,
    onSecondary = Color(0xFF0C3229),
    background = VeltrixColors.Ice,
    onBackground = VeltrixColors.Ink,
    surface = Color.White,
    onSurface = VeltrixColors.Ink,
    error = VeltrixColors.Error,
)

@Composable
fun VeltrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VeltrixScheme,
        typography = Typography(),
        content = content,
    )
}

@Composable
fun rememberVeltrixEffectPolicy(): VeltrixEffectPolicy {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val highContrast = Build.VERSION.SDK_INT >= 36 && accessibility?.isHighContrastTextEnabled == true
        VeltrixEffectPolicy(
            reducedMotion = scale == 0f,
            highContrast = highContrast,
        )
    }
}

fun veltrixMotion(reduced: Boolean): FiniteAnimationSpec<Float> =
    if (reduced) snap() else spring(
        dampingRatio = .82f,
        stiffness = Spring.StiffnessMediumLow,
    )

@Composable
fun VeltrixWorldBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val base = Brush.verticalGradient(
                listOf(Color(0xFFF9FBFF), Color(0xFFEFF5FF), Color(0xFFF7FBFF)),
            )
            val coolGlow = Brush.radialGradient(
                listOf(Color(0x5C83A7FF), Color.Transparent),
                center = Offset(size.width * .18f, size.height * .16f),
                radius = size.minDimension * .82f,
            )
            val mintGlow = Brush.radialGradient(
                listOf(Color(0x385DE8C8), Color.Transparent),
                center = Offset(size.width * .92f, size.height * .62f),
                radius = size.minDimension * .78f,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(coolGlow)
                drawRect(mintGlow)
            }
        },
        content = content,
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    strong: Boolean = false,
    policy: VeltrixEffectPolicy = rememberVeltrixEffectPolicy(),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val fill = when {
        policy.highContrast -> Color(0xFFFDFEFF)
        strong -> VeltrixColors.GlassStrong
        else -> VeltrixColors.Glass
    }
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val body = Brush.linearGradient(
                    listOf(fill, fill.copy(alpha = (fill.alpha * .83f).coerceAtMost(1f))),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val rim = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = .96f),
                        Color.White.copy(alpha = .28f),
                        Color(0x6685A6DC),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val highlight = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = if (policy.highContrast) 0f else .72f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * .22f, 0f),
                    radius = size.width * .72f,
                )
                onDrawBehind {
                    val corner = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
                    drawRoundRect(body, cornerRadius = corner)
                    if (!policy.highContrast) {
                        drawRoundRect(highlight, cornerRadius = corner)
                        drawRoundRect(
                            VeltrixColors.GlassShadow,
                            topLeft = Offset(0f, size.height - 2.dp.toPx()),
                            size = Size(size.width, 2.dp.toPx()),
                            cornerRadius = corner,
                        )
                    }
                    drawRoundRect(
                        rim,
                        cornerRadius = corner,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
            }
            .border(
                .5.dp,
                Color.White.copy(alpha = if (policy.highContrast) .9f else .35f),
                shape,
            ),
        content = content,
    )
}

@Composable
fun PressableGlass(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    strong: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = rememberVeltrixEffectPolicy()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val circularIdentity = radius.value >= 900f
    var entered by remember(circularIdentity, policy.reducedMotion) {
        mutableStateOf(!circularIdentity || policy.reducedMotion)
    }
    LaunchedEffect(circularIdentity, policy.reducedMotion) {
        if (circularIdentity) entered = true
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) .975f else 1f,
        animationSpec = veltrixMotion(policy.reducedMotion),
        label = "veltrix-press",
    )
    val entranceScale by animateFloatAsState(
        targetValue = if (entered) 1f else .94f,
        animationSpec = veltrixMotion(policy.reducedMotion),
        label = "veltrix-circular-entry",
    )
    GlassSurface(
        // Interactive glass is a control, not a full-height content surface. Bounding
        // it before caller modifiers prevents a child fillMaxSize() from claiming an
        // entire Row/Column axis and starving sibling weighted content.
        modifier = Modifier
            .heightIn(max = 80.dp)
            .then(modifier)
            .graphicsLayer {
                val combined = pressScale * entranceScale
                scaleX = combined
                scaleY = combined
                alpha = when {
                    !enabled -> .5f
                    circularIdentity && !policy.reducedMotion -> .72f + (.28f * entranceScale)
                    else -> 1f
                }
            }
            .semantics { this.role = role }
            .clip(RoundedCornerShape(radius))
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                onClick = onClick,
            ),
        radius = radius,
        strong = strong,
        policy = policy,
        content = content,
    )
}
