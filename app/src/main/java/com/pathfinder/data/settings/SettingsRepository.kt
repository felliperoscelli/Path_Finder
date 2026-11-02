package com.pathfinder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class UserRole(val label: String) {
    COORDINATOR("Coordenador"),
    MONITOR("Monitor"),

    /** Celular fixo num ponto intermediário, só repassando mensagens. */
    REPEATER("Repetidor"),
}

enum class CoordinateFormat(val label: String) {
    UTM("UTM"),
    LAT_LON("Latitude/longitude"),
    BOTH("Ambos"),
}

/** Configurações gerais do app (seção 10 da especificação). */
data class AppSettings(
    val userName: String = "",
    /** Último grupo usado na navegação (monitor). */
    val groupName: String = "",
    val role: UserRole = UserRole.COORDINATOR,
    val deviationDistanceMeters: Int = DEFAULT_DEVIATION_DISTANCE_M,
    val deviationTimeSeconds: Int = DEFAULT_DEVIATION_TIME_S,
    val stopDetectionSeconds: Int = DEFAULT_STOP_DETECTION_S,
    val positionIntervalSeconds: Int = DEFAULT_POSITION_INTERVAL_S,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.BOTH,
    /** Distância em que o monitor é considerado chegado ao ponto. */
    val arrivalRadiusMeters: Int = DEFAULT_ARRIVAL_RADIUS_M,
) {
    companion object {
        const val DEFAULT_DEVIATION_DISTANCE_M = 30
        const val DEFAULT_DEVIATION_TIME_S = 5 * 60
        const val DEFAULT_STOP_DETECTION_S = 25
        const val DEFAULT_POSITION_INTERVAL_S = 30
        const val DEFAULT_ARRIVAL_RADIUS_M = 15
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map(::currentFrom)

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val s = transform(currentFrom(p))
            p[USER_NAME] = s.userName
            p[GROUP_NAME] = s.groupName
            p[ARRIVAL_RADIUS] = s.arrivalRadiusMeters
            p[ROLE] = s.role.name
            p[DEVIATION_DISTANCE] = s.deviationDistanceMeters
            p[DEVIATION_TIME] = s.deviationTimeSeconds
            p[STOP_DETECTION] = s.stopDetectionSeconds
            p[POSITION_INTERVAL] = s.positionIntervalSeconds
            p[COORDINATE_FORMAT] = s.coordinateFormat.name
        }
    }

    private fun currentFrom(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            userName = p[USER_NAME] ?: d.userName,
            groupName = p[GROUP_NAME] ?: d.groupName,
            arrivalRadiusMeters = p[ARRIVAL_RADIUS] ?: d.arrivalRadiusMeters,
            role = p[ROLE]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: d.role,
            deviationDistanceMeters = p[DEVIATION_DISTANCE] ?: d.deviationDistanceMeters,
            deviationTimeSeconds = p[DEVIATION_TIME] ?: d.deviationTimeSeconds,
            stopDetectionSeconds = p[STOP_DETECTION] ?: d.stopDetectionSeconds,
            positionIntervalSeconds = p[POSITION_INTERVAL] ?: d.positionIntervalSeconds,
            coordinateFormat = p[COORDINATE_FORMAT]
                ?.let { runCatching { CoordinateFormat.valueOf(it) }.getOrNull() }
                ?: d.coordinateFormat,
        )
    }

    private companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val GROUP_NAME = stringPreferencesKey("group_name")
        val ARRIVAL_RADIUS = intPreferencesKey("arrival_radius_m")
        val ROLE = stringPreferencesKey("role")
        val DEVIATION_DISTANCE = intPreferencesKey("deviation_distance_m")
        val DEVIATION_TIME = intPreferencesKey("deviation_time_s")
        val STOP_DETECTION = intPreferencesKey("stop_detection_s")
        val POSITION_INTERVAL = intPreferencesKey("position_interval_s")
        val COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
    }
}
