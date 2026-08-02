package com.mew.animemew.data.auth

import com.google.gson.annotations.SerializedName

// =========================================================
//  Modelos de la API REST de AnimeMew.
//  Cada clase mapea el JSON que envía/recibe el backend.
// =========================================================

// --- AUTH ---

data class RegisterRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("email") val email: String,
    @SerializedName("salt") val salt: String  // hex 32 bytes — para derivar clave AES en Fase 3
)

data class MeResponse(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("email") val email: String,
    @SerializedName("has_snapshot") val hasSnapshot: Boolean,
    @SerializedName("blob_updated_at") val blobUpdatedAt: String?,
    @SerializedName("ads_enabled") val adsEnabled: Boolean = true  // NUEVO
)

// --- SYNC (se usarán en Fase 3, los dejamos definidos ya) ---

data class SnapshotUpload(
    @SerializedName("encrypted_blob") val encryptedBlob: String,  // base64
    @SerializedName("schema_version") val schemaVersion: Int = 1
)

data class SnapshotDownload(
    @SerializedName("encrypted_blob") val encryptedBlob: String?,  // base64 o null
    @SerializedName("schema_version") val schemaVersion: Int,
    @SerializedName("updated_at") val updatedAt: String?
)

data class UploadResponse(
    val ok: Boolean,
    @SerializedName("updated_at") val updatedAt: String
)
