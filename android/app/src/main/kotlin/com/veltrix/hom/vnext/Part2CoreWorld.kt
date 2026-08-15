package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private val coreNumber = NumberFormat.getIntegerInstance(Locale.US)
private fun Long.corePretty() = coreNumber.format(this)
private fun String.coreTitle() = lowercase(Locale.US).replace('_',' ').replaceFirstChar { it.uppercaseChar().toString() }

@Composable
fun Part2HomeScreen(
    home: RepositoryState<HomeFinalModel>,
    game: RepositoryState<GameProfileUiModel>,
    projects: RepositoryState<List<ProjectCardModel>>,
    sessionResolved: Boolean,
    onRetry: () -> Unit,
    onOpenPersonal: () -> Unit,
    onChat: () -> Unit,
    onPractice: () -> Unit,
    onProjects: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home-screen")) {
        val model = home.value
        if (!sessionResolved || model == null) {
            HomeScreen(home, sessionResolved, onRetry, onOpenPersonal, onChat, onPractice, onProjects)
            return@BoxWithConstraints
        }
        val compact = maxHeight < 700.dp || maxWidth < 355.dp
        val expanded = maxWidth >= 760.dp
        val profile = game.value
        val avatarId = profile?.avatarId?.takeIf { it.isNotBlank() } ?: model.avatarId
        val asset = profile?.avatarAssetKey.orEmpty()
        val tier = profile?.avatarTier ?: "CORE"
        if (expanded) {
            Row(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeIdentityBand(model, avatarId, asset, tier, onOpenPersonal)
                    NowWorld(model, Modifier.weight(1f), onChat)
                    HomeActions(onChat, onPractice, onProjects)
                }
                ContinuityRail(model, projects, Modifier.weight(.42f).fillMaxHeight(), onProjects)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)) {
                if (home.freshness != DataFreshness.FRESH) CoreFreshness(home.freshness, onRetry)
                HomeIdentityBand(model, avatarId, asset, tier, onOpenPersonal)
                NowWorld(model, Modifier.weight(1f).fillMaxWidth(), onChat)
                HomeActions(onChat, onPractice, onProjects)
                if (!compact) ContinuityStrip(model, projects, onProjects)
            }
        }
    }
}

@Composable
private fun HomeIdentityBand(model: HomeFinalModel, avatarId: String, asset: String, tier: String, onOpenPersonal: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        LivingVeltrixAvatar(avatarId, asset, tier, Modifier.size(72.dp).testTag("living-avatar-home"), equipped = true, onClick = onOpenPersonal)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(model.displayName.ifBlank { "Veltrix learner" }, style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Level ${model.level} · ${model.lifetimeXp.corePretty()} XP", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.bodyMedium)
            XpThread(model)
        }
        GlassSurface(Modifier.heightIn(min = 54.dp), 20.dp, strong = true) { Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) { Text(model.coins.corePretty(), color = VeltrixColors.Ink, fontWeight = FontWeight.Bold); Text("coins", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun XpThread(model: HomeFinalModel) {
    val d = model.nextLevelXp.coerceAtLeast(1); val p = (model.currentLevelXp.toFloat() / d.toFloat()).coerceIn(0f,1f)
    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0xC9D7E1F1)).semantics { progressBarRangeInfo = ProgressBarRangeInfo(p,0f..1f); contentDescription = "Level ${model.level} progress" }) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Brush.horizontalGradient(listOf(VeltrixColors.Sky,Color(0xFF7868E9),VeltrixColors.Mint))))
    }
}

@Composable
private fun NowWorld(model: HomeFinalModel, modifier: Modifier, onChat: () -> Unit) {
    val focus = model.currentFocus?.takeIf { it.isNotBlank() }
    Box(
        modifier.clip(RoundedCornerShape(42.dp)).drawWithCache {
            val base = Brush.linearGradient(listOf(Color(0xFFDCE8FF),Color(0xFFF7FAFF),Color(0xFFDCF7EF)),Offset.Zero,Offset(size.width,size.height))
            val intelligence = Brush.radialGradient(listOf(Color(0x664B7DFF),Color.Transparent),Offset(size.width*.82f,size.height*.15f),size.minDimension*.82f)
            val growth = Brush.radialGradient(listOf(Color(0x4855D8B4),Color.Transparent),Offset(size.width*.10f,size.height*.94f),size.minDimension*.68f)
            onDrawBehind {
                val r=androidx.compose.ui.geometry.CornerRadius(42.dp.toPx());drawRoundRect(base,cornerRadius=r);drawRoundRect(intelligence,cornerRadius=r);drawRoundRect(growth,cornerRadius=r)
                val path=Path().apply{moveTo(size.width*.07f,size.height*.73f);cubicTo(size.width*.27f,size.height*.45f,size.width*.46f,size.height*.78f,size.width*.68f,size.height*.46f);cubicTo(size.width*.81f,size.height*.29f,size.width*.89f,size.height*.34f,size.width*.95f,size.height*.23f)}
                drawPath(path,Color.White.copy(alpha=.56f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("NOW", style = MaterialTheme.typography.labelSmall, color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold)
                Text(focus ?: "Build the next useful step", style = MaterialTheme.typography.headlineSmall, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(if (focus == null) "No current focus is confirmed yet. Veltrix keeps the space intentional instead of inventing a recommendation." else "Continue the learning context already confirmed for your account.", color = VeltrixColors.InkMuted)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(model.memoryMaturity.ifBlank { "Memory building" }.coreTitle(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                    Text("learning memory", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                }
                PressableGlass(onChat, Modifier.heightIn(min=54.dp).testTag("home-primary-action"), 22.dp, strong = true) { Text("Ask Veltrix →", Modifier.padding(horizontal=16.dp,vertical=13.dp), color=VeltrixColors.Ink, fontWeight=FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun HomeActions(onChat:()->Unit,onPractice:()->Unit,onProjects:()->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Ask" to onChat,"Practice" to onPractice,"Projects" to onProjects).forEach { (label,action) -> PressableGlass(action,Modifier.weight(1f).heightIn(min=56.dp),20.dp) { Box(Modifier.fillMaxSize().padding(10.dp),contentAlignment=Alignment.Center){Text(label,color=VeltrixColors.Ink,fontWeight=FontWeight.Medium)} } }
    }
}

@Composable
private fun ContinuityStrip(model:HomeFinalModel,projects:RepositoryState<List<ProjectCardModel>>,onProjects:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
        SignalPill("Map",model.mapState.ifBlank{"Unknown"});SignalPill("Consistency",model.consistency.toString());SignalPill("Projects",projects.value?.size?.toString()?:"—")
        Spacer(Modifier.weight(1f));Text("Continue →",Modifier.padding(8.dp),color=VeltrixColors.SkyDeep,fontWeight=FontWeight.SemiBold)
    }
}

@Composable private fun SignalPill(label:String,value:String){Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.48f)).padding(horizontal=11.dp,vertical=7.dp)){Text(value.coreTitle(),color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium);Text(label,color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)}}

@Composable
private fun ContinuityRail(model:HomeFinalModel,projects:RepositoryState<List<ProjectCardModel>>,modifier:Modifier,onProjects:()->Unit) {
    WorldRailSurface(modifier){
        Text("Living context",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        Text("Signals stay subordinate to the next useful action.",color=VeltrixColors.InkMuted)
        RailSignal("Personal Map",model.mapState.ifBlank{"Unknown"},VeltrixColors.Mint)
        RailSignal("Memory",model.memoryMaturity.ifBlank{"Building"},VeltrixColors.Sky)
        RailSignal("Consistency",model.consistency.toString(),Color(0xFF7868E9))
        Spacer(Modifier.height(4.dp))
        Text("Recent project continuity",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        projects.value.orEmpty().take(4).forEach{p->Text("• ${p.title}",color=VeltrixColors.InkMuted,maxLines=1,overflow=TextOverflow.Ellipsis)}
        if(projects.value.orEmpty().isEmpty())Text("No confirmed projects yet.",color=VeltrixColors.InkMuted)
        PressableGlass(onProjects,Modifier.fillMaxWidth().heightIn(min=52.dp),20.dp){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Open Projects",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)}}
    }
}

@Composable private fun WorldRailSurface(modifier:Modifier,content:@Composable ColumnScope.()->Unit){Box(modifier.clip(RoundedCornerShape(38.dp)).background(Color.White.copy(alpha=.47f))){Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp),content=content)}}
@Composable private fun RailSignal(label:String,value:String,accent:Color){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).clip(CircleShape).background(accent));Spacer(Modifier.width(9.dp));Text(label,Modifier.weight(1f),color=VeltrixColors.InkMuted);Text(value.coreTitle(),color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)}}

@Composable
fun Part2PersonalScreen(
    personal:RepositoryState<PersonalFinalModel>,
    game:RepositoryState<GameProfileUiModel>,
    map:RepositoryState<PersonalMapUiModel>,
    sessionResolved:Boolean,
    onRetry:()->Unit,
    onUnlockMap:()->Unit,
    onStartUnit:(String,Long)->Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("personal-screen")) {
        val model=personal.value
        if(!sessionResolved||model==null){PersonalScreen(personal,sessionResolved,onRetry);return@BoxWithConstraints}
        val profile=game.value;val avatarId=profile?.avatarId?.takeIf{it.isNotBlank()}?:model.avatarId;val expanded=maxWidth>=780.dp
        if(expanded){
            Row(Modifier.fillMaxSize().padding(22.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
                LazyColumn(Modifier.weight(.43f),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=28.dp)){item{PersonalIdentityHero(model,avatarId,profile)};item{LearnerIntelligence(model)};item{GrowthNarrative(model)}}
                LazyColumn(Modifier.weight(.57f),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=28.dp)){item{PersonalMapExplorer(map,onRetry,onUnlockMap,onStartUnit)};item{PersonalTrajectory(model)}}
            }
        }else{
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp).testTag("personal-scroll"),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(top=8.dp,bottom=30.dp)){
                if(personal.freshness!=DataFreshness.FRESH)item{CoreFreshness(personal.freshness,onRetry)}
                item{PersonalIdentityHero(model,avatarId,profile)}
                item{LearnerIntelligence(model)}
                item{PersonalMapExplorer(map,onRetry,onUnlockMap,onStartUnit)}
                item{GrowthNarrative(model)}
                item{PersonalTrajectory(model)}
            }
        }
    }
}

@Composable
private fun PersonalIdentityHero(model:PersonalFinalModel,avatarId:String,profile:GameProfileUiModel?) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(38.dp)).background(Brush.linearGradient(listOf(Color(0xFFDCE8FF),Color(0xFFF4F7FF),Color(0xFFDCF7EF))))){
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            LivingVeltrixAvatar(avatarId,profile?.avatarAssetKey.orEmpty(),profile?.avatarTier?:"CORE",Modifier.size(96.dp).testTag("living-avatar-personal"),equipped=true)
            Column(Modifier.weight(1f)){Text("YOUR LEARNING IDENTITY",style=MaterialTheme.typography.labelSmall,color=VeltrixColors.SkyDeep,fontWeight=FontWeight.Bold);Text(model.displayName.ifBlank{"Veltrix learner"},style=MaterialTheme.typography.headlineSmall,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("Level ${model.level} · ${model.lifetimeXp.corePretty()} lifetime XP",color=VeltrixColors.InkMuted);Text(model.memoryMaturity.ifBlank{"Memory building"}.coreTitle(),color=VeltrixColors.Ink,fontWeight=FontWeight.Medium)}
        }
    }
}

@Composable
private fun LearnerIntelligence(model:PersonalFinalModel){
    WorldRailSurface(Modifier.fillMaxWidth().heightIn(min=180.dp)){
        Text("Learner intelligence",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        Text("Signals are descriptive, not labels. They evolve with confirmed learning evidence.",color=VeltrixColors.InkMuted)
        NarrativeSignal("Strengths",model.strengths,VeltrixColors.Mint)
        NarrativeSignal("Growth edges",model.weaknesses,Color(0xFF7868E9))
        NarrativeSignal("Interests",model.interests,VeltrixColors.Sky)
    }
}

@Composable private fun NarrativeSignal(label:String,values:List<String>,accent:Color){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){Box(Modifier.padding(top=6.dp).size(7.dp).clip(CircleShape).background(accent));Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(label,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text(if(values.isEmpty())"Not enough confirmed evidence yet" else values.take(4).joinToString(" · "),color=VeltrixColors.InkMuted)}}}

@Composable
private fun GrowthNarrative(model:PersonalFinalModel){
    WorldRailSurface(Modifier.fillMaxWidth().heightIn(min=160.dp)){
        Text("Growth & ownership",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth()){IdentityMetric("Achievements",model.achievementCount.toString(),Modifier.weight(1f));IdentityMetric("Owned",model.inventoryCount.toString(),Modifier.weight(1f));IdentityMetric("Coins",model.coins.corePretty(),Modifier.weight(1f))}
        Text(if(model.achievementCount==0)"Meaningful milestones will appear only after backend-confirmed achievement events." else "${model.achievementCount} backend-confirmed milestone${if(model.achievementCount==1)"" else "s"} now shape your identity.",color=VeltrixColors.InkMuted)
    }
}

@Composable private fun IdentityMetric(label:String,value:String,modifier:Modifier){Column(modifier){Text(value,style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.Bold);Text(label,color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)}}

@Composable
private fun PersonalTrajectory(model:PersonalFinalModel){
    WorldRailSurface(Modifier.fillMaxWidth().heightIn(min=150.dp)){
        Text("Becoming",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        Text("Goals and consistency describe direction; they do not punish missed days.",color=VeltrixColors.InkMuted)
        NarrativeSignal("Goals",model.goals,VeltrixColors.Mint)
        RailSignal("Consistency",model.currentConsistency.toString(),VeltrixColors.Sky)
        model.seasonId?.let{RailSignal("Season",it,Color(0xFF7868E9))}
    }
}

@Composable
private fun CoreFreshness(freshness:DataFreshness,onRetry:()->Unit){GlassSurface(Modifier.fillMaxWidth(),18.dp,strong=true){Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(if(freshness==DataFreshness.OFFLINE)"Offline · saved learning state" else "Saved state · refresh unavailable",Modifier.weight(1f),color=VeltrixColors.InkMuted);PressableGlass(onRetry,Modifier.heightIn(min=44.dp),16.dp){Text("Retry",Modifier.padding(horizontal=12.dp,vertical=9.dp),color=VeltrixColors.Ink)}}}}
