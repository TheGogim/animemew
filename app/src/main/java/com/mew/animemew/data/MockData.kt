package com.mew.animemew.data

data class Anime(
    val id: String,
    val title: String,
    val coverUrl: String,
    val score: Double,
    val type: String // TV, OVA, Película
)

data class AnimeRelation(
    val id: String,
    val relationType: String, // Secuela, Precuela, Spin-off
    val title: String,
    val coverUrl: String,
    val type: String
)

data class AnimeDetails(
    val id: String,
    val title: String,
    val coverUrl: String,
    val bannerUrl: String,
    val score: Double,
    val type: String,
    val status: String,
    val releaseYear: Int,
    val seasons: Int,
    val ranking: Int,
    val genres: List<String>,
    val description: String,
    val trailerYoutubeId: String?,
    val relations: List<AnimeRelation>,
    // NUEVOS campos para próximo episodio:
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeTimestamp: Long? = null
)

object MockData {
    val popularAnime = listOf(
        Anime("1", "Solo Leveling", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx151807-m1gX3iqITqHn.png", 8.5, "TV"),
        Anime("2", "Frieren: Más allá del final", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-n1MjsJGAEemG.png", 9.1, "TV"),
        Anime("3", "Jujutsu Kaisen", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-bbBWj4pEFseh.jpg", 8.7, "TV"),
        Anime("4", "Demon Slayer", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-PEn1CTc93DQl.jpg", 8.4, "TV"),
        Anime("5", "Attack on Titan", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-73IhOXpJZiMF.jpg", 9.0, "TV")
    )

    val trendingAction = listOf(
        Anime("6", "Chainsaw Man", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx127230-Nuos3B30rXJg.png", 8.6, "TV"),
        Anime("7", "Bleach: Thousand-Year Blood War", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx146065-p3sD6iKz7Vtc.jpg", 8.8, "TV"),
        Anime("8", "One Punch Man", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20966-ibrnE1470h6E.jpg", 8.3, "TV")
    )

    val trendingFantasy = listOf(
        Anime("9", "Mushoku Tensei", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx108465-BnsWNWZ5qvJt.jpg", 8.3, "TV"),
        Anime("10", "Re:ZERO", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21214-wD2h82gC4lW5.jpg", 8.2, "TV"),
        Anime("11", "KonoSuba", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21202-1qB3qB2fB8sJ.jpg", 8.1, "TV")
    )

    val detailedAnimeMock = AnimeDetails(
        id = "1",
        title = "Solo Leveling",
        coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx151807-m1gX3iqITqHn.png",
        bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/151807-ngVsA41OaEaD.jpg",
        score = 8.5,
        type = "TV",
        status = "Finalizado",
        releaseYear = 2024,
        seasons = 1,
        ranking = 14,
        genres = listOf("Acción", "Aventura", "Fantasía"),
        description = "Dicen que todo lo que no te mata te hace más fuerte, pero en el caso de Sung Jinwoo, lo que lo mató lo hizo el cazador más fuerte de todos. Después de ser brutalmente asesinado por monstruos en una mazmorra de alto rango, Jinwoo regresa con un misterioso sistema, un programa que solo él puede ver y que le permite subir de nivel a la velocidad de la luz. Ahora está decidido a descubrir los secretos detrás de sus nuevos poderes y la mazmorra que lo engendró.",
        trailerYoutubeId = "oB8Ww5hR1sM",
        relations = listOf(
            AnimeRelation("12", "Secuela", "Solo Leveling Season 2: Arise from the Shadow", "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx175806-zGv4rMOfV0iQ.png", "TV")
        )
    )
}
