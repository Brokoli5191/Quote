package app.brokoli5191.quote.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class UpdateAvailable(val version: String, val downloadUrl: String, val sizeBytes: Long) : UpdateStatus()
    data class Downloading(val progress: Int) : UpdateStatus()
    data class ReadyToInstall(val filePath: String, val version: String) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

data class ReleaseInfo(val version: String, val downloadUrl: String, val sizeBytes: Long)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/brokoli5191/quote/releases/latest"

    suspend fun checkLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (connection.responseCode != 200) return@withContext null

            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val tagName = json.getString("tag_name")
            val assets = json.getJSONArray("assets")

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    return@withContext ReleaseInfo(
                        version = tagName.removePrefix("v"),
                        downloadUrl = asset.getString("browser_download_url"),
                        sizeBytes = asset.optLong("size", 0L)
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isNewerVersion(current: String, remote: String): Boolean {
        fun parse(v: String) = v.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val c = parse(current)
        val r = parse(remote)
        for (i in 0 until maxOf(c.size, r.size)) {
            val cv = c.getOrElse(i) { 0 }
            val rv = r.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        version: String,
        onProgress: (Int) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            val totalBytes = connection.contentLength.toLong()

            val outFile = File(context.cacheDir, "update-v$version.apk")
            val input = connection.inputStream
            val output = outFile.outputStream()
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var bytes: Int
            while (input.read(buffer).also { bytes = it } != -1) {
                output.write(buffer, 0, bytes)
                downloaded += bytes
                if (totalBytes > 0) {
                    onProgress(((downloaded * 100) / totalBytes).toInt())
                }
            }
            output.flush()
            output.close()
            input.close()
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
