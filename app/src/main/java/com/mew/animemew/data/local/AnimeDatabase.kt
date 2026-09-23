package com.mew.animemew.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mew.animemew.data.season.SeasonChainDao
import com.mew.animemew.data.season.SeasonChainEntity

@Database(
    entities = [
        AnimeListEntity::class,
        LocalAnimeEntity::class,
        AnimeListCrossRef::class,
        WatchHistoryEntity::class,
        SeasonChainEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun seasonChainDao(): SeasonChainDao

    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        // MIGRACIÓN 12 → 13: rediseño del AiringController.
        // Añade 2 columnas a watch_history para el nuevo modelo:
        //  - nextAvailableEpisode: el episodio más alto disponible en scrapers
        //    (calculado por AiringController en background, NO tocado por el usuario)
        //  - hasNewEpisode: flag booleano para mostrar badge en HomeScreen
        //
        // IMPORTANTE: NO toca episodeNumber ni ningún otro campo existente.
        // Todos los animes actuales quedan con nextAvailableEpisode=0 y
        // hasNewEpisode=false → siguen funcionando como antes hasta que el
        // AiringController los verifique por primera vez.
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextAvailableEpisode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN hasNewEpisode INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRACIÓN 13 → 14: La "Bolsa" de emisión.
        // Añade 2 columnas para trackear el próximo episodio desde AniList:
        //  - anilistNextEpNum: número del próximo ep según AniList (0 = sin info)
        //  - anilistNextEpAirAt: cuándo se emite (Unix seg UTC, 0 = sin info)
        //
        // Esto permite que el AiringController sepa CUÁNDO verificar jk sin
        // tener que hacer scraping constante. Solo verifica cuando
        // anilistNextEpAirAt + 1.5h <= now.
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN anilistNextEpNum INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN anilistNextEpAirAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRACIÓN 14 → 15: Campo epEsperado.
        // Añade una columna para trackear qué episodio está esperando el usuario
        // cuando entra en "En espera". Esto permite que el AiringController
        // verifique ESE episodio específicamente en jk, sin importar si
        // salieron más eps después.
        //
        // - 0 = no está esperando un ep específico (o no está en espera)
        // - N > 0 = está esperando el episodio N (verificar disponibilidad de N)
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN epEsperado INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRACIÓN 11 → 12: invalidar caché de SeasonChain
        // para que se vuelva a resolver con el campo status (NOT_YET_RELEASED, etc.)
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM season_chains")
            }
        }

        // MIGRACIÓN 10 → 11: añadir waitingSinceTimestamp
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN waitingSinceTimestamp INTEGER")
            }
        }

        // MIGRACIÓN 9 → 10: añadir nextEpisodeTimestamp
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                forceDefaultLists(db)
            }
        }

        // MIGRACIÓN 8 → 10
        val MIGRATION_8_10 = object : Migration(8, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isAiring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                forceDefaultLists(db)
            }
        }

        // MIGRACIÓN 8 → 11 (saltar versión)
        val MIGRATION_8_11 = object : Migration(8, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isAiring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN waitingSinceTimestamp INTEGER")
                forceDefaultLists(db)
            }
        }

        // Migraciones desde versiones más antiguas
        val MIGRATION_7_11 = object : Migration(7, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS season_chains (
                        rootAnilistId INTEGER NOT NULL PRIMARY KEY,
                        chainJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastAccessed INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN anilistId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isAiring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN waitingSinceTimestamp INTEGER")
                forceDefaultLists(db)
            }
        }

        val MIGRATION_6_11 = object : Migration(6, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                forceDefaultLists(db)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS season_chains (
                        rootAnilistId INTEGER NOT NULL PRIMARY KEY,
                        chainJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastAccessed INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN anilistId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isAiring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN waitingSinceTimestamp INTEGER")
            }
        }

        val MIGRATION_5_11 = object : Migration(5, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                forceDefaultLists(db)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS season_chains (
                        rootAnilistId INTEGER NOT NULL PRIMARY KEY,
                        chainJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastAccessed INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN anilistId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isAiring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN waitingSinceTimestamp INTEGER")
            }
        }

        val MIGRATION_4_11 = object : Migration(4, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                forceDefaultLists(db)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS season_chains (
                        rootAnilistId INTEGER NOT NULL PRIMARY KEY,
                        chainJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastAccessed INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN anilistId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN seasonTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isAiring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN nextEpisodeTimestamp INTEGER")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN waitingSinceTimestamp INTEGER")
            }
        }

        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                .addMigrations(
                    MIGRATION_4_11, MIGRATION_5_11, MIGRATION_6_11,
                    MIGRATION_7_11, MIGRATION_8_11, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        forceDefaultLists(db)
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        forceDefaultLists(db)
                    }
                })
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun forceDefaultLists(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO anime_list (id, name, isDefault) VALUES (1, 'Favoritos', 1)")
            db.execSQL("INSERT OR IGNORE INTO anime_list (id, name, isDefault) VALUES (2, 'Vistos', 1)")
            db.execSQL("INSERT OR IGNORE INTO anime_list (id, name, isDefault) VALUES (3, 'Viendo', 1)")
            db.execSQL("UPDATE anime_list SET name = 'Favoritos', isDefault = 1 WHERE id = 1")
            db.execSQL("UPDATE anime_list SET name = 'Vistos', isDefault = 1 WHERE id = 2")
            db.execSQL("UPDATE anime_list SET name = 'Viendo', isDefault = 1 WHERE id = 3")
        }
    }
}
