package com.pathfinder.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pathfinder.MainActivity
import com.pathfinder.R

object Notifications {
    const val CHANNEL_RECORDING = "recording"
    const val CHANNEL_STOP = "stop_detected"
    const val ID_RECORDING = 1
    const val ID_STOP = 2

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
    }

    /** Abre o app direto no mapa da trilha. */
    fun openTrailIntent(context: Context, trailId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TRAIL_ID, trailId)
        return PendingIntent.getActivity(
            context, trailId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

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
