package com.flysafeweather.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object AppUpdateInstaller {
    private const val UPDATE_DIR = "updates"
    private const val APK_NAME = "FlySafeWeather-update.apk"

    sealed class Result {
        data object LaunchedInstaller : Result()
        data object NeedsInstallPermission : Result()
        data class Failed(val message: String) : Result()
    }

    fun needsInstallPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Downloads [apkUrl] into app cache and opens the system package installer.
     * Call from a background thread.
     */
    fun downloadAndInstall(context: Context, apkUrl: String): Result {
        if (needsInstallPermission(context)) {
            return Result.NeedsInstallPermission
        }
        return runCatching {
            val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
            val apkFile = File(dir, APK_NAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }
            val bytes = AppUpdateChecker.getBytes(apkUrl)
            require(bytes.size > 10_000) { "Download looked empty (${bytes.size} bytes)" }
            apkFile.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Result.LaunchedInstaller
        }.getOrElse { error ->
            Result.Failed(error.message ?: "Update download failed")
        }
    }
}
