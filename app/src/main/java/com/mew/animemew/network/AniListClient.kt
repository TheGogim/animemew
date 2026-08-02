package com.mew.animemew.network

import com.apollographql.apollo.ApolloClient

object AniListClient {
    val apolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .build()
}
