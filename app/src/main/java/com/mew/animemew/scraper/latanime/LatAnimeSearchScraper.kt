package com.mew.animemew.scraper.latanime

import android.util.Log
import com.mew.animemew.scraper.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =========================================================
//  LatAnimeSearchScraper — Busca animes en Latanime.org
//
//  ESTRATEGIA SIMPLE:
//  1. Usar título en inglés de AniList (Latanime usa muchos títulos en inglés)
//  2. Generar slug base sin sufijo de temporada
//  3. Probar variantes: con/sin -latino, con s2/s3/s4, temporada-2, etc.
//  4. Verificar con GET si el slug existe (200 = sí, 404 = no)
//  5. Match exacto, no porcentajes
//
//  Ejemplos reales:
//  - "Black Clover" → black-clover-latino ✅
//  - "Attack on Titan Season 2" → shingeki-no-kyojin-s2-latino ✅ (usa romaji en este caso)
//  - "That Time I Got Reincarnated as a Slime Season 4" → that-time-i-got-reincarnated-as-a-slime-temporada-4 ✅
//  - "Ghost in the Shell" → ghost-in-the-shell-latino ✅
// =========================================================

object LatAnimeSearchScraper {

    private const val TAG = "LatAnimeSearch"
    private const val BASE_URL = "https://latanime.org"

    // Caché en memoria: title → slug
    private val slugCache = mutableMapOf<String, String>()

    /**
     * Busca un anime por título en Latanime.
     *
     * @param titleRomaji Título romaji de AniList
     * @param titleEnglish Título inglés de AniList (puede ser null)
     * @return slug encontrado o null
     */
    suspend fun search(
        titleRomaji: String,
        titleEnglish: String? = null,
        debugLogger: (String) -> Unit = {}
    ): String? {
        return withContext(Dispatchers.IO) {
            // Verificar caché
            val cacheKey = titleRomaji + "|" + (titleEnglish ?: "")
            slugCache[cacheKey]?.let {
                debugLogger("LatAnime: caché hit → $it")
                return@withContext it
            }

            debugLogger("LatAnime: buscando '$titleRomaji' / '$titleEnglish'")

            // Lista de títulos a probar (inglés primero, luego romaji)
            val titles = mutableListOf<String>()
            titleEnglish?.let { if (it.isNotBlank()) titles.add(it) }
            titles.add(titleRomaji)

            for (title in titles) {
                val slug = tryWithTitle(title, debugLogger)
                if (slug != null) {
                    slugCache[cacheKey] = slug
                    Log.i(TAG, "✅ Encontrado: $slug (usando '$title')")
                    return@withContext slug
                }
            }

            debugLogger("LatAnime: ❌ No encontrado")
            return@withContext null
        }
    }

    /**
     * Intenta encontrar un slug usando un título específico.
     */
    private suspend fun tryWithTitle(title: String, debugLogger: (String) -> Unit): String? {
        // Detectar número de temporada
        val seasonNum = detectSeasonNumber(title)
        debugLogger("LatAnime: temporada detectada: $seasonNum")

        // Generar slug base (quitar sufijo de temporada del título)
        val titleWithoutSeason = removeSeasonSuffix(title)
        val baseSlug = generateSlug(titleWithoutSeason)
        debugLogger("LatAnime: slug base: '$baseSlug' (de '$titleWithoutSeason')")

        // Si hay temporada, también probar con el título completo (sin quitar temporada)
        val baseSlugWithSeason = generateSlug(title)

        // Generar todas las variantes a probar
        val variants = generateVariants(baseSlug, seasonNum)

        // Si el slug con temporada es diferente, agregar sus variantes también
        if (baseSlugWithSeason != baseSlug) {
            variants.addAll(generateVariants(baseSlugWithSeason, seasonNum))
        }

        // Probar cada variante
        for (variant in variants) {
            debugLogger("LatAnime: probando '$variant'")
            if (slugExists(variant)) {
                debugLogger("LatAnime: ✅ Encontrado: $variant")
                return variant
            }
        }

        return null
    }

    /**
     * Genera todas las variantes de slug a probar.
     * Orden: primero las más probables.
     */
    private fun generateVariants(baseSlug: String, seasonNum: Int?): MutableList<String> {
        val variants = mutableListOf<String>()

        if (seasonNum != null) {
            // Con temporada - variantes comunes en Latanime
            // Orden de probabilidad (más común primero):
            variants.add("$baseSlug-s$seasonNum-latino")        // shingeki-no-kyojin-s2-latino
            variants.add("$baseSlug-s$seasonNum")               // sin -latino
            variants.add("$baseSlug-temporada-$seasonNum")      // slime-temporada-4
            variants.add("$baseSlug-temporada-$seasonNum-latino")
            variants.add("$baseSlug-$seasonNum-latino")         // solo el número
            variants.add("$baseSlug-season-$seasonNum-latino")
            variants.add("$baseSlug-season-$seasonNum")
            variants.add("$baseSlug-${seasonNum}latino")        // sin guion (raro pero pasa)
        }

        // Sin temporada (o si las variantes con temporada fallaron)
        variants.add("$baseSlug-latino")     // black-clover-latino
        variants.add("$baseSlug")            // sin -latino
        variants.add("$baseSlug-castellano") // variante castellano

        return variants.distinct().toMutableList()
    }

    // =====================================================
    //  Helpers
    // =====================================================

    /**
     * Detecta el número de temporada del título.
     * Ej: "Tensei 4th Season" → 4
     *     "Attack on Titan Season 2" → 2
     *     "Re:Zero 2nd Season" → 2
     *     "Hell Mode 2nd Season" → 2
     */
    private fun detectSeasonNumber(title: String): Int? {
        val lower = title.lowercase()
        // "4th season", "3rd season", "2nd season", "1st season"
        Regex("(\\d)(?:st|nd|rd|th)\\s+season").find(lower)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        // "season 2", "season 4"
        Regex("season\\s+(\\d)").find(lower)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        // "s2", "s4" como palabra separada
        Regex("\\bs(\\d)\\b").find(lower)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        return null
    }

    /**
     * Quita el sufijo de temporada del título.
     * Ej: "Tensei Shitara Slime 4th Season" → "Tensei Shitara Slime"
     *     "Attack on Titan Season 2" → "Attack on Titan"
     */
    private fun removeSeasonSuffix(title: String): String {
        return title
            .replace(Regex("(?i)\\s+\\d+(?:st|nd|rd|th)\\s+season\\s*$"), "")
            .replace(Regex("(?i)\\s+season\\s+\\d+\\s*$"), "")
            .replace(Regex("(?i)\\s+s\\d+\\s*$"), "")
            .trim()
    }

    /**
     * Genera un slug a partir de un título.
     */
    private fun generateSlug(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    /**
     * Verifica si un slug existe en Latanime.
     */
    private suspend fun slugExists(slug: String): Boolean {
        return try {
            val html = HttpClient.get("$BASE_URL/anime/$slug", referer = BASE_URL)
            !html.contains("Página no encontrada") &&
            !html.contains("404 Not Found") &&
            html.contains("og:title")
        } catch (e: Exception) {
            false
        }
    }

    fun clearCache() {
        slugCache.clear()
    }
}
