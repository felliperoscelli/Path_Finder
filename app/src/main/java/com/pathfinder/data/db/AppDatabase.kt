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
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trailDao(): TrailDao
    abstract fun trailPointDao(): TrailPointDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun offlineAreaDao(): OfflineAreaDao
    abstract fun navigationDao(): NavigationDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pathfinder.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
