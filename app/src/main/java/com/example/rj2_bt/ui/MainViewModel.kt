package com.example.rj2_bt.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rj2_bt.ble.BleDeviceItem
import com.example.rj2_bt.ble.BleRepository
import com.example.rj2_bt.ble.ConnectionStatus
import com.example.rj2_bt.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.ADAPTER_OFF,
    val isScanning: Boolean = false,
    val devices: List<BleDeviceItem> = emptyList(),
    val selectedAddress: String? = null,
    val angle: Float = 90f,
    val angle2: Float = 90f,
    val receivedData: String = "",
    val permissionsGranted: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val appLog = AppLog()
    private val repository = BleRepository(application.applicationContext, appLog)

    private val _angle = MutableStateFlow(90f)
    val angle: StateFlow<Float> = _angle.asStateFlow()

    private val _angle2 = MutableStateFlow(90f)
    val angle2: StateFlow<Float> = _angle2.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private data class BleFlows(
        val status: ConnectionStatus,
        val scanning: Boolean,
        val devices: List<BleDeviceItem>,
        val selected: String?,
        val received: String,
    )

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            repository.connectionStatus,
            repository.isScanning,
            repository.devices,
            repository.selectedAddress,
            repository.receivedData,
        ) { status, scanning, devices, selected, received ->
            BleFlows(status, scanning, devices, selected, received)
        },
        combine(_angle, _angle2) { a, a2 -> a to a2 },
        _permissionsGranted,
    ) { ble, (angle, angle2), perms ->
        MainUiState(
            connectionStatus = ble.status,
            isScanning = ble.scanning,
            devices = ble.devices,
            selectedAddress = ble.selected,
            angle = angle,
            angle2 = angle2,
            receivedData = ble.received,
            permissionsGranted = perms,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainUiState(),
    )

    val debugLog = appLog.text
    val scanSessionText = repository.scanSessionText

    private var autoScanPending = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    repository.refreshAdapterState()
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON && _permissionsGranted.value) {
                        autoScanPending = true
                        startScan()
                    }
                }
            }
        }
    }

    init {
        repository.refreshAdapterState()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                bluetoothReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(bluetoothReceiver, filter)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _permissionsGranted.value = granted
        repository.refreshAdapterState()
        if (granted) {
            repository.appLog.append("Permissions granted")
            if (repository.connectionStatus.value != ConnectionStatus.ADAPTER_OFF) {
                startScan()
            } else {
                autoScanPending = true
            }
        } else {
            repository.appLog.append("Permissions denied")
        }
    }

    fun refreshPermissions(granted: Boolean) {
        _permissionsGranted.value = granted
    }

    fun startScan() {
        if (!_permissionsGranted.value) return
        autoScanPending = false
        repository.startScan()
    }

    fun stopScan() = repository.stopScan()

    fun selectDevice(address: String) = repository.selectDevice(address)

    fun connect() = repository.connectSelected()

    fun disconnect() = repository.disconnect()

    fun setAngle(value: Float) {
        _angle.value = value.coerceIn(0f, 180f)
    }

    fun sendAngle() = repository.sendAngle(_angle.value.toInt())

    fun setAngle2(value: Float) {
        _angle2.value = value.coerceIn(0f, 180f)
    }

    fun sendAngle2() = repository.sendAngle2(_angle2.value.toInt())

    fun clearDebugLog() = appLog.clear()

    fun clearScanSession() = repository.clearScanSessionLog()

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {
        }
        repository.cleanup()
    }
}
