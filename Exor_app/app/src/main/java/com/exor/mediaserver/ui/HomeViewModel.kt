package com.exor.mediaserver.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exor.mediaserver.media.LibraryCounts
import com.exor.mediaserver.media.MediaStoreRepo
import com.exor.mediaserver.scan.DeviceScanner
import com.exor.mediaserver.scan.RendererDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaStoreRepo(app)

    val counts = MutableStateFlow(LibraryCounts())
    val devices = MutableStateFlow<List<RendererDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    val scannedOnce = MutableStateFlow(false)

    fun refreshCounts() {
        viewModelScope.launch(Dispatchers.IO) { counts.value = repo.libraryCounts() }
    }

    fun scan() {
        if (scanning.value) return
        scanning.value = true
        viewModelScope.launch {
            try {
                devices.value = DeviceScanner().scan()
            } finally {
                scanning.value = false
                scannedOnce.value = true
            }
        }
    }
}
