package com.mew.animemew.data.airing

import android.content.Context
import android.util.Log
import com.mew.animemew.data.AnimeRepository
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.local.WatchHistoryEntity
import com.mew.animemew.scraper.EpisodeResolver
import com.mew.animemew.scraper.ServerInfo

// =========================================================
//  AiringController v3 — Controlador de "En espera"
//
//  FILOSOFÍA:
//  NUNCA marcamos un anime como "Visto" automáticamente solo
//  porque AniList diga FINISHED. El usuario debe ver el episodio.
//
//  FLUJO:
//
//  1. Usuario ve último ep (80%+) → markAsWaiting()
//     → isAiring=true, progressMs=0
//     → nextEpisodeTimestamp = airingAt + 3h (si AniList da fecha)
//     → waitingSinceTimestamp = now (para tracking)
//
//  2. Verificación (al abrir app / cada 15 min WorkManager):
//     Para cada anime en espera:
//
//     A) Si nextTs > 0 Y currentTime < nextTs:
//        → Aún no toca, mantener en espera
//
//     B) Si nextTs > 0 Y currentTime >= nextTs:
//        → Consultar AniList
//        B1) status == FINISHED:
//            - Si history.episodeNumber == AniList.episodes:
//              → Ya vimos el último episodio real → marcar Visto ✅
//            - Si history.episodeNumber < AniList.episodes:
//              → Hay más episodios → verificar disponibilidad
//              → Si disponible → habilitar
//              → Si no → reintentar en 1h
//        B2) status == RELEASING:
//            - Si tiene nextAiringEpisode:
//              → Actualizar nextTs (por si cambió)
//              → Si currentTime >= airingAt + 3h → verificar disponibilidad
//              → Si no → mantener en espera
//            - Si NO tiene nextAiringEpisode:
//              → Verificar disponibilidad del siguiente ep
//              → Si disponible → habilitar
//              → Si no → reintentar en 1h
//
//     C) Si nextTs == null o 0 (sin fecha):
//        → Consultar AniList
//        → Mismo flujo que B
//
//  3. Verificación de disponibilidad:
//     Usa EpisodeResolver que busca EN PARALELO en jkanime y tioanime.
//     Tan pronto como un scraper devuelve un streamUrl, se considera disponible.
//
//  IMPORTANTE: NUNCA marcamos como "Visto" si el episodio
//  no se ha visto realmente. Solo marcamos "Visto" cuando:
//  - AniList dice FINISHED Y
//  - history.episodeNumber == AniList.episodes (vimos el último)
// =========================================================

class AiringController private constructor(
    private val context: Context,
    private val animeRepository: AnimeRepository,
    private val episodeResolver: EpisodeResolver
) {
    private val dao = AnimeDatabase.getDatabase(context).animeDao()
    private val TAG = "AiringController"

    companion object {
        @Volatile
        private var INSTANCE: AiringController? = null

        fun getInstance(context: Context): AiringController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiringController(
                    context.applicationContext,
                    AnimeRepository(),
                    EpisodeResolver()
                ).also { INSTANCE = it }
            }
        }

        // 2 horas en segundos — buffer para Colombia + tiempo de scraping
        // (ajustado: con 3h era demasiado, los scrapers suelen tener el ep en 1-2h)
        const val BUFFER_SECONDS = 2 * 60 * 60L

        // 1 hora en segundos — reintento cuando el episodio no está disponible
        const val RETRY_SECONDS = 60 * 60L

        // 80% requerido para marcar como visto/terminado
        const val WATCHED_THRESHOLD = 0.8f
    }

    /**
     * Verifica TODOS los animes en "En espera".
     * @return número de animes actualizados
     */
    suspend fun checkAllWaiting(): Int {
        val waitingAnimes = try {
            dao.getAiringWatchHistory()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo animes en espera: ${e.message}")
            return 0
        }

        if (waitingAnimes.isEmpty()) {
            Log.i(TAG, "No hay animes en espera")
            return 0
        }

        Log.i(TAG, "=== Verificando ${waitingAnimes.size} animes en espera ===")
        var updatedCount = 0
        val currentTimeSec = System.currentTimeMillis() / 1000

        for (history in waitingAnimes) {
            try {
                val wasUpdated = checkOneWaiting(history, currentTimeSec)
                if (wasUpdated) updatedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando ${history.title}: ${e.message}")
            }
        }

        Log.i(TAG, "=== Verificación completada: $updatedCount actualizados ===")
        return updatedCount
    }

    /**
     * Verifica un solo anime en espera.
     * @return true si fue actualizado
     */
    private suspend fun checkOneWaiting(history: WatchHistoryEntity, currentTimeSec: Long): Boolean {
        // FIX 1: Detectar estado buggy donde episodeNumber > totalEpisodes
        if (history.episodeNumber > history.totalEpisodes && history.totalEpisodes > 0) {
            Log.w(TAG, "${history.title}: estado buggy (ep=${history.episodeNumber} > total=${history.totalEpisodes}), corrigiendo...")
            val correctEp = history.totalEpisodes
            dao.insertWatchHistory(history.copy(
                episodeNumber = correctEp,
                progressMs = 0L,
                durationMs = 0L,
                isAiring = true,
                nextEpisodeTimestamp = null,
                waitingSinceTimestamp = null,
                timestamp = System.currentTimeMillis()
            ))
            Log.i(TAG, "✅ ${history.title}: corregido a E$correctEp, re-verificando...")
            val corrected = dao.getWatchHistoryForAnime(history.animeSlug)
            if (corrected != null) {
                return checkOneWaiting(corrected, currentTimeSec)
            }
            return false
        }

        // FIX 2: Detectar estado buggy donde totalEpisodes fue inflado
        // (ej: totalEpisodes=24 de AniList pero jkanime solo tiene 13).
        // Síntomas: isAiring=true, progressMs=0, waitingSinceTimestamp=null,
        // Y episodeNumber < totalEpisodes (significa que "avanzó" sin ver el ep).
        // Esto NO debería pasar normalmente:
        // - Si está en espera → waitingSinceTimestamp != null
        // - Si el ep fue habilitado → episodeNumber == totalEpisodes (ambos actualizados)
        // - Si está viendo → progressMs > 0
        // Así que si episodeNumber < totalEpisodes Y progressMs=0 Y sin waiting,
        // algo está mal. Verificar disponibilidad del ep actual.
        if (history.isAiring && history.progressMs == 0L && history.waitingSinceTimestamp == null
            && history.episodeNumber > 1 && history.episodeNumber < history.totalEpisodes) {
            Log.w(TAG, "${history.title}: estado inconsistente (E${history.episodeNumber} < total=${history.totalEpisodes}, progress=0, sin waiting), verificando...")
            val isCurrentAvailable = checkEpisodeAvailable(history.animeSlug, history.episodeNumber, history.title)
            if (!isCurrentAvailable) {
                // El episodio actual no existe. Buscar el último disponible.
                Log.w(TAG, "${history.title}: E${history.episodeNumber} no disponible, buscando último ep válido...")
                var probeEp = history.episodeNumber - 1
                while (probeEp >= 1) {
                    val isAvail = checkEpisodeAvailable(history.animeSlug, probeEp, history.title)
                    if (isAvail) {
                        Log.i(TAG, "✅ ${history.title}: último ep disponible = E$probeEp, corrigiendo...")
                        val fixedHistory = history.copy(
                            episodeNumber = probeEp,
                            totalEpisodes = probeEp,
                            progressMs = 0L,
                            durationMs = 0L,
                            isAiring = true,
                            nextEpisodeTimestamp = null,
                            waitingSinceTimestamp = currentTimeSec,
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertWatchHistory(fixedHistory)
                        Log.i(TAG, "✅ ${history.title}: corregido a E$probeEp en espera")
                        return true
                    }
                    probeEp--
                }
                Log.w(TAG, "${history.title}: ningún ep disponible, manteniendo estado")
            } else {
                Log.i(TAG, "${history.title}: E${history.episodeNumber} está disponible, continuando verificación normal")
            }
        }

        val nextTs = history.nextEpisodeTimestamp ?: 0L

        // CASO A: Hay fecha Y aún no se ha cumplido → esperar
        if (nextTs > 0L && currentTimeSec < nextTs) {
            val remaining = nextTs - currentTimeSec
            val hoursLeft = remaining / 3600
            val minsLeft = (remaining % 3600) / 60
            Log.i(TAG, "${history.title}: en espera, faltan ${hoursLeft}h ${minsLeft}m")
            return false
        }

        // CASO B: Se cumplió la fecha (o no había fecha) → consultar AniList
        Log.i(TAG, "${history.title}: fecha cumplida o sin fecha, consultando AniList...")

        if (history.anilistId <= 0) {
            Log.w(TAG, "${history.title}: sin anilistId, no se puede verificar")
            return false
        }

        val media = try {
            animeRepository.getAnimeDetails(history.anilistId)
        } catch (e: Exception) {
            Log.e(TAG, "${history.title}: error consultando AniList: ${e.message}")
            return false
        }

        if (media == null) {
            Log.w(TAG, "${history.title}: AniList no devolvió datos")
            return false
        }

        val status = media.status?.name
        val anilistTotalEps = media.episodes ?: 0
        Log.i(TAG, "${history.title}: AniList status=$status, episodes=$anilistTotalEps, " +
                "nosotros vimos E${history.episodeNumber}/${history.totalEpisodes}")

        // =====================================================
        //  CASO B1: AniList dice FINISHED
        // =====================================================
        if (status == "FINISHED") {
            // ¿Ya vimos el último episodio real?
            if (anilistTotalEps > 0 && history.episodeNumber >= anilistTotalEps) {
                // Sí, vimos el último → marcar como Visto
                Log.i(TAG, "✅ ${history.title}: vimos el último episodio (E${history.episodeNumber} = $anilistTotalEps de AniList) → Visto")
                markAsFinished(history)
                return true
            }

            // No hemos visto el último → hay más episodios
            val nextEp = history.episodeNumber + 1
            Log.i(TAG, "${history.title}: FINISHED pero faltan eps (vimos ${history.episodeNumber}, total AniList=$anilistTotalEps). Verificando E$nextEp...")

            val isAvailable = checkEpisodeAvailable(history.animeSlug, nextEp, history.title)
            if (isAvailable) {
                enableNextEpisode(history, nextEp, null)
                Log.i(TAG, "✅ ${history.title}: E$nextEp habilitado (anime finalizado pero hay más eps)")
                return true
            } else {
                // No disponible aún, reintentar en 1h
                val retryTs = currentTimeSec + RETRY_SECONDS
                dao.insertWatchHistory(history.copy(
                    nextEpisodeTimestamp = retryTs,
                    timestamp = System.currentTimeMillis()
                ))
                Log.i(TAG, "${history.title}: E$nextEp aún no disponible, reintento en 1h")
                return false
            }
        }

        // =====================================================
        //  CASO B2: AniList dice RELEASING (o NOT_YET_RELEASED, etc.)
        // =====================================================
        val nextAiringEp = media.nextAiringEpisode
        val airingAt = nextAiringEp?.airingAt?.toLong() ?: 0L
        val airingEpNum = nextAiringEp?.episode

        if (airingEpNum != null && airingAt > 0L) {
            // AniList tiene info del próximo episodio
            val effectiveTs = airingAt + BUFFER_SECONDS
            val nextEpExpected = history.episodeNumber + 1

            Log.i(TAG, "${history.title}: AniList dice próximo ep=$airingEpNum en $airingAt (con buffer=$effectiveTs)")

            // Si el próximo ep de AniList es MAYOR al que esperamos,
            // significa que nos quedamos atrás (gap)
            if (airingEpNum > nextEpExpected) {
                Log.w(TAG, "${history.title}: gap (esperado=$nextEpExpected, AniList=$airingEpNum)")
                val isAvailable = checkEpisodeAvailable(history.animeSlug, nextEpExpected, history.title)
                if (isAvailable) {
                    enableNextEpisode(history, nextEpExpected, effectiveTs)
                    Log.i(TAG, "✅ ${history.title}: E$nextEpExpected habilitado (gap cerrado)")
                    return true
                } else {
                    // No disponible, reintentar en 1h
                    val retryTs = currentTimeSec + RETRY_SECONDS
                    dao.insertWatchHistory(history.copy(
                        nextEpisodeTimestamp = retryTs,
                        timestamp = System.currentTimeMillis()
                    ))
                    return false
                }
            }

            // Si el próximo ep de AniList es MENOR al que esperamos,
            // algo raro pasó. Verificar disponibilidad del siguiente.
            if (airingEpNum < nextEpExpected) {
                Log.w(TAG, "${history.title}: AniList dice ep $airingEpNum pero esperamos $nextEpExpected")
                val isAvailable = checkEpisodeAvailable(history.animeSlug, nextEpExpected, history.title)
                if (isAvailable) {
                    enableNextEpisode(history, nextEpExpected, effectiveTs)
                    return true
                } else {
                    val retryTs = currentTimeSec + RETRY_SECONDS
                    dao.insertWatchHistory(history.copy(
                        nextEpisodeTimestamp = retryTs,
                        timestamp = System.currentTimeMillis()
                    ))
                    return false
                }
            }

            // airingEpNum == nextEpExpected: es el episodio que esperamos
            // ¿Ya se cumplió el buffer?
            if (currentTimeSec < effectiveTs) {
                // Aún no, mantener en espera
                val remaining = effectiveTs - currentTimeSec
                val hoursLeft = remaining / 3600
                val minsLeft = (remaining % 3600) / 60
                Log.i(TAG, "${history.title}: en espera con buffer, faltan ${hoursLeft}h ${minsLeft}m")

                // Actualizar el timestamp en la BD por si cambió en AniList
                if (history.nextEpisodeTimestamp != effectiveTs) {
                    dao.insertWatchHistory(history.copy(
                        nextEpisodeTimestamp = effectiveTs,
                        timestamp = System.currentTimeMillis()
                    ))
                    Log.i(TAG, "${history.title}: timestamp actualizado a $effectiveTs")
                }
                return false
            }

            // Ya se cumplió el buffer → verificar disponibilidad real
            Log.i(TAG, "${history.title}: buffer cumplido, verificando E$nextEpExpected en scrapers...")
            val isAvailable = checkEpisodeAvailable(history.animeSlug, nextEpExpected, history.title)

            if (isAvailable) {
                enableNextEpisode(history, nextEpExpected, null)
                Log.i(TAG, "✅ ${history.title}: E$nextEpExpected habilitado")
                return true
            } else {
                // No disponible aún. Reintentar en 1h.
                val retryTs = currentTimeSec + RETRY_SECONDS
                dao.insertWatchHistory(history.copy(
                    nextEpisodeTimestamp = retryTs,
                    timestamp = System.currentTimeMillis()
                ))
                Log.i(TAG, "${history.title}: E$nextEpExpected no disponible aún, reintento en 1h")
                return false
            }
        }

        // AniList dice RELEASING pero NO tiene info de próximo episodio.
        // Verificar si el siguiente episodio ya está disponible en scrapers.
        Log.i(TAG, "${history.title}: RELEASING sin info de próximo ep en AniList")
        val nextEp = history.episodeNumber + 1
        val isAvailable = checkEpisodeAvailable(history.animeSlug, nextEp, history.title)

        if (isAvailable) {
            enableNextEpisode(history, nextEp, null)
            Log.i(TAG, "✅ ${history.title}: E$nextEp habilitado (sin info AniList pero disponible)")
            return true
        } else {
            // Reintentar en 1h
            val retryTs = currentTimeSec + RETRY_SECONDS
            dao.insertWatchHistory(history.copy(
                nextEpisodeTimestamp = retryTs,
                timestamp = System.currentTimeMillis()
            ))
            Log.i(TAG, "${history.title}: E$nextEp no disponible, reintento en 1h")
            return false
        }
    }

    /**
     * Verifica si un episodio está disponible en jkanime O tioanime.
     * Usa EpisodeResolver que busca en ambos en paralelo.
     * Tan pronto como un scraper devuelve un streamUrl, se considera disponible.
     */
    private suspend fun checkEpisodeAvailable(slug: String, episode: Int, title: String): Boolean {
        return try {
            Log.i(TAG, "Verificando disponibilidad: $slug E$episode...")
            var foundServer = false

            episodeResolver.resolve(slug, episode, {}, titleForTioAnime = title)
                .collect { server: ServerInfo ->
                    if (server.streamUrl != null && !foundServer) {
                        foundServer = true
                        Log.i(TAG, "  ✅ Servidor disponible: ${server.name}")
                        // Cancelar el flow lo antes posible
                        throw kotlinx.coroutines.CancellationException("Server found, stopping")
                    }
                }
            foundServer
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Esto es esperado cuando encontramos un servidor
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando disponibilidad: ${e.message}")
            false
        }
    }

    /**
     * Habilita el siguiente episodio quitando el estado "En espera".
     * Mantiene isAiring=true porque el anime sigue en emisión.
     */
    private suspend fun enableNextEpisode(history: WatchHistoryEntity, nextEp: Int, newNextTs: Long?) {
        dao.insertWatchHistory(history.copy(
            episodeNumber = nextEp,
            totalEpisodes = maxOf(history.totalEpisodes, nextEp),
            progressMs = 0L,
            durationMs = 0L,
            isAiring = true,
            nextEpisodeTimestamp = newNextTs,
            waitingSinceTimestamp = null,  // ya no estamos esperando
            timestamp = System.currentTimeMillis()
        ))
        Log.i(TAG, "✅ ${history.title}: habilitado E$nextEp (isAiring=true, nextTs=$newNextTs)")
    }

    /**
     * Marca un anime como finalizado (visto).
     * SOLO se llama cuando:
     * - AniList dice FINISHED Y
     * - history.episodeNumber >= AniList.episodes (vimos el último real)
     */
    private suspend fun markAsFinished(history: WatchHistoryEntity) {
        dao.deleteWatchHistory(history.animeSlug)

        if (history.anilistId > 0) {
            try {
                dao.insertAnime(
                    com.mew.animemew.data.local.LocalAnimeEntity(
                        history.anilistId,
                        history.title,
                        history.coverUrl,
                        "TV"
                    )
                )
                dao.insertAnimeIntoList(
                    com.mew.animemew.data.local.AnimeListCrossRef(2, history.anilistId)
                )
                dao.removeAnimeFromListById(3, history.anilistId)
            } catch (e: Exception) {
                Log.e(TAG, "Error marcando como visto: ${e.message}")
            }
        }
        Log.i(TAG, "✅ ${history.title}: marcado como VISTO")
    }

    /**
     * Marca un anime como "En espera" después de ver el último episodio.
     * Llamado por PlayerViewModel cuando se alcanza 80%+ del último ep.
     *
     * @param nextEpisodeTimestamp timestamp del próximo episodio (ya con buffer de 3h) o null si no hay fecha
     */
    suspend fun markAsWaiting(
        history: WatchHistoryEntity,
        nextEpisodeTimestamp: Long?
    ) {
        val effectiveTs = nextEpisodeTimestamp?.takeIf { it > 0L }
        val now = System.currentTimeMillis()
        dao.insertWatchHistory(history.copy(
            progressMs = 0L,
            durationMs = 0L,
            isAiring = true,
            nextEpisodeTimestamp = effectiveTs,
            waitingSinceTimestamp = now / 1000,  // en segundos
            timestamp = now
        ))
        Log.i(TAG, "✅ ${history.title} marcado en espera (nextTs=$effectiveTs, waitingSince=${now/1000})")

        // Asegurar que esté en lista "Viendo" (lista 3)
        if (history.anilistId > 0) {
            try {
                dao.insertAnime(
                    com.mew.animemew.data.local.LocalAnimeEntity(
                        history.anilistId,
                        history.title,
                        history.coverUrl,
                        "TV"
                    )
                )
                dao.insertAnimeIntoList(
                    com.mew.animemew.data.local.AnimeListCrossRef(3, history.anilistId)
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Obtiene el próximo timestamp de emisión desde AniList y le suma el buffer.
     * Devuelve null si el anime no está en emisión o no hay info.
     */
    suspend fun fetchNextEpisodeTimestamp(anilistId: Int): Long? {
        if (anilistId <= 0) return null
        return try {
            val media = animeRepository.getAnimeDetails(anilistId)
            val airingAt = media?.nextAiringEpisode?.airingAt?.toLong() ?: return null
            val status = media.status?.name
            if (status != "RELEASING") {
                Log.i(TAG, "Anime $anilistId no está en emisión (status=$status)")
                return null
            }
            val withBuffer = airingAt + BUFFER_SECONDS
            Log.i(TAG, "AniList nextEp ts=$airingAt, con buffer=$withBuffer")
            withBuffer
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo timestamp de AniList: ${e.message}")
            null
        }
    }
}
