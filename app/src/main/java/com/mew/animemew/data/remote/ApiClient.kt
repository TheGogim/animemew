package com.mew.animemew.data.remote

import com.mew.animemew.data.auth.AuthService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// =========================================================
//  Singleton que configura Retrofit una sola vez.
//
//  ⚠️  CAMBIA BASE_URL por tu dominio real de DuckDNS.
//      Debe terminar con `/` (slash final).
// =========================================================

object ApiClient {

    // 🔧 Cambia esto por tu dominio real:
    private const val BASE_URL = "https://animemew-api.duckdns.org/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // BASIC = no loguea bodies (mejor para producción).
        // Si necesitas debuggear, cambia a Level.BODY.
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // snapshots pueden tardar
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authService: AuthService = retrofit.create(AuthService::class.java)
}
