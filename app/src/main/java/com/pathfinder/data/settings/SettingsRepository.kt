package com.pathfinder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pathfinder.power.PowerMode
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
    /** Código do evento: só aparelhos com o mesmo código formam a rede. */
    val eventCode: String = "",
    /** SHA-256 do PIN exigido para usar o perfil Coordenador (vazio = sem PIN). */
    val coordinatorPinHash: String = "",
    /** Minutos sem notícias de um grupo até o alarme do coordenador. */
    val noSignalMinutes: Int = DEFAULT_NO_SIGNAL_MIN,
    /** Manter a tela acesa durante a navegação. */
    val keepScreenOn: Boolean = true,
    /** Cores de alto contraste e letras maiores, para ler no sol. */
    val highContrast: Boolean = false,
    /** Economia de bateria na navegação. */
    val powerMode: PowerMode = PowerMode.AUTO,
    /** Mostra o botão de ensaio com caminhada simulada. */
    val allowSimulation: Boolean = false,
) {
    companion object {
        const val DEFAULT_DEVIATION_DISTANCE_M = 30
        const val DEFAULT_DEVIATION_TIME_S = 5 * 60
        const val DEFAULT_STOP_DETECTION_S = 25
        const val DEFAULT_POSITION_INTERVAL_S = 30
        const val DEFAULT_ARRIVAL_RADIUS_M = 15
        const val DEFAULT_NO_SIGNAL_MIN = 10
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
            p[EVENT_CODE] = s.eventCode
            p[COORDINATOR_PIN] = s.coordinatorPinHash
            p[NO_SIGNAL] = s.noSignalMinutes
            p[KEEP_SCREEN_ON] = s.keepScreenOn
            p[HIGH_CONTRAST] = s.highContrast
            p[POWER_MODE] = s.powerMode.name
            p[ALLOW_SIMULATION] = s.allowSimulation
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
            eventCode = p[EVENT_CODE] ?: d.eventCode,
            coordinatorPinHash = p[COORDINATOR_PIN] ?: d.coordinatorPinHash,
            noSignalMinutes = p[NO_SIGNAL] ?: d.noSignalMinutes,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: d.keepScreenOn,
            highContrast = p[HIGH_CONTRAST] ?: d.highContrast,
            powerMode = p[POWER_MODE]?.let { runCatching { PowerMode.valueOf(it) }.getOrNull() } ?: d.powerMode,
            allowSimulation = p[ALLOW_SIMULATION] ?: d.allowSimulation,
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
        val EVENT_CODE = stringPreferencesKey("event_code")
        val COORDINATOR_PIN = stringPreferencesKey("coordinator_pin_sha256")
        val NO_SIGNAL = intPreferencesKey("no_signal_min")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val POWER_MODE = stringPreferencesKey("power_mode")
        val ALLOW_SIMULATION = booleanPreferencesKey("allow_simulation")
    }
}
