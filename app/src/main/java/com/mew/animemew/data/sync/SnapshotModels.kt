package com.mew.animemew.data.sync

import com.mew.animemew.data.local.AnimeListCrossRef
import com.mew.animemew.data.local.AnimeListEntity
import com.mew.animemew.data.local.LocalAnimeEntity
import com.mew.animemew.data.local.WatchHistoryEntity

// =========================================================
//  Snapshot — estructura del JSON que se cifra y se envía
//  al server como "encrypted_blob".
//
//  Al descifrar, Gson reconstruye esta clase automáticamente.
// =========================================================

data class Snapshot(
    val schema_version: Int = 1,
    val lists: List<AnimeListEntity> = emptyList(),
    val animes: List<LocalAnimeEntity> = emptyList(),
    val cross_refs: List<AnimeListCrossRef> = emptyList(),
    val history: List<WatchHistoryEntity> = emptyList(),
    val updated_at: Long = 0  // epoch seconds
)
