package de.livegigplayer.pro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class, Playlist::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`isLiveLocked` INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN keySignature TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN artist TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE songs ADD COLUMN bpmExact REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN autoStop INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "livegig_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
