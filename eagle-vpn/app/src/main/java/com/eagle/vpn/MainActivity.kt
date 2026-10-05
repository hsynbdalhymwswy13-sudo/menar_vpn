package com.eagle.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EagleBlack = Color(0xFF05070B)
private val EaglePanel = Color(0xFF0B1018)
private val EagleGold = Color(0xFFD8B56A)
private val EagleGreen = Color(0xFF35D6A0)
private val EagleText = Color(0xFFF3F5F7)
private val EagleMuted = Color(0xFF8C96A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EagleApp()
        }
    }
}

@Composable
fun EagleApp() {
    var connected by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = EagleBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EAGLE",
                        color = EagleGold,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "PRIVATE NETWORK",
                        color = EagleMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                Text(
                    text = "●",
                    color = if (connected) EagleGreen else EagleMuted,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(44.dp))

            Text(
                text = if (connected) "PROTECTED" else "READY",
                color = if (connected) EagleGreen else EagleText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            Box(
                modifier = Modifier
                    .size(210.dp)
                    .border(
                        width = 2.dp,
                        color = if (connected) EagleGreen else EagleGold,
                        shape = CircleShape
                    )
                    .padding(12.dp)
                    .border(
                        width = 1.dp,
                        color = EaglePanel,
                        shape = CircleShape
                    )
                    .background(
                        color = EaglePanel,
                        shape = CircleShape
                    )
                    .padding(20.dp)
                    .background(
                        color = EagleBlack,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { connected = !connected },
                    modifier = Modifier.size(145.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connected) EagleGreen else EagleGold,
                        contentColor = EagleBlack
                    )
                ) {
                    Text(
                        text = if (connected) "ON" else "CONNECT",
                        fontSize = if (connected) 18.sp else 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(38.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = EaglePanel
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "BEST LOCATION",
                        color = EagleMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto Select",
                                color = EagleText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Optimal server",
                                color = EagleMuted,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            text = "›",
                            color = EagleGold,
                            fontSize = 30.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "DOWNLOAD",
                    value = "—"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "UPLOAD",
                    value = "—"
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    title: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = EaglePanel
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = EagleMuted,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = EagleText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
