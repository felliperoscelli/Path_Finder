package com.pathfinder.net

import android.content.Context
import java.util.UUID

/** Identificador fixo deste aparelho na rede (gerado na primeira execução). */
class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString(KEY, null) ?: UUID.randomUUID().toString().replace("-", "").take(12).also {
        prefs.edit().putString(KEY, it).apply()
    }

    private companion object {
        const val KEY = "device_id"
    }
}
