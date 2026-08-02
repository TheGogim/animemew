package com.mew.animemew.scraper

import android.util.Log

object ScraperRepository {

    private const val TAG = "ScraperRepo"
    private val slugCache = mutableMapOf<Int, String>()

    suspend fun getAnimePage(anilistId: Int, titleRomaji: String?, titleEnglish: String?, titleNative: String?, debugLogger: (String) -> Unit = {}): AnimePage? {
        Log.i(TAG, "getAnimePage: anilistId=$anilistId, romaji='$titleRomaji', english='$titleEnglish'")
        val cachedSlug = slugCache[anilistId]
        if (cachedSlug != null) {
            Log.i(TAG, "Usando slug cacheado: $cachedSlug")
            debugLogger("Usando slug cacheado: $cachedSlug")
            return AnimePageScraper.fetch(cachedSlug, debugLogger)
        }

        debugLogger("Buscando slug para: $titleRomaji | $titleEnglish")
        val slug = findSlugForAnilist(titleRomaji, titleEnglish, titleNative, debugLogger) ?: run {
            Log.w(TAG, "❌ No se encontró slug para: $titleRomaji")
            return null
        }
        
        Log.i(TAG, "Slug encontrado: $slug. Cargando episodios...")
        debugLogger("Slug encontrado: $slug. Procediendo a cargar episodios...")
        val page = AnimePageScraper.fetch(slug, debugLogger)
        if (page != null) {
            Log.i(TAG, "✅ Página cargada: ${page.title} - ${page.totalEpisodes} eps")
            slugCache[anilistId] = slug
        }
        return page
    }

    private suspend fun findSlugForAnilist(titleRomaji: String?, titleEnglish: String?, titleNative: String?, debugLogger: (String) -> Unit): String? {
        val candidates = buildList {
            titleRomaji?.let { add(it) }
            titleEnglish?.let { add(it) }
            titleNative?.let { add(it) }
        }

        // FASE 1: Probar títulos originales
        for (query in candidates) {
            if (query.isBlank()) continue
            Log.i(TAG, "FASE 1: Buscando en Jkanime: '$query'")
            debugLogger("Buscando en Jkanime: '$query'")
            val results = SearchScraper.search(query, debugLogger)
            Log.i(TAG, "  SearchScraper devolvió ${results.size} resultados:")
            results.forEach { r -> Log.i(TAG, "    - slug='${r.slug}' title='${r.title}'") }

            val match = pickBestMatch(query, results, debugLogger)
            if (match != null && slugExists(match.slug, debugLogger)) {
                Log.i(TAG, "✅ Match encontrado válido: ${match.slug} (title='${match.title}')")
                debugLogger("Match encontrado válido: ${match.slug}")
                return match.slug
            } else if (match != null) {
                Log.w(TAG, "Match '${match.slug}' parece inválido o devuelve 404.")
                debugLogger("Match '${match.slug}' parece inválido o devuelve 404.")
            }
        }

        // FASE 2: Probar variantes Part 2 ↔ 2nd Season (para Mushoku Tensei T1 Part 2)
        val variants = generateSuffixVariants(candidates)
        for (query in variants) {
            if (query.isBlank()) continue
            Log.i(TAG, "FASE 2: Buscando variante: '$query'")
            debugLogger("Buscando variante: '$query'")
            val results = SearchScraper.search(query, debugLogger)
            val match = pickBestMatch(query, results, debugLogger)
            if (match != null && slugExists(match.slug, debugLogger)) {
                Log.i(TAG, "✅ Match encontrado válido (variante): ${match.slug}")
                debugLogger("Match encontrado válido (variante): ${match.slug}")
                return match.slug
            }
        }

        return null
    }

    /**
     * Genera variantes REEMPLAZANDO sufijos.
     * Ej: "Part 2" → "2nd Season", "2nd Season" → "Part 2"
     */
    private fun generateSuffixVariants(titles: List<String>): List<String> {
        val variants = mutableListOf<String>()
        val replacements = listOf(
            Regex("(?i)\\s+Part\\s+2\\b") to " 2nd Season",
            Regex("(?i)\\s+Part\\s+3\\b") to " 3rd Season",
            Regex("(?i)\\s+Part\\s+4\\b") to " 4th Season",
            Regex("(?i)\\s+2nd\\s+Season\\b") to " Part 2",
            Regex("(?i)\\s+3rd\\s+Season\\b") to " Part 3",
            Regex("(?i)\\s+4th\\s+Season\\b") to " Part 4",
            Regex("(?i)\\s+Part\\s+II\\b") to " 2nd Season",
            Regex("(?i)\\s+Part\\s+III\\b") to " 3rd Season",
            Regex("(?i)\\s+Season\\s+2\\b") to " Part 2",
            Regex("(?i)\\s+Season\\s+3\\b") to " Part 3"
        )
        for (title in titles) {
            for ((regex, replacement) in replacements) {
                if (regex.containsMatchIn(title)) {
                    val variant = regex.replace(title, replacement).trim()
                    if (variant.isNotBlank() && variant != title && variant !in variants) {
                        variants.add(variant)
                    }
                }
            }
        }
        return variants
    }

    /**
     * Matching con estrategias en orden de prioridad.
     *
     * ESTRATEGIAS:
     * 1. Match exacto (lowercase) → el mejor
     * 2. Match por inclusión con score más alto (ratio de longitud)
     *    - Ej: "Black Clover" → "Black Clover (TV)" → ratio=0.8 ✅
     *    - Ej: "Black Clover" → "Black Clover: Mahou Tei no Ken" → ratio=0.36 ❌
     * 3. Fallback: primer resultado (solo si hay results)
     *
     * IMPORTANTE: NO devolver el primero por defecto, mejor no devolver nada
     * si ningún match es confiable.
     */
    private fun pickBestMatch(query: String, results: List<SearchResult>, debugLogger: (String) -> Unit): SearchResult? {
        if (results.isEmpty()) return null
        val q = query.lowercase().trim()
        debugLogger("pickBestMatch: query='$q', ${results.size} resultados")

        // ESTRATEGIA 1: Match exacto
        val exact = results.firstOrNull { it.title.lowercase().trim() == q }
        if (exact != null) {
            debugLogger("  → Match exacto: '${exact.title}'")
            return exact
        }

        // ESTRATEGIA 2: Match por inclusión con score
        // Calculamos el ratio de longitud (más corto / más largo)
        // Si el resultado CONTIENE al query, mejor score si el resultado es corto
        var bestScore = 0.0f
        var bestMatch: SearchResult? = null
        for (result in results) {
            val r = result.title.lowercase().trim()
            if (r.contains(q)) {
                // El resultado contiene al query
                // Ratio = longitud query / longitud resultado
                // Si son iguales → 1.0 (perfecto)
                // Si resultado es mucho más largo → ratio bajo (probablemente otro anime)
                val score = q.length.toFloat() / r.length.toFloat()
                debugLogger("  → Candidato inclusión: '${result.title}' (score=$score)")
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = result
                }
            } else if (q.contains(r)) {
                // El query contiene al resultado (caso raro)
                val score = r.length.toFloat() / q.length.toFloat()
                debugLogger("  → Candidato inclusión inversa: '${result.title}' (score=$score)")
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = result
                }
            }
        }

        // UMBRAL: solo aceptar si score >= 0.5
        // Esto significa que el más corto es al menos la mitad del más largo
        // - "black clover" / "black clover tv" = 12/15 = 0.8 ✅
        // - "black clover" / "black clover mahou tei no ken" = 12/30 = 0.4 ❌
        // - "hell mode..." / "hell mode... 2nd season" = ~0.85 ✅ (pero es T2!)
        //
        // PROBLEMA: si el query es "Hell Mode T1" y hay "Hell Mode T1" y "Hell Mode T2",
        // ambos tienen score alto. Necesitamos el EXACTO o el que no tenga extras.
        if (bestMatch != null && bestScore >= 0.5f) {
            // Verificar si hay un match con score = 1.0 (casi exacto)
            // Si hay, preferirlo sobre uno con score menor
            val perfectMatch = results.firstOrNull { result ->
                val r = result.title.lowercase().trim()
                r == q || (r.contains(q) && q.length.toFloat() / r.length.toFloat() >= 0.95f)
            }
            if (perfectMatch != null) {
                debugLogger("  → Match casi-perfecto seleccionado: '${perfectMatch.title}'")
                return perfectMatch
            }
            debugLogger("  → Match por inclusión seleccionado: '${bestMatch.title}' (score=$bestScore)")
            return bestMatch
        }

        // ESTRATEGIA 3: Si solo hay 1 resultado, devolverlo (es lo único que hay)
        if (results.size == 1) {
            debugLogger("  → Único resultado disponible: '${results.first().title}'")
            return results.first()
        }

        // ESTRATEGIA 4: Matching aproximado con normalización
        // Para casos como Hell Mode T1 donde AniList y jkanime tienen
        // diferencias de transcripción:
        // - AniList: "Yarikomi-zuki" → jkanime: "Yarikomizuki" (sin guion)
        // - AniList: "Haisettei" → jkanime: "Hai Settei" (con espacio)
        //
        // Normalizamos: quitar espacios, guiones, dos puntos, lowercase
        // Y comparamos si el resultado normalizado contiene al query normalizado
        val qNorm = normalizeForComparison(q)
        debugLogger("  → Estrategia 4: query normalizado='$qNorm'")

        var bestNormScore = 0.0f
        var bestNormMatch: SearchResult? = null
        for (result in results) {
            val rNorm = normalizeForComparison(result.title.lowercase().trim())
            if (rNorm.contains(qNorm) || qNorm.contains(rNorm)) {
                // Calcular score basado en qué tan similares son en longitud
                val shorter = minOf(qNorm.length, rNorm.length)
                val longer = maxOf(qNorm.length, rNorm.length)
                val score = shorter.toFloat() / longer.toFloat()
                debugLogger("  → Candidato normalizado: '${result.title}' (norm='$rNorm', score=$score)")
                if (score > bestNormScore) {
                    bestNormScore = score
                    bestNormMatch = result
                }
            }
        }

        // UMBRAL: 0.85 (muy similares después de normalizar)
        if (bestNormMatch != null && bestNormScore >= 0.85f) {
            debugLogger("  → Match normalizado seleccionado: '${bestNormMatch.title}' (score=$bestNormScore)")
            return bestNormMatch
        }

        debugLogger("  → No hay match confiable, rechazando")
        return null
    }

    /**
     * Normaliza un título para comparación aproximada.
     * Quita espacios, guiones, dos puntos, puntos, comas.
     * Esto permite matchear "Yarikomi-zuki" con "Yarikomizuki"
     * o "Haisettei" con "Hai Settei".
     */
    private fun normalizeForComparison(s: String): String {
        return s
            .replace(Regex("[-\\s:.,_]"), "")  // quitar separadores
            .trim()
    }

    private fun slugExists(slug: String, debugLogger: (String) -> Unit): Boolean {
        return try {
            val html = HttpClient.get("https://jkanime.net/$slug/")
            val notFound = html.contains("Página no encontrada") || html.contains("404 Not Found")
            if (notFound) {
                debugLogger("Jkanime devolvió 404 para el slug '$slug'")
                false
            } else {
                html.contains("og:title")
            }
        } catch (e: Exception) {
            debugLogger("Error verificando slug '$slug': ${e.message}")
            false
        }
    }
}
