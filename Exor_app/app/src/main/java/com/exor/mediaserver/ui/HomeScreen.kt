package com.exor.mediaserver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exor.mediaserver.core.ServerStatus
import com.exor.mediaserver.media.LibraryCounts
import com.exor.mediaserver.scan.DeviceKind
import com.exor.mediaserver.scan.RendererDevice
import kotlin.math.abs
import kotlin.math.sin

data class Perms(val storage: Boolean, val overlay: Boolean, val notifications: Boolean)

@Composable
fun HomeScreen(
    counts: LibraryCounts,
    devices: List<RendererDevice>,
    scanning: Boolean,
    scannedOnce: Boolean,
    server: ServerStatus,
    perms: Perms,
    onToggleServer: () -> Unit,
    onGrantStorage: () -> Unit,
    onFloating: () -> Unit,
    onScan: () -> Unit,
    onTerminal: () -> Unit,
    onSettings: () -> Unit,
    onInfo: () -> Unit,
    onCard: (String) -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ExorColors.Bg2, ExorColors.Bg)))
    ) {
        // Soft "space swirl" glows behind everything.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(ExorColors.Cyan.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width, size.height * 0.25f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width, size.height * 0.25f),
            )
            drawCircle(
                Brush.radialGradient(listOf(ExorColors.Purple.copy(alpha = 0.25f), Color.Transparent), center = Offset(0f, size.height * 0.75f), radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = Offset(0f, size.height * 0.75f),
            )
        }

        // Hamburger (three lines) top-left
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(44.dp)
                .clickable { onOpenDrawer() },
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(Modifier.width(22.dp).height(2.5.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header()

            ServerCard(server, onToggleServer, onFloating, onTerminal)

            if (!perms.storage) {
                GlassCard(Brush.linearGradient(listOf(Color(0x33FFB84D), Color(0x11FFB84D))), Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allow access to your media", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Needed so the TV can browse videos, music and photos from internal storage.", color = ExorColors.TextDim, fontSize = 13.sp)
                        Button(onClick = onGrantStorage) { Text("Grant access") }
                    }
                }
            }

            // VIDEOS
            GlassCard(
                Brush.linearGradient(listOf(Color(0xFF123A66), Color(0xFF2B2A6E), Color(0xFF0B1A3A))),
                Modifier.fillMaxWidth().height(150.dp),
                onClick = { onCard("videos") },
            ) {
                Text("VIDEOS", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(18.dp))
                Pill("${counts.videos} Videos", Modifier.align(Alignment.TopEnd).padding(16.dp))
                Box(
                    Modifier.align(Alignment.BottomStart).padding(16.dp).size(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ExorColors.Cyan, ExorColors.Purple))),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", fontSize = 24.sp) }
            }

            // MUSIC
            GlassCard(
                Brush.linearGradient(listOf(Color(0xFF3B1A6B), Color(0xFF5A1E7A), Color(0xFF1B1040))),
                Modifier.fillMaxWidth().height(170.dp),
                onClick = { onCard("music") },
            ) {
                Text("MUSIC", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(18.dp))
                Pill("${counts.albums} Albums", Modifier.align(Alignment.TopEnd).padding(16.dp))
                Waveform(Modifier.align(Alignment.Center).fillMaxWidth().height(56.dp).padding(horizontal = 14.dp))
                if (counts.recentTracks.isNotEmpty()) {
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text("Recently Added:", fontSize = 13.sp, color = ExorColors.TextDim)
                        counts.recentTracks.forEach { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCard(
                    Brush.linearGradient(listOf(Color(0xFF4A5675), Color(0xFF28304D))),
                    Modifier.weight(1f).height(150.dp),
                    onClick = { onCard("photos") },
                ) {
                    Text("PHOTOS", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(16.dp))
                    Pill("${counts.photos} Photos", Modifier.align(Alignment.BottomStart).padding(14.dp))
                }
                GlassCard(
                    Brush.linearGradient(listOf(Color(0xFF14566B), Color(0xFF1A2F5E))),
                    Modifier.weight(1f).height(150.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("MORE", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Row(Modifier.clickable { onSettings() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp)); Text("Settings", fontSize = 16.sp)
                        }
                        Row(Modifier.clickable { onInfo() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(20.dp)); Text("Info", fontSize = 16.sp)
                        }
                    }
                }
            }

            DevicesPanel(devices, scanning, scannedOnce, onScan)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(ExorColors.Cyan, ExorColors.Purple))),
            contentAlignment = Alignment.Center,
        ) { Text("E", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        Spacer(Modifier.height(10.dp))
        Text("Exor Media Server", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text("Your DLNA Media Hub", fontSize = 16.sp, color = ExorColors.TextDim)
    }
}

@Composable
private fun ServerCard(server: ServerStatus, onToggle: () -> Unit, onFloating: () -> Unit, onTerminal: () -> Unit) {
    GlassCard(Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))), Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (server.running) ExorColors.Good else ExorColors.Warn))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (server.running) "Server running" else "Server stopped", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    val sub = when {
                        server.running && server.activeStreams > 0 -> "${server.url} · ${server.activeStreams} streaming"
                        server.running -> "${server.url} · idle"
                        else -> server.message ?: "Tap Start, then pick this server on your TV"
                    }
                    Text(sub, color = ExorColors.TextDim, fontSize = 13.sp)
                }
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = if (server.running) Color(0xFF8A2D3B) else ExorColors.Purple),
                ) { Text(if (server.running) "Stop" else "Start", color = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onFloating, modifier = Modifier.weight(1f)) { Text("Floating window", fontSize = 13.sp) }
                OutlinedButton(onClick = onTerminal, modifier = Modifier.weight(1f)) { Text("Terminal", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun DevicesPanel(devices: List<RendererDevice>, scanning: Boolean, scannedOnce: Boolean, onScan: () -> Unit) {
    val tvs = devices.filter { it.kind == DeviceKind.TV }
    val receivers = devices.filter { it.kind == DeviceKind.RECEIVER }
    val others = devices.filter { it.kind == DeviceKind.OTHER }

    GlassCard(Brush.linearGradient(listOf(Color(0x33101830), Color(0x55101830))), Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Devices on your network", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onScan, enabled = !scanning) {
                    Icon(Icons.Default.Refresh, "Scan", tint = if (scanning) ExorColors.TextDim else ExorColors.Cyan)
                }
            }
            when {
                scanning -> Text("Discovery in progress…", color = ExorColors.TextDim)
                !scannedOnce -> Text("Tap refresh to scan for Sony BRAVIA TVs and other DLNA players.", color = ExorColors.TextDim, fontSize = 13.sp)
                devices.isEmpty() -> Text("No players found. Make sure the TV is on and on the same Wi-Fi.", color = ExorColors.TextDim, fontSize = 13.sp)
            }
            (tvs + receivers).forEach { DeviceRow(it, if (it.kind == DeviceKind.TV) "📺" else "🔊") }
            if (others.isNotEmpty()) {
                Text("Other DLNA Devices", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                others.forEach { DeviceRow(it, "📡") }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: RendererDevice, emoji: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 22.sp) }
        Column {
            Text(d.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text("${d.manufacturer} · ${d.ip}", color = ExorColors.TextDim, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(Color(0x55FFFFFF)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 56
        val step = size.width / n
        for (i in 0 until n) {
            val h = (abs(sin(i * 0.55f) * 0.6f + sin(i * 0.17f) * 0.4f) * 0.9f + 0.08f) * size.height
            val x = step * i + step / 2
            drawLine(
                if (i % 2 == 0) ExorColors.Magenta else ExorColors.Purple,
                Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2),
                strokeWidth = step * 0.5f, cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun GlassCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, Brush.linearGradient(listOf(ExorColors.Cyan.copy(alpha = 0.7f), ExorColors.Purple.copy(alpha = 0.5f))), shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        content = content,
    )
}
