package com.eagle.vpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipboardManager
import java.io.File
import io.nekohasekai.libbox.Libbox
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val connectionState = mutableStateOf("READY")
    private val profileState = mutableStateOf("No profile imported")
    private lateinit var filePicker: ActivityResultLauncher<Array<String>>
    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) importConfig(result.contents)
    }
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != EagleVpnService.ACTION_STATUS) return
            connectionState.value = intent.getStringExtra("status") ?: "READY"
        }
    }

    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { importConfig(it.readText()) } } catch (e: Exception) { android.widget.Toast.makeText(this, "Could not read selected file", android.widget.Toast.LENGTH_LONG).show() }
        }
        profileState.value = if (File(filesDir, "profile-imported.flag").isFile && File(filesDir, "config.json").isFile) "Profile imported" else "No profile imported"
        vpnPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startVpnService()
                } else {
                    connectionState.value = "PERMISSION DENIED"
                }
            }

        setContent {
            EagleHome(
                status = connectionState.value,
                onConnect = { requestVpnPermission() },
                onDisconnect = { stopVpnService() },
            profile = profileState.value,
            onImportFile = { filePicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            onPaste = { pasteConfig() },
            onScanQr = { qrScanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan sing-box JSON QR").setBeepEnabled(false).setOrientationLocked(false)) }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter(EagleVpnService.ACTION_STATUS)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(statusReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun importConfig(raw: String) {
        try {
            require(raw.trimStart().startsWith("{")) { "Only sing-box JSON profiles are supported" }
            Libbox.checkConfig(raw)
            val target = File(filesDir, "config.json")
            val temp = File(filesDir, "config.json.tmp")
            temp.writeText(raw)
            check(temp.renameTo(target)) { "Could not save profile" }
            File(filesDir, "profile-imported.flag").writeText("ok")
            profileState.value = "Profile imported and validated"
            android.widget.Toast.makeText(this, "Config imported successfully", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            profileState.value = "Import failed: ${e.message ?: "Invalid config"}"
            android.widget.Toast.makeText(this, profileState.value, android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.e("MainActivity", "Config import failed", e)
        }
    }

    private fun pasteConfig() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (raw.isNullOrBlank()) {
            android.widget.Toast.makeText(this, "Clipboard is empty", android.widget.Toast.LENGTH_LONG).show()
        } else importConfig(raw)
    }

    private fun requestVpnPermission() {
        if (!File(filesDir, "config.json").isFile || !File(filesDir, "profile-imported.flag").isFile) {
            connectionState.value = "IMPORT CONFIG FIRST"
            android.widget.Toast.makeText(this, "Import a valid sing-box JSON profile first", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            startVpnService()
        } else {
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }

    private fun startVpnService() {
        connectionState.value = "CONNECTING"
        val intent = Intent(this, EagleVpnService::class.java).apply {
            action = EagleVpnService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            connectionState.value = "ERROR"
            android.util.Log.e("MainActivity", "Could not start VPN service", e)
        }
    }

    private fun stopVpnService() {
        connectionState.value = "DISCONNECTING"
        startService(Intent(this, EagleVpnService::class.java).apply {
            action = EagleVpnService.ACTION_STOP
        })
    }
}

@Composable
fun EagleHome(
    status: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    profile: String,
    onImportFile: () -> Unit,
    onPaste: () -> Unit,
    onScanQr: () -> Unit
) {
    val connected = status == "CONNECTED" || status == "ENGINE_STARTED"
    val busy = status == "CONNECTING" || status == "DISCONNECTING"

    val background = Brush.verticalGradient(
        listOf(Color(0xFF020205), Color(0xFF090A10), Color(0xFF16070A))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("☰", color = Color.White, fontSize = 28.sp)
                Text(
                    "EAGLE VPN",
                    color = Color.White,
                    fontSize = 22.sp,
                    style = MaterialTheme.typography.titleLarge
                )
                Text("⚙", color = Color.White, fontSize = 25.sp)
            }

            Spacer(Modifier.height(70.dp))

            Text(
                "EAGLE",
                color = Color(0xFFFF2020),
                fontSize = 34.sp,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                "SECURE CONNECTION",
                color = Color.White.copy(alpha = .65f),
                fontSize = 12.sp,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(55.dp))

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .shadow(35.dp, CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFF3030), Color(0xFF8B0000), Color(0xFF180307)),
                            radius = 500f
                        ),
                        CircleShape
                    )
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        if (connected) onDisconnect() else onConnect()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF09090D),
                        disabledContainerColor = Color(0xFF09090D)
                    )
                ) {
                    Text(
                        when {
                            busy -> "PLEASE WAIT"
                            connected -> "DISCONNECT"
                            else -> "CONNECT"
                        },
                        color = Color(0xFFFF3030),
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(profile, color = if (profile.startsWith("Profile imported")) Color(0xFF65E6A5) else Color(0xFFFF7070), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onImportFile, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252532))) { Text("IMPORT FILE", fontSize = 9.sp) }
                Button(onClick = onPaste, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252532))) { Text("PASTE", fontSize = 9.sp) }
                Button(onClick = onScanQr, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))) { Text("SCAN QR", fontSize = 9.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Text("Sing-box JSON only • Server reachability is not guaranteed", color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))

            Text(
                status,
                color = if (connected || status == "ERROR") Color(0xFFFF3030) else Color.White,
                fontSize = 15.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111118), RoundedCornerShape(22.dp))
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("HOME", color = Color(0xFFFF3030), fontSize = 12.sp)
                Text("SERVERS", color = Color.White, fontSize = 12.sp)
                Text("STATS", color = Color.White, fontSize = 12.sp)
                Text("SETTINGS", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
