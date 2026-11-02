package com.pathfinder

import android.content.Context
import com.pathfinder.data.NavigationRepository
import com.pathfinder.data.OfflineAreaRepository
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.geo.AndroidDeclinationProvider
import com.pathfinder.map.OfflineMapManager
import com.pathfinder.net.DeviceIdentity
import com.pathfinder.net.NearbyManager
import com.pathfinder.net.NetworkHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Injeção de dependências manual: uma instância de cada serviço para o app inteiro. */
class AppContainer(context: Context) {
    /** Escopo do app inteiro (rede, mensagens recebidas). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database = AppDatabase.build(context)

    val trailRepository = TrailRepository(database, AndroidDeclinationProvider)
    val offlineAreaRepository = OfflineAreaRepository(database)
    val navigationRepository = NavigationRepository(database)
    val settingsRepository = SettingsRepository(context)
    val offlineMapManager = OfflineMapManager(context.applicationContext)
    val nearby = NearbyManager(context.applicationContext, DeviceIdentity(context), appScope)
    val networkHub = NetworkHub(context.applicationContext, nearby, trailRepository, settingsRepository, appScope)
}
