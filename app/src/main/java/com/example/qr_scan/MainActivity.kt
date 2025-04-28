package com.example.qr_scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qr_scan.ui.theme.Qr_scanTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var nfcManager: NFCManager
    private var nfcAdapter: NfcAdapter? = null
    
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Para Android 11 y superior
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Para Android 6.0 hasta Android 10
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta NFC", Toast.LENGTH_LONG).show()
        } else if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "NFC está desactivado. Por favor, actívalo", Toast.LENGTH_LONG).show()
        }

        nfcManager = NFCManager(this)
        checkStoragePermission()
        
        setContent {
            Qr_scanTheme {
                MainContent(nfcManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                nfcManager.onResume()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                nfcManager.onPause()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            nfcManager.onNewIntent(intent)
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }
}

@Composable
fun MainContent(nfcManager: NFCManager) {
    val nfcStatus by nfcManager.nfcStatus.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(nfcStatus) {
        when (nfcStatus) {
            is NFCManager.NFCStatus.Error -> {
                Toast.makeText(context, (nfcStatus as NFCManager.NFCStatus.Error).message, Toast.LENGTH_SHORT).show()
            }
            is NFCManager.NFCStatus.Success -> {
                Toast.makeText(context, (nfcStatus as NFCManager.NFCStatus.Success).message, Toast.LENGTH_SHORT).show()
            }
            else -> {} // Handle other states as needed
        }
    }
    
    MainScreen(nfcManager = nfcManager)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: HotelViewModel = viewModel(),
    nfcManager: NFCManager
) {
    val hotelInfo by viewModel.hotelInfo.collectAsState()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cameraPermissionState.status.isGranted) {
                if (hotelInfo == null) {
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                    ) {
                        QRScanner { code ->
                            viewModel.processQRCode(code)
                        }
                    }
                } else {
                    HotelCard(
                        hotelInfo = hotelInfo!!,
                        onDismiss = { viewModel.clearHotelInfo() },
                        nfcManager = nfcManager
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Se necesita permiso de cámara para escanear códigos QR")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Solicitar Permiso")
                    }
                }
            }
        }
    }
}