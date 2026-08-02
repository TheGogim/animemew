package com.mew.animemew.data.sync

// =========================================================
//  Estado del sync observable por la UI (SyncStatusBadge).
// =========================================================

sealed class SyncState {
    /** No hay sesión activa — no se muestra el badge. */
    object LoggedOut : SyncState()

    /** Sesión activa pero nunca se ha sincronizado. */
    object Never : SyncState()

    /** Sync en curso (pull o push). */
    object Syncing : SyncState()

    /** Última sync exitosa, con timestamp en ms. */
    data class Success(val timestampMs: Long) : SyncState()

    /** Error en la última sync. */
    data class Error(val message: String, val lastSuccessMs: Long? = null) : SyncState()
}
