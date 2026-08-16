package com.veltrix.hom.vnext

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

private fun String.p3Title():String = lowercase(Locale.US).replace('_',' ').split(' ').joinToString(" "){it.replaceFirstChar { c->c.uppercaseChar().toString() }}

@Composable
private fun ToolHeading(eyebrow:String,title:String,detail:String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(eyebrow.uppercase(Locale.US),style=MaterialTheme.typography.labelSmall,color=VeltrixColors.SkyDeep,fontWeight=FontWeight.Bold)
        Text(title,style=MaterialTheme.typography.headlineMedium,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,modifier=Modifier.semantics{heading()})
        Text(detail,color=VeltrixColors.InkMuted,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToolPanel(modifier:Modifier=Modifier,accent:Color=VeltrixColors.Sky,content:@Composable ColumnScope.()->Unit) {
    Box(modifier.clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xEEFFFFFF),accent.copy(alpha=.08f),Color(0xEAF6F9FF))))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
private fun ToolError(code:String?,retry:(()->Unit)?=null) {
    val copy=when(code){
        "HTTP_401","AUTH_EXPIRED"->"Your session expired. Sign in again to continue."
        "HTTP_409","CONFLICT","REVISION_CONFLICT"->"This changed on another device. Refresh before saving."
        "HTTP_429","RATE_LIMITED"->"Too many requests. Try again shortly."
        "HTTP_503","SERVICE_UNAVAILABLE"->"This service is temporarily unavailable."
        "OFFLINE","NO_SESSION"->"No live connection is available right now."
        null->"This content is unavailable right now."
        else->"Veltrix could not complete this action ($code)."
    }
    ToolPanel(accent=VeltrixColors.Error){Text(copy,color=VeltrixColors.Error);retry?.let{TextButton(onClick=it){Text("Retry")}}}
}

@Composable
fun CalculatorWorldScreen(
    state:RepositoryState<CalculatorResultUiModel>,
    history:List<CalculatorResultUiModel>,
    onCalculate:(String)->Unit,
) {
    var expression by rememberSaveable{ mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("calculator-screen")) {
        val expanded=maxWidth>=760.dp
        val editor:@Composable (Modifier)->Unit={m->
            Column(m.padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                ToolHeading("Deterministic tool","Calculator","Immediate expression evaluation through the accepted server-side calculator engine.")
                ToolPanel(accent=VeltrixColors.Mint) {
                    OutlinedTextField(expression,{expression=it},Modifier.fillMaxWidth().testTag("calculator-input"),label={Text("Expression")},placeholder={Text("(18 + 6) / 3")},singleLine=false,minLines=2,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={if(expression.isNotBlank())onCalculate(expression)}))
                    PressableGlass({onCalculate(expression)},Modifier.fillMaxWidth().heightIn(min=54.dp),20.dp,strong=true,enabled=expression.isNotBlank()) { Box(Modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.Center){Text("Calculate",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)} }
                }
                state.value?.let { result->
                    ToolPanel(accent=VeltrixColors.Sky) {
                        Text("RESULT",style=MaterialTheme.typography.labelSmall,color=VeltrixColors.SkyDeep,fontWeight=FontWeight.Bold)
                        Text(result.result.ifBlank{"—"},style=MaterialTheme.typography.displaySmall,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,fontFamily=FontFamily.Monospace,modifier=Modifier.testTag("calculator-result"))
                        Text(if(result.deterministic)"Deterministic backend result" else "Backend result",color=VeltrixColors.InkMuted)
                    }
                }
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.errorCode?.let{ToolError(it){if(expression.isNotBlank())onCalculate(expression)}}
            }
        }
        val historyPane:@Composable (Modifier)->Unit={m->
            LazyColumn(m.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=30.dp)) {
                item{Text("Recent results",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)}
                if(history.isEmpty()) item{Text("Results from this session appear here. Server history remains authoritative where provided.",color=VeltrixColors.InkMuted)}
                items(history.asReversed(),key={it.expression+it.result}){r->PressableGlass({expression=r.expression},Modifier.fillMaxWidth(),20.dp){Column(Modifier.padding(14.dp)){Text(r.expression,color=VeltrixColors.InkMuted,fontFamily=FontFamily.Monospace,maxLines=2,overflow=TextOverflow.Ellipsis);Text(r.result,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,fontFamily=FontFamily.Monospace)}}}
            }
        }
        if(expanded)Row(Modifier.fillMaxSize()){editor(Modifier.weight(.58f).fillMaxHeight());VerticalDivider();historyPane(Modifier.weight(.42f).fillMaxHeight())} else LazyColumn(Modifier.fillMaxSize()){item{editor(Modifier.fillMaxWidth())};item{historyPane(Modifier.fillMaxWidth().height(300.dp))}}
    }
}

@Composable
fun TranslateWorldScreen(
    state:RepositoryState<TranslationUiModel>,
    projectId:String?,
    onTranslate:(String,String,String?,String?)->Unit,
) {
    var input by rememberSaveable{mutableStateOf("")};var source by rememberSaveable{mutableStateOf("auto")};var target by rememberSaveable{mutableStateOf("en")}
    val clipboard=LocalClipboardManager.current
    val languages=listOf("auto" to "Auto detect","en" to "English","uz" to "Uzbek","ru" to "Russian")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp).imePadding().testTag("translate-screen"),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(top=10.dp,bottom=32.dp)) {
        item{ToolHeading("Language tool","Translate",if(projectId==null)"Fast translation with provider status shown honestly." else "Translation stays attached to the active project context when submitted.")}
        item{ToolPanel(accent=VeltrixColors.Mint){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                LanguagePicker("From",source,languages,Modifier.weight(1f)){source=it}
                PressableGlass({val old=source;if(source!="auto"){source=target;target=old}},Modifier.size(50.dp),18.dp,enabled=source!="auto"){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("⇄",color=VeltrixColors.Ink,fontWeight=FontWeight.Bold)}}
                LanguagePicker("To",target,languages.filter{it.first!="auto"},Modifier.weight(1f)){target=it}
            }
            OutlinedTextField(input,{input=it},Modifier.fillMaxWidth().testTag("translate-input"),label={Text("Text")},minLines=5,maxLines=10,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={if(input.isNotBlank())onTranslate(input,target,source.takeUnless{it=="auto"},projectId)}))
            PressableGlass({onTranslate(input,target,source.takeUnless{it=="auto"},projectId)},Modifier.fillMaxWidth().heightIn(min=54.dp),20.dp,strong=true,enabled=input.isNotBlank()){Box(Modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.Center){Text("Translate",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)}}
        }}
        if(state.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.errorCode?.let{item{ToolError(it){if(input.isNotBlank())onTranslate(input,target,source.takeUnless{s->s=="auto"},projectId)}}}
        state.value?.let{r->item{ToolPanel(accent=VeltrixColors.Sky){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${(r.sourceLanguage?:"detected").p3Title()} → ${r.targetLanguage.p3Title()}",color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelMedium);Text(r.translatedText,style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,modifier=Modifier.testTag("translate-result"))};TextButton(onClick={clipboard.setText(AnnotatedString(r.translatedText))}){Text("Copy")}}
            Text(if(r.live)"Live provider · ${r.provider}" else "Deterministic/test provider · ${r.provider}",color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)
        }}}
    }
}

@Composable
private fun LanguagePicker(label:String,value:String,items:List<Pair<String,String>>,modifier:Modifier,onSelect:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box(modifier){OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Column(horizontalAlignment=Alignment.Start){Text(label,style=MaterialTheme.typography.labelSmall);Text(items.firstOrNull{it.first==value}?.second?:value,maxLines=1)}};DropdownMenu(open,{open=false}){items.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);open=false})}}}
}

@Composable
fun NotificationsWorldScreen(
    intents: RepositoryState<List<NotificationIntentUiModel>>,
    preferences: RepositoryState<List<NotificationPreferenceUiModel>>,
    onRefresh: () -> Unit,
    onToggle: (NotificationPreferenceUiModel, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val permissionGranted = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val osEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("notifications-screen")) {
        val expanded = maxWidth >= 780.dp
        val inbox: @Composable (Modifier) -> Unit = { m ->
            LazyColumn(m.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { ToolHeading("Attention center", "Notifications", "Only backend-created notification intents are shown. No fabricated reminders.") }
                item {
                    ToolPanel(accent = if (permissionGranted && osEnabled) VeltrixColors.Mint else VeltrixColors.Amber) {
                        Text(if (permissionGranted && osEnabled) "Android notifications are available" else "Android notifications are disabled or permission is missing", color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                        Text("Veltrix keeps in-app state visible even when OS delivery is unavailable.", color = VeltrixColors.InkMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRefresh) { Text("Refresh") }
                            if (!permissionGranted || !osEnabled) {
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            data = Uri.parse("package:${context.packageName}")
                                        })
                                    }
                                }) { Text("Android settings") }
                            }
                        }
                    }
                }
                intents.errorCode?.let { item { ToolError(it, onRefresh) } }
                if (intents.value.orEmpty().isEmpty() && !intents.loading) item { Text("No notification intents yet.", color = VeltrixColors.InkMuted) }
                items(intents.value.orEmpty(), key = { it.id }) { n ->
                    ToolPanel(accent = if (n.status.contains("PEND", true)) VeltrixColors.Sky else VeltrixColors.Mint) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.category.p3Title(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                                Text(n.payload.take(180), color = VeltrixColors.InkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            Text(n.status.p3Title(), style = MaterialTheme.typography.labelSmall, color = VeltrixColors.SkyDeep)
                        }
                    }
                }
            }
        }
        val prefs: @Composable (Modifier) -> Unit = { m ->
            LazyColumn(m.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text("Delivery preferences", style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                    Text("Backend preference truth remains separate from Android permission state.", color = VeltrixColors.InkMuted)
                }
                preferences.errorCode?.let { item { ToolError(it, onRefresh) } }
                items(preferences.value.orEmpty(), key = { it.category }) { p ->
                    ToolPanel(accent = if (p.enabled) VeltrixColors.Mint else Color(0xFF9AA6B8)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.category.p3Title(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                                Text(p.timezone, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = p.enabled, onCheckedChange = { onToggle(p, it) })
                        }
                    }
                }
                if (preferences.value.orEmpty().isEmpty() && !preferences.loading) item { Text("No server notification preferences have been configured yet.", color = VeltrixColors.InkMuted) }
            }
        }
        if (expanded) Row(Modifier.fillMaxSize()) { inbox(Modifier.weight(.58f)); VerticalDivider(); prefs(Modifier.weight(.42f)) }
        else LazyColumn(Modifier.fillMaxSize()) { item { inbox(Modifier.fillMaxWidth().height(520.dp)) }; item { prefs(Modifier.fillMaxWidth().height(320.dp)) } }
    }
}

@Composable
fun SettingsWorldScreen(
    profile:RepositoryState<ProfileUiModel>,
    settings:RepositoryState<List<SettingUiModel>>,
    exportState:RepositoryState<AccountExportUiModel>,
    feedback:MutationFeedback?,
    onRefresh:()->Unit,
    onSaveProfile:(ProfileUiModel,String,String,String,Boolean)->Unit,
    onSaveSetting:(String,String,String)->Unit,
    onExport:()->Unit,
    onDelete:(String)->Unit,
    initialSection:String="Account",
) {
    var section by rememberSaveable(initialSection){mutableStateOf(initialSection)}
    val sections=listOf("Account","Appearance & accessibility","Memory & personalization","Notifications","Data & privacy","About")
    BoxWithConstraints(Modifier.fillMaxSize().testTag("settings-screen")) {
        val expanded=maxWidth>=820.dp
        val nav:@Composable (Modifier)->Unit={m->Column(m.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Settings",style=MaterialTheme.typography.headlineSmall,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);sections.forEach{s->PressableGlass({section=s},Modifier.fillMaxWidth().heightIn(min=48.dp),18.dp,strong=section==s){Box(Modifier.fillMaxSize().padding(horizontal=12.dp),contentAlignment=Alignment.CenterStart){Text(s,color=VeltrixColors.Ink,fontWeight=if(section==s)FontWeight.SemiBold else FontWeight.Normal)}}}}}
        val detail:@Composable (Modifier)->Unit={m->LazyColumn(m.padding(18.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=38.dp)){
            item{ToolHeading("Control center",section,"Changes are explicit, backend-confirmed where persistent, and never hidden behind dark patterns.")}
            feedback?.takeIf{!it.success}?.let{item{ToolError(it.code)}}
            when(section){
                "Account"->item{AccountSettingsPanel(profile,onRefresh,onSaveProfile)}
                "Appearance & accessibility"->item{AccessibilitySettingsPanel(settings,onSaveSetting)}
                "Memory & personalization"->item{MemorySettingsPanel(profile,settings,onSaveProfile,onSaveSetting)}
                "Notifications"->item{ToolPanel(accent=VeltrixColors.Sky){Text("Notification controls",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("Open Notifications from the sidebar for server category preferences and Android delivery status.",color=VeltrixColors.InkMuted)}}
                "Data & privacy"->item{DataControlsPanel(exportState,onExport,onDelete)}
                else->item{ToolPanel(accent=VeltrixColors.Mint){Text("Veltrix Hom vNext",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("Native Android · Kotlin + Jetpack Compose",color=VeltrixColors.InkMuted);Text("Frontend presentation never overrides progression, economy, scoring, permissions or server revision truth.",color=VeltrixColors.InkMuted)}}
            }
        }}
        if(expanded)Row(Modifier.fillMaxSize()){nav(Modifier.width(280.dp).fillMaxHeight());VerticalDivider();detail(Modifier.weight(1f).fillMaxHeight())} else Column(Modifier.fillMaxSize()){LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)){items(sections,key={it}){s->AssistChip(onClick={section=s},label={Text(s)},modifier=Modifier)}};detail(Modifier.weight(1f))}
    }
}

@Composable
private fun AccountSettingsPanel(profile:RepositoryState<ProfileUiModel>,onRefresh:()->Unit,onSave:(ProfileUiModel,String,String,String,Boolean)->Unit) {
    val p=profile.value
    if(p==null){if(profile.loading)LinearProgressIndicator(Modifier.fillMaxWidth()) else ToolError(profile.errorCode,onRefresh);return}
    var name by rememberSaveable(p.revision){mutableStateOf(p.displayName)};var language by rememberSaveable(p.revision){mutableStateOf(p.preferredLanguage)};var timezone by rememberSaveable(p.revision){mutableStateOf(p.timezone)}
    ToolPanel(accent=VeltrixColors.Sky){
        Text("Account profile",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        p.username?.let{Text("@${it}",color=VeltrixColors.InkMuted)}
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Display name")},singleLine=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(language,{language=it},Modifier.weight(1f),label={Text("Language")},singleLine=true);OutlinedTextField(timezone,{timezone=it},Modifier.weight(1f),label={Text("Timezone")},singleLine=true)}
        Button(onClick={onSave(p,name,language,timezone,p.memoryEnabled)},enabled=name.isNotBlank()&&language.isNotBlank()&&timezone.isNotBlank()){Text("Save profile")}
        Text("Revision ${p.revision} · conflicts are never overwritten silently.",color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AccessibilitySettingsPanel(settings:RepositoryState<List<SettingUiModel>>,onSaveSetting:(String,String,String)->Unit) {
    val reduced=settings.value.orEmpty().firstOrNull{it.category=="ACCESSIBILITY"&&it.key=="reduced_motion_preference"}?.jsonValue?.contains("true",true)==true
    val high=settings.value.orEmpty().firstOrNull{it.category=="ACCESSIBILITY"&&it.key=="high_contrast_preference"}?.jsonValue?.contains("true",true)==true
    ToolPanel(accent=VeltrixColors.Mint){Text("Accessibility preferences",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("System accessibility settings still take priority. These account preferences are only stored when supported.",color=VeltrixColors.InkMuted);SettingSwitch("Prefer reduced motion",reduced){onSaveSetting("ACCESSIBILITY","reduced_motion_preference",it.toString())};SettingSwitch("Prefer higher contrast",high){onSaveSetting("ACCESSIBILITY","high_contrast_preference",it.toString())}}
}

@Composable
private fun MemorySettingsPanel(profile:RepositoryState<ProfileUiModel>,settings:RepositoryState<List<SettingUiModel>>,onSaveProfile:(ProfileUiModel,String,String,String,Boolean)->Unit,onSaveSetting:(String,String,String)->Unit) {
    val p=profile.value
    ToolPanel(accent=Color(0xFF7868E9)){Text("Memory & personalization",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("Confirmed memory and probabilistic learning signals are not presented as the same thing.",color=VeltrixColors.InkMuted);if(p!=null)SettingSwitch("Memory enabled",p.memoryEnabled){enabled->onSaveProfile(p,p.displayName,p.preferredLanguage,p.timezone,enabled)};val value=settings.value.orEmpty().firstOrNull{it.category=="MEMORY"&&it.key=="personalization_enabled"}?.jsonValue?.contains("true",true)!=false;SettingSwitch("Personalization suggestions",value){onSaveSetting("MEMORY","personalization_enabled",it.toString())}}
}

@Composable
private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().semantics{role=Role.Switch},verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),color=VeltrixColors.Ink);Switch(checked=checked,onCheckedChange=onChange)}}

@Composable
private fun DataControlsPanel(exportState:RepositoryState<AccountExportUiModel>,onExport:()->Unit,onDelete:(String)->Unit) {
    var deleteOpen by rememberSaveable{mutableStateOf(false)}
    ToolPanel(accent=VeltrixColors.Amber){Text("Your data",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("Export summarizes backend-owned account data. Deletion requires password re-authentication and explicit confirmation.",color=VeltrixColors.InkMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onExport){Text("Prepare export")};OutlinedButton(onClick={deleteOpen=true}){Text("Delete account")}}
        exportState.value?.let{e->Text("Export snapshot · ${e.generatedAt}",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text("${e.entityCounts.values.sum()} account-owned records across ${e.entityCounts.size} domains",color=VeltrixColors.InkMuted)}
        exportState.errorCode?.let{ToolError(it,onExport)}
    }
    if(deleteOpen){var password by rememberSaveable{mutableStateOf("")};var typed by rememberSaveable{mutableStateOf("")};AlertDialog(onDismissRequest={deleteOpen=false},title={Text("Delete Veltrix account?")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("This revokes sessions and schedules backend account purge. It cannot be represented as completed until the server confirms it.");OutlinedTextField(typed,{typed=it},Modifier.fillMaxWidth(),label={Text("Type DELETE")},singleLine=true);OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation())}},confirmButton={Button(enabled=typed=="DELETE"&&password.length>=12,onClick={onDelete(password)}){Text("Delete account")}},dismissButton={TextButton(onClick={deleteOpen=false}){Text("Cancel")}})}
}
