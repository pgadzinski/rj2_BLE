package com.example.rj2_bt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rj2_bt.permissions.BlePermissions
import com.example.rj2_bt.ui.DebugScreen
import com.example.rj2_bt.ui.MainScreen
import com.example.rj2_bt.ui.MainViewModel
import com.example.rj2_bt.ui.theme.RJ2Theme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        viewModel.onPermissionsResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher.launch(BlePermissions.requiredPermissions())

        setContent {
            val navController = rememberNavController()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val debugLog by viewModel.debugLog.collectAsStateWithLifecycle()
            val scanSession by viewModel.scanSessionText.collectAsStateWithLifecycle()

            RJ2Theme {
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            uiState = uiState,
                            onScan = { viewModel.startScan() },
                            onConnect = { viewModel.connect() },
                            onDisconnect = { viewModel.disconnect() },
                            onSelectDevice = { viewModel.selectDevice(it) },
                            onAngleChange = { viewModel.setAngle(it) },
                            onSend = { viewModel.sendAngle() },
                            onAngleChange2 = { viewModel.setAngle2(it) },
                            onSend2 = { viewModel.sendAngle2() },
                            onOpenDebug = { navController.navigate("debug") },
                        )
                    }
                    composable("debug") {
                        DebugScreen(
                            scanSession = scanSession,
                            rollingLog = debugLog,
                            onBack = { navController.popBackStack() },
                            onClear = {
                                viewModel.clearDebugLog()
                                viewModel.clearScanSession()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions(BlePermissions.hasAll(this))
    }

}
