package app.brokoli5191.quote.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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

/** Outcome of asking GitHub for the latest release, so callers can tell failures apart. */
sealed class ReleaseCheckResult {
    data class Found(val release: ReleaseInfo) : ReleaseCheckResult()

    /** No connection, DNS failure or timeout. */
    object Offline : ReleaseCheckResult()

    /** GitHub's unauthenticated API limit was hit (HTTP 429, or 403 with no requests left). */
    object RateLimited : ReleaseCheckResult()

    /** There is no published release, or the latest one has no .apk asset. */
    object NoApk : ReleaseCheckResult()

    /** Any other HTTP status, or a response that could not be parsed ([code] 200 then). */
    data class ServerError(val code: Int) : ReleaseCheckResult()
}

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/brokoli5191/quote/releases/latest"

    /** Source-compatible wrapper: the release, or null for every failure. */
    suspend fun checkLatestRelease(): ReleaseInfo? =
        (fetchLatestRelease() as? ReleaseCheckResult.Found)?.release

    suspend fun fetchLatestRelease(): ReleaseCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext classifyFailure(code, connection.getHeaderField("X-RateLimit-Remaining"))
            }
            parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ReleaseCheckResult.Offline
        } catch (_: Exception) {
            ReleaseCheckResult.ServerError(HttpURLConnection.HTTP_OK)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun classifyFailure(code: Int, rateLimitRemaining: String?): ReleaseCheckResult = when {
        code == 429 || (code == HttpURLConnection.HTTP_FORBIDDEN && rateLimitRemaining == "0") ->
            ReleaseCheckResult.RateLimited
        code == HttpURLConnection.HTTP_NOT_FOUND -> ReleaseCheckResult.NoApk
        else -> ReleaseCheckResult.ServerError(code)
    }

    internal fun parseRelease(body: String): ReleaseCheckResult {
        return try {
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            val assets = json.optJSONArray("assets") ?: return ReleaseCheckResult.NoApk
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return ReleaseCheckResult.Found(
                        ReleaseInfo(
                            version = tagName.removePrefix("v"),
                            downloadUrl = asset.getString("browser_download_url"),
                            sizeBytes = asset.optLong("size", 0L)
                        )
                    )
                }
            }
            ReleaseCheckResult.NoApk
        } catch (_: JSONException) {
            ReleaseCheckResult.ServerError(HttpURLConnection.HTTP_OK)
        }
    }

    /**
     * True when [remote] is a newer version than [current]. Accepts a leading "v" and
     * pre-release suffixes ("2.1.0-beta1"): a pre-release sorts before its release, build
     * metadata ("+5") is ignored.
     */
    fun isNewerVersion(current: String, remote: String): Boolean =
        compareVersions(remote, current) > 0

    internal fun compareVersions(a: String, b: String): Int {
        val va = ParsedVersion.parse(a)
        val vb = ParsedVersion.parse(b)
        for (i in 0 until maxOf(va.numbers.size, vb.numbers.size)) {
            val cmp = va.numbers.getOrElse(i) { 0 }.compareTo(vb.numbers.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        if (va.preRelease.isEmpty() && vb.preRelease.isEmpty()) return 0
        if (va.preRelease.isEmpty()) return 1
        if (vb.preRelease.isEmpty()) return -1
        for (i in 0 until maxOf(va.preRelease.size, vb.preRelease.size)) {
            val x = va.preRelease.getOrNull(i) ?: return -1
            val y = vb.preRelease.getOrNull(i) ?: return 1
            val cmp = comparePreReleaseIdentifier(x, y)
            if (cmp != 0) return cmp
        }
        return 0
    }

    /**
     * Numeric identifiers sort before alphanumeric ones (SemVer); alphanumeric identifiers compare
     * in natural order, so "beta2" < "beta10" as release tags are usually written.
     */
    private fun comparePreReleaseIdentifier(x: String, y: String): Int {
        val xn = x.toIntOrNull()
        val yn = y.toIntOrNull()
        if (xn != null && yn != null) return xn.compareTo(yn)
        if (xn != null) return -1
        if (yn != null) return 1
        val xs = NATURAL_CHUNK.findAll(x).map { it.value }.toList()
        val ys = NATURAL_CHUNK.findAll(y).map { it.value }.toList()
        for (i in 0 until minOf(xs.size, ys.size)) {
            val a = xs[i]
            val b = ys[i]
            val an = a.toBigIntegerOrNull()
            val bn = b.toBigIntegerOrNull()
            val cmp = if (an != null && bn != null) an.compareTo(bn) else a.compareTo(b)
            if (cmp != 0) return cmp
        }
        return xs.size.compareTo(ys.size)
    }

    private val NATURAL_CHUNK = Regex("\\d+|\\D+")

    private class ParsedVersion(val numbers: List<Int>, val preRelease: List<String>) {
        companion object {
            fun parse(raw: String): ParsedVersion {
                val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
                val core = clean.substringBefore('-')
                val pre = clean.substringAfter('-', "")
                return ParsedVersion(
                    core.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 },
                    if (pre.isEmpty()) emptyList() else pre.split('.', '-')
                )
            }
        }
    }

    /** Extracts the hash from a `sha256sum` line ("<hex>  <file>"), or null if it is not one. */
    internal fun parseSha256(text: String): String? {
        val hash = text.trim().substringBefore(' ').substringBefore('\t').lowercase()
        return hash.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /**
     * Downloads the APK to the cache directory and returns its path, or null on any failure.
     * Requires HTTP 200 and, when [expectedSize] is known, a matching length. The release
     * workflow publishes `<apk>.sha256` next to the APK; when it exists the download must match
     * it, when GitHub answers 404 for it the check is skipped. Old and partial APKs are deleted.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        version: String,
        expectedSize: Long = 0L,
        onProgress: (Int) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outFile = File(context.cacheDir, "update-v$safeVersion.apk")
        val partFile = File(context.cacheDir, "update-v$safeVersion.apk.part")
        context.cacheDir.listFiles { f -> f.name.startsWith("update-v") }?.forEach { it.delete() }

        var connection: HttpURLConnection? = null
        try {
            connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val totalBytes = connection.contentLength.toLong()
            val digest = MessageDigest.getInstance("SHA-256")

            var downloaded = 0L
            connection.inputStream.use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    var lastProgress = -1
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        digest.update(buffer, 0, bytes)
                        downloaded += bytes
                        if (totalBytes > 0) {
                            val progress = ((downloaded * 100) / totalBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            if (downloaded == 0L) return@withContext null
            if (totalBytes > 0 && downloaded != totalBytes) return@withContext null
            if (expectedSize > 0 && downloaded != expectedSize) return@withContext null

            val expectedHash = fetchExpectedSha256("$downloadUrl.sha256")
            if (expectedHash == FETCH_FAILED) return@withContext null
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedHash.isNotEmpty() && expectedHash != actualHash) return@withContext null

            if (!partFile.renameTo(outFile)) return@withContext null
            outFile.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
            partFile.delete()
        }
    }

    private const val FETCH_FAILED = "!"

    // Note: a .sha256 asset that exists but cannot be fetched rejects the download (null), deliberately strict.
    /** The published hash, "" when the release has no .sha256 asset, [FETCH_FAILED] on errors. */
    private fun fetchExpectedSha256(url: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    parseSha256(connection.inputStream.bufferedReader().use { it.readText() }) ?: FETCH_FAILED
                HttpURLConnection.HTTP_NOT_FOUND -> ""
                else -> FETCH_FAILED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FETCH_FAILED
        } finally {
            connection?.disconnect()
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
