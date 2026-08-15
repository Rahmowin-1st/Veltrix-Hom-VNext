package com.veltrix.hom.vnext

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veltrix.hom.vnext.core.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEV_ACCOUNT="dev-account-local"

class AppViewModel(app:Application):AndroidViewModel(app){
    private val localDb=VeltrixLocalDatabase.get(app)
    private val sessionStore=SessionStore(app)
    private val part3=Part3AndroidRepository(app)
    private val part2=Part2FeatureRepository(app)

    val projects:StateFlow<List<LocalProjectEntity>> = localDb.projects().observe(DEV_ACCOUNT).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _session=MutableStateFlow<LocalSession?>(null);val session:StateFlow<LocalSession?> = _session.asStateFlow()
    private val _sessionResolved=MutableStateFlow(false);val sessionResolved:StateFlow<Boolean> = _sessionResolved.asStateFlow()
    private val _home=MutableStateFlow(RepositoryState<HomeFinalModel>(null,DataFreshness.OFFLINE,loading=true));val home=_home.asStateFlow()
    private val _personal=MutableStateFlow(RepositoryState<PersonalFinalModel>(null,DataFreshness.OFFLINE,loading=true));val personal=_personal.asStateFlow()
    private val _remoteProjects=MutableStateFlow(RepositoryState<List<ProjectCardModel>>(null,DataFreshness.OFFLINE,loading=true));val remoteProjects=_remoteProjects.asStateFlow()
    private val _workspace=MutableStateFlow(RepositoryState<ProjectWorkspaceUiModel>(null,DataFreshness.OFFLINE));val workspace=_workspace.asStateFlow()
    private val _chats=MutableStateFlow(RepositoryState<List<ConversationUiModel>>(null,DataFreshness.OFFLINE,loading=true));val chats=_chats.asStateFlow()
    private val _messages=MutableStateFlow(RepositoryState<List<ChatMessageUiModel>>(emptyList(),DataFreshness.OFFLINE));val messages=_messages.asStateFlow()
    private val _sources=MutableStateFlow(RepositoryState<List<SourceUiModel>>(null,DataFreshness.OFFLINE,loading=true));val sources=_sources.asStateFlow()
    private val _flashcards=MutableStateFlow(RepositoryState<List<FlashcardUiModel>>(null,DataFreshness.OFFLINE));val flashcards=_flashcards.asStateFlow()
    private val _mistakes=MutableStateFlow(RepositoryState<List<MistakeUiModel>>(null,DataFreshness.OFFLINE));val mistakes=_mistakes.asStateFlow()
    private val _store=MutableStateFlow(RepositoryState<StoreCatalogUiModel>(null,DataFreshness.OFFLINE));val store=_store.asStateFlow()
    private val _inventory=MutableStateFlow(RepositoryState<List<InventoryItemUiModel>>(null,DataFreshness.OFFLINE));val inventory=_inventory.asStateFlow()
    private val _avatars=MutableStateFlow(RepositoryState<List<AvatarCatalogUiModel>>(null,DataFreshness.OFFLINE));val avatars=_avatars.asStateFlow()
    private val _map=MutableStateFlow(RepositoryState<PersonalMapUiModel>(null,DataFreshness.OFFLINE));val map=_map.asStateFlow()
    private val _gameProfile=MutableStateFlow(RepositoryState<GameProfileUiModel>(null,DataFreshness.OFFLINE));val gameProfile=_gameProfile.asStateFlow()
    private val _history=MutableStateFlow(RepositoryState<List<ActivityUiModel>>(null,DataFreshness.OFFLINE));val history=_history.asStateFlow()
    private val _search=MutableStateFlow(RepositoryState<List<SearchUiModel>>(emptyList(),DataFreshness.FRESH));val search=_search.asStateFlow()
    private val _assessment=MutableStateFlow(RepositoryState<AssessmentDetailUiModel>(null,DataFreshness.OFFLINE));val assessment=_assessment.asStateFlow()
    private val _attempt=MutableStateFlow<AttemptUiModel?>(null);val attempt=_attempt.asStateFlow()
    private val _assessmentResult=MutableStateFlow<AssessmentResultUiModel?>(null);val assessmentResult=_assessmentResult.asStateFlow()
    private val _practice=MutableStateFlow(RepositoryState<PracticeSessionUiModel>(null,DataFreshness.OFFLINE));val practice=_practice.asStateFlow()
    private val _practiceHint=MutableStateFlow<String?>(null);val practiceHint=_practiceHint.asStateFlow()
    private val _practiceCheck=MutableStateFlow<PracticeCheckUiModel?>(null);val practiceCheck=_practiceCheck.asStateFlow()
    private val _practiceComplete=MutableStateFlow<PracticeCompleteUiModel?>(null);val practiceComplete=_practiceComplete.asStateFlow()
    private val _streamingText=MutableStateFlow("");val streamingText=_streamingText.asStateFlow()
    private val _streaming=MutableStateFlow(false);val streaming=_streaming.asStateFlow()
    private val _streamError=MutableStateFlow<String?>(null);val streamError=_streamError.asStateFlow()
    private val _selectedSources=MutableStateFlow<Set<String>>(emptySet());val selectedSources=_selectedSources.asStateFlow()
    private val _citations=MutableStateFlow<Map<String,List<CitationUiModel>>>(emptyMap());val citations=_citations.asStateFlow()
    private val _mutationFeedback=MutableStateFlow<MutationFeedback?>(null);val mutationFeedback=_mutationFeedback.asStateFlow()
    private val _createdConversationId=MutableStateFlow<String?>(null);val createdConversationId=_createdConversationId.asStateFlow()
    private val _openedPracticeId=MutableStateFlow<String?>(null);val openedPracticeId=_openedPracticeId.asStateFlow()

    init{viewModelScope.launch{_session.value=sessionStore.read();_sessionResolved.value=true;refreshPart1(false);refreshCorePart2(false)}}

    fun refreshHome()=viewModelScope.launch{loadHome(true)}
    fun refreshPersonal()=viewModelScope.launch{loadPersonal(true);loadMap(true);loadGame(true)}
    fun refreshAll(force:Boolean=true)=viewModelScope.launch{refreshPart1(force);refreshCorePart2(force)}
    private suspend fun refreshPart1(force:Boolean){loadHome(force);loadPersonal(force)}
    private suspend fun refreshCorePart2(force:Boolean){loadProjects(force);loadChats(null,force);loadSources(force);loadStore(force);loadInventory(force);loadAvatars(force);loadMap(force);loadGame(force);loadHistory(force)}
    private fun apiSession():ApiSession?=_session.value?.let{ApiSession(it.accountId,it.accessToken)}
    private suspend fun loadHome(force:Boolean){val s=apiSession()?:run{_home.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};val old=_home.value.value;_home.value=_home.value.copy(loading=old==null,errorCode=null);_home.value=part3.home(s,force)}
    private suspend fun loadPersonal(force:Boolean){val s=apiSession()?:run{_personal.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};val old=_personal.value.value;_personal.value=_personal.value.copy(loading=old==null,errorCode=null);_personal.value=part3.personal(s,force)}

    fun refreshProjects()=viewModelScope.launch{loadProjects(true)}
    private suspend fun loadProjects(force:Boolean){val s=apiSession()?:run{_remoteProjects.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_remoteProjects.value=_remoteProjects.value.copy(loading=_remoteProjects.value.value==null,errorCode=null);_remoteProjects.value=part2.projects(s,force)}
    fun openProject(id:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_workspace.value=RepositoryState(_workspace.value.value?.takeIf{it.project.id==id},_workspace.value.freshness,loading=true);_workspace.value=part2.workspace(s,id,true)}
    fun clearWorkspace(){_workspace.value=RepositoryState(null,DataFreshness.OFFLINE)}
    fun createProject(title:String,purpose:String?){val clean=title.trim();if(clean.isEmpty())return;viewModelScope.launch{val s=apiSession();if(s!=null){try{val created=part2.createProject(s,clean,purpose);loadProjects(true);openProject(created.id);return@launch}catch(_:Throwable){}}localDb.projects().upsert(LocalProjectEntity(newId("proj"),DEV_ACCOUNT,clean,purpose?.trim()?.takeIf{it.isNotEmpty()},"ACTIVE",0,System.currentTimeMillis(),1,"PENDING"))}}

    fun refreshChats(projectId:String?=null)=viewModelScope.launch{loadChats(projectId,true)}
    private suspend fun loadChats(projectId:String?,force:Boolean){val s=apiSession()?:run{_chats.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_chats.value=part2.chats(s,projectId,force)}
    fun createChat(projectId:String?=null)=viewModelScope.launch{val s=apiSession()?:return@launch;runCatching{part2.createConversation(s,projectId,title=if(projectId==null)"New chat" else "Project chat")}.onSuccess{c->_createdConversationId.value=c.id;loadChats(projectId,true);loadMessages(c.id,true);bindContext(projectId,c.id,null,"DEFAULT")}.onFailure{_streamError.value="CHAT_CREATE_FAILED"}}
    fun consumeCreatedConversation(){_createdConversationId.value=null}
    fun openConversation(id:String)=viewModelScope.launch{loadMessages(id,true)}
    private suspend fun loadMessages(id:String,force:Boolean){val s=apiSession()?:return;_messages.value=_messages.value.copy(loading=_messages.value.value.isNullOrEmpty(),errorCode=null);_messages.value=part2.messages(s,id,force)}
    fun toggleSource(id:String){_selectedSources.value=if(id in _selectedSources.value)_selectedSources.value-id else _selectedSources.value+id}
    fun clearSelectedSources(){_selectedSources.value=emptySet()}
    fun sendChat(conversationId:String,projectId:String?,text:String,learningMode:String="DEFAULT")=viewModelScope.launch(Dispatchers.IO){val s=apiSession()?:return@launch;_streaming.value=true;_streamingText.value="";_streamError.value=null;try{part2.streamAi(s,conversationId,projectId,_selectedSources.value.toList(),text,learningMode){event->when(event.type){"segment"->event.segment?.let{_streamingText.value+=it};"error"->_streamError.value=event.errorCode?:"AI_ERROR"}};loadMessages(conversationId,true);bindContext(projectId,conversationId,null,learningMode)}catch(e:BackendUiException){_streamError.value=e.code}catch(_:Throwable){_streamError.value="OFFLINE"}finally{_streaming.value=false;_streamingText.value=""}}
    fun retryMessage(conversationId:String,messageId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.retryMessage(s,conversationId,messageId);loadMessages(conversationId,true)}
    fun regenerateMessage(conversationId:String,messageId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.regenerateMessage(s,conversationId,messageId);loadMessages(conversationId,true)}
    fun loadCitations(conversationId:String,messageId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;runCatching{part2.citations(s,conversationId,messageId)}.onSuccess{_citations.value=_citations.value+(messageId to it)}}

    fun refreshSources()=viewModelScope.launch{loadSources(true)}
    private suspend fun loadSources(force:Boolean){val s=apiSession()?:run{_sources.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_sources.value=part2.sources(s,force)}
    fun createTextSource(title:String,text:String)=viewModelScope.launch{val s=apiSession()?:return@launch;runCatching{part2.createTextSource(s,title,text)}.onSuccess{loadSources(true)}.onFailure{_mutationFeedback.value=MutationFeedback(false,(it as? BackendUiException)?.code?:"OFFLINE",it.message,true)}}
    fun retrySource(id:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.retrySource(s,id);loadSources(true)}
    fun search(query:String,projectId:String?=null)=viewModelScope.launch{val s=apiSession()?:run{_search.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return@launch};_search.value=RepositoryState(_search.value.value,DataFreshness.FRESH,loading=true);_search.value=part2.search(s,query,projectId)}

    fun openAssessment(id:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_assessmentResult.value=null;_attempt.value=null;_assessment.value=RepositoryState(null,DataFreshness.FRESH,loading=true);_assessment.value=part2.assessment(s,id)}
    fun clearAssessment(){_assessment.value=RepositoryState(null,DataFreshness.OFFLINE);_attempt.value=null;_assessmentResult.value=null}
    fun startAssessment()=viewModelScope.launch{val s=apiSession()?:return@launch;val id=_assessment.value.value?.id?:return@launch;runCatching{part2.startAttempt(s,id)}.onSuccess{_attempt.value=it}.onFailure{_mutationFeedback.value=MutationFeedback(false,(it as? BackendUiException)?.code?:"ASSESSMENT_START_FAILED",it.message,true)}}
    fun answerAssessment(questionId:String,answers:List<String>)=viewModelScope.launch{val s=apiSession()?:return@launch;val a=_attempt.value?:return@launch;_mutationFeedback.value=part2.answer(s,a.id,questionId,answers)}
    fun submitAssessment()=viewModelScope.launch{val s=apiSession()?:return@launch;val a=_attempt.value?:return@launch;runCatching{part2.submitAttempt(s,a.id)}.onSuccess{_assessmentResult.value=it;loadMistakes(null,true);loadGame(true);loadHome(true);loadPersonal(true)}.onFailure{_mutationFeedback.value=MutationFeedback(false,(it as? BackendUiException)?.code?:"ASSESSMENT_SUBMIT_FAILED",it.message,true)}}

    fun createPractice(projectId:String?,topic:String?)=viewModelScope.launch{val s=apiSession()?:return@launch;_practice.value=RepositoryState(null,DataFreshness.FRESH,loading=true);runCatching{part2.createPractice(s,projectId,topic)}.onSuccess{_practice.value=RepositoryState(it,DataFreshness.FRESH);_openedPracticeId.value=it.id;bindContext(projectId,null,topic,"DEFAULT")}.onFailure{_practice.value=RepositoryState(null,DataFreshness.STALE,errorCode=(it as? BackendUiException)?.code?:"PRACTICE_CREATE_FAILED",retryable=true)}}
    fun openPractice(id:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_practice.value=part2.practice(s,id);_practiceHint.value=null;_practiceCheck.value=null;_practiceComplete.value=null}
    fun consumeOpenedPractice(){_openedPracticeId.value=null}
    fun practiceAttempt(itemId:String,answer:String)=viewModelScope.launch{val s=apiSession()?:return@launch;val p=_practice.value.value?:return@launch;_mutationFeedback.value=part2.practiceAttempt(s,p.id,itemId,answer);_practice.value=part2.practice(s,p.id)}
    fun practiceHint(itemId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;val p=_practice.value.value?:return@launch;runCatching{part2.practiceHint(s,p.id,itemId)}.onSuccess{_practiceHint.value=it}}
    fun practiceCheck(itemId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;val p=_practice.value.value?:return@launch;runCatching{part2.practiceCheck(s,p.id,itemId)}.onSuccess{_practiceCheck.value=it;_practice.value=part2.practice(s,p.id)}}
    fun practiceSkip(itemId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;val p=_practice.value.value?:return@launch;_mutationFeedback.value=part2.practiceSkip(s,p.id,itemId);_practice.value=part2.practice(s,p.id)}
    fun completePractice()=viewModelScope.launch{val s=apiSession()?:return@launch;val p=_practice.value.value?:return@launch;runCatching{part2.completePractice(s,p.id,p.revision)}.onSuccess{_practiceComplete.value=it;loadMistakes(null,true);loadGame(true)}}
    fun refreshFlashcards()=viewModelScope.launch{loadFlashcards(true)}
    private suspend fun loadFlashcards(force:Boolean){val s=apiSession()?:run{_flashcards.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_flashcards.value=part2.flashcards(s,force)}
    fun reviewFlashcard(cardId:String,rating:String)=viewModelScope.launch{val s=apiSession()?:return@launch;runCatching{part2.reviewFlashcard(s,cardId,rating)}.onSuccess{loadFlashcards(true)}}
    fun refreshMistakes()=viewModelScope.launch{loadMistakes(null,true)}
    private suspend fun loadMistakes(projectId:String?,force:Boolean){val s=apiSession()?:run{_mistakes.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_mistakes.value=part2.mistakes(s,projectId,force)}
    fun resolveMistake(m:MistakeUiModel)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.resolveMistake(s,m);loadMistakes(null,true)}
    fun practiceFromMistake(id:String)=viewModelScope.launch{val s=apiSession()?:return@launch;runCatching{part2.practiceFromMistake(s,id)}.onSuccess{pid->_openedPracticeId.value=pid;openPractice(pid)}}
    fun flashcardFromMistake(id:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.flashcardFromMistake(s,id);loadFlashcards(true)}

    fun refreshStore()=viewModelScope.launch{loadStore(true);loadInventory(true);loadAvatars(true);loadGame(true)}
    private suspend fun loadStore(force:Boolean){val s=apiSession()?:run{_store.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_store.value=part2.store(s,force)}
    private suspend fun loadInventory(force:Boolean){val s=apiSession()?:run{_inventory.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_inventory.value=part2.inventory(s,force)}
    private suspend fun loadAvatars(force:Boolean){val s=apiSession()?:run{_avatars.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_avatars.value=part2.avatars(s,force)}
    private suspend fun loadMap(force:Boolean){val s=apiSession()?:run{_map.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_map.value=part2.personalMap(s,force)}
    private suspend fun loadGame(force:Boolean){val s=apiSession()?:run{_gameProfile.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_gameProfile.value=part2.gameProfile(s,force)}
    fun purchase(itemId:String)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.purchase(s,itemId);loadStore(true);loadInventory(true);loadAvatars(true);loadGame(true);loadHome(true);loadPersonal(true)}
    fun equipAvatar(avatarId:String,expectedRevision:Long)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.equipAvatar(s,avatarId,expectedRevision);loadAvatars(true);loadGame(true);loadHome(true);loadPersonal(true)}
    fun unlockMap()=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.unlockMap(s);loadMap(true);loadGame(true);loadHome(true);loadPersonal(true)}
    fun startMapUnit(unitId:String,revision:Long)=viewModelScope.launch{val s=apiSession()?:return@launch;_mutationFeedback.value=part2.startMapUnit(s,unitId,revision);loadMap(true);loadGame(true);loadHome(true);loadPersonal(true)}
    fun refreshHistory()=viewModelScope.launch{loadHistory(true)}
    private suspend fun loadHistory(force:Boolean){val s=apiSession()?:run{_history.value=RepositoryState(emptyList(),DataFreshness.OFFLINE,errorCode="NO_SESSION");return};_history.value=part2.history(s,force)}

    private suspend fun bindContext(projectId:String?,conversationId:String?,topic:String?,learningMode:String){val s=apiSession()?:return;runCatching{val current=part3.contextCarry(s).value;val value=ContextCarryModel(s.accountId,projectId,_selectedSources.value.toList(),conversationId,null,topic,learningMode,"ANDROID_FRONTEND",if(projectId==null)"HOME" else "PROJECT",current?.contextRevision?:0);part3.updateContextCarryOfflineFirst(s,value)}}
}
