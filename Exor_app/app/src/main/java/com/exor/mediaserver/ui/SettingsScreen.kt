package com.exor.mediaserver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exor.mediaserver.core.Prefs

data class TermuxUi(val installed: Boolean, val apkBundled: Boolean, val runPermission: Boolean)

@Composable
fun SettingsScreen(
    prefs: Prefs,
    termux: TermuxUi,
    onBack: () -> Unit,
    onInstallTermux: () -> Unit,
    onOpenTerminal: () -> Unit,
    onCopySetup: () -> Unit,
    onGrantTermuxPerm: () -> Unit,
    onRunExample: () -> Unit,
) {
    var name by remember { mutableStateOf(prefs.serverName) }
    var port by remember { mutableStateOf(prefs.port.toString()) }
    var auto by remember { mutableStateOf(prefs.autoStartServer) }
    var sv by remember { mutableStateOf(prefs.shareVideos) }
    var sm by remember { mutableStateOf(prefs.shareMusic) }
    var sph by remember { mutableStateOf(prefs.sharePhotos) }
    var webPass by remember { mutableStateOf(prefs.webPassword) }
    var webPort by remember { mutableStateOf(prefs.webPort.toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ExorColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Section("Server")
        OutlinedTextField(
            value = name, onValueChange = { name = it; prefs.serverName = it },
            label = { Text("Name shown on the TV") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = port,
            onValueChange = { v -> port = v.filter { it.isDigit() }.take(5); port.toIntOrNull()?.let { prefs.port = it } },
            label = { Text("HTTP port (restart server to apply)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        ToggleRow("Start server when app opens", auto) { auto = it; prefs.autoStartServer = it }

        Section("Share with TV")
        ToggleRow("Videos", sv) { sv = it; prefs.shareVideos = it }
        ToggleRow("Music", sm) { sm = it; prefs.shareMusic = it }
        ToggleRow("Photos", sph) { sph = it; prefs.sharePhotos = it }

        Section("Workspace web host")
        OutlinedTextField(
            value = webPass, onValueChange = { webPass = it; prefs.webPassword = it },
            label = { Text("Login password (default Frozen)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = webPort,
            onValueChange = { v -> webPort = v.filter { it.isDigit() }.take(5); webPort.toIntOrNull()?.let { prefs.webPort = it } },
            label = { Text("Web host port (default 8080)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Section("Termux (Python scripts)")
        Text(
            when {
                termux.installed -> "Termux is installed."
                termux.apkBundled -> "Termux APK is bundled in this build. Tap install."
                else -> "Termux is not installed and no APK is bundled (see guidetobuild.txt)."
            },
            color = ExorColors.TextDim, fontSize = 13.sp,
        )
        if (!termux.installed) {
            Button(onClick = onInstallTermux, enabled = termux.apkBundled) { Text("Install bundled Termux") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.weight(1f)) { Text("Open terminal") }
                OutlinedButton(onClick = onCopySetup, modifier = Modifier.weight(1f)) { Text("Copy setup cmds") }
            }
            if (!termux.runPermission) {
                OutlinedButton(onClick = onGrantTermuxPerm, modifier = Modifier.fillMaxWidth()) { Text("Allow running Termux commands") }
            }
            Button(onClick = onRunExample, modifier = Modifier.fillMaxWidth()) { Text("Run example_server.py in Termux") }
            Text(
                "First time: open the terminal once, paste the copied setup commands, then come back.",
                color = ExorColors.TextDim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = ExorColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
