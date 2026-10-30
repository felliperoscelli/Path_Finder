package com.pathfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pathfinder.ui.PathFinderNavHost
import com.pathfinder.ui.theme.PathFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PathFinderTheme {
                PathFinderNavHost()
            }
        }
    }
}
