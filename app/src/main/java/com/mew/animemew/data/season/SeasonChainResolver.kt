package com.mew.animemew.data.season

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mew.animemew.data.AnimeRepository
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.graphql.GetAnimeDetailsQuery
import com.mew.animemew.scraper.ScraperRepository

// =========================================================
//  SeasonChainResolver — arma la cadena de temporadas
//  caminando PREQUEL/SEQUEL desde AniList.
//
//  Reglas:
//  - Incluye TODAS las relaciones PREQUEL y SEQUEL
//  - isMainSeason = true solo para TV y TV_SHORT
//  - Solo las main seasons llevan número (T1, T2, T3...)
//  - OVA/MOVIE/SPECIAL/ONA/MUSIC se etiquetan por formato
//  - Caché PERMANENTE en Room
//  - Slug resolution LAZY
// =========================================================

class SeasonChainResolver private constructor(
    private val context: Context,
    private val animeRepository: AnimeRepository,
    private val dao: SeasonChainDao
) {
    private val gson = Gson()
    private val TAG = "SeasonChainResolver"

    // Formatos que cuentan como "temporada principal" (numeradas T1, T2...)
    private val MAIN_FORMATS = setOf("TV", "TV_SHORT")

    suspend fun resolve(anilistId: Int): SeasonChain {
        // 1. Caché permanente
        dao.getByRootId(anilistId)?.let { cached ->
            Log.i(TAG, "Cache hit para anilistId=$anilistId")
            dao.touchLastAccessed(anilistId, System.currentTimeMillis())
            return parseChain(cached.chainJson)
        }

        Log.i(TAG, "Cache miss — resolviendo cadena para anilistId=$anilistId")

        // 2. Anime actual
        val current = animeRepository.getAnimeDetails(anilistId)
        if (current == null) {
            Log.w(TAG, "AniList no devolvió datos para $anilistId")
            return SeasonChain(emptyList())
        }

        // 3. Caminar PREQUEL hacia atrás
        val prequels = mutableListOf<SeasonInfo>()
        var cursor: GetAnimeDetailsQuery.Media? = current
        var safety = 0
        while (safety < 20) {
            val c = cursor ?: break
            val prequelEdge = c.relations?.edges?.filterNotNull()
                ?.firstOrNull { it.relationType?.name == "PREQUEL" }
            val prequelNode = prequelEdge?.node ?: break

            val info = buildSeasonInfo(prequelNode.id,
                prequelNode.title?.romaji ?: prequelNode.title?.english ?: "Unknown",
                prequelNode.coverImage?.large ?: "",
                prequelNode.episodes,           // nullable → 0
                prequelNode.format?.name ?: "Unknown",
                prequelNode.status?.name)      // NUEVO
            prequels.add(0, info)  // T1 al inicio
            Log.i(TAG, "Prequel: ${info.title} (${info.format})")

            cursor = animeRepository.getAnimeDetails(prequelNode.id)
            safety++
        }

        // 4. Caminar SEQUEL hacia adelante
        val sequels = mutableListOf<SeasonInfo>()
        cursor = current
        safety = 0
        while (safety < 20) {
            val c = cursor ?: break
            val sequelEdge = c.relations?.edges?.filterNotNull()
                ?.firstOrNull { it.relationType?.name == "SEQUEL" }
            val sequelNode = sequelEdge?.node ?: break

            val info = buildSeasonInfo(sequelNode.id,
                sequelNode.title?.romaji ?: sequelNode.title?.english ?: "Unknown",
                sequelNode.coverImage?.large ?: "",
                sequelNode.episodes,
                sequelNode.format?.name ?: "Unknown",
                sequelNode.status?.name)        // NUEVO
            sequels.add(info)
            Log.i(TAG, "Sequel: ${info.title} (${info.format})")

            cursor = animeRepository.getAnimeDetails(sequelNode.id)
            safety++
        }

        // 5. Anime actual
        val currentInfo = buildSeasonInfo(current.id,
            current.title?.romaji ?: current.title?.english ?: "Unknown",
            current.coverImage?.large ?: current.coverImage?.extraLarge ?: "",
            current.episodes,
            current.format?.name ?: "Unknown",
            current.status?.name)             // NUEVO

        // 6. Armar cadena cronológica y numerar main seasons
        val fullChain = prequels + currentInfo + sequels
        val numberedChain = numberMainSeasons(fullChain)

        Log.i(TAG, "Cadena armada: ${numberedChain.size} entradas")
        numberedChain.forEach { s ->
            val label = if (s.isMainSeason) "T${s.seasonNumber}" else s.format
            Log.i(TAG, "  $label: ${s.title} (${s.totalEpisodes} eps)")
        }

        // 7. Guardar caché permanente — bajo TODOS los anilistIds de la cadena.
        // Esto es CRÍTICO para que computeEpisodeLabel() funcione tras playNext().
        // Si solo guardamos bajo el rootAnilistId, cuando el player avance a T2
        // y llame getCached(T2_anilistId), no encontrará la cadena y el label
        // caerá al fallback "E1" en vez de "T2 E1".
        val chainJson = gson.toJson(SeasonChain(numberedChain))
        val now = System.currentTimeMillis()
        numberedChain.forEach { season ->
            dao.upsert(SeasonChainEntity(
                rootAnilistId = season.anilistId,
                chainJson = chainJson,
                createdAt = now,
                lastAccessed = now
            ))
        }

        return SeasonChain(numberedChain)
    }

    /**
     * Construye un SeasonInfo SIN número (se asigna después).
     * isMainSeason se calcula aquí; seasonNumber se asigna en numberMainSeasons().
     */
    private fun buildSeasonInfo(
        id: Int, title: String, coverUrl: String,
        episodes: Int?, format: String, status: String? = null
    ): SeasonInfo {
        return SeasonInfo(
            anilistId = id,
            title = title,
            coverUrl = coverUrl,
            totalEpisodes = episodes ?: 0,   // null → 0 (emisión) — jkanime lo confirmará
            format = format,
            isMainSeason = format in MAIN_FORMATS,
            seasonNumber = 0,                 // se asigna en numberMainSeasons()
            status = status ?: "FINISHED"     // NUEVO
        )
    }

    /**
     * Recorre la cadena cronológica y asigna seasonNumber=1,2,3... SOLO a las main seasons.
     * Las extras (OVA/MOVIE/SPECIAL) quedan con seasonNumber=0.
     */
    private fun numberMainSeasons(chain: List<SeasonInfo>): List<SeasonInfo> {
        var counter = 0
        return chain.map { info ->
            if (info.isMainSeason) {
                counter++
                info.copy(seasonNumber = counter)
            } else {
                info  // sin número
            }
        }
    }

    suspend fun getCached(anilistId: Int): SeasonChain? {
        val cached = dao.getByRootId(anilistId) ?: return null
        return parseChain(cached.chainJson)
    }

    /**
     * Resolución LAZY del slug de una temporada.
     * Devuelve slug + totalEpisodes real (de jkanime), o null si no se encuentra.
     */
    suspend fun resolveSlug(season: SeasonInfo): SlugResolutionResult? {
        // FIX: No resolver slug si el anime aún no se ha emitido
        // (status = NOT_YET_RELEASED). No tiene episodios disponibles.
        if (season.status == "NOT_YET_RELEASED") {
            Log.i(TAG, "${season.title}: status=NOT_YET_RELEASED, no se resuelve slug (aún no emitido)")
            return null
        }

        // FIX: No resolver slug si status es CANCELLED
        if (season.status == "CANCELLED") {
            Log.i(TAG, "${season.title}: status=CANCELLED, no se resuelve slug")
            return null
        }

        if (season.slugResolved && season.slug != null) {
            return SlugResolutionResult(season.slug, season.totalEpisodes)
        }

        Log.i(TAG, "Resolución lazy de slug para: ${season.title} (${season.format})")

        // NUEVO: limpiar el título antes de buscar en jkanime.
        // jkanime normaliza los slugs: quita ':', espacios→'-', etc.
        // Ej: "NARUTO: Shippuuden" → "NARUTO Shippuuden" → slug "naruto-shippuden"
        val cleanedTitle = cleanTitleForSearch(season.title)
        Log.i(TAG, "  Título limpiado: '$cleanedTitle' (original: '${season.title}')")

        val animePage = ScraperRepository.getAnimePage(
            anilistId = season.anilistId,
            titleRomaji = cleanedTitle,           // NUEVO: usar título limpio
            titleEnglish = season.title,          // fallback con original
            titleNative = null,
            debugLogger = { msg -> Log.i(TAG, "  Scraper: $msg") }
        )

        if (animePage == null) {
            Log.w(TAG, "Slug NO encontrado para: ${season.title} — será saltado")
            return null
        }

        Log.i(TAG, "Slug encontrado: ${animePage.slug} (${animePage.totalEpisodes} eps)")
        return SlugResolutionResult(animePage.slug, animePage.totalEpisodes)
    }

    /**
     * NUEVO: limpia un título para que coincida mejor con los slugs de jkanime.
     *
     * Reglas:
     * - Quitar ':' (NARUTO: Shippuuden → NARUTO Shippuuden)
     * - Quitar guiones bajos
     * - Reemplazar múltiples espacios por uno solo
     * - Trim
     */
    private fun cleanTitleForSearch(title: String): String {
        return title
            .replace(":", " ")      // NARUTO: Shippuuden → "NARUTO  Shippuuden"
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")  // múltiples espacios → uno
            .trim()
    }

    private fun parseChain(json: String): SeasonChain {
        return try {
            gson.fromJson(json, SeasonChain::class.java) ?: SeasonChain(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando cadena cacheada", e)
            SeasonChain(emptyList())
        }
    }

    data class SlugResolutionResult(val slug: String, val totalEpisodes: Int)

    companion object {
        @Volatile
        private var INSTANCE: SeasonChainResolver? = null

        fun getInstance(context: Context): SeasonChainResolver {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val repo = AnimeRepository()
                val dao = AnimeDatabase.getDatabase(appContext).seasonChainDao()
                SeasonChainResolver(appContext, repo, dao).also { INSTANCE = it }
            }
        }
    }
}
