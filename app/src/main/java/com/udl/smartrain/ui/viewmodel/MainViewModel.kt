package com.udl.smartrain.ui.viewmodel

import android.content.Context
import android.content.Intent
import java.util.UUID
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.smartrain.data.repository.SessionRepository
import com.udl.smartrain.data.local.LocationProvider
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import com.udl.smartrain.service.TrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val repository: SessionRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _currentSession = MutableStateFlow<Session?>(null)


    var currentUserName by mutableStateOf("Usuari Actual") // Placeholder, pots obtenir-ho de FirebaseAuth o d'una font de dades


    val sessionsHistory = repository.getSessionHistory("usuari_id_actual")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startNewSession(userId: String) {
        // Generem un ID únic i l'assignem a la sessió
        val newSessionId = UUID.randomUUID().toString()

        _currentSession.value = Session(
            id = newSessionId,
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
            Log.e("DEBUG_VM", "Error: _currentSession és NULL! No es pot guardar res.")
        } else {
            Log.d("DEBUG_VM", "Sessió trobada, guardant: ${current.id}")
            viewModelScope.launch {
                repository.saveSession(current)
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
        // Aquí en el futur es podria afegir codi per guardar-ho a Firebase o Room
        Log.d("DEBUG_VM", "Nom d'usuari actualitzat a: $newName")
    }

}

class MainViewModelFactory(
    private val repository: SessionRepository,
    private val locationProvider: LocationProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, locationProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}