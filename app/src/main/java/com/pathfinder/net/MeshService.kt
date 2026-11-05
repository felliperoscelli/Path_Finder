package com.pathfinder.net

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pathfinder.PathFinderApp
import com.pathfinder.recording.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Mantém a rede local (Nearby) ligada em segundo plano durante o evento. */
class MeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as PathFinderApp).container }
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground("Ligando a rede…")
        if (intent?.action == ACTION_RESTART) {
            // Novo código do evento ou nome: religa a rede com os dados atuais.
            watcher?.cancel()
            watcher = null
            container.nearby.stop()
        }
        if (intent?.action == ACTION_STOP) {
            container.nearby.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                val s = container.settingsRepository.current()
                container.mesh.eventCode = EventCode.normalize(s.eventCode)
                container.nearby.start(s.userName, s.role.name, s.eventCode)
                container.nearby.state.collect { state ->
                    val n = state.connected.size
                    val text = when {
                        state.error != null -> state.error
                        n == 0 -> "Procurando aparelhos…"
                        n == 1 -> "1 aparelho conectado"
                        else -> "$n aparelhos conectados"
                    }
                    getSystemService(NotificationManager::class.java)
                        .notify(Notifications.ID_NETWORK, Notifications.network(this@MeshService, text))
                }
            }
        }
        // Se o sistema encerrar o processo, o serviço volta e religa a rede.
        return START_STICKY
    }

    private fun goForeground(text: String) {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_NETWORK,
            Notifications.network(this, text),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
    }

    override fun onDestroy() {
        container.nearby.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.pathfinder.net.STOP"
        const val ACTION_RESTART = "com.pathfinder.net.RESTART"

        /** Religa a rede, se estiver ligada (ex.: depois de mudar o código do evento). */
        fun restartIfRunning(context: Context, running: Boolean) {
            if (running) {
                ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java).setAction(ACTION_RESTART))
            }
        }

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
