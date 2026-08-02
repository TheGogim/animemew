package com.mew.animemew.data.season

import androidx.room.Entity
import androidx.room.PrimaryKey

// =========================================================
//  Caché permanente de cadenas de temporada.
//
//  Se guarda 1 fila por anime raíz (el anilistId con el que
//  el usuario entró a detalles). El JSON contiene TODA la
//  cadena (prequels + actual + sequels) ya ordenada.
//
//  Es PERMANENTE: no expira. Se borra solo si el usuario
//  elimina el anime del historial o lo marca como visto.
// =========================================================

@Entity(tableName = "season_chains")
data class SeasonChainEntity(
    @PrimaryKey
    val rootAnilistId: Int,       // ID del anime por donde se entró a detalles
    val chainJson: String,        // JSON serializado de SeasonChain
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis()
)
