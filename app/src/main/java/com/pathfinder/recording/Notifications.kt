package com.pathfinder.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pathfinder.MainActivity
import com.pathfinder.R
import com.pathfinder.navigation.NavigationService

object Notifications {
    const val CHANNEL_RECORDING = "recording"
    const val CHANNEL_STOP = "stop_detected"
    const val ID_RECORDING = 1
    const val ID_STOP = 2
    const val CHANNEL_NAVIGATION = "navigation"
    const val CHANNEL_DEVIATION = "deviation"
    const val ID_NAVIGATION = 3
    const val ID_DEVIATION = 4

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Gravação de trilha", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STOP, "Sugestão de ponto", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisa quando você fica parado durante a gravação"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NAVIGATION, "Navegação", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEVIATION, "Alerta de desvio", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisa quando o grupo sai da rota"
                // Som e vibração são tocados pelo próprio app (AlertPlayer), como alarme.
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    /** Abre o app direto no mapa (ou na navegação) da trilha. */
    fun openTrailIntent(context: Context, trailId: Long, navigation: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TRAIL_ID, trailId)
            .putExtra(MainActivity.EXTRA_OPEN_NAVIGATION, navigation)
        return PendingIntent.getActivity(
            context, trailId.toInt() * 2 + if (navigation) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun navigationAction(context: Context, action: String, label: String) =
        NotificationCompat.Action(
            0, label,
            PendingIntent.getService(
                context, action.hashCode(),
                Intent(context, NavigationService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    fun navigation(context: Context, trailId: Long, title: String, detail: String) =
        NotificationCompat.Builder(context, CHANNEL_NAVIGATION)
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentTitle(title)
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openTrailIntent(context, trailId, navigation = true))
            .build()

    fun deviation(context: Context, trailId: Long, detail: String) =
        NotificationCompat.Builder(context, CHANNEL_DEVIATION)
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentTitle("Fora da rota!")
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openTrailIntent(context, trailId, navigation = true))
            .addAction(navigationAction(context, NavigationService.ACTION_SILENCE, "Silenciar"))
            .build()

    fun recording(context: Context, trailId: Long, trailName: String, detail: String) =
        NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentTitle("Gravando \"$trailName\"")
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openTrailIntent(context, trailId))
            .addAction(
                0, "Parar",
                PendingIntent.getService(
                    context, 0,
                    Intent(context, TrailRecordingService::class.java).setAction(TrailRecordingService.ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    fun stopDetected(context: Context, trailId: Long, seconds: Int) =
        NotificationCompat.Builder(context, CHANNEL_STOP)
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentTitle("Marcar ponto aqui?")
            .setContentText("Você está parado há $seconds s. Toque para marcar o ponto.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openTrailIntent(context, trailId))
            .build()
}
