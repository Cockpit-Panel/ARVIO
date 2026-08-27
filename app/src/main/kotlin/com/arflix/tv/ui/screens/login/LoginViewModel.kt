package com.arflix.tv.ui.screens.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.data.api.ArvioPortal
import com.arflix.tv.data.api.CockpitArvioApi
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.repository.AuthRepository
import com.arflix.tv.data.repository.AuthState
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.util.authDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoadingPortals: Boolean = false,
    val error: String? = null,
    val authState: AuthState = AuthState.Loading,
    val portals: List<ArvioPortal> = emptyList(),
    val loginReady: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val cockpitArvioApi: CockpitArvioApi,
    private val iptvRepository: com.arflix.tv.data.repository.IptvRepository,
    private val streamRepository: StreamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private val gson = Gson()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                _uiState.update { it.copy(authState = authState) }
            }
        }
        loadPortals()
    }

    fun signIn(username: String, password: String, serverUrl: String) {
        val trimmedUsername = username.trim()
        val trimmedServerUrl = serverUrl.trim()
        if (trimmedServerUrl.isBlank()) {
            _uiState.update { it.copy(error = "Please select a service") }
            return
        }
        if (trimmedUsername.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your username") }
            return
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = authRepository.signIn(trimmedUsername, password, trimmedServerUrl)

            if (result.isSuccess) {
                try {
                    val prefs = context.authDataStore.data.first()
                    val serverUrl = prefs[stringPreferencesKey("server_url")].orEmpty()
                    val user = prefs[stringPreferencesKey("username")].orEmpty()
                    val pass = prefs[stringPreferencesKey("password")].orEmpty()
                    val displayName = prefs[stringPreferencesKey("display_name")].orEmpty().ifBlank { "Arvio" }
                    if (serverUrl.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                        val finalM3uUrl = "$serverUrl $user $pass"
                        val finalEpgUrl = "$serverUrl $user $pass"
                        val list = listOf(
                            com.arflix.tv.data.repository.IptvPlaylistEntry(
                                id = "list_1",
                                name = displayName,
                                m3uUrl = finalM3uUrl,
                                epgUrl = finalEpgUrl,
                                enabled = true,
                                epgUrls = listOf(finalEpgUrl)
                            )
                        )
                        iptvRepository.savePlaylists(list)
                    }
                } catch (e: Exception) {
                    // Ignore errors during IPTV auto-sync
                }

                try {
                    importPanelAddons()
                } catch (e: Exception) {
                    // Ignore errors during addon provisioning
                }
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                    loginReady = result.isSuccess
                )
            }
        }
    }

    private fun loadPortals() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPortals = true) }
            runCatching { cockpitArvioApi.getPortals().portals }
                .onSuccess { portals ->
                    val servicePortals = portals.filter { it.id != 0 }
                    _uiState.update {
                        it.copy(
                            isLoadingPortals = false,
                            portals = servicePortals
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoadingPortals = false,
                            error = "Unable to load services"
                        )
                    }
                }
        }
    }

    fun onLoginNavigationHandled() {
        _uiState.update { it.copy(loginReady = false) }
    }

    private suspend fun importPanelAddons() {
        val payload = authRepository.loadAccountSyncPayload().getOrNull().orEmpty()
        if (payload.isBlank()) return

        val root = org.json.JSONObject(payload)
        val addonsJson = root.optJSONArray("addons")?.toString().orEmpty()
        if (addonsJson.isBlank()) return

        val type = TypeToken.getParameterized(List::class.java, Addon::class.java).type
        val addons: List<Addon> = gson.fromJson(addonsJson, type) ?: emptyList()
        streamRepository.replaceSharedAddonsFromCloud(addons)
    }
}
