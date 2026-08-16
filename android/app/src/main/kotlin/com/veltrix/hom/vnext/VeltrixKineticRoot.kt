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

/** Presentation continuity only. It deliberately owns no account/economy/progression truth. */
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
fun rememberWorldContinuityCoordinator(): WorldContinuityCoordinator =
    remember { WorldContinuityCoordinator(WorldPresentationState()) }

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
        modifier
            .fillMaxSize()
            .background(
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
                brush = Brush.radialGradient(
                    listOf(KineticColor.Mint.copy(alpha = .13f), Color.Transparent),
                    center = secondary,
                    radius = d * .48f,
                ),
                radius = d * .48f,
                center = secondary,
            )
            drawOval(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .78f), Color.Transparent),
                    start = Offset(size.width * .15f, size.height * .04f),
                    end = Offset(size.width * .9f, size.height * .36f),
                ),
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
    Box(
        modifier
            .clip(shape)
            .background(if (strong) Color.White.copy(alpha = .80f) else Color.White.copy(alpha = .61f)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .92f), KineticColor.Sky.copy(alpha = .07f), KineticColor.Mint.copy(alpha = .06f)),
                ),
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

@Composable
fun KineticAvatar(
    identity: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "Equipped Veltrix avatar",
) {
    val family = ((identity?.hashCode() ?: 0) and Int.MAX_VALUE) % 5
    val accent = listOf(KineticColor.Sky, KineticColor.Violet, KineticColor.Mint, KineticColor.Ember, Color(0xFFE36D8C))[family]
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        val c = center
        val r = size.minDimension * .30f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, accent.copy(alpha = .38f), accent.copy(alpha = .08f))),
            radius = r * 1.65f,
            center = c,
        )
        when (family) {
            0 -> {
                drawCircle(accent.copy(alpha = .92f), r, c)
                drawArc(KineticColor.Ink.copy(alpha = .72f), -35f, 250f, false, Offset(c.x-r, c.y-r), Size(r*2,r*2), style = Stroke(r*.13f, cap = StrokeCap.Round))
            }
            1 -> {
                val p = Path().apply {
                    moveTo(c.x, c.y-r*1.15f); lineTo(c.x+r*.88f,c.y-r*.18f); lineTo(c.x+r*.52f,c.y+r); lineTo(c.x-r*.52f,c.y+r); lineTo(c.x-r*.88f,c.y-r*.18f); close()
                }
                drawPath(p, accent.copy(alpha = .92f)); drawPath(p, Color.White.copy(alpha=.7f), style=Stroke(r*.08f))
            }
            2 -> {
                repeat(6) { i ->
                    val a = i * PI / 3.0
                    val petal = Offset(c.x + cos(a).toFloat()*r*.62f, c.y + sin(a).toFloat()*r*.62f)
                    drawCircle(accent.copy(alpha=.56f), r*.52f, petal)
                }
                drawCircle(Color.White, r*.52f, c)
            }
            3 -> {
                drawCircle(accent.copy(alpha=.92f), r*.82f, c)
                drawArc(accent.copy(alpha=.58f), -50f, 280f, false, Offset(c.x-r*1.35f,c.y-r*1.35f), Size(r*2.7f,r*2.7f), style=Stroke(r*.18f,cap=StrokeCap.Round))
            }
            else -> {
                drawRoundRect(accent.copy(alpha=.92f), Offset(c.x-r,c.y-r), Size(r*2,r*2), androidx.compose.ui.geometry.CornerRadius(r*.34f))
                drawCircle(Color.White.copy(alpha=.9f), r*.18f, Offset(c.x-r*.35f,c.y-r*.1f))
                drawCircle(Color.White.copy(alpha=.9f), r*.18f, Offset(c.x+r*.35f,c.y-r*.1f))
            }
        }
    }
}
