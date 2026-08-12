package com.veltrix.hom.vnext

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veltrix.hom.vnext.core.PrimaryDestination
import com.veltrix.hom.vnext.core.newId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEV_ACCOUNT = "dev-account-local"

data class ShellState(
    val destination: PrimaryDestination = PrimaryDestination.HOME,
    val capability: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = VeltrixLocalDatabase.get(app)
    val projects: StateFlow<List<LocalProjectEntity>> = db.projects().observe(DEV_ACCOUNT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createProject(title: String, purpose: String?) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.projects().upsert(
                LocalProjectEntity(
                    id = newId("proj"), accountId = DEV_ACCOUNT, title = clean, purpose = purpose?.trim()?.takeIf { it.isNotEmpty() },
                    status = "ACTIVE", priority = 0, updatedAtEpochMs = System.currentTimeMillis(), revision = 1, syncState = "PENDING"
                )
            )
        }
    }
}
