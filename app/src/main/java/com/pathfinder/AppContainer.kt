package com.pathfinder

import android.content.Context
import com.pathfinder.data.OfflineAreaRepository
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.geo.AndroidDeclinationProvider
import com.pathfinder.map.OfflineMapManager

/** Injeção de dependências manual: uma instância de cada serviço para o app inteiro. */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)

    val trailRepository = TrailRepository(database, AndroidDeclinationProvider)
    val offlineAreaRepository = OfflineAreaRepository(database)
    val settingsRepository = SettingsRepository(context)
    val offlineMapManager = OfflineMapManager(context.applicationContext)
}
