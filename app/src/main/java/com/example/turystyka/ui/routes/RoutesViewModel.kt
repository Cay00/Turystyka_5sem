package com.example.turystyka.ui.routes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RoutesViewModel : ViewModel() {

    private val _savedRoutes = MutableLiveData<List<String>>().apply {
        value = emptyList()
    }
    val savedRoutes: LiveData<List<String>> = _savedRoutes

    fun saveRoute(routeName: String) {
        val currentRoutes = _savedRoutes.value?.toMutableList() ?: mutableListOf()
        currentRoutes.add(routeName)
        _savedRoutes.value = currentRoutes
    }
}
