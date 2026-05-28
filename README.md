# RJ2 — Native Android BLE (Kotlin)

Control Arduino Uno R3 / Bluno / Romeo BLE / Nordic UART boards over BLE. Send servo angles with `S1:0-180\n` and read serial data via notifications.

## Stack

- Kotlin, Jetpack Compose, MVVM (`StateFlow` + coroutines)
- Android BLE API only (`BluetoothManager`, `BluetoothLeScanner`, `BluetoothGatt`)
- `minSdk 26`, `compileSdk` / `targetSdk 35`
- Package: `com.example.rj2_bt`

## Setup

1. Open the `RJ2` folder in **Android Studio** (Ladybug or newer recommended).
2. Install **Android SDK 35** and accept licenses.
3. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install on a physical phone (BLE does not work on most emulators):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Purpose |
|------------|---------|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Legacy (API ≤ 30) |
| `BLUETOOTH_SCAN` | BLE scan (API 31+) |
| `BLUETOOTH_CONNECT` | Connect / GATT (API 31+) |
| `ACCESS_FINE_LOCATION` | Required for BLE scan on many devices |

Runtime permission dialog runs on launch. Grant all for scan/connect.

`android.hardware.bluetooth_le` is `required=false` so the app installs on non-BLE devices but BLE features need a LE-capable phone.

## Bluno test steps

1. Power **Bluno** (or target board) and confirm it advertises in **nRF Connect**.
2. Expected test MAC: `94:A9:A8:3D:AB:0F` — when seen during scan, debug log shows:
   ```
   * BLUNO TARGET *
   ```
3. Open **RJ2**, grant permissions, enable Bluetooth if prompted.
4. Tap **Scan BLE (15s)** — unfiltered scan, devices sorted by RSSI.
5. Select your board in the dropdown (name or MAC).
6. Tap **Connect** — app discovers GATT, picks UART characteristics:
   - Bluno serial: `0000dfb1-…`
   - Nordic UART TX/RX: `6e400002` / `6e400003`
7. Move slider **0–180°**, tap **Send** — writes `S1:<angle>\n` (UTF-8).
8. Watch **Received data** (green monospace) for serial replies.
9. Open **Debug log** for full scan session + rolling BLE log (Copy / Clear, auto-trim ~14k chars).

## UART timing

After connect: ~280 ms → service discovery → ~220 ms → pick characteristics → ~160 ms → enable notify (with up to 4 retries on GATT error 133, exponential backoff).

## Project layout

```
app/src/main/java/com/example/rj2_bt/
  ble/          BleRepository, constants, models
  permissions/  Runtime permission helpers
  ui/           Compose screens + MainViewModel
  MainActivity.kt
```

## Troubleshooting

- **No devices in scan**: Location permission + GPS/location on (device-dependent).
- **GATT 133**: Tap Disconnect, wait 2 s, Connect again; app retries notify automatically.
- **Adapter off**: Status shows `ADAPTER OFF` — turn Bluetooth on; app auto-starts scan when BT enables (after permissions).
