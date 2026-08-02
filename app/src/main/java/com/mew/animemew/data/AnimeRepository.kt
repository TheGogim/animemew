package com.mew.animemew.data

import com.apollographql.apollo.api.Optional
import com.mew.animemew.graphql.GetAnimeDetailsQuery
import com.mew.animemew.graphql.GetGenresQuery
import com.mew.animemew.graphql.GetPopularAnimeQuery
import com.mew.animemew.graphql.GetTrendingAnimeQuery
import com.mew.animemew.graphql.SearchAnimeQuery
import com.mew.animemew.graphql.type.MediaSort
import com.mew.animemew.graphql.type.MediaStatus
import com.mew.animemew.network.AniListClient

class AnimeRepository {
    private val client = AniListClient.apolloClient

    suspend fun getTrendingAnime(page: Int, perPage: Int): GetTrendingAnimeQuery.Page? {
        val response = client.query(GetTrendingAnimeQuery(
            page = Optional.present(page),
            perPage = Optional.present(perPage)
        )).execute()
        return response.data?.Page
    }

    suspend fun getPopularAnime(page: Int, perPage: Int): GetPopularAnimeQuery.Page? {
        val response = client.query(GetPopularAnimeQuery(
            page = Optional.present(page),
            perPage = Optional.present(perPage)
        )).execute()
        return response.data?.Page
    }

    // NUEVO: añadido parámetro 'status' para filtrar por animes en emisión
    suspend fun searchAnime(
        page: Int,
        perPage: Int,
        searchQuery: String?,
        genres: List<String>?,
        sort: List<MediaSort>? = null,
        status: List<MediaStatus>? = null
    ): SearchAnimeQuery.Page? {
        val response = client.query(SearchAnimeQuery(
            page = Optional.present(page),
            perPage = Optional.present(perPage),
            search = Optional.presentIfNotNull(searchQuery),
            genres = Optional.presentIfNotNull(genres),
            sort = Optional.presentIfNotNull(sort),
            status = Optional.presentIfNotNull(status)
        )).execute()
        return response.data?.Page
    }

    suspend fun getAnimeDetails(id: Int): GetAnimeDetailsQuery.Media? {
        val response = client.query(GetAnimeDetailsQuery(id = id)).execute()
        return response.data?.Media
    }

    suspend fun getGenres(): List<String>? {
        val response = client.query(GetGenresQuery()).execute()
        return response.data?.GenreCollection?.filterNotNull()
    }
}
