package com.eagle.vpn

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import java.util.Locale

private val EagleBlack = Color(0xFF05070B)
private val EaglePanel = Color(0xFF0B1018)
private val EagleRed = Color(0xFFE11D2E)
private val EagleRedDark = Color(0xFF7A101C)
private val EagleGold = Color(0xFFD8B56A)
private val EagleGreen = Color(0xFF35D6A0)
private val EagleText = Color(0xFFF3F5F7)
private val EagleMuted = Color(0xFF8C96A5)

class MainActivity : ComponentActivity() {
    private var pendingConfig: String? = null

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) pendingConfig?.let { c -> startVpn(c) }
    }

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { saveConfig(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EagleApp(
                    onClipboard = { importClipboard() },
                    onQr = { scanQr() },
                    onConnect = { connectOrDisconnect() }
                )
            }
        }
    }

    private fun saveConfig(config: String) {
        try {
            io.nekohasekai.libbox.Libbox.checkConfig(config)
            getSharedPreferences("eagle", MODE_PRIVATE).edit()
                .putString("config", config)
                .apply()
            EagleState.update { it.copy(configReady = true, configName = "Saved configuration", error = null) }
        } catch (e: Exception) {
            EagleState.update { it.copy(error = "Invalid configuration: ${e.message ?: "unknown"}") }
        }
    }

    private fun importClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            EagleState.update { it.copy(error = "Clipboard is empty") }
        } else saveConfig(text)
    }

    private fun scanQr() {
        qrScanner.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan EAGLE VPN configuration")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
        )
    }

    private fun connectOrDisconnect() {
        if (EagleState.state.value.connected || EagleState.state.value.connecting) {
            ContextCompat.startForegroundService(
                this, Intent(this, EagleVpnService::class.java).setAction(EagleVpnService.ACTION_STOP)
            )
            return
        }

        val config = getSharedPreferences("eagle", MODE_PRIVATE).getString("config", null)
        if (config.isNullOrBlank()) {
            EagleState.update { it.copy(error = "Add a configuration first") }
            return
        }

        pendingConfig = config
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpn(config)
    }

    private fun startVpn(config: String) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, EagleVpnService::class.java)
                .setAction(EagleVpnService.ACTION_CONNECT)
                .putExtra("config", config)
        )
    }
}

@Composable
private fun EagleApp(onClipboard: () -> Unit, onQr: () -> Unit, onConnect: () -> Unit) {
    val state by EagleState.state.collectAsState()
    var showConfig by remember { mutableStateOf(false) }

    LaunchedEffect(state.connected, state.startedAt) {
        while (state.connected) {
            delay(1000)
            EagleState.update { it }
        }
    }

    val elapsed = if (state.startedAt > 0L) {
        ((System.currentTimeMillis() - state.startedAt).coerceAtLeast(0L) / 1000L)
    } else 0L

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(EagleBlack, Color(0xFF090B12), Color(0xFF12070A)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("EAGLE", color = EagleText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PRIVATE NETWORK", color = EagleMuted, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                Text(
                    "●",
                    color = if (state.connected) EagleGreen else EagleRed,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                if (state.connected) "PROTECTED" else if (state.connecting) "CONNECTING" else "READY",
                color = if (state.connected) EagleGreen else EagleText,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(220.dp).border(
                    2.dp, if (state.connected) EagleGreen else EagleRed, CircleShape
                ).padding(10.dp).border(1.dp, EagleRedDark, CircleShape)
                    .background(EaglePanel, CircleShape).padding(18.dp)
                    .background(EagleBlack, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.size(150.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.connected) EagleGreen else EagleRed,
                        contentColor = EagleText
                    )
                ) {
                    Text(
                        if (state.connected) "ON" else if (state.connecting) "..." else "CONNECT",
                        fontSize = if (state.connected) 19.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), EaglePanel) {
                Column(Modifier.padding(18.dp)) {
                    Text("CONFIGURATION", color = EagleMuted, fontSize = 9.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.configName, color = EagleText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.configReady) "Configuration ready" else "Add a configuration",
                                color = if (state.configReady) EagleGreen else EagleMuted, fontSize = 11.sp
                            )
                        }
                        Text("›", color = EagleRed, fontSize = 30.sp,
                            modifier = Modifier.clickable { showConfig = true })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("CLIPBOARD", "PASTE", Modifier.weight(1f), onClipboard)
                ActionCard("QR CODE", "SCAN", Modifier.weight(1f), onQr)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("DOWNLOAD", formatBytes(state.download), Modifier.weight(1f))
                StatCard("UPLOAD", formatBytes(state.upload), Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("TIME", formatTime(elapsed), Modifier.weight(1f))
                StatCard("STATUS", if (state.connected) "LIVE" else "OFF", Modifier.weight(1f))
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = EagleRed, fontSize = 11.sp, modifier = Modifier.alpha(0.95f))
            }
        }
    }

    if (showConfig) {
        AlertDialog(
            onDismissRequest = { showConfig = false },
            containerColor = EaglePanel,
            title = { Text("Configuration", color = EagleText) },
            text = {
                Text(
                    "Use Clipboard or QR Code to add a sing-box configuration.",
                    color = EagleMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfig = false }) { Text("CLOSE", color = EagleRed) }
            }
        )
    }
}

@Composable
private fun ActionCard(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), RoundedCornerShape(18.dp), EaglePanel) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = EagleMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = EagleText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(18.dp), EaglePanel) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = EagleMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = EagleText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

private fun formatTime(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
