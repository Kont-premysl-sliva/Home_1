package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {
    const val LATEST_CLOUD_VERSION_NAME = "404"
    const val LATEST_CLOUD_VERSION_CODE = 404
    const val LATEST_CLOUD_UPDATE_DATE = "1.11.2026"
    const val APK_DOWNLOAD_URL = "https://ais-dev-6j7lbmiei2bndsmwbtshh6-935777442239.europe-west2.run.app/app-release.apk"

    fun getLocalVersionCode(context: Context): Long {
        val code = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            APP_VERSION_NUMBER.toLong()
        }
        return maxOf(code, APP_VERSION_NUMBER.toLong())
    }

    fun isUpdateAvailable(context: Context): Boolean {
        val cloudMax = maxOf(
            LATEST_CLOUD_VERSION_CODE.toLong(),
            com.example.data.GitHubBackupManager.highestVersionFlow.value.toLong()
        )
        return cloudMax > getLocalVersionCode(context)
    }

    fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 100_000) return false
        return try {
            file.inputStream().use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openBrowserDownload(context: Context, urlStr: String = APK_DOWNLOAD_URL) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Nelze otevřít prohlížeč: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!isValidApkFile(apkFile)) {
            Toast.makeText(
                context,
                "Soubor APK není kompletní nebo platný. Použijte odkaz pro stažení v prohlížeči.",
                Toast.LENGTH_LONG
            ).show()
            openBrowserDownload(context)
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Chyba při spuštění instalace APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun tryDownloadFromGitHub(
        context: Context,
        targetFile: File,
        versionName: String = "",
        onProgress: (Int, String) -> Unit
    ): Boolean {
        val primaryRepo = com.example.data.GitHubBackupManager.getRepo(context).trim().removePrefix("https://github.com/").trimEnd('/')
        val branch = com.example.data.GitHubBackupManager.getBranch(context).trim().ifBlank { "main" }
        val token = com.example.data.GitHubBackupManager.getToken(context).trim()
        val reposToTry = listOf(primaryRepo, com.example.data.GitHubBackupManager.PUBLIC_PORTAL_REPO)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        if (reposToTry.isEmpty()) return false

        val authHeader = if (token.isNotBlank()) {
            if (token.startsWith("ghp_") || token.startsWith("github_pat_")) "Bearer $token" else "token $token"
        } else ""

        for (repo in reposToTry) {
            // 1. Zkusíme GitHub Releases (/releases/latest a /releases) pro nejnovější vydanou APK
            for (relEndpoint in listOf("releases/latest", "releases?per_page=5")) {
                try {
                    val relUrl = java.net.URI("https://api.github.com/repos/$repo/$relEndpoint").toURL()
                    val relConn = (relUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 12000
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                        setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                        if (authHeader.isNotBlank()) {
                            setRequestProperty("Authorization", authHeader)
                        }
                    }
                    if (relConn.responseCode == 200) {
                        val body = relConn.inputStream.bufferedReader().readText().trim()
                        val relObjects = mutableListOf<org.json.JSONObject>()
                        if (body.startsWith("{")) {
                            relObjects.add(org.json.JSONObject(body))
                        } else if (body.startsWith("[")) {
                            val arr = org.json.JSONArray(body)
                            for (idx in 0 until arr.length()) {
                                relObjects.add(arr.getJSONObject(idx))
                            }
                        }
                        for (relJson in relObjects) {
                            if (relJson.has("assets")) {
                                val assets = relJson.getJSONArray("assets")
                                for (i in 0 until assets.length()) {
                                    val asset = assets.getJSONObject(i)
                                    val name = asset.optString("name", "")
                                    if (name.endsWith(".apk", ignoreCase = true)) {
                                        val assetUrl = asset.optString("url", "")
                                        if (assetUrl.isNotBlank()) {
                                            val ok = downloadGitHubAsset(assetUrl, token, targetFile, onProgress)
                                            if (ok && isValidApkFile(targetFile)) return true
                                        }
                                        val browserUrl = asset.optString("browser_download_url", "")
                                        if (browserUrl.isNotBlank()) {
                                            val ok = downloadRawUrl(browserUrl, targetFile, "", onProgress)
                                            if (ok && isValidApkFile(targetFile)) return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Dynamicky prohledáme obsah složky android/ na GitHubu pro nalezení libovolné APK
            try {
                val androidContentsUrl = java.net.URI("https://api.github.com/repos/$repo/contents/android?ref=$branch").toURL()
                val aConn = (androidContentsUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                    if (authHeader.isNotBlank()) {
                        setRequestProperty("Authorization", authHeader)
                    }
                }
                if (aConn.responseCode == 200) {
                    val arr = org.json.JSONArray(aConn.inputStream.bufferedReader().readText())
                    for (idx in 0 until arr.length()) {
                        val item = arr.getJSONObject(idx)
                        val name = item.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            val dUrl = item.optString("download_url", "")
                            if (dUrl.isNotBlank() && dUrl != "null") {
                                val ok = downloadRawUrl(dUrl, targetFile, "", onProgress)
                                if (ok && isValidApkFile(targetFile)) return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Zkusíme známé cesty k APK v repozitáři podle rozdělení (android/ i kořen)
            val cleanVer = versionName.trim().removePrefix("v").trim()
            val apkCandidates = mutableListOf<String>()
            if (cleanVer.isNotBlank()) {
                apkCandidates.add("android/Kontejnery_Ostrava_Android_v$cleanVer.apk")
                apkCandidates.add("android/Kontejnery_Ostrava_Android_$cleanVer.apk")
                apkCandidates.add("android/app-release-v$cleanVer.apk")
                apkCandidates.add("Kontejnery_Ostrava_Android_v$cleanVer.apk")
            }
            apkCandidates.add("android/Kontejnery_Ostrava_Android.apk")
            apkCandidates.add("android/app-release.apk")
            apkCandidates.add("android/Kontejnery_Ostrava.apk")
            apkCandidates.add("Kontejnery_Ostrava_Android.apk")
            apkCandidates.add("app-release.apk")

            for (apkPath in apkCandidates.distinct()) {
                try {
                    val apiUrl = java.net.URI("https://api.github.com/repos/$repo/contents/$apkPath?ref=$branch").toURL()
                    val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 12000
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                        setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                        if (authHeader.isNotBlank()) {
                            setRequestProperty("Authorization", authHeader)
                        }
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(body)
                        val dUrl = json.optString("download_url", "")
                        if (dUrl.isNotBlank() && dUrl != "null") {
                            val ok = downloadRawUrl(dUrl, targetFile, "", onProgress)
                            if (ok && isValidApkFile(targetFile)) return true
                        }
                    }
                } catch (_: Exception) {}

                // Fallback přes raw.githubusercontent.com
                try {
                    val rawUrl = "https://raw.githubusercontent.com/$repo/$branch/$apkPath"
                    val ok = downloadRawUrl(rawUrl, targetFile, authHeader, onProgress)
                    if (ok && isValidApkFile(targetFile)) return true
                } catch (_: Exception) {}
            }
        }

        return false
    }

    private fun downloadGitHubAsset(assetApiUrl: String, token: String, targetFile: File, onProgress: (Int, String) -> Unit): Boolean {
        return try {
            val authHeader = if (token.isNotBlank()) {
                if (token.startsWith("ghp_") || token.startsWith("github_pat_")) "Bearer $token" else "token $token"
            } else ""
            val url = java.net.URI(assetApiUrl).toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 30000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                if (authHeader.isNotBlank()) {
                    setRequestProperty("Authorization", authHeader)
                }
            }
            val code = conn.responseCode
            if (code == 302 || code == 301 || code == 307 || code == 308) {
                val redirectUrl = conn.getHeaderField("Location")
                if (!redirectUrl.isNullOrBlank()) {
                    // S3 redirect URL nesmí mít Authorization hlavičku z GitHubu
                    return downloadRawUrl(redirectUrl, targetFile, "", onProgress)
                }
            } else if (code == 200) {
                val total = conn.contentLength.coerceAtLeast(1)
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buf = ByteArray(16384)
                        var read: Int
                        var curr = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            curr += read
                            val pct = ((curr * 100) / total).toInt().coerceIn(0, 100)
                            onProgress(pct, "${curr / 1024} KB / ${total / 1024} KB")
                        }
                    }
                }
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun downloadRawUrl(urlStr: String, targetFile: File, authHeader: String = "", onProgress: (Int, String) -> Unit): Boolean {
        val successWithoutAuth = downloadRawUrlInternal(urlStr, targetFile, "", onProgress)
        if (successWithoutAuth) return true

        if (authHeader.isNotBlank()) {
            return downloadRawUrlInternal(urlStr, targetFile, authHeader, onProgress)
        }
        return false
    }

    private fun downloadRawUrlInternal(urlStr: String, targetFile: File, authHeader: String = "", onProgress: (Int, String) -> Unit): Boolean {
        return try {
            var currentUrl = urlStr
            var redirects = 0
            while (redirects < 5) {
                val conn = (java.net.URI(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 12000
                    readTimeout = 45000
                    setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                    if (authHeader.isNotBlank() && (currentUrl.contains("api.github.com") || currentUrl.contains("github.com"))) {
                        setRequestProperty("Authorization", authHeader)
                    }
                }
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val loc = conn.getHeaderField("Location") ?: return false
                    currentUrl = loc
                    redirects++
                    continue
                }
                if (code == 200) {
                    val total = conn.contentLength.coerceAtLeast(1)
                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buf = ByteArray(16384)
                            var read: Int
                            var curr = 0L
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                curr += read
                                val pct = ((curr * 100) / total).toInt().coerceIn(0, 100)
                                onProgress(pct, "${curr / 1024} KB / ${total / 1024} KB")
                            }
                        }
                    }
                    return true
                }
                return false
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun WifiUpdateBanner(
    forceShow: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadStatusText by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    val updateAvailable = remember(context) { AppUpdateManager.isUpdateAvailable(context) }

    if (!forceShow && !updateAvailable && !showDialog) return

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (updateAvailable) Color(0xFF16A34A) else Color(0xFF0284C7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (updateAvailable) Icons.Default.SystemUpdate else Icons.Default.SystemUpdate,
                        contentDescription = "Aktualizace",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (updateAvailable) "Nová verze v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} na cloudu!" else "✅ Aplikace je aktuální • v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} • ${AppUpdateManager.LATEST_CLOUD_UPDATE_DATE}",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = if (updateAvailable) "Klikněte pro Wi-Fi aktualizaci aplikace v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME}" else "Používáte nejnovější S POHODA v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} • Aktualizace ${AppUpdateManager.LATEST_CLOUD_UPDATE_DATE}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (updateAvailable) Color(0xFF16A34A) else Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (updateAvailable) "AKTUALIZOVAT" else "STAV v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                IconButton(onClick = { dismissed = true; onDismiss() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            icon = {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    if (updateAvailable) "Wi-Fi Aktualizace Aplikace v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME}" else "✅ v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} je Nainstalována a Aktivní",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (updateAvailable)
                            "Aktualizace v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} (${AppUpdateManager.LATEST_CLOUD_UPDATE_DATE}) probíhá automaticky přes Wi-Fi z cloudu. Pokud preferujete přímé stažení APK v prohlížeči, použijte tlačítko níže."
                        else
                            "Gratulujeme! V této aplikaci již běží nejnovější v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} (aktualizace ${AppUpdateManager.LATEST_CLOUD_UPDATE_DATE}) se všemi funkcemi a cloudovou synchronizací pro PC Terminál i mobilní zařízení.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isDownloading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Color(0xFF16A34A),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$downloadProgress %", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(downloadStatusText, fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }

                    OutlinedButton(
                        onClick = { AppUpdateManager.openBrowserDownload(context) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("OTEVŘÍT STAHOVÁNÍ APK V PROHLÍŽEČI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDownloading = true
                            downloadStatusText = "Navazuji spojení s GitHubem / Cloudem přes Wi-Fi..."
                            withContext(Dispatchers.IO) {
                                try {
                                    val apkFile = File(context.cacheDir, "Kontejnery_v${AppUpdateManager.LATEST_CLOUD_VERSION_NAME}.apk")
                                    var downloadedSuccessfully = false

                                    // 1. Zkusíme stáhnout z GitHub repozitáře / releases
                                    withContext(Dispatchers.Main) {
                                        downloadStatusText = "Hledám aktualizaci na GitHubu..."
                                    }
                                    downloadedSuccessfully = AppUpdateManager.tryDownloadFromGitHub(context, apkFile) { pct, status ->
                                        scope.launch(Dispatchers.Main) {
                                            downloadProgress = pct
                                            downloadStatusText = "GitHub Wi-Fi: $status"
                                        }
                                    }

                                    // 2. Pokud není na GitHubu, stáhneme z Cloud serveru
                                    if (!downloadedSuccessfully) {
                                        withContext(Dispatchers.Main) {
                                            downloadStatusText = "Stahuji z Cloudu..."
                                        }
                                        downloadedSuccessfully = AppUpdateManager.downloadRawUrl(AppUpdateManager.APK_DOWNLOAD_URL, apkFile) { pct, status ->
                                            scope.launch(Dispatchers.Main) {
                                                downloadProgress = pct
                                                downloadStatusText = "Cloud Wi-Fi: $status"
                                            }
                                        }
                                    }

                                    downloadedSuccessfully = downloadedSuccessfully && AppUpdateManager.isValidApkFile(apkFile)

                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        if (downloadedSuccessfully) {
                                            showDialog = false
                                            AppUpdateManager.installApk(context, apkFile)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Otevírám stažení verze ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME} v prohlížeči pro přímou instalaci APK...",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            AppUpdateManager.openBrowserDownload(context)
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        Toast.makeText(context, "Chyba při stahování: ${e.message}", Toast.LENGTH_LONG).show()
                                        AppUpdateManager.openBrowserDownload(context)
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text(if (isDownloading) "STAHOVÁNÍ..." else "STÁHNOUT A NAINSTALOVAT v ${AppUpdateManager.LATEST_CLOUD_VERSION_NAME}", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showDialog = false }) {
                        Text("ZRUŠIT", fontSize = 12.sp)
                    }
                }
            }
        )
    }
}
