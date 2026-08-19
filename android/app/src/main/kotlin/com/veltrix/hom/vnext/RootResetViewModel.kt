package com.veltrix.hom.vnext

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Account-first, online-only root state owner for the Final Root Reset.
 *
 * A locally-restored token or cached model never grants PRODUCT. PRODUCT is entered only after
 * trusted backend session validation. World state is published only when every required repository
 * snapshot is FRESH; stale/offline values remain internal caches and are never current account truth.
 */
class RootResetViewModel(app: Application) : AndroidViewModel(app) {
    private val sessionStore = SessionStore(app)
    private val api = VeltrixApiClient()
    private val network = NetworkMonitor(app)
    private val part3 = Part3AndroidRepository(app)
    private val part2 = Part2FeatureRepository(app)

    private val _gate = MutableStateFlow(ProductGateState())
    val gate: StateFlow<ProductGateState> = _gate.asStateFlow()

    private val _auth = MutableStateFlow(AuthUiState())
    val auth: StateFlow<AuthUiState> = _auth.asStateFlow()

    private val _session = MutableStateFlow<LocalSession?>(null)
    val session: StateFlow<LocalSession?> = _session.asStateFlow()

    private val _home = MutableStateFlow<HomeFinalModel?>(null)
    val home: StateFlow<HomeFinalModel?> = _home.asStateFlow()
    private val _personal = MutableStateFlow<PersonalFinalModel?>(null)
    val personal: StateFlow<PersonalFinalModel?> = _personal.asStateFlow()
    private val _projects = MutableStateFlow<List<ProjectCardModel>>(emptyList())
    val projects: StateFlow<List<ProjectCardModel>> = _projects.asStateFlow()
    private val _store = MutableStateFlow<StoreCatalogUiModel?>(null)
    val store: StateFlow<StoreCatalogUiModel?> = _store.asStateFlow()
    private val _inventory = MutableStateFlow<List<InventoryItemUiModel>>(emptyList())
    val inventory: StateFlow<List<InventoryItemUiModel>> = _inventory.asStateFlow()
    private val _avatars = MutableStateFlow<List<AvatarCatalogUiModel>>(emptyList())
    val avatars: StateFlow<List<AvatarCatalogUiModel>> = _avatars.asStateFlow()
    private val _map = MutableStateFlow<PersonalMapUiModel?>(null)
    val map: StateFlow<PersonalMapUiModel?> = _map.asStateFlow()
    private val _game = MutableStateFlow<GameProfileUiModel?>(null)
    val game: StateFlow<GameProfileUiModel?> = _game.asStateFlow()

    init {
        viewModelScope.launch { bootstrap() }
        viewModelScope.launch {
            network.state.collect { state ->
                if (!state.validated && _gate.value.kind == ProductGateKind.PRODUCT) {
                    clearWorldState()
                    _gate.value = ProductGateState(
                        kind = ProductGateKind.CONNECTION,
                        connectionIssue = ConnectionIssue.NO_INTERNET,
                        message = "Veltrix needs a live connection before account state can be used.",
                    )
                } else if (
                    state.validated &&
                    _gate.value.kind == ProductGateKind.CONNECTION &&
                    _gate.value.connectionIssue == ConnectionIssue.NO_INTERNET
                ) {
                    bootstrap()
                }
            }
        }
    }

    override fun onCleared() {
        network.close()
        super.onCleared()
    }

    fun setAuthMode(mode: AuthMode) {
        _auth.value = _auth.value.copy(mode = mode, error = null)
    }

    fun reportAuthError(message: String) {
        _auth.value = _auth.value.copy(processing = false, error = message)
    }

    fun signIn(login: String, password: String) = authenticate {
        api.login(login.trim(), password)
    }

    fun createAccount(login: String, password: String, displayName: String) = authenticate {
        api.register(login.trim(), password, displayName.trim())
    }

    fun completeGoogleSignIn(idToken: String, nonce: String) = authenticate {
        api.exchangeGoogleIdentity(idToken, nonce)
    }

    fun retryConnection() {
        viewModelScope.launch { bootstrap() }
    }

    fun signOut(clearCredentialState: suspend () -> Unit = {}) {
        _gate.value = ProductGateState(kind = ProductGateKind.CHECKING)
        viewModelScope.launch {
            val current = _session.value
            if (current != null && network.currentValidated()) {
                withContext(Dispatchers.IO) {
                    runCatching { api.logout(ApiSession(current.accountId, current.accessToken)) }
                }
            }
            sessionStore.clear(explicitSignOut = true)
            _session.value = null
            clearWorldState()
            runCatching { clearCredentialState() }
            _auth.value = AuthUiState(mode = AuthMode.SIGN_IN)
            _gate.value = ProductGateState(kind = ProductGateKind.AUTH)
        }
    }

    suspend fun wasExplicitlySignedOut(): Boolean = sessionStore.wasExplicitlySignedOut()

    private fun authenticate(block: suspend () -> ApiSession) {
        if (!network.currentValidated()) {
            _gate.value = ProductGateState(
                kind = ProductGateKind.CONNECTION,
                connectionIssue = ConnectionIssue.NO_INTERNET,
                message = "Connect to the internet to sign in.",
            )
            return
        }
        viewModelScope.launch {
            _auth.value = _auth.value.copy(processing = true, error = null)
            try {
                val apiSession = withContext(Dispatchers.IO) { block() }
                val local = LocalSession(apiSession.accountId, apiSession.token)
                sessionStore.save(local)
                _session.value = local
                if (!validateServerSession(local)) return@launch
                _auth.value = _auth.value.copy(processing = false, error = null)
                _gate.value = ProductGateState(kind = ProductGateKind.PRODUCT)
                refreshWorlds()
            } catch (_: GoogleBackendContractMissingException) {
                _auth.value = _auth.value.copy(
                    processing = false,
                    error = "Google identity is ready on Android, but the trusted Veltrix server exchange contract is not configured yet.",
                )
            } catch (e: BackendUiException) {
                _auth.value = _auth.value.copy(processing = false, error = authMessage(e))
            } catch (_: Throwable) {
                _auth.value = _auth.value.copy(processing = false, error = "Veltrix could not reach the server. Try again.")
            }
        }
    }

    private suspend fun bootstrap() {
        _gate.value = ProductGateState(kind = ProductGateKind.CHECKING)
        val stored = sessionStore.read()
        _session.value = stored
        if (stored == null) {
            clearWorldState()
            _gate.value = ProductGateState(kind = ProductGateKind.AUTH)
            return
        }
        if (!network.currentValidated()) {
            clearWorldState()
            _gate.value = ProductGateState(
                kind = ProductGateKind.CONNECTION,
                connectionIssue = ConnectionIssue.NO_INTERNET,
                message = "Connect to continue your Veltrix account.",
            )
            return
        }
        if (!validateServerSession(stored)) return
        _gate.value = ProductGateState(kind = ProductGateKind.PRODUCT)
        refreshWorlds()
    }

    private suspend fun validateServerSession(local: LocalSession): Boolean {
        return try {
            val (status, _) = withContext(Dispatchers.IO) {
                api.validateSession(ApiSession(local.accountId, local.accessToken))
            }
            when (status) {
                200 -> true
                401, 403 -> {
                    sessionStore.clear(explicitSignOut = false)
                    _session.value = null
                    clearWorldState()
                    _gate.value = ProductGateState(
                        kind = ProductGateKind.SESSION_EXPIRED,
                        message = "Your session ended. Sign in again to continue.",
                    )
                    false
                }
                else -> {
                    clearWorldState()
                    _gate.value = ProductGateState(
                        kind = ProductGateKind.CONNECTION,
                        connectionIssue = ConnectionIssue.SERVER_UNAVAILABLE,
                        message = "Veltrix is temporarily unavailable.",
                    )
                    false
                }
            }
        } catch (_: Throwable) {
            clearWorldState()
            val connected = network.currentValidated()
            _gate.value = ProductGateState(
                kind = ProductGateKind.CONNECTION,
                connectionIssue = if (connected) ConnectionIssue.SERVER_UNAVAILABLE else ConnectionIssue.NO_INTERNET,
                message = if (connected) "Veltrix is temporarily unavailable." else "Connect to continue your Veltrix account.",
            )
            false
        }
    }

    fun refreshWorlds() {
        val local = _session.value ?: return
        if (_gate.value.kind != ProductGateKind.PRODUCT || !network.currentValidated()) {
            clearWorldState()
            _gate.value = ProductGateState(
                kind = ProductGateKind.CONNECTION,
                connectionIssue = ConnectionIssue.NO_INTERNET,
                message = "Connect to refresh your Veltrix world.",
            )
            return
        }
        val session = ApiSession(local.accountId, local.accessToken)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                WorldRefresh(
                    home = part3.home(session, true),
                    personal = part3.personal(session, true),
                    projects = part2.projects(session, true),
                    store = part2.store(session, true),
                    inventory = part2.inventory(session, true),
                    avatars = part2.avatars(session, true),
                    map = part2.personalMap(session, true),
                    game = part2.gameProfile(session, true),
                )
            }
            if (!results.allFresh) {
                // Repository snapshots intentionally collapse auth and transport failures into
                // freshness. Revalidate the trusted session before classifying the root failure so
                // a token revoked while PRODUCT is open can never masquerade as a connection error.
                if (!validateServerSession(local)) return@launch
                clearWorldState()
                val connected = network.currentValidated()
                _gate.value = ProductGateState(
                    kind = ProductGateKind.CONNECTION,
                    connectionIssue = if (connected) ConnectionIssue.SERVER_UNAVAILABLE else ConnectionIssue.NO_INTERNET,
                    message = if (connected) "Veltrix could not refresh current account state." else "Connection interrupted.",
                )
                return@launch
            }
            _home.value = results.home.value
            _personal.value = results.personal.value
            _projects.value = results.projects.value.orEmpty()
            _store.value = results.store.value
            _inventory.value = results.inventory.value.orEmpty()
            _avatars.value = results.avatars.value.orEmpty()
            _map.value = results.map.value
            _game.value = results.game.value
        }
    }

    private fun clearWorldState() {
        _home.value = null
        _personal.value = null
        _projects.value = emptyList()
        _store.value = null
        _inventory.value = emptyList()
        _avatars.value = emptyList()
        _map.value = null
        _game.value = null
    }

    private fun authMessage(e: BackendUiException): String = when (e.code) {
        "INVALID_CREDENTIALS", "AUTH_INVALID" -> "Email, username or password is incorrect."
        "LOGIN_TAKEN", "ACCOUNT_EXISTS" -> "That account already exists."
        "RATE_LIMITED" -> "Too many attempts. Try again shortly."
        else -> e.detail.takeIf { it.isNotBlank() } ?: "Sign in could not complete."
    }

    private data class WorldRefresh(
        val home: RepositoryState<HomeFinalModel>,
        val personal: RepositoryState<PersonalFinalModel>,
        val projects: RepositoryState<List<ProjectCardModel>>,
        val store: RepositoryState<StoreCatalogUiModel>,
        val inventory: RepositoryState<List<InventoryItemUiModel>>,
        val avatars: RepositoryState<List<AvatarCatalogUiModel>>,
        val map: RepositoryState<PersonalMapUiModel>,
        val game: RepositoryState<GameProfileUiModel>,
    ) {
        val allFresh: Boolean
            get() = listOf(
                home.freshness,
                personal.freshness,
                projects.freshness,
                store.freshness,
                inventory.freshness,
                avatars.freshness,
                map.freshness,
                game.freshness,
            ).all { it == DataFreshness.FRESH }
    }
}
