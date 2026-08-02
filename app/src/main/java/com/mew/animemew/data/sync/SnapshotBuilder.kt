package com.mew.animemew.data.sync

import androidx.room.withTransaction
import com.google.gson.Gson
import com.mew.animemew.data.local.AnimeDatabase
import java.util.concurrent.ConcurrentHashMap

// =========================================================
//  SnapshotBuilder — empaqueta todo el estado de Room en un
//  JSON (Snapshot) y aplica un Snapshot remoto sobre Room.
//
//  NUEVO: Cuarentena de borrados.
//  Cuando se borra una lista o un historial localmente, se
//  registra aquí. Si el server intenta traerlo de vuelta
//  en un pull, se ignora.
// =========================================================

class SnapshotBuilder(
    private val db: AnimeDatabase
) {
    private val gson = Gson()
    private val dao = db.animeDao()

    // NUEVO: registros de borrados recientes (slug o listId -> timestamp)
    private val deletedHistory = ConcurrentHashMap<String, Long>()
    private val deletedLists = ConcurrentHashMap<Int, Long>()

    /** Llamar cuando se borra un historial localmente. */
    fun registerHistoryDeletion(slug: String) {
        deletedHistory[slug] = System.currentTimeMillis()
    }

    /** Llamar cuando se borra una lista localmente. */
    fun registerListDeletion(listId: Int) {
        deletedLists[listId] = System.currentTimeMillis()
    }

    /** Recolecta todo de Room y devuelve el JSON listo para cifrar. */
    suspend fun build(): String {
        val snapshot = Snapshot(
            schema_version = 1,
            lists = dao.getAllListsOnce(),
            animes = dao.getAllAnimesOnce(),
            cross_refs = dao.getAllCrossRefsOnce(),
            history = dao.getAllWatchHistoryOnce(),
            updated_at = System.currentTimeMillis() / 1000
        )
        return gson.toJson(snapshot)
    }

    /** Aplica un snapshot remoto sobre la DB local (merge, no overwrite). */
    suspend fun apply(remote: Snapshot) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val quarantineTtl = 24 * 60 * 60 * 1000L // 24 horas

            // 1. Lists
            val remoteListIds = remote.lists.map { it.id }.toSet()
            val localLists = dao.getAllListsOnce()

            localLists.forEach { localList ->
                if (localList.id > 3 && localList.id !in remoteListIds) {
                    dao.deleteList(localList)
                }
            }

            remote.lists.forEach { remoteList ->
                if (remoteList.id in 1..3) return@forEach // skip defaults

                // NUEVO: revisar cuarentena
                val deletedTs = deletedLists[remoteList.id]
                if (deletedTs != null && (now - deletedTs) < quarantineTtl) {
                    // Fue borrado recientemente, ignorar la inserción del server
                    return@forEach
                }

                dao.upsertList(remoteList)
            }

            // 2. Animes
            remote.animes.forEach { dao.insertAnime(it) }

            // 3. CrossRef
            remote.cross_refs.forEach { dao.insertAnimeIntoList(it) }

            // 4. History
            remote.history.forEach { remoteEntry ->
                val sanitized = remoteEntry.copy(
                    seasonTitle = remoteEntry.seasonTitle ?: ""
                )

                // NUEVO: revisar cuarentena
                val deletedTs = deletedHistory[sanitized.animeSlug]
                if (deletedTs != null && (now - deletedTs) < quarantineTtl) {
                    // Fue borrado recientemente, ignorar
                    return@forEach
                }

                val local = dao.getWatchHistoryForAnime(sanitized.animeSlug)
                if (local == null) {
                    dao.insertWatchHistory(sanitized)
                } else if (sanitized.timestamp > local.timestamp) {
                    dao.insertWatchHistory(sanitized)
                }
            }
        }
    }

    fun parse(json: String): Snapshot? {
        return try {
            gson.fromJson(json, Snapshot::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
