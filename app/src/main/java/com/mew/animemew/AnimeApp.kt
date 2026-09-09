package com.mew.animemew

import android.app.Application
import android.content.Context
import android.util.Log
import com.mew.animemew.util.GlobalErrorHandler

class AnimeApp : Application() {

    companion object {
        private const val TAG = "AnimeApp"

        /**
         * Contexto de aplicación (no de Activity).
         * Lo usan componentes que necesitan crear Views en background,
         * como el WebViewResolver.
         */
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // NUEVO Fase 1: instalar el handler global de crashes.
        // Cualquier excepción no capturada será interceptada y mostrada
        // al usuario en un overlay (CrashOverlay) con opción de copiar
        // el stacktrace para reportarlo.
        try {
            GlobalErrorHandler.install()
            Log.i(TAG, "GlobalErrorHandler instalado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error instalando GlobalErrorHandler", e)
        }
    }
}
