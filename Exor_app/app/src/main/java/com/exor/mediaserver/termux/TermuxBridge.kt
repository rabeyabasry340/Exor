package com.exor.mediaserver.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Glue to Termux. The APK itself is NOT built into this project: drop it into
 * app/src/main/assets/termux/ (see guidetobuild.txt) and this class installs it on demand.
 */
object TermuxBridge {
    const val PKG = "com.termux"
    const val PERM_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    private const val PREFIX = "/data/data/com.termux/files/usr"
    private const val HOME = "/data/data/com.termux/files/home"

    /** Paste-able first-run setup for the Termux shell. */
    const val SETUP_COMMANDS =
        "mkdir -p ~/.termux && (grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null || " +
            "echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && " +
            "termux-reload-settings; pkg update -y && pkg install -y python"

    enum class InstallResult { NOT_BUNDLED, NEEDS_PERMISSION, LAUNCHED, ERROR }

    fun isInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Name of the *.apk placed in assets/termux/, or null if only the .info placeholder is there. */
    fun bundledApk(ctx: Context): String? =
        ctx.assets.list("termux")?.firstOrNull { it.endsWith(".apk", ignoreCase = true) }

    fun hasRunPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERM_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED

    fun installBundled(ctx: Context): InstallResult {
        val name = bundledApk(ctx) ?: return InstallResult.NOT_BUNDLED
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallResult.NEEDS_PERMISSION
        }
        return try {
            val dir = File(ctx.cacheDir, "termux-install").apply { mkdirs() }
            val out = File(dir, "termux.apk")
            ctx.assets.open("termux/$name").use { i -> out.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            InstallResult.LAUNCHED
        } catch (e: Exception) {
            InstallResult.ERROR
        }
    }

    fun openTerminal(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /**
     * Writes an asset script into Termux's home (~/exor/<name>) and runs it with python in a
     * background Termux session. Needs: Termux installed + opened once, `python` installed,
     * allow-external-apps=true, and the RUN_COMMAND permission granted to this app.
     */
    fun runPythonAsset(ctx: Context, assetName: String): Boolean {
        val src = try { ctx.assets.open("scripts/$assetName").bufferedReader().use { it.readText() } } catch (e: Exception) { return false }
        val marker = "EXOR_EOF_7f3a"
        val cmd = "mkdir -p ~/exor && cat > ~/exor/$assetName <<'$marker'\n$src\n$marker\n" +
            "cd ~/exor && exec $PREFIX/bin/python ~/exor/$assetName"
        return runBash(ctx, cmd, background = true)
    }

    /** Run an absolute-path .py file (copied into Termux home first). */
    fun runPythonFile(ctx: Context, absolutePath: String): Boolean {
        val src = try { File(absolutePath).readText() } catch (e: Exception) { return false }
        val name = File(absolutePath).name
        val marker = "EXOR_EOF_7f3a"
        val cmd = "mkdir -p ~/exor && cat > ~/exor/$name <<'$marker'\n$src\n$marker\n" +
            "cd ~/exor && exec $PREFIX/bin/python ~/exor/$name"
        return runBash(ctx, cmd, background = true)
    }

    fun runBash(ctx: Context, command: String, background: Boolean): Boolean {
        return try {
            val i = Intent()
                .setClassName(PKG, "com.termux.app.RunCommandService")
                .setAction("com.termux.RUN_COMMAND")
                .putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/bash")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            ContextCompat.startForegroundService(ctx, i)
            true
        } catch (e: Exception) {
            false // SecurityException = permission / allow-external-apps missing
        }
    }

    fun copySetupCommands(ctx: Context) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Termux setup", SETUP_COMMANDS))
    }
}
