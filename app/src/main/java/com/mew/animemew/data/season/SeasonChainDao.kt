package com.mew.animemew.data.season

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// =========================================================
//  DAO para el caché de cadenas de temporada.
//
//  NOTA: los métodos @Query con UPDATE/DELETE devuelven Int
//  (número de filas afectadas) en vez de Unit. Esto evita
//  el bug "unexpected jvm signature V" de KSP+Room.
// =========================================================

@Dao
interface SeasonChainDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeasonChainEntity): Long

    @Query("SELECT * FROM season_chains WHERE rootAnilistId = :anilistId")
    suspend fun getByRootId(anilistId: Int): SeasonChainEntity?

    @Query("DELETE FROM season_chains WHERE rootAnilistId = :anilistId")
    suspend fun deleteByRootId(anilistId: Int): Int

    @Query("UPDATE season_chains SET lastAccessed = :timestamp WHERE rootAnilistId = :anilistId")
    suspend fun touchLastAccessed(anilistId: Int, timestamp: Long): Int
}
