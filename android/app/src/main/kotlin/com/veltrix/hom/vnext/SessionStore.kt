package com.veltrix.hom.vnext

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name="veltrix_session")
data class LocalSession(val accountId:String,val accessToken:String)
class SessionStore(private val context:Context) {
    private val accountKey=stringPreferencesKey("account_id")
    private val tokenKey=stringPreferencesKey("access_token")
    suspend fun save(session:LocalSession){context.sessionDataStore.edit{it[accountKey]=session.accountId;it[tokenKey]=session.accessToken}}
    suspend fun read():LocalSession?{val p=context.sessionDataStore.data.first();val a=p[accountKey]?:return null;val t=p[tokenKey]?:return null;return LocalSession(a,t)}
    suspend fun clear(){context.sessionDataStore.edit{it.remove(accountKey);it.remove(tokenKey)}}
}
