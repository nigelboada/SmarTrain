package com.udl.smartrain.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.smartrain.data.repository.SessionRepository
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession

    val sessionsHistory: StateFlow<List<Session>> = repository.getSessionsStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startNewSession(userId: String) {
        _currentSession.value = Session(userId = userId)
    }

    fun finishAndSaveSession() {
        viewModelScope.launch {
            _currentSession.value?.let {
                repository.saveSession(it)
                _currentSession.value = null
            }
        }
    }
}

class MainViewModelFactory(private val repository: SessionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}