package com.exor.mediaserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.exor.mediaserver.core.NetUtil
import com.exor.mediaserver.core.Prefs
import com.exor.mediaserver.core.ServerState
import com.exor.mediaserver.overlay.FloatingWindowService
import com.exor.mediaserver.server.ExorServerService
import com.exor.mediaserver.server.WebHostService
import com.exor.mediaserver.termux.TermuxBridge
import com.exor.mediaserver.ui.ExorColors
import com.exor.mediaserver.ui.ExorTheme
import com.exor.mediaserver.ui.HomeScreen
import com.exor.mediaserver.ui.HomeViewModel
import com.exor.mediaserver.ui.Perms
import com.exor.mediaserver.ui.SettingsScreen
import com.exor.mediaserver.ui.TermuxUi
import com.exor.mediaserver.ui.WorkspaceScreen
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()
    private lateinit var prefs: Prefs

    private var perms by mutableStateOf(Perms(false, false, false))
    private var termux by mutableStateOf(TermuxUi(false, false, false))

    private var selectedFileName by mutableStateOf<String?>(null)
    private var selectedFilePath by mutableStateOf<String?>(null)
    private var isWebHosting by mutableStateOf(false)
    private var webHostUrl by mutableStateOf<String?>(null)

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshState()
        vm.refreshCounts()
    }
    private val askTermuxPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handlePickedFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        refreshState()

        // First-run: Notification + Storage + (overlay asked later)
        if (!perms.storage || !perms.notifications) requestMediaPermissions()
        else if (prefs.autoStartServer && !ServerState.status.value.running) ExorServerService.start(this)

        setContent {
            ExorTheme {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf("home") }
                var showInfo by remember { mutableStateOf(false) }
                val counts by vm.counts.collectAsState()
                val devices by vm.devices.collectAsState()
                val scanning by vm.scanning.collectAsState()
                val scanned by vm.scannedOnce.collectAsState()
                val server by ServerState.status.collectAsState()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        Column(
                            Modifier
                                .fillMaxHeight()
                                .width(280.dp)
                                .background(ExorColors.Bg2)
                                .padding(top = 48.dp, start = 20.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Exor", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            DrawerItem("Home") {
                                screen = "home"
                                scope.launch { drawerState.close() }
                            }
                            DrawerItem("Workspace") {
                                screen = "workspace"
                                scope.launch { drawerState.close() }
                            }
                            DrawerItem("Open terminal") {
                                openTerminal()
                                scope.launch { drawerState.close() }
                            }
                        }
                    },
                ) {
                    when (screen) {
                        "settings" -> {
                            BackHandler { screen = "home" }
                            SettingsScreen(
                                prefs = prefs,
                                termux = termux,
                                onBack = { screen = "home" },
                                onInstallTermux = ::installTermux,
                                onOpenTerminal = ::openTerminal,
                                onCopySetup = {
                                    TermuxBridge.copySetupCommands(this)
                                    toast("Setup commands copied. Paste them in Termux.")
                                },
                                onGrantTermuxPerm = { askTermuxPermission.launch(TermuxBridge.PERM_RUN_COMMAND) },
                                onRunExample = ::runExample,
                            )
                        }
                        "workspace" -> {
                            BackHandler { screen = "home" }
                            WorkspaceScreen(
                                prefs = prefs,
                                selectedName = selectedFileName,
                                isHosting = isWebHosting,
                                hostUrl = webHostUrl,
                                onPickFile = {
                                    pickFile.launch(arrayOf("text/html", "text/x-python", "application/octet-stream", "*/*"))
                                },
                                onStartHost = ::startSelectedHost,
                                onStopHost = ::stopWebHost,
                                onBack = { screen = "home" },
                            )
                        }
                        else -> {
                            HomeScreen(
                                counts = counts, devices = devices, scanning = scanning, scannedOnce = scanned,
                                server = server, perms = perms,
                                onToggleServer = ::toggleServer,
                                onGrantStorage = ::requestMediaPermissions,
                                onFloating = ::openFloating,
                                onScan = vm::scan,
                                onTerminal = ::openTerminal,
                                onSettings = { screen = "settings" },
                                onInfo = { showInfo = true },
                                onCard = { toast("Browsing screens come later. The TV can already browse your $it.") },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )
                        }
                    }
                }

                if (showInfo) {
                    AlertDialog(
                        onDismissRequest = { showInfo = false },
                        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
                        title = { Text("Exor Media Server") },
                        text = {
                            Text(
                                "Version ${BuildConfigInfo.VERSION}\n\n" +
                                    "Start the server, then open Media Player on your Sony BRAVIA and pick this device. " +
                                    "Phone and TV must be on the same Wi-Fi.\n\n" +
                                    "Hamburger menu (top-left) → Workspace for HTML/Python hosting."
                            )
                        },
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DrawerItem(label: String, onClick: () -> Unit) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        vm.refreshCounts()
    }

    private fun refreshState() {
        fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        val storage = if (Build.VERSION.SDK_INT >= 33)
            granted(Manifest.permission.READ_MEDIA_VIDEO) && granted(Manifest.permission.READ_MEDIA_AUDIO) && granted(Manifest.permission.READ_MEDIA_IMAGES)
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        perms = Perms(storage, Settings.canDrawOverlays(this), notif)
        termux = TermuxUi(TermuxBridge.isInstalled(this), TermuxBridge.bundledApk(this) != null, TermuxBridge.hasRunPermission(this))
    }

    private fun requestMediaPermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_VIDEO
            list += Manifest.permission.READ_MEDIA_AUDIO
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        askPermissions.launch(list.toTypedArray())
    }

    private fun toggleServer() {
        if (ServerState.status.value.running) {
            ExorServerService.stop(this)
        } else {
            if (!perms.storage) requestMediaPermissions()
            ExorServerService.start(this)
        }
    }

    private fun openFloating() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow \"Display over other apps\" for Exor, then tap Floating window again.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            FloatingWindowService.start(this)
            toast("Floating window opened.")
        }
    }

    private fun openTerminal() {
        if (!TermuxBridge.isInstalled(this)) {
            toast("Termux is not installed. Open Settings > Termux.")
        } else if (!TermuxBridge.openTerminal(this)) {
            toast("Could not open Termux.")
        }
    }

    private fun installTermux() {
        when (TermuxBridge.installBundled(this)) {
            TermuxBridge.InstallResult.NOT_BUNDLED -> toast("No Termux APK in this build. See guidetobuild.txt")
            TermuxBridge.InstallResult.NEEDS_PERMISSION -> toast("Allow installing apps for Exor, then tap Install again.")
            TermuxBridge.InstallResult.ERROR -> toast("Could not start the Termux installer.")
            TermuxBridge.InstallResult.LAUNCHED -> Unit
        }
    }

    private fun runExample() {
        if (!TermuxBridge.hasRunPermission(this)) {
            askTermuxPermission.launch(TermuxBridge.PERM_RUN_COMMAND)
            return
        }
        val ok = TermuxBridge.runPythonAsset(this, "example_server.py")
        toast(if (ok) "Sent to Termux." else "Termux refused. Did you paste the setup commands?")
    }

    private fun handlePickedFile(uri: Uri) {
        val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else "file"
        } ?: "file"
        selectedFileName = name
        // Copy to cache so we have a real path
        val dest = File(cacheDir, "workspace_${System.currentTimeMillis()}_$name")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            selectedFilePath = dest.absolutePath
            toast("Selected: $name")
        } catch (e: Exception) {
            toast("Could not read file: ${e.message}")
            selectedFileName = null
            selectedFilePath = null
        }
    }

    private fun startSelectedHost() {
        val path = selectedFilePath ?: return
        val name = selectedFileName ?: return
        val lower = name.lowercase()
        if (lower.endsWith(".py")) {
            // Check if Flask is mentioned
            val content = try { File(path).readText() } catch (_: Exception) { "" }
            if (content.contains("flask", ignoreCase = true) || content.contains("from flask")) {
                if (!TermuxBridge.hasRunPermission(this)) {
                    askTermuxPermission.launch(TermuxBridge.PERM_RUN_COMMAND)
                    toast("Grant Termux RUN_COMMAND first.")
                    return
                }
                // Copy to Termux-accessible location and run
                val ok = TermuxBridge.runPythonFile(this, path)
                toast(if (ok) "Python server started in Termux." else "Failed to run in Termux.")
            } else {
                toast("This .py does not appear to use Flask. Only Flask servers are auto-executed.")
            }
        } else {
            // HTML host
            WebHostService.start(this, path)
            isWebHosting = true
            val ip = NetUtil.localIp(this) ?: "0.0.0.0"
            webHostUrl = "http://$ip:${prefs.webPort}"
            toast("Web host started. Password: ${prefs.webPassword}")
        }
    }

    private fun stopWebHost() {
        WebHostService.stop(this)
        isWebHosting = false
        webHostUrl = null
        toast("Web host stopped.")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

private object BuildConfigInfo {
    const val VERSION = "0.2.0"
}
