package com.eagle.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Red = Color(0xFFEF3340)
private val Night = Color(0xFF070B14)
private val Panel = Color(0xFF111827)
private val Muted = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EagleScreen() }
    }
}

@Composable
private fun EagleScreen() {
    var connected by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("خانه") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Night, surface = Panel, onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111827), Night, Color.Black)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(18.dp))
                Text("🦅", fontSize = 54.sp)
                Text("EAGLE PRO", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("اتصال امن، ساده و سریع", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(28.dp))
                Card(Modifier.fillMaxWidth().border(1.dp, Color(0xFF293548), RoundedCornerShape(24.dp)), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(142.dp).border(2.dp, if (connected) Color(0xFF22C55E) else Red, RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                            Text(if (connected) "✓" else "⏻", fontSize = 58.sp, color = if (connected) Color(0xFF22C55E) else Red)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(if (connected) "متصل (نمایشی)" else "آماده اتصال", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("وضعیت موتور VPN: هنوز راه‌اندازی نشده", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { connected = !connected }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCornerShape(16.dp)) {
                            Text(if (connected) "قطع نمایشی اتصال" else "اتصال آزمایشی", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("این دکمه فعلاً نمایشی است و هنوز ترافیک را از sing-box عبور نمی‌دهد.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("پروفایل سرور", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = profile, onValueChange = { profile = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("لینک کانفیگ یا آدرس سرور") }, minLines = 2, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { page = "سرورها" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("مدیریت سرورها") }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("خانه", "سرورها", "آمار", "تنظیمات").forEach { item ->
                        TextButton(onClick = { page = item }) { Text(item, color = if (page == item) Red else Muted) }
                    }
                }
                Text("صفحه انتخاب‌شده: $page", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("EAGLE PRO • نسخه مستقل", color = Muted, fontSize = 10.sp)
            }
        }
    }
}
