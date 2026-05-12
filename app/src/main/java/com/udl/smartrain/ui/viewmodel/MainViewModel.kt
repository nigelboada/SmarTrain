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
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.data.repository.SessionRepository
import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.ml.ActivityRecognitionState
import com.udl.smartrain.ml.SessionRagRecommender
import com.udl.smartrain.service.TrackingService
import com.udl.smartrain.service.TrackingSessionState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _currentSession = MutableStateFlow<Session?>(null)
    private val currentUserId = MutableStateFlow(auth.currentUser?.uid.orEmpty())

    var currentUserName by mutableStateOf(auth.currentUser?.email ?: "Usuari")
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    val isAuthenticated: Boolean
        get() = currentUserId.value.isNotBlank()

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
        auth.currentUser?.uid?.let { userId ->
            viewModelScope.launch {
                syncUserSessions(userId)
            }
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        authenticate(email, password, createAccount = false, onSuccess = onSuccess)
    }

    fun createAccount(email: String, password: String, onSuccess: () -> Unit) {
        authenticate(email, password, createAccount = true, onSuccess = onSuccess)
    }

    private fun authenticate(
        email: String,
        password: String,
        createAccount: Boolean,
        onSuccess: () -> Unit
    ) {
        if (email.isBlank() || password.length < 6) {
            authError = "Introdueix un email valid i una contrasenya de 6 caracters o mes."
            return
        }

        viewModelScope.launch {
            try {
                if (createAccount) {
                    auth.createUserWithEmailAndPassword(email.trim(), password).await()
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password).await()
                }

                val user = auth.currentUser ?: error("Usuari no disponible")
                currentUserId.value = user.uid
                currentUserName = user.email ?: "Usuari"
                authError = null
                syncUserSessions(user.uid)
                onSuccess()
            } catch (e: Exception) {
                authError = e.localizedMessage ?: "No s'ha pogut iniciar sessio."
            }
        }
    }

    fun signOut() {
        auth.signOut()
        currentUserId.value = ""
        currentUserName = "Usuari"
        _currentSession.value = null
        TrackingSessionState.reset()
        ActivityRecognitionState.reset()
    }

    fun startNewSession() {
        val userId = currentUserId.value
        if (userId.isBlank()) {
            Log.e("DEBUG_VM", "No hi ha usuari autenticat per iniciar sessio.")
            return
        }

        _currentSession.value = Session(
            id = UUID.randomUUID().toString(),
            userId = userId
        )
    }

    fun finishAndSaveSession(context: Context) {
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
            val ragInsight = SessionRagRecommender.buildInsight(sessionWithMlResults)
            val sessionWithRagResults = sessionWithMlResults.copy(
                ragTitle = ragInsight.title,
                ragAnswer = ragInsight.answer,
                ragSourceTitles = ragInsight.sourceTitles.joinToString(separator = "|")
            )

            Log.d("DEBUG_VM", "Sessio trobada, guardant: ${sessionWithRagResults.id}")
            viewModelScope.launch {
                repository.saveSession(sessionWithRagResults)
                _currentSession.value = null
            }
        }
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
}

class MainViewModelFactory(
    private val repository: SessionRepository,
    private val locationProvider: LocationProvider,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, locationProvider, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
