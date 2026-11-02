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

    /** v3: identificador global (uuid) de cada trilha, para compartilhar entre aparelhos. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_2_3.forEach(db::execSQL)
        }
    }

    internal val SQL_2_3 = listOf(
        "ALTER TABLE `trails` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''",
        "UPDATE `trails` SET `uuid` = lower(hex(randomblob(16)))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_trails_uuid` ON `trails` (`uuid`)",
    )

    /** v4: mensagens guardadas da rede em malha. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_3_4.forEach(db::execSQL)
        }
    }

    internal val SQL_3_4 = listOf(
        "CREATE TABLE IF NOT EXISTS `mesh_messages` (`id` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
            "`priority` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, " +
            "`supersedeKey` TEXT, `payload` BLOB NOT NULL, `deliveredTo` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_mesh_messages_supersedeKey` ON `mesh_messages` (`supersedeKey`)",
        "CREATE INDEX IF NOT EXISTS `index_mesh_messages_expiresAt` ON `mesh_messages` (`expiresAt`)",
    )

    /** v5: histórico de mensagens de texto e emergências. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_4_5.forEach(db::execSQL)
        }
    }

    internal val SQL_4_5 = listOf(
        "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `fromDevice` TEXT NOT NULL, " +
            "`fromName` TEXT NOT NULL, `fromRole` TEXT NOT NULL, `fromGroup` TEXT, `toDevice` TEXT, " +
            "`text` TEXT NOT NULL, `urgent` INTEGER NOT NULL, `emergency` INTEGER NOT NULL, `latitude` REAL, " +
            "`longitude` REAL, `createdAt` INTEGER NOT NULL, `outgoing` INTEGER NOT NULL, `read` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)",
    )

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
