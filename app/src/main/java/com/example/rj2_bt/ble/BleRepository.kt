package com.example.rj2_bt.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.rj2_bt.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleRepository(
    private val context: Context,
    val appLog: AppLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null
    private var scanSessionLog = StringBuilder()
    private val scanMap = ConcurrentHashMap<String, BleDeviceItem>()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var pendingCccd: CompletableDeferred<Boolean>? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.ADAPTER_OFF)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val devices: StateFlow<List<BleDeviceItem>> = _devices.asStateFlow()

    private val _selectedAddress = MutableStateFlow<String?>(null)
    val selectedAddress: StateFlow<String?> = _selectedAddress.asStateFlow()

    private val _receivedData = MutableStateFlow("")
    val receivedData: StateFlow<String> = _receivedData.asStateFlow()

    private val _scanSessionText = MutableStateFlow("")
    val scanSessionText: StateFlow<String> = _scanSessionText.asStateFlow()

    fun refreshAdapterState() {
        val on = adapter?.isEnabled == true
        _connectionStatus.value = when {
            !on -> ConnectionStatus.ADAPTER_OFF
            gatt != null -> ConnectionStatus.CONNECTED
            else -> ConnectionStatus.DISCONNECTED
        }
    }

    fun selectDevice(address: String?) {
        _selectedAddress.value = address
    }

    fun clearReceived() {
        _receivedData.value = ""
    }

    fun clearScanSessionLog() {
        scanSessionLog = StringBuilder()
        _scanSessionText.value = ""
    }

    fun startScan() {
        if (_isScanning.value) return
        if (adapter?.isEnabled != true) {
            log("Scan blocked: Bluetooth adapter off")
            refreshAdapterState()
            return
        }
        val leScanner = scanner ?: run {
            log("Scan blocked: no BLE scanner")
            return
        }

        stopScanInternal()
        scanMap.clear()
        publishDevices()
        scanSessionLog = StringBuilder()
        appendScanSession("=== BLE scan start (${BleConstants.SCAN_DURATION_MS}ms) ===")

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsBuilder.setLegacy(true)
            settingsBuilder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
        }

        val settings = settingsBuilder.build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                log("Scan failed: error=$errorCode")
                appendScanSession("Scan failed: error=$errorCode")
                finishScan()
            }
        }
        scanCallback = callback
        _isScanning.value = true
        log("Starting BLE scan (15s, LOW_LATENCY, legacy=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O})")
        leScanner.startScan(null, settings, callback)

        mainHandler.postDelayed({ finishScan() }, BleConstants.SCAN_DURATION_MS)
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val name = result.scanRecord?.deviceName ?: device.name
        val rssi = result.rssi
        val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.isConnectable
        } else {
            true
        }

        scanMap[address] = BleDeviceItem(address, name, rssi, connectable)

        val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
        val mfg = result.scanRecord?.manufacturerSpecificData
        val mfgStr = buildString {
            if (mfg != null && mfg.size() > 0) {
                for (i in 0 until mfg.size()) {
                    val id = mfg.keyAt(i)
                    val data = mfg.valueAt(i)
                    append("id=0x${id.toString(16)} hex=${data.toHex()}")
                    if (i < mfg.size() - 1) append("; ")
                }
            } else {
                append("none")
            }
        }

        val displayName = name?.takeIf { it.isNotBlank() } ?: "UNNAMED"
        val line = buildString {
            append("MAC=$address name=$displayName RSSI=$rssi connectable=$connectable")
            append(" services=${if (serviceUuids.isEmpty()) "none" else serviceUuids.joinToString()}")
            append(" mfg=$mfgStr")
        }
        appendScanSession(line)
        log(line)

        if (address.equals(BleConstants.BLUNO_TARGET_MAC, ignoreCase = true)) {
            val marker = "* BLUNO TARGET *"
            appendScanSession(marker)
            log(marker)
        }
    }

    private fun finishScan() {
        if (!_isScanning.value) return
        stopScanInternal()
        val sorted = scanMap.values.sortedByDescending { it.rssi }
        _devices.value = sorted
        appendScanSession("=== Scan complete: ${sorted.size} device(s), sorted by RSSI ===")
        sorted.forEach { d ->
            appendScanSession("  ${d.displayLabel}  ${d.address}  RSSI=${d.rssi}")
        }
        _scanSessionText.value = scanSessionLog.toString()
        log("Scan complete: ${sorted.size} devices")
    }

    private fun stopScanInternal() {
        val cb = scanCallback
        if (cb != null && scanner != null && adapter?.isEnabled == true) {
            try {
                scanner?.stopScan(cb)
            } catch (_: Exception) {
            }
        }
        scanCallback = null
        _isScanning.value = false
    }

    fun stopScan() {
        if (_isScanning.value) {
            appendScanSession("=== Scan stopped early ===")
            _scanSessionText.value = scanSessionLog.toString()
        }
        finishScan()
    }

    private fun publishDevices() {
        _devices.value = scanMap.values.sortedByDescending { it.rssi }
    }

    fun connectSelected() {
        val address = _selectedAddress.value ?: run {
            log("Connect: no device selected")
            return
        }
        connect(address)
    }

    fun connect(address: String) {
        if (adapter?.isEnabled != true) {
            log("Connect blocked: adapter off")
            return
        }
        stopScan()
        disconnect()

        val device = adapter?.getRemoteDevice(address) ?: run {
            log("Connect failed: invalid address $address")
            return
        }

        log("Connecting to $address …")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        writeChar = null
        notifyChar = null
        gatt?.let { g ->
            try {
                g.disconnect()
                g.close()
            } catch (_: Exception) {
            }
        }
        gatt = null
        refreshAdapterState()
        log("Disconnected")
    }

    fun cleanup() {
        stopScan()
        disconnect()
    }

    fun sendAngle(angle: Int) = sendServo(1, angle)

    fun sendAngle2(angle: Int) = sendServo(2, angle)

    private fun sendServo(servo: Int, angle: Int) {
        val clamped = angle.coerceIn(0, 180)
        val payload = "S$servo:$clamped\n"
        val characteristic = writeChar
        val g = gatt
        if (characteristic == null || g == null) {
            log("Send failed: not connected / no write characteristic")
            return
        }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = g.writeCharacteristic(characteristic, bytes, characteristic.writeType)
            log("Write S$servo:$clamped → status=$code")
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            val ok = g.writeCharacteristic(characteristic)
            log("Write S$servo:$clamped → ok=$ok")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("GATT connected (status=$status)")
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    scope.launch {
                        delay(BleConstants.DELAY_AFTER_CONNECT_MS)
                        log("Discovering services …")
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("GATT disconnected (status=$status)")
                    writeChar = null
                    notifyChar = null
                    refreshAdapterState()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed: $status")
                return
            }
            log("Services discovered")
            scope.launch {
                delay(BleConstants.DELAY_AFTER_DISCOVER_MS)
                pickUartCharacteristics(gatt)
                delay(BleConstants.DELAY_BEFORE_NOTIFY_MS)
                enableNotifyWithRetry(gatt, attempt = 0)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            onNotifyData(bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotifyData(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            log("onCharacteristicWrite status=$status")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            log("CCCD write status=$status")
            if (status == BleConstants.GATT_ERROR_133) {
                log("GATT 133 on descriptor write")
            }
            pendingCccd?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingCccd = null
        }
    }

    private fun onNotifyData(bytes: ByteArray) {
        val text = String(bytes, Charsets.UTF_8)
        _receivedData.update { it + text }
        log("RX: ${text.trimEnd()}")
    }

    private fun pickUartCharacteristics(gatt: BluetoothGatt) {
        var foundWrite: BluetoothGattCharacteristic? = null
        var foundNotify: BluetoothGattCharacteristic? = null

        for (service in gatt.services) {
            for (ch in service.characteristics) {
                val uuid = ch.uuid
                if (foundWrite == null && BleConstants.matchesShortUuid(uuid, BleConstants.UART_WRITE_UUIDS)) {
                    foundWrite = ch
                    log("Write char: $uuid (${BleConstants.shortUuid(uuid)})")
                }
                if (foundNotify == null && BleConstants.matchesShortUuid(uuid, BleConstants.UART_NOTIFY_UUIDS)) {
                    val props = ch.properties
                    if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                        props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    ) {
                        foundNotify = ch
                        log("Notify char: $uuid (${BleConstants.shortUuid(uuid)})")
                    }
                }
                if (foundWrite == null &&
                    BleConstants.matchesShortUuid(uuid, BleConstants.UART_NOTIFY_UUIDS) &&
                    ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                ) {
                    foundWrite = ch
                }
            }
        }

        if (foundWrite == null || foundNotify == null) {
            for (service in gatt.services) {
                for (ch in service.characteristics) {
                    if (foundWrite == null &&
                        ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                    ) {
                        foundWrite = ch
                        log("Fallback write char: ${ch.uuid}")
                    }
                    if (foundNotify == null &&
                        (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    ) {
                        foundNotify = ch
                        log("Fallback notify char: ${ch.uuid}")
                    }
                }
            }
        }

        writeChar = foundWrite
        notifyChar = foundNotify
        if (foundWrite == null) log("WARNING: no UART write characteristic found")
        if (foundNotify == null) log("WARNING: no UART notify characteristic found")
    }

    private fun enableNotifyWithRetry(gatt: BluetoothGatt, attempt: Int) {
        val ch = notifyChar
        if (ch == null) {
            log("Notify enable skipped: no notify characteristic")
            return
        }
        scope.launch {
            val ok = enableNotifications(gatt, ch)
            if (!ok && attempt < BleConstants.NOTIFY_RETRY_MAX) {
                val backoff = (attempt + 1) * 200L
                log("Notify enable failed — retry ${attempt + 1}/${BleConstants.NOTIFY_RETRY_MAX} in ${backoff}ms")
                delay(backoff)
                enableNotifyWithRetry(gatt, attempt + 1)
            } else if (ok) {
                log("Notifications enabled")
            } else {
                log("Notify enable gave up after ${BleConstants.NOTIFY_RETRY_MAX} retries")
            }
        }
    }

    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val okSet = gatt.setCharacteristicNotification(characteristic, true)
        if (!okSet) {
            log("setCharacteristicNotification returned false")
            return false
        }
        val cccd = characteristic.getDescriptor(BleConstants.CCCD_UUID)
            ?: characteristic.descriptors.firstOrNull()
        if (cccd == null) {
            log("No CCCD descriptor found")
            return false
        }
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return writeDescriptor(gatt, cccd, enable)
    }

    private suspend fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingCccd = deferred

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        if (!started) {
            pendingCccd = null
            return false
        }

        return try {
            withTimeout(2_000L) {
                deferred.await()
            }
        } catch (_: Exception) {
            pendingCccd = null
            false
        }
    }

    private fun appendScanSession(line: String) {
        scanSessionLog.appendLine(line)
    }

    private fun log(msg: String) {
        appLog.append(msg)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
