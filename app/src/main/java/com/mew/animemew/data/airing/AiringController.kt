package com.mew.animemew.data.airing

import android.content.Context
import android.util.Log
import com.mew.animemew.data.AnimeRepository
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.local.WatchHistoryEntity
import com.mew.animemew.scraper.EpisodeResolver
import com.mew.animemew.scraper.ServerInfo

// =========================================================
//  AiringController v5 — La "Bolsa" de emisión (Fase 3, 2026-09-22)
//
//  CAMBIO PRINCIPAL vs v4:
//  - isAiring ahora significa "este anime está en emisión y lo seguimos"
//    (siempre true para animes en emisión, sin importar si está viendo o esperando)
//  - NUEVO: refreshBolsa() — consulta AniList + jk para tener info fresca
//  - NUEVO: checkAllAiring() — verifica cada anime en emisión según su fecha
//
//  FLUJO DE LA BOLSA:
//
//  1. Usuario ve un episodio de un anime en emisión → saveToHistory(isAiring=true)
//     → El anime entra a la bolsa (watch_history con isAiring=true)
//     → Aparece en "Continuar viendo" Y en "Horarios"
//
//  2. Al abrir la app o cada 15 min (SyncWorker):
//     checkAllAiring() para cada anime con isAiring=true:
//       a. Si anilistNextEpAirAt == 0 → refreshBolsa() para obtener fecha
//       b. Si anilistNextEpAirAt + 1.5h > now → saltar (aún no toca)
//       c. Si anilistNextEpAirAt + 1.5h <= now:
//          - Verificar si ep anilistNextEpNum está en jk
//          - Si SÍ: totalEpisodes = anilistNextEpNum, refresh fecha próximo ep
//          - Si NO: reintentar en 15 min (nextEpisodeTimestamp = now + 15min)
//
//  3. Usuario ve 80%+ del último episodio disponible → markAsWaiting()
//     → waitingSinceTimestamp = now (sigue en la bolsa, isAiring=true)
//     → El fix v4 impide auto-avance hasta que vea el actual
//
//  4. Usuario ve 100% del último → auto-avanza al siguiente (si lo hay)
//     → Si no hay siguiente → markAsWaiting()
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

        // 1.5 horas en segundos — buffer para Colombia + tiempo de scraping
        // (bajamos de 2h a 1.5h según petición del usuario)
        const val BUFFER_SECONDS = 90 * 60L  // 5400 seg = 1.5h

        // 15 minutos en segundos — reintento cuando el episodio no está disponible aún
        const val RETRY_SECONDS = 15 * 60L

        // 80% requerido para marcar como visto/terminado (lo usa PlayerViewModel)
        const val WATCHED_THRESHOLD = 0.8f

        // Máximo de episodios a probar hacia adelante buscando el último disponible.
        const val MAX_EPISODES_TO_PROBE = 50
    }

    // =====================================================
    //  BOLSA: refreshBolsa() — actualiza info de AniList + jk
    // =====================================================

    /**
     * Refresca la "bolsa" para un anime específico.
     * Consulta AniList (status, nextAiringEpisode) y jk (totalEpisodes disponibles).
     *
     * NO toca episodeNumber ni progressMs.
     *
     * Llamado desde:
     * - DetailViewModel.loadAnimeDetails() cuando el usuario entra a detalles
     * - checkAllAiring() cuando anilistNextEpAirAt == 0
     */
    suspend fun refreshBolsa(
        anilistId: Int,
        slug: String,
        title: String,
        coverUrl: String
    ) {
        if (anilistId <= 0 || slug.isBlank()) return

        Log.i(TAG, "🔄 refreshBolsa: $title (anilistId=$anilistId, slug=$slug)")

        // 1. Consultar AniList
        val media = try {
            animeRepository.getAnimeDetails(anilistId)
        } catch (e: Exception) {
            Log.w(TAG, "refreshBolsa: error AniList para $title: ${e.message}")
            null
        }

        val status = media?.status?.name
        val anilistTotalEps = media?.episodes ?: 0
        val nextEpNum = media?.nextAiringEpisode?.episode ?: 0
        val nextEpAirAt = media?.nextAiringEpisode?.airingAt?.toLong() ?: 0L

        Log.i(TAG, "refreshBolsa: $title status=$status, anilistEps=$anilistTotalEps, nextEp=$nextEpNum @ $nextEpAirAt")

        // Si AniList dice que NO está en emisión, no hacer nada (el PlayerViewModel
        // se encarga de marcar como Visto cuando corresponde)
        if (status != "RELEASING") {
            Log.i(TAG, "refreshBolsa: $title no está en emisión (status=$status), omitiendo")
            return
        }

        // 2. Buscar el entry actual en watch_history (si existe)
        val existing = dao.getWatchHistoryForAnime(slug)

        if (existing == null) {
            // El anime no está en watch_history → no lo agregamos aquí.
            // El anime entra a la bolsa solo cuando el usuario ve un episodio
            // (lo maneja PlayerViewModel.saveToHistory).
            Log.i(TAG, "refreshBolsa: $title no está en watch_history, no se agrega (esperar a que el usuario vea un ep)")
            return
        }

        // 3. Buscar el último episodio disponible en jk ahora mismo
        // Solo si tenemos nextEpNum, probamos ese. Si no, salimos.
        var jkTotalEps = existing.totalEpisodes
        if (nextEpNum > 0) {
            val isNextAvailable = checkEpisodeAvailable(slug, nextEpNum, title)
            if (isNextAvailable) {
                // El próximo ep ya está disponible → actualizar totalEpisodes
                jkTotalEps = maxOf(jkTotalEps, nextEpNum)
                Log.i(TAG, "refreshBolsa: $title E$nextEpNum ya disponible en jk, totalEps=$jkTotalEps")
            } else {
                Log.i(TAG, "refreshBolsa: $title E$nextEpNum aún no disponible en jk")
            }
        }

        // 4. Actualizar watch_history con la info fresca
        // NO tocamos episodeNumber ni progressMs
        val now = System.currentTimeMillis()
        val nextTs = if (nextEpAirAt > 0) nextEpAirAt + BUFFER_SECONDS else null

        dao.insertWatchHistory(existing.copy(
            title = title,  // por si cambió
            coverUrl = coverUrl,  // por si cambió
            totalEpisodes = jkTotalEps,
            isAiring = true,  // siempre true para animes en emisión
            anilistNextEpNum = nextEpNum,
            anilistNextEpAirAt = nextEpAirAt,
            nextEpisodeTimestamp = nextTs,
            timestamp = now
        ))

        Log.i(TAG, "✅ refreshBolsa: $title actualizado (totalEps=$jkTotalEps, nextEp=$nextEpNum @ $nextEpAirAt)")
    }

    // =====================================================
    //  Verificación periódica — checkAllAiring()
    // =====================================================

    /**
     * Verifica TODOS los animes en emisión (isAiring=true).
     *
     * Para cada uno:
     * 1. Si anilistNextEpAirAt == 0 → refreshBolsa() para obtener fecha
     * 2. Si anilistNextEpAirAt + 1.5h > now → saltar (aún no toca)
     * 3. Si anilistNextEpAirAt + 1.5h <= now:
     *    - Verificar si ep anilistNextEpNum está en jk
     *    - Si SÍ: totalEpisodes = anilistNextEpNum, refresh fecha próximo ep
     *    - Si NO: reintentar en 15 min
     *
     * @return número de animes actualizados
     */
    suspend fun checkAllAiring(): Int {
        val airingAnimes = try {
            dao.getAiringWatchHistory()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo animes en emisión: ${e.message}")
            return 0
        }

        if (airingAnimes.isEmpty()) {
            Log.i(TAG, "No hay animes en emisión para verificar")
            return 0
        }

        Log.i(TAG, "=== Verificando ${airingAnimes.size} animes en emisión ===")
        var updatedCount = 0
        val currentTimeSec = System.currentTimeMillis() / 1000

        for (history in airingAnimes) {
            try {
                val wasUpdated = checkOneAiring(history, currentTimeSec)
                if (wasUpdated) updatedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando ${history.title}: ${e.message}")
            }
        }

        Log.i(TAG, "=== Verificación completada: $updatedCount actualizados ===")
        return updatedCount
    }

    /**
     * Alias de checkAllAiring() para compatibilidad con SyncWorker/HomeViewModel
     * que aún llaman checkAllWaiting().
     */
    suspend fun checkAllWaiting(): Int = checkAllAiring()

    /**
     * Verifica un solo anime en emisión.
     *
     * NUEVO v15: si epEsperado != 0 (el usuario está en "En espera" formal
     * esperando un episodio específico), SOLO verificamos ese episodio en jk.
     * Si está disponible → episodeNumber = epEsperado, progressMs = 0, epEsperado = 0.
     *
     * Si epEsperado == 0 (no está en espera, solo siguiendo), usamos la
     * lógica anterior basada en anilistNextEpAirAt + buffer.
     */
    private suspend fun checkOneAiring(history: WatchHistoryEntity, currentTimeSec: Long): Boolean {
        Log.i(TAG, "→ Verificando: ${history.title} (ep=${history.episodeNumber}, total=${history.totalEpisodes}, epEsperado=${history.epEsperado}, nextAnilistEp=${history.anilistNextEpNum} @ ${history.anilistNextEpAirAt})")

        // =====================================================
        // CASO ESPECIAL: epEsperado != 0 (usuario en "En espera" formal)
        // =====================================================
        // El usuario está esperando un episodio específico (ej: el 12).
        // Verificamos ESE episodio en jk sin importar si salieron 12, 13, 14.
        // Cuando lo encontremos → episodeNumber = epEsperado, progressMs = 0,
        // epEsperado = 0 (ya no estamos esperando).
        if (history.epEsperado > 0) {
            Log.i(TAG, "${history.title}: en espera del E${history.epEsperado}, verificando en jk...")

            // Primero: ¿ya se cumplió la fecha de AniList + buffer?
            // Si todavía no, no hacer scraping innecesario.
            if (history.anilistNextEpAirAt > 0) {
                val effectiveTs = history.anilistNextEpAirAt + BUFFER_SECONDS
                if (currentTimeSec < effectiveTs) {
                    val remaining = effectiveTs - currentTimeSec
                    val hoursLeft = remaining / 3600
                    val minsLeft = (remaining % 3600) / 60
                    Log.i(TAG, "${history.title}: en espera, faltan ${hoursLeft}h ${minsLeft}m para E${history.epEsperado}")
                    return false
                }
            }

            // Ya se cumplió la fecha (o no había fecha) → verificar jk
            val isAvailable = checkEpisodeAvailable(history.animeSlug, history.epEsperado, history.title)

            if (isAvailable) {
                // El episodio esperado ya está disponible en jk.
                // Actualizar episodeNumber al epEsperado, progressMs = 0, epEsperado = 0.
                Log.i(TAG, "✅ ${history.title}: E${history.epEsperado} disponible, habilitando para el usuario...")

                // Buscar el último ep disponible en jk para actualizar totalEpisodes
                // (puede que ya hayan salido 12, 13, 14, pero el usuario va en el 12)
                val lastAvailable = findLastAvailableEpisode(
                    slug = history.animeSlug,
                    title = history.title,
                    startEp = history.epEsperado,
                    maxEp = history.epEsperado + MAX_EPISODES_TO_PROBE
                )
                val newTotalEps = lastAvailable ?: history.epEsperado

                // Consultar AniList para nueva fecha de próximo ep
                val media = try { animeRepository.getAnimeDetails(history.anilistId) } catch (_: Exception) { null }
                val newNextEpNum = media?.nextAiringEpisode?.episode ?: 0
                val newNextEpAirAt = media?.nextAiringEpisode?.airingAt?.toLong() ?: 0L
                val newNextTs = if (newNextEpAirAt > 0) newNextEpAirAt + BUFFER_SECONDS else null

                dao.insertWatchHistory(history.copy(
                    episodeNumber = history.epEsperado,  // AVANZAR al ep esperado
                    progressMs = 0L,
                    durationMs = 0L,
                    totalEpisodes = newTotalEps,
                    isAiring = true,
                    anilistNextEpNum = newNextEpNum,
                    anilistNextEpAirAt = newNextEpAirAt,
                    nextEpisodeTimestamp = newNextTs,
                    waitingSinceTimestamp = null,  // ya no estamos esperando
                    epEsperado = 0,  // NUEVO: ya no esperamos un ep específico
                    timestamp = System.currentTimeMillis()
                ))

                Log.i(TAG, "✅ ${history.title}: habilitado E${history.epEsperado} (totalEps=$newTotalEps, nextEp=$newNextEpNum @ $newNextEpAirAt, epEsperado=0)")
                return true
            } else {
                // El episodio aún no está en jk → reintentar en 15 min
                val retryTs = currentTimeSec + RETRY_SECONDS
                dao.insertWatchHistory(history.copy(
                    nextEpisodeTimestamp = retryTs,
                    timestamp = System.currentTimeMillis()
                ))
                Log.i(TAG, "${history.title}: E${history.epEsperado} aún no disponible, reintentar en 15 min")
                return false
            }
        }

        // =====================================================
        // CASO A: No tenemos fecha de AniList → refreshBolsa para obtenerla
        // =====================================================
        if (history.anilistNextEpAirAt == 0L || history.anilistNextEpNum == 0) {
            Log.i(TAG, "${history.title}: sin fecha de AniList, ejecutando refreshBolsa()")
            refreshBolsa(
                anilistId = history.anilistId,
                slug = history.animeSlug,
                title = history.title,
                coverUrl = history.coverUrl
            )
            return true
        }

        // =====================================================
        // CASO B: Verificar si AniList dice FINISHED (anime terminó emisión)
        // =====================================================
        if (history.anilistId > 0) {
            val media = try {
                animeRepository.getAnimeDetails(history.anilistId)
            } catch (e: Exception) {
                null
            }

            if (media != null) {
                val status = media.status?.name
                val anilistTotalEps = media.episodes ?: 0

                if (status == "FINISHED" && anilistTotalEps > 0 && history.episodeNumber >= anilistTotalEps) {
                    Log.i(TAG, "✅ ${history.title}: AniList dice FINISHED y vimos el último (E${history.episodeNumber} = $anilistTotalEps) → Visto")
                    markAsFinished(history)
                    return true
                }

                // Si AniList ya no tiene nextAiringEpisode, el anime terminó
                if (media.nextAiringEpisode == null && status == "FINISHED") {
                    // Verificar si hay más eps disponibles en jk que no vimos
                    val lastAvailable = findLastAvailableEpisode(
                        slug = history.animeSlug,
                        title = history.title,
                        startEp = history.episodeNumber + 1,
                        maxEp = anilistTotalEps
                    )
                    if (lastAvailable != null && lastAvailable > history.episodeNumber) {
                        dao.insertWatchHistory(history.copy(
                            totalEpisodes = lastAvailable,
                            anilistNextEpAirAt = 0,  // ya no hay más fechas que verificar
                            anilistNextEpNum = 0,
                            timestamp = System.currentTimeMillis()
                        ))
                        Log.i(TAG, "✅ ${history.title}: AniList FINISHED pero jk tiene hasta E$lastAvailable, actualizado")
                        return true
                    } else {
                        // No hay más eps disponibles → limpiar fecha para no volver a verificar
                        dao.insertWatchHistory(history.copy(
                            anilistNextEpAirAt = 0,
                            anilistNextEpNum = 0,
                            nextEpisodeTimestamp = null,
                            timestamp = System.currentTimeMillis()
                        ))
                        Log.i(TAG, "${history.title}: AniList FINISHED, sin más eps en jk, limpiando fecha")
                        return false
                    }
                }
            }
        }

        // =====================================================
        // CASO C: ¿Ya se cumplió la fecha del próximo ep + buffer?
        // =====================================================
        val effectiveTs = history.anilistNextEpAirAt + BUFFER_SECONDS

        if (currentTimeSec < effectiveTs) {
            // Aún no toca verificar
            val remaining = effectiveTs - currentTimeSec
            val hoursLeft = remaining / 3600
            val minsLeft = (remaining % 3600) / 60
            Log.i(TAG, "${history.title}: en seguimiento, faltan ${hoursLeft}h ${minsLeft}m para E${history.anilistNextEpNum}")
            return false
        }

        // =====================================================
        // CASO D: Ya se cumplió la fecha → verificar si el ep está en jk
        // =====================================================
        Log.i(TAG, "${history.title}: fecha cumplida, verificando E${history.anilistNextEpNum} en jk...")

        val isAvailable = checkEpisodeAvailable(history.animeSlug, history.anilistNextEpNum, history.title)

        if (isAvailable) {
            // El episodio ya está disponible en jk
            // 1. Actualizar totalEpisodes
            // 2. Consultar AniList para nueva fecha de próximo ep
            Log.i(TAG, "✅ ${history.title}: E${history.anilistNextEpNum} disponible en jk, actualizando bolsa...")

            val media = try { animeRepository.getAnimeDetails(history.anilistId) } catch (_: Exception) { null }
            val newNextEpNum = media?.nextAiringEpisode?.episode ?: 0
            val newNextEpAirAt = media?.nextAiringEpisode?.airingAt?.toLong() ?: 0L

            val newTotalEps = maxOf(history.totalEpisodes, history.anilistNextEpNum)
            val newNextTs = if (newNextEpAirAt > 0) newNextEpAirAt + BUFFER_SECONDS else null

            dao.insertWatchHistory(history.copy(
                totalEpisodes = newTotalEps,
                anilistNextEpNum = newNextEpNum,
                anilistNextEpAirAt = newNextEpAirAt,
                nextEpisodeTimestamp = newNextTs,
                timestamp = System.currentTimeMillis()
            ))

            Log.i(TAG, "✅ ${history.title}: bolsa actualizada (totalEps=$newTotalEps, nextEp=$newNextEpNum @ $newNextEpAirAt)")
            return true
        } else {
            // El episodio aún no está en jk → reintentar en 15 min
            val retryTs = currentTimeSec + RETRY_SECONDS
            dao.insertWatchHistory(history.copy(
                nextEpisodeTimestamp = retryTs,
                timestamp = System.currentTimeMillis()
            ))
            Log.i(TAG, "${history.title}: E${history.anilistNextEpNum} aún no disponible, reintentar en 15 min")
            return false
        }
    }

    // =====================================================
    //  Helpers de scraping
    // =====================================================

    /**
     * Busca el último episodio disponible en scrapers empezando desde startEp.
     */
    private suspend fun findLastAvailableEpisode(
        slug: String,
        title: String,
        startEp: Int,
        maxEp: Int
    ): Int? {
        if (startEp > maxEp) return null

        if (startEp == maxEp) {
            return if (checkEpisodeAvailable(slug, startEp, title)) startEp else null
        }

        if (checkEpisodeAvailable(slug, maxEp, title)) {
            return maxEp
        }

        var lastFound: Int? = null
        var probe = startEp
        while (probe <= maxEp) {
            val isAvailable = checkEpisodeAvailable(slug, probe, title)
            if (isAvailable) {
                lastFound = probe
                probe += 5
            } else {
                if (lastFound != null) break
                return null
            }
        }

        if (lastFound != null) {
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

    private suspend fun checkEpisodeAvailable(slug: String, episode: Int, title: String): Boolean {
        return try {
            var foundServer = false

            episodeResolver.resolve(slug, episode, {}, titleForTioAnime = title)
                .collect { server: ServerInfo ->
                    if (server.streamUrl != null && !foundServer) {
                        foundServer = true
                        Log.d(TAG, "  ✅ $slug E$episode disponible en ${server.name}")
                        throw kotlinx.coroutines.CancellationException("Server found, stopping")
                    }
                }
            foundServer
        } catch (e: kotlinx.coroutines.CancellationException) {
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando disponibilidad $slug E$episode: ${e.message}")
            false
        }
    }

    // =====================================================
    //  markAsFinished y markAsWaiting (sin cambios funcionales)
    // =====================================================

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
     * Marca un anime como "En espera" después de ver el último episodio disponible.
     * Llamado por PlayerViewModel cuando se alcanza 80%+ del último ep.
     *
     * NO cambia episodeNumber. Resetea progressMs y marca waitingSinceTimestamp.
     * isAiring sigue true (el anime sigue en emisión, sigue en la bolsa).
     *
     * NUEVO v15: setea epEsperado = episodeNumber + 1 para que el AiringController
     * sepa qué episodio específico está esperando el usuario.
     */
    suspend fun markAsWaiting(
        history: WatchHistoryEntity,
        nextEpisodeTimestamp: Long?
    ) {
        val effectiveTs = nextEpisodeTimestamp?.takeIf { it > 0L }
        val now = System.currentTimeMillis()
        val expectedEp = history.episodeNumber + 1  // el episodio que el usuario espera ver
        dao.insertWatchHistory(history.copy(
            progressMs = 0L,
            durationMs = 0L,
            isAiring = true,  // sigue en la bolsa
            nextEpisodeTimestamp = effectiveTs,
            waitingSinceTimestamp = now / 1000,
            hasNewEpisode = false,
            nextAvailableEpisode = history.episodeNumber,
            epEsperado = expectedEp,  // NUEVO: trackear el ep que estamos esperando
            timestamp = now
        ))
        Log.i(TAG, "✅ ${history.title} marcado en espera (ep=${history.episodeNumber}, epEsperado=$expectedEp, nextTs=$effectiveTs, waitingSince=${now/1000})")

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
