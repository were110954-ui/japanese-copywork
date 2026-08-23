package com.bioluck.copywork.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bioluck.copywork.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = "",
    /** 배포 APK의 SHA-256. 설정하면 다운로드 변조를 추가로 차단한다. */
    val sha256: String? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val bytes: Long, val total: Long) : UpdateState
    data class Ready(val manifest: UpdateManifest, val apk: File) : UpdateState
    data class Error(val message: String, val manifest: UpdateManifest? = null) : UpdateState
}

class AppUpdater(private val activity: Activity) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        val url = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (url.isBlank()) return@withContext UpdateState.Idle
        runCatching {
            val response = client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute()
            response.use {
                check(it.isSuccessful) { "버전 정보를 확인할 수 없습니다. (HTTP ${it.code})" }
                val remote = gson.fromJson(checkNotNull(it.body).charStream(), UpdateManifest::class.java)
                require(remote.versionCode > 0 && remote.versionName.isNotBlank() && remote.apkUrl.startsWith("https://")) {
                    "업데이트 정보 형식이 올바르지 않습니다."
                }
                if (remote.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(remote) else UpdateState.Idle
            }
        }.getOrElse { UpdateState.Error(it.message ?: "업데이트 확인에 실패했습니다.") }
    }

    suspend fun download(manifest: UpdateManifest, progress: (UpdateState.Downloading) -> Unit): UpdateState =
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
                val partial = File(directory, "update-${manifest.versionCode}.apk.part")
                val target = File(directory, "update-${manifest.versionCode}.apk")
                partial.delete(); target.delete()
                val response = client.newCall(Request.Builder().url(manifest.apkUrl).build()).execute()
                response.use {
                    check(it.isSuccessful) { "APK 다운로드에 실패했습니다. (HTTP ${it.code})" }
                    val body = checkNotNull(it.body)
                    val total = body.contentLength()
                    body.byteStream().use { input -> partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count); downloaded += count
                            progress(UpdateState.Downloading(manifest, downloaded, total))
                        }
                    } }
                }
                manifest.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
                    check(sha256(partial).equals(expected.filterNot(Char::isWhitespace), ignoreCase = true)) {
                        "APK 무결성(SHA-256) 검증에 실패했습니다."
                    }
                }
                check(isSameApplication(partial)) { "다른 앱이거나 서명이 일치하지 않는 APK입니다." }
                check(partial.renameTo(target)) { "다운로드 파일을 저장하지 못했습니다." }
                UpdateState.Ready(manifest, target)
            }.getOrElse { UpdateState.Error(it.message ?: "APK 다운로드에 실패했습니다.", manifest) }
        }

    fun install(apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        })
    }

    @Suppress("DEPRECATION")
    private fun isSameApplication(apk: File): Boolean {
        val pm = activity.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageArchiveInfo(apk.path, flags)
        if (archive?.packageName != activity.packageName) return false
        val installed = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(activity.packageName, flags)
        val archiveCerts = archive.signingInfo?.apkContentsSigners?.map { certificateHash(it.toByteArray()) }?.toSet()
        val installedCerts = installed.signingInfo?.apkContentsSigners?.map { certificateHash(it.toByteArray()) }?.toSet()
        return !archiveCerts.isNullOrEmpty() && archiveCerts == installedCerts
    }

    private fun certificateHash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
