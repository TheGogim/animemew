package com.mew.animemew.scraper

import java.net.URLEncoder

object SearchScraper {

    /**
     * Busca animes por título en jkanime.
     * Acepta español, inglés, romaji.
     *
     * @param query texto a buscar (ej: "naruto", "slime", "Tensei shitara")
     * @return lista de resultados con slug + título + carátula
     */
    fun search(query: String, debugLogger: (String) -> Unit = {}): List<SearchResult> {
        // FIX CRÍTICO: URLEncoder.encode convierte espacios en "+", pero
        // jkanime necesita "%20". Sin este fix, búsquedas con espacios largos
        // devuelven 0 resultados.
        val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val url = "https://jkanime.net/buscar/$encoded/"
        val html = try {
            HttpClient.get(url, referer = "https://jkanime.net/")
        } catch (e: Exception) {
            debugLogger("Error en SearchScraper ($url): ${e.message}")
            return emptyList()
        }

        // 1. Verificar si hubo redirección a la página de detalle directamente (ocurre cuando hay match exacto)
        if (html.contains("class=\"anime_info\"")) {
            val titleMatch = Regex("""<h3>([^<]+)</h3>""").find(html)
            val imgUrlMatch = Regex("""src="([^"]*/animes/image/([a-z0-9-]+)\.(?:jpg|jpeg|png))"""").find(html)
            
            if (titleMatch != null && imgUrlMatch != null) {
                val title = titleMatch.groupValues[1].trim()
                val coverUrl = imgUrlMatch.groupValues[1]
                val slug = imgUrlMatch.groupValues[2]
                debugLogger("SearchScraper: Redirección detectada a $slug")
                return listOf(SearchResult(slug, coverUrl, title))
            }
        }

        // 2. Página de resultados de búsqueda
        val results = mutableListOf<SearchResult>()
        val items = html.split("class=\"anime__item\"")
        
        if (items.size > 1) {
            for (item in items.drop(1)) {
                val slugMatch = Regex("""href="https://jkanime\.net/([a-z0-9-]+)/"""").find(item)
                val imgMatch = Regex("""(?:data-setbg|src)="([^"]+)"""").find(item)
                val titleMatch = Regex("""<h5[^>]*>(?:<a[^>]*>)?([^<]+)(?:</a>)?</h5>""").find(item)
                
                if (slugMatch != null && titleMatch != null) {
                    // Evitar falsos positivos como "/buscar/" u otros enlaces de sistema
                    val slug = slugMatch.groupValues[1]
                    if (slug != "buscar" && slug != "genero" && slug != "letra") {
                        results.add(
                            SearchResult(
                                slug = slug,
                                coverUrl = imgMatch?.groupValues?.get(1),
                                title = titleMatch.groupValues[1].trim()
                            )
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            debugLogger("SearchScraper: Encontrados ${results.size} resultados")
        }

        return results.distinctBy { it.slug }
    }
}
