package com.veltrix.hom.vnext

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "veltrix_session")

data class LocalSession(val accountId: String, val accessToken: String)

/**
 * Device-scoped session persistence only. This is restoration state, not offline product authority.
 * Server validation is required before the account world is exposed after cold launch/process death.
 */
class SessionStore(private val context: Context) {
    private val accountKey = stringPreferencesKey("account_id")
    private val tokenKey = stringPreferencesKey("access_token")
    private val explicitSignedOutKey = booleanPreferencesKey("explicit_signed_out")

    suspend fun save(session: LocalSession) {
        context.sessionDataStore.edit {
            it[accountKey] = session.accountId
            it[tokenKey] = session.accessToken
            it[explicitSignedOutKey] = false
        }
    }

    suspend fun read(): LocalSession? {
        val p = context.sessionDataStore.data.first()
        val a = p[accountKey] ?: return null
        val t = p[tokenKey] ?: return null
        return LocalSession(a, t)
    }

    suspend fun wasExplicitlySignedOut(): Boolean =
        context.sessionDataStore.data.first()[explicitSignedOutKey] == true

    suspend fun clear(explicitSignOut: Boolean = false) {
        context.sessionDataStore.edit {
            it.remove(accountKey)
            it.remove(tokenKey)
            it[explicitSignedOutKey] = explicitSignOut
        }
    }
}
