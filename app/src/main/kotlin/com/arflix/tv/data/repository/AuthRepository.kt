package com.arflix.tv.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.data.model.Addon
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.Constants
import com.arflix.tv.util.authDataStore
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Serializable
data class UserProfile(
    val id: String = "",
    val email: String = "",
    val trakt_token: kotlinx.serialization.json.JsonObject? = null,
    val addons: String? = null,
    val default_subtitle: String? = null,
    val auto_play_next: Boolean? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

sealed class AuthState {
    object Loading : AuthState()
    object NotAuthenticated : AuthState()
    data class Authenticated(
        val userId: String,
        val email: String,
        val profile: UserProfile?
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cockpitArvioApi: com.arflix.tv.data.api.CockpitArvioApi,
    private val streamRepositoryProvider: Provider<StreamRepository>,
    private val traktRepositoryProvider: Provider<TraktRepository>
) {
    private val TAG = "AuthRepository"

    private object PrefsKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ACCOUNT_SYNC_PAYLOAD = stringPreferencesKey("account_sync_payload")
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    private val gson = Gson()

    suspend fun checkAuthState() {
        try {
            val prefs = context.authDataStore.data.first()
            val accessToken = prefs[PrefsKeys.ACCESS_TOKEN]
            val cachedUserId = prefs[PrefsKeys.USER_ID]
            val cachedEmail = prefs[PrefsKeys.USER_EMAIL]

            if (!accessToken.isNullOrBlank() && !cachedUserId.isNullOrBlank()) {
                val email = cachedEmail ?: "user@xtream.local"
                val profile = UserProfile(id = cachedUserId, email = email)
                _userProfile.value = profile
                _authState.value = AuthState.Authenticated(cachedUserId, email, profile)
                runCatching { provisionPanelAddonsFromCloud() }
            } else {
                _authState.value = AuthState.NotAuthenticated
            }
        } catch (e: Exception) {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    suspend fun signIn(username: String, password: String, serverUrl: String = ""): Result<Unit> {
        if (username.isBlank() || password.isBlank()) {
            val msg = "Username and password are required"
            _authState.value = AuthState.Error(msg)
            return Result.failure(Exception(msg))
        }
        return try {
            AppLogger.breadcrumb("Auth", "panel_sign_in_start")
            _authState.value = AuthState.Loading

            val response = cockpitArvioApi.login(
                com.arflix.tv.data.api.ArvioLoginRequest(
                    username = username,
                    password = password,
                    deviceName = "Android TV",
                    serverUrl = serverUrl.trim()
                )
            )

            val user = response.user
            val tokens = response.tokens

            storeRawSessionTokens(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                userId = user.id,
                email = user.email,
                accountSyncPayload = response.accountSyncPayload
            )
            provisionPanelAddons(response.addons, response.accountSyncPayload)

            context.authDataStore.edit { prefs ->
                prefs[stringPreferencesKey("server_url")] = user.serverUrl
                prefs[stringPreferencesKey("username")] = username
                prefs[stringPreferencesKey("password")] = password
                prefs[PrefsKeys.DISPLAY_NAME] = user.displayName
            }

            val profile = UserProfile(
                id = user.id,
                email = user.email
            )
            _userProfile.value = profile
            _authState.value = AuthState.Authenticated(user.id, user.email, profile)

            AppLogger.breadcrumb("Auth", "panel_sign_in_success")
            Result.success(Unit)
        } catch (e: Exception) {
            val message = e.message ?: "Sign in failed"
            _authState.value = AuthState.Error(message)
            AppLogger.breadcrumb("Auth", "panel_sign_in_failed", severity = "warning")
            Result.failure(Exception(message))
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        return Result.failure(Exception("Sign up disabled"))
    }

    suspend fun signInWithSessionTokens(accessToken: String, refreshToken: String): Result<Unit> {
        return Result.failure(Exception("Session import disabled"))
    }

    suspend fun signOut() {
        try {
            traktRepositoryProvider.get().logout()
        } catch (e: Exception) {}
        context.authDataStore.edit { prefs -> prefs.clear() }
        context.settingsDataStore.edit { prefs -> prefs.clear() }
        _userProfile.value = null
        _authState.value = AuthState.NotAuthenticated
    }

    fun getTraktAccessToken(): String? = null
    fun isTraktLinked(): Boolean = false

    fun getCurrentUserId(): String? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.userId
            else -> null
        }
    }

    suspend fun getCurrentUserIdForSync(): String? = getCurrentUserId()
    fun getCurrentUserEmail(): String? = _userProfile.value?.email
    suspend fun hasValidCloudSyncSession(): Boolean = getCurrentUserId() != null

    suspend fun getAccessToken(): String? {
        return context.authDataStore.data.first()[PrefsKeys.ACCESS_TOKEN]
    }

    suspend fun refreshAccessToken(): String? = getAccessToken()

    private suspend fun storeRawSessionTokens(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String?,
        accountSyncPayload: String? = null
    ) {
        context.authDataStore.edit { prefs ->
            prefs[PrefsKeys.ACCESS_TOKEN] = accessToken
            prefs[PrefsKeys.REFRESH_TOKEN] = refreshToken
            prefs[PrefsKeys.USER_ID] = userId
            if (!email.isNullOrBlank()) {
                prefs[PrefsKeys.USER_EMAIL] = email
            }
            if (!accountSyncPayload.isNullOrBlank()) {
                prefs[PrefsKeys.ACCOUNT_SYNC_PAYLOAD] = accountSyncPayload
            }
        }
    }

    fun getAddonsFromProfile(): String? = null
    suspend fun getAddonsFromProfileFresh(): Result<String?> = Result.success(null)
    suspend fun saveAddonsToProfile(addonsJson: String): Result<Unit> = Result.success(Unit)

    fun getDefaultSubtitleFromProfile(): String? = null
    suspend fun saveDefaultSubtitleToProfile(subtitle: String?): Result<Unit> = Result.success(Unit)

    fun getAutoPlayNextFromProfile(): Boolean? = null
    suspend fun saveAutoPlayNextToProfile(autoPlayNext: Boolean): Result<Unit> = Result.success(Unit)

    suspend fun loadAccountSyncPayload(): Result<String?> {
        return try {
            val prefs = context.authDataStore.data.first()
            val accessToken = prefs[PrefsKeys.ACCESS_TOKEN]
            if (accessToken.isNullOrBlank()) {
                return Result.success(prefs[PrefsKeys.ACCOUNT_SYNC_PAYLOAD])
            }

            val payload = cockpitArvioApi.getAccountSyncPayload("Bearer $accessToken")
            val json = gson.toJson(payload)
            context.authDataStore.edit { editPrefs ->
                editPrefs[PrefsKeys.ACCOUNT_SYNC_PAYLOAD] = json
            }
            Result.success(json)
        } catch (e: Exception) {
            val cached = context.authDataStore.data.first()[PrefsKeys.ACCOUNT_SYNC_PAYLOAD]
            if (!cached.isNullOrBlank()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun saveAccountSyncPayload(payload: String): Result<Unit> {
        return try {
            context.authDataStore.edit { prefs ->
                prefs[PrefsKeys.ACCOUNT_SYNC_PAYLOAD] = payload
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mutateAccountSyncPayload(mutator: (JSONObject) -> Unit): Result<Unit> {
        return try {
            val current = loadAccountSyncPayload().getOrNull().orEmpty()
            val root = if (current.isBlank()) JSONObject() else JSONObject(current)
            mutator(root)
            saveAccountSyncPayload(root.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun provisionPanelAddonsFromCloud() {
        val payload = loadAccountSyncPayload().getOrNull().orEmpty()
        provisionPanelAddons(emptyList(), payload)
    }

    private suspend fun provisionPanelAddons(responseAddons: List<Addon>, accountSyncPayload: String?) {
        val addons = responseAddons.takeIf { it.isNotEmpty() }
            ?: addonsFromAccountSyncPayload(accountSyncPayload.orEmpty())

        if (addons.isNotEmpty()) {
            streamRepositoryProvider.get().replaceSharedAddonsFromCloud(addons)
        }
    }

    private fun addonsFromAccountSyncPayload(payload: String): List<Addon> {
        if (payload.isBlank()) return emptyList()

        return runCatching {
            val root = JSONObject(payload)
            val addonsJson = root.optJSONArray("addons")?.toString().orEmpty()
            if (addonsJson.isBlank()) return@runCatching emptyList()

            val type = TypeToken.getParameterized(List::class.java, Addon::class.java).type
            gson.fromJson<List<Addon>>(addonsJson, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
