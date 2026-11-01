package com.example.api

import com.example.data.FakturaRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class MPohodaConfig(
    val companyGuid: String = "501ebfc5-fec2-46db-a9a3-365be2848df1",
    val communicationKey: String = "D94ABB55A16E4C0599E3DCC6660E41A4",
    val apiKey: String = "D94ABB55A16E4C0599E3DCC6660E41A4",
    val autoSyncOnSave: Boolean = true
)

data class MPohodaSyncResponse(
    val success: Boolean,
    val statusCode: Int = 0,
    val id: String = "",
    val code: String = "",
    val message: String = "",
    val errorDetails: String = ""
)

object MPohodaApiService {
    private const val BASE_URL = "https://api.mpohoda.cz/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun toIsoDate(czOrIso: String): String {
        val clean = czOrIso.trim()
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(clean)) return clean
        val m = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$").find(clean)
        if (m != null) {
            val d = m.groupValues[1].padStart(2, '0')
            val mo = m.groupValues[2].padStart(2, '0')
            val y = m.groupValues[3]
            return "$y-$mo-$d"
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    suspend fun testApiKey(apiKey: String): MPohodaSyncResponse =
        testConnection(MPohodaConfig(apiKey = apiKey, communicationKey = apiKey))

    suspend fun testConnection(config: MPohodaConfig): MPohodaSyncResponse = withContext(Dispatchers.IO) {
        val cleanKey = (config.apiKey.ifBlank { config.communicationKey }).trim()
        if (cleanKey.isBlank() && config.communicationKey.isBlank()) {
            return@withContext MPohodaSyncResponse(
                success = false,
                statusCode = 0,
                message = "Komunikační klíč nebo API klíč je prázdný. Zadejte klíč z programu POHODA / mPohoda."
            )
        }

        try {
            val reqBuilder = Request.Builder()
                .url("$BASE_URL/invoices?limit=1")
                .get()
                .header("Authorization", "Bearer $cleanKey")
                .header("Accept", "application/json")
            if (config.companyGuid.isNotBlank()) {
                reqBuilder.header("X-Company-Id", config.companyGuid.trim())
            }
            if (config.communicationKey.isNotBlank()) {
                reqBuilder.header("X-Communication-Key", config.communicationKey.trim())
            }
            val request = reqBuilder.build()

            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            when (code) {
                200, 201 -> {
                    MPohodaSyncResponse(
                        success = true,
                        statusCode = code,
                        message = "Připojení k mPOHODA cloudu je úspěšné! (API klíč je platný)"
                    )
                }
                401, 403 -> {
                    MPohodaSyncResponse(
                        success = false,
                        statusCode = code,
                        message = "Neplatný API klíč mPohody (HTTP $code)",
                        errorDetails = "Zkontrolujte, zda jste správně zkopírovali API klíč z webu app.mpohoda.cz (Nastavení -> API)."
                    )
                }
                else -> {
                    // Try /company endpoint as fallback test
                    val req2 = Request.Builder()
                        .url("$BASE_URL/company")
                        .get()
                        .header("Authorization", "Bearer $cleanKey")
                        .header("Accept", "application/json")
                        .build()
                    val resp2 = client.newCall(req2).execute()
                    if (resp2.isSuccessful) {
                        MPohodaSyncResponse(
                            success = true,
                            statusCode = resp2.code,
                            message = "Připojení k mPOHODA cloudu je úspěšné!"
                        )
                    } else {
                        MPohodaSyncResponse(
                            success = false,
                            statusCode = code,
                            message = "Odpověď mPohody (HTTP $code)",
                            errorDetails = body.take(300)
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            MPohodaSyncResponse(
                success = false,
                statusCode = 0,
                message = "Časový limit vypršel (Timeout)",
                errorDetails = "Cloud mPohody neodpověděl včas. Zkontrolujte připojení k internetu."
            )
        } catch (e: Exception) {
            MPohodaSyncResponse(
                success = false,
                statusCode = 0,
                message = "Chyba připojení k mPohoda cloudu",
                errorDetails = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }

    suspend fun sendInvoice(faktura: FakturaRecord, apiKey: String): MPohodaSyncResponse =
        sendInvoice(faktura, MPohodaConfig(apiKey = apiKey, communicationKey = apiKey))

    suspend fun sendInvoice(faktura: FakturaRecord, config: MPohodaConfig): MPohodaSyncResponse = withContext(Dispatchers.IO) {
        val cleanKey = (config.apiKey.ifBlank { config.communicationKey }).trim()
        if (cleanKey.isBlank() && config.communicationKey.isBlank()) {
            return@withContext MPohodaSyncResponse(
                success = false,
                statusCode = 0,
                message = "Není zadán komunikační ani API klíč mPohody. Přejděte do Nastavení -> mPOHODA CLOUD."
            )
        }

        val isZalohova = faktura.typDokladu == "ZALOHOVA_FAKTURA"
        val endpoint = if (isZalohova) "$BASE_URL/proformaInvoices" else "$BASE_URL/invoices"

        val jsonPayload = buildInvoiceJson(faktura, isZalohova)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toString().toRequestBody(mediaType)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Authorization", "Bearer $cleanKey")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")

        if (config.companyGuid.isNotBlank()) {
            reqBuilder.header("X-Company-Id", config.companyGuid.trim())
        }
        if (config.communicationKey.isNotBlank()) {
            reqBuilder.header("X-Communication-Key", config.communicationKey.trim())
        }

        val request = reqBuilder.build()

        try {
            val response = client.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""

            if (code in 200..204) {
                var createdId = ""
                var createdCode = faktura.cislo
                try {
                    val respObj = JSONObject(responseBody)
                    createdId = respObj.optString("id", "")
                    createdCode = respObj.optString("code", faktura.cislo).ifBlank { faktura.cislo }
                } catch (_: Exception) {}

                MPohodaSyncResponse(
                    success = true,
                    statusCode = code,
                    id = createdId,
                    code = createdCode,
                    message = "Doklad $createdCode byl úspěšně zaevidován v mPohodě!"
                )
            } else if (code == 401 || code == 403) {
                MPohodaSyncResponse(
                    success = false,
                    statusCode = code,
                    message = "Chyba autorizace mPohody (HTTP $code)",
                    errorDetails = "Neplatný nebo expirovaný API klíč. Zkontrolujte nastavení mPohody v aplikaci."
                )
            } else {
                var errDetail = responseBody.take(400)
                try {
                    val errObj = JSONObject(responseBody)
                    val msg = errObj.optString("message", "")
                    val errors = errObj.optJSONArray("errors")
                    if (msg.isNotBlank()) errDetail = msg
                    if (errors != null && errors.length() > 0) {
                        errDetail += " (" + (0 until errors.length()).joinToString("; ") { errors.optString(it) } + ")"
                    }
                } catch (_: Exception) {}

                MPohodaSyncResponse(
                    success = false,
                    statusCode = code,
                    message = "mPohoda vrátila chybu ($code)",
                    errorDetails = errDetail
                )
            }
        } catch (e: Exception) {
            MPohodaSyncResponse(
                success = false,
                statusCode = 0,
                message = "Chyba při odesílání do mPohody",
                errorDetails = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }

    private fun buildInvoiceJson(f: FakturaRecord, isZalohova: Boolean): JSONObject {
        val root = JSONObject()
        val varSym = f.variabilniSymbol.ifBlank { f.cislo.filter { it.isDigit() } }.ifBlank { "1" }
        val dateIso = toIsoDate(f.datumVystaveni)
        val taxDateIso = toIsoDate(f.datumZdanitelnehoPlneni.ifBlank { f.datumVystaveni })
        val dueDateIso = toIsoDate(f.datumSplatnosti.ifBlank { f.datumVystaveni })
        val defaultText = if (f.ucelFaktury == "PRONAJEM") "Pronájem kontejneru ${f.oznaceniKontejneru}" else "Prodej kontejneru ${f.oznaceniKontejneru}"

        root.put("code", f.cislo)
        root.put("varSym", varSym)
        root.put("issueDate", dateIso)
        if (!isZalohova) {
            root.put("taxDate", taxDateIso)
        }
        root.put("dueDate", dueDateIso)
        root.put("text", f.text.ifBlank { defaultText })
        root.put("paymentType", if (f.formaUhrady.contains("Hotov", true)) "cash" else "transfer")

        // Partner
        val partner = JSONObject()
        if (f.firma.isNotBlank()) partner.put("company", f.firma)
        if (f.jmeno.isNotBlank()) partner.put("name", f.jmeno)
        if (f.ic.isNotBlank()) partner.put("ic", f.ic)
        if (f.dic.isNotBlank()) partner.put("dic", f.dic)
        if (f.ulice.isNotBlank()) partner.put("street", f.ulice)
        if (f.telefon.isNotBlank()) partner.put("phone", f.telefon)
        if (f.email.isNotBlank()) partner.put("email", f.email)
        root.put("partner", partner)

        // Note
        val note = "Kontejner: ${f.oznaceniKontejneru} | Středisko: ${f.depo} (${f.stredisko}) | ${f.poznamka}".trim()
        root.put("note", note)

        // Items
        val itemsArray = JSONArray()
        f.items.forEach { item ->
            val itObj = JSONObject()
            itObj.put("code", item.kod.ifBlank { "KONT" })
            itObj.put("text", item.text.ifBlank { "Kontejner" })
            itObj.put("quantity", if (item.mnozstvi > 0) item.mnozstvi else 1.0)
            itObj.put("unit", item.mj.ifBlank { "ks" })
            itObj.put("unitPrice", item.jCena)
            itObj.put("discountPercentage", item.sleva)
            itObj.put("vatRate", if (item.pdp) 0.0 else item.dphPct)
            itemsArray.put(itObj)
        }
        if (f.items.isEmpty()) {
            val itObj = JSONObject()
            itObj.put("code", "KONT")
            itObj.put("text", defaultText)
            itObj.put("quantity", 1.0)
            itObj.put("unit", "ks")
            itObj.put("unitPrice", 0.0)
            itObj.put("vatRate", 21.0)
            itemsArray.put(itObj)
        }
        root.put("items", itemsArray)

        return root
    }
}
