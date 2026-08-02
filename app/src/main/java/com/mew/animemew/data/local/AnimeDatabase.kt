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
    version = 12,
    exportSchema = false
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun seasonChainDao(): SeasonChainDao

    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

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
                    MIGRATION_10_11, MIGRATION_11_12
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
