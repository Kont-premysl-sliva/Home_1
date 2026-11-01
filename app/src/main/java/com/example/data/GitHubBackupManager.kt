package com.example.data

import android.content.Context
import android.util.Base64
import com.example.ui.HtmlDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GitHubBackupManager {

    private const val PREFS_NAME = "github_sync_prefs"
    private const val KEY_REPO = "github_repo"
    private const val KEY_BRANCH = "github_branch"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_LAST_SYNC = "github_last_sync"

    const val DEFAULT_REPO = "Kont-premysl-sliva/Home_1"
    const val PUBLIC_PORTAL_REPO = "Kont-premysl-sliva/Home_1"
    const val FALLBACK_PORTAL_REPO = "Kont-premysl-sliva/Home_1"
    const val DEFAULT_BRANCH = "main"
    const val DEFAULT_TOKEN = ""

    // Expirace vygenerovaného tokenu: 16. prosince 2026 18:32 UTC
    const val TOKEN_EXPIRATION_DATE_STR = "16. 12. 2026"
    const val TOKEN_EXPIRATION_TIMESTAMP_MS = 1797445928000L // 2026-12-16 18:32:08 UTC

    data class TokenExpirationStatus(
        val isExpired: Boolean,
        val isWarningPeriod: Boolean, // < 30 dní před vypršením
        val daysRemaining: Long,
        val expirationDate: String,
        val warningMessage: String?
    )

    fun getTokenExpirationStatus(): TokenExpirationStatus {
        val now = System.currentTimeMillis()
        val diffMs = TOKEN_EXPIRATION_TIMESTAMP_MS - now
        val daysRemaining = if (diffMs > 0) diffMs / (1000L * 60 * 60 * 24) else 0L

        return when {
            diffMs <= 0 -> {
                TokenExpirationStatus(
                    isExpired = true,
                    isWarningPeriod = true,
                    daysRemaining = 0,
                    expirationDate = TOKEN_EXPIRATION_DATE_STR,
                    warningMessage = "⛔ GitHub token expiroval $TOKEN_EXPIRATION_DATE_STR! Pro obnovení Wi-Fi záloh a aktualizací je nutné na GitHubu vygenerovat nový token."
                )
            }
            daysRemaining <= 14 -> {
                TokenExpirationStatus(
                    isExpired = false,
                    isWarningPeriod = true,
                    daysRemaining = daysRemaining,
                    expirationDate = TOKEN_EXPIRATION_DATE_STR,
                    warningMessage = "⚠️ KRITICKÉ UPOZORNĚNÍ: GitHub token expiruje již za $daysRemaining dní ($TOKEN_EXPIRATION_DATE_STR)! Nezapomeňte včas vygenerovat nový token na GitHubu."
                )
            }
            daysRemaining <= 30 -> {
                TokenExpirationStatus(
                    isExpired = false,
                    isWarningPeriod = true,
                    daysRemaining = daysRemaining,
                    expirationDate = TOKEN_EXPIRATION_DATE_STR,
                    warningMessage = "⚠️ Upozornění: GitHub token brzy vyprší – zbývá $daysRemaining dní (platný do $TOKEN_EXPIRATION_DATE_STR)."
                )
            }
            else -> {
                TokenExpirationStatus(
                    isExpired = false,
                    isWarningPeriod = false,
                    daysRemaining = daysRemaining,
                    expirationDate = TOKEN_EXPIRATION_DATE_STR,
                    warningMessage = null
                )
            }
        }
    }

    fun getRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REPO, DEFAULT_REPO)?.ifBlank { DEFAULT_REPO } ?: DEFAULT_REPO
        if (saved.contains("Home_1", ignoreCase = true) || saved.contains("Kont_1", ignoreCase = true) || saved.contains("preprava", ignoreCase = true) || saved.contains("Home_1", ignoreCase = true)) {
            prefs.edit().putString(KEY_REPO, DEFAULT_REPO).apply()
            return DEFAULT_REPO
        }
        return saved
    }

    fun setRepo(context: Context, repo: String) {
        val target = if (repo.isBlank() || repo.contains("Home_1", ignoreCase = true) || repo.contains("Kont_1", ignoreCase = true) || repo.contains("preprava", ignoreCase = true) || repo.contains("Home_1", ignoreCase = true)) {
            DEFAULT_REPO
        } else {
            repo.trim()
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPO, target).apply()
    }

    fun getBranch(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BRANCH, DEFAULT_BRANCH)?.ifBlank { DEFAULT_BRANCH } ?: DEFAULT_BRANCH
    }

    fun setBranch(context: Context, branch: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BRANCH, branch.trim()).apply()
    }

    fun getToken(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
        if (saved.isNullOrBlank() || saved.contains("ghp_fQzEcvExoIzjx") || saved.contains("11CR6KY") || saved.startsWith("github_pat_")) {
            return ""
        }
        return saved.trim()
    }

    fun setToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun getLastSync(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SYNC, "Zatím nesynchronizováno") ?: "Zatím nesynchronizováno"
    }

    private fun setLastSync(context: Context) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("cs-CZ"))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SYNC, sdf.format(Date())).apply()
    }

    sealed class GitHubResult {
        data class Success(val message: String, val details: String = "") : GitHubResult()
        data class Error(val message: String) : GitHubResult()
    }

    private fun getAuthHeader(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("ghp_") || trimmed.startsWith("github_pat_")) {
            "Bearer $trimmed"
        } else {
            "token $trimmed"
        }
    }

    /**
     * Otestuje spojení s GitHub repozitářem a ověří platnost Personal Access Tokenu.
     */
    suspend fun testConnection(
        context: Context,
        repoInput: String = getRepo(context),
        tokenInput: String = getToken(context)
    ): GitHubResult = withContext(Dispatchers.IO) {
        val repo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
        val token = tokenInput.trim().ifBlank { getToken(context) }

        if (repo.isBlank()) {
            return@withContext GitHubResult.Error("Zadejte prosím název repozitáře ve tvaru: Vlastnik/NazevRepozitare (např. $DEFAULT_REPO)")
        }

        try {
            // 1. Nejprve zkusíme autorizované volání, pokud je zadán token
            var isTokenValid = false
            var isPrivate = false
            var defBranch = "main"
            var httpCode = 0

            if (token.isNotBlank()) {
                val url = URI("https://api.github.com/repos/$repo").toURL()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Kontejnery-Android-App")
                    setRequestProperty("Authorization", getAuthHeader(token))
                }
                httpCode = conn.responseCode
                if (httpCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val repoJson = JSONObject(body)
                    isPrivate = repoJson.optBoolean("private", false)
                    defBranch = repoJson.optString("default_branch", "main")
                    isTokenValid = true
                }
                conn.disconnect()
            }

            // 2. Pokud token není zadán nebo vrátil 401/403, otestujeme veřejný přístup (bez Authorization headeru)
            if (!isTokenValid) {
                val pubUrl = URI("https://api.github.com/repos/$repo").toURL()
                val pubConn = (pubUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Kontejnery-Android-App")
                }
                val pubCode = pubConn.responseCode
                if (pubCode == 200) {
                    val pubBody = pubConn.inputStream.bufferedReader().readText()
                    val repoJson = JSONObject(pubBody)
                    isPrivate = repoJson.optBoolean("private", false)
                    defBranch = repoJson.optString("default_branch", "main")
                    pubConn.disconnect()

                    val tokenNote = if (token.isNotBlank()) {
                        "⚠️ Zadaný token GitHub odmítl (Chyba $httpCode: Neplatný nebo vypršený token).\n" +
                        "Pro nahrávání nových verzí na GitHub (zápis) prosím vložte nový Classic token v Nastavení."
                    } else {
                        "ℹ️ Token zatím není zadán (režim pouze pro čtení).\n" +
                        "Stahování aktualizací softwaru i dat funguje. Pro nahrávání nových verzí na GitHub vložte Classic token v Nastavení."
                    }

                    return@withContext GitHubResult.Success(
                        message = "Veřejné spojení s GitHubem ověřeno!",
                        details = "🌐 Veřejný repozitář: $repo\n" +
                                "✅ Výchozí větev: $defBranch • Čtení a stahování aktualizací pro řidiče je plně funkční.\n\n" +
                                tokenNote
                    )
                } else if (pubCode == 404) {
                    pubConn.disconnect()
                    return@withContext GitHubResult.Error(
                        "Repozitář '$repo' nebyl na GitHubu nalezen (Chyba 404).\n" +
                        "Zkontrolujte prosím přesný název repozitáře (má být Kont-premysl-sliva/Home_1)."
                    )
                }
                pubConn.disconnect()
            }

            // 3. Plně autorizovaný přístup
            if (isTokenValid) {
                val contentsCheck = fetchRepoFilesList(repo, defBranch, token)
                val filesList = contentsCheck.first
                val hasBackup = filesList.any { it.contains("containers_sync.json") || it.contains("backup.json") }
                val filesSummary = if (filesList.isNotEmpty()) {
                    "Nalezené soubory: " + filesList.take(6).joinToString(", ") + (if (filesList.size > 6) " a další..." else "")
                } else {
                    "V kořenu repozitáře zatím nejsou soubory."
                }
                val backupStatus = if (hasBackup) "✅ Soubor zálohy databáze připraven k načtení." else "ℹ️ Záloha (containers_sync.json) zatím v repozitáři není."

                return@withContext GitHubResult.Success(
                    message = "Spojení s GitHubem je plně funkční!",
                    details = "✅ Plný přístup (čtení i zápis aktivní)\n" +
                            "${if (isPrivate) "🔒 Soukromý repozitář" else "🌐 Veřejný repozitář"} • Větev: $defBranch\n" +
                            "$filesSummary\n$backupStatus"
                )
            }

            GitHubResult.Error("Nepodařilo se navázat spojení s GitHub repozitářem '$repo' (HTTP $httpCode).")
        } catch (e: Exception) {
            GitHubResult.Error("Chyba sítě při testu spojení: ${e.message}\nUjistěte se, že máte aktivní Wi-Fi nebo mobilní data.")
        }
    }

    private fun fetchRepoFilesList(repo: String, branch: String, token: String): Pair<List<String>, Int> {
        return try {
            val url = URI("https://api.github.com/repos/$repo/contents?ref=$branch").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Kontejnery-Android-App")
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", getAuthHeader(token))
                }
            }
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONArray(body)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(item.optString("name"))
                }
                Pair(list, code)
            } else {
                Pair(emptyList(), code)
            }
        } catch (_: Exception) {
            Pair(emptyList(), 0)
        }
    }

    /**
     * Rychlá tichá obousměrná synchronizace pouze datového souboru containers_sync.json
     * s GitHubem (Home_1 + veřejný portál Home_1) bez navyšování verze SW.
     */
    suspend fun syncDataOnlyWithGitHub(
        context: Context,
        pullFirst: Boolean = true,
        pushAfter: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = getRepo(context).trim().removePrefix("https://github.com/").trimEnd('/')
            val branch = getBranch(context).trim().ifBlank { "main" }
            val token = getToken(context).trim().ifBlank { DEFAULT_TOKEN }
            if (repo.isBlank() || token.isBlank()) return@withContext false

            var anySuccess = false

            if (pullFirst) {
                val syncBytes = fetchGitHubFileBytes(repo, branch, "containers_sync.json", token)
                    ?: fetchGitHubFileBytes(PUBLIC_PORTAL_REPO, branch, "containers_sync.json", token)
                if (syncBytes != null && syncBytes.isNotEmpty()) {
                    val jsonStr = syncBytes.toString(Charsets.UTF_8)
                    if (jsonStr.trimStart().startsWith("{")) {
                        HtmlDownloadManager.saveWebSyncSnapshotToMobile(context, jsonStr)
                        anySuccess = true
                    }
                }
            }

            if (pushAfter) {
                val snapshotJson = HtmlDownloadManager.buildMobileSyncSnapshotJson(context)
                val jsonBytes = snapshotJson.toByteArray(Charsets.UTF_8)
                if (jsonBytes.size > 20) {
                    val ok1 = uploadRawBytes(repo, branch, "containers_sync.json", jsonBytes, token, "sync: automatická synchronizace dat (containers_sync.json)")
                    val ok2 = if (!repo.equals(PUBLIC_PORTAL_REPO, ignoreCase = true)) {
                        uploadRawBytes(PUBLIC_PORTAL_REPO, branch, "containers_sync.json", jsonBytes, token, "sync: automatická synchronizace dat (containers_sync.json)")
                    } else true
                    if (ok1 || ok2) {
                        setLastSync(context)
                        anySuccess = true
                    }
                }
            }

            anySuccess
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Stáhne zálohu přímo z GitHubu přes Wi-Fi/Internet bez nutnosti propojovat telefon kabelem.
     * Zkouší postupně standardní záložní soubory (containers_sync.json, backup.json, DataBackup.zip, atd.)
     * na větvích 'main' a 'master' přes GitHub REST API s Base64 dekódováním nebo download_url (pro soubory > 1 MB).
     */
    suspend fun restoreFromGitHub(
        context: Context,
        repoInput: String = getRepo(context),
        branchInput: String = getBranch(context),
        tokenInput: String = getToken(context),
        onStatus: (String) -> Unit = {}
    ): GitHubResult = withContext(Dispatchers.IO) {
        val repo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
        val branches = listOf(branchInput.trim().ifBlank { "main" }, "main", "master").distinct()
        val token = tokenInput.trim().ifBlank { DEFAULT_TOKEN }

        if (repo.isBlank()) {
            return@withContext GitHubResult.Error("Zadejte název repozitáře na GitHubu.")
        }

        val candidateFiles = listOf(
            "containers_sync.json",
            "data_backup.json",
            "backup.json",
            "initial_adresar.json",
            "DataBackup.zip",
            "index.html"
        )

        onStatus("Ověřuji spojení s repozitářem $repo na GitHubu...")
        val testRes = testConnection(context = context, repoInput = repo, tokenInput = token)
        if (testRes is GitHubResult.Error) {
            return@withContext GitHubResult.Error(
                "Nepodařilo se připojit k repozitáři na GitHubu:\n\n${testRes.message}"
            )
        }

        var restoredCount = 0

        for (br in branches) {
            for (filename in candidateFiles) {
                try {
                    onStatus("Hledám $filename na větvi '$br'...")
                    val fileBytes = fetchGitHubFileBytes(repo, br, filename, token)
                        ?: if (filename == "containers_sync.json" && !repo.equals(PUBLIC_PORTAL_REPO, ignoreCase = true)) {
                            fetchGitHubFileBytes(PUBLIC_PORTAL_REPO, br, filename, token)
                        } else null
                    if (fileBytes != null && fileBytes.isNotEmpty()) {
                        onStatus("Nalezen $filename (${fileBytes.size / 1024} KB). Zpracovávám data...")

                        if (filename.endsWith(".json")) {
                            val jsonStr = fileBytes.toString(Charsets.UTF_8)
                            HtmlDownloadManager.saveWebSyncSnapshotToMobile(context, jsonStr)

                            try {
                                val obj = JSONObject(jsonStr)
                                val cCount = if (obj.has("containers")) obj.getJSONArray("containers").length() else 0
                                val tCount = if (obj.has("transports")) obj.getJSONArray("transports").length() else 0
                                val custCount = if (obj.has("customers")) obj.getJSONArray("customers").length() else 0
                                restoredCount = cCount + tCount + custCount
                            } catch (_: Exception) {}

                            setLastSync(context)
                            return@withContext GitHubResult.Success(
                                message = "Záloha byla úspěšně stažena z GitHubu přes Wi-Fi!",
                                details = "Soubor: $filename ($br) • Položek v agendě: $restoredCount"
                            )
                        } else if (filename.endsWith(".zip")) {
                            val tempFile = File(context.cacheDir, "github_restore.zip")
                            FileOutputStream(tempFile).use { it.write(fileBytes) }
                            val bm = BackupManager(context)
                            bm.restoreDataBackup(tempFile)
                            setLastSync(context)
                            return@withContext GitHubResult.Success(
                                message = "Kompletní ZIP záloha byla stažena a obnovena z GitHubu!",
                                details = "Soubor: $filename ($br)"
                            )
                        } else if (filename == "index.html") {
                            val assetFile = File(context.filesDir, "github_index.html")
                            assetFile.writeBytes(fileBytes)
                            setLastSync(context)
                            return@withContext GitHubResult.Success(
                                message = "PC Terminál (index.html) byl úspěšně stažen z GitHubu přes Wi-Fi!",
                                details = "Velikost: ${fileBytes.size / 1024} KB ($br)"
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return@withContext GitHubResult.Error(
            "Repozitář '$repo' je úspěšně propojen, ale je zatím prázdný — neobsahuje ještě žádnou zálohu (containers_sync.json).\n\n" +
            "👉 Klikněte nejprve na tlačítko 'Uložit zálohu na GitHub' pro nahrání prvotní zálohy z mobilu (nebo z PC verze)."
        )
    }

    /**
     * Nahraje aktuální zálohu databáze z mobilu přímo na GitHub přes Wi-Fi.
     */
    suspend fun uploadToGitHub(
        context: Context,
        repoInput: String = getRepo(context),
        branchInput: String = getBranch(context),
        tokenInput: String = getToken(context),
        commitMessage: String = "Záloha z mobilní aplikace (přes Wi-Fi)",
        onStatus: (String) -> Unit = {}
    ): GitHubResult = withContext(Dispatchers.IO) {
        val repo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
        val branch = branchInput.trim().ifBlank { "main" }
        val token = tokenInput.trim().ifBlank { DEFAULT_TOKEN }

        if (token.isBlank()) {
            return@withContext GitHubResult.Error("Pro nahrání zálohy na GitHub je vyžadován GitHub Personal Access Token (PAT s oprávněním 'repo'). Zadejte jej prosím v nastavení níže.")
        }

        try {
            // Nejprve stáhneme případné novější záznamy z GitHubu a sloučíme je, abychom nic nepřepsali
            try {
                onStatus("Kontroluji a slučuji případné novější záznamy na GitHubu...")
                val remoteSyncBytes = fetchGitHubFileBytes(repo, branch, "containers_sync.json", token)
                if (remoteSyncBytes != null && remoteSyncBytes.isNotEmpty()) {
                    val remoteJsonStr = remoteSyncBytes.toString(Charsets.UTF_8)
                    if (remoteJsonStr.trimStart().startsWith("{")) {
                        HtmlDownloadManager.saveWebSyncSnapshotToMobile(context, remoteJsonStr)
                    }
                }
            } catch (_: Exception) {}

            onStatus("Sestavuji kompletní zálohu databáze (Kontejnery, Přepravy, Zákazníci, Zakázky, Faktury)...")
            val snapshotJson = HtmlDownloadManager.buildMobileSyncSnapshotJson(context)
            val jsonBytes = snapshotJson.toByteArray(Charsets.UTF_8)
            val base64Content = Base64.encodeToString(jsonBytes, Base64.NO_WRAP)

            val filePath = "containers_sync.json"
            onStatus("Zjišťuji existenci a SHA souboru $filePath na GitHubu...")

            val currentSha = getFileShaFromGitHub(repo, branch, filePath, token)

            onStatus("Odesílám novou zálohu na GitHub ($repo / $filePath)...")
            val putUrl = URI("https://api.github.com/repos/$repo/contents/$filePath").toURL()
            val conn = (putUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 20000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                setRequestProperty("Authorization", getAuthHeader(token))
            }

            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("content", base64Content)
                put("branch", branch)
                if (!currentSha.isNullOrBlank()) {
                    put("sha", currentSha)
                }
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode

            if (code in 200..299) {
                setLastSync(context)

                // Zrcadlíme containers_sync.json také do veřejného portálu Home_1 pro iPhone a Web
                if (!repo.equals(PUBLIC_PORTAL_REPO, ignoreCase = true)) {
                    try {
                        uploadRawBytes(PUBLIC_PORTAL_REPO, branch, filePath, jsonBytes, token, commitMessage)
                    } catch (_: Exception) {}
                }

                // Navýšíme verzi po provedených změnách a nahrajeme do 3 oddělených složek (pc/, android/, iphone/) + kořene
                val newVer = incrementAppVersionOnChange(context)
                try {
                    val htmlFile = File(context.filesDir, "index.html")
                    val hBytes = if (htmlFile.exists() && htmlFile.length() > 1000) {
                        htmlFile.readBytes()
                    } else {
                        com.example.ui.HtmlDownloadManager.readHtmlBytes(context)
                    }
                    if (hBytes.size > 1000) {
                        onStatus("Aktualizuji složky 1. WEB PC (pc/), 2. Android (android/) a 3. iPhone (iphone/) • v$newVer...")
                        uploadThreePlatformStructure(
                            context = context,
                            repo = repo,
                            branch = branch,
                            token = token,
                            version = newVer,
                            htmlBytes = hBytes
                        )
                        // Automaticky promazáváme starší verze na GitHubu – u každé ze 3 platforem udržujeme vždy pouze poslední 3 verze:
                        pruneOldGitHubVersions(context, repo, branch, token, maxKeep = 3)
                    }
                } catch (_: Exception) {}

                return@withContext GitHubResult.Success(
                    message = "Záloha i verze v$newVer nahrány na GitHub (1. PC, 2. Android, 3. iPhone – uloženy poslední 3 verze)!",
                    details = "Repozitář: $repo • Větev: $branch • Verze: v$newVer • Velikost: ${jsonBytes.size / 1024} KB"
                )
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                val helpfulMsg = when (code) {
                    404 -> "Chyba 404 (Repozitář nenalezen):\nRepozitář '$repo' nebyl nalezen, nebo váš Token nemá oprávnění k zápisu.\n\nZkontrolujte na GitHubu:\n1. Zda je repozitář Home_1 vytvořen (https://github.com/new).\n2. V nastavení tokenu (Repository access) přepněte na 'All repositories' a v oprávněních (Contents) povolte 'Read and write'."
                    401 -> "Chyba 401 (Neplatný Token):\nGitHub odmítl zadaný Token. Zkontrolujte jeho správnost a platnost."
                    403 -> "Chyba 403 (Přístup odepřen):\nToken nemá oprávnění pro zápis do repozitáře. V nastavení tokenu povolte 'Contents: Read and write'."
                    else -> "Chyba GitHub API (HTTP $code): $err"
                }
                return@withContext GitHubResult.Error(helpfulMsg)
            }
        } catch (e: Exception) {
            return@withContext GitHubResult.Error("Chyba při odesílání na GitHub: ${e.message}")
        }
    }

    /**
     * Nahraje soubory do 3 oddělených složek na GitHubu:
     * 1. pc/ (WEB PC Aplikace)
     * 2. android/ (Android verze)
     * 3. iphone/ (iPhone verze)
     * + zachová hlavní odkazy v kořenu repozitáře pro zpětnou kompatibilitu.
     */
    private fun uploadThreePlatformStructure(
        context: Context,
        repo: String,
        branch: String,
        token: String,
        version: Int,
        htmlBytes: ByteArray
    ) {
        val verStr = version.toString()
        val todayDot = SimpleDateFormat("d.M.yyyy", Locale.forLanguageTag("cs-CZ")).format(Date())
        val keptVers = getKeptVersionsList(version, 3)
        val keptVersJson = keptVers.joinToString(",") { "\"v$it\"" }

        val iphoneBytes = com.example.ui.HtmlDownloadManager.readIphoneHtmlText(context).toByteArray(Charsets.UTF_8)
        val mobileConfigBytes = com.example.ui.HtmlDownloadManager.buildIosMobileConfigXml(context = context).toByteArray(Charsets.UTF_8)
        val ipaBytes = com.example.ui.HtmlDownloadManager.buildIphoneIpaBytes(context)

        val pcVersionMeta = """{
  "platform": "WEB_PC_APLIKACE",
  "folder": "pc/",
  "currentVersion": $version,
  "versionBadge": "v $verStr",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJson]
}""".toByteArray(Charsets.UTF_8)

        val androidBuildMeta = """{
  "platform": "ANDROID_VERZE",
  "folder": "android/",
  "applicationId": "${context.packageName}",
  "versionCode": $version,
  "versionName": "$verStr.0",
  "releaseDate": "$todayDot",
  "apkPath": "android/Kontejnery_Ostrava_Android_v$verStr.apk",
  "releaseTag": "v$verStr"
}""".toByteArray(Charsets.UTF_8)

        val androidVersionMeta = """{
  "platform": "ANDROID_VERZE",
  "folder": "android/",
  "applicationId": "${context.packageName}",
  "currentVersion": $version,
  "versionCode": $version,
  "versionName": "$verStr.0",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJson],
  "apkDownloadUrl": "https://github.com/$repo/releases/download/v$verStr/Kontejnery_Ostrava_Android_v$verStr.apk"
}""".toByteArray(Charsets.UTF_8)

        val iphoneVersionMeta = """{
  "platform": "IPHONE_VERZE",
  "folder": "iphone/",
  "currentVersion": $version,
  "versionBadge": "v $verStr",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJson]
}""".toByteArray(Charsets.UTF_8)

        val rootVersionMeta = """{
  "system": "Kontejnery Ostrava • Stormware Pohoda ERP",
  "currentVersion": $version,
  "versionCode": $version,
  "versionName": "$verStr.0",
  "versionBadge": "v $verStr",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJson],
  "platforms": {
    "pc": { "folder": "pc/", "version": $version, "entryFile": "pc/index.html", "versionJson": "pc/version.json" },
    "android": { "folder": "android/", "version": $version, "versionCode": $version, "apkPath": "android/Kontejnery_Ostrava_Android_v$verStr.apk" },
    "iphone": { "folder": "iphone/", "version": $version, "entryFile": "iphone/iphone.html", "versionJson": "iphone/version.json" }
  }
}""".toByteArray(Charsets.UTF_8)

        val targetRepos = listOf(repo, PUBLIC_PORTAL_REPO).distinctBy { it.lowercase() }
        for (targetRepo in targetRepos) {
            // Kořenové odkazy (zpětná kompatibilita a centrální rozcestník)
            uploadRawBytes(targetRepo, branch, "version.json", rootVersionMeta, token, "release: metadata verze v$verStr (centrální version.json)")
            uploadRawBytes(targetRepo, branch, "index.html", htmlBytes, token, "release: verze v$verStr - hlavní rozcestník index.html")
            uploadRawBytes(targetRepo, branch, "S_POHODA_Kontejnery_v$verStr.html", htmlBytes, token, "release: verze v$verStr - samostatný HTML soubor")

            // 1. WEB PC APLIKACE (pc/)
            uploadRawBytes(targetRepo, branch, "pc/index.html", htmlBytes, token, "release(pc): 1. WEB PC Aplikace v$verStr (pc/index.html)")
            uploadRawBytes(targetRepo, branch, "pc/S_POHODA_Kontejnery_PC_v$verStr.html", htmlBytes, token, "release(pc): 1. WEB PC Aplikace archiv v$verStr")
            uploadRawBytes(targetRepo, branch, "pc/version.json", pcVersionMeta, token, "release(pc): metadata verze v$verStr")

            // 2. ANDROID VERZE (android/)
            uploadRawBytes(targetRepo, branch, "android/Kontejnery_Ostrava_Android_v$verStr.json", androidBuildMeta, token, "release(android): 2. Android verze v$verStr manifest")
            uploadRawBytes(targetRepo, branch, "android/version.json", androidVersionMeta, token, "release(android): metadata verze v$verStr")

            // 3. IPHONE VERZE (iphone/)
            uploadRawBytes(targetRepo, branch, "iphone.html", iphoneBytes, token, "release: iPhone verze v$verStr (kořenový odkaz iphone.html)")
            uploadRawBytes(targetRepo, branch, "Kontejnery_iPhone.mobileconfig", mobileConfigBytes, token, "release: iOS profil v$verStr (kořenový odkaz)")
            uploadRawBytes(targetRepo, branch, "Kontejnery_Preprava_iPhone.ipa", ipaBytes, token, "release: iOS IPA v$verStr (kořenový odkaz)")

            uploadRawBytes(targetRepo, branch, "iphone/iphone.html", iphoneBytes, token, "release(iphone): 3. iPhone verze v$verStr (iphone/iphone.html)")
            uploadRawBytes(targetRepo, branch, "iphone/index.html", iphoneBytes, token, "release(iphone): 3. iPhone verze v$verStr (iphone/index.html)")
            uploadRawBytes(targetRepo, branch, "iphone/Kontejnery_iPhone_v$verStr.html", iphoneBytes, token, "release(iphone): 3. iPhone verze archiv v$verStr HTML")
            uploadRawBytes(targetRepo, branch, "iphone/Kontejnery_iPhone_v$verStr.mobileconfig", mobileConfigBytes, token, "release(iphone): 3. iPhone verze archiv v$verStr mobileconfig")
            uploadRawBytes(targetRepo, branch, "iphone/Kontejnery_Preprava_iPhone_v$verStr.ipa", ipaBytes, token, "release(iphone): 3. iPhone verze archiv v$verStr IPA")
            uploadRawBytes(targetRepo, branch, "iphone/version.json", iphoneVersionMeta, token, "release(iphone): metadata verze v$verStr")
        }
    }

    /**
     * Udržuje na GitHubu i v lokálním úložišti vždy pouze poslední 3 verze programu pro každou ze 3 platforem:
     * 1. WEB PC Aplikace (pc/S_POHODA_Kontejnery_PC_v*.html a kořenové S_POHODA_Kontejnery_v*.html)
     * 2. Android verze (android/Kontejnery_Ostrava_Android_v*.json / .apk + GitHub Releases)
     * 3. iPhone verze (iphone/Kontejnery_iPhone_v*.html / .mobileconfig / Kontejnery_Preprava_iPhone_v*.ipa)
     * Všechny starší verze nad limit 3 automaticky maže přes GitHub REST API DELETE endpoint.
     */
    suspend fun pruneOldGitHubVersions(
        context: Context,
        repoInput: String = getRepo(context),
        branchInput: String = getBranch(context),
        tokenInput: String = getToken(context),
        maxKeep: Int = 3
    ): Int = withContext(Dispatchers.IO) {
        val repo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
        val branch = branchInput.trim().ifBlank { "main" }
        val token = tokenInput.trim().ifBlank { DEFAULT_TOKEN }

        // Nejprve pročistíme lokální filesDir na poslední 3 verze
        try {
            val localRegex = Regex("""S_POHODA_Kontejnery_v(\d+)\.html""")
            val localFiles = context.filesDir.listFiles { f -> f.isFile && localRegex.matches(f.name) }
                ?.sortedByDescending { f -> localRegex.find(f.name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0 }
                ?: emptyList()
            localFiles.drop(maxKeep).forEach { it.delete() }
        } catch (_: Exception) {}

        if (token.isBlank() || repo.isBlank()) return@withContext 0

        var deletedCount = 0

        fun pruneFolderPattern(targetRepo: String, folderPath: String, pattern: Regex) {
            try {
                val apiPath = if (folderPath.isBlank()) "contents?ref=$branch" else "contents/$folderPath?ref=$branch"
                val url = URI("https://api.github.com/repos/$targetRepo/$apiPath").toURL()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Kontejnery-Android-App")
                    setRequestProperty("Authorization", getAuthHeader(token))
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(body)
                    data class FileItem(val path: String, val sha: String, val ver: Double)
                    val versionFiles = mutableListOf<FileItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val path = obj.optString("path", if (folderPath.isBlank()) name else "$folderPath/$name")
                        val sha = obj.optString("sha", "")
                        val m = pattern.find(name)
                        if (m != null) {
                            val verVal = m.groupValues[1].toDoubleOrNull() ?: 0.0
                            versionFiles.add(FileItem(path, sha, verVal))
                        }
                    }
                    versionFiles.sortByDescending { it.ver }
                    if (versionFiles.size > maxKeep) {
                        val toDelete = versionFiles.drop(maxKeep)
                        for (file in toDelete) {
                            try {
                                val delUrl = URI("https://api.github.com/repos/$targetRepo/contents/${file.path}").toURL()
                                val delConn = (delUrl.openConnection() as HttpURLConnection).apply {
                                    requestMethod = "DELETE"
                                    connectTimeout = 8000
                                    readTimeout = 10000
                                    doOutput = true
                                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                    setRequestProperty("User-Agent", "Kontejnery-Android-App")
                                    setRequestProperty("Authorization", getAuthHeader(token))
                                }
                                val payload = JSONObject().apply {
                                    put("message", "cleanup: delete old version ${file.path} (keep latest $maxKeep)")
                                    put("sha", file.sha)
                                    put("branch", branch)
                                }
                                delConn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                if (delConn.responseCode in 200..299) {
                                    deletedCount++
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        fun pruneReleases(targetRepo: String) {
            try {
                val url = URI("https://api.github.com/repos/$targetRepo/releases?per_page=30").toURL()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Kontejnery-Android-App")
                    setRequestProperty("Authorization", getAuthHeader(token))
                }
                if (conn.responseCode == 200) {
                    val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                    if (arr.length() > maxKeep) {
                        for (i in maxKeep until arr.length()) {
                            val rel = arr.getJSONObject(i)
                            val relId = rel.optLong("id", 0L)
                            if (relId > 0L) {
                                try {
                                    val delConn = (URI("https://api.github.com/repos/$targetRepo/releases/$relId").toURL().openConnection() as HttpURLConnection).apply {
                                        requestMethod = "DELETE"
                                        connectTimeout = 8000
                                        readTimeout = 10000
                                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                                        setRequestProperty("User-Agent", "Kontejnery-Android-App")
                                        setRequestProperty("Authorization", getAuthHeader(token))
                                    }
                                    if (delConn.responseCode in 200..299) deletedCount++
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val targetRepos = listOf(repo, PUBLIC_PORTAL_REPO).distinctBy { it.lowercase() }
        for (targetRepo in targetRepos) {
            // Kořen repozitáře
            pruneFolderPattern(targetRepo, "", Regex("""S_POHODA_Kontejnery_v(\d+(\.\d+)?)\.html"""))
            // 1. WEB PC Aplikace (pc/)
            pruneFolderPattern(targetRepo, "pc", Regex("""S_POHODA_Kontejnery_PC_v(\d+(\.\d+)?)\.html"""))
            // 2. Android verze (android/)
            pruneFolderPattern(targetRepo, "android", Regex("""Kontejnery_Ostrava_Android_v(\d+(\.\d+)?)\.json"""))
            pruneFolderPattern(targetRepo, "android", Regex("""Kontejnery_Ostrava_Android_v(\d+(\.\d+)?)\.apk"""))
            pruneReleases(targetRepo)
            // 3. iPhone verze (iphone/)
            pruneFolderPattern(targetRepo, "iphone", Regex("""Kontejnery_iPhone_v(\d+(\.\d+)?)\.html"""))
            pruneFolderPattern(targetRepo, "iphone", Regex("""Kontejnery_iPhone_v(\d+(\.\d+)?)\.mobileconfig"""))
            pruneFolderPattern(targetRepo, "iphone", Regex("""Kontejnery_Preprava_iPhone_v(\d+(\.\d+)?)\.ipa"""))
        }

        deletedCount
    }

    private fun uploadRawBytes(repo: String, branch: String, path: String, bytes: ByteArray, token: String, message: String): Boolean {
        return try {
            val sha = getFileShaFromGitHub(repo, branch, path, token)
            val putUrl = URI("https://api.github.com/repos/$repo/contents/$path").toURL()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val conn = (putUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 20000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                if (token.isNotBlank()) setRequestProperty("Authorization", getAuthHeader(token))
            }
            val payload = JSONObject().apply {
                put("message", message)
                put("content", base64)
                put("branch", branch)
                if (!sha.isNullOrBlank()) put("sha", sha)
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Spolehlivě stáhne soubor z GitHubu (včetně velkých souborů nad 1 MB jako containers_sync.json nebo index.html):
     * 1. Primárně přes GitHub REST API /contents/path s hlavičkou Accept: application/vnd.github.v3+json.
     *    Pokud API vrátí neprázdný Base64 obsah (content), dekóduje jej.
     * 2. Pokud je soubor větší než 1 MB (GitHub vrátí content: "" a encoding: "none"), stáhne jej přes download_url
     *    nebo přes Git Blobs API (/git/blobs/{sha}).
     * 3. Jako fallback použije raw.githubusercontent.com.
     */
    private fun fetchGitHubFileBytes(repo: String, branch: String, path: String, token: String?): ByteArray? {
        val t = token?.trim()
        val candidateRepos = listOf(repo, DEFAULT_REPO, FALLBACK_PORTAL_REPO).filter { it.isNotBlank() }.distinctBy { it.lowercase() }

        // 1. Zkusíme raw.githubusercontent.com BEZ hlavičky Authorization (rychlé, veřejné, imunní vůči neplatným tokenům)
        for (candidateRepo in candidateRepos) {
            try {
                val rawUrlStr = "https://raw.githubusercontent.com/$candidateRepo/$branch/$path"
                val url = URI(rawUrlStr).toURL()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                }
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {}
        }

        // 2. Pokud neautorizované stahování selhalo (např. soukromý repozitář) a je zadaný token, zkusíme s Authorization
        if (!t.isNullOrBlank()) {
            for (candidateRepo in candidateRepos) {
                try {
                    val rawUrlStr = "https://raw.githubusercontent.com/$candidateRepo/$branch/$path"
                    val url = URI(rawUrlStr).toURL()
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 20000
                        setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                        setRequestProperty("Authorization", getAuthHeader(t))
                    }
                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) return bytes
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Fallback přes GitHub REST API /contents/
        for (candidateRepo in candidateRepos) {
            val apiBytes = fetchViaContentsApi(candidateRepo, branch, path, t ?: "")
            if (apiBytes != null && apiBytes.isNotEmpty()) return apiBytes
        }

        return null
    }

    private fun fetchViaContentsApi(repo: String, branch: String, path: String, token: String): ByteArray? {
        return try {
            val apiUrl = URI("https://api.github.com/repos/$repo/contents/$path?ref=$branch").toURL()
            val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 25000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", getAuthHeader(token))
                }
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                if (json.has("content")) {
                    val rawBase64 = json.optString("content", "")
                    val cleanBase64 = rawBase64.replace("\n", "").replace("\r", "").trim()
                    if (cleanBase64.isNotEmpty() && cleanBase64 != "null") {
                        val decoded = Base64.decode(cleanBase64, Base64.DEFAULT)
                        if (decoded.isNotEmpty()) return decoded
                    }
                }

                // Pokud je soubor větší než 1 MB (GitHub vrací content="" a download_url)
                val downloadUrl = json.optString("download_url", "").trim()
                if (downloadUrl.isNotBlank() && downloadUrl != "null") {
                    try {
                        val dConn = (URI(downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 15000
                            readTimeout = 45000
                            setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                        }
                        if (dConn.responseCode == 200) {
                            val bytes = dConn.inputStream.use { it.readBytes() }
                            if (bytes.isNotEmpty()) return bytes
                        }
                    } catch (_: Exception) {}
                }

                // Fallback přes Git Blobs API (/git/blobs/{sha}) s hlavičkou application/vnd.github.v3.raw
                val sha = json.optString("sha", "").trim()
                if (sha.isNotBlank() && sha != "null") {
                    try {
                        val blobUrl = URI("https://api.github.com/repos/$repo/git/blobs/$sha").toURL()
                        val bConn = (blobUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 15000
                            readTimeout = 45000
                            setRequestProperty("Accept", "application/vnd.github.v3.raw")
                            setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                            if (token.isNotBlank()) {
                                setRequestProperty("Authorization", getAuthHeader(token))
                            }
                        }
                        if (bConn.responseCode == 200) {
                            val bytes = bConn.inputStream.use { it.readBytes() }
                            if (bytes.isNotEmpty()) return bytes
                        }
                    } catch (_: Exception) {}
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getFileShaFromGitHub(repo: String, branch: String, path: String, token: String): String? {
        return try {
            val apiUrl = URI("https://api.github.com/repos/$repo/contents/$path?ref=$branch").toURL()
            val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Kontejnery-Ostrava-Android")
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", getAuthHeader(token))
                }
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                if (json.has("sha")) json.getString("sha") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    data class SoftwareUpdateDetails(
        val success: Boolean,
        val version: String,
        val date: String,
        val commitSha: String,
        val message: String,
        val details: String = "",
        val apkFile: File? = null,
        val apkDownloaded: Boolean = false
    )

    private val _highestVersionFlow = kotlinx.coroutines.flow.MutableStateFlow(com.example.ui.APP_VERSION_NUMBER)
    val highestVersionFlow: kotlinx.coroutines.flow.StateFlow<Int> = _highestVersionFlow

    private val _highestDateFlow = kotlinx.coroutines.flow.MutableStateFlow(com.example.ui.APP_LAST_UPDATE_DATE)
    val highestDateFlow: kotlinx.coroutines.flow.StateFlow<String> = _highestDateFlow

    private fun extractVersionFromHtml(htmlStr: String): Int {
        val m = Regex("""const CURRENT_SW_VERSION\s*=\s*(\d+)""").find(htmlStr)
            ?: Regex("""VERZE PROGRAMU:\s*(\d+)""").find(htmlStr)
            ?: Regex("""VERZE\s*(\d+)""").find(htmlStr)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    fun getLastUpdateVersion(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString("last_update_version", null)
        val storedNum = stored?.toIntOrNull() ?: 0
        var maxVer = maxOf(
            com.example.ui.APP_VERSION_NUMBER,
            com.example.ui.AppUpdateManager.LATEST_CLOUD_VERSION_CODE,
            _highestVersionFlow.value,
            storedNum
        )
        try {
            val ghFile = File(context.filesDir, "github_index.html")
            if (ghFile.exists() && ghFile.length() > 1000) {
                val headText = ghFile.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    val buf = CharArray(120_000)
                    val read = reader.read(buf)
                    if (read > 0) String(buf, 0, read) else ""
                }
                maxVer = maxOf(maxVer, extractVersionFromHtml(headText))
            }
        } catch (_: Exception) {}
        try {
            context.filesDir.listFiles()?.forEach { f ->
                val m = Regex("""S_POHODA_Kontejnery_v(\d+)\.html""").matchEntire(f.name)
                val v = m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                if (v > maxVer) maxVer = v
            }
        } catch (_: Exception) {}
        if (maxVer > storedNum) {
            prefs.edit().putString("last_update_version", maxVer.toString()).apply()
        }
        if (maxVer > _highestVersionFlow.value) {
            _highestVersionFlow.value = maxVer
        }
        return maxVer.toString()
    }

    fun getLastUpdateDate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVer = prefs.getString("last_update_version", null)?.toIntOrNull() ?: 0
        val defaultDate = com.example.ui.APP_LAST_UPDATE_DATE
        val resolved = if (storedVer < com.example.ui.APP_VERSION_NUMBER) {
            defaultDate
        } else {
            prefs.getString("last_update_date", defaultDate)?.ifBlank { defaultDate } ?: defaultDate
        }
        _highestDateFlow.value = resolved
        return resolved
    }

    fun getLastUpdateTimestamp(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("last_update_timestamp", System.currentTimeMillis())
    }

    fun getKeptVersionsList(currentVer: Int, maxKeep: Int = 3): List<Int> {
        return (0 until maxKeep).map { currentVer - it }.filter { it > 0 }
    }

    /**
     * Automaticky navýší verzi aplikace o +1 při provedení a publikaci změn.
     */
    fun incrementAppVersionOnChange(context: Context): Int {
        val currentHighest = getLastUpdateVersion(context).toIntOrNull() ?: com.example.ui.APP_VERSION_NUMBER
        val nextVer = maxOf(currentHighest, com.example.ui.APP_VERSION_NUMBER) + 1
        val todayDot = SimpleDateFormat("d.M.yyyy", Locale.forLanguageTag("cs-CZ")).format(Date())
        saveLastUpdateInfo(context, nextVer.toString(), todayDot, "v$nextVer")
        return nextVer
    }

    fun saveLastUpdateInfo(context: Context, version: String, date: String, sha: String) {
        val currentHighest = getLastUpdateVersion(context).toIntOrNull() ?: com.example.ui.APP_VERSION_NUMBER
        val highestVer = maxOf(version.toIntOrNull() ?: 0, currentHighest, com.example.ui.APP_VERSION_NUMBER)
        val finalVerStr = highestVer.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("last_update_version", finalVerStr)
            .putString("last_update_date", date)
            .putString("last_commit_sha", sha)
            .putLong("last_update_timestamp", System.currentTimeMillis())
            .apply()
        _highestVersionFlow.value = highestVer
        _highestDateFlow.value = date
    }

    /**
     * Zkontroluje při spuštění aplikace, zda je na GitHubu k dispozici novější verze než lokálně nainstalovaná APK,
     * a pokud ano, automaticky ji stáhne a nainstaluje.
     */
    suspend fun checkIfNewerVersionOnGitHub(
        context: Context,
        repoInput: String = getRepo(context),
        branchInput: String = getBranch(context),
        tokenInput: String = getToken(context)
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        try {
            val primaryRepo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
            val branch = branchInput.trim().ifBlank { "main" }
            val token = tokenInput.trim().ifBlank { DEFAULT_TOKEN }
            val installedApkVer = com.example.ui.APP_VERSION_NUMBER
            val storedVer = getLastUpdateVersion(context).toIntOrNull() ?: installedApkVer

            val reposToTry = listOf(primaryRepo, DEFAULT_REPO, FALLBACK_PORTAL_REPO)
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }

            var remoteVer = 0

            // 1. Prohledáme metadata version.json ve všech složkách (android/, kořen, pc/, iphone/)
            for (r in reposToTry) {
                for (verPath in listOf("android/version.json", "version.json", "pc/version.json", "iphone/version.json")) {
                    val vBytes = fetchGitHubFileBytes(r, branch, verPath, token)
                    if (vBytes != null && vBytes.isNotEmpty()) {
                        try {
                            val vJson = JSONObject(String(vBytes, Charsets.UTF_8))
                            val v = maxOf(
                                vJson.optInt("currentVersion", 0),
                                vJson.optInt("versionCode", 0),
                                vJson.optInt("version", 0)
                            )
                            if (v > remoteVer) remoteVer = v
                        } catch (_: Exception) {}
                    }
                }
            }

            // 2. Kontrola přes GitHub Releases (/releases/latest a /releases)
            for (r in reposToTry) {
                try {
                    val relUrl = URI("https://api.github.com/repos/$r/releases/latest").toURL()
                    val conn = (relUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 8000
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                        setRequestProperty("User-Agent", "Kontejnery-Android-App")
                        if (token.isNotBlank()) setRequestProperty("Authorization", getAuthHeader(token))
                    }
                    if (conn.responseCode == 200) {
                        val relJson = JSONObject(conn.inputStream.bufferedReader().readText())
                        val tag = relJson.optString("tag_name", "").removePrefix("v").trim()
                        val v = tag.toIntOrNull() ?: 0
                        if (v > remoteVer) remoteVer = v
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback přes pc/index.html, index.html nebo iphone/iphone.html
            for (r in reposToTry) {
                for (htmlPath in listOf("pc/index.html", "index.html", "iphone/iphone.html", "iphone.html")) {
                    val htmlBytes = fetchGitHubFileBytes(r, branch, htmlPath, token)
                    if (htmlBytes != null && htmlBytes.isNotEmpty()) {
                        val htmlStr = String(htmlBytes, Charsets.UTF_8)
                        val v = extractVersionFromHtml(htmlStr)
                        if (v > remoteVer) remoteVer = v
                    }
                }
            }

            if (remoteVer > _highestVersionFlow.value) {
                _highestVersionFlow.value = remoteVer
            }
            // Porovnáváme proti skutečně nainstalované APK verzi (installedApkVer) i uložené verzi
            val needsUpdate = remoteVer > installedApkVer || remoteVer > storedVer
            Pair(needsUpdate, remoteVer)
        } catch (_: Exception) {
            Pair(false, 0)
        }
    }

    /**
     * Automaticky odešle novou lokální verzi programu do 3 oddělených složek (pc/, android/, iphone/)
     * v synchronizovaném GitHub repozitáři vždy, když je lokální verze vyšší než verze na GitHubu
     * nebo když složka pc/ ještě neobsahuje aktuální verzi.
     */
    suspend fun pushCurrentAppHtmlToGitHubIfNewer(
        context: Context,
        repoInput: String = getRepo(context),
        branchInput: String = getBranch(context),
        tokenInput: String = getToken(context)
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
            val branch = branchInput.trim().ifBlank { "main" }
            val token = tokenInput.trim().ifBlank { DEFAULT_TOKEN }
            if (repo.isBlank() || token.isBlank()) return@withContext false

            val currentVer = maxOf(
                com.example.ui.APP_VERSION_NUMBER,
                getLastUpdateVersion(context).toIntOrNull() ?: com.example.ui.APP_VERSION_NUMBER
            )
            val (_, remoteVer) = checkIfNewerVersionOnGitHub(context, repo, branch, token)
            val pcVersionedPath = "pc/S_POHODA_Kontejnery_PC_v$currentVer.html"
            val existingPcSha = getFileShaFromGitHub(repo, branch, pcVersionedPath, token)

            if (currentVer > remoteVer || existingPcSha.isNullOrBlank()) {
                val htmlBytes = try {
                    context.assets.open("index.html").use { it.readBytes() }
                } catch (_: Exception) {
                    val localFile = File(context.filesDir, "index.html")
                    if (localFile.exists()) localFile.readBytes() else null
                } ?: return@withContext false

                uploadThreePlatformStructure(
                    context = context,
                    repo = repo,
                    branch = branch,
                    token = token,
                    version = currentVer,
                    htmlBytes = htmlBytes
                )
                pruneOldGitHubVersions(context, repo, branch, token, maxKeep = 3)
                return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun checkAndAutoUpdateOnStartup(
        context: Context,
        onUpdated: () -> Unit = {}
    ) {
        // Při startu nejprve tiše sesynchronizujeme data (containers_sync.json) z GitHubu
        try {
            syncDataOnlyWithGitHub(context, pullFirst = true, pushAfter = false)
        } catch (_: Exception) {}

        val (hasNewer, remoteVer) = checkIfNewerVersionOnGitHub(context)
        if (hasNewer) {
            val (ok, _) = performSoftwareUpdateFromGitHub(
                context = context,
                onProgress = { _, _ -> }
            )
            if (ok) {
                withContext(Dispatchers.Main) {
                    onUpdated()
                }
            }
        } else if (com.example.ui.APP_VERSION_NUMBER >= remoteVer) {
            pushCurrentAppHtmlToGitHubIfNewer(context)
        }
    }

    /**
     * Stáhne a zaktualizuje kompletní aplikaci z GitHubu (containers_sync.json, pc/index.html a Android .apk).
     * Během stahování reportuje procenta (0..100) a pokud je na GitHubu novější APK než nainstalovaná verze,
     * automaticky spustí systémový instalátor Androidu pro aktualizaci aplikace přes Wi-Fi.
     */
    suspend fun performSoftwareUpdateFromGitHub(
        context: Context,
        repoInput: String = getRepo(context),
        branchInput: String = getBranch(context),
        tokenInput: String = getToken(context),
        onProgress: (Int, String) -> Unit
    ): Pair<Boolean, SoftwareUpdateDetails> = withContext(Dispatchers.IO) {
        val repo = repoInput.trim().removePrefix("https://github.com/").trimEnd('/')
        val branch = branchInput.trim().ifBlank { "main" }
        val token = tokenInput.trim().ifBlank { DEFAULT_TOKEN }
        val reposToTry = listOf(repo, DEFAULT_REPO, FALLBACK_PORTAL_REPO).filter { it.isNotBlank() }.distinctBy { it.lowercase() }

        onProgress(8, "Navazuji spojení s GitHubem ($repo)...")
        kotlinx.coroutines.delay(200)

        // 1. Zjistíme informace o posledním commitu a verzi z GitHubu
        var latestSha = com.example.ui.APP_VERSION_NAME
        var latestDate = SimpleDateFormat("dd. MM. yyyy", Locale.forLanguageTag("cs-CZ")).format(Date())
        var commitMsg = "v ${com.example.ui.APP_VERSION_NAME}"
        var detectedVersion = com.example.ui.APP_VERSION_NAME

        try {
            var highestFound = detectedVersion.toIntOrNull() ?: 0
            for (r in reposToTry) {
                for (vPath in listOf("android/version.json", "version.json", "pc/version.json", "iphone/version.json")) {
                    val verBytes = fetchGitHubFileBytes(r, branch, vPath, token)
                    if (verBytes != null && verBytes.isNotEmpty()) {
                        val vJson = JSONObject(String(verBytes, Charsets.UTF_8))
                        val vNum = maxOf(
                            vJson.optInt("currentVersion", 0),
                            vJson.optInt("versionCode", 0),
                            vJson.optInt("version", 0)
                        )
                        if (vNum > highestFound) {
                            highestFound = vNum
                            detectedVersion = vNum.toString()
                        }
                        val rDate = vJson.optString("releaseDate", "").trim()
                        if (rDate.isNotBlank()) latestDate = rDate
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val commitUrl = URI("https://api.github.com/repos/$repo/commits?per_page=1&sha=$branch").toURL()
            val conn = (commitUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Kontejnery-Android-App")
                if (token.isNotBlank()) setRequestProperty("Authorization", getAuthHeader(token))
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    latestSha = obj.optString("sha", "").take(8)
                    val commitObj = obj.optJSONObject("commit")
                    val defaultMsg = "Aktualizace na verzi $detectedVersion"
                    commitMsg = commitObj?.optString("message", defaultMsg) ?: defaultMsg
                    val committer = commitObj?.optJSONObject("committer") ?: commitObj?.optJSONObject("author")
                    val rawDate = committer?.optString("date", "") ?: ""
                    if (rawDate.isNotBlank()) {
                        try {
                            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }
                            val parsed = inFmt.parse(rawDate)
                            if (parsed != null) {
                                latestDate = SimpleDateFormat("dd. MM. yyyy", Locale.forLanguageTag("cs-CZ")).format(parsed)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        onProgress(22, "Ověřeno: Repozitář $repo • Revize $latestSha ($latestDate)")
        kotlinx.coroutines.delay(200)

        // 2. Stažení a sloučení databáze containers_sync.json z GitHubu
        onProgress(40, "Stahuji a synchronizuji databázi containers_sync.json z GitHubu...")
        var syncBytes: ByteArray? = null
        for (r in reposToTry) {
            syncBytes = fetchGitHubFileBytes(r, branch, "containers_sync.json", token)
            if (syncBytes != null && syncBytes.isNotEmpty()) break
        }
        if (syncBytes != null && syncBytes.isNotEmpty()) {
            try {
                val jsonStr = syncBytes.toString(Charsets.UTF_8)
                if (jsonStr.trimStart().startsWith("{") || jsonStr.trimStart().startsWith("[")) {
                    HtmlDownloadManager.saveWebSyncSnapshotToMobile(context, jsonStr)
                }
            } catch (_: Exception) {}
        }
        kotlinx.coroutines.delay(200)

        // 3. Stažení jádra webové aplikace a PC/iPhone Terminálu (pc/index.html, index.html nebo iphone/iphone.html)
        onProgress(60, "Stahuji aplikaci z GitHubu (pc/index.html)...")
        var htmlBytes: ByteArray? = null
        for (r in reposToTry) {
            for (hPath in listOf("pc/index.html", "index.html", "iphone/iphone.html", "iphone.html", "pc/S_POHODA_Kontejnery_PC_v$detectedVersion.html")) {
                htmlBytes = fetchGitHubFileBytes(r, branch, hPath, token)
                if (htmlBytes != null && htmlBytes.isNotEmpty()) break
            }
            if (htmlBytes != null && htmlBytes.isNotEmpty()) break
        }

        if (htmlBytes != null && htmlBytes.isNotEmpty()) {
            try {
                val htmlStr = String(htmlBytes, Charsets.UTF_8)
                val verMatch = Regex("""const CURRENT_SW_VERSION\s*=\s*(\d+)""").find(htmlStr)
                    ?: Regex("""VERZE PROGRAMU:\s*(\d+)""").find(htmlStr)
                    ?: Regex("""PC Terminál v(\d+)""").find(htmlStr)
                    ?: Regex("""VERZE\s*(\d+)""").find(htmlStr)
                if (verMatch != null) {
                    val vFromHtml = verMatch.groupValues[1].toIntOrNull() ?: 0
                    if (vFromHtml >= (detectedVersion.toIntOrNull() ?: 0)) {
                        detectedVersion = vFromHtml.toString()
                    }
                }
                val filesDir = context.filesDir
                File(filesDir, "index.html").writeBytes(htmlBytes)
                File(filesDir, "github_index.html").writeBytes(htmlBytes)
                File(filesDir, "S_POHODA_Kontejnery_v${detectedVersion}.html").writeBytes(htmlBytes)
                // Udržujeme lokálně pouze poslední 3 verze
                val localRegex = Regex("""S_POHODA_Kontejnery_v(\d+)\.html""")
                val localFiles = filesDir.listFiles { f -> f.isFile && localRegex.matches(f.name) }
                    ?.sortedByDescending { f -> localRegex.find(f.name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0 }
                    ?: emptyList()
                localFiles.drop(3).forEach { it.delete() }
            } catch (_: Exception) {}
        } else {
            val commitVerMatch = Regex("""(?:verze|v|version)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(commitMsg)
            if (commitVerMatch != null) {
                detectedVersion = commitVerMatch.groupValues[1]
            }
        }

        // 4. Stažení a instalace nativní Android APK aktualizace přes Wi-Fi (pokud je na GitHubu novější APK)
        val detectedVerInt = detectedVersion.toIntOrNull() ?: com.example.ui.APP_VERSION_NUMBER
        var downloadedApkFile: File? = null
        var apkReady = false
        val apkTargetFile = File(context.cacheDir, "Kontejnery_Ostrava_Android_v${detectedVersion}.apk")

        if (detectedVerInt > com.example.ui.APP_VERSION_NUMBER || !com.example.ui.AppUpdateManager.isValidApkFile(apkTargetFile)) {
            onProgress(75, "Stahuji novou Android aplikaci (v $detectedVersion .APK) z GitHubu přes Wi-Fi...")
            val apkOk = com.example.ui.AppUpdateManager.tryDownloadFromGitHub(
                context = context,
                targetFile = apkTargetFile,
                versionName = detectedVersion,
                onProgress = { apkPct, apkStatus ->
                    val mappedPct = 75 + ((apkPct.coerceIn(0, 100) * 18) / 100)
                    onProgress(mappedPct, "Stahuji APK v $detectedVersion: $apkStatus")
                }
            )
            if (apkOk && com.example.ui.AppUpdateManager.isValidApkFile(apkTargetFile)) {
                downloadedApkFile = apkTargetFile
                apkReady = true
            }
        }

        // 5. Verifikace, uložení a pročištění starších verzí na GitHubu (ponechat poslední 3 u každé platformy)
        onProgress(95, "Dokončuji instalaci v $detectedVersion (uchovány poslední 3 verze)...")
        pruneOldGitHubVersions(context, repo, branch, token, maxKeep = 3)
        kotlinx.coroutines.delay(150)

        val version = detectedVersion
        saveLastUpdateInfo(context, version, latestDate, latestSha)
        setLastSync(context)

        onProgress(100, "Stažená verze: v $version • ze dne $latestDate")
        kotlinx.coroutines.delay(150)

        if (apkReady && downloadedApkFile != null && detectedVerInt > com.example.ui.APP_VERSION_NUMBER) {
            withContext(Dispatchers.Main) {
                com.example.ui.AppUpdateManager.installApk(context, downloadedApkFile)
            }
        }

        Pair(
            true,
            SoftwareUpdateDetails(
                success = true,
                version = version,
                date = latestDate,
                commitSha = latestSha,
                message = commitMsg,
                details = "Stažená verze: v $version • ze dne $latestDate",
                apkFile = downloadedApkFile,
                apkDownloaded = apkReady
            )
        )
    }
}

