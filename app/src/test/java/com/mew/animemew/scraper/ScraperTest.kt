package com.mew.animemew.scraper

import org.junit.Test
import org.junit.Assert.*

class ScraperTest {
    @Test
    fun testSearchAndFetch() {
        val query = "Solo Leveling"
        println("Buscando: $query")
        val results = SearchScraper.search(query)
        println("Resultados: $results")
        
        if (results.isNotEmpty()) {
            val slug = results.first().slug
            println("Slug seleccionado: $slug")
            
            val page = AnimePageScraper.fetch(slug)
            println("Página cargada: ${page?.title} con ${page?.totalEpisodes} episodios")
            
            val jkanimeScraper = JkanimeScraper()
            kotlinx.coroutines.runBlocking {
                val data = jkanimeScraper.fetchServers(slug, 1)
                println("Servidores del ep 1: ${data.servers.map { it.name }}")
            }
        } else {
            println("No se encontraron resultados para $query")
        }
    }
}
