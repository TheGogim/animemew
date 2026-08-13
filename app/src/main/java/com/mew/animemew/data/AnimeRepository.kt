package com.mew.animemew.data

import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloGraphQLException
import com.mew.animemew.graphql.GetAnimeDetailsQuery
import com.mew.animemew.graphql.GetGenresQuery
import com.mew.animemew.graphql.GetPopularAnimeQuery
import com.mew.animemew.graphql.GetTrendingAnimeQuery
import com.mew.animemew.graphql.SearchAnimeQuery
import com.mew.animemew.graphql.type.MediaSort
import com.mew.animemew.graphql.type.MediaStatus
import com.mew.animemew.network.AniListClient

// =========================================================
//  Excepción específica para cuando AniList está caído.
//  Permite que los ViewModels distingan entre:
//    - Error de red del usuario (sin internet)
//    - AniList caído (sus servers patean 403)
//    - Otro error Apollo
// =========================================================
class AniListUnavailableException(message: String) : Exception(message)

class AnimeRepository {
    private val client = AniListClient.apolloClient

    private fun checkAniListErrors(errors: List<com.apollographql.apollo.api.Error>?) {
        // AniList cuando está caído devuelve:
        //   { "errors": [{ "message": "The AniList API has been temporarily disabled...", "status": 403 }] }
        //
        // Para probar el error view, descomenta la siguiente línea:
        // throw AniListUnavailableException("The AniList API has been temporarily disabled due to severe stability issues.")

        // Usamos safe calls (?.) para que el smart cast no dependa del check anterior
        // (necesario si arriba hay un throw activo que hace el código inalcanzable)
        val first = errors?.firstOrNull() ?: return
        val msg = first.message ?: ""
        if (msg.contains("temporarily disabled", ignoreCase = true) ||
            msg.contains("AniList API has been", ignoreCase = true) ||
            msg.contains("stability issues", ignoreCase = true)) {
            throw AniListUnavailableException(msg)
        }
        // Cualquier otro error GraphQL también lo reportamos
        throw ApolloGraphQLException(first)
    }

    suspend fun getTrendingAnime(page: Int, perPage: Int): GetTrendingAnimeQuery.Page? {
        val response = client.query(GetTrendingAnimeQuery(
            page = Optional.present(page),
            perPage = Optional.present(perPage)
        )).execute()
        checkAniListErrors(response.errors)
        return response.data?.Page
    }

    suspend fun getPopularAnime(page: Int, perPage: Int): GetPopularAnimeQuery.Page? {
        val response = client.query(GetPopularAnimeQuery(
            page = Optional.present(page),
            perPage = Optional.present(perPage)
        )).execute()
        checkAniListErrors(response.errors)
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
        checkAniListErrors(response.errors)
        return response.data?.Page
    }

    suspend fun getAnimeDetails(id: Int): GetAnimeDetailsQuery.Media? {
        val response = client.query(GetAnimeDetailsQuery(id = id)).execute()
        checkAniListErrors(response.errors)
        return response.data?.Media
    }

    suspend fun getGenres(): List<String>? {
        val response = client.query(GetGenresQuery()).execute()
        checkAniListErrors(response.errors)
        return response.data?.GenreCollection?.filterNotNull()
    }
}
