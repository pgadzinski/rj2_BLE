package com.example.rj2_bt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rj2_bt.ble.BleDeviceItem
import com.example.rj2_bt.ble.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAngleChange: (Float) -> Unit,
    onSend: () -> Unit,
    onAngleChange2: (Float) -> Unit,
    onSend2: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("RJ2", style = MaterialTheme.typography.headlineMedium)
        StatusChip(uiState.connectionStatus)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onScan,
                enabled = !uiState.isScanning && uiState.permissionsGranted,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (uiState.isScanning) "Scanning…" else "Scan BLE (15s)")
            }
            if (uiState.isScanning) {
                CircularProgressIndicator(modifier = Modifier.height(32.dp))
            }
        }

        DeviceDropdown(
            devices = uiState.devices,
            selectedAddress = uiState.selectedAddress,
            enabled = !uiState.isScanning,
            onSelect = onSelectDevice,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onConnect,
                enabled = uiState.selectedAddress != null &&
                    uiState.connectionStatus != ConnectionStatus.CONNECTED &&
                    !uiState.isScanning,
                modifier = Modifier.weight(1f),
            ) {
                Text("Connect")
            }
            OutlinedButton(
                onClick = onDisconnect,
                enabled = uiState.connectionStatus == ConnectionStatus.CONNECTED,
                modifier = Modifier.weight(1f),
            ) {
                Text("Disconnect")
            }
        }

        Text("Servo 1: ${uiState.angle.toInt()}°", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = uiState.angle,
            onValueChange = onAngleChange,
            valueRange = 0f..180f,
            steps = 179,
            enabled = uiState.connectionStatus == ConnectionStatus.CONNECTED,
        )
        Button(
            onClick = onSend,
            enabled = uiState.connectionStatus == ConnectionStatus.CONNECTED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send S1:${uiState.angle.toInt()}")
        }

        Text("Servo 2: ${uiState.angle2.toInt()}°", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = uiState.angle2,
            onValueChange = onAngleChange2,
            valueRange = 0f..180f,
            steps = 179,
            enabled = uiState.connectionStatus == ConnectionStatus.CONNECTED,
        )
        Button(
            onClick = onSend2,
            enabled = uiState.connectionStatus == ConnectionStatus.CONNECTED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send S2:${uiState.angle2.toInt()}")
        }

        Text("Received data", style = MaterialTheme.typography.titleSmall)
        Text(
            text = uiState.receivedData.ifEmpty { "(waiting for notify data…)" },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(8.dp),
            color = Color(0xFF00E676),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )

        OutlinedButton(onClick = onOpenDebug, modifier = Modifier.fillMaxWidth()) {
            Text("Debug log")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusChip(status: ConnectionStatus) {
    val (label, color) = when (status) {
        ConnectionStatus.ADAPTER_OFF -> "ADAPTER OFF" to Color(0xFFE57373)
        ConnectionStatus.DISCONNECTED -> "DISCONNECTED" to Color(0xFFFFB74D)
        ConnectionStatus.CONNECTED -> "CONNECTED" to Color(0xFF81C784)
    }
    Text(
        text = "Status: $label",
        color = color,
        style = MaterialTheme.typography.titleMedium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(
    devices: List<BleDeviceItem>,
    selectedAddress: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.find { it.address == selectedAddress }
    val label = selected?.let { "${it.displayLabel} (${it.rssi} dBm)" }
        ?: selectedAddress
        ?: "Select device"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        TextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No devices — run scan") },
                    onClick = { expanded = false },
                )
            } else {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Text("${device.displayLabel}  ${device.address}  RSSI=${device.rssi}")
                        },
                        onClick = {
                            onSelect(device.address)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
