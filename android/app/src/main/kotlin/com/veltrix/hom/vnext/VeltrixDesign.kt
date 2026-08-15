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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class VeltrixEffectPolicy(val reducedMotion:Boolean,val highContrast:Boolean)

object VeltrixColors {
    val Ink=Color(0xFF15233D);val InkMuted=Color(0xFF60708C);val Sky=Color(0xFF4B7DFF);val SkyDeep=Color(0xFF3156C8)
    val Ice=Color(0xFFF5F8FF);val Mint=Color(0xFF41C7A2);val Amber=Color(0xFFFFB650);val Error=Color(0xFFB4233D)
    val Glass=Color(0xC7FFFFFF);val GlassStrong=Color(0xE9FFFFFF);val GlassShadow=Color(0x24173A76);val Scrim=Color(0x4A10213D)
}

private val VeltrixScheme=lightColorScheme(primary=VeltrixColors.Sky,onPrimary=Color.White,secondary=VeltrixColors.Mint,onSecondary=Color(0xFF0C3229),background=VeltrixColors.Ice,onBackground=VeltrixColors.Ink,surface=Color.White,onSurface=VeltrixColors.Ink,error=VeltrixColors.Error)

@Composable fun VeltrixTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=VeltrixScheme,typography=Typography(),content=content)}

@Composable
fun rememberVeltrixEffectPolicy():VeltrixEffectPolicy {
    val context=LocalContext.current
    return remember(context){
        val scale=runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)}.getOrDefault(1f)
        val a11y=context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        VeltrixEffectPolicy(scale==0f,Build.VERSION.SDK_INT>=36&&a11y?.isHighContrastTextEnabled==true)
    }
}

fun veltrixMotion(reduced:Boolean):FiniteAnimationSpec<Float> = if(reduced)snap() else spring(dampingRatio=.82f,stiffness=Spring.StiffnessMediumLow)

@Composable
fun VeltrixWorldBackground(modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    Box(modifier.fillMaxSize().drawWithCache{
        val base=Brush.verticalGradient(listOf(Color(0xFFFAFCFF),Color(0xFFEEF4FF),Color(0xFFF8FBFF)))
        val intelligence=Brush.radialGradient(listOf(Color(0x5983A7FF),Color.Transparent),Offset(size.width*.16f,size.height*.14f),size.minDimension*.86f)
        val growth=Brush.radialGradient(listOf(Color(0x365DE8C8),Color.Transparent),Offset(size.width*.92f,size.height*.64f),size.minDimension*.78f)
        val project=Brush.radialGradient(listOf(Color(0x1E8A6FFF),Color.Transparent),Offset(size.width*.62f,size.height*.94f),size.minDimension*.58f)
        onDrawBehind{drawRect(base);drawRect(intelligence);drawRect(growth);drawRect(project)}
    },content=content)
}

/**
 * Bounded, draw-phase optical material: transmission + internal edge depth + localized
 * highlight + lower-edge concentration. No full-screen blur/offscreen effect is required,
 * so the material has a deterministic high-contrast/performance fallback.
 */
@Composable
fun GlassSurface(
    modifier:Modifier=Modifier,
    radius:Dp=28.dp,
    strong:Boolean=false,
    policy:VeltrixEffectPolicy=rememberVeltrixEffectPolicy(),
    content:@Composable BoxScope.()->Unit,
){
    val shape=RoundedCornerShape(radius)
    val fill=when{policy.highContrast->Color(0xFFFCFDFF);strong->VeltrixColors.GlassStrong;else->VeltrixColors.Glass}
    Box(modifier.clip(shape).drawWithCache{
        val corner=androidx.compose.ui.geometry.CornerRadius(radius.toPx())
        val body=Brush.linearGradient(
            listOf(fill.copy(alpha=(fill.alpha*.93f).coerceAtMost(1f)),fill.copy(alpha=(fill.alpha*.73f).coerceAtMost(1f)),fill.copy(alpha=(fill.alpha*.89f).coerceAtMost(1f))),
            Offset.Zero,Offset(size.width,size.height),
        )
        val topCaustic=Brush.radialGradient(listOf(Color.White.copy(alpha=if(policy.highContrast)0f else .88f),Color.White.copy(alpha=.16f),Color.Transparent),Offset(size.width*.26f,-size.height*.08f),size.width*.78f)
        val ambientTint=Brush.radialGradient(listOf(Color(0x365D88FF),Color.Transparent),Offset(size.width*.98f,size.height*.72f),size.minDimension*.95f)
        val rim=Brush.linearGradient(listOf(Color.White.copy(alpha=.98f),Color.White.copy(alpha=.34f),Color(0x597B98C9),Color.White.copy(alpha=.62f)),Offset.Zero,Offset(size.width,size.height))
        val innerRim=Brush.linearGradient(listOf(Color.White.copy(alpha=.54f),Color.Transparent,Color(0x28708EC0)),Offset(size.width*.08f,0f),Offset(size.width*.92f,size.height))
        val lowerDepth=Brush.verticalGradient(listOf(Color.Transparent,Color(0x251A477C)),startY=size.height*.55f,endY=size.height)
        onDrawBehind{
            drawRoundRect(body,cornerRadius=corner)
            if(!policy.highContrast){
                drawRoundRect(ambientTint,cornerRadius=corner,blendMode=BlendMode.SrcOver)
                drawRoundRect(topCaustic,cornerRadius=corner)
                drawRoundRect(lowerDepth,cornerRadius=corner)
                drawRoundRect(Color.White.copy(alpha=.20f),topLeft=Offset(1.5.dp.toPx(),1.5.dp.toPx()),size=Size((size.width-3.dp.toPx()).coerceAtLeast(0f),(size.height-3.dp.toPx()).coerceAtLeast(0f)),cornerRadius=androidx.compose.ui.geometry.CornerRadius((radius-1.5.dp).toPx().coerceAtLeast(0f)),style=Stroke(.7.dp.toPx()))
                drawRoundRect(VeltrixColors.GlassShadow,topLeft=Offset(0f,(size.height-2.5.dp.toPx()).coerceAtLeast(0f)),size=Size(size.width,2.5.dp.toPx()),cornerRadius=corner)
            }
            drawRoundRect(rim,cornerRadius=corner,style=Stroke(if(policy.highContrast)1.5.dp.toPx() else 1.dp.toPx()))
            if(!policy.highContrast)drawRoundRect(innerRim,topLeft=Offset(1.dp.toPx(),1.dp.toPx()),size=Size((size.width-2.dp.toPx()).coerceAtLeast(0f),(size.height-2.dp.toPx()).coerceAtLeast(0f)),cornerRadius=androidx.compose.ui.geometry.CornerRadius((radius-1.dp).toPx().coerceAtLeast(0f)),style=Stroke(.65.dp.toPx()))
        }
    },content=content)
}

@Composable
fun PressableGlass(
    onClick:()->Unit,
    modifier:Modifier=Modifier,
    radius:Dp=22.dp,
    strong:Boolean=false,
    enabled:Boolean=true,
    role:Role=Role.Button,
    content:@Composable BoxScope.()->Unit,
){
    val policy=rememberVeltrixEffectPolicy();val source=remember{MutableInteractionSource()};val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed&&enabled).968f else 1f,veltrixMotion(policy.reducedMotion),label="glass-deform-scale")
    val lift by animateFloatAsState(if(pressed&&enabled)1.2f else 0f,veltrixMotion(policy.reducedMotion),label="glass-deform-lift")
    GlassSurface(
        modifier=Modifier.heightIn(max=80.dp).then(modifier).graphicsLayer{scaleX=scale;scaleY=scale;translationY=lift;alpha=if(enabled)1f else .5f}.semantics{this.role=role}.clip(RoundedCornerShape(radius)).clickable(enabled=enabled,interactionSource=source,indication=null,onClick=onClick),
        radius=radius,strong=strong,policy=policy,content=content,
    )
}
