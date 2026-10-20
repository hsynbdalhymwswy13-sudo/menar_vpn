package com.eagle.pro

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.io.File
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.clickable

private val Red = Color(0xFF13B887)
private val Night = Color(0xFF0D0F0E)
private val Panel = Color(0xFF171B19)
private val Panel2 = Color(0xFF222923)
private val Muted = Color(0xFFB5B9B2)
private val Green = Color(0xFFD6B46A)

class MainActivity : ComponentActivity() {
    private var vpnStatusReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EagleScreen() }
    }

    override fun onStart() {
        super.onStart()
        if (vpnStatusReceiver != null) return
        vpnStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != EagleVpnService.ACTION_STATUS) return
                val status = intent.getStringExtra("status") ?: return
                val message = intent.getStringExtra("message").orEmpty()
                vpnUiStatus.value = status
                vpnUiMessage.value = message
                vpnUiDownload.value = intent.getLongExtra("downlink", 0L).coerceAtLeast(0L)
                vpnUiUpload.value = intent.getLongExtra("uplink", 0L).coerceAtLeast(0L)
                vpnUiDownloadTotal.value = intent.getLongExtra("downlinkTotal", 0L).coerceAtLeast(0L)
                vpnUiUploadTotal.value = intent.getLongExtra("uplinkTotal", 0L).coerceAtLeast(0L)
            }
        }
        val filter = IntentFilter(EagleVpnService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(vpnStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(vpnStatusReceiver, filter)
        }
    }

    override fun onStop() {
        vpnStatusReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        vpnStatusReceiver = null
        super.onStop()
    }
}

private val vpnUiStatus = mutableStateOf("READY")
private val vpnUiMessage = mutableStateOf("آماده اتصال")
private val vpnUiDownload = mutableStateOf(0L)
private val vpnUiUpload = mutableStateOf(0L)
private val vpnUiDownloadTotal = mutableStateOf(0L)
private val vpnUiUploadTotal = mutableStateOf(0L)


private val eaglePingExecutor = Executors.newFixedThreadPool(3)

private fun tcpLatency(profile: String, callback: (String) -> Unit) {
    eaglePingExecutor.execute {
        val result = try {
            val value = profile.trim()
            val endpoint = if (value.startsWith("{")) {
                val root = org.json.JSONObject(value)
                val outbounds = root.optJSONArray("outbounds")
                    ?: throw IllegalArgumentException("outbounds پیدا نشد")
                var found: org.json.JSONObject? = null
                for (i in 0 until outbounds.length()) {
                    val item = outbounds.optJSONObject(i) ?: continue
                    if (item.optString("type") in setOf(
                            "vless", "vmess", "trojan", "shadowsocks",
                            "hysteria2", "tuic", "socks", "http"
                        )
                    ) {
                        found = item
                        break
                    }
                }
                val item = found ?: throw IllegalArgumentException("سرور قابل‌شناسایی نیست")
                item.optString("server") to item.optInt("server_port", 0)
            } else {
                val uri = URI(value)
                val host = uri.host ?: throw IllegalArgumentException("آدرس سرور نامعتبر است")
                val port = if (uri.port > 0) uri.port else when (uri.scheme?.lowercase()) {
                    "https" -> 443
                    "http" -> 80
                    else -> 0
                }
                host to port
            }

            val (host, port) = endpoint
            require(host.isNotBlank() && port in 1..65535) {
                "میزبان یا پورت معتبر نیست"
            }

            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3500)
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            "${ms} ms · TCP"
        } catch (e: Exception) {
            "ناموفق"
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            callback(result)
        }
    }
}

private fun formatTraffic(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024 && index < units.lastIndex)
    return String.format(java.util.Locale.US, "%.1f %s", value, units[index.coerceAtMost(units.lastIndex)])
}

@Composable
private fun EagleScreen() {
    val serviceStatus = vpnUiStatus.value
    // eagle-connected-duration: live elapsed time while engine is running
    val serviceMessage = vpnUiMessage.value
    val downloadBytes = vpnUiDownload.value
    val uploadBytes = vpnUiUpload.value
    val downloadTotal = vpnUiDownloadTotal.value
    val uploadTotal = vpnUiUploadTotal.value
    var page by remember { mutableStateOf("خانه") }
    var filterRunning by remember { mutableStateOf(false) }
    var filterSummary by remember { mutableStateOf("آماده بررسی کانفیگ‌ها") }
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    // eagle-connected-duration
    var durationNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val runtimePrefs = remember {
        context.getSharedPreferences("eagle_runtime", Context.MODE_PRIVATE)
    }
    var connectedSince by remember { mutableLongStateOf(
        runtimePrefs.getLong("connectedSince", 0L)
    ) }

    LaunchedEffect(serviceStatus) {
        val active = serviceStatus == "ENGINE_STARTED" ||
            serviceStatus == "STATUS_MONITOR_CONNECTED"
        connectedSince = if (active) {
            runtimePrefs.getLong("connectedSince", 0L)
        } else {
            0L
        }
        while (active) {
            durationNow = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val elapsedSeconds = if (connectedSince > 0L &&
        (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED")) {
        ((durationNow - connectedSince).coerceAtLeast(0L) / 1000L)
    } else 0L
    val durationText = "%02d:%02d:%02d".format(
        elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60
    )
    val preferences = remember { context.getSharedPreferences("eagle_profiles", Context.MODE_PRIVATE) }
    var autoConnect by remember { mutableStateOf(preferences.getBoolean("autoConnect", false)) }
    var autoReconnect by remember { mutableStateOf(preferences.getBoolean("autoReconnect", true)) }
    var killSwitch by remember { mutableStateOf(preferences.getBoolean("killSwitch", false)) }
    var splitTunnel by remember { mutableStateOf(preferences.getBoolean("splitTunnel", false)) }
    val profiles = remember {
        mutableStateListOf<String>().apply {
            addAll(preferences.getStringSet("profiles", emptySet()).orEmpty())
        }
    }
    val clipboard = LocalClipboardManager.current
    var selectedProfile by remember { mutableStateOf(preferences.getString("selectedProfile", profiles.firstOrNull().orEmpty()).orEmpty().ifBlank { profiles.firstOrNull().orEmpty() }) }
    val latencyResults = remember { mutableStateMapOf<String, String>() }

    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun message(text: String) {
        scope.launch { snack.showSnackbar(text) }
    }

    fun saveSingBoxConfig(): Boolean {
        val input = draft.trim().ifBlank { selectedProfile.trim() }
        if (input.isBlank()) {
            message("ابتدا از بخش سرورها یک پروفایل انتخاب یا وارد کنید.")
            return false
        }
        return try {
            val config = EagleConfigConverter.convert(input)
            JSONObject(config)
            File(context.filesDir, "config.json").writeText(config)
            File(context.filesDir, "profile-imported.flag").writeText("1")
            selectedProfile = input
            preferences.edit().putString("selectedProfile", selectedProfile).apply()
            message("کانفیگ تبدیل و ذخیره شد؛ در حال بررسی و راه‌اندازی موتور VPN.")
            true
        } catch (e: Exception) {
            message("کانفیگ قابل استفاده نیست: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    fun startVpnService() {
        val intent = Intent(context, EagleVpnService::class.java).apply {
            action = EagleVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
            message("درخواست شروع سرویس VPN ارسال شد.")
        } else {
            message("مجوز VPN داده نشد.")
        }
    }

    // eagle-file-import-launcher: import config/profile files from device storage
    // eagle-auto-connect-on-open: start only with a saved config and user preference
    var autoConnectHandled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoConnectHandled) {
            autoConnectHandled = true
            if (autoConnect) {
                val configFile = File(context.filesDir, "config.json")
                val importedFlag = File(context.filesDir, "profile-imported.flag")
                if (configFile.isFile && configFile.length() > 0L && importedFlag.exists()) {
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) {
                        startVpnService()
                        message("اتصال خودکار: درخواست شروع موتور VPN ارسال شد.")
                    } else {
                        vpnPermissionLauncher.launch(permissionIntent)
                    }
                } else {
                    message("برای اتصال خودکار ابتدا یک کانفیگ معتبر ذخیره کنید.")
                }
            }
        }
    }

    val fileImporter = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val imported = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?.trim()
                    .orEmpty()
                if (imported.isBlank()) {
                    message("فایل خالی است.")
                } else {
                    draft = imported
                    if (!profiles.contains(imported)) {
                        profiles.add(imported)
                        preferences.edit().putStringSet("profiles", profiles.toSet()).apply()
                    }
                    selectedProfile = imported
                    preferences.edit().putString("selectedProfile", selectedProfile).apply()
                    message("فایل وارد شد؛ برای اتصال، کانفیگ را ذخیره و اجرا کنید.")
                }
            } catch (e: Exception) {
                message("خواندن فایل ناموفق بود: ${e.message ?: "خطای فایل"}")
            }
        }
    }

    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val value = result.contents?.trim().orEmpty()
        val supported = value.startsWith("{") ||
            value.startsWith("vless://") ||
            value.startsWith("vmess://") ||
            value.startsWith("hysteria2://") ||
            value.startsWith("trojan://") ||
            value.startsWith("ss://")
        if (supported) {
            if (!profiles.contains(value)) {
                profiles.add(value)
                preferences.edit().putStringSet("profiles", profiles.toSet()).apply()
            }
            draft = value
            selectedProfile = value
            preferences.edit().putString("selectedProfile", selectedProfile).apply()
            message("QR خوانده شد؛ پروفایل ذخیره شد.")
        } else if (value.isNotBlank()) {
            message("QR خوانده شد، اما لینک پشتیبانی‌شده‌ای نبود.")
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red, secondary = Green, background = Night,
            surface = Panel, onPrimary = Color.White,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Night,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0C1220)) {
                    listOf(
                        Triple("خانه", Icons.Filled.Home, "خانه"),
                        Triple("سرورها", Icons.Filled.Dns, "سرورها"),
                        Triple("آمار", Icons.Filled.BarChart, "آمار"),
                        Triple("تنظیمات", Icons.Filled.Settings, "تنظیمات")
                    ).forEach { (label, icon, destination) ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { page = destination },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Red,
                                selectedTextColor = Red,
                                indicatorColor = Red.copy(alpha = 0.13f),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF111B2C), Night, Color(0xFF05070C))
                        )
                    )
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp)
                            .background(Red.copy(alpha = 0.12f), RoundedCornerShape(17.dp))
                            .border(1.dp, Red.copy(alpha = 0.55f), RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🦅", fontSize = 30.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("EAGLE PRO", fontSize = 23.sp, fontWeight = FontWeight.Black)
                        Text("اتصال امن، کنترل کامل", fontSize = 12.sp, color = Muted)
                    }
                    Icon(Icons.Filled.Shield, contentDescription = "امنیت", tint = Red, modifier = Modifier.size(28.dp))
                }

                Spacer(Modifier.height(22.dp))

                when (page) {
                    "خانه" -> {
                        PanelCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(10.dp).background(Red, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("وضعیت اتصال", color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text(when (serviceStatus) { "ENGINE_STARTED" -> "موتور فعال"; "STATUS_MONITOR_CONNECTED" -> "سرویس فعال"; "ERROR" -> "خطا"; "STATUS_UNAVAILABLE" -> "وضعیت نامشخص"; else -> "آماده اتصال" }, color = if (serviceStatus == "ERROR") Red else if (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED") Green else Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(22.dp))
                            Box(
                                Modifier.size(156.dp)
                                    .border(2.dp, Red.copy(alpha = 0.75f), CircleShape)
                                    .padding(9.dp)
                                    .border(1.dp, Red.copy(alpha = 0.25f), CircleShape)
                                    .background(Color(0xFF171522), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PowerSettingsNew,
                                    contentDescription = "اتصال",
                                    tint = Red,
                                    modifier = Modifier.size(72.dp)
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("اتصال امن خود را مدیریت کنید", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (serviceMessage.isNotBlank()) serviceMessage else "برای شروع، یک پروفایل معتبر انتخاب کنید.",
                                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    if (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED") {
                                        val stopIntent = Intent(context, EagleVpnService::class.java).apply {
                                            action = EagleVpnService.ACTION_STOP
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(stopIntent)
                                        } else {
                                            context.startService(stopIntent)
                                        }
                                        message("درخواست قطع اتصال ارسال شد.")
                                    } else if (saveSingBoxConfig()) {
                                        val permissionIntent = VpnService.prepare(context)
                                        if (permissionIntent == null) {
                                            startVpnService()
                                        } else {
                                            vpnPermissionLauncher.launch(permissionIntent)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(58.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED") Color(0xFF263244) else Red
                                )
                            ) {
                                Icon(
                                    if (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED") Icons.Filled.Close else Icons.Filled.PowerSettingsNew,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    if (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED") "قطع اتصال" else "اتصال به سرور",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(9.dp))
                            OutlinedButton(
                                onClick = { page = "سرورها" },
                                modifier = Modifier.fillMaxWidth().height(49.dp),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                Icon(Icons.Filled.Dns, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("انتخاب و مدیریت سرور")
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("دانلود", formatTraffic(downloadTotal.takeIf { it > 0 } ?: downloadBytes), Icons.Filled.Download, Modifier.weight(1f))
                            MetricCard("آپلود", formatTraffic(uploadTotal.takeIf { it > 0 } ?: uploadBytes), Icons.Filled.Upload, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Security, contentDescription = null, tint = Red)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("وضعیت موتور VPN", fontWeight = FontWeight.Bold)
                                    Text(when (serviceStatus) { "ERROR" -> "خطا: ${serviceMessage.ifBlank { "علت نامشخص" }}"; "ENGINE_STARTED", "STATUS_MONITOR_CONNECTED" -> serviceMessage.ifBlank { "موتور اجرا شده؛ دسترسی اینترنت هنوز تأیید نشده است." }; "STATUS_UNAVAILABLE" -> serviceMessage.ifBlank { "وضعیت موتور تأیید نشده است." }; else -> "سرویس VPN هنوز راه‌اندازی نشده است." }, color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    "سرورها" -> {
                        SectionTitle("مدیریت سرورها", "لینک پروفایل را اضافه یا از کلیپ‌بورد وارد کنید")
                        PanelCard {
                            Text("افزودن پروفایل", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("لینک VLESS / VMess / Hysteria2") },
                                minLines = 2,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val copied = clipboard.getText()?.text.orEmpty()
                                        if (copied.isNotBlank()) draft = copied
                                        else message("کلیپ‌بورد خالی است.")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                                    Spacer(Modifier.width(5.dp))
                                    Text("از کلیپ‌بورد")
                                }
                                Button(
                                    onClick = {
                                        val value = draft.trim()
                                        if (value.trimStart().startsWith("{") || value.startsWith("vless://") ||
                                            value.startsWith("vmess://") ||
                                            value.startsWith("hysteria2://") ||
                                            value.startsWith("trojan://") ||
                                            value.startsWith("ss://")
                                        ) {
                                            if (!profiles.contains(value)) {
                                                profiles.add(value)
                                                preferences.edit().putStringSet("profiles", profiles.toSet()).apply()
                                            }
                                            selectedProfile = value
                                            preferences.edit().putString("selectedProfile", selectedProfile).apply()
                                            draft = ""
                                            message("پروفایل به فهرست محلی اضافه شد.")
                                        } else {
                                            message("لطفاً یک لینک پشتیبانی‌شده وارد کنید.")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(5.dp))
                                    Text("افزودن")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    fileImporter.launch(arrayOf(
                                        "application/json",
                                        "text/plain",
                                        "application/octet-stream",
                                        "*/*"
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("واردکردن فایل کانفیگ")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    qrScanner.launch(
                                        ScanOptions()
                                            .setPrompt("QR لینک سرور را روبه‌روی دوربین بگیرید")
                                            .setBeepEnabled(false)
                                            .setOrientationLocked(false)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("اسکن QR کد")
                            }
                        }
                        Spacer(Modifier.height(14.dp))

PanelCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.FilterAlt, contentDescription = null, tint = Green)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("فیلتر هوشمند", fontWeight = FontWeight.Bold)
            Text("حذف تکراری‌ها و تست پاسخ TCP", color = Muted, fontSize = 11.sp)
        }
        Button(
            onClick = {
                if (profiles.isEmpty()) {
                    message("ابتدا کانفیگ اضافه کنید.")
                } else if (!filterRunning) {
                    val unique = profiles.distinct()
                    val supported = unique.filter { raw ->
                        val v = raw.trimStart()
                        v.startsWith("{") ||
                        listOf("vless://", "vmess://", "trojan://", "ss://",
                            "hysteria2://", "tuic://").any { v.startsWith(it) }
                    }
                    profiles.clear()
                    profiles.addAll(supported)
                    preferences.edit().putStringSet("profiles", profiles.toSet()).apply()

                    if (selectedProfile !in profiles) {
                        selectedProfile = profiles.firstOrNull().orEmpty()
                        preferences.edit().putString("selectedProfile", selectedProfile).apply()
                    }

                    if (profiles.isEmpty()) {
                        filterSummary = "کانفیگ پشتیبانی‌شده‌ای پیدا نشد."
                        message(filterSummary)
                    } else {
                        filterRunning = true
                        filterSummary = "در حال تست ${profiles.size} سرور…"
                        val candidates = profiles.toList()
                        val completed = java.util.concurrent.atomic.AtomicInteger(0)
                        val results = java.util.Collections.synchronizedList(
                            mutableListOf<Pair<String, Long>>()
                        )

                        candidates.forEach { candidate ->
                            latencyResults[candidate] = "در حال تست…"
                            tcpLatency(candidate) { result ->
                                latencyResults[candidate] = result
                                Regex("""(\d+)\s*ms""").find(result)
                                    ?.groupValues?.get(1)?.toLongOrNull()
                                    ?.let { results.add(candidate to it) }

                                if (completed.incrementAndGet() == candidates.size) {
                                    val ranked = synchronized(results) {
                                        results.sortedBy { it.second }
                                    }
                                    filterRunning = false
                                    if (ranked.isNotEmpty()) {
                                        selectedProfile = ranked.first().first
                                        preferences.edit()
                                            .putString("selectedProfile", selectedProfile).apply()
                                        filterSummary =
                                            "${ranked.size} سرور پاسخ TCP داد؛ کمترین تأخیر ${ranked.first().second} ms"
                                        message("سرور با کمترین تأخیر TCP انتخاب شد. برای اتصال، دکمه اتصال را بزنید.")
                                    } else {
                                        filterSummary = "هیچ سروری پاسخ TCP نداد."
                                        message(filterSummary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            enabled = !filterRunning,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) {
            Icon(Icons.Filled.FilterAlt, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (filterRunning) "در حال تست" else "شروع")
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(filterSummary, color = Green, fontSize = 12.sp)
    Text(
        "پاسخ TCP به‌تنهایی سلامت اینترنت از داخل تونل VPN را ثابت نمی‌کند.",
        color = Muted,
        fontSize = 11.sp
    )
}
Spacer(Modifier.height(12.dp))

SectionTitle("پروفایل‌های ذخیره‌شده", "${profiles.size} پروفایل")
                        if (profiles.isEmpty()) {
                            PanelCard {
                                Icon(Icons.Filled.Dns, contentDescription = null, tint = Muted, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("هنوز سروری اضافه نشده است.", fontWeight = FontWeight.Bold)
                                Text("لینک سرور را در کادر بالا وارد کنید.", color = Muted, fontSize = 12.sp)
                            }
                        } else {
                            profiles.forEachIndexed { index, item ->
                                PanelCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedProfile = item
                                                preferences.edit().putString("selectedProfile", selectedProfile).apply()
                                                draft = ""
                                                message("پروفایل ${index + 1} انتخاب شد؛ برای اتصال، دکمه اتصال را بزنید.")
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Dns, contentDescription = null, tint = Red, modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("پروفایل ${index + 1}", fontWeight = FontWeight.Bold)
                                            Text(item.take(48), color = Muted, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Column {
                                            Text(
                                                latencyResults[item] ?: "تأخیر: تست نشده",
                                                color = Muted,
                                                fontSize = 11.sp
                                            )
                                            TextButton(onClick = {
                                                latencyResults[item] = "در حال تست…"
                                                tcpLatency(item) { result ->
                                                    latencyResults[item] = result
                                                }
                                            }) {
                                                Icon(Icons.Filled.NetworkCheck, contentDescription = null)
                                                Text("تست تأخیر")
                                            }
                                        }
                                        IconButton(onClick = {
                                            profiles.remove(item)
                                            if (selectedProfile == item) {
                                                selectedProfile = profiles.firstOrNull().orEmpty()
                                                preferences.edit().putString("selectedProfile", selectedProfile).apply()
                                            }
                                            preferences.edit().putStringSet("profiles", profiles.toSet()).apply()
                                            message("پروفایل حذف شد.")
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Red)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(profiles.joinToString("\n")))
                                    message("لینک‌های پروفایل در کلیپ‌بورد کپی شدند.")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("خروجی گرفتن از لینک‌ها")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("JSON، VLESS، VMess، Trojan، Shadowsocks و Hysteria2: پروفایل را انتخاب کنید و اتصال را بزنید. پشتیبانی نهایی به سازگاری کانفیگ با موتور sing-box بستگی دارد.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }

                    "آمار" -> {
                        SectionTitle("آمار ترافیک", if (serviceStatus == "ENGINE_STARTED" || serviceStatus == "STATUS_MONITOR_CONNECTED") "داده‌های گزارش‌شده توسط موتور sing-box" else "هنوز آمار زنده‌ای دریافت نشده است")
                        PanelCard {
                            Icon(Icons.Filled.BarChart, contentDescription = null, tint = Red, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(if (serviceStatus == "ERROR") "خطا در راه‌اندازی موتور" else if (downloadBytes > 0 || uploadBytes > 0 || downloadTotal > 0 || uploadTotal > 0) "آمار موتور دریافت شد" else "هنوز داده‌ای ثبت نشده", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(serviceMessage.ifBlank { "آمار فقط در صورت گزارش موتور نمایش داده می‌شود؛ اتصال اینترنت از این پیام به‌تنهایی تأیید نمی‌شود." }, color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("دریافت", formatTraffic(downloadTotal.takeIf { it > 0 } ?: downloadBytes), Icons.Filled.Download, Modifier.weight(1f))
                            MetricCard("ارسال", formatTraffic(uploadTotal.takeIf { it > 0 } ?: uploadBytes), Icons.Filled.Upload, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("مدت اتصال", durationText, Icons.Filled.Speed, Modifier.weight(1f))
                            MetricCard("تأخیر", "— ms", Icons.Filled.NetworkCheck, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelCard {
                            Text("نکته", fontWeight = FontWeight.Bold)
                            Text("پینگ و آمار واقعی باید از موتور فعال sing-box دریافت شوند؛ اعداد ساختگی نمایش داده نمی‌شوند.", color = Muted, fontSize = 12.sp)
                        }
                    }

                    "تنظیمات" -> {
                        SectionTitle("تنظیمات برنامه", "کنترل‌ها و گزینه‌های EAGLE PRO")
                        PanelCard {
                            SettingRow("اتصال خودکار", "اتصال هنگام باز شدن برنامه", autoConnect, Icons.Filled.PowerSettingsNew) { autoConnect = it; preferences.edit().putBoolean("autoConnect", it).apply() }
                            Divider(color = Color(0xFF354239))
                            SettingRow("اتصال مجدد خودکار", "پس از قطع اتصال", autoReconnect, Icons.Filled.Refresh) { autoReconnect = it; preferences.edit().putBoolean("autoReconnect", it).apply() }
                            Divider(color = Color(0xFF354239))
                            SettingRow("Kill Switch", "جلوگیری از ترافیک خارج از VPN", killSwitch, Icons.Filled.Lock) { killSwitch = it; preferences.edit().putBoolean("killSwitch", it).apply() }
                            Divider(color = Color(0xFF354239))
                            SettingRow("Split Tunneling", "انتخاب برنامه‌های عبوری", splitTunnel, Icons.Filled.Tune) { splitTunnel = it; preferences.edit().putBoolean("splitTunnel", it).apply() }
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelCard {
                            Column {
                                Text("گزارش عیب‌یابی", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "گزارش داخلی موتور را برای بررسی مشکل کپی کنید.",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val logFile = File(context.filesDir, "eagle-engine.log")
                                        val report = try {
                                            if (logFile.isFile) logFile.readText().takeLast(24000)
                                            else "هنوز گزارش داخلی ثبت نشده است."
                                        } catch (e: Exception) {
                                            "خطا در خواندن گزارش: ${e.message}"
                                        }
                                        clipboard.setText(AnnotatedString(report))
                                        message("گزارش عیب‌یابی در کلیپ‌بورد کپی شد.")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("کپی گزارش عیب‌یابی")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        PanelCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = Red)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("وضعیت قابلیت‌ها", fontWeight = FontWeight.Bold)
                                    Text("این کلیدها فعلاً تنظیمات رابط کاربری هستند و تا اتصال سرویس VPN اعمال نمی‌شوند.", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelCard {
                            Text("درباره برنامه", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("EAGLE PRO · نسخه 1.1", color = Muted, fontSize = 12.sp)
                            Text("رابط فارسی · تم تیره · طراحی سرمه‌ای و قرمز", color = Muted, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("🦅  EAGLE PRO  •  نسخه مستقل", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF354239), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFF354239), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
            Icon(icon, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Red, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
