package com.eagle.vpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
                onDisconnect = { stopVpnService() }
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

    private fun requestVpnPermission() {
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
    onDisconnect: () -> Unit
) {
    val connected = status == "CONNECTED"
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

            Spacer(Modifier.height(30.dp))

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
