package com.mew.animemew.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// =========================================================
//  LanguagePreferences — Preferencia de idioma de audio.
//
//  Valores:
//  - "sub" (default) → Subtitulado en español (JKanime/TioAnime)
//  - "lat" → Latino/Castellano (Latanime)
// =========================================================

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_prefs")

class LanguagePreferences(private val context: Context) {

    val languageFlow: Flow<String> = context.languageDataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "sub"  // default: subtitulado
    }

    suspend fun setLanguage(language: String) {
        context.languageDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language
        }
    }

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("audio_language")
    }
}
