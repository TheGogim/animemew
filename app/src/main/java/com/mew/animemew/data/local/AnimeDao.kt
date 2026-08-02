package com.mew.animemew.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class AnimeListWithAnimes(
    @Embedded val animeList: AnimeListEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = AnimeListCrossRef::class,
            parentColumn = "listId",
            entityColumn = "animeId"
        )
    )
    val animes: List<LocalAnimeEntity>
)

@Dao
interface AnimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnime(anime: LocalAnimeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(animeList: AnimeListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(animeList: AnimeListEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnimeIntoList(crossRef: AnimeListCrossRef): Long

    @Delete
    suspend fun removeAnimeFromList(crossRef: AnimeListCrossRef): Int

    @Query("DELETE FROM anime_list_cross_ref WHERE listId = :listId AND animeId = :animeId")
    suspend fun removeAnimeFromListById(listId: Int, animeId: Int): Int

    @Delete
    suspend fun deleteList(animeList: AnimeListEntity): Int

    @Query("SELECT * FROM anime_list")
    fun getAllLists(): Flow<List<AnimeListEntity>>

    @Transaction
    @Query("SELECT * FROM anime_list")
    fun getListsWithAnimes(): Flow<List<AnimeListWithAnimes>>

    @Transaction
    @Query("SELECT * FROM anime_list WHERE id = :listId")
    fun getListWithAnimesById(listId: Int): Flow<AnimeListWithAnimes?>

    @Query("SELECT EXISTS(SELECT 1 FROM anime_list_cross_ref WHERE listId = :listId AND animeId = :animeId)")
    fun isAnimeInList(listId: Int, animeId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity): Long

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE animeSlug = :slug")
    suspend fun getWatchHistoryForAnime(slug: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE animeSlug = :slug")
    suspend fun deleteWatchHistory(slug: String): Int

    @Query("DELETE FROM watch_history WHERE anilistId = :anilistId")
    suspend fun deleteWatchHistoryByAnilistId(anilistId: Int): Int

    // NUEVO: obtener animes en emisión (para actualizar "En espera")
    @Query("SELECT * FROM watch_history WHERE isAiring = 1")
    suspend fun getAiringWatchHistory(): List<WatchHistoryEntity>

    // === Batch reads para SyncManager ===

    @Query("SELECT * FROM anime_list")
    suspend fun getAllListsOnce(): List<AnimeListEntity>

    @Query("SELECT * FROM local_anime")
    suspend fun getAllAnimesOnce(): List<LocalAnimeEntity>

    @Query("SELECT * FROM anime_list_cross_ref")
    suspend fun getAllCrossRefsOnce(): List<AnimeListCrossRef>

    @Query("SELECT * FROM watch_history")
    suspend fun getAllWatchHistoryOnce(): List<WatchHistoryEntity>
}
