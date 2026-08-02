package com.mew.animemew.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.auth.AuthRepository
import com.mew.animemew.data.auth.SessionInfo
import com.mew.animemew.data.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =========================================================
//  Estado de UI para AuthScreen (login + register).
//  - Idle:    inicial, sin acción en curso
//  - Loading: petición en vuelo (mostrar spinner)
//  - Success: login/register OK (navegar atrás)
//  - Error:   mostrar mensaje en rojo
// =========================================================

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    val session: StateFlow<SessionInfo> = repository.session

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // =====================================================
    //  LOGIN
    // =====================================================
    fun login(email: String, password: String) {
        if (!validateEmail(email)) {
            _uiState.value = AuthUiState.Error("Email inválido")
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error("Ingresa tu contraseña")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(email, password)
            _uiState.value = if (result.isSuccess) {
                // NUEVO: Tras login exitoso, obtener adsEnabled del server
                repository.fetchUserInfo()
                AuthUiState.Success
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Error desconocido")
            }
        }
    }

    // =====================================================
    //  REGISTER
    // =====================================================
    fun register(email: String, password: String, confirm: String) {
        if (!validateEmail(email)) {
            _uiState.value = AuthUiState.Error("Email inválido")
            return
        }
        if (password.length < 8) {
            _uiState.value = AuthUiState.Error("La contraseña debe tener al menos 8 caracteres")
            return
        }
        if (password != confirm) {
            _uiState.value = AuthUiState.Error("Las contraseñas no coinciden")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(email, password)
            _uiState.value = if (result.isSuccess) {
                // NUEVO: Tras registro exitoso, obtener adsEnabled del server
                repository.fetchUserInfo()
                AuthUiState.Success
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Error desconocido")
            }
        }
    }

    fun logout() {
        repository.logout()
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun validateEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return false
        // Validación simple: contiene @ y al menos un punto después
        return trimmed.contains("@") && trimmed.substringAfter("@").contains(".")
    }

    companion object {
        fun factory(sessionManager: SessionManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = AuthRepository(sessionManager)
                    return AuthViewModel(repo) as T
                }
            }
    }
}
