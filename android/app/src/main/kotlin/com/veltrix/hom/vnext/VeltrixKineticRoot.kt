package com.veltrix.hom.vnext

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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

/** Final Root Reset presentation DNA. These colors are intentionally quieter than a gamer HUD. */
object KineticColor {
    val Ink = Color(0xFF101828)
    val Muted = Color(0xFF667085)
    val Sky = Color(0xFF4C82FF)
    val Mint = Color(0xFF55CFA9)
    val Violet = Color(0xFF7866E9)
    val Ember = Color(0xFFF0A25E)
    val Rose = Color(0xFFE06D93)
    val Surface = Color(0xFFF8FAFF)
    val SurfaceWarm = Color(0xFFFFFBF7)
    val Line = Color(0x2C40516B)
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
fun rememberWorldContinuityCoordinator(): WorldContinuityCoordinator = remember {
    WorldContinuityCoordinator(WorldPresentationState())
}

/**
 * A living world backdrop, not a pastel gradient wallpaper. The scene is built from bounded
 * vector layers: environmental light volumes, terrain-like planes and a restrained constellation
 * of learning signals. It keeps the resting UI calm while giving every primary world distinct DNA.
 */
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
        animationSpec = infiniteRepeatable(tween(11000), RepeatMode.Reverse),
        label = "ambient-phase",
    )
    val phase = if (reducedMotion) .43f else raw
    val accent = when (world) {
        VeltrixWorld.HOME -> KineticColor.Sky
        VeltrixWorld.PERSONAL -> KineticColor.Violet
        VeltrixWorld.STORE -> KineticColor.Ember
        VeltrixWorld.PROJECTS -> KineticColor.Mint
    }
    val companion = when (world) {
        VeltrixWorld.HOME -> KineticColor.Mint
        VeltrixWorld.PERSONAL -> KineticColor.Rose
        VeltrixWorld.STORE -> KineticColor.Violet
        VeltrixWorld.PROJECTS -> KineticColor.Sky
    }
    val baseTop = when (world) {
        VeltrixWorld.HOME -> Color(0xFFF7F9FF)
        VeltrixWorld.PERSONAL -> Color(0xFFFAF8FF)
        VeltrixWorld.STORE -> Color(0xFFFFFAF5)
        VeltrixWorld.PROJECTS -> Color(0xFFF6FCFA)
    }

    Box(
        modifier.fillMaxSize().drawWithCache {
            val d = size.minDimension
            val base = Brush.linearGradient(
                listOf(baseTop, Color(0xFFF7FAFF), Color(0xFFFFFCF8)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val upperLight = Brush.radialGradient(
                listOf(accent.copy(alpha = .30f), accent.copy(alpha = .10f), Color.Transparent),
                center = Offset(size.width * .78f, size.height * .13f),
                radius = d * .82f,
            )
            val lowerLight = Brush.radialGradient(
                listOf(companion.copy(alpha = .19f), companion.copy(alpha = .05f), Color.Transparent),
                center = Offset(size.width * .12f, size.height * .80f),
                radius = d * .70f,
            )
            val warmLift = Brush.radialGradient(
                listOf(Color(0x4DFFD7A3), Color.Transparent),
                center = Offset(size.width * .92f, size.height * .83f),
                radius = d * .48f,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(upperLight)
                drawRect(lowerLight)
                drawRect(warmLift)

                // Distant translucent world planes: depth without blur-everywhere.
                val horizon = Path().apply {
                    moveTo(-size.width * .08f, size.height * .68f)
                    cubicTo(
                        size.width * .18f, size.height * .60f,
                        size.width * .42f, size.height * .75f,
                        size.width * .70f, size.height * .64f,
                    )
                    cubicTo(
                        size.width * .88f, size.height * .57f,
                        size.width * 1.03f, size.height * .63f,
                        size.width * 1.08f, size.height * .58f,
                    )
                    lineTo(size.width * 1.08f, size.height * 1.08f)
                    lineTo(-size.width * .08f, size.height * 1.08f)
                    close()
                }
                drawPath(horizon, Brush.verticalGradient(listOf(Color.White.copy(.18f), accent.copy(.045f))))

                val ribbon = Path().apply {
                    moveTo(-size.width * .12f, size.height * .34f)
                    cubicTo(
                        size.width * .20f, size.height * .23f,
                        size.width * .51f, size.height * .43f,
                        size.width * 1.12f, size.height * .26f,
                    )
                }
                drawPath(ribbon, Color.White.copy(alpha = .54f), style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
                drawPath(ribbon, accent.copy(alpha = .13f), style = Stroke(5.5.dp.toPx(), cap = StrokeCap.Round))

                // A few semantic signal nodes instead of noisy particles.
                val shift = phase * d * .018f
                val nodes = listOf(
                    Offset(size.width * .84f + shift, size.height * .20f),
                    Offset(size.width * .67f - shift, size.height * .31f),
                    Offset(size.width * .18f + shift * .35f, size.height * .73f),
                )
                nodes.forEachIndexed { index, node ->
                    val c = if (index == 2) companion else accent
                    drawCircle(Color.White.copy(.66f), 8.dp.toPx(), node)
                    drawCircle(c.copy(.30f), 5.5.dp.toPx(), node)
                    drawCircle(c.copy(.88f), 2.2.dp.toPx(), node)
                }

                // World-specific large optical landmark.
                val landmark = Offset(size.width * .82f, size.height * (.13f + .012f * phase))
                drawCircle(Color.White.copy(.24f), d * .18f, landmark, style = Stroke(.8.dp.toPx()))
                drawCircle(accent.copy(.13f), d * .125f, landmark, style = Stroke(1.1.dp.toPx()))
                drawArc(
                    companion.copy(.36f),
                    startAngle = 205f,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(landmark.x - d * .155f, landmark.y - d * .155f),
                    size = Size(d * .31f, d * .31f),
                    style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        },
    ) {
        content()
    }
}

/**
 * Veltrix optical material. This deliberately is NOT `white alpha + border` glassmorphism.
 * It models transmission, optical thickness, localized environment tint, edge concentration,
 * top caustic and lower-depth absorption using draw-phase vector work only.
 */
@Composable
fun KineticGlass(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = rememberVeltrixEffectPolicy()
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val corner = CornerRadius(radius.toPx())
                val fillAlpha = when {
                    policy.highContrast -> 1f
                    strong -> .86f
                    else -> .67f
                }
                val body = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = fillAlpha),
                        Color(0xFFF7FAFF).copy(alpha = (fillAlpha - .12f).coerceAtLeast(.45f)),
                        Color(0xFFFFFCF8).copy(alpha = (fillAlpha - .05f).coerceAtLeast(.50f)),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val environment = Brush.radialGradient(
                    listOf(Color(0x385E86FF), Color(0x1857CFA9), Color.Transparent),
                    center = Offset(size.width * .92f, size.height * .73f),
                    radius = size.minDimension * 1.05f,
                )
                val caustic = Brush.radialGradient(
                    listOf(Color.White.copy(.94f), Color.White.copy(.18f), Color.Transparent),
                    center = Offset(size.width * .24f, -size.height * .05f),
                    radius = size.width * .78f,
                )
                val transmittedBand = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(.30f), Color.Transparent),
                    start = Offset(size.width * .03f, size.height * .74f),
                    end = Offset(size.width * .92f, size.height * .14f),
                )
                val edgeLight = Brush.linearGradient(
                    listOf(Color.White.copy(.98f), Color.White.copy(.33f), Color(0x4D6B86BC), Color.White.copy(.64f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val lowerDepth = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x27193B68)),
                    startY = size.height * .55f,
                    endY = size.height,
                )
                val sideThickness = Brush.horizontalGradient(
                    listOf(Color.White.copy(.38f), Color.Transparent, Color.Transparent, Color(0x306284BD)),
                )
                onDrawBehind {
                    drawRoundRect(body, cornerRadius = corner)
                    if (!policy.highContrast) {
                        drawRoundRect(environment, cornerRadius = corner, blendMode = BlendMode.SrcOver)
                        drawRoundRect(caustic, cornerRadius = corner)
                        drawRoundRect(transmittedBand, cornerRadius = corner)
                        drawRoundRect(lowerDepth, cornerRadius = corner)
                        drawRoundRect(sideThickness, cornerRadius = corner)
                    }
                    drawRoundRect(
                        edgeLight,
                        cornerRadius = corner,
                        style = Stroke(if (policy.highContrast) 1.5.dp.toPx() else 1.05.dp.toPx()),
                    )
                    if (!policy.highContrast && size.width > 12.dp.toPx() && size.height > 12.dp.toPx()) {
                        drawRoundRect(
                            Color.White.copy(.24f),
                            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                            size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                            cornerRadius = CornerRadius((radius - 2.dp).toPx().coerceAtLeast(0f)),
                            style = Stroke(.65.dp.toPx()),
                        )
                        drawRoundRect(
                            Color(0x1F173A70),
                            topLeft = Offset(3.dp.toPx(), (size.height - 4.dp.toPx()).coerceAtLeast(0f)),
                            size = Size((size.width - 6.dp.toPx()).coerceAtLeast(0f), 2.dp.toPx()),
                            cornerRadius = corner,
                        )
                    }
                }
            },
        content = content,
    )
}

/**
 * Stable collectible character renderer. Backend owns identity/tier/equipped truth; this code owns
 * only the visual family. Layering, specular highlights and accessory silhouettes make the avatar
 * feel like a small collectible object rather than a flat mascot clip-art asset.
 */
@Composable
fun KineticAvatar(
    identity: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "Equipped Veltrix avatar",
) {
    val normalized = identity?.takeIf { it.isNotBlank() }
    val family = ((normalized?.hashCode() ?: 0) and Int.MAX_VALUE) % 5
    val accent = listOf(KineticColor.Sky, KineticColor.Violet, KineticColor.Mint, KineticColor.Ember, KineticColor.Rose)[family]
    val accentDeep = when (family) {
        0 -> Color(0xFF355FC8)
        1 -> Color(0xFF5545B8)
        2 -> Color(0xFF278D72)
        3 -> Color(0xFFB86A2E)
        else -> Color(0xFFB44870)
    }

    Canvas(
        modifier
            .testTag("veltrix-character-${normalized ?: "unknown"}")
            .semantics { this.contentDescription = contentDescription },
    ) {
        val w = size.width
        val h = size.height
        val u = size.minDimension
        val cx = w / 2f

        // Environment halo + contact shadow anchor the character in the world.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(.30f), accent.copy(.08f), Color.Transparent),
                center = Offset(cx, h * .49f),
                radius = u * .60f,
            ),
            radius = u * .60f,
            center = Offset(cx, h * .49f),
        )
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0x40101828), Color.Transparent), center = Offset(cx, h * .88f), radius = u * .34f),
            topLeft = Offset(cx - u * .38f, h * .82f),
            size = Size(u * .76f, u * .14f),
        )

        // Legs + feet behind body.
        drawLine(accentDeep.copy(.66f), Offset(cx - u * .095f, h * .69f), Offset(cx - u * .13f, h * .84f), u * .065f, cap = StrokeCap.Round)
        drawLine(accentDeep.copy(.66f), Offset(cx + u * .095f, h * .69f), Offset(cx + u * .13f, h * .84f), u * .065f, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFF25334B).copy(.62f), Offset(cx - u * .205f, h * .835f), Size(u * .16f, u * .065f), CornerRadius(u * .03f))
        drawRoundRect(Color(0xFF25334B).copy(.62f), Offset(cx + u * .045f, h * .835f), Size(u * .16f, u * .065f), CornerRadius(u * .03f))

        // Arms with bright joint caps.
        drawLine(accentDeep.copy(.76f), Offset(cx - u * .18f, h * .52f), Offset(cx - u * .34f, h * .65f), u * .075f, cap = StrokeCap.Round)
        drawLine(accentDeep.copy(.76f), Offset(cx + u * .18f, h * .52f), Offset(cx + u * .34f, h * .65f), u * .075f, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(.90f), u * .043f, Offset(cx - u * .35f, h * .66f))
        drawCircle(Color.White.copy(.90f), u * .043f, Offset(cx + u * .35f, h * .66f))

        // Main body shell with material depth.
        val bodyTop = h * .46f
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(.92f), accent.copy(.92f), accentDeep.copy(.90f)),
                start = Offset(cx - u * .18f, bodyTop),
                end = Offset(cx + u * .20f, bodyTop + u * .36f),
            ),
            topLeft = Offset(cx - u * .20f, bodyTop),
            size = Size(u * .40f, u * .34f),
            cornerRadius = CornerRadius(u * .15f),
        )
        drawRoundRect(
            Color.White.copy(.42f),
            Offset(cx - u * .125f, bodyTop + u * .055f),
            Size(u * .19f, u * .045f),
            CornerRadius(u * .022f),
        )
        drawCircle(Color.White.copy(.90f), u * .055f, Offset(cx, bodyTop + u * .20f))
        drawCircle(accentDeep.copy(.90f), u * .028f, Offset(cx, bodyTop + u * .20f))

        // Head shell + visor. The shell receives a diagonal highlight to read as a real object.
        val headTop = h * .19f
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.White, Color(0xFFF2F6FF), accent.copy(.48f), accentDeep.copy(.36f)),
                start = Offset(cx - u * .26f, headTop),
                end = Offset(cx + u * .28f, headTop + u * .33f),
            ),
            topLeft = Offset(cx - u * .255f, headTop),
            size = Size(u * .51f, u * .32f),
            cornerRadius = CornerRadius(u * .14f),
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF17233B), Color(0xFF2A3958), Color(0xFF101828))),
            topLeft = Offset(cx - u * .175f, headTop + u * .085f),
            size = Size(u * .35f, u * .115f),
            cornerRadius = CornerRadius(u * .055f),
        )
        drawRoundRect(
            Color.White.copy(.20f),
            Offset(cx - u * .145f, headTop + u * .098f),
            Size(u * .18f, u * .022f),
            CornerRadius(u * .011f),
        )
        drawCircle(Color.White, u * .026f, Offset(cx - u * .073f, headTop + u * .145f))
        drawCircle(Color.White, u * .026f, Offset(cx + u * .073f, headTop + u * .145f))
        drawCircle(accent.copy(.95f), u * .012f, Offset(cx - u * .073f, headTop + u * .145f))
        drawCircle(accent.copy(.95f), u * .012f, Offset(cx + u * .073f, headTop + u * .145f))

        // Side glass nodes communicate collectible hardware without making it humanoid-heavy.
        drawCircle(accent.copy(.75f), u * .055f, Offset(cx - u * .275f, headTop + u * .16f))
        drawCircle(accent.copy(.75f), u * .055f, Offset(cx + u * .275f, headTop + u * .16f))
        drawCircle(Color.White.copy(.76f), u * .026f, Offset(cx - u * .275f, headTop + u * .16f))
        drawCircle(Color.White.copy(.76f), u * .026f, Offset(cx + u * .275f, headTop + u * .16f))

        // Family-specific crown/accessory preserves persistent identity between worlds.
        when (family) {
            0 -> {
                drawLine(accentDeep, Offset(cx, headTop), Offset(cx, headTop - u * .12f), u * .032f, cap = StrokeCap.Round)
                drawCircle(Color.White, u * .046f, Offset(cx, headTop - u * .125f))
                drawCircle(accent, u * .025f, Offset(cx, headTop - u * .125f))
            }
            1 -> {
                val crest = Path().apply {
                    moveTo(cx - u * .15f, headTop + u * .015f)
                    lineTo(cx, headTop - u * .15f)
                    lineTo(cx + u * .15f, headTop + u * .015f)
                    close()
                }
                drawPath(crest, Brush.verticalGradient(listOf(Color.White.copy(.75f), accent, accentDeep)))
            }
            2 -> {
                drawArc(accentDeep, 205f, 130f, false, Offset(cx - u * .28f, headTop - u * .09f), Size(u * .56f, u * .28f), style = Stroke(u * .033f, cap = StrokeCap.Round))
                drawCircle(accent, u * .042f, Offset(cx - u * .23f, headTop + u * .005f))
                drawCircle(accent, u * .042f, Offset(cx + u * .23f, headTop + u * .005f))
            }
            3 -> {
                drawArc(accentDeep, -30f, 240f, false, Offset(cx - u * .31f, headTop - u * .10f), Size(u * .62f, u * .35f), style = Stroke(u * .035f, cap = StrokeCap.Round))
            }
            else -> {
                drawRoundRect(accentDeep.copy(.82f), Offset(cx - u * .33f, headTop + u * .035f), Size(u * .11f, u * .16f), CornerRadius(u * .045f))
                drawRoundRect(accentDeep.copy(.82f), Offset(cx + u * .22f, headTop + u * .035f), Size(u * .11f, u * .16f), CornerRadius(u * .045f))
            }
        }
    }
}
