package com.mew.animemew.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.local.LanguagePreferences
import com.mew.animemew.data.local.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val themePreferences = ThemePreferences(application)
    private val languagePreferences = LanguagePreferences(application)  // NUEVO

    val currentTheme: StateFlow<String> = themePreferences.themeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "dark"  // NUEVO v9.0: dark por defecto
        )

    // NUEVO: preferencia de idioma ("sub" o "lat")
    val currentLanguage: StateFlow<String> = languagePreferences.languageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "sub"  // default: subtitulado
        )

    fun setTheme(theme: String) {
        viewModelScope.launch {
            themePreferences.saveTheme(theme)
        }
    }

    // NUEVO
    fun setLanguage(language: String) {
        viewModelScope.launch {
            languagePreferences.setLanguage(language)
        }
    }
}
