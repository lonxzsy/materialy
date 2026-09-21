package com.materialy.music.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /** Version 2 adds persisted online catalog entries without touching local media. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `online_songs` (
                    `onlineId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `artworkUrl` TEXT,
                    `sourceUrl` TEXT NOT NULL,
                    `directStreamUrl` TEXT,
                    `playlistName` TEXT,
                    `sourceType` TEXT NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    `isFavorite` INTEGER NOT NULL,
                    `lastPlayedAt` INTEGER
                )""".trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_online_songs_sourceUrl` ON `online_songs` (`sourceUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_online_songs_title` ON `online_songs` (`title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_online_songs_addedAt` ON `online_songs` (`addedAt`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
