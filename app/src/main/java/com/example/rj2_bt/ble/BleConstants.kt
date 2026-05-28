package com.example.rj2_bt.ble

import java.util.UUID

object BleConstants {
    const val SCAN_DURATION_MS = 15_000L
    const val BLUNO_TARGET_MAC = "94:A9:A8:3D:AB:0F"

    const val DELAY_AFTER_CONNECT_MS = 280L
    const val DELAY_AFTER_DISCOVER_MS = 220L
    const val DELAY_BEFORE_NOTIFY_MS = 160L

    const val GATT_ERROR_133 = 133
    const val NOTIFY_RETRY_MAX = 4

    val UUID_BLUNO_SERIAL: UUID =
        UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb")
    val UUID_NORDIC_WRITE: UUID =
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val UUID_NORDIC_NOTIFY: UUID =
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    val UART_WRITE_UUIDS = listOf(UUID_BLUNO_SERIAL, UUID_NORDIC_WRITE)
    val UART_NOTIFY_UUIDS = listOf(UUID_BLUNO_SERIAL, UUID_NORDIC_NOTIFY)

    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun shortUuid(uuid: UUID): String = uuid32(uuid)

    fun uuid32(uuid: UUID): String = uuid.toString().lowercase().substringBefore('-')

    fun matchesShortUuid(uuid: UUID, candidates: List<UUID>): Boolean {
        val key = uuid32(uuid)
        return candidates.any { uuid32(it) == key || it == uuid }
    }
}
