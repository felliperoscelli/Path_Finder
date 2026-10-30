package com.pathfinder.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.pathfinder.AppContainer
import com.pathfinder.PathFinderApp

/** Acesso ao [AppContainer] dentro das fábricas de ViewModel. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PathFinderApp).container
