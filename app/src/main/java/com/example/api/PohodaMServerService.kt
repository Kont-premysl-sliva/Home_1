package com.example.api

import android.util.Base64
import com.example.data.CustomerRecord
import com.example.data.FakturaRecord
import com.example.ui.buildPohodaInvoicesXml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class PohodaServerConfig(
    val serverUrl: String = "http://192.168.1.100:4444/xml",
    val ico: String = "25388720",
    val username: String = "Prema",
    val password: String = "",
    val invoiceSeries: String = "",
    val advanceSeries: String = "",
    val timeoutSeconds: Int = 25
)

data class PohodaSyncResult(
    val success: Boolean,
    val statusCode: Int = 0,
    val message: String = "",
    val detailMessage: String = "",
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val rawXmlResponse: String = "",
    val docNumberMapping: Map<String, String> = emptyMap(), // packItemId -> Pohoda number
    val errorList: List<String> = emptyList()
)

object PohodaMServerService {

    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isBlank()) return "http://127.0.0.1:4444/xml"
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "http://$clean"
        }
        val uriWithoutProtocol = clean.substringAfter("://")
        if (!uriWithoutProtocol.contains("/")) {
            clean = "$clean/xml"
        } else if (clean.endsWith("/")) {
            clean = "${clean}xml"
        }
        return clean
    }

    private fun getClient(timeoutSeconds: Int): OkHttpClient {
        val t = timeoutSeconds.coerceIn(5, 120).toLong()
        return OkHttpClient.Builder()
            .connectTimeout(t, TimeUnit.SECONDS)
            .readTimeout(t, TimeUnit.SECONDS)
            .writeTimeout(t, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun escXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    suspend fun executePost(config: PohodaServerConfig, xmlPayload: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeUrl(config.serverUrl)
        val mediaType = "application/xml; charset=utf-8".toMediaType()
        val body = xmlPayload.toByteArray(Charsets.UTF_8).toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(body)
            .header("Content-Type", "application/xml; charset=utf-8")
            .header("Accept", "application/xml, text/xml")

        if (config.username.isNotBlank()) {
            val credentials = "${config.username.trim()}:${config.password}"
            val authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            requestBuilder.header("Authorization", authHeader)
            requestBuilder.header("STW-Authorization", authHeader)
        }

        val client = getClient(config.timeoutSeconds)
        val response = client.newCall(requestBuilder.build()).execute()
        val code = response.code
        val responseBody = response.body?.string() ?: ""
        Pair(code, responseBody)
    }

    suspend fun testConnection(config: PohodaServerConfig): PohodaSyncResult = withContext(Dispatchers.IO) {
        val cleanIco = config.ico.ifBlank { "25388720" }.trim()
        val pingXml = """<?xml version="1.0" encoding="UTF-8"?>
<dat:dataPack id="PING_${System.currentTimeMillis()}" ico="${escXml(cleanIco)}" application="KontejneryOstrava" version="2.0" note="Test spojeni s mServerem" xmlns:dat="http://www.stormware.cz/schema/version_2/data.xsd" xmlns:adb="http://www.stormware.cz/schema/version_2/addressbook.xsd" xmlns:ftr="http://www.stormware.cz/schema/version_2/filter.xsd">
  <dat:dataPackItem id="PING_TEST" version="2.0">
    <adb:addressbookVersion version="2.0">
      <adb:requestAddressBook>
        <ftr:filter>
          <ftr:id>1</ftr:id>
        </ftr:filter>
      </adb:requestAddressBook>
    </adb:addressbookVersion>
  </dat:dataPackItem>
</dat:dataPack>"""

        try {
            val (code, responseBody) = executePost(config, pingXml)
            when (code) {
                200 -> {
                    val isStateOk = responseBody.contains("state=\"ok\"", ignoreCase = true)
                    val hasResponsePack = responseBody.contains("responsePack", ignoreCase = true)
                    if (isStateOk || hasResponsePack) {
                        PohodaSyncResult(
                            success = true,
                            statusCode = 200,
                            message = "Spojení s mServerem proběhlo úspěšně!",
                            detailMessage = "mServer na adrese ${normalizeUrl(config.serverUrl)} odpovídá (HTTP 200 OK). Účetní jednotka IČO $cleanIco je připravena k synchronizaci.",
                            rawXmlResponse = responseBody
                        )
                    } else {
                        val parsedError = parseGeneralError(responseBody)
                        PohodaSyncResult(
                            success = false,
                            statusCode = 200,
                            message = "mServer odpověděl s chybou v datech",
                            detailMessage = parsedError.ifBlank { "mServer vrátil stav jiný než 'ok'. Zkontrolujte IČO a práva uživatele." },
                            rawXmlResponse = responseBody
                        )
                    }
                }
                401, 403 -> {
                    PohodaSyncResult(
                        success = false,
                        statusCode = code,
                        message = "Chyba přihlášení (HTTP $code)",
                        detailMessage = "Nesprávné uživatelské jméno nebo heslo k mServeru. Ověřte nastavení uživatele v Pohodě (Nastavení -> Přístupová práva) a povolení XML komunikace.",
                        rawXmlResponse = responseBody
                    )
                }
                404 -> {
                    PohodaSyncResult(
                        success = false,
                        statusCode = 404,
                        message = "Cesta nebyla nalezena (HTTP 404)",
                        detailMessage = "Adresa ${normalizeUrl(config.serverUrl)} neexistuje. Obvykle je koncový bod v Pohodě zakončen '/xml'.",
                        rawXmlResponse = responseBody
                    )
                }
                else -> {
                    PohodaSyncResult(
                        success = false,
                        statusCode = code,
                        message = "Chyba serveru (HTTP $code)",
                        detailMessage = "mServer vrátil chybový kód $code: ${responseBody.take(300)}",
                        rawXmlResponse = responseBody
                    )
                }
            }
        } catch (e: ConnectException) {
            PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Nelze se spojit se zadanou adresou",
                detailMessage = "Spojení s ${normalizeUrl(config.serverUrl)} bylo odmítnuto. Ujistěte se, že:\n1. V Pohodě je spuštěný mServer na stejném portu.\n2. Telefon i počítač jsou na stejné Wi-Fi / VPN síti.\n3. Ve Windows Firewall je povolen příchozí port mServeru."
            )
        } catch (e: SocketTimeoutException) {
            PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Vypršel časový limit (Timeout)",
                detailMessage = "Server na adrese ${normalizeUrl(config.serverUrl)} neodpověděl do ${config.timeoutSeconds} sekund."
            )
        } catch (e: UnknownHostException) {
            PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Neznámý hostitel / IP adresa",
                detailMessage = "Zadanou IP adresu nebo doménu nelze přeložit: ${e.message}"
            )
        } catch (e: Exception) {
            PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Chyba komunikace s mServerem",
                detailMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }

    suspend fun sendInvoices(config: PohodaServerConfig, invoices: List<FakturaRecord>): PohodaSyncResult = withContext(Dispatchers.IO) {
        if (invoices.isEmpty()) {
            return@withContext PohodaSyncResult(
                success = true,
                message = "Žádné faktury k odeslání",
                syncedCount = 0
            )
        }

        val xml = buildPohodaInvoicesXml(invoices, config.ico)
        try {
            val (code, responseBody) = executePost(config, xml)
            if (code == 200) {
                parseResponsePack(responseBody, invoices.size)
            } else {
                PohodaSyncResult(
                    success = false,
                    statusCode = code,
                    message = "Chyba mServeru při odesílání faktur (HTTP $code)",
                    detailMessage = "Server vrátil kód $code: ${responseBody.take(400)}",
                    rawXmlResponse = responseBody
                )
            }
        } catch (e: Exception) {
            PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Chyba při odesílání faktur do Pohody",
                detailMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }

    fun buildAddressBookXml(customers: List<CustomerRecord>, ico: String): String {
        val cleanIco = ico.ifBlank { "25388720" }
        val itemsXml = customers.mapIndexed { index, cust ->
            val itemId = "ADR_${cust.id.ifBlank { cust.ic.ifBlank { "${index + 1}" } }}"
            """  <dat:dataPackItem id="${escXml(itemId)}" version="2.0">
    <adb:addressbook version="2.0">
      <adb:addressbookHeader>
        <adb:identity>
          <typ:address>
            <typ:company>${escXml(cust.company.ifBlank { cust.name })}</typ:company>
            <typ:name>${escXml(cust.name)}</typ:name>
            <typ:city>${escXml(cust.mesto)}</typ:city>
            <typ:street>${escXml(cust.ulice)}</typ:street>
            <typ:zip>${escXml(cust.psc)}</typ:zip>
            <typ:ico>${escXml(cust.ic)}</typ:ico>
            <typ:dic>${escXml(cust.dic)}</typ:dic>
          </typ:address>
        </adb:identity>
        <adb:phone>${escXml(cust.phone)}</adb:phone>
        <adb:email>${escXml(cust.email)}</adb:email>
        <adb:note>${escXml(cust.message.ifBlank { "Import z mobilní aplikace" })}</adb:note>
      </adb:addressbookHeader>
    </adb:addressbook>
  </dat:dataPackItem>"""
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
<dat:dataPack id="ADR_PACK_${System.currentTimeMillis()}" ico="${escXml(cleanIco)}" application="KontejneryOstravaERP" version="2.0" note="Import Adresare do SW POHODA" xmlns:dat="http://www.stormware.cz/schema/version_2/data.xsd" xmlns:adb="http://www.stormware.cz/schema/version_2/addressbook.xsd" xmlns:typ="http://www.stormware.cz/schema/version_2/type.xsd">
$itemsXml
</dat:dataPack>"""
    }

    suspend fun sendCustomers(config: PohodaServerConfig, customers: List<CustomerRecord>): PohodaSyncResult = withContext(Dispatchers.IO) {
        if (customers.isEmpty()) {
            return@withContext PohodaSyncResult(
                success = true,
                message = "Žádní zákazníci k odeslání",
                syncedCount = 0
            )
        }
        val xml = buildAddressBookXml(customers, config.ico)
        try {
            val (code, responseBody) = executePost(config, xml)
            if (code == 200) {
                parseResponsePack(responseBody, customers.size)
            } else {
                PohodaSyncResult(
                    success = false,
                    statusCode = code,
                    message = "Chyba mServeru při odesílání kontaktů (HTTP $code)",
                    detailMessage = "Server vrátil kód $code: ${responseBody.take(400)}",
                    rawXmlResponse = responseBody
                )
            }
        } catch (e: Exception) {
            PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Chyba při odesílání kontaktů do Pohody",
                detailMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }

    suspend fun fetchAddressBook(config: PohodaServerConfig): Pair<PohodaSyncResult, List<CustomerRecord>> = withContext(Dispatchers.IO) {
        val cleanIco = config.ico.ifBlank { "25388720" }
        val queryXml = """<?xml version="1.0" encoding="UTF-8"?>
<dat:dataPack id="ADR_REQ_${System.currentTimeMillis()}" ico="${escXml(cleanIco)}" application="KontejneryOstravaERP" version="2.0" note="Nacteni adresare z Pohody" xmlns:dat="http://www.stormware.cz/schema/version_2/data.xsd" xmlns:adb="http://www.stormware.cz/schema/version_2/addressbook.xsd" xmlns:ftr="http://www.stormware.cz/schema/version_2/filter.xsd">
  <dat:dataPackItem id="REQ_ALL" version="2.0">
    <adb:addressbookVersion version="2.0">
      <adb:requestAddressBook>
        <ftr:filter/>
      </adb:requestAddressBook>
    </adb:addressbookVersion>
  </dat:dataPackItem>
</dat:dataPack>"""

        try {
            val (code, responseBody) = executePost(config, queryXml)
            if (code == 200) {
                val customers = parseAddressBookItemsFromXml(responseBody)
                val result = PohodaSyncResult(
                    success = true,
                    statusCode = 200,
                    message = "Z Pohody bylo úspěšně staženo ${customers.size} kontaktů.",
                    detailMessage = "Adresář z mServeru byl načten.",
                    syncedCount = customers.size,
                    rawXmlResponse = responseBody
                )
                Pair(result, customers)
            } else {
                val result = PohodaSyncResult(
                    success = false,
                    statusCode = code,
                    message = "Chyba při čtení adresáře z Pohody (HTTP $code)",
                    detailMessage = responseBody.take(300),
                    rawXmlResponse = responseBody
                )
                Pair(result, emptyList())
            }
        } catch (e: Exception) {
            val result = PohodaSyncResult(
                success = false,
                statusCode = 0,
                message = "Chyba při stahování kontaktů z mServeru",
                detailMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
            Pair(result, emptyList())
        }
    }

    private fun parseResponsePack(xml: String, totalExpected: Int): PohodaSyncResult {
        var syncedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        val docMapping = mutableMapOf<String, String>()

        // Split by responsePackItem
        val itemPattern = Pattern.compile("<(?:rsp:)?responsePackItem\\s+([^>]+)>(.*?)</(?:rsp:)?responsePackItem>", Pattern.DOTALL)
        val matcher = itemPattern.matcher(xml)

        while (matcher.find()) {
            val attributes = matcher.group(1) ?: ""
            val itemBody = matcher.group(2) ?: ""

            val idMatch = Regex("""id=["']([^"']+)["']""").find(attributes)
            val stateMatch = Regex("""state=["']([^"']+)["']""").find(attributes)
            val itemId = idMatch?.groupValues?.get(1) ?: "item_${syncedCount + failedCount}"
            val itemState = stateMatch?.groupValues?.get(1) ?: "ok"

            if (itemState.equals("ok", ignoreCase = true)) {
                syncedCount++
                val numMatch = Regex("""<(?:rdc:)?number>([^<]+)</(?:rdc:)?number>""").find(itemBody)
                val idProduceMatch = Regex("""<(?:rdc:)?id>([^<]+)</(?:rdc:)?id>""").find(itemBody)
                val createdDocNum = numMatch?.groupValues?.get(1) ?: idProduceMatch?.groupValues?.get(1) ?: ""
                if (createdDocNum.isNotBlank()) {
                    docMapping[itemId] = createdDocNum
                }
            } else {
                failedCount++
                val noteMatch = Regex("""<(?:rdc:)?note>([^<]+)</(?:rdc:)?note>""").find(itemBody)
                val msgMatch = Regex("""<(?:response:)?message>([^<]+)</(?:response:)?message>""").find(itemBody)
                val errMsg = noteMatch?.groupValues?.get(1) ?: msgMatch?.groupValues?.get(1) ?: "Neznámá chyba položky $itemId"
                errors.add("$itemId: $errMsg")
            }
        }

        if (syncedCount == 0 && failedCount == 0) {
            // Check global state
            val isOk = xml.contains("state=\"ok\"", ignoreCase = true)
            if (isOk) {
                return PohodaSyncResult(
                    success = true,
                    statusCode = 200,
                    message = "Pohoda požadavek úspěšně zpracovala ($totalExpected dokladů)",
                    syncedCount = totalExpected,
                    rawXmlResponse = xml
                )
            } else {
                val err = parseGeneralError(xml)
                return PohodaSyncResult(
                    success = false,
                    statusCode = 200,
                    message = "Pohoda vrátila chybu zpracování",
                    detailMessage = err.ifBlank { "Chyba validace XML v Pohodě." },
                    failedCount = totalExpected,
                    errorList = listOf(err),
                    rawXmlResponse = xml
                )
            }
        }

        val allOk = failedCount == 0
        val summaryMsg = if (allOk) {
            "Všech $syncedCount dokladů bylo úspěšně přeneseno a zaevidováno v Pohodě."
        } else if (syncedCount > 0) {
            "Částečný úspěch: $syncedCount dokladů uloženo, $failedCount dokladů selhalo."
        } else {
            "Žádný doklad nebyl uložen. Pohoda vrátila $failedCount chyb."
        }

        return PohodaSyncResult(
            success = allOk,
            statusCode = 200,
            message = summaryMsg,
            detailMessage = if (errors.isNotEmpty()) errors.joinToString("\n") else "",
            syncedCount = syncedCount,
            failedCount = failedCount,
            docNumberMapping = docMapping,
            errorList = errors,
            rawXmlResponse = xml
        )
    }

    private fun parseGeneralError(xml: String): String {
        val noteMatch = Regex("""<(?:rdc:)?note>([^<]+)</(?:rdc:)?note>""").find(xml)
        if (noteMatch != null) return noteMatch.groupValues[1]
        val messageMatch = Regex("""<(?:rsp:)?message>([^<]+)</(?:rsp:)?message>""").find(xml)
        if (messageMatch != null) return messageMatch.groupValues[1]
        return ""
    }

    private fun parseAddressBookItemsFromXml(xml: String): List<CustomerRecord> {
        val list = mutableListOf<CustomerRecord>()
        val blockPattern = Pattern.compile("<(?:adb:)?addressbook\\s+[^>]*>(.*?)</(?:adb:)?addressbook>", Pattern.DOTALL)
        val matcher = blockPattern.matcher(xml)

        var idx = 0
        while (matcher.find()) {
            val content = matcher.group(1) ?: ""
            idx++
            fun extractTag(tag: String): String {
                val m = Regex("""<(?:typ:|adb:)?$tag>([^<]*)</(?:typ:|adb:)?$tag>""").find(content)
                return m?.groupValues?.get(1)?.trim() ?: ""
            }

            val company = extractTag("company")
            val name = extractTag("name")
            val city = extractTag("city")
            val street = extractTag("street")
            val zip = extractTag("zip")
            val ico = extractTag("ico")
            val dic = extractTag("dic")
            val phone = extractTag("phone")
            val email = extractTag("email")
            val idStr = extractTag("id")

            if (company.isNotBlank() || name.isNotBlank() || ico.isNotBlank()) {
                list.add(
                    CustomerRecord(
                        id = if (idStr.isNotBlank()) "pohoda_$idStr" else "pohoda_${System.currentTimeMillis()}_$idx",
                        company = company,
                        name = name.ifBlank { company },
                        ic = ico,
                        dic = dic,
                        ulice = street,
                        mesto = city,
                        psc = zip,
                        phone = phone,
                        email = email,
                        isAdresar = true,
                        isOrder = false,
                        status = "POHODA",
                        pohodaId = idStr,
                        pohodaSyncedAt = System.currentTimeMillis(),
                        pohodaStatus = "SYNCED"
                    )
                )
            }
        }
        return list
    }
}
