package io.nekohasekai.sfa.compose.menar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.database.Profile

@Composable
fun MenarServerList(
    profiles: List<Profile>,
    selectedProfileId: Long,
    onProfileSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1D35)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "سرورها",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            if (profiles.isEmpty()) {
                Text(
                    text = "هنوز سروری اضافه نشده؛ از بخش پروفایل‌ها کانفیگ وارد کن.",
                    color = Color(0xFFB8C7D9),
                )
            } else {
                profiles.forEach { profile ->
                    val selected = profile.id == selectedProfileId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Color(0xFF163B55) else Color(0xFF10263C),
                            )
                            .clickable { onProfileSelected(profile.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "🌐", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = profile.name,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                        )
                        if (selected) {
                            Text(
                                text = "انتخاب‌شده",
                                color = Color(0xFF35D07F),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
