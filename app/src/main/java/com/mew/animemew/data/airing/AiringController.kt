package com.mew.animemew.data.airing

import android.content.Context
import android.util.Log
import com.mew.animemew.data.AnimeRepository
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.local.WatchHistoryEntity
import com.mew.animemew.scraper.EpisodeResolver
import com.mew.animemew.scraper.ServerInfo

// =========================================================
//  AiringController v4 — Rediseño completo (Fase 2, 2026-09-09)
//
//  PROBLEMA ANTERIOR (v3):
//    El AiringController auto-avanzaba episodeNumber en background
//    cuando encontraba un episodio nuevo disponible en jkanime.
//    Si el usuario dejaba un anime en ep 2 y volvía en 3 semanas,
//    la app decía "Continuar Ep 5" en vez de "Continuar Ep 2".
//
//  FILOSOFÍA NUEVA (v4):
//    El AiringController NUNCA toca episodeNumber. Solo:
//      1. Calcula cuál es el episodio más alto disponible en scrapers
//      2. Lo guarda en nextAvailableEpisode
//      3. Marca hasNewEpisode = true si nextAvailableEpisode > episodeNumber
//
//    El usuario es el único que cambia episodeNumber (viendo un episodio).
//
//  REGLAS DE ESTADO (ver WatchHistoryEntity para el modelo completo):
//
//    "Viendo E{N}" → episodeNumber=N, progressMs>0, isAiring=false
//    "En espera"   → episodeNumber=N (último visto), progressMs=0,
//                    isAiring=true, waitingSinceTimestamp=now,
//                    nextAvailableEpisode<=N (no hay nuevos)
//    "Continuar E{N} + badge nuevos eps" → episodeNumber=N, progressMs=0,
//                    isAiring=true, waitingSinceTimestamp=now,
//                    nextAvailableEpisode>N, hasNewEpisode=true
//    "Visto"       → eliminado de watch_history, movido a lista 2 (Vistos)
//
//  FLUJO DEL AiringController.checkAllWaiting():
//    Para cada anime en watch_history donde isAiring=true:
//      1. Si AniList dice FINISHED y vimos el último episodio real →
//         marcar como Visto (eliminar de watch_history, agregar a lista 2)
//      2. Si no, buscar el último episodio disponible en jkanime:
//         - Si es > episodeNumber → marcar hasNewEpisode=true, nextAvailableEpisode=ultimo
//         - Si es <= episodeNumber → hasNewEpisode=false, sin cambios
//
//  IMPORTANTE: el SyncWorker sigue llamando checkAllWaiting() cada 15 min.
//  Solo se procesan animes con isAiring=true (los que el usuario está siguiendo).
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
        const val BUFFER_SECONDS = 2 * 60 * 60L

        // 1 hora en segundos — reintento cuando no podemos verificar disponibilidad
        const val RETRY_SECONDS = 60 * 60L

        // 80% requerido para marcar como visto/terminado (lo usa PlayerViewModel)
        const val WATCHED_THRESHOLD = 0.8f

        // Máximo de episodios a probar hacia adelante buscando el último disponible.
        // Si AniList dice 24 eps y el usuario va en ep 2, probamos hasta ep 24.
        // Si no hay info de AniList, probamos hasta episodeNumber + 50 (límite razonable).
        const val MAX_EPISODES_TO_PROBE = 50
    }

    /**
     * Verifica TODOS los animes en "En espera" (isAiring=true).
     *
     * IMPORTANTE: NO toca episodeNumber de ningún anime.
     * Solo actualiza nextAvailableEpisode y hasNewEpisode.
     *
     * @return número de animes que tuvieron cambios
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

        for (history in waitingAnimes) {
            try {
                val wasUpdated = checkOneWaiting(history)
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
     *
     * NO cambia episodeNumber. Solo actualiza:
     *   - nextAvailableEpisode (el episodio más alto disponible en scrapers)
     *   - hasNewEpisode (true si hay nuevos eps que el usuario no vio)
     *
     * CASOS:
     *   A) AniList dice FINISHED y vimos el último → marcar como Visto
     *   B) AniList dice FINISHED pero hay más eps que vimos → buscar último disponible
     *   C) AniList dice RELEASING → buscar último disponible en scrapers
     *   D) Sin info de AniList → buscar último disponible en scrapers
     *
     * @return true si fue actualizado
     */
    private suspend fun checkOneWaiting(history: WatchHistoryEntity): Boolean {
        Log.i(TAG, "→ Verificando: ${history.title} (ep=${history.episodeNumber}, total=${history.totalEpisodes})")

        // =====================================================
        // CASO A: AniList dice FINISHED y vimos el último episodio real
        // =====================================================
        if (history.anilistId > 0) {
            val media = try {
                animeRepository.getAnimeDetails(history.anilistId)
            } catch (e: Exception) {
                Log.w(TAG, "${history.title}: no se pudo consultar AniList: ${e.message}, continuando con scrapers")
                null
            }

            if (media != null) {
                val status = media.status?.name
                val anilistTotalEps = media.episodes ?: 0
                Log.i(TAG, "${history.title}: AniList status=$status, episodes=$anilistTotalEps (nosotros vimos E${history.episodeNumber}/${history.totalEpisodes})")

                if (status == "FINISHED" && anilistTotalEps > 0 && history.episodeNumber >= anilistTotalEps) {
                    // Vimos el último episodio real → marcar como Visto
                    Log.i(TAG, "✅ ${history.title}: vimos el último episodio (E${history.episodeNumber} = $anilistTotalEps de AniList) → Visto")
                    markAsFinished(history)
                    return true
                }
            }
        }

        // =====================================================
        // CASOS B/C/D: Buscar el último episodio disponible en scrapers
        // =====================================================
        // Empezamos desde episodeNumber+1 y vamos probando hacia adelante.
        // Si encontramos episodios disponibles, actualizamos nextAvailableEpisode.
        val knownMax = maxOf(history.totalEpisodes, history.nextAvailableEpisode)
        val probeLimit = if (history.anilistId > 0) {
            // Si tenemos AniList, no probar más allá de lo que AniList dice
            val media = try { animeRepository.getAnimeDetails(history.anilistId) } catch (_: Exception) { null }
            val anilistTotal = media?.episodes ?: 0
            if (anilistTotal > 0) anilistTotal else history.episodeNumber + MAX_EPISODES_TO_PROBE
        } else {
            history.episodeNumber + MAX_EPISODES_TO_PROBE
        }

        Log.i(TAG, "${history.title}: buscando último episodio disponible desde E${history.episodeNumber + 1} hasta E$probeLimit (conocido: $knownMax)")

        val lastAvailable = findLastAvailableEpisode(
            slug = history.animeSlug,
            title = history.title,
            startEp = history.episodeNumber + 1,
            maxEp = probeLimit
        )

        Log.i(TAG, "${history.title}: último disponible encontrado = E${lastAvailable ?: history.episodeNumber}")

        // Calcular nuevo estado
        val newNextAvailable = lastAvailable ?: history.episodeNumber
        val newHasNew = newNextAvailable > history.episodeNumber

        if (newNextAvailable != history.nextAvailableEpisode || newHasNew != history.hasNewEpisode) {
            // FIX Fase 5: cuando hay nuevos episodios disponibles (hasNewEpisode=true),
            // el anime ya NO está "en espera" — tiene episodios para ver.
            // Setear isAiring=false para que:
            //   - No vuelva a sumar otro ep cuando salga otro nuevo sin haber visto el anterior
            //   - El AiringController no lo vuelva a verificar hasta que el usuario vea un ep
            //   - La UI muestre "Continuar E{N}" + badge "+X" en vez de "En espera"
            //
            // Si no hay nuevos eps (newHasNew=false), mantener el isAiring que tenía.
            val newIsAiring = if (newHasNew) false else history.isAiring

            dao.insertWatchHistory(history.copy(
                nextAvailableEpisode = newNextAvailable,
                hasNewEpisode = newHasNew,
                isAiring = newIsAiring,
                timestamp = System.currentTimeMillis()
            ))

            if (newHasNew) {
                Log.i(TAG, "✅ ${history.title}: marcados ${newNextAvailable - history.episodeNumber} eps nuevos disponibles (E${history.episodeNumber + 1}-E$newNextAvailable), isAiring=$newIsAiring")
            } else {
                Log.i(TAG, "${history.title}: sin eps nuevos disponibles (sigue en E${history.episodeNumber})")
            }
            return true
        } else {
            Log.i(TAG, "${history.title}: sin cambios")
            return false
        }
    }

    /**
     * Busca el último episodio disponible en scrapers empezando desde startEp.
     *
     * Estrategia:
     *   1. Saltar a startEp + 5 (si hay startEp+5, hay startEp+1, +2, +3, +4 también)
     *   2. Si startEp+5 no está, probar startEp+1, startEp+2, ... linearmente
     *
     * Esto minimiza las llamadas al scraper (probamos ~10 episodios en vez de 50).
     *
     * @return el número del último episodio disponible, o null si startEp no está disponible
     */
    private suspend fun findLastAvailableEpisode(
        slug: String,
        title: String,
        startEp: Int,
        maxEp: Int
    ): Int? {
        if (startEp > maxEp) return null

        // Si solo hay 1 episodio para probar, ir directo
        if (startEp == maxEp) {
            return if (checkEpisodeAvailable(slug, startEp, title)) startEp else null
        }

        // Probar el último primero (binary-search style)
        // Si maxEp está disponible, ese es el último
        if (checkEpisodeAvailable(slug, maxEp, title)) {
            return maxEp
        }

        // Si no está maxEp, buscar linealmente desde startEp hacia adelante
        // hasta encontrar el primer ep que no esté disponible
        var lastFound: Int? = null

        // Optimización: probar en saltos de a 5 primero
        var probe = startEp
        while (probe <= maxEp) {
            val isAvailable = checkEpisodeAvailable(slug, probe, title)
            if (isAvailable) {
                lastFound = probe
                probe += 5  // saltar 5 adelante
            } else {
                // No está disponible en este probe. Si ya teníamos uno, parar.
                if (lastFound != null) break
                // Si no teníamos ninguno, este startEp no está disponible → no hay nada nuevo
                return null
            }
        }

        // Si lastFound está en el futuro lejano, ajustar: probar los intermedios
        // para encontrar el verdadero último disponible
        if (lastFound != null) {
            // Probar linealmente desde lastFound+1 hasta encontrar uno que no esté
            var searchEp = lastFound + 1
            while (searchEp <= maxEp) {
                val isAvailable = checkEpisodeAvailable(slug, searchEp, title)
                if (isAvailable) {
                    lastFound = searchEp
                    searchEp++
                } else {
                    break
                }
            }
        }

        return lastFound
    }

    /**
     * Verifica si un episodio está disponible en jkanime O tioanime.
     * Usa EpisodeResolver que busca en ambos en paralelo.
     * Tan pronto como un scraper devuelve un streamUrl, se considera disponible.
     */
    private suspend fun checkEpisodeAvailable(slug: String, episode: Int, title: String): Boolean {
        return try {
            var foundServer = false

            episodeResolver.resolve(slug, episode, {}, titleForTioAnime = title)
                .collect { server: ServerInfo ->
                    if (server.streamUrl != null && !foundServer) {
                        foundServer = true
                        Log.d(TAG, "  ✅ $slug E$episode disponible en ${server.name}")
                        // Cancelar el flow lo antes posible
                        throw kotlinx.coroutines.CancellationException("Server found, stopping")
                    }
                }
            foundServer
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Esto es esperado cuando encontramos un servidor
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando disponibilidad $slug E$episode: ${e.message}")
            false
        }
    }

    /**
     * Marca un anime como finalizado (visto).
     * SOLO se llama cuando:
     *   - AniList dice FINISHED Y
     *   - history.episodeNumber >= AniList.episodes (vimos el último real)
     *
     * Elimina de watch_history (deja de aparecer en "Continuar viendo")
     * y lo mueve a la lista 2 (Vistos).
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
     * IMPORTANTE: NO cambia episodeNumber (lo dejamos en el último que vimos).
     * Solo resetea progressMs y marca isAiring=true + waitingSinceTimestamp.
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
            hasNewEpisode = false,  // recién entró en espera, no sabemos si hay nuevos
            nextAvailableEpisode = history.episodeNumber,  // por ahora, lo último que sabemos
            timestamp = now
        ))
        Log.i(TAG, "✅ ${history.title} marcado en espera (ep=${history.episodeNumber}, nextTs=$effectiveTs, waitingSince=${now/1000})")

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
