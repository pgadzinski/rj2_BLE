package com.example.rj2_bt.ble

data class BleDeviceItem(
    val address: String,
    val name: String?,
    val rssi: Int,
    val connectable: Boolean,
) {
    val displayLabel: String
        get() = if (!name.isNullOrBlank()) name else address
}
