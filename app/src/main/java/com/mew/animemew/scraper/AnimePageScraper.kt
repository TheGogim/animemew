package com.mew.animemew.scraper

object AnimePageScraper {

    /**
     * Scrapea la página principal de un anime.
     * Devuelve toda la info disponible: título, sinopsis, episodios, etc.
     */
    fun fetch(slug: String, debugLogger: (String) -> Unit = {}): AnimePage? {
        val url = "https://jkanime.net/$slug/"
        val html = try {
            HttpClient.get(url, referer = "https://jkanime.net/")
        } catch (e: Exception) {
            debugLogger("Error en AnimePageScraper ($url): ${e.message}")
            return null
        }

        // 404 check
        if (html.contains("Página no encontrada")) return null

        val title = parseTitle(html) ?: slug
        val cover = parseCover(html) ?: ""
        val synopsis = parseSynopsis(html) ?: ""
        val status = parseStatus(html) ?: "En emisión"
        val totalEpisodes = parseTotalEpisodes(html, slug) ?: 0
        val genres = parseGenres(html)
        val airedDate = parseAiredDate(html)
        val duration = parseDuration(html)
        val episodes = parseEpisodeList(slug, totalEpisodes)

        return AnimePage(
            slug = slug,
            title = title,
            coverUrl = cover,
            synopsis = synopsis,
            status = status,
            totalEpisodes = totalEpisodes,
            genres = genres,
            airedDate = airedDate,
            duration = duration,
            episodeList = episodes
        )
    }

    // ─── Parsers ─────────────────────────────────────────────────

    private fun parseTitle(html: String): String? {
        return Regex("""<title>([^<]+) - anime[^<]*</title>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    private fun parseCover(html: String): String? {
        return Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)
            ?.groupValues?.get(1)
    }

    private fun parseSynopsis(html: String): String? {
        return Regex("""<p\s+class="scroll">([^<]+)</p>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    private fun parseStatus(html: String): String? {
        return Regex("""<li>\s*<span>Estado:</span>\s*([^<]+)</li>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    private fun parseTotalEpisodes(html: String, slug: String): Int? {
        // 1. Intentar encontrar el total inyectado en javascript (sirve para animes muy largos)
        val scriptMatch = Regex("""paginationEps\((\d+)\)""").find(html)
        if (scriptMatch != null) {
            return scriptMatch.groupValues[1].toIntOrNull()
        }
        
        // 2. Fallback robusto: Buscar TODOS los links de episodios en la página y tomar el mayor
        // Jkanime siempre tiene links del tipo https://jkanime.net/slug/numero/
        val allEpsRegex = Regex("""href="https://jkanime\.net/$slug/(\d+)/"""")
        val maxEp = allEpsRegex.findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
        if (maxEp != null && maxEp > 0) {
            return maxEp
        }
        
        // 3. Fallback al listado clásico de la info
        return Regex("""<li>\s*<span>Episodios:</span>\s*(\d+)\s*</li>""").find(html)
            ?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseGenres(html: String): List<String> {
        return Regex(
            """<a\s+href="https://jkanime\.net/genero/[^"]+">([^<]+)</a>"""
        ).findAll(html).map { it.groupValues[1].trim() }.toList()
    }

    private fun parseAiredDate(html: String): String? {
        return Regex("""<li>\s*<span>\s*Emitido:\s*</span>\s*([^<]+)</li>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    private fun parseDuration(html: String): String? {
        return Regex("""<li>\s*<span>Duracion:</span>\s*([^<]+)</li>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    private fun parseEpisodeList(slug: String, total: Int): List<EpisodeSummary> {
        if (total <= 0) return emptyList()
        return (1..total).map { n ->
            EpisodeSummary(
                number = n,
                thumbnailUrl = null,
                url = "https://jkanime.net/$slug/$n/"
            )
        }
    }
}
