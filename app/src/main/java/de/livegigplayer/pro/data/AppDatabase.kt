package de.livegigplayer.pro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Song::class,
        GigEntity::class,
        SetEntity::class,
        SetSongCrossRef::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun gigDao(): GigDao
    abstract fun setDao(): SetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ── Bestehende Migrations ──────────────────────────────────────────────

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

        // Brücken-Migration: deckt Lücken 2→3→4→5.
        // Geräte auf v2 erhalten sauberes v9-Schema (Songdaten inkompatibel — autoStop/loopPoints fehlten).
        private val MIGRATION_2_9 = object : Migration(2, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `songs`")
                db.execSQL("""
                    CREATE TABLE `songs` (
                        `id`            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title`         TEXT NOT NULL,
                        `artist`        TEXT NOT NULL DEFAULT '',
                        `bpm`           INTEGER NOT NULL,
                        `bpmExact`      REAL NOT NULL DEFAULT 0.0,
                        `timeSignature` TEXT NOT NULL,
                        `playlistId`    INTEGER NOT NULL,
                        `isCompleted`   INTEGER NOT NULL DEFAULT 0,
                        `audioFilePath` TEXT NOT NULL,
                        `duration`      TEXT NOT NULL DEFAULT '00:00',
                        `capoPosition`  INTEGER NOT NULL DEFAULT 0,
                        `keySignature`  TEXT NOT NULL DEFAULT '',
                        `genre`         TEXT NOT NULL DEFAULT '',
                        `volDrums`      REAL NOT NULL DEFAULT 0.0,
                        `volBass`       REAL NOT NULL DEFAULT 0.0,
                        `volKeys`       REAL NOT NULL DEFAULT 0.0,
                        `volVocals`     REAL NOT NULL DEFAULT 0.0,
                        `volClick`      REAL NOT NULL DEFAULT 0.0,
                        `volCue`        REAL NOT NULL DEFAULT 0.0,
                        `autoStop`      INTEGER NOT NULL DEFAULT 0,
                        `loopStartMs`   INTEGER NOT NULL DEFAULT 0,
                        `loopEndMs`     INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
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

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN loopStartMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN loopEndMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        // ── Phase 1: Stabilisierung — no-op ───────────────────────────────────
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Kein Schema-Change. fallbackToDestructiveMigration() wird entfernt.
            }
        }

        // ── Phase 2: Gig → Set → Song Architektur ─────────────────────────────
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // Neue Tabelle: gigs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gigs` (
                        `gigId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name`  TEXT NOT NULL
                    )
                """.trimIndent())

                // Neue Tabelle: sets (FK → gigs, CASCADE)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sets` (
                        `setId`      INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gigOwnerId` INTEGER NOT NULL,
                        `name`       TEXT NOT NULL,
                        `position`   INTEGER NOT NULL,
                        FOREIGN KEY(`gigOwnerId`) REFERENCES `gigs`(`gigId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sets_gigOwnerId` ON `sets`(`gigOwnerId`)"
                )

                // Neue Tabelle: set_song_cross_ref (Composite PK, CASCADE)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `set_song_cross_ref` (
                        `setId`         INTEGER NOT NULL,
                        `songId`        INTEGER NOT NULL,
                        `positionInSet` INTEGER NOT NULL,
                        `isCompleted`   INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`setId`, `songId`),
                        FOREIGN KEY(`setId`) REFERENCES `sets`(`setId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`songId`) REFERENCES `songs`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_set_song_cross_ref_songId` " +
                    "ON `set_song_cross_ref`(`songId`)"
                )

                db.execSQL("DROP TABLE IF EXISTS `playlists`")

                // songs-Tabelle bleibt vollständig unangetastet.
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE set_song_cross_ref ADD COLUMN isSpontaneous INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE set_song_cross_ref ADD COLUMN endAction INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "livegig_database"
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_9,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                )
                // fallbackToDestructiveMigration() entfernt — Phase 1 abgeschlossen
                .build()
                .also { INSTANCE = it }
            }
    }
}
