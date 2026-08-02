package com.mew.animemew.data.auth

import retrofit2.Response
import retrofit2.http.*

// =========================================================
//  Interfaz Retrofit para nuestra API.
//  Los métodos son `suspend` para usarlos desde corrutinas.
//  Usamos `Response<T>` para poder inspeccionar errores HTTP
//  (código, body del error) en el repositorio.
// =========================================================

interface AuthService {

    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun me(
        @Header("Authorization") authHeader: String
    ): Response<MeResponse>

    @GET("sync/snapshot")
    suspend fun getSnapshot(
        @Header("Authorization") authHeader: String
    ): Response<SnapshotDownload>

    @PUT("sync/snapshot")
    suspend fun uploadSnapshot(
        @Header("Authorization") authHeader: String,
        @Body payload: SnapshotUpload
    ): Response<UploadResponse>
}
