package com.example.btchat.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btchat.bluetooth.DeviceScanner
import com.example.btchat.model.Device
import com.example.btchat.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isScanning: Boolean = false,
    val isBluetoothOn: Boolean = true,
    val pairedDevices: List<Device> = emptyList(),
    val discoveredDevices: List<Device> = emptyList(),
    val recentDevices: List<Device> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    app: Application,
    private val scanner: DeviceScanner,
    private val deviceRepo: DeviceRepository
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Live discovered devices
        scanner.devices
            .onEach { list ->
                _uiState.value = _uiState.value.copy(discoveredDevices = list)
            }
            .launchIn(viewModelScope)

        scanner.isScanning
            .onEach { s ->
                _uiState.value = _uiState.value.copy(isScanning = s)
            }
            .launchIn(viewModelScope)

        // Persisted devices (paired + recent)
        deviceRepo.observeAll()
            .onEach { all ->
                _uiState.value = _uiState.value.copy(
                    pairedDevices = all.filter { it.nickname != null || it.signalStrength >= 3 },
                    recentDevices = all.take(10)
                )
            }
            .launchIn(viewModelScope)
    }

    fun toggleScan() {
        if (_uiState.value.isScanning) scanner.stop() else scanner.start()
    }

    fun connectToDevice(device: Device) {
        viewModelScope.launch {
            deviceRepo.save(device.copy(lastSeen = System.currentTimeMillis()))
        }
    }

    fun addManualDevice(mac: String, name: String) {
        viewModelScope.launch {
            deviceRepo.save(Device(name = name, mac = mac))
        }
    }

    fun clearDiscovered() = scanner.clear()

    override fun onCleared() {
        scanner.stop()
        super.onCleared()
    }
}
