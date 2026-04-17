package com.udl.smartrain.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.smartrain.data.repository.SessionRepository
import com.udl.smartrain.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession

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