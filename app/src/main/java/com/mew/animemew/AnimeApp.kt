package com.mew.animemew

import android.app.Application
import android.content.Context

class AnimeApp : Application() {

    companion object {
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
    }
}
