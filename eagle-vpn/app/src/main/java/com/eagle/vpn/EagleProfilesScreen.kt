package com.eagle.vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProfileBlack = Color(0xFF05070B)
private val ProfilePanel = Color(0xFF0B1018)
private val ProfileRed = Color(0xFFE11D2E)
private val ProfileGreen = Color(0xFF35D6A0)
private val ProfileText = Color(0xFFF3F5F7)
private val ProfileMuted = Color(0xFF8C96A5)

@Composable
fun EagleProfilesScreen(
    profiles: List<EagleProfile>,
    activeId: String?,
    onSelect: (EagleProfile) -> Unit,
    onFavorite: (EagleProfile) -> Unit,
    onDelete: (EagleProfile) -> Unit,
    onPing: (EagleProfile) -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBlack)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "SERVERS",
                    color = ProfileText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "PROFILE MANAGER",
                    color = ProfileMuted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProfileRed
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("+ ADD", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (profiles.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ProfilePanel
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "NO SERVERS",
                        color = ProfileText,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add a configuration from Clipboard or QR Code.",
                        color = ProfileMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileItem(
                        profile = profile,
                        active = profile.id == activeId,
                        onSelect = { onSelect(profile) },
                        onFavorite = { onFavorite(profile) },
                        onDelete = { onDelete(profile) },
                        onPing = { onPing(profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(
    profile: EagleProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onPing: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        color = ProfilePanel
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        color = ProfileText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        profile.address.ifBlank { "Configuration" },
                        color = ProfileMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    if (profile.isFavorite) "★" else "☆",
                    color = if (profile.isFavorite) ProfileRed else ProfileMuted,
                    fontSize = 25.sp,
                    modifier = Modifier.clickable(onClick = onFavorite)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPing) {
                    Text(
                        if (profile.ping > 0) "${profile.ping} ms" else "PING",
                        color = if (profile.ping > 0) ProfileGreen else ProfileText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onDelete) {
                    Text("DELETE", color = ProfileRed, fontSize = 11.sp)
                }

                if (active) {
                    Text(
                        "ACTIVE",
                        color = ProfileGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
