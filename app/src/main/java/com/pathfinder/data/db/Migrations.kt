package com.pathfinder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /** v2: sessões de navegação, eventos de desvio e sessionId no trajeto. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_1_2.forEach(db::execSQL)
        }
    }

    internal val SQL_1_2 = listOf(
        "ALTER TABLE `track_points` ADD COLUMN `sessionId` INTEGER",
        "CREATE INDEX IF NOT EXISTS `index_track_points_sessionId_timestamp` ON `track_points` (`sessionId`, `timestamp`)",
        "CREATE TABLE IF NOT EXISTS `navigation_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`trailId` INTEGER NOT NULL, `groupName` TEXT NOT NULL, `monitorName` TEXT NOT NULL, " +
            "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `nextPointIndex` INTEGER NOT NULL, " +
            "`reachedPoints` TEXT NOT NULL, `deviationAlerts` INTEGER NOT NULL, " +
            "FOREIGN KEY(`trailId`) REFERENCES `trails`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_navigation_sessions_trailId` ON `navigation_sessions` (`trailId`)",
        "CREATE TABLE IF NOT EXISTS `deviation_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`sessionId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `alarmAt` INTEGER NOT NULL, " +
            "`endedAt` INTEGER, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
            "`maxDistanceMeters` REAL NOT NULL, " +
            "FOREIGN KEY(`sessionId`) REFERENCES `navigation_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_deviation_events_sessionId` ON `deviation_events` (`sessionId`)",
    )

    val ALL = arrayOf(MIGRATION_1_2)
}
