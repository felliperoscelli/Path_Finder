package com.pathfinder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrailEntity::class,
        TrailPointEntity::class,
        TrackPointEntity::class,
        OfflineAreaEntity::class,
        NavigationSessionEntity::class,
        DeviationEventEntity::class,
        MeshMessageEntity::class,
        ChatMessageEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trailDao(): TrailDao
    abstract fun trailPointDao(): TrailPointDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun offlineAreaDao(): OfflineAreaDao
    abstract fun navigationDao(): NavigationDao
    abstract fun meshMessageDao(): MeshMessageDao
    abstract fun chatDao(): ChatDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pathfinder.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
