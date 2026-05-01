package dev.akhilnarang.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import dev.akhilnarang.smsforwarder.ui.SmsForwarderScreen
import dev.akhilnarang.smsforwarder.ui.SmsForwarderViewModel
import dev.akhilnarang.smsforwarder.ui.theme.SmsForwarderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as SmsForwarderApp
            val viewModel: SmsForwarderViewModel =
                viewModel(factory = SmsForwarderViewModel.Factory)
            var hasReceiveSmsPermission by remember {
                mutableStateOf(hasPermission(Manifest.permission.RECEIVE_SMS))
            }
            var hasReadSmsPermission by remember {
                mutableStateOf(hasPermission(Manifest.permission.READ_SMS))
            }
            val permissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    hasReceiveSmsPermission =
                        result[Manifest.permission.RECEIVE_SMS] == true ||
                            hasPermission(Manifest.permission.RECEIVE_SMS)
                    hasReadSmsPermission =
                        result[Manifest.permission.READ_SMS] == true ||
                            hasPermission(Manifest.permission.READ_SMS)
                }

            SmsForwarderTheme {
                SmsForwarderScreen(
                    viewModel = viewModel,
                    hasReceiveSmsPermission = hasReceiveSmsPermission,
                    hasReadSmsPermission = hasReadSmsPermission,
                    onRequestSmsPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS,
                            ),
                        )
                    },
                )
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
