package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.PhoneIphone
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object HtmlDownloadManager {

    private fun readSyncFileSafe(filesDir: File, fileName: String, maxBytes: Long): String? {
        return try {
            val file = File(filesDir, fileName)
            if (!file.exists() || file.length() <= 0L || file.length() > maxBytes) {
                null
            } else {
                file.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun buildMobileSyncSnapshotJson(context: Context, maxFileBytes: Long = 25_000_000L): String {
        return try {
            val filesDir = context.filesDir
            val containersJson = readSyncFileSafe(filesDir, "containers_sync.json", maxFileBytes)
                ?: readSyncFileSafe(filesDir, "sync_containers_cache.json", maxFileBytes)
                ?: "[]"
            val transportsJson = readSyncFileSafe(filesDir, "transports_sync.json", maxFileBytes) ?: "[]"
            val customersJson = readSyncFileSafe(filesDir, "customers_sync.json", maxFileBytes) ?: "[]"
            val nabidkyJson = readSyncFileSafe(filesDir, "nabidky_sync.json", maxFileBytes) ?: "[]"
            val zakazkyJson = readSyncFileSafe(filesDir, "zakazky_sync.json", maxFileBytes) ?: "[]"
            val fakturyJson = readSyncFileSafe(filesDir, "faktury_sync.json", maxFileBytes) ?: "[]"
            val zalohoveFakturyJson = readSyncFileSafe(filesDir, "zalohove_faktury_sync.json", maxFileBytes) ?: "[]"
            val zajemciJson = readSyncFileSafe(filesDir, "zajemci_sync.json", maxFileBytes) ?: "[]"
            val polozkyJson = readSyncFileSafe(filesDir, "polozky_catalog.json", maxFileBytes) ?: "[]"
            val inventoriesJson = readSyncFileSafe(filesDir, "inventories_sync.json", maxFileBytes) ?: "[]"
            val deletedIdsJson = readSyncFileSafe(filesDir, "deleted_sync_ids.json", maxFileBytes) ?: "[]"
            """{"containers":$containersJson,"transports":$transportsJson,"customers":$customersJson,"nabidky":$nabidkyJson,"zakazky":$zakazkyJson,"faktury":$fakturyJson,"zalohoveFaktury":$zalohoveFakturyJson,"zajemci":$zajemciJson,"polozky":$polozkyJson,"catalogItems":$polozkyJson,"inventories":$inventoriesJson,"deletedIds":$deletedIdsJson}"""
        } catch (_: Throwable) {
            """{"containers":[],"transports":[],"customers":[],"nabidky":[],"zakazky":[],"faktury":[],"zalohoveFaktury":[],"zajemci":[],"polozky":[],"catalogItems":[],"inventories":[],"deletedIds":[]}"""
        }
    }

    private fun normalizeTransportJsonObj(o: org.json.JSONObject): org.json.JSONObject {
        try {
            val dt = o.optString("dateText", "").ifBlank {
                o.optString("date", "").ifBlank { o.optString("transportDate", "") }
            }
            if (dt.isNotBlank()) {
                o.put("dateText", dt)
                o.put("date", dt)
                if (o.optString("transportDate", "").isBlank()) o.put("transportDate", dt)
            } else if (!o.has("dateText") || o.isNull("dateText")) {
                o.put("dateText", "")
            }
            val intNote = o.optString("internalNote", "").ifBlank { o.optString("interniPoznamka", "") }
            o.put("internalNote", intNote)
            o.put("interniPoznamka", intNote)

            val isVr = if (o.has("isVraceni") && !o.isNull("isVraceni")) {
                o.optBoolean("isVraceni", false)
            } else if (o.has("vraceni") && !o.isNull("vraceni")) {
                o.optBoolean("vraceni", false)
            } else {
                o.optString("customerSource", "").equals("VRÁCENÍ", ignoreCase = true)
            }
            o.put("isVraceni", isVr)
            o.put("vraceni", isVr)

            val comp = o.optString("company", "").ifBlank { o.optString("customerCompany", "") }
            val custComp = o.optString("customerCompany", "").ifBlank { comp }
            o.put("company", comp)
            o.put("customerCompany", custComp)

            val addr = o.optString("customerAddress", "").ifBlank { o.optString("destinationLocation", "") }
            val dest = o.optString("destinationLocation", "").ifBlank { addr }
            o.put("customerAddress", addr)
            o.put("destinationLocation", dest)

            val c1 = o.optString("containerImageUri", "").takeIf { it.isNotBlank() && it != "null" }
            val c2 = o.optString("containerImageUri2", "").takeIf { it.isNotBlank() && it != "null" }
            val c3 = o.optString("containerImageUri3", "").takeIf { it.isNotBlank() && it != "null" }
            val rawExtra = o.optString("extraImages", "").takeIf { it.isNotBlank() && it != "null" }
            val combinedExtras = listOfNotNull(rawExtra, c2, c3)
                .flatMap { it.split("|") }
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "null" }
                .distinct()
                .joinToString("|")
            o.put("extraImages", combinedExtras)
            if (o.optString("deliveryPhotos", "").isBlank()) {
                val deliv = listOfNotNull(c1, c2, c3).distinct().joinToString("|")
                if (deliv.isNotBlank()) o.put("deliveryPhotos", deliv)
            }

            // Ensure non-null strings for Moshi non-nullable fields
            val nonNullStringFields = listOf(
                "id", "status", "designation", "dateText", "company", "spz", "driver",
                "containerType", "note", "extraImages", "rawExtractedText", "depo",
                "customerName", "customerCompany", "customerIc", "customerDic",
                "customerAddress", "customerPhone", "customerEmail", "customerInitials",
                "customerSource", "sourceOrderId", "sourceZakazkaId", "cisloDokladuZdroj",
                "destinationLocation", "containersJson", "deliveryPhotos", "prevzalaOsoba",
                "prevzalaMobil", "internalNote", "transportDate"
            )
            for (k in nonNullStringFields) {
                if (!o.has(k) || o.isNull(k)) {
                    o.put(k, if (k == "depo") "Depo 1" else if (k == "status") "NOVÁ" else "")
                }
            }
            if (o.isNull("imageUri") || o.optString("imageUri", "") == "null") o.remove("imageUri")
            if (o.isNull("containerImageUri") || o.optString("containerImageUri", "") == "null") o.remove("containerImageUri")
        } catch (_: Throwable) {}
        return o
    }

    private fun mergeJsonArraysByIdAndTimestamp(
        existingFile: File,
        incomingArr: org.json.JSONArray,
        deletedIds: Set<String>,
        isTransport: Boolean = false
    ): String {
        val map = LinkedHashMap<String, org.json.JSONObject>()
        try {
            if (existingFile.exists() && existingFile.length() > 2L) {
                val localArr = org.json.JSONArray(existingFile.readText(Charsets.UTF_8))
                for (i in 0 until localArr.length()) {
                    val item = localArr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").trim()
                    if (id.isNotEmpty() && id !in deletedIds) {
                        map[id] = if (isTransport) normalizeTransportJsonObj(item) else item
                    }
                }
            }
        } catch (_: Throwable) {}

        for (i in 0 until incomingArr.length()) {
            val incRaw = incomingArr.optJSONObject(i) ?: continue
            val inc = if (isTransport) normalizeTransportJsonObj(incRaw) else incRaw
            val id = inc.optString("id", "").trim()
            if (id.isEmpty() || id in deletedIds) continue
            val ex = map[id]
            if (ex == null) {
                map[id] = inc
            } else {
                val incTs = maxOf(inc.optLong("lastModified", 0L), inc.optLong("timestamp", 0L))
                val exTs = maxOf(ex.optLong("lastModified", 0L), ex.optLong("timestamp", 0L))
                if (incTs >= exTs) {
                    map[id] = inc
                }
            }
        }

        val outArr = org.json.JSONArray()
        map.values.forEach { outArr.put(it) }
        return outArr.toString()
    }

    fun saveWebSyncSnapshotToMobile(context: Context, json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val filesDir = context.filesDir

            // 1. Sloučíme deletedIds z lokálního souboru a příchozího JSONu
            val mergedDeletedIds = LinkedHashSet<String>()
            val delFile = File(filesDir, "deleted_sync_ids.json")
            try {
                if (delFile.exists() && delFile.length() > 2L) {
                    val localDel = org.json.JSONArray(delFile.readText(Charsets.UTF_8))
                    for (i in 0 until localDel.length()) {
                        val id = localDel.optString(i, "").trim()
                        if (id.isNotEmpty()) mergedDeletedIds.add(id)
                    }
                }
            } catch (_: Throwable) {}
            if (obj.has("deletedIds")) {
                val incDel = obj.optJSONArray("deletedIds")
                if (incDel != null) {
                    for (i in 0 until incDel.length()) {
                        val id = incDel.optString(i, "").trim()
                        if (id.isNotEmpty()) mergedDeletedIds.add(id)
                    }
                }
            }
            val delOutArr = org.json.JSONArray()
            mergedDeletedIds.forEach { delOutArr.put(it) }
            delFile.writeText(delOutArr.toString(), Charsets.UTF_8)

            // 2. Nedestruktivní sloučení všech agend podle id a timestamp / lastModified
            if (obj.has("containers")) {
                val cFile = File(filesDir, "containers_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(cFile, obj.getJSONArray("containers"), mergedDeletedIds)
                File(filesDir, "sync_containers_cache.json").writeText(mergedStr, Charsets.UTF_8)
                cFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("transports")) {
                val tFile = File(filesDir, "transports_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(tFile, obj.getJSONArray("transports"), mergedDeletedIds, isTransport = true)
                tFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("customers")) {
                val custFile = File(filesDir, "customers_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(custFile, obj.getJSONArray("customers"), mergedDeletedIds)
                custFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("nabidky")) {
                val nFile = File(filesDir, "nabidky_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(nFile, obj.getJSONArray("nabidky"), mergedDeletedIds)
                nFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("zakazky")) {
                val zFile = File(filesDir, "zakazky_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(zFile, obj.getJSONArray("zakazky"), mergedDeletedIds)
                zFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("faktury")) {
                val fFile = File(filesDir, "faktury_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(fFile, obj.getJSONArray("faktury"), mergedDeletedIds)
                fFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("zalohoveFaktury")) {
                val zfFile = File(filesDir, "zalohove_faktury_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(zfFile, obj.getJSONArray("zalohoveFaktury"), mergedDeletedIds)
                zfFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("zajemci")) {
                val zajFile = File(filesDir, "zajemci_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(zajFile, obj.getJSONArray("zajemci"), mergedDeletedIds)
                zajFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("inventories")) {
                val invFile = File(filesDir, "inventories_sync.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(invFile, obj.getJSONArray("inventories"), mergedDeletedIds)
                invFile.writeText(mergedStr, Charsets.UTF_8)
            }
            if (obj.has("catalogItems")) {
                val catFile = File(filesDir, "polozky_catalog.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(catFile, obj.getJSONArray("catalogItems"), mergedDeletedIds)
                catFile.writeText(mergedStr, Charsets.UTF_8)
            } else if (obj.has("polozky")) {
                val catFile = File(filesDir, "polozky_catalog.json")
                val mergedStr = mergeJsonArraysByIdAndTimestamp(catFile, obj.getJSONArray("polozky"), mergedDeletedIds)
                catFile.writeText(mergedStr, Charsets.UTF_8)
            }
        } catch (_: Throwable) {}
    }

    private fun stripEmbeddedSyncSnapshots(html: String): String {
        var current = html
        val marker = "<script id=\"embeddedSyncSnapshot\">"
        val endMarker = "</script>"
        var startIdx = current.indexOf(marker)
        while (startIdx != -1) {
            val endIdx = current.indexOf(endMarker, startIdx)
            if (endIdx == -1) break
            var cutEnd = endIdx + endMarker.length
            if (cutEnd < current.length && current[cutEnd] == '\n') cutEnd++
            current = current.removeRange(startIdx, cutEnd)
            startIdx = current.indexOf(marker)
        }
        return current
    }

    fun readHtmlText(context: Context): String {
        return try {
            val ghFile = File(context.filesDir, "github_index.html")
            // Clean up any bloated cached HTML files (> 12 MB) caused by previous repeated snapshot injections
            if (ghFile.exists() && ghFile.length() > 12_000_000L) {
                try { ghFile.delete() } catch (_: Throwable) {}
            }
            val localIndexFile = File(context.filesDir, "index.html")
            if (localIndexFile.exists() && localIndexFile.length() > 12_000_000L) {
                try { localIndexFile.delete() } catch (_: Throwable) {}
            }

            val useGhFile = if (ghFile.exists() && ghFile.length() in 10_000L..12_000_000L) {
                val headSnippet = try {
                    ghFile.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                        val buf = CharArray(80_000)
                        val n = reader.read(buf)
                        if (n > 0) String(buf, 0, n) else ""
                    }
                } catch (_: Throwable) { "" }
                val ghVer = Regex("""const CURRENT_SW_VERSION\s*=\s*(\d+)""").find(headSnippet)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                ghVer >= APP_VERSION_NUMBER
            } else {
                false
            }

            val rawHtmlLoaded = if (useGhFile) {
                try {
                    ghFile.readText(Charsets.UTF_8)
                } catch (_: Throwable) {
                    context.assets.open("index.html").use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } else {
                context.assets.open("index.html").use { it.readBytes().toString(Charsets.UTF_8) }
            }

            val cleanHtml = stripEmbeddedSyncSnapshots(rawHtmlLoaded)
            val activeVerStr = com.example.data.GitHubBackupManager.getLastUpdateVersion(context)
            val activeVerNum = activeVerStr.toIntOrNull()?.coerceAtLeast(APP_VERSION_NUMBER) ?: APP_VERSION_NUMBER
            val activeDateStr = com.example.data.GitHubBackupManager.getLastUpdateDate(context)

            // Apply version replacements only on the first 160 KB (header/config block) to avoid allocating 9 copies of the entire multi-MB file
            val splitPos = minOf(cleanHtml.length, 160_000)
            val patchedPrefix = cleanHtml.substring(0, splitPos)
                .replace(Regex("""const CURRENT_SW_VERSION\s*=\s*\d+;"""), "const CURRENT_SW_VERSION = $activeVerNum;")
                .replace(Regex("""const CURRENT_SW_VERSION_NAME\s*=\s*"[^"]*";"""), "const CURRENT_SW_VERSION_NAME = \"$activeVerNum\";")
                .replace(Regex("""VERZE \d+ \(v\s*\d+\)"""), "v $activeVerNum")
                .replace(Regex("""VERZE PROGRAMU: \d+ \(v\s*\d+\)"""), "v $activeVerNum")
                .replace(Regex("""PC Terminál v\s*\d+"""), "PC Terminál v $activeVerNum")
                .replace(Regex("""Verze \d+ •"""), "v $activeVerNum •")
                .replace(Regex("""S_POHODA_Kontejnery_v\d+\.html"""), "S_POHODA_Kontejnery_v${activeVerNum}.html")
                .replace(Regex("""cachedVer\s*>\s*\d+"""), "cachedVer > $activeVerNum")
                .replace(Regex("""•\s*\d{1,2}\.\d{1,2}\.\d{4}"""), "• $activeDateStr")

            val syncJson = buildMobileSyncSnapshotJson(context, maxFileBytes = 15_000_000L)
                .replace("</script>", "<\\/script>", ignoreCase = true)
            val geminiKey = try {
                com.example.BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } ?: ""
            } catch (_: Throwable) { "" }
            val keyScript = if (geminiKey.isNotBlank()) "window.__GEMINI_API_KEY__ = \"${geminiKey.replace("\"", "\\\"")}\";" else ""
            val injection = "<script id=\"embeddedSyncSnapshot\">$keyScript window.__MOBILE_INITIAL_SYNC__ = $syncJson;</script>\n</head>"

            val headCloseIdx = patchedPrefix.indexOf("</head>")
            if (headCloseIdx != -1) {
                val sb = StringBuilder(patchedPrefix.length + (cleanHtml.length - splitPos) + injection.length)
                sb.append(patchedPrefix, 0, headCloseIdx)
                sb.append(injection)
                sb.append(patchedPrefix, headCloseIdx + "</head>".length, patchedPrefix.length)
                if (splitPos < cleanHtml.length) {
                    sb.append(cleanHtml, splitPos, cleanHtml.length)
                }
                sb.toString()
            } else {
                patchedPrefix + cleanHtml.substring(splitPos)
            }
        } catch (_: Throwable) {
            try {
                context.assets.open("index.html").use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Throwable) {
                ""
            }
        }
    }

    fun readHtmlBytes(context: Context): ByteArray {
        return try {
            readHtmlText(context).toByteArray(Charsets.UTF_8)
        } catch (_: Throwable) {
            try {
                context.assets.open("index.html").use { it.readBytes() }
            } catch (_: Throwable) {
                ByteArray(0)
            }
        }
    }

    /**
     * Okamžitě uloží index.html do veřejné složky Stažené soubory (Download/index.html)
     * bez nutnosti systémového dialogu, který na některých zařízeních/emulátorech selhává.
     */
    fun saveHtmlDirectlyToDownloads(context: Context): String {
        val htmlBytes = readHtmlBytes(context)
        val activeVerNum = com.example.data.GitHubBackupManager.getLastUpdateVersion(context).toIntOrNull()?.coerceAtLeast(APP_VERSION_NUMBER) ?: APP_VERSION_NUMBER
        val fileName = "S_POHODA_Kontejnery_v${activeVerNum}.html"
        val indexFileName = "index.html"

        // 1. Vždy uložíme kopii i do cache a externalFilesDir pro okamžité sdílení/otevření
        try {
            File(context.cacheDir, fileName).writeBytes(htmlBytes)
            File(context.cacheDir, indexFileName).writeBytes(htmlBytes)
        } catch (_: Throwable) {}

        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (extDir != null) {
                if (!extDir.exists()) extDir.mkdirs()
                File(extDir, fileName).writeBytes(htmlBytes)
                File(extDir, indexFileName).writeBytes(htmlBytes)
            }
        } catch (_: Exception) {}

        // 2. Android 10+ (API 29+): Uložení přímo do veřejné složky Download přes MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                for (targetName in listOf(fileName, indexFileName)) {
                    try {
                        resolver.delete(
                            collection,
                            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                            arrayOf(targetName)
                        )
                    } catch (_: Exception) {}
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, targetName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/html")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val itemUri = resolver.insert(collection, contentValues)
                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            out.write(htmlBytes)
                            out.flush()
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(itemUri, contentValues, null, null)
                    }
                }
                return "Download/$fileName"
            } catch (_: Exception) {
                // Fallback níže
            }
        }

        // 3. Fallback pro starší Android nebo přímý zápis do veřejné složky Download
        try {
            @Suppress("DEPRECATION")
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null) {
                if (!publicDownloads.exists()) publicDownloads.mkdirs()
                val outFile = File(publicDownloads, fileName)
                FileOutputStream(outFile).use { it.write(htmlBytes) }
                FileOutputStream(File(publicDownloads, indexFileName)).use { it.write(htmlBytes) }
                return outFile.absolutePath
            }
        } catch (_: Exception) {}

        // 4. Finální fallback do aplikační složky Download
        val fallbackFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            fileName
        )
        fallbackFile.writeBytes(htmlBytes)
        return fallbackFile.absolutePath
    }

    fun shareHtmlFile(context: Context) {
        try {
            val htmlBytes = readHtmlBytes(context)
            val activeVerNum = com.example.data.GitHubBackupManager.getLastUpdateVersion(context).toIntOrNull()?.coerceAtLeast(APP_VERSION_NUMBER) ?: APP_VERSION_NUMBER
            val fileName = "S_POHODA_Kontejnery_v${activeVerNum}.html"
            val shareFile = File(context.cacheDir, fileName)
            shareFile.writeBytes(htmlBytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                shareFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_SUBJECT, "Kontejnery Ostrava - PC Terminál ($fileName)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Sdílet nebo odeslat $fileName"))
        } catch (e: Exception) {
            Toast.makeText(context, "Chyba při sdílení: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun copyHtmlToClipboard(context: Context) {
        try {
            val htmlText = readHtmlText(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("index.html", htmlText))
            Toast.makeText(
                context,
                "Celý kód index.html (${htmlText.length / 1024} KB) byl zkopírován do schránky!",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Chyba kopírování: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    suspend fun publishHtmlUpdateToCloud(context: Context): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val htmlText = readHtmlText(context)
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val chunkSize = 100_000
                val chunks = htmlText.chunked(chunkSize)
                for ((idx, chunkStr) in chunks.withIndex()) {
                    val chunkDoc = mapOf(
                        "index" to idx,
                        "version" to APP_VERSION_NUMBER,
                        "content" to chunkStr,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    com.google.android.gms.tasks.Tasks.await(
                        db.collection("app_updates").document("chunk_$idx").set(chunkDoc)
                    )
                }
                val metaDoc = mapOf(
                    "version" to APP_VERSION_NUMBER,
                    "versionName" to APP_VERSION_NAME,
                    "badge" to "$APP_VERSION_BADGE • $APP_LAST_UPDATE_DATE",
                    "fileName" to "S_POHODA_Kontejnery_v${APP_VERSION_NUMBER}.html",
                    "chunkCount" to chunks.size,
                    "totalLength" to htmlText.length,
                    "storageUrl" to "",
                    "updatedAt" to System.currentTimeMillis()
                )
                com.google.android.gms.tasks.Tasks.await(
                    db.collection("app_updates").document("latest_meta").set(metaDoc)
                )
                "CLOUD_FIRESTORE_OK"
            } catch (e: Exception) {
                null
            }
        }
    }

    fun buildIosMobileConfigXml(
        targetUrl: String = "https://kont-premysl-sliva.github.io/Home_1/iphone.html?iphone=1",
        context: Context? = null
    ): String {
        val iconTag = try {
            val iconBytes = context?.assets?.open("public/assets/ko_print_logo.png")?.use { it.readBytes() }
            if (iconBytes != null && iconBytes.isNotEmpty()) {
                val b64 = android.util.Base64.encodeToString(iconBytes, android.util.Base64.NO_WRAP)
                "            <key>Icon</key>\n            <data>$b64</data>\n"
            } else ""
        } catch (_: Exception) { "" }

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>PayloadContent</key>
    <array>
        <dict>
            <key>FullScreen</key>
            <true/>
$iconTag            <key>IgnoreManifestScope</key>
            <true/>
            <key>IsRemovable</key>
            <true/>
            <key>Label</key>
            <string>Přeprava KO</string>
            <key>PayloadDescription</key>
            <string>Mobilní aplikace pro řidiče (Sekce Přeprava) • Kontejnery Ostrava v $APP_VERSION_NUMBER</string>
            <key>PayloadDisplayName</key>
            <string>Přeprava KO – Řidič (iPhone)</string>
            <key>PayloadIdentifier</key>
            <string>cz.kontejneryostrava.ios.webclip.v$APP_VERSION_NUMBER</string>
            <key>PayloadType</key>
            <string>com.apple.webClip.managed</string>
            <key>PayloadUUID</key>
            <string>6A4E8F12-90B1-4C3D-A812-000000000$APP_VERSION_NUMBER</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
            <key>Precomposed</key>
            <true/>
            <key>URL</key>
            <string>$targetUrl</string>
        </dict>
    </array>
    <key>PayloadDescription</key>
    <string>Nainstaluje zkrácenou mobilní aplikaci Přeprava KO pro řidiče (v $APP_VERSION_NUMBER) na plochu iPhone v režimu celé obrazovky.</string>
    <key>PayloadDisplayName</key>
    <string>Přeprava KO • Aplikace pro řidiče (iPhone) • v $APP_VERSION_NUMBER</string>
    <key>PayloadIdentifier</key>
    <string>cz.kontejneryostrava.ios.profile.v$APP_VERSION_NUMBER</string>
    <key>PayloadOrganization</key>
    <string>Kontejnery Ostrava s.r.o.</string>
    <key>PayloadRemovalDisallowed</key>
    <false/>
    <key>PayloadType</key>
    <string>Configuration</string>
    <key>PayloadUUID</key>
    <string>9B7C2D45-11E2-4F8A-B$APP_VERSION_NUMBER-000000000$APP_VERSION_NUMBER</string>
    <key>PayloadVersion</key>
    <integer>1</integer>
</dict>
</plist>"""
    }

    fun readIphoneHtmlText(context: Context): String {
        return try {
            context.assets.open("iphone.html").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            val raw = readHtmlText(context)
            raw.replace(
                "<head>",
                "<head><script>window.FORCE_IPHONE_APP_MODE = true; try { localStorage.setItem('ko_ios_app_mode','1'); } catch(e){}</script>"
            )
        }
    }

    fun buildIphoneIpaBytes(context: Context): ByteArray {
        try {
            return context.assets.open("Kontejnery_Preprava_iPhone.ipa").use { it.readBytes() }
        } catch (_: Exception) {}

        val iphoneHtml = readIphoneHtmlText(context)
        val infoPlist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>cs</string>
    <key>CFBundleDisplayName</key>
    <string>Přeprava KO</string>
    <key>CFBundleExecutable</key>
    <string>PrepravaKO</string>
    <key>CFBundleIdentifier</key>
    <string>cz.kontejneryostrava.preprava.driver</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>PrepravaKO</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${APP_VERSION_NUMBER}.0</string>
    <key>CFBundleVersion</key>
    <string>$APP_VERSION_NUMBER</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
    <key>KOAppRole</key>
    <string>DRIVER_PREPRAVA_ONLY</string>
    <key>KOGitHubRepo</key>
    <string>Kont-premysl-sliva/Home_1</string>
    <key>KOGitHubUpdateURL</key>
    <string>https://raw.githubusercontent.com/Kont-premysl-sliva/Home_1/main/iphone/iphone.html</string>
</dict>
</plist>"""

        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            fun putEntry(name: String, data: ByteArray) {
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
            putEntry("Payload/PrepravaKO.app/Info.plist", infoPlist.toByteArray(Charsets.UTF_8))
            putEntry("Payload/PrepravaKO.app/PkgInfo", "APPL????".toByteArray(Charsets.UTF_8))
            putEntry("Payload/PrepravaKO.app/PrepravaKO", "#!/bin/sh\nexec \"$(dirname \"$0\")/www/iphone.html\"\n".toByteArray(Charsets.UTF_8))
            putEntry("Payload/PrepravaKO.app/www/iphone.html", iphoneHtml.toByteArray(Charsets.UTF_8))
            putEntry("Payload/PrepravaKO.app/Kontejnery_iPhone.mobileconfig", buildIosMobileConfigXml(context = context).toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }

    fun saveIphoneIpaToDownloads(context: Context): String {
        val bytes = buildIphoneIpaBytes(context)
        val fileName = "Kontejnery_Preprava_iPhone_v${APP_VERSION_NUMBER}.ipa"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return "Stažené soubory / $fileName"
                }
            } catch (_: Exception) {}
        }
        val out = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    fun shareIphoneIpaFile(context: Context) {
        try {
            val bytes = buildIphoneIpaBytes(context)
            val fileName = "Kontejnery_Preprava_iPhone_v${APP_VERSION_NUMBER}.ipa"
            val shareFile = File(context.cacheDir, fileName)
            shareFile.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", shareFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_SUBJECT, "Přeprava KO – iPhone .IPA aplikace pro řidiče ($fileName)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Instalační balíček .IPA pro Apple iPhone (pouze sekce PŘEPRAVA + aktualizace přes GitHub Kont-premysl-sliva/Home_1) • v $APP_VERSION_NUMBER.\n\n" +
                    "Okamžitá instalace přes Safari / QR kód:\nhttps://kont-premysl-sliva.github.io/Home_1/install"
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Sdílet iPhone .IPA aplikaci pro řidiče"))
        } catch (e: Exception) {
            Toast.makeText(context, "Chyba při sdílení .IPA: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun saveIphoneHtmlToDownloads(context: Context): String {
        val bytes = readIphoneHtmlText(context).toByteArray(Charsets.UTF_8)
        val fileName = "Kontejnery_iPhone_v${APP_VERSION_NUMBER}.html"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return "Stažené soubory / $fileName"
                }
            } catch (_: Exception) {}
        }
        val out = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    fun saveIosMobileConfigToDownloads(context: Context): String {
        val bytes = buildIosMobileConfigXml(context = context).toByteArray(Charsets.UTF_8)
        val fileName = "Kontejnery_iPhone_v${APP_VERSION_NUMBER}.mobileconfig"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-apple-aspen-config")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return "Stažené soubory / $fileName"
                }
            } catch (_: Exception) {}
        }
        val out = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    fun shareIphoneAppFile(context: Context) {
        try {
            val bytes = readIphoneHtmlText(context).toByteArray(Charsets.UTF_8)
            val fileName = "Kontejnery_iPhone_v${APP_VERSION_NUMBER}.html"
            val shareFile = File(context.cacheDir, fileName)
            shareFile.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", shareFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_SUBJECT, "Přeprava KO – Mobilní aplikace pro řidiče na iPhone ($fileName)")
                putExtra(Intent.EXTRA_TEXT, "Mobilní aplikace Přeprava KO pro řidiče (pouze sekce PŘEPRAVA) na iPhone • v $APP_VERSION_NUMBER: https://kont-premysl-sliva.github.io/Home_1/install")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Sdílet iPhone aplikaci pro řidiče"))
        } catch (e: Exception) {
            Toast.makeText(context, "Chyba při sdílení: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun HtmlDownloadDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val highestVer = rememberHighestVersionNumber()
    val unifiedVersionBadge = rememberUnifiedVersionBadge()
    var savedLocation by remember { mutableStateOf<String?>(null) }
    var cloudStatus by remember(unifiedVersionBadge) { mutableStateOf("Nahrávám $unifiedVersionBadge do Cloudu Firebase pro vaše PC...") }
    var cloudStorageUrl by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showWebViewPreview by remember { mutableStateOf(false) }
    var showHtmlSource by remember { mutableStateOf(false) }
    var htmlText by remember { mutableStateOf("") }
    var htmlSizeKb by remember { mutableStateOf(1400) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loaded = HtmlDownloadManager.readHtmlText(context)
                htmlText = loaded
                htmlSizeKb = (loaded.length / 1024).coerceAtLeast(1)
                val path = HtmlDownloadManager.saveHtmlDirectlyToDownloads(context)
                savedLocation = path
            } catch (e: Throwable) {
                errorText = e.message
            }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bytes = HtmlDownloadManager.readHtmlBytes(context)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        savedLocation = uri.toString()
                        Toast.makeText(context, "Soubor index.html byl úspěšně uložen!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Chyba při ukládání: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showWebViewPreview) {
        HtmlWebViewPreviewDialog(
            onDismiss = { showWebViewPreview = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PC Terminál ($unifiedVersionBadge)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                if (savedLocation != null) {
                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Soubor připraven ($htmlSizeKb KB)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0C4A6E),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Pro ruční stažení v AI Studiu můžete také v levém panelu souborů stáhnout /S_POHODA_Kontejnery_v$highestVer.html nebo /index.html.",
                                    color = Color(0xFF0369A1),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = "Upozornění: $errorText",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Kompletní webová aplikace PC Terminál ($unifiedVersionBadge) obsahuje DEPO 1, DEPO 2, samostatnou sekci POLOŽKY (katalog Kontejnerů 001–003, Vybavení a Přepravy s řazením a dobíráním do CN a Zakázek) i export do SW POHODA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Vybrat složku v Průzkumníku (Uložit samostatný soubor S_POHODA_Kontejnery_vX.html)
                Button(
                    onClick = {
                        try {
                            createDocLauncher.launch("S_POHODA_Kontejnery_v${highestVer}.html")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Nelze otevřít průzkumník souborů: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ULOŽIT DO PC / PRŮZKUMNÍKU (v$highestVer.html)", fontWeight = FontWeight.Bold)
                }

                // 2. Otevřít přímo v aplikaci (WebView PC Terminál)
                Button(
                    onClick = { showWebViewPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1D4ED8),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OTEVŘÍT HTML (PC TERMINÁL) ZDE", fontWeight = FontWeight.Bold)
                }

                // 3. Zkopírovat HTML kód do schránky (pro PC)
                OutlinedButton(
                    onClick = { HtmlDownloadManager.copyHtmlToClipboard(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ZKOPÍROVAT HTML KÓD DO SCHRÁNKY", fontWeight = FontWeight.Bold)
                }

                // 4. Sdílet / poslat e-mailem
                OutlinedButton(
                    onClick = { HtmlDownloadManager.shareHtmlFile(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SDÍLET / ODESLAT SOUBOR (E-MAIL)", fontWeight = FontWeight.Bold)
                }

                // 5. Zobrazit vygenerovaný zdrojový kód HTML
                TextButton(
                    onClick = { showHtmlSource = !showHtmlSource },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showHtmlSource) "SKRÝT ZDROJOVÝ KÓD HTML ▲" else "ZOBRAZIT VYGENEROVANÝ KÓD HTML ($htmlSizeKb KB) ▼",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showHtmlSource && htmlText.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text = htmlText.take(12000) + "\n\n... [Pro celý kód ($htmlSizeKb KB) klepněte na ZKOPÍROVAT HTML KÓD DO SCHRÁNKY nebo STÁHNOUT]",
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ZAVŘÍT", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebViewPreviewDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E3A8A))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PC Terminál – index.html (Živý náhled)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val path = HtmlDownloadManager.saveHtmlDirectlyToDownloads(context)
                                Toast.makeText(context, "Uloženo do: $path", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STÁHNOUT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Zavřít",
                                tint = Color.White
                            )
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun downloadHtml() {
                                        val path = HtmlDownloadManager.saveHtmlDirectlyToDownloads(ctx)
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            Toast.makeText(
                                                ctx,
                                                "Soubor index.html byl uložen do: $path",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }

                                    @JavascriptInterface
                                    fun getMobileSyncJson(): String {
                                        return HtmlDownloadManager.buildMobileSyncSnapshotJson(ctx)
                                    }

                                    @JavascriptInterface
                                    fun getSyncData(): String {
                                        return HtmlDownloadManager.buildMobileSyncSnapshotJson(ctx)
                                    }

                                    @JavascriptInterface
                                    fun saveFromWebJson(json: String) {
                                        HtmlDownloadManager.saveWebSyncSnapshotToMobile(ctx, json)
                                    }

                                    @JavascriptInterface
                                    fun pushSyncData(json: String) {
                                        HtmlDownloadManager.saveWebSyncSnapshotToMobile(ctx, json)
                                    }

                                    @JavascriptInterface
                                    fun analyzePhoto(base64Data: String, kind: String): String {
                                        return analyzePhotoWithHint(base64Data, kind, "")
                                    }

                                    @JavascriptInterface
                                    fun analyzePhotoWithHint(base64Data: String, kind: String, hint: String): String {
                                        return try {
                                            val cleanBase64 = if (base64Data.contains(",")) {
                                                base64Data.substringAfter(",")
                                            } else {
                                                base64Data
                                            }
                                            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            if (bmp == null) return "{}"
                                            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                                                when (kind.lowercase()) {
                                                    "customer", "zajemce", "adresar" -> com.example.api.analyzeVizitka(bmp)
                                                    "container", "kontejner" -> com.example.api.analyzeContainer(bmp, hint.ifBlank { null })
                                                    "transport", "preprava" -> com.example.api.analyzePreprava(bmp)
                                                    "protocol", "zakazka", "doklad" -> com.example.api.analyzeProtocol(bmp)
                                                    else -> com.example.api.analyzeContainer(bmp, hint.ifBlank { null })
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("AndroidBridge", "analyzePhoto failed: ${e.message}")
                                            "{}"
                                        }
                                    }
                                },
                                "AndroidBridge"
                            )
                            setDownloadListener { _, _, _, _, _ ->
                                val path = HtmlDownloadManager.saveHtmlDirectlyToDownloads(ctx)
                                Toast.makeText(
                                    ctx,
                                    "Soubor uložen do: $path",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            val ghFile = File(ctx.filesDir, "github_index.html")
                            if (ghFile.exists() && ghFile.length() > 10_000) {
                                val ghVer = try {
                                    Regex("""const CURRENT_SW_VERSION\s*=\s*(\d+)""").find(ghFile.readText(Charsets.UTF_8))?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                                } catch (_: Exception) { 0 }
                                if (ghVer >= APP_VERSION_NUMBER) {
                                    loadUrl("file://${ghFile.absolutePath}")
                                } else {
                                    loadUrl("file:///android_asset/index.html")
                                }
                            } else {
                                loadUrl("file:///android_asset/index.html")
                            }
                        }
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IphoneAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLivePreview by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isUploadingGitHub by remember { mutableStateOf(false) }

    if (showLivePreview) {
        Dialog(
            onDismissRequest = { showLivePreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F172A)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "iPHONE ŘIDIČ • v $APP_VERSION_NUMBER",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    val path = HtmlDownloadManager.saveIphoneIpaToDownloads(context)
                                    Toast.makeText(context, "Soubor .IPA uložen: $path", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(".IPA", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val path = HtmlDownloadManager.saveIosMobileConfigToDownloads(context)
                                    Toast.makeText(context, "Profil uložen: $path", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("PROFIL", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { showLivePreview = false },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("ZAVŘÍT", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                        }
                    }
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadUrl("file:///android_asset/iphone.html?iphone=1")
                            }
                        }
                    )
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val iphoneUrl = "https://kont-premysl-sliva.github.io/Home_1/install"
        val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&margin=8&data=" + java.net.URLEncoder.encode(iphoneUrl, "UTF-8")

        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(10.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0284C7))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneIphone,
                                contentDescription = "Apple iPhone",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.padding(6.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "iPHONE APLIKACE PRO ŘIDIČE (.IPA & QR)",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Pouze sekce PŘEPRAVA + GitHub • iOS v $APP_VERSION_NUMBER",
                                color = Color(0xFFE0F2FE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("ZAVŘÍT", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (statusBanner != null) {
                        Surface(
                            color = Color(0xFF0369A1),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusBanner ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // A) SOUBOR .IPA + GITHUB AKTUALIZACE
                    Surface(
                        color = Color(0xFF1E1B4B),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "A) Soubor .IPA (Kontejnery_Preprava_iPhone.ipa) + GitHub",
                                color = Color(0xFFDDD6FE),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Plnohodnotný iOS balíček .IPA (Payload/PrepravaKO.app) obsahující výhradně sekci PŘEPRAVA pro řidiče a automatickou aktualizaci jízd i verze přes GitHub (Kont-premysl-sliva/Home_1). Pro instalaci .IPA přes PC/Mac použijte Sideloadly, AltStore nebo Apple Configurator 2.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val path = HtmlDownloadManager.saveIphoneIpaToDownloads(context)
                                        statusBanner = "Balíček .IPA uložen do: $path"
                                        Toast.makeText(context, "Uloženo: $path", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("STÁHNOUT .IPA", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { HtmlDownloadManager.shareIphoneIpaFile(context) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SDÍLET .IPA", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    if (!isUploadingGitHub) {
                                        isUploadingGitHub = true
                                        statusBanner = "Nahrávám iphone.html, .IPA a .mobileconfig na GitHub..."
                                        scope.launch {
                                            val res = com.example.data.GitHubBackupManager.uploadToGitHub(
                                                context = context,
                                                commitMessage = "release: iPhone Přeprava .IPA + iphone.html v$APP_VERSION_NUMBER",
                                                onStatus = { msg: String -> statusBanner = msg }
                                            )
                                            isUploadingGitHub = false
                                            statusBanner = when (res) {
                                                is com.example.data.GitHubBackupManager.GitHubResult.Success ->
                                                    "✅ GitHub aktualizován (v $APP_VERSION_NUMBER): nahrán iphone.html, Kontejnery_Preprava_iPhone.ipa i databáze přeprav!"
                                                is com.example.data.GitHubBackupManager.GitHubResult.Error ->
                                                    "⚠️ ${res.message}"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                Text(
                                    text = if (isUploadingGitHub) "NAHRÁVÁM NA GITHUB..." else "AKTUALIZOVAT .IPA & PŘEPRAVU NA GITHUBU",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // B) QR Kód a přímý odkaz pro okamžitou instalaci přes Safari
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "B) Okamžitá instalace přes QR kód (bez počítače)",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Namiřte fotoaparát iPhonu řidiče na tento QR kód – otevře se průvodce instalací přímo na displeji iPhonu:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Bílá plocha s QR kódem
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(190.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize().padding(10.dp)
                                ) {
                                    AsyncImage(
                                        model = qrUrl,
                                        contentDescription = "QR kód pro instalaci na iPhone",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Nebo otevřete v Safari na iPhonu:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = iphoneUrl,
                                    color = Color(0xFF38BDF8),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // Tříkrokový návod pro Safari na iPhone
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Jak proběhne instalace v mobilu iPhone:",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF0284C7),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    text = "Řidič naskenuje QR kód fotoaparátem iPhonu (nebo mu pošlete odkaz přes WhatsApp/SMS/AirDrop) a otevře jej v prohlížeči Safari.",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF0284C7),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    text = "V dolní liště Safari klepne na ikonu Sdílet ⎋ (čtvereček se šipkou nahoru) a vybere „Přidat na plochu“ (nebo klepne na zelený banner NÁVOD K INSTALACI přímo na obrazovce iPhonu).",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF0284C7),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    text = "Na ploše iPhonu vznikne ikona „Přeprava KO“. Po spuštění běží přes celou obrazovku, obsahuje výhradně sekci PŘEPRAVA a tlačítkem GITHUB v horní liště se kdykoli zaktualizuje.",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Tlačítka pro rychlé akce
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("iPhone URL", iphoneUrl))
                                statusBanner = "Odkaz pro iPhone byl zkopírován do schránky."
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ZKOPÍROVAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                try {
                                    val msg = "Kontejnery Ostrava – Instalace mobilní aplikace Přeprava KO pro řidiče (iPhone): $iphoneUrl"
                                    val waIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/?text=" + java.net.URLEncoder.encode(msg, "UTF-8")))
                                    context.startActivity(waIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Nelze otevřít WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WHATSAPP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { HtmlDownloadManager.shareIphoneAppFile(context) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SDÍLET ODKAZ", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val path = HtmlDownloadManager.saveIosMobileConfigToDownloads(context)
                                statusBanner = "iOS profil uložen: $path"
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PROFIL .mobileconfig", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

