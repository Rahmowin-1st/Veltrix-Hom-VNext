package com.veltrix.hom.vnext

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object KineticColor {
    val Ink = Color(0xFF111827)
    val Muted = Color(0xFF667085)
    val Sky = Color(0xFF55A6FF)
    val Mint = Color(0xFF5DD6B1)
    val Violet = Color(0xFF806CF4)
    val Ember = Color(0xFFF3A55C)
    val Surface = Color(0xFFF8FAFF)
    val SurfaceWarm = Color(0xFFFFFBF7)
    val Line = Color(0x243B4A63)
    val Danger = Color(0xFFB42318)
}

enum class VeltrixWorld { HOME, PERSONAL, STORE, PROJECTS }

@Immutable
data class WorldPresentationState(
    val world: VeltrixWorld = VeltrixWorld.HOME,
    val activeProjectId: String? = null,
    val storePreviewId: String? = null,
    val avatarIdentity: String? = null,
)

@Stable
class WorldContinuityCoordinator internal constructor(initial: WorldPresentationState) {
    var state by mutableStateOf(initial)
        private set

    fun enter(world: VeltrixWorld) { state = state.copy(world = world) }
    fun project(id: String?) { state = state.copy(activeProjectId = id) }
    fun preview(itemId: String?) { state = state.copy(storePreviewId = itemId) }
    fun avatar(identity: String?) { state = state.copy(avatarIdentity = identity) }
}

@Composable
fun rememberWorldContinuityCoordinator(): WorldContinuityCoordinator = remember { WorldContinuityCoordinator(WorldPresentationState()) }

@Composable
fun VeltrixKineticWorld(
    world: VeltrixWorld,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "veltrix-world-ambient")
    val raw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
        label = "ambient-phase",
    )
    val phase = if (reducedMotion) .42f else raw
    val accent = when (world) {
        VeltrixWorld.HOME -> KineticColor.Sky
        VeltrixWorld.PERSONAL -> KineticColor.Violet
        VeltrixWorld.STORE -> KineticColor.Ember
        VeltrixWorld.PROJECTS -> KineticColor.Mint
    }
    Box(
        modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFFF9FBFF), Color(0xFFF2F6FF), Color(0xFFFFFBF6)),
                start = Offset.Zero,
                end = Offset(1500f, 2400f),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val p = phase.toDouble() * PI * 2.0
            val primary = Offset(
                x = size.width * (.69f + .035f * cos(p).toFloat()),
                y = size.height * (.22f + .025f * sin(p).toFloat()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = .22f), accent.copy(alpha = .06f), Color.Transparent),
                    center = primary,
                    radius = d * .68f,
                ),
                radius = d * .68f,
                center = primary,
            )
            val secondary = Offset(size.width * .12f, size.height * (.72f - phase * .03f))
            drawCircle(
                brush = Brush.radialGradient(listOf(KineticColor.Mint.copy(alpha = .13f), Color.Transparent), center = secondary, radius = d * .48f),
                radius = d * .48f,
                center = secondary,
            )
            drawOval(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .78f), Color.Transparent), start = Offset(size.width * .15f, size.height * .04f), end = Offset(size.width * .9f, size.height * .36f)),
                topLeft = Offset(size.width * .06f, size.height * .03f),
                size = Size(size.width * .88f, size.height * .36f),
            )
        }
        content()
    }
}

@Composable
fun KineticGlass(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(if (strong) Color.White.copy(alpha = .80f) else Color.White.copy(alpha = .61f))) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .92f), KineticColor.Sky.copy(alpha = .07f), KineticColor.Mint.copy(alpha = .06f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = .84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .72f), Color.Transparent)),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size((size.width - 2.dp.toPx()).coerceAtLeast(0f), (size.height * .44f).coerceAtLeast(0f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((radius - 1.dp).toPx()),
            )
        }
        content()
    }
}

/**
 * Stable renderer for the backend-owned avatar identity. The ID selects presentation DNA only;
 * ownership/equipped/tier truth remains backend-owned. Unlike the legacy orb, this is a full
 * collectible character silhouette and therefore preserves identity across Home/Personal/Store.
 */
@Composable
fun KineticAvatar(
    identity: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "Equipped Veltrix avatar",
) {
    val normalized = identity?.takeIf { it.isNotBlank() }
    val family = ((normalized?.hashCode() ?: 0) and Int.MAX_VALUE) % 5
    val accent = listOf(KineticColor.Sky, KineticColor.Violet, KineticColor.Mint, KineticColor.Ember, Color(0xFFE36D8C))[family]
    Canvas(
        modifier
            .testTag("veltrix-character-${normalized ?: "unknown"}")
            .semantics { this.contentDescription = contentDescription },
    ) {
        val w = size.width
        val h = size.height
        val u = size.minDimension
        val cx = w / 2f

        drawOval(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = .24f), accent.copy(alpha = .05f), Color.Transparent), center = Offset(cx, h * .57f), radius = u * .62f),
            topLeft = Offset(cx - u * .58f, h * .08f),
            size = Size(u * 1.16f, u * 1.16f),
        )
        drawOval(Color(0x160F172A), Offset(cx - u * .29f, h * .83f), Size(u * .58f, u * .10f))

        val bodyTop = h * .48f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(accent.copy(alpha = .98f), accent.copy(alpha = .70f))),
            topLeft = Offset(cx - u * .19f, bodyTop),
            size = Size(u * .38f, u * .34f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .16f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = .28f),
            topLeft = Offset(cx - u * .105f, bodyTop + u * .075f),
            size = Size(u * .21f, u * .12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .05f),
        )

        drawLine(accent.copy(alpha = .82f), Offset(cx - u * .17f, h * .57f), Offset(cx - u * .33f, h * .68f), strokeWidth = u * .075f, cap = StrokeCap.Round)
        drawLine(accent.copy(alpha = .82f), Offset(cx + u * .17f, h * .57f), Offset(cx + u * .33f, h * .68f), strokeWidth = u * .075f, cap = StrokeCap.Round)
        drawLine(KineticColor.Ink.copy(alpha = .54f), Offset(cx - u * .10f, h * .77f), Offset(cx - u * .13f, h * .88f), strokeWidth = u * .075f, cap = StrokeCap.Round)
        drawLine(KineticColor.Ink.copy(alpha = .54f), Offset(cx + u * .10f, h * .77f), Offset(cx + u * .13f, h * .88f), strokeWidth = u * .075f, cap = StrokeCap.Round)

        val headTop = h * .22f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .96f), accent.copy(alpha = .30f))),
            topLeft = Offset(cx - u * .245f, headTop),
            size = Size(u * .49f, u * .31f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .14f),
        )
        drawRoundRect(
            color = KineticColor.Ink.copy(alpha = .78f),
            topLeft = Offset(cx - u * .16f, headTop + u * .085f),
            size = Size(u * .32f, u * .105f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * .052f),
        )
        drawCircle(Color.White, u * .028f, Offset(cx - u * .072f, headTop + u * .137f))
        drawCircle(Color.White, u * .028f, Offset(cx + u * .072f, headTop + u * .137f))

        when (family) {
            0 -> {
                drawLine(accent, Offset(cx, headTop), Offset(cx, headTop - u * .11f), strokeWidth = u * .035f, cap = StrokeCap.Round)
                drawCircle(Color.White, u * .045f, Offset(cx, headTop - u * .12f))
            }
            1 -> {
                val crest = Path().apply {
                    moveTo(cx - u * .13f, headTop + u * .01f)
                    lineTo(cx, headTop - u * .13f)
                    lineTo(cx + u * .13f, headTop + u * .01f)
                    close()
                }
                drawPath(crest, accent.copy(alpha = .9f))
            }
            2 -> {
                drawCircle(accent.copy(alpha = .75f), u * .075f, Offset(cx - u * .27f, headTop + u * .12f))
                drawCircle(accent.copy(alpha = .75f), u * .075f, Offset(cx + u * .27f, headTop + u * .12f))
            }
            3 -> {
                drawArc(accent, -30f, 240f, false, Offset(cx - u * .30f, headTop - u * .09f), Size(u * .60f, u * .34f), style = Stroke(u * .035f, cap = StrokeCap.Round))
            }
            else -> {
                drawRoundRect(accent.copy(alpha = .8f), Offset(cx - u * .31f, headTop + u * .045f), Size(u * .10f, u * .15f), androidx.compose.ui.geometry.CornerRadius(u * .04f))
                drawRoundRect(accent.copy(alpha = .8f), Offset(cx + u * .21f, headTop + u * .045f), Size(u * .10f, u * .15f), androidx.compose.ui.geometry.CornerRadius(u * .04f))
            }
        }
    }
}
