package de.minitraxx.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        StemEntity::class,
        SetlistEntity::class,
        SetlistItemEntity::class,
        GigEntity::class,
        GigPlayEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun gigDao(): GigDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN endAction INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN notes TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN chordPro TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN syncData TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """CREATE TABLE gigs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE gig_plays (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gigId INTEGER NOT NULL,
                        songId INTEGER,
                        titleSnapshot TEXT NOT NULL,
                        artistSnapshot TEXT NOT NULL,
                        playedAt INTEGER NOT NULL,
                        playedMs INTEGER NOT NULL,
                        FOREIGN KEY (gigId) REFERENCES gigs (id) ON DELETE CASCADE,
                        FOREIGN KEY (songId) REFERENCES songs (id) ON DELETE SET NULL
                    )"""
                )
                db.execSQL("CREATE INDEX index_gig_plays_gigId ON gig_plays (gigId)")
                db.execSQL("CREATE INDEX index_gig_plays_songId ON gig_plays (songId)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minitraxx.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                ).build().also { instance = it }
            }
    }
}
