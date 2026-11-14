package com.exor.mediaserver.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exor.mediaserver.core.Prefs

@Composable
fun WorkspaceScreen(
    prefs: Prefs,
    selectedName: String?,
    isHosting: Boolean,
    hostUrl: String?,
    onPickFile: () -> Unit,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ExorColors.Bg2, ExorColors.Bg)))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Back", tint = Color.White)
            }
            Text("Workspace", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Text(
            "Pick a .html file to host on your IP (default port ${prefs.webPort}). " +
                "Visitors must enter the password (default: Frozen). " +
                "Pick a .py that uses Flask and it will be executed via Termux instead.",
            color = ExorColors.TextDim,
            fontSize = 14.sp,
        )

        GlassCard(
            Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))),
            Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (selectedName == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x22FFFFFF))
                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(18.dp))
                            .clickable { onPickFile() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, null, tint = ExorColors.Cyan, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Select .html or .py file", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text("Selected:", color = ExorColors.TextDim, fontSize = 13.sp)
                    Text(selectedName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isHosting) {
                            Button(
                                onClick = onStopHost,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2D3B)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Stop")
                            }
                        } else {
                            Button(
                                onClick = onStartHost,
                                colors = ButtonDefaults.buttonColors(containerColor = ExorColors.Purple),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Start")
                            }
                        }
                        OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                            Text("Change file")
                        }
                    }
                }

                if (isHosting && hostUrl != null) {
                    Text("Hosting at:", color = ExorColors.TextDim, fontSize = 13.sp)
                    Text(hostUrl, fontWeight = FontWeight.Bold, color = ExorColors.Cyan, fontSize = 16.sp)
                    Text("Password: ${prefs.webPassword}", color = ExorColors.TextDim, fontSize = 13.sp)
                }
            }
        }

        Text(
            "Tip: Change password in Settings. HTML is served with a simple login page. " +
                "Python Flask scripts are launched through Termux (must be installed & allowed).",
            color = ExorColors.TextDim,
            fontSize = 12.sp,
        )
    }
}
