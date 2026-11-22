package com.nabhas.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AppUpdater(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Checks the latest GitHub release without downloading the APK.
     */
    fun checkForUpdate(callback: (UpdateCheckResult) -> Unit) {
        executor.execute {
            try {
                val releaseAsset = fetchLatestReleaseAsset()
                val lastInstalledUpdatedAt = preferences.getString(
                    KEY_LAST_INSTALLED_UPDATED_AT,
                    null
                )

                if (lastInstalledUpdatedAt != null &&
                    releaseAsset.updatedAt <= lastInstalledUpdatedAt
                ) {
                    postResult(
                        callback,
                        UpdateCheckResult.NoUpdate(
                            releaseAsset.updatedAt,
                            lastInstalledUpdatedAt
                        )
                    )
                } else {
                    postResult(
                        callback,
                        UpdateCheckResult.UpdateAvailable(
                            AvailableUpdate(
                                updatedAt = releaseAsset.updatedAt,
                                downloadUrl = releaseAsset.downloadUrl
                            )
                        )
                    )
                }
            } catch (exception: Exception) {
                postResult(callback, UpdateCheckResult.Failure(exception))
            }
        }
    }

    /**
     * Downloads the previously discovered APK and opens Android's package installer.
     */
    fun downloadAndInstall(
        update: AvailableUpdate,
        callback: (InstallResult) -> Unit
    ) {
        executor.execute {
            try {
                val apkFile = downloadApk(update.downloadUrl)

                mainHandler.post {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !appContext.packageManager.canRequestPackageInstalls()
                        ) {
                            val settingsIntent = Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${appContext.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            appContext.startActivity(settingsIntent)
                            callback(InstallResult.InstallPermissionRequired(apkFile))
                            return@post
                        }

                        openInstaller(apkFile)

                        // Android does not report installation completion for this ACTION_VIEW
                        // flow. Save the release timestamp once the installer has been opened.
                        preferences.edit()
                            .putString(
                                KEY_LAST_INSTALLED_UPDATED_AT,
                                update.updatedAt
                            )
                            .apply()

                        callback(
                            InstallResult.InstallerOpened(
                                update.updatedAt,
                                apkFile
                            )
                        )
                    } catch (exception: Exception) {
                        callback(InstallResult.Failure(exception))
                    }
                }
            } catch (exception: Exception) {
                postResult(callback, InstallResult.Failure(exception))
            }
        }
    }

    private fun fetchLatestReleaseAsset(): ReleaseAsset {
        val connection = (URL(RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val responseBody = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                throw IOException(
                    "GitHub Releases API returned HTTP $responseCode" +
                        if (responseBody.isNotBlank()) ": $responseBody" else "."
                )
            }

            val release = connection.inputStream
                .bufferedReader()
                .use { JSONObject(it.readText()) }

            val assets = release.optJSONArray("assets")
                ?: throw IOException("The GitHub release response has no assets list.")

            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") == APK_ASSET_NAME) {
                    val updatedAt = asset.optString("updated_at").trim()
                    val downloadUrl = asset.optString("browser_download_url").trim()

                    if (updatedAt.isBlank() || downloadUrl.isBlank()) {
                        throw IOException(
                            "The '$APK_ASSET_NAME' release asset is missing its timestamp or download URL."
                        )
                    }

                    return ReleaseAsset(updatedAt, downloadUrl)
                }
            }

            throw IOException(
                "The latest GitHub release does not contain '$APK_ASSET_NAME'."
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(downloadUrl: String): File {
        val updatesDirectory = File(appContext.cacheDir, UPDATES_DIRECTORY)
        if (!updatesDirectory.exists() && !updatesDirectory.mkdirs()) {
            throw IOException("Could not create the APK download directory.")
        }

        val temporaryFile = File(updatesDirectory, "$APK_ASSET_NAME.download")
        val apkFile = File(updatesDirectory, APK_ASSET_NAME)

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("APK download returned HTTP $responseCode.")
            }

            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(temporaryFile.outputStream()).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (!temporaryFile.exists() || temporaryFile.length() == 0L) {
                throw IOException("The downloaded APK is empty.")
            }

            if (apkFile.exists() && !apkFile.delete()) {
                throw IOException("Could not replace the previously downloaded APK.")
            }

            if (!temporaryFile.renameTo(apkFile)) {
                throw IOException("Could not finalize the downloaded APK.")
            }

            return apkFile
        } catch (exception: Exception) {
            temporaryFile.delete()
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    private fun openInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        appContext.startActivity(installIntent)
    }

    private fun postResult(
        callback: (UpdateCheckResult) -> Unit,
        result: UpdateCheckResult
    ) {
        mainHandler.post { callback(result) }
    }

    private fun postResult(
        callback: (InstallResult) -> Unit,
        result: InstallResult
    ) {
        mainHandler.post { callback(result) }
    }

    data class AvailableUpdate internal constructor(
        val updatedAt: String,
        internal val downloadUrl: String
    )

    private data class ReleaseAsset(
        val updatedAt: String,
        val downloadUrl: String
    )

    sealed class UpdateCheckResult {
        data class NoUpdate(
            val latestUpdatedAt: String,
            val lastInstalledUpdatedAt: String
        ) : UpdateCheckResult()

        data class UpdateAvailable(
            val update: AvailableUpdate
        ) : UpdateCheckResult()

        data class Failure(
            val cause: Exception
        ) : UpdateCheckResult()
    }

    sealed class InstallResult {
        data class InstallerOpened(
            val updatedAt: String,
            val apkFile: File
        ) : InstallResult()

        data class InstallPermissionRequired(
            val apkFile: File
        ) : InstallResult()

        data class Failure(
            val cause: Exception
        ) : InstallResult()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_updater_preferences"
        const val KEY_LAST_INSTALLED_UPDATED_AT = "last_installed_updated_at"
        const val RELEASE_API_URL =
            "https://api.github.com/repos/gkgk84373-arch/NabhasAI/releases/tags/latest"
        const val APK_ASSET_NAME = "nabhas-ai-debug.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UPDATES_DIRECTORY = "updates"
        const val NETWORK_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 8 * 1024
        const val USER_AGENT = "NabhasAI-AppUpdater"
    }
}
