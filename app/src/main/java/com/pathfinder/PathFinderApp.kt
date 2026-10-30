package com.pathfinder

import android.app.Application
import com.pathfinder.map.TopoMap

class PathFinderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TopoMap.configure(this)
        container = AppContainer(this)
    }
}
