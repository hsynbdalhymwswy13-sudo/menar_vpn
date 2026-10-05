package io.nekohasekai.sfa.compose.menar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status

@Composable
fun MenarConnectionCard(
    status: Status,
    profileName: String?,
    uplink: String,
    downlink: String,
    uplinkTotal: String,
    downlinkTotal: String,
    connectionsCount: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = status == Status.Started

    val background = Brush.linearGradient(
        colors = listOf(
            Color(0xFF07121F),
            Color(0xFF0B1D2D),
            Color(0xFF07111D),
        ),
    )

    val accent = if (connected) Color(0xFFFFC857) else Color(0xFF35D6C4)
    val statusText = if (connected) "اتصال امن عقاب برقرار است" else "آماده اتصال"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF102A3D)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_menar_eagle),
                        contentDescription = "EAGLE",
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(modifier = Modifier.size(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "EAGLE",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "VPN",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = if (connected) "متصل" else "آفلاین",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB9C7D6),
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = profileName ?: "کانفیگی انتخاب نشده",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF91A4B7),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MenarTraffic(
                    title = "دانلود",
                    value = downlink,
                    icon = Icons.Default.ArrowDownward,
                    modifier = Modifier.weight(1f),
                )

                MenarTraffic(
                    title = "آپلود",
                    value = uplink,
                    icon = Icons.Default.ArrowUpward,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF031019),
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x22000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (connected) {
                            Icons.Default.Stop
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.size(10.dp))

                Text(
                    text = if (connected) "قطع اتصال" else "اتصال",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MenarInfo(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0A1B29))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8EA3B7),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun MenarTraffic(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0D2435))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF12374D)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFFC857),
                modifier = Modifier.size(19.dp),
            )
        }

        Spacer(modifier = Modifier.size(10.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8EA3B7),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
