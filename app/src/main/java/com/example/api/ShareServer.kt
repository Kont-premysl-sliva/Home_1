package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.BackupManager
import com.example.data.CatalogItemRecord
import com.example.data.ContainerCard
import com.example.data.ContainerRepository
import com.example.data.CustomerRecord
import com.example.data.FakturaRecord
import com.example.data.NabidkaRecord
import com.example.data.TransportRecord
import com.example.data.ZajemceRecord
import com.example.data.ZakazkaRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections

data class FullSyncPayload(
    val customers: List<CustomerRecord> = emptyList(),
    val nabidky: List<NabidkaRecord> = emptyList(),
    val zakazky: List<ZakazkaRecord> = emptyList(),
    val faktury: List<FakturaRecord> = emptyList(),
    val zalohoveFaktury: List<FakturaRecord> = emptyList(),
    val zajemci: List<ZajemceRecord> = emptyList(),
    val polozky: List<CatalogItemRecord> = emptyList(),
    val catalogItems: List<CatalogItemRecord> = emptyList(),
    val containers: List<ContainerCard> = emptyList(),
    val transports: List<TransportRecord> = emptyList(),
    val deletedIds: List<String> = emptyList()
)

class ShareServer(
    private val context: Context,
    private val repository: ContainerRepository,
    private val onLogMessage: (String) -> Unit
) {
    private var server: HttpServer? = null
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val containerAdapter = moshi.adapter(ContainerCard::class.java)
    private val containerListAdapter = moshi.adapter<List<ContainerCard>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, ContainerCard::class.java)
    )
    private val syncPayloadAdapter = moshi.adapter(FullSyncPayload::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun start(port: Int = 8080): String? {
        try {
            stop()
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/", MainHandler())
                createContext("/api", ApiHandler())
                executor = java.util.concurrent.Executors.newCachedThreadPool()
                start()
            }
            onLogMessage("Server spuštěn na portu $port")
            return "http://${getIpAddress()}:$port"
        } catch (e: Exception) {
            e.printStackTrace()
            onLogMessage("Chyba při spuštění serveru: ${e.message}")
            return null
        }
    }

    fun stop() {
        try {
            server?.stop(0)
            server = null
            onLogMessage("Server zastaven")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isRunning(): Boolean = server != null

    fun getIpAddress(): String {
        return getWifiIpAddress() ?: "127.0.0.1"
    }

    private fun getWifiIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Nejprve zkusíme najít Wi-Fi rozhraní (obvykle začíná na "wlan" nebo "eth")
            for (intf in interfaces) {
                if (intf.name.startsWith("wlan") || intf.name.startsWith("eth")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress
                            val isIPv4 = sAddr != null && sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                return sAddr
                            }
                        }
                    }
                }
            }
            // Pokud nenajdeme wlan, zkusíme jakékoliv jiné non-loopback IPv4 (jako fallback)
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr != null && sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    private inner class MainHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val path = exchange.requestURI.path
                val query = exchange.requestURI.query ?: ""
                if (path == "/pc-terminal.html" || path == "/download-html") {
                    val bytes = com.example.ui.HtmlDownloadManager.readHtmlBytes(context)
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    if (path == "/download-html" || query.contains("download=1")) {
                        exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"S_POHODA_Kontejnery_v${com.example.ui.APP_VERSION_NUMBER}.html\"")
                    }
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    onLogMessage("Stažen HTML soubor PC Terminálu (index.html)")
                } else if (path == "/iphone" || path == "/iphone.html") {
                    val bytes = com.example.ui.HtmlDownloadManager.readIphoneHtmlText(context).toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    if (query.contains("download=1")) {
                        exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"Kontejnery_iPhone_v${com.example.ui.APP_VERSION_NUMBER}.html\"")
                    }
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    onLogMessage("Otevřena mobilní aplikace pro iPhone (iphone.html)")
                } else if (path == "/install" || path == "/install.html") {
                    val bytes = try {
                        context.assets.open("install.html").use { it.readBytes() }
                    } catch (_: Exception) {
                        com.example.ui.HtmlDownloadManager.readIphoneHtmlText(context).toByteArray(Charsets.UTF_8)
                    }
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    onLogMessage("Otevřena instalační stránka pro iPhone (/install)")
                } else if (path == "/iphone.ipa" || path == "/Kontejnery_Preprava_iPhone.ipa") {
                    val bytes = com.example.ui.HtmlDownloadManager.buildIphoneIpaBytes(context)
                    exchange.responseHeaders.set("Content-Type", "application/octet-stream")
                    exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"Kontejnery_Preprava_iPhone_v${com.example.ui.APP_VERSION_NUMBER}.ipa\"")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    onLogMessage("Stažen instalační balíček .IPA pro iPhone (Kontejnery_Preprava_iPhone.ipa)")
                } else if (path == "/iphone.mobileconfig" || path == "/Kontejnery_iPhone.mobileconfig") {
                    val hostHeader = exchange.requestHeaders.getFirst("Host")
                    val targetUrl = if (!hostHeader.isNullOrBlank()) {
                        "http://$hostHeader/iphone.html?iphone=1"
                    } else {
                        "https://kont-premysl-sliva.github.io/Home_1/iphone.html?iphone=1"
                    }
                    val xml = com.example.ui.HtmlDownloadManager.buildIosMobileConfigXml(targetUrl, context)
                    val bytes = xml.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/x-apple-aspen-config")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    onLogMessage("Otevřen instalační iOS profil (.mobileconfig) pro iPhone")
                } else if (path == "/" || path == "/index.html") {
                    val bytes = com.example.ui.HtmlDownloadManager.readHtmlBytes(context)
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    onLogMessage("Otevřen PC Terminál (index.html) v PC prohlížeči")
                } else {
                    sendError(exchange, 404, "Nenalezeno")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendError(exchange, 500, "Chyba serveru: ${e.message}")
            }
        }
    }

    private inner class ApiHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")

                if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                    exchange.sendResponseHeaders(204, -1)
                    return
                }

                val path = exchange.requestURI.path
                val query = exchange.requestURI.query

                when {
                    path == "/api/containers" && exchange.requestMethod.equals("GET", ignoreCase = true) -> {
                        coroutineScope.launch {
                            val list = repository.getAllContainersDirect()
                            val json = containerListAdapter.toJson(list)
                            sendJsonResponse(exchange, 200, json)
                        }
                    }
                    path == "/api/containers" && exchange.requestMethod.equals("POST", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val body = readRequestBody(exchange)
                                val raw = containerAdapter.fromJson(body)
                                if (raw != null) {
                                    val now = System.currentTimeMillis()
                                    val cleanDepo = raw.depo.ifBlank { if (raw.status == "DEPO 02") "Depo 2" else "Depo 1" }
                                    val cleanStatus = raw.status.ifBlank { if (cleanDepo == "Depo 2") "DEPO 02" else "DEPO 01" }
                                    val container = raw.copy(
                                        depo = cleanDepo,
                                        status = cleanStatus,
                                        timestamp = if (raw.timestamp > 0) raw.timestamp else now,
                                        lastModified = if (raw.lastModified > 0) raw.lastModified else now
                                    )
                                    if (container.id.isNotBlank()) repository.update(container) else repository.insert(container)
                                    sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Kontejner přidán\"}")
                                    onLogMessage("Přidán kontejner přes web: ${container.designation}")
                                } else {
                                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Neplatná data\"}")
                                }
                            } catch (e: Exception) {
                                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"${e.message}\"}")
                            }
                        }
                    }
                    path == "/api/containers" && exchange.requestMethod.equals("PUT", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val body = readRequestBody(exchange)
                                val raw = containerAdapter.fromJson(body)
                                if (raw != null) {
                                    val now = System.currentTimeMillis()
                                    val cleanDepo = raw.depo.ifBlank { if (raw.status == "DEPO 02") "Depo 2" else "Depo 1" }
                                    val cleanStatus = raw.status.ifBlank { if (cleanDepo == "Depo 2") "DEPO 02" else "DEPO 01" }
                                    val container = raw.copy(
                                        depo = cleanDepo,
                                        status = cleanStatus,
                                        lastModified = if (raw.lastModified > 0) raw.lastModified else now
                                    )
                                    repository.update(container)
                                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Kontejner upraven\"}")
                                    onLogMessage("Upraven kontejner přes web: ${container.designation}")
                                } else {
                                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Neplatná data\"}")
                                }
                            } catch (e: Exception) {
                                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"${e.message}\"}")
                            }
                        }
                    }
                    path == "/api/containers" && exchange.requestMethod.equals("DELETE", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val params = parseQueryParams(query)
                                val idStr = params["id"]
                                if (idStr != null) {
                                    val id = idStr
                                    if (id.isNotEmpty()) {
                                        val container = repository.getContainerById(id)
                                        if (container != null) {
                                            repository.delete(container)
                                            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Kontejner smazán\"}")
                                            onLogMessage("Smazán kontejner přes web: ${container.designation}")
                                        } else {
                                            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Kontejner nenalezen\"}")
                                        }
                                    } else {
                                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Neplatné ID\"}")
                                    }
                                } else {
                                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Chybí parametr ID\"}")
                                }
                            } catch (e: Exception) {
                                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"${e.message}\"}")
                            }
                        }
                    }
                    path == "/api/image" && exchange.requestMethod.equals("GET", ignoreCase = true) -> {
                        val params = parseQueryParams(query)
                        val imagePath = params["path"]
                        if (imagePath != null) {
                            val decodedPath = URLDecoder.decode(imagePath, "UTF-8").removePrefix("file://").trim()
                            if (decodedPath.startsWith("data:image/")) {
                                val commaIdx = decodedPath.indexOf(',')
                                if (commaIdx > 0) {
                                    val b64 = decodedPath.substring(commaIdx + 1).trim()
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    exchange.responseHeaders.set("Content-Type", "image/jpeg")
                                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                                    exchange.responseBody.use { it.write(bytes) }
                                    return
                                }
                            }
                            val file = File(decodedPath)
                            if (file.exists() && file.isFile) {
                                val bytes = file.readBytes()
                                exchange.responseHeaders.set("Content-Type", "image/jpeg")
                                exchange.sendResponseHeaders(200, bytes.size.toLong())
                                exchange.responseBody.use { it.write(bytes) }
                            } else {
                                sendError(exchange, 404, "Obrázek nenalezen")
                            }
                        } else {
                            sendError(exchange, 400, "Chybí cesta k obrázku")
                        }
                    }
                    path == "/api/backup/export" && exchange.requestMethod.equals("GET", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val backupManager = BackupManager(context)
                                val backupPath = withContext(Dispatchers.IO) {
                                    backupManager.createDataBackup { }
                                }
                                val file = File(backupPath)
                                if (file.exists()) {
                                    val bytes = file.readBytes()
                                    exchange.responseHeaders.set("Content-Type", "application/zip")
                                    exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"DataBackup_${System.currentTimeMillis()}.zip\"")
                                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                                    exchange.responseBody.use { it.write(bytes) }
                                    onLogMessage("Exportována kompletní záloha ZIP přes web")
                                } else {
                                    sendError(exchange, 500, "Nepodařilo se vytvořit soubor zálohy")
                                }
                            } catch (e: Exception) {
                                sendError(exchange, 500, "Chyba: ${e.message}")
                            }
                        }
                    }
                    path == "/api/backup/import" && exchange.requestMethod.equals("POST", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val tempFile = File(context.cacheDir, "web_import_backup.zip")
                                exchange.requestBody.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                val backupManager = BackupManager(context)
                                withContext(Dispatchers.IO) {
                                    backupManager.restoreDataBackup(tempFile) { }
                                }
                                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Záloha úspěšně nahrána a obnovena\"}")
                                onLogMessage("Obnovena kompletní záloha ZIP přes web")
                            } catch (e: Exception) {
                                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"${e.message}\"}")
                            }
                        }
                    }
                    path == "/api/sync" && exchange.requestMethod.equals("GET", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val catList = try { repository.getAllCatalogItemsDirect() } catch (_: Exception) { emptyList() }
                                val remContainers = try { repository.getAllContainersDirect() } catch (_: Exception) { emptyList() }
                                val remTransports = try { repository.getAllTransportsDirect() } catch (_: Exception) { emptyList() }
                                val remCustomers = try { repository.getAllCustomersDirect() } catch (_: Exception) { emptyList() }
                                val remNabidky = try { repository.getAllNabidkyDirect() } catch (_: Exception) { emptyList() }
                                val remZakazky = try { repository.getAllZakazkyDirect() } catch (_: Exception) { emptyList() }
                                val remFaktury = try { repository.getAllFakturyDirect() } catch (_: Exception) { emptyList() }
                                val remZalohove = try { repository.getAllZalohoveFakturyDirect() } catch (_: Exception) { emptyList() }
                                val remZajemci = try { repository.getAllZajemciDirect() } catch (_: Exception) { emptyList() }
                                val remDeleted = try { repository.getAllDeletedIdsDirect().toList() } catch (_: Exception) { emptyList() }

                                val filesDir = context.filesDir
                                val localConts = try {
                                    val f = File(filesDir, "containers_sync.json")
                                    if (f.exists()) containerListAdapter.fromJson(f.readText()) ?: emptyList() else emptyList()
                                } catch (_: Exception) { emptyList() }

                                // Sloučení a odstranění duplicit podle označení kontejneru
                                val mergedContainersMap = LinkedHashMap<String, ContainerCard>()
                                (localConts + remContainers).forEach { c ->
                                    val key = if (c.designation.isNotBlank()) c.designation.trim().uppercase() else c.id
                                    val ex = mergedContainersMap[key]
                                    if (ex == null || (c.lastModified.takeIf { it > 0 } ?: c.timestamp) >= (ex.lastModified.takeIf { it > 0 } ?: ex.timestamp)) {
                                        mergedContainersMap[key] = c
                                    }
                                }
                                val finalContainers = mergedContainersMap.values.toList()

                                val payload = FullSyncPayload(
                                    customers = if (remCustomers.isNotEmpty()) remCustomers else parseLocalList(File(filesDir, "customers_sync.json")),
                                    nabidky = if (remNabidky.isNotEmpty()) remNabidky else parseLocalList(File(filesDir, "nabidky_sync.json")),
                                    zakazky = if (remZakazky.isNotEmpty()) remZakazky else parseLocalList(File(filesDir, "zakazky_sync.json")),
                                    faktury = if (remFaktury.isNotEmpty()) remFaktury else parseLocalList(File(filesDir, "faktury_sync.json")),
                                    zalohoveFaktury = if (remZalohove.isNotEmpty()) remZalohove else parseLocalList(File(filesDir, "zalohove_faktury_sync.json")),
                                    zajemci = if (remZajemci.isNotEmpty()) remZajemci else parseLocalList(File(filesDir, "zajemci_sync.json")),
                                    polozky = if (catList.isNotEmpty()) catList else parseLocalList(File(filesDir, "polozky_catalog.json")),
                                    catalogItems = if (catList.isNotEmpty()) catList else parseLocalList(File(filesDir, "polozky_catalog.json")),
                                    containers = finalContainers,
                                    transports = if (remTransports.isNotEmpty()) remTransports else parseLocalList(File(filesDir, "transports_sync.json")),
                                    deletedIds = remDeleted
                                )
                                sendJsonResponse(exchange, 200, syncPayloadAdapter.toJson(payload))
                            } catch (_: Exception) {
                                val fallbackJson = com.example.ui.HtmlDownloadManager.buildMobileSyncSnapshotJson(context)
                                sendJsonResponse(exchange, 200, fallbackJson)
                            }
                        }
                    }
                    path == "/api/sync" && exchange.requestMethod.equals("POST", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val body = readRequestBody(exchange)
                                com.example.ui.HtmlDownloadManager.saveWebSyncSnapshotToMobile(context, body)
                                val incoming = syncPayloadAdapter.fromJson(body)
                                if (incoming != null) {
                                    val deletedSet = (try { repository.getAllDeletedIdsDirect() } catch (_: Exception) { emptySet() } + incoming.deletedIds).toSet()
                                    val now = System.currentTimeMillis()
                                    incoming.containers.filter { it.id !in deletedSet }.forEach { raw ->
                                        try {
                                            val cleanDepo = raw.depo.ifBlank { if (raw.status == "DEPO 02") "Depo 2" else "Depo 1" }
                                            val cleanStatus = raw.status.ifBlank { if (cleanDepo == "Depo 2") "DEPO 02" else "DEPO 01" }
                                            val c = raw.copy(
                                                depo = cleanDepo,
                                                status = cleanStatus,
                                                timestamp = if (raw.timestamp > 0) raw.timestamp else now,
                                                lastModified = if (raw.lastModified > 0) raw.lastModified else now
                                            )
                                            if (c.id.isNotBlank()) repository.update(c) else repository.insert(c)
                                        } catch (_: Exception) {}
                                    }
                                    incoming.transports.filter { it.id !in deletedSet }.forEach { t ->
                                        try {
                                            val cleanT = t.copy(depo = t.depo.ifBlank { "Depo 1" })
                                            if (cleanT.id.isNotBlank()) repository.updateTransport(cleanT) else repository.insertTransport(cleanT)
                                        } catch (_: Exception) {}
                                    }
                                    incoming.customers.filter { it.id !in deletedSet }.forEach { try { repository.insertCustomer(it) } catch (_: Exception) {} }
                                    incoming.nabidky.filter { it.id !in deletedSet }.forEach { try { repository.insertNabidka(it, it.text) } catch (_: Exception) {} }
                                    incoming.zakazky.filter { it.id !in deletedSet }.forEach { try { repository.insertZakazka(it, it.lokalita, it.items) } catch (_: Exception) {} }
                                    incoming.faktury.filter { it.id !in deletedSet }.forEach { try { repository.insertFaktura(it) } catch (_: Exception) {} }
                                    incoming.zalohoveFaktury.filter { it.id !in deletedSet }.forEach {
                                        try { repository.insertFaktura(it.copy(typDokladu = "ZALOHOVA_FAKTURA")) } catch (_: Exception) {}
                                    }
                                    incoming.zajemci.filter { it.id !in deletedSet }.forEach { try { repository.insertZajemce(it) } catch (_: Exception) {} }
                                    (incoming.polozky + incoming.catalogItems).distinctBy { it.id }.filter { it.id !in deletedSet }.forEach {
                                        try { repository.upsertCatalogItem(it) } catch (_: Exception) {}
                                    }
                                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Synchronizace dokončena\"}")
                                    onLogMessage("Synchronizována všechna data agend (Kontejnery, Přeprava, Adresář, CN, Zakázky, Faktury, Zájemci, Položky) z PC")
                                } else {
                                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Neplatná data\"}")
                                }
                            } catch (e: Exception) {
                                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"${e.message}\"}")
                            }
                        }
                    }
                    path == "/api/analyze-photo" && exchange.requestMethod.equals("POST", ignoreCase = true) -> {
                        coroutineScope.launch {
                            try {
                                val body = readRequestBody(exchange)
                                val jsonReq = org.json.JSONObject(body)
                                val dataUrl = jsonReq.optString("dataUrl", "")
                                val kind = jsonReq.optString("kind", "container")
                                val hint = jsonReq.optString("hint", "")
                                val cleanBase64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
                                val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp == null) {
                                    sendJsonResponse(exchange, 400, "{}")
                                    return@launch
                                }
                                val resJson = when (kind.lowercase()) {
                                    "customer", "zajemce", "adresar" -> analyzeVizitka(bmp)
                                    "transport", "preprava" -> analyzePreprava(bmp)
                                    "protocol", "zakazka", "doklad" -> analyzeProtocol(bmp)
                                    else -> analyzeContainer(bmp, hint.ifBlank { null })
                                }
                                sendJsonResponse(exchange, 200, resJson)
                                onLogMessage("AI + OCR analýza fotografie z PC Terminálu ($kind)")
                            } catch (e: Exception) {
                                sendJsonResponse(exchange, 500, "{}")
                            }
                        }
                    }
                    else -> {
                        sendError(exchange, 404, "Nenalezeno")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendError(exchange, 500, "Vnitřní chyba: ${e.message}")
            }
        }
    }

    private fun readRequestBody(exchange: HttpExchange): String {
        return exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun sendJsonResponse(exchange: HttpExchange, statusCode: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, statusCode: Int, message: String) {
        val errorJson = "{\"success\":false,\"message\":\"$message\"}"
        sendJsonResponse(exchange, statusCode, errorJson)
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query == null) return map
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    private inline fun <reified T> parseLocalList(file: File): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, T::class.java)
            moshi.adapter<List<T>>(type).fromJson(file.readText()) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
