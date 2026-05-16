package com.udl.smartrain.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.data.local.AppPreferences
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.local.RememberedUser
import com.udl.smartrain.data.repository.SessionRepository
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ml.ActivityRecognitionState
import com.udl.smartrain.ml.DebugRagGenerationSettings
import com.udl.smartrain.ml.RagGenerationMode
import com.udl.smartrain.ml.RagSourceDetail
import com.udl.smartrain.ml.SessionRagRecommender
import com.udl.smartrain.ml.resolved
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.service.TrackingSessionState
import com.udl.smartrain.ui.i18n.TextKey
import com.udl.smartrain.ui.i18n.text
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: SessionRepository,
    private val locationProvider: LocationProvider,
    private val auth: FirebaseAuth,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _currentSession = MutableStateFlow<Session?>(null)
    private val _ragGenerationUiState = MutableStateFlow(RagGenerationUiState())
    private val _rememberedUsers = MutableStateFlow(appPreferences.getRememberedUsers())
    private val _appLanguage = MutableStateFlow(appPreferences.loadLanguage())
    private val currentUserId = MutableStateFlow(auth.currentUser?.uid.orEmpty())

    var currentUserName by mutableStateOf(auth.currentUser?.email ?: _appLanguage.value.text(TextKey.USER))
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    val isAuthenticated: Boolean
        get() = currentUserId.value.isNotBlank()

    val ragGenerationUiState: StateFlow<RagGenerationUiState> = _ragGenerationUiState.asStateFlow()
    val ragGenerationSettings = DebugRagGenerationSettings.settings
    val rememberedUsers: StateFlow<List<RememberedUser>> = _rememberedUsers.asStateFlow()
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    val sessionsHistory = currentUserId
        .flatMapLatest { userId ->
            if (userId.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.getSessionHistory(userId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        DebugRagGenerationSettings.replace(appPreferences.loadRagSettings())
        auth.currentUser?.uid?.let { userId ->
            viewModelScope.launch {
                syncUserSessions(userId)
            }
        }
    }

    fun signIn(email: String, password: String, rememberUser: Boolean, onSuccess: () -> Unit) {
        authenticate(email, password, createAccount = false, rememberUser = rememberUser, onSuccess = onSuccess)
    }

    fun createAccount(email: String, password: String, rememberUser: Boolean, onSuccess: () -> Unit) {
        authenticate(email, password, createAccount = true, rememberUser = rememberUser, onSuccess = onSuccess)
    }

    private fun authenticate(
        email: String,
        password: String,
        createAccount: Boolean,
        rememberUser: Boolean,
        onSuccess: () -> Unit
    ) {
        if (email.isBlank() || password.length < 6) {
            authError = authValidationError(_appLanguage.value)
            return
        }

        viewModelScope.launch {
            try {
                if (createAccount) {
                    auth.createUserWithEmailAndPassword(email.trim(), password).await()
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password).await()
                }

                val user = auth.currentUser ?: error(userUnavailableError(_appLanguage.value))
                currentUserId.value = user.uid
                currentUserName = user.email ?: _appLanguage.value.text(TextKey.USER)
                authError = null
                if (rememberUser) {
                    appPreferences.saveRememberedUser(email.trim(), password)
                    _rememberedUsers.value = appPreferences.getRememberedUsers()
                }
                syncUserSessions(user.uid)
                onSuccess()
            } catch (e: Exception) {
                authError = e.localizedMessage ?: signInError(_appLanguage.value)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        currentUserId.value = ""
        currentUserName = _appLanguage.value.text(TextKey.USER)
        _currentSession.value = null
        TrackingSessionState.reset()
        ActivityRecognitionState.reset()
        appPreferences.clearRagSettings()
        DebugRagGenerationSettings.reset()
    }

    fun startNewSession() {
        val userId = currentUserId.value
        if (userId.isBlank()) {
            Log.e("DEBUG_VM", "No hi ha usuari autenticat per iniciar sessio.")
            return
        }

        _ragGenerationUiState.value = RagGenerationUiState()
        _currentSession.value = Session(
            id = UUID.randomUUID().toString(),
            userId = userId,
            sessionName = _appLanguage.value.text(TextKey.NEW_SESSION)
        )
    }

    fun finishAndSaveSession(context: Context, onSaved: () -> Unit = {}) {
        Log.d("DEBUG_VM", "Entrant a finishAndSaveSession()")

        locationProvider.stopTracking()

        val intent = Intent(context, TrackingService::class.java)
        context.stopService(intent)

        val current = _currentSession.value
        if (current == null) {
            Log.e("DEBUG_VM", "Error: _currentSession es NULL. No es pot guardar res.")
        } else {
            val mlSummary = ActivityRecognitionState.buildSessionSummary()
            val trackingMetrics = TrackingSessionState.metrics.value
            val durationSeconds = trackingMetrics.startedAtMillis
                ?.let { (System.currentTimeMillis() - it) / 1000 }
                ?: current.durationSeconds

            val sessionWithMlResults = current.copy(
                durationSeconds = durationSeconds,
                distanceMetres = trackingMetrics.distanceMeters,
                dominantActivity = mlSummary.dominantActivity,
                avgMlConfidence = mlSummary.avgMlConfidence,
                mlPredictionCount = mlSummary.mlPredictionCount,
                highIntensityCount = mlSummary.highIntensityCount,
                activityTimeline = mlSummary.activityTimeline,
                intensityScore = mlSummary.avgMlConfidence
            )
            Log.d("DEBUG_VM", "Sessio trobada, guardant: ${sessionWithMlResults.id}")
            viewModelScope.launch {
                val settings = DebugRagGenerationSettings.settings.value.resolved()
                _ragGenerationUiState.value = RagGenerationUiState(
                    isGenerating = true,
                    message = when {
                        settings.useRemoteRag -> _appLanguage.value.text(TextKey.SESSION_SAVE_OLLAMA_GENERATING, "Remote RAG")
                        settings.useOllama -> _appLanguage.value.text(TextKey.SESSION_SAVE_OLLAMA_GENERATING, settings.ollamaModel)
                        else -> _appLanguage.value.text(TextKey.SESSION_SAVE_LOCAL_GENERATING)
                    }
                )
                val ragResult = SessionRagRecommender.buildInsightWithGenerator(
                    session = sessionWithMlResults,
                    settings = settings,
                    language = _appLanguage.value
                )
                val ragInsight = ragResult.insight
                val sessionWithRagResults = sessionWithMlResults.copy(
                    ragTitle = ragInsight.title,
                    ragAnswer = ragInsight.answer,
                    ragSourceTitles = ragInsight.sourceTitles.joinToString(separator = "|"),
                    ragSourceDetails = ragInsight.sourceDetails.joinToString(separator = "|") { it.serialize() },
                    ragProvider = ragResult.provider,
                    ragModel = ragResult.model,
                    ragLatencyMillis = ragResult.latencyMillis,
                    ragUsedFallback = ragResult.usedFallback,
                    ragFallbackReason = ragResult.fallbackReason
                )
                repository.saveSession(sessionWithRagResults)
                _currentSession.value = null
                _ragGenerationUiState.value = RagGenerationUiState(
                    isGenerating = false,
                    message = if (ragResult.usedFallback) {
                        _appLanguage.value.text(TextKey.SESSION_SAVE_FALLBACK)
                    } else {
                        _appLanguage.value.text(TextKey.SESSION_SAVE_SUCCESS, ragResult.provider, ragResult.model)
                    }
                )
                onSaved()
            }
        }
    }

    fun updateRagGenerationSettings(
        mode: RagGenerationMode,
        useRemoteRag: Boolean,
        remoteRagBaseUrl: String,
        useOllama: Boolean,
        ollamaBaseUrl: String,
        ollamaModel: String,
        ollamaApiKey: String
    ) {
        DebugRagGenerationSettings.update(
            mode = mode,
            useRemoteRag = useRemoteRag,
            remoteRagBaseUrl = remoteRagBaseUrl,
            useOllama = useOllama,
            ollamaBaseUrl = ollamaBaseUrl,
            ollamaModel = ollamaModel,
            ollamaApiKey = ollamaApiKey
        )
        appPreferences.saveRagSettings(DebugRagGenerationSettings.settings.value)
    }

    fun updateLanguage(language: AppLanguage) {
        _appLanguage.value = language
        appPreferences.saveLanguage(language)
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun updateSession(session: Session) {
        viewModelScope.launch {
            repository.updateSession(session)
        }
    }

    fun updateUserName(newName: String) {
        currentUserName = newName
        Log.d("DEBUG_VM", "Nom d'usuari actualitzat a: $newName")
    }

    private suspend fun syncUserSessions(userId: String) {
        repository.syncRemoteSessions(userId)
        repository.syncPendingSessions(userId)
    }

    private fun authValidationError(language: AppLanguage): String = when (language) {
        AppLanguage.CATALAN -> "Introdueix un email valid i una contrasenya de 6 caracters o mes."
        AppLanguage.ENGLISH -> "Enter a valid email and a password of at least 6 characters."
        AppLanguage.SPANISH -> "Introduce un email valido y una contrasena de 6 caracteres o mas."
        AppLanguage.CHINESE -> "\u8bf7\u8f93\u5165\u6709\u6548\u90ae\u7bb1\u548c\u81f3\u5c11 6 \u4e2a\u5b57\u7b26\u7684\u5bc6\u7801\u3002"
    }

    private fun userUnavailableError(language: AppLanguage): String = when (language) {
        AppLanguage.CATALAN -> "Usuari no disponible"
        AppLanguage.ENGLISH -> "User unavailable"
        AppLanguage.SPANISH -> "Usuario no disponible"
        AppLanguage.CHINESE -> "\u7528\u6237\u4e0d\u53ef\u7528"
    }

    private fun signInError(language: AppLanguage): String = when (language) {
        AppLanguage.CATALAN -> "No s'ha pogut iniciar sessio."
        AppLanguage.ENGLISH -> "Could not sign in."
        AppLanguage.SPANISH -> "No se ha podido iniciar sesion."
        AppLanguage.CHINESE -> "\u65e0\u6cd5\u767b\u5f55\u3002"
    }

    private fun RagSourceDetail.serialize(): String {
        return listOf(id, source, category, chunkId.toString(), String.format(Locale.US, "%.4f", score), text.take(220))
            .joinToString("~") { value ->
                value.replace("%", "%25")
                    .replace("|", "%7C")
                    .replace("~", "%7E")
                    .replace("\n", " ")
            }
    }
}

data class RagGenerationUiState(
    val isGenerating: Boolean = false,
    val message: String? = null
)

class MainViewModelFactory(
    private val repository: SessionRepository,
    private val locationProvider: LocationProvider,
    private val auth: FirebaseAuth,
    private val appPreferences: AppPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, locationProvider, auth, appPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
