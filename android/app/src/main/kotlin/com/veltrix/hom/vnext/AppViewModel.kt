package com.veltrix.hom.vnext

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veltrix.hom.vnext.core.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEV_ACCOUNT="dev-account-local"
class AppViewModel(app:Application):AndroidViewModel(app){
    private val localDb=VeltrixLocalDatabase.get(app);private val sessionStore=SessionStore(app);private val part3=Part3AndroidRepository(app)
    val projects:StateFlow<List<LocalProjectEntity>> = localDb.projects().observe(DEV_ACCOUNT).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _session=MutableStateFlow<LocalSession?>(null);val session:StateFlow<LocalSession?> = _session.asStateFlow()
    private val _sessionResolved=MutableStateFlow(false);val sessionResolved:StateFlow<Boolean> = _sessionResolved.asStateFlow()
    private val _home=MutableStateFlow(RepositoryState<HomeFinalModel>(null,DataFreshness.OFFLINE,loading=true));val home:StateFlow<RepositoryState<HomeFinalModel>> = _home.asStateFlow()
    private val _personal=MutableStateFlow(RepositoryState<PersonalFinalModel>(null,DataFreshness.OFFLINE,loading=true));val personal:StateFlow<RepositoryState<PersonalFinalModel>> = _personal.asStateFlow()
    init{viewModelScope.launch{_session.value=sessionStore.read();_sessionResolved.value=true;loadHome(false);loadPersonal(false)}}
    fun refreshHome()=viewModelScope.launch{loadHome(true)};fun refreshPersonal()=viewModelScope.launch{loadPersonal(true)};fun refreshAll(force:Boolean=true)=viewModelScope.launch{loadHome(force);loadPersonal(force)}
    private suspend fun loadHome(force:Boolean){val s=_session.value?:run{_home.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};val old=_home.value.value;_home.value=_home.value.copy(loading=old==null,errorCode=null);_home.value=part3.home(ApiSession(s.accountId,s.accessToken),force)}
    private suspend fun loadPersonal(force:Boolean){val s=_session.value?:run{_personal.value=RepositoryState(null,DataFreshness.OFFLINE,errorCode="NO_SESSION");return};val old=_personal.value.value;_personal.value=_personal.value.copy(loading=old==null,errorCode=null);_personal.value=part3.personal(ApiSession(s.accountId,s.accessToken),force)}
    fun createProject(title:String,purpose:String?){val clean=title.trim();if(clean.isEmpty())return;viewModelScope.launch{localDb.projects().upsert(LocalProjectEntity(newId("proj"),DEV_ACCOUNT,clean,purpose?.trim()?.takeIf{it.isNotEmpty()},"ACTIVE",0,System.currentTimeMillis(),1,"PENDING"))}}
}
