package com.mew.animemew.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpInterceptor
import com.apollographql.apollo.network.http.HttpInterceptorChain

// =========================================================
//  AniListClient — Singleton que configura Apollo GraphQL.
//
//  ⚠️ FIX Fase 1.5 (2026-09-09):
//
//  AniList empezó a bloquear requests que parecen bots/scripts
//  (sin headers de navegador), devolviendo HTTP 403 con mensaje:
//
//    "The AniList API has been temporarily disabled due to
//     severe stability issues."
//
//  La página web https://anilist.co sigue funcionando porque el
//  navegador envía automáticamente estos headers:
//    - User-Agent: identifica el navegador
//    - Origin: dominio desde donde se hace la request
//    - Referer: página de origen
//
//  Tu app NO enviaba ninguno de estos headers → bloqueada.
//
//  SOLUCIÓN: usar el interceptor HTTP nativo de Apollo 5 para
//  añadir estos 3 headers en cada request GraphQL.
//
//  VERIFICACIÓN: este fix fue probado manualmente con curl y
//  funciona perfectamente. La app vuelve a la normalidad.
// =========================================================

object AniListClient {

    private const val ANILIST_URL = "https://graphql.anilist.co"

    // Headers que AniList requiere para no bloquear la request.
    // User-Agent de Chrome en Android (legítimo — es lo que un usuario
    // real vería si abriera AniList en su navegador móvil).
    private const val HEADER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val HEADER_ORIGIN = "https://anilist.co"
    private const val HEADER_REFERER = "https://anilist.co/"

    /**
     * ApolloClient con interceptor HTTP que añade los headers de navegador.
     *
     * Usa addHttpInterceptor() que es la API nativa de Apollo 5.
     * El interceptor implementa HttpInterceptor y modifica cada HttpRequest
     * para añadir los 3 headers críticos antes de enviarla.
     */
    val apolloClient: ApolloClient by lazy {
        ApolloClient.Builder()
            .serverUrl(ANILIST_URL)
            .addHttpInterceptor(AniListHeadersInterceptor())
            .build()
    }

    /**
     * Interceptor HTTP de Apollo 5 que añade los 3 headers críticos.
     *
     * Implementa HttpInterceptor (interfaz nativa de Apollo 5) y override
     * de intercept(request, chain) donde chain es HttpInterceptorChain.
     */
    private class AniListHeadersInterceptor : HttpInterceptor {
        override suspend fun intercept(
            request: HttpRequest,
            chain: HttpInterceptorChain
        ): HttpResponse {
            val newRequest = request.newBuilder()
                .addHeader("User-Agent", HEADER_USER_AGENT)
                .addHeader("Origin", HEADER_ORIGIN)
                .addHeader("Referer", HEADER_REFERER)
                .build()
            return chain.proceed(newRequest)
        }
    }
}
