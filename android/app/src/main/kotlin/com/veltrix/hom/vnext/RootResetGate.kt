package com.veltrix.hom.vnext

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProductGateKind { CHECKING, AUTH, PRODUCT, CONNECTION, SESSION_EXPIRED }
enum class ConnectionIssue { NO_INTERNET, SERVER_UNAVAILABLE }

data class ProductGateState(
    val kind: ProductGateKind = ProductGateKind.CHECKING,
    val connectionIssue: ConnectionIssue? = null,
    val message: String? = null,
)

enum class AuthMode { SIGN_IN, CREATE_ACCOUNT }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val processing: Boolean = false,
    val error: String? = null,
)

data class NetworkState(val validated: Boolean)

/**
 * Connectivity is only a transport signal. It never authorizes account/product state.
 * Product access still requires a server-validated Veltrix session.
 */
class NetworkMonitor(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(NetworkState(isValidated()))
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
        override fun onUnavailable() = publish()
    }

    init {
        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
        publish()
    }

    fun currentValidated(): Boolean = isValidated()

    fun close() {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    private fun publish() {
        _state.value = NetworkState(isValidated())
    }

    private fun isValidated(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
