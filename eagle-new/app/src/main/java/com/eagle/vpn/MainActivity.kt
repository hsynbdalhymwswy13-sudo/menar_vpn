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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val connectionState = mutableStateOf("READY")
    private val profileState = mutableStateOf("No profile imported")
    private val profilesState = mutableStateOf<List<String>>(emptyList())
    private val selectedProfileState = mutableStateOf("")
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
            if (uri != null) try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { importConfig(it.readText()) } } catch (e: Exception) { android.widget.Toast.makeText(this, "خواندن فایل انتخاب‌شده ناموفق بود", android.widget.Toast.LENGTH_LONG).show() }
        }
        loadProfiles()
        vpnPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startVpnService()
                } else {
                    connectionState.value = "مجوز اتصال رد شد"
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
            onScanQr = { qrScanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("کد QR کانفیگ سینگ‌باکس را اسکن کنید").setBeepEnabled(false).setOrientationLocked(false)) },
            profiles = profilesState.value,
            selectedProfile = selectedProfileState.value,
            onSelectProfile = { selectProfile(it) },
            onDeleteProfile = { deleteProfile(it) }
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
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else startVpnService()
    }

    private fun pasteConfig() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            check(text.isNotBlank()) { "حافظهٔ موقت خالی است" }
            importConfig(text)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "خواندن کانفیگ از حافظه ناموفق بود: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun importConfig(raw: String) {
        try {
            val input = ConfigImportParser.convert(raw)
            Libbox.checkConfig(input)

            val formatted = try {
                Libbox.formatConfig(input).value.takeIf { it.isNotBlank() } ?: input
            } catch (_: Exception) {
                input
            }
            Libbox.checkConfig(formatted)

            val target = File(filesDir, "config.json")
            val temp = File(filesDir, "config.json.tmp")
            temp.writeText(formatted)
            check(temp.renameTo(target)) { "ذخیره کانفیگ ناموفق بود" }

            File(filesDir, "profile-imported.flag").writeText("ok")
            val dir = File(filesDir, "profiles")
            if (!dir.exists()) dir.mkdirs()
            val nextId = (getProfileFiles().mapNotNull { Regex("profile_(\\d+)\\.json").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
            val saved = File(dir, "profile_${nextId}.json")
            saved.writeText(formatted)
            File(filesDir, "selected-profile.txt").writeText(saved.name)
            loadProfiles()
            profileState.value = "کانفیگ وارد و بررسی شد"

            android.widget.Toast.makeText(
                this,
                "کانفیگ با موفقیت وارد و بررسی شد",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            profileState.value = "خطا در واردکردن: ${e.message ?: "کانفیگ نامعتبر است"}"
            android.widget.Toast.makeText(
                this,
                profileState.value,
                android.widget.Toast.LENGTH_LONG
            ).show()
            android.util.Log.e("MainActivity", "Config import failed", e)
        }
    }

    private fun getProfileFiles(): List<File> {
        val dir = File(filesDir, "profiles")
        if (!dir.exists()) dir.mkdirs()
        return dir.listFiles()?.filter { it.isFile && Regex("profile_\\d+\\.json").matches(it.name) }?.sortedBy { Regex("profile_(\\d+)\\.json").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0 } ?: emptyList()
    }

    private fun loadProfiles() {
        val dir = File(filesDir, "profiles")
        if (!dir.exists()) dir.mkdirs()
        var files = getProfileFiles()
        val active = File(filesDir, "config.json")
        if (files.isEmpty() && active.isFile) {
            File(dir, "profile_1.json").writeText(active.readText())
            files = getProfileFiles()
        }
        val marker = File(filesDir, "selected-profile.txt")
        var selected = if (marker.isFile) marker.readText().trim() else ""
        if (files.none { it.name == selected }) selected = files.lastOrNull()?.name.orEmpty()
        if (selected.isNotBlank()) {
            files.first { it.name == selected }.copyTo(active, overwrite = true)
            marker.writeText(selected)
            File(filesDir, "profile-imported.flag").writeText("ok")
            profileState.value = "کانفیگ فعال: $selected"
        } else {
            profileState.value = "هنوز کانفیگی وارد نشده"
        }
        profilesState.value = files.map { it.name }
        selectedProfileState.value = selected
    }

    private fun selectProfile(name: String) {
        try {
            val file = getProfileFiles().firstOrNull { it.name == name } ?: error("کانفیگ پیدا نشد")
            Libbox.checkConfig(file.readText())
            file.copyTo(File(filesDir, "config.json"), overwrite = true)
            File(filesDir, "selected-profile.txt").writeText(name)
            File(filesDir, "profile-imported.flag").writeText("ok")
            loadProfiles()
            android.widget.Toast.makeText(this, "کانفیگ انتخاب شد؛ برای اعمال دوباره متصل شوید", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "انتخاب ناموفق: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteProfile(name: String) {
        try {
            val file = getProfileFiles().firstOrNull { it.name == name } ?: error("کانفیگ پیدا نشد")
            check(file.delete()) { "حذف فایل ناموفق بود" }
            val remaining = getProfileFiles()
            if (remaining.isEmpty()) {
                File(filesDir, "config.json").delete()
                File(filesDir, "selected-profile.txt").delete()
                File(filesDir, "profile-imported.flag").delete()
                profilesState.value = emptyList()
                selectedProfileState.value = ""
                profileState.value = "هنوز کانفیگی وارد نشده"
            } else {
                val next = if (name == selectedProfileState.value) remaining.first().name else selectedProfileState.value
                selectProfile(next)
            }
            android.widget.Toast.makeText(this, "کانفیگ حذف شد", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "حذف ناموفق: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
    onScanQr: () -> Unit,
    profiles: List<String>,
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    var selectedPage by remember { mutableStateOf("خانه") }
    val connected = status == "CONNECTED" || status == "ENGINE_STARTED"
    val busy = status == "CONNECTING" || status == "DISCONNECTING"

    val background = Brush.verticalGradient(
        listOf(Color(0xFF020205), Color(0xFF090A10), Color(0xFF16070A))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Image(
            painter = painterResource(id = R.drawable.eagle_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.24f)
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xD9020205), Color(0xD9090A10), Color(0xE616070A))
                )
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
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

            if (selectedPage == "خانه") {
                Spacer(Modifier.height(70.dp))

            Text(
                "EAGLE",
                color = Color(0xFFFF2020),
                fontSize = 34.sp,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                "اتصال امن",
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
                            if (connected)
                                listOf(Color(0xFF39F59A), Color(0xFF087F4F), Color(0xFF031B12))
                            else
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
                        containerColor = if (connected) Color(0xFF087F4F) else if (busy) Color(0xFF665018) else Color(0xFF09090D),
                        disabledContainerColor = if (connected) Color(0xFF087F4F) else Color(0xFF665018)
                    )
                ) {
                    Text(
                        when {
                            busy -> "لطفاً صبر کنید"
                            connected -> "قطع اتصال"
                            else -> "اتصال"
                        },
                        color = if (connected) Color.White else if (busy) Color.White else Color(0xFFFF6060),
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(profile, color = if (profile.startsWith("Profile imported")) Color(0xFF65E6A5) else Color(0xFFFF7070), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onImportFile, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252532))) { Text("انتخاب فایل", fontSize = 9.sp) }
                Button(onClick = onPaste, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252532))) { Text("چسباندن", fontSize = 9.sp) }
                Button(onClick = onScanQr, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))) { Text("اسکن QR", fontSize = 9.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Text("پشتیبانی از JSON و لینک‌های VLESS، VMess، Trojan و Shadowsocks", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))

            Text(
                when (status) {
                    "CONNECTED", "ENGINE_STARTED" -> "● موتور VPN روشن است"
                    "CONNECTING" -> "در حال اتصال..."
                    "DISCONNECTING" -> "در حال قطع اتصال..."
                    "ERROR" -> "خطا در اتصال"
                    "مجوز اتصال رد شد" -> status
                    else -> "آماده اتصال"
                },
                color = when {
                    connected -> Color(0xFF65E6A5)
                    status == "ERROR" -> Color(0xFFFF6060)
                    busy -> Color(0xFFFFD166)
                    else -> Color.White
                },
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )

            } else if (selectedPage == "سرورها") {
                Spacer(Modifier.height(30.dp))
                Text("سرورها و کانفیگ‌ها", color = Color.White, fontSize = 22.sp)
                Spacer(Modifier.height(18.dp))
                Text(
                    if (profile.startsWith("Profile imported")) "کانفیگ وارد شده است" else "هنوز کانفیگی وارد نشده",
                    color = Color(0xFF65E6A5),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onImportFile) { Text("انتخاب فایل کانفیگ") }
                Button(onClick = onPaste) { Text("چسباندن کانفیگ") }
                Button(
                    onClick = onScanQr,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
                ) { Text("اسکن کد QR") }
            } else if (selectedPage == "آمار") {
                Spacer(Modifier.height(30.dp))
                Text("آمار اتصال", color = Color.White, fontSize = 22.sp)
                Spacer(Modifier.height(24.dp))
                Text(
                    when (status) {
                        "CONNECTED" -> "وضعیت: متصل"
                        "CONNECTING" -> "وضعیت: در حال اتصال"
                        "DISCONNECTING" -> "وضعیت: در حال قطع اتصال"
                        "ERROR" -> "وضعیت: خطا"
                        else -> "وضعیت: آماده اتصال"
                    },
                    color = if (connected) Color(0xFF65E6A5) else Color.White,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(16.dp))
                Text("آمار زندهٔ سرعت و حجم مصرف هنوز فعال نیست.", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
            } else {
                Spacer(Modifier.height(30.dp))
                Text("تنظیمات", color = Color.White, fontSize = 22.sp)
                Spacer(Modifier.height(24.dp))
                Text("نام برنامه: EAGLE VPN", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                Text("هسته: sing-box / libbox", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Kill Switch و Split Tunneling هنوز پیاده‌سازی نشده‌اند.",
                    color = Color.White.copy(alpha = .65f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827), RoundedCornerShape(22.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("خانه", "سرورها", "آمار", "تنظیمات").forEach { page ->
                    TextButton(onClick = { selectedPage = page }) {
                        Text(
                            page,
                            color = if (selectedPage == page) Color(0xFFFF3448) else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
