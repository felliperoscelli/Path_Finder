package com.pathfinder

import android.app.Application
import com.pathfinder.map.TopoMap
import com.pathfinder.recording.Notifications

class PathFinderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TopoMap.configure(this)
        Notifications.createChannels(this)
        container = AppContainer(this)
    }
}
