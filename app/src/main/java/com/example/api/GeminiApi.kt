package com.example.api

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Content(val parts: List<Part>, val role: String? = null)

data class Part(val text: String? = null, val inlineData: InlineData? = null)

data class InlineData(val mimeType: String, val data: String)

data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null
)

data class GenerateContentResponse(val candidates: List<Candidate>?)

data class Candidate(val content: Content?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContentPro(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent35(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/gemini-flash-latest:generateContent")
    suspend fun generateContentFlashLatest(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/gemini-3.1-flash-lite-preview:generateContent")
    suspend fun generateContentFlashLite(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

suspend fun callGeminiApi(apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
    var lastError: Exception? = null
    try {
        return RetrofitClient.service.generateContentPro(apiKey, request)
    } catch (e: Exception) {
        Log.w("GeminiApi", "generateContentPro (gemini-3.1-pro-preview) failed: ${e.message}")
        lastError = e
    }
    try {
        return RetrofitClient.service.generateContent35(apiKey, request)
    } catch (e: Exception) {
        Log.w("GeminiApi", "generateContent35 failed: ${e.message}")
        lastError = e
    }
    try {
        return RetrofitClient.service.generateContentFlashLatest(apiKey, request)
    } catch (e: Exception) {
        Log.w("GeminiApi", "generateContentFlashLatest failed: ${e.message}")
        lastError = e
    }
    try {
        return RetrofitClient.service.generateContentFlashLite(apiKey, request)
    } catch (e: Exception) {
        Log.w("GeminiApi", "generateContentFlashLite failed: ${e.message}")
        lastError = e
    }
    throw lastError ?: Exception("Gemini API nedostupné")
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

/**
 * Normalizes any container color string into a single capitalized Czech masculine adjective word
 * (e.g., "Modrý", "Bílý", "Zelený", "Červený", "Šedý", "Černý", "Žlutý", "Oranžový", "Vínový").
 */
fun normalizeContainerColorAdjective(rawColor: String?): String {
    val c = rawColor?.trim().orEmpty().lowercase()
    return when {
        c.contains("modr") || c.contains("blue") -> "Modrý"
        c.contains("bíl") || c.contains("bil") || c.contains("white") -> "Bílý"
        c.contains("zelen") || c.contains("green") -> "Zelený"
        c.contains("červen") || c.contains("cerven") || c.contains("red") -> "Červený"
        c.contains("vínov") || c.contains("vinov") || c.contains("bord") -> "Vínový"
        c.contains("šed") || c.contains("sed") || c.contains("antracit") || c.contains("gray") || c.contains("grey") -> "Šedý"
        c.contains("čern") || c.contains("cern") || c.contains("black") -> "Černý"
        c.contains("žlut") || c.contains("zlut") || c.contains("yellow") -> "Žlutý"
        c.contains("oranž") || c.contains("oranz") || c.contains("orange") -> "Oranžový"
        c.contains("hněd") || c.contains("hned") || c.contains("brown") -> "Hnědý"
        c.contains("béž") || c.contains("bezov") || c.contains("beige") -> "Béžový"
        else -> "Modrý"
    }
}

/**
 * Ensures container numbers always have a hyphen before the check digit.
 * e.g. "OVLU 251501 9" -> "OVLU 251501-9"
 * e.g. "OVLU 2515019" -> "OVLU 251501-9"
 * e.g. "U 251501 9" -> "U 251501-9"
 * e.g. "U 2515019" -> "U 251501-9"
 */
fun formatContainerDesignationWithHyphen(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isBlank()) return ""
    // If it starts with SK or OB, it's an internal number (e.g. "SK 0724", "OB 095645")
    if (s.startsWith("SK", ignoreCase = true) || s.startsWith("OB", ignoreCase = true)) {
        return s.replace(Regex("""^(SK|OB)\s*[-_./ ]*(\d+)""", RegexOption.IGNORE_CASE)) { m ->
            "${m.groupValues[1].uppercase()} ${m.groupValues[2]}"
        }
    }
    // Match [1-4 letters] [optional space] [6 digits] [separator]? [1 check digit]
    val isoWithSep = Regex("""^([A-Za-z]{1,4})\s*[-_ ]?(\d{6})[\s_./]*(\d)$""").find(s)
    if (isoWithSep != null) {
        val p = isoWithSep.groupValues[1].uppercase()
        val d6 = isoWithSep.groupValues[2]
        val c = isoWithSep.groupValues[3]
        return "$p $d6-$c"
    }
    // Match [1-4 letters] [optional space] [7 contiguous digits]
    val iso7Digits = Regex("""^([A-Za-z]{1,4})\s*(\d{6})(\d)$""").find(s)
    if (iso7Digits != null) {
        val p = iso7Digits.groupValues[1].uppercase()
        val d6 = iso7Digits.groupValues[2]
        val c = iso7Digits.groupValues[3]
        return "$p $d6-$c"
    }
    return s
}

/**
 * Formats container description so it is ALWAYS on 2 lines:
 * Line 1: container color in lowercase (e.g. "šedý", "žlutý", "zelený").
 * Line 2: short container type (e.g. "obytný", "skladový", "40 HC", "20 DV").
 */
fun formatContainerShortDescription(
    color: String?,
    type: String?,
    rawDescription: String?
): String {
    val colorWord = normalizeContainerColorAdjective(
        if (!color.isNullOrBlank()) color else rawDescription
    ).lowercase()
    val combined = "${type.orEmpty()} ${rawDescription.orEmpty()}".uppercase()
    val typePart = when {
        combined.contains("OB") || combined.contains("OBYT") -> "obytný"
        combined.contains("SK") || combined.contains("SKLAD") -> "skladový"
        combined.contains("40") && combined.contains("HC") -> "40 HC"
        combined.contains("40") -> "40 DV"
        combined.contains("TV") -> "20 TV"
        combined.contains("20") && combined.contains("HC") -> "20 HC"
        else -> "20 DV"
    }
    return "$colorWord\n$typePart"
}

/**
 * =========================================================================================
 * JEDNOTNÁ METODIKA ZNAČENÍ KONTEJNERŮ A VYTĚŽOVÁNÍ OZNAČENÍ POMOCÍ AI A LOKÁLNÍHO OCR
 * (POUŽÍVÁNA V MOBILNÍ ANDROID I PC / WEBOVÉ APLIKACI)
 * =========================================================================================
 * Tato metodika slouží jako centrální pravidlový a trénovací základ pro umělou inteligenci
 * (Gemini Pro / Flash) i lokální OCR (Google ML Kit / PC OCR).
 *
 * Pravidla metodiky:
 * 1. PRIORITA 1 (NEJVYŠŠÍ PRIORITA - Interní označení OB a SK plus číslo):
 *    - Označení "OB" a "SK" plus číslo mají VŽDY nejvyšší prioritu v označení (mají přednost
 *      i tehdy, pokud je v rohu vrat současně vidět ISO kód).
 *    - Skladové kontejnery (SK):
 *      • Bezpečně načítá číselnou řadu v rozsahu 3 až 8 číslic (např. "SK 6240221" se 7 číslicemi,
 *        "SK 0789", "SK 0724", "SK 26678") -> typ: Skladový kontejner ("SK").
 *    - Obytné kontejnery (OB):
 *      • Rozpoznávání "OB" s číselnou řadou 3 až 8 číslic (např. "OB 8967", "OB 095645")
 *        -> typ: Obytný kontejner ("OB").
 *
 * 2. PRIORITA 2 (Mezinárodní formát ISO 6346 - VŽDY PRVNÍ ŘÁDEK S TEXTEM A ODFILTROVÁNÍ TECHNICKÝCH HODNOT):
 *    - Příklady: "MEDU 311432-9", "CARU 002238-0", "CARU 002266-7", "DSFU 237856-7", "OVLU 251501-9".
 *    - VŽDY SE IDENTIFIKUJE POUZE PRVNÍ (NEJVYŠŠÍ) ŘÁDEK S TEXTEM OZNAČENÍ NA VRATECH!
 *    - Technické hodnoty a jednotky hmotnosti/objemu na nižších řádcích (např. LBS, KGS, MAX. GROSS,
 *      TARE, NET, PAYLOAD, CU.CAP, CUFT, CUM, 22G1, 45G1) se striktně odfiltrují a NIKDY se nesmí
 *      zaměnit za kód vlastníka (nikdy nesmí vzniknout např. "LBS 311432-9" místo "MEDU 311432-9").
 *    - Víceřádkové sestavení z vrat kontejneru: U lodních kontejnerů bývá kód vlastníka
 *      (např. "MEDU", "CARU", "DSFU") na úplně prvním (nejvyšším) řádku a 6místné číslo s kontrolní
 *      číslicí ("311432-9", "002238-0" nebo "002266 7") na řádku přímo pod ním (ještě nad tabulkou
 *      MAX. GROSS / TARE / LBS / KGS). Lokální OCR i AI tyto bloky správně spojují do jednoho plného čísla.
 *    - Sjednocení zápisu: Výsledné označení se vždy zapíše s pomlčkou před kontrolní číslicí
 *      (např. "MEDU 311432-9", "CARU 002238-0", "CARU 002266-7", "DSFU 237856-7").
 *
 * 3. PRIORITA 3 (Částečně zakrytý prefix - ZÁKAZ REKONSTRUKCE):
 *    - Rekonstrukce částečně zakrytého prefixu se NIKDY neprovádí!
 *    - Zobrazí a uloží se striktně POUZE ta písmena, která jsou na fotografii skutečně vidět
 *      (např. "U 251501-9" nebo "LU 251501-9"). Nikdy se nedomýšlí zakrytá písmena podle jiných kontejnerů.
 *
 * 4. PRIORITA 4 (Nečitelné označení - striktně XXX <poslední 2 znaky fotografie>):
 *    - Pokud některý znak nebo celé označení na fotografii skutečně není čitelné, použije se
 *      striktně pravidlo: "XXX <poslední 2 znaky fotografie>" (např. "XXX 01", "XXX 02"),
 *      NIKDY "KONT-XX".
 *
 * 5. AUTOMATICKÉ PŘIOSTŘENÍ, ROZLOŽENÍ NA BODY A ZVÝŠENÍ ROZLIŠENÍ NA POZADÍ:
 *    - Oblast s označením (pravý horní roh vrat a oblast interního štítku SK / OB) se automaticky
 *      na pozadí zvětší na vysoké rozlišení, rozloží na jednotlivé obrazové body (pixely), zvýší se
 *      lokální kontrast a aplikuje se 3x3 konvoluční přiostřovací filtr.
 */
object ContainerOcrMethodology {

    val STANDARD_COLORS_FEMININE = listOf("Bílá", "Žlutá", "Modrá", "Zelená", "Červená", "Černá", "Šedá")

    /**
     * Zakázané technické zkratky a jednotky na vratech kontejneru (2. a další řádky pod označením),
     * které se NIKDY nesmí použít jako prefix kontejneru (např. zabrání záměně "MEDU" za "LBS").
     */
    val FORBIDDEN_TECHNICAL_PREFIXES = setOf(
        "LBS", "KGS", "KG", "LB", "MAX", "MIN", "NET", "TAR", "TARE", "GRS", "GROSS",
        "PAY", "PAYLOAD", "CAP", "CUCAP", "CUB", "CUM", "CUFT", "CBM", "FT", "M3",
        "ISO", "CSC", "ACEP", "RAL", "WT", "WGT", "VOL", "HIGH", "CUBE", "DRY", "VAN",
        "KONT", "XXX", "CONTAINER", "KONTEJNER"
    )

    private val TECHNICAL_LINE_KEYWORDS_REGEX = Regex(
        """\b(?:MAX\.?\s*GROSS|GROSS\s*WT|TARE\s*WT|TARE|NET\s*WT|PAYLOAD|CU\.?\s*CAP|CUBIC|CU\.?\s*FT|CUFT|KGS|LBS)\b""",
        RegexOption.IGNORE_CASE
    )

    fun isForbiddenTechnicalPrefix(prefix: String): Boolean {
        val clean = prefix.trim().uppercase()
        if (clean.isEmpty()) return true
        if (clean in FORBIDDEN_TECHNICAL_PREFIXES) return true
        if (clean.endsWith("LBS") || clean.endsWith("KGS") || clean.startsWith("LBS") || clean.startsWith("KGS")) return true
        return false
    }

    fun isTechnicalSpecificationLine(lineText: String): Boolean {
        val upper = lineText.trim().uppercase()
        if (upper.isEmpty()) return false
        // If the line contains an SK/OB sticker or a valid 4-letter ISO owner code ending in U/J/Z + 6 digits, keep it
        if (Regex("""\b(?:SK|OB)\s*[-_./:\s]*[0-9OQoIlZSB]{3,8}\b""").containsMatchIn(upper)) return false
        if (Regex("""\b[A-Z]{3}[UJZ]\s*[-_ ]?\s*[0-9OQoIlZSB]{6}\b""").containsMatchIn(upper) &&
            !upper.contains("LBS") && !upper.contains("KGS")
        ) {
            return false
        }
        return TECHNICAL_LINE_KEYWORDS_REGEX.containsMatchIn(upper)
    }

    /**
     * Strips technical weight/volume tokens (LBS, KGS, TARE, GROSS, PAYLOAD, etc.) from a string
     * so they can never be paired with the 6-digit container serial number.
     */
    fun stripTechnicalWords(raw: String): String {
        return raw.replace(
            Regex("""\b(?:LBS|KGS|MAX|GROSS|TARE|PAYLOAD|NET|CU\.?\s*CAP|CUFT|CUM|WT|22G1|45G1|42G1)\b""", RegexOption.IGNORE_CASE),
            " "
        ).replace(Regex("""\s+"""), " ").trim()
    }

    val AI_METHODOLOGY_PROMPT = """
Jsi expertní systém pro čtení označení kontejnerů z fotografií podle závazné firemní metodiky:

1. KDE HLEDAT OZNAČENÍ (VŽDY POUZE PRVNÍ NEJVYŠŠÍ ŘÁDEK V PRAVÉM HORNÍM ROHU NEBO ŠTÍTEK SK/OB):
   - Nejprve zkontroluj štítek / samolepku s interním označením začínajícím na 'SK' nebo 'OB' (má nejvyšší prioritu!).
   - Dále detailně prohlédni ÚPLNĚ PRVNÍ (NEJVYŠŠÍ) ŘÁDEK S TEXTEM v pravém horním rohu vrat kontejneru (v přiloženém přiostřeném výřezu s vysokým rozlišením).
   - KRITICKÉ PRAVIDLO PROTI ZÁMĚNĚ ZA VÁHOVÉ ÚDAJE (LBS / KGS):
     • Vždy identifikuj označení kontejneru POUZE z prvního (nejvyššího) řádku v pravém horním rohu vrat (např. 'MEDU 311432-9', 'CARU 002238-0', 'DSFU 237856-7')!
     • Všechny technické hodnoty a jednotky hmotnosti/objemu na nižších řádcích pod označením ('LBS', 'KGS', 'MAX. GROSS', 'TARE', 'NET', 'PAYLOAD', 'CU. CAP.', 'CUFT', 'CUM', '22G1', '45G1') STRIKTNĚ IGNORUJ a ODFILTRUJ!
     • NIKDY nespojuj zkratku 'LBS' ani 'KGS' s číslem kontejneru (např. NIKDY nevracej 'LBS 311432-9' ani 'LBS311432-9', správně je na prvním řádku 'MEDU 311432-9'!).
   - VÍCEŘÁDKOVÉ SESTAVENÍ Z VRAT KONTEJNERU:
     • Pokud je na úplně prvním řádku 4písmenný kód vlastníka končící obvykle na 'U' (např. 'MEDU', 'CARU', 'DSFU', 'CAIU', 'OVLU') a na řádku bezprostředně pod ním (ještě nad tabulkou MAX. GROSS / KGS / LBS) je 6místné číslo s kontrolní číslicí ('311432-9', '002238-0' nebo '002266 7'), spoj tyto dva řádky do jednoho plného označení!

2. PŘESNÁ PRAVIDLA ZÁPISU DO POLE 'designation' (SEŘAZENO PODLE PRIORITY):
   - PRIORITA 1 (Interní označení OB a SK plus číslo - NEJVYŠŠÍ PRIORITA, má přednost i před ISO kódem):
     • Skladové kontejnery ('SK'): 'SK' + 3 až 8 číslic (např. 'SK 6240221' se 7 číslicemi, 'SK 0789', 'SK 0724', 'SK 26678'). Typ nastav na 'Skladový'.
     • Obytné kontejnery ('OB'): 'OB' + 3 až 8 číslic (např. 'OB 8967', 'OB 095645'). Typ nastav na 'Obytný'.
   - PRIORITA 2 (Mezinárodní formát ISO 6346 z prvního řádku v pravém horním rohu):
     • Tvořen 4 písmeny kódu vlastníka (např. 'MEDU', 'CARU', 'DSFU', 'OVLU', 'CAIU') a následně 6 číslicemi + 1 kontrolní číslicí.
     • Ve výsledku kontrolní číslici VŽDY odděl pomlčkou (-)!
     • Příklady:
       - Na 1. řádku 'MEDU 311432 9' -> zapiš 'MEDU 311432-9'.
       - Horní řádek 'CARU', dolní řádek '002238-0' -> zapiš 'CARU 002238-0'.
       - Horní řádek 'CARU', dolní řádek '002266 7' -> zapiš 'CARU 002266-7'.
       - Na 1. řádku 'DSFU 237856 7' -> zapiš 'DSFU 237856-7'.
   - PRIORITA 3 (Částečně zakrytý prefix na prvním řádku - REKONSTRUKCE SE NEPROVÁDÍ!):
     • Pokud je část prefixu na prvním řádku zakrytá (např. samolepkou), NIKDY chybějící písmena nedomýšlej a nerekonstruuj (a nikdy nepoužij 'LBS' nebo 'KGS' z nižších řádků)!
     • Zapiš striktně POUZE ta písmena z prvního řádku, která jsou skutečně vidět (např. 'U 251501-9', 'U 113091-6' nebo 'LU 251501-9').
   - PRIORITA 4 (Nečitelné označení):
     • Pokud označení na fotografii skutečně není čitelné, vrať 'XXX 01' (striktně formát 'XXX <poslední 2 znaky fotografie>', NIKDY nevracej 'KONT-XX').

3. PRAVIDLA PRO TYP KONTEJNERU ('type'):
   - Pokud je označení 'SK ...', typ je 'Skladový'.
   - Pokud je označení 'OB ...', typ je 'Obytný'.
   - Jinak urči typ lodního kontejneru ('20 DV', '40 HC', '40 DV').

4. PRAVIDLA PRO BARVU KONTEJNERU ('color'):
   - Vyber primárně jednu ze 7 základních barev: Bílá (Bílý), Žlutá (Žlutý), Modrá (Modrý), Zelená (Zelený), Červená (Červený), Černá (Černý), Šedá (Šedý).
""".trimIndent()

    /**
     * Fixes typical OCR character confusions inside a numeric sequence.
     */
    fun normalizeOcrDigits(raw: String): String {
        return buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    'O', 'o', 'Q', 'D' -> append('0')
                    'I', 'i', 'l', '|' -> append('1')
                    'Z', 'z' -> append('2')
                    'S', 's' -> append('5')
                    'B' -> append('8')
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * Fixes typical OCR character confusions inside an alphabetic container prefix.
     * Does NOT reconstruct or invent hidden characters — only normalizes visible characters.
     */
    fun normalizeOcrPrefixLetters(raw: String): String {
        return buildString(raw.length) {
            for (ch in raw.uppercase()) {
                when (ch) {
                    '0' -> append('O')
                    '1' -> append('I')
                    '5' -> append('S')
                    '8' -> append('B')
                    in 'A'..'Z' -> append(ch)
                }
            }
        }
    }

    /**
     * Decomposes the cropped region into individual pixels, applies local dynamic range contrast
     * stretching (removing haze/shadows in container corrugations) and applies a 3x3 sharpening
     * convolution kernel so small characters on the first row (e.g. "MEDU 311432-9") and SK/OB
     * stickers have crisp, high-contrast edges.
     */
    fun sharpenAndEnhanceBitmapForOcr(source: Bitmap): Bitmap {
        return try {
            val w = source.width
            val h = source.height
            if (w < 16 || h < 16) return source
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)

            // 1. Find luminance 2nd and 98th percentile for contrast stretching
            val hist = IntArray(256)
            for (px in pixels) {
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                hist[lum.coerceIn(0, 255)]++
            }
            val total = w * h
            val lowTarget = (total * 0.02f).toInt()
            val highTarget = (total * 0.98f).toInt()
            var sum = 0
            var minLum = 0
            for (i in 0..255) {
                sum += hist[i]
                if (sum >= lowTarget) {
                    minLum = i
                    break
                }
            }
            sum = 0
            var maxLum = 255
            for (i in 0..255) {
                sum += hist[i]
                if (sum >= highTarget) {
                    maxLum = i
                    break
                }
            }
            val range = (maxLum - minLum).coerceAtLeast(32)

            val stretched = IntArray(w * h)
            for (idx in pixels.indices) {
                val px = pixels[idx]
                val a = px and -0x1000000
                val r = (((((px shr 16) and 0xFF) - minLum) * 255) / range).coerceIn(0, 255)
                val g = (((((px shr 8) and 0xFF) - minLum) * 255) / range).coerceIn(0, 255)
                val b = ((((px and 0xFF) - minLum) * 255) / range).coerceIn(0, 255)
                stretched[idx] = a or (r shl 16) or (g shl 8) or b
            }

            // 2. Apply 3x3 sharpening kernel: [0, -1, 0; -1, 5, -1; 0, -1, 0]
            val outPixels = stretched.clone()
            for (y in 1 until h - 1) {
                val rowOffset = y * w
                for (x in 1 until w - 1) {
                    val i = rowOffset + x
                    val c = stretched[i]
                    val top = stretched[i - w]
                    val bot = stretched[i + w]
                    val left = stretched[i - 1]
                    val right = stretched[i + 1]

                    val r = ((c shr 16 and 0xFF) * 5 -
                        (top shr 16 and 0xFF) -
                        (bot shr 16 and 0xFF) -
                        (left shr 16 and 0xFF) -
                        (right shr 16 and 0xFF)).coerceIn(0, 255)
                    val g = ((c shr 8 and 0xFF) * 5 -
                        (top shr 8 and 0xFF) -
                        (bot shr 8 and 0xFF) -
                        (left shr 8 and 0xFF) -
                        (right shr 8 and 0xFF)).coerceIn(0, 255)
                    val b = ((c and 0xFF) * 5 -
                        (top and 0xFF) -
                        (bot and 0xFF) -
                        (left and 0xFF) -
                        (right and 0xFF)).coerceIn(0, 255)

                    outPixels[i] = (c and -0x1000000) or (r shl 16) or (g shl 8) or b
                }
            }

            val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            output.setPixels(outPixels, 0, w, 0, 0, w, h)
            output
        } catch (e: Throwable) {
            Log.w("GeminiApi", "sharpenAndEnhanceBitmapForOcr fallback: ${e.message}")
            source
        }
    }

    /**
     * Assembles vertical single-character column markings on container doors
     * (e.g. O-V-L-U-2-5-0-6-0-5-2 -> "OVLU 250605-2" or O-V-L-U-2-1-1-4-2-1-5 -> "OVLU 211421-5").
     */
    fun collapseVerticalSingleCharLines(rawLines: List<String>): String {
        val tokens = rawLines
            .map { it.trim().replace(Regex("""[^A-Za-z0-9]"""), "") }
            .filter { it.length in 1..2 }
        if (tokens.size < 7) return ""
        val joined = tokens.joinToString("")
        val fullM = Regex("""([A-Za-z]{3}[UuJjZz0158])([0-9OQoIlZSB]{6})([0-9OQoIlZSB])""").find(joined)
        if (fullM != null) {
            val p = normalizeOcrPrefixLetters(fullM.groupValues[1])
            val d6 = normalizeOcrDigits(fullM.groupValues[2])
            val chk = normalizeOcrDigits(fullM.groupValues[3])
            if (p.length == 4 && !isForbiddenTechnicalPrefix(p) && d6.all { it.isDigit() } && chk.all { it.isDigit() }) {
                return "$p $d6-$chk"
            }
        }
        val partM = Regex("""([UuJjZz])([0-9OQoIlZSB]{6})([0-9OQoIlZSB])""").find(joined)
        if (partM != null) {
            val p = normalizeOcrPrefixLetters(partM.groupValues[1])
            val d6 = normalizeOcrDigits(partM.groupValues[2])
            val chk = normalizeOcrDigits(partM.groupValues[3])
            if (p.isNotEmpty() && d6.all { it.isDigit() } && chk.all { it.isDigit() }) {
                return "$p $d6-$chk"
            }
        }
        return ""
    }

    /**
     * Extracts a super-resolution, sharpened crop strictly of the 1st (topmost) horizontal door text band
     * (top 2% to 28% of image height, 8% to 98% of width), completely excluding the 2nd row (22G1/45G1)
     * and lower weight tables (M. G. W., MAX. GROSS, TARE, NET, PAYLOAD, KGS, LBS).
     */
    fun cropFirstRowDoorBand(bitmap: Bitmap): Bitmap? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 80 || h < 80) return null
            val cropX = (w * 0.08f).toInt().coerceIn(0, w - 1)
            val cropY = (h * 0.02f).toInt().coerceIn(0, h - 1)
            val cropW = (w * 0.90f).toInt().coerceIn(1, w - cropX)
            val cropH = (h * 0.26f).toInt().coerceIn(1, h - cropY)
            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            val targetMinWidth = 1500
            val scaled = if (cropped.width < targetMinWidth) {
                val scale = (targetMinWidth.toFloat() / cropped.width).coerceAtMost(2.8f)
                Bitmap.createScaledBitmap(
                    cropped,
                    (cropped.width * scale).toInt().coerceAtMost(1900),
                    (cropped.height * scale).toInt().coerceAtMost(1400),
                    true
                )
            } else {
                cropped
            }
            sharpenAndEnhanceBitmapForOcr(scaled)
        } catch (e: Throwable) {
            Log.w("GeminiApi", "cropFirstRowDoorBand failed: ${e.message}")
            null
        }
    }

    /**
     * Extracts a super-resolution, sharpened crop of the top-right corner of the container photo
     * (where the 1st row ISO numbers like "MEDU 311432-9", "CARU 002238-0", "DSFU 237856-7",
     * vertical column markings like "OVLU 250605-2", or partial "U 251501-9" are located),
     * upscaling to 1400px+ width and applying pixel-level sharpening.
     */
    fun cropTopRightCorner(bitmap: Bitmap): Bitmap? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 80 || h < 80) return null
            val cropX = (w * 0.08f).toInt().coerceIn(0, w - 1)
            val cropY = 0
            val cropW = (w - cropX).coerceAtLeast(1)
            val cropH = (h * 0.56f).toInt().coerceIn(1, h)
            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            // Upscale to at least 1400px width (up to 1800px) for super-resolution character clarity
            val targetMinWidth = 1400
            val scaled = if (cropped.width < targetMinWidth) {
                val scale = (targetMinWidth.toFloat() / cropped.width).coerceAtMost(2.8f)
                Bitmap.createScaledBitmap(
                    cropped,
                    (cropped.width * scale).toInt().coerceAtMost(1800),
                    (cropped.height * scale).toInt().coerceAtMost(1800),
                    true
                )
            } else {
                cropped
            }
            sharpenAndEnhanceBitmapForOcr(scaled)
        } catch (e: Throwable) {
            Log.w("GeminiApi", "cropTopRightCorner failed: ${e.message}")
            null
        }
    }

    /**
     * Extracts a super-resolution, sharpened crop of the central/upper container wall/door region
     * where internal "SK" (3-8 digits, e.g. "SK 6240221") and "OB" (e.g. "OB 8967") stickers are placed.
     */
    fun cropStickerRegion(bitmap: Bitmap): Bitmap? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 80 || h < 80) return null
            val cropX = (w * 0.12f).toInt().coerceIn(0, w - 1)
            val cropY = (h * 0.08f).toInt().coerceIn(0, h - 1)
            val cropW = (w * 0.76f).toInt().coerceIn(1, w - cropX)
            val cropH = (h * 0.58f).toInt().coerceIn(1, h - cropY)
            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            val scaled = if (cropped.width < 1200) {
                val scale = (1200f / cropped.width).coerceAtMost(2.4f)
                Bitmap.createScaledBitmap(
                    cropped,
                    (cropped.width * scale).toInt().coerceAtMost(1600),
                    (cropped.height * scale).toInt().coerceAtMost(1600),
                    true
                )
            } else {
                cropped
            }
            sharpenAndEnhanceBitmapForOcr(scaled)
        } catch (e: Throwable) {
            Log.w("GeminiApi", "cropStickerRegion failed: ${e.message}")
            null
        }
    }
}

/**
 * Normalizes container designation strictly conforming to the unified methodology:
 * 1. Priority 1 - Internal stickers (highest priority):
 *    • Storage container (Skladový kontejner): SK followed by 3 to 8 digits (e.g. "SK 6240221", "SK 0789")
 *    • Residential container (Obytný kontejner): OB followed by 3 to 8 digits (e.g. "OB 8967", "OB 095645")
 * 2. Priority 2 - International ISO 6346 format from the 1st row (single-line or multi-line from container doors):
 *    • Full 3-4 letters (e.g. "MEDU 311432-9", "CARU 002238-0", "CARU 002266-7", "DSFU 237856-7")
 *    • Strictly filters out technical weight/volume units on lower lines (LBS, KGS, TARE, GROSS, PAYLOAD, etc.)
 * 3. Priority 3 - Partially covered prefix (NO reconstruction of hidden letters):
 *    • Only visible 1-2 letters + 6 digits + check digit (e.g. "U 251501-9", "LU 251501-9")
 * 4. Priority 4 - Fallback if unreadable / missing:
 *    • Strictly "XXX <poslední 2 znaky fotografie>" (e.g. "XXX 01", "XXX 02"), never "KONT-XX".
 */
fun normalizeContainerDesignation(rawDesig: String?, photoIdentifierHint: String? = null): String {
    val fallbackSuffix: String = run {
        val hint = photoIdentifierHint?.trim().orEmpty()
        val digitsOnly = hint.filter { it.isDigit() }
        when {
            digitsOnly.length >= 2 -> digitsOnly.takeLast(2)
            digitsOnly.length == 1 -> "0$digitsOnly"
            hint.length >= 2 -> hint.takeLast(2).uppercase()
            hint.isNotEmpty() -> "0${hint.uppercase()}"
            else -> "01"
        }
    }

    val raw = rawDesig?.trim().orEmpty()
    if (raw.isBlank()) {
        return "XXX $fallbackSuffix"
    }

    // Never allow KONT-XX or XXX without proper 2-character suffix
    if (raw.uppercase().startsWith("XXX") || raw.uppercase().startsWith("KONT")) {
        val trailingDigits = raw.filter { it.isDigit() }
        val suffix = if (trailingDigits.length >= 2) trailingDigits.takeLast(2) else fallbackSuffix
        return "XXX $suffix"
    }

    // Clean any stray question marks or asterisks around partially read prefixes if valid digits exist;
    // never reconstruct or replace missing prefix letters with guessed letters.
    val cleanedRaw = raw.replace(Regex("""[?*~]+"""), "").trim()
    if (cleanedRaw.isBlank()) {
        return "XXX $fallbackSuffix"
    }

    // PRIORITY 1A: Storage container (Skladový kontejner): SK + 3 to 8 digits (e.g. SK 6240221, SK 0789, SK 0724, SK 26678)
    val skMatch = Regex("""\b(?:SK|SKLADOV[ÝY]|SKLAD)\s*[-_./:\s]*([0-9OQoIlZSB]{3,8})\b""", RegexOption.IGNORE_CASE).find(cleanedRaw)
    if (skMatch != null) {
        val num = ContainerOcrMethodology.normalizeOcrDigits(skMatch.groupValues[1]).filter { it.isDigit() }
        if (num.length in 3..8) {
            val formattedNum = if (num.length < 4) num.padStart(4, '0') else num
            return "SK $formattedNum"
        }
    }

    // PRIORITY 1B: Residential container (Obytný kontejner): OB + 3 to 8 digits (e.g. OB 8967, OB 095645)
    val obMatch = Regex("""\b(?:OB|OBYTN[ÝY]|OBYT)\s*[-_./:\s]*([0-9OQoIlZSB]{3,8})\b""", RegexOption.IGNORE_CASE).find(cleanedRaw)
    if (obMatch != null) {
        val num = ContainerOcrMethodology.normalizeOcrDigits(obMatch.groupValues[1]).filter { it.isDigit() }
        if (num.length in 3..8) {
            val formattedNum = if (num.length < 4) num.padStart(4, '0') else num
            return "OB $formattedNum"
        }
    }

    // Strip technical weight/volume units (LBS, KGS, TARE, GROSS, PAYLOAD, etc.) before matching ISO codes
    // so "LBS311432-9" or "LBS 311432-9" can never be accepted as an owner prefix!
    val cleanedNoTech = ContainerOcrMethodology.stripTechnicalWords(cleanedRaw)

    // PRIORITY 2A-1: Standard 4-letter ISO 6346 owner code ending in U, J, or Z (e.g. MEDU 311432-9, CARU 002238-0, DSFU 237856-7)
    val standardIsoMatches = Regex("""\b([A-Za-z]{3}[UuJjZz0158])[\s\-_./]*([0-9OQoIlZSB]{6})[\s\-_./]*\[?([0-9OQoIlZSB])\]?\b""").findAll(cleanedNoTech)
    for (m in standardIsoMatches) {
        val prefix = ContainerOcrMethodology.normalizeOcrPrefixLetters(m.groupValues[1])
        val num6 = ContainerOcrMethodology.normalizeOcrDigits(m.groupValues[2])
        val check = ContainerOcrMethodology.normalizeOcrDigits(m.groupValues[3])
        if (prefix.length == 4 && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(prefix) &&
            num6.all { it.isDigit() } && check.all { it.isDigit() }
        ) {
            return "$prefix $num6-$check"
        }
    }

    // PRIORITY 2A-2: Full ISO 6346 prefix (3 or 4 letters not in technical blacklist)
    val fullIsoMatches = Regex("""\b([A-Za-z]{3,4})[\s\-_./]*([0-9OQoIlZSB]{6})[\s\-_./]*\[?([0-9OQoIlZSB])\]?\b""").findAll(cleanedNoTech)
    for (fullIsoMatch in fullIsoMatches) {
        val prefix = ContainerOcrMethodology.normalizeOcrPrefixLetters(fullIsoMatch.groupValues[1])
        val num6 = ContainerOcrMethodology.normalizeOcrDigits(fullIsoMatch.groupValues[2])
        val check = ContainerOcrMethodology.normalizeOcrDigits(fullIsoMatch.groupValues[3])
        if (prefix.length in 3..4 && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(prefix) &&
            num6.all { it.isDigit() } && check.all { it.isDigit() }
        ) {
            return "$prefix $num6-$check"
        }
    }

    // PRIORITY 2B: Partially covered prefix by sticker (1 or 2 visible letters + 6 digits + 1 check digit).
    // Strict rule: No reconstruction of covered letters! Show only the letters that are visible.
    val partialIsoMatches = Regex("""\b([A-Za-z]{1,2})[\s\-_./]*([0-9OQoIlZSB]{6})[\s\-_./]*\[?([0-9OQoIlZSB])\]?\b""").findAll(cleanedNoTech)
    for (partialIsoMatch in partialIsoMatches) {
        val prefix = ContainerOcrMethodology.normalizeOcrPrefixLetters(partialIsoMatch.groupValues[1])
        val num6 = ContainerOcrMethodology.normalizeOcrDigits(partialIsoMatch.groupValues[2])
        val check = ContainerOcrMethodology.normalizeOcrDigits(partialIsoMatch.groupValues[3])
        if (prefix.isNotEmpty() && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(prefix) &&
            !prefix.equals("SK", true) && !prefix.equals("OB", true) &&
            num6.all { it.isDigit() } && check.all { it.isDigit() }
        ) {
            return "$prefix $num6-$check"
        }
    }

    // PRIORITY 2C: 1 to 4 letters + 7 contiguous digits (when space/hyphen before check digit was omitted by OCR)
    val contiguous7Matches = Regex("""\b([A-Za-z]{1,4})[\s\-_./]*(\d{6})(\d)\b""").findAll(cleanedNoTech)
    for (contiguous7Match in contiguous7Matches) {
        val prefix = ContainerOcrMethodology.normalizeOcrPrefixLetters(contiguous7Match.groupValues[1])
        if (prefix.isNotEmpty() && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(prefix) &&
            !prefix.equals("SK", ignoreCase = true) && !prefix.equals("OB", ignoreCase = true)
        ) {
            val num6 = contiguous7Match.groupValues[2]
            val check = contiguous7Match.groupValues[3]
            return "$prefix $num6-$check"
        }
    }

    // PRIORITY 2D: 1 to 4 letters + 6 digits (without 7th check digit)
    val isoMatch6List = Regex("""\b([A-Za-z]{1,4})[\s\-_./]*(\d{6})\b""").findAll(cleanedNoTech)
    for (isoMatch6 in isoMatch6List) {
        val prefix = ContainerOcrMethodology.normalizeOcrPrefixLetters(isoMatch6.groupValues[1])
        if (prefix.isNotEmpty() && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(prefix) &&
            !prefix.equals("SK", ignoreCase = true) && !prefix.equals("OB", ignoreCase = true)
        ) {
            val num6 = isoMatch6.groupValues[2]
            return "$prefix $num6"
        }
    }

    // Standalone 4 to 8 digits that represent an SK serial when prefix was cut off on internal sticker
    val pureDigits = cleanedRaw.filter { it.isDigit() }
    if (cleanedRaw.length == pureDigits.length && pureDigits.length in 4..8) {
        return "SK $pureDigits"
    }

    // Fallback: unreadable or non-standard -> strictly XXX + last 2 chars of photo (never KONT-XX)
    return "XXX $fallbackSuffix"
}

fun Bitmap.toBase64(maxDim: Int = 1600, quality: Int = 86): String {
    val outputStream = ByteArrayOutputStream()
    val scaled = if (width > maxDim || height > maxDim) {
        val ratio = maxDim.toFloat() / maxOf(width, height)
        Bitmap.createScaledBitmap(this, (width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1), true)
    } else {
        this
    }
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

/**
 * Intelligent on-device color detector for shipping containers based on HSV color distribution.
 * Maps primarily to the 7 core container colors:
 * Bílá (Bílý), Žlutá (Žlutý), Modrá (Modrý), Zelená (Zelený), Červená (Červený), Černá (Černý), Šedá (Šedý).
 */
fun detectContainerDominantColor(bitmap: Bitmap): String {
    try {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return "Modrý"

        val startX = (width * 0.15f).toInt()
        val endX = (width * 0.85f).toInt()
        val startY = (height * 0.15f).toInt()
        val endY = (height * 0.85f).toInt()

        val stepX = maxOf(1, (endX - startX) / 36)
        val stepY = maxOf(1, (endY - startY) / 36)

        var blueCount = 0
        var redCount = 0
        var greenCount = 0
        var yellowCount = 0
        var grayCount = 0
        var whiteCount = 0
        var blackCount = 0

        val hsv = FloatArray(3)
        for (y in startY until endY step stepY) {
            for (x in startX until endX step stepX) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                // Skip overexposed white sky or deep shadows
                if (v > 0.96f && s < 0.08f) continue
                if (v < 0.06f) continue

                when {
                    v < 0.20f && s < 0.35f -> blackCount++
                    s < 0.15f && v > 0.72f -> whiteCount++
                    s < 0.18f -> grayCount++
                    (h >= 340f || h <= 20f) && s >= 0.20f -> redCount++
                    h in 21f..72f && s >= 0.20f -> yellowCount++
                    h in 73f..165f && s >= 0.18f -> greenCount++
                    h in 166f..265f && s >= 0.18f -> blueCount++
                    else -> grayCount++
                }
            }
        }

        val counts = listOf(
            "Modrý" to blueCount,
            "Červený" to redCount,
            "Zelený" to greenCount,
            "Žlutý" to yellowCount,
            "Šedý" to grayCount,
            "Bílý" to whiteCount,
            "Černý" to blackCount
        )

        val top = counts.maxByOrNull { it.second }
        if (top != null && top.second > 10) {
            return top.first
        }
    } catch (e: Throwable) {
        Log.e("GeminiApi", "Color detection error: ${e.message}")
    }
    return "Modrý"
}

data class LocalContainerScanResult(
    val designation: String = "",
    val type: String = "",
    val color: String = "",
    val description: String = "",
    val spz: String = "",
    val containerBox: List<Int>? = null,
    val designationBox: List<Int>? = null,
    val rawText: String = ""
)

/**
 * Performs on-device Optical Character Recognition using Google ML Kit on:
 * 1) The super-resolution, sharpened top-right corner crop (where the 1st row ISO code
 *    like "MEDU 311432-9", "CARU 002238-0", "DSFU 237856-7", or partial "U 251501-9" is located)
 * 2) The super-resolution, sharpened central/upper sticker region (for "SK 6240221", "OB 8967")
 * 3) The full container bitmap.
 *
 * Key accuracy rules:
 * - Strictly sorts detected lines top-to-bottom by vertical Y coordinate (`boundingBox.top`)
 *   so the VERY FIRST (topmost) line on the container door is evaluated first.
 * - Filters out all technical weight/volume rows on lower lines (`LBS`, `KGS`, `MAX. GROSS`,
 *   `TARE`, `NET`, `PAYLOAD`, `CU.CAP`, `CUFT`, `CUM`) so `LBS` is never mistaken for the owner prefix.
 */
suspend fun extractLocalContainerOcr(bitmap: Bitmap): LocalContainerScanResult = withContext(Dispatchers.Default) {
    var detectedDesig = ""
    var detectedType = ""
    var detectedSpz = ""
    var designationBox: List<Int>? = null
    val rawStringBuilder = StringBuilder()

    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // 1. Process super-resolution sharpened 1st-row door band crop & top-right corner crop
        val firstRowBitmap = ContainerOcrMethodology.cropFirstRowDoorBand(bitmap)
        val firstRowVisionText = if (firstRowBitmap != null) {
            try {
                recognizer.process(InputImage.fromBitmap(firstRowBitmap, 0)).await()
            } catch (_: Throwable) {
                null
            }
        } else null

        val topRightBitmap = ContainerOcrMethodology.cropTopRightCorner(bitmap)
        val cornerVisionText = if (topRightBitmap != null) {
            try {
                recognizer.process(InputImage.fromBitmap(topRightBitmap, 0)).await()
            } catch (_: Throwable) {
                null
            }
        } else null

        // 2. Process super-resolution sharpened sticker crop (for internal SK / OB stickers)
        val stickerBitmap = ContainerOcrMethodology.cropStickerRegion(bitmap)
        val stickerVisionText = if (stickerBitmap != null) {
            try {
                recognizer.process(InputImage.fromBitmap(stickerBitmap, 0)).await()
            } catch (_: Throwable) {
                null
            }
        } else null

        // 3. Process full image
        val fullVisionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        // Sort lines strictly top-to-bottom by Y coordinate (boundingBox.top), then left-to-right (boundingBox.left)
        val firstRowLinesSorted = firstRowVisionText?.textBlocks
            ?.flatMap { it.lines }
            ?.sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            ?: emptyList()

        val cornerLinesSorted = cornerVisionText?.textBlocks
            ?.flatMap { it.lines }
            ?.sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            ?: emptyList()

        val stickerLinesSorted = stickerVisionText?.textBlocks
            ?.flatMap { it.lines }
            ?.sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            ?: emptyList()

        val fullLinesSorted = fullVisionText.textBlocks
            .flatMap { it.lines }
            .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))

        // Keep only non-technical lines for designation extraction (filter out LBS, KGS, TARE, MAX. GROSS, PAYLOAD, M.G.W., etc.)
        val cleanFirstRowLines = firstRowLinesSorted.filterNot { ContainerOcrMethodology.isTechnicalSpecificationLine(it.text) }
        val cleanCornerLines = cornerLinesSorted.filterNot { ContainerOcrMethodology.isTechnicalSpecificationLine(it.text) }
        val cleanStickerLines = stickerLinesSorted.filterNot { ContainerOcrMethodology.isTechnicalSpecificationLine(it.text) }
        val cleanFullLines = fullLinesSorted.filterNot { ContainerOcrMethodology.isTechnicalSpecificationLine(it.text) }

        val allOrderedCleanLines = buildList {
            addAll(cleanFirstRowLines)
            addAll(cleanCornerLines)
            addAll(cleanStickerLines)
            addAll(cleanFullLines)
        }

        val allLinesForTypeAndSpz = buildList {
            addAll(firstRowLinesSorted)
            addAll(cornerLinesSorted)
            addAll(stickerLinesSorted)
            addAll(fullLinesSorted)
        }

        // Build clean raw text from non-technical lines first so Gemini hint and fulltext regex see the 1st row first
        for (line in cleanFirstRowLines) {
            rawStringBuilder.append(line.text.trim()).append("\n")
        }
        for (line in cleanCornerLines) {
            rawStringBuilder.append(line.text.trim()).append("\n")
        }
        for (line in cleanFullLines) {
            rawStringBuilder.append(line.text.trim()).append("\n")
        }
        val allCleanText = ContainerOcrMethodology.stripTechnicalWords(rawStringBuilder.toString())
        val allRawTextForType = allLinesForTypeAndSpz.joinToString("\n") { it.text }

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        fun setBox(rect: android.graphics.Rect?) {
            if (rect != null && designationBox == null && bmpW > 0 && bmpH > 0) {
                val ymin = ((rect.top.toFloat() / bmpH) * 1000).toInt().coerceIn(0, 1000)
                val xmin = ((rect.left.toFloat() / bmpW) * 1000).toInt().coerceIn(0, 1000)
                val ymax = ((rect.bottom.toFloat() / bmpH) * 1000).toInt().coerceIn(0, 1000)
                val xmax = ((rect.right.toFloat() / bmpW) * 1000).toInt().coerceIn(0, 1000)
                designationBox = listOf(ymin, xmin, ymax, xmax)
            }
        }

        // --- PRIORITY 1: Storage container SK (3-8 digits, e.g. SK 6240221, SK 0789) & Residential container OB (e.g. OB 8967) ---
        val skPattern = Regex("""\b(?:SK|SKLADOV[ÝY]|SKLAD)\s*[-_./:\s]*([0-9OQoIlZSB]{3,8})\b""", RegexOption.IGNORE_CASE)
        val obPattern = Regex("""\b(?:OB|OBYTN[ÝY]|OBYT)\s*[-_./:\s]*([0-9OQoIlZSB]{3,8})\b""", RegexOption.IGNORE_CASE)
        for (line in allOrderedCleanLines) {
            val txt = line.text.trim()
            val skM = skPattern.find(txt)
            if (skM != null) {
                val num = ContainerOcrMethodology.normalizeOcrDigits(skM.groupValues[1]).filter { it.isDigit() }
                if (num.length in 3..8) {
                    val formattedNum = if (num.length < 4) num.padStart(4, '0') else num
                    detectedDesig = "SK $formattedNum"
                    detectedType = "SK"
                    setBox(line.boundingBox)
                    break
                }
            }
            val obM = obPattern.find(txt)
            if (obM != null) {
                val num = ContainerOcrMethodology.normalizeOcrDigits(obM.groupValues[1]).filter { it.isDigit() }
                if (num.length in 3..8) {
                    val formattedNum = if (num.length < 4) num.padStart(4, '0') else num
                    detectedDesig = "OB $formattedNum"
                    detectedType = "OB"
                    setBox(line.boundingBox)
                    break
                }
            }
        }

        // Fulltext SK/OB check if split across lines
        if (detectedDesig.isBlank()) {
            val skFull = skPattern.find(allCleanText)
            if (skFull != null) {
                val num = ContainerOcrMethodology.normalizeOcrDigits(skFull.groupValues[1]).filter { it.isDigit() }
                if (num.length in 3..8) {
                    detectedDesig = "SK ${if (num.length < 4) num.padStart(4, '0') else num}"
                    detectedType = "SK"
                }
            } else {
                val obFull = obPattern.find(allCleanText)
                if (obFull != null) {
                    val num = ContainerOcrMethodology.normalizeOcrDigits(obFull.groupValues[1]).filter { it.isDigit() }
                    if (num.length in 3..8) {
                        detectedDesig = "OB ${if (num.length < 4) num.padStart(4, '0') else num}"
                        detectedType = "OB"
                    }
                }
            }
        }

        // --- PRIORITY 2: Topmost row full ISO 6346 match (lines are sorted top-down by Y coordinate, technical lines removed) ---
        if (detectedDesig.isBlank()) {
            val lineIsoFull = Regex("""\b([A-Za-z]{3,4})\s*[-_ ]?\s*([0-9OQoIlZSB]{6})\s*[-_ ]?\s*\[?([0-9OQoIlZSB])\]?\b""")
            for (line in allOrderedCleanLines) {
                val txt = ContainerOcrMethodology.stripTechnicalWords(line.text)
                val m = lineIsoFull.find(txt)
                if (m != null) {
                    val p = ContainerOcrMethodology.normalizeOcrPrefixLetters(m.groupValues[1])
                    val d6 = ContainerOcrMethodology.normalizeOcrDigits(m.groupValues[2])
                    val chk = ContainerOcrMethodology.normalizeOcrDigits(m.groupValues[3])
                    if (p.length in 3..4 && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(p) &&
                        d6.all { it.isDigit() } && chk.all { it.isDigit() }
                    ) {
                        detectedDesig = "$p $d6-$chk"
                        setBox(line.boundingBox)
                        break
                    }
                }
            }
        }

        // --- PRIORITY 3: Topmost multi-line ISO 6346 assembly from container doors ---
        // Evaluates top-down sorted non-technical lines (e.g. Row 1: MEDU / CARU / DSFU, Row 2: 311432-9 / 002238-0 / 002266 7)
        if (detectedDesig.isBlank()) {
            val prefixLineRegex = Regex("""\b([A-Za-z]{3}[UuJjZz0158]|[A-Za-z]{1,4})\b""")
            val digitsLineRegex = Regex("""\b([0-9OQoIlZSB]{6})\s*[-_./ ]?\s*\[?([0-9OQoIlZSB])?\]?\b""")

            val lineSequences = listOf(cleanFirstRowLines, cleanCornerLines, cleanFullLines)
            for (seq in lineSequences) {
                for (i in 0 until seq.size) {
                    val l1 = ContainerOcrMethodology.stripTechnicalWords(seq[i].text).uppercase()
                    if (l1.isBlank()) continue
                    val prefixCandidates = prefixLineRegex.findAll(l1)
                        .map { ContainerOcrMethodology.normalizeOcrPrefixLetters(it.groupValues[1]) }
                        .filter { it.isNotEmpty() && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(it) }
                        .toList()
                    if (prefixCandidates.isEmpty()) continue
                    // Prefer standard 4-letter ISO owner code ending with U/J/Z on the first row (e.g. MEDU, CARU, DSFU)
                    val bestPrefix = prefixCandidates.firstOrNull { it.length == 4 && (it.endsWith("U") || it.endsWith("J") || it.endsWith("Z")) }
                        ?: prefixCandidates.firstOrNull { it.length in 3..4 }
                        ?: prefixCandidates.first()

                    // Check same line or immediately following line (i + 1) for the 6-digit serial + check digit
                    for (j in i..minOf(i + 2, seq.size - 1)) {
                        val l2 = ContainerOcrMethodology.stripTechnicalWords(seq[j].text)
                        val digitsMatch = digitsLineRegex.find(l2)
                        if (digitsMatch != null) {
                            val d6 = ContainerOcrMethodology.normalizeOcrDigits(digitsMatch.groupValues[1])
                            val chk = ContainerOcrMethodology.normalizeOcrDigits(digitsMatch.groupValues[2])
                            // Ensure the 6 digits are not part of a 22G1/45G1 size code
                            if (d6.length == 6 && d6.all { it.isDigit() }) {
                                detectedDesig = if (chk.isNotBlank() && chk.all { it.isDigit() }) {
                                    "$bestPrefix $d6-$chk"
                                } else {
                                    "$bestPrefix $d6"
                                }
                                setBox(seq[i].boundingBox ?: seq[j].boundingBox)
                                break
                            }
                        }
                    }
                    if (detectedDesig.isNotBlank()) break
                }
                if (detectedDesig.isNotBlank()) break
            }
        }

        // --- PRIORITY 3B: Vertical single-character column assembly (e.g. OVLU 250605-2, OVLU 211421-5) ---
        if (detectedDesig.isBlank()) {
            val vertCandidate = ContainerOcrMethodology.collapseVerticalSingleCharLines(cleanFullLines.map { it.text })
                .ifBlank { ContainerOcrMethodology.collapseVerticalSingleCharLines(cleanCornerLines.map { it.text }) }
            if (vertCandidate.isNotBlank()) {
                detectedDesig = vertCandidate
            }
        }

        // --- PRIORITY 4: Single-line partial prefix covered by sticker on the topmost row (only visible 1-2 letters, no reconstruction) ---
        if (detectedDesig.isBlank()) {
            val lineIsoPartial = Regex("""\b([A-Za-z]{1,2})\s*[-_ ]?\s*([0-9OQoIlZSB]{6})\s*[-_ ]?\s*\[?([0-9OQoIlZSB])\]?\b""")
            for (line in allOrderedCleanLines) {
                val txt = ContainerOcrMethodology.stripTechnicalWords(line.text)
                val m = lineIsoPartial.find(txt)
                if (m != null) {
                    val p = ContainerOcrMethodology.normalizeOcrPrefixLetters(m.groupValues[1])
                    val d6 = ContainerOcrMethodology.normalizeOcrDigits(m.groupValues[2])
                    val chk = ContainerOcrMethodology.normalizeOcrDigits(m.groupValues[3])
                    if (p.isNotEmpty() && !ContainerOcrMethodology.isForbiddenTechnicalPrefix(p) &&
                        !p.equals("SK", true) && !p.equals("OB", true) &&
                        d6.all { it.isDigit() } && chk.all { it.isDigit() }
                    ) {
                        detectedDesig = "$p $d6-$chk"
                        setBox(line.boundingBox)
                        break
                    }
                }
            }
        }

        // SPZ detection
        val spzPattern = Regex("""\b([0-9][A-Z0-9][0-9]\s*[0-9]{4})\b""")
        for (line in allLinesForTypeAndSpz) {
            val m = spzPattern.find(line.text)
            if (m != null) {
                detectedSpz = m.groupValues[1]
                break
            }
        }

        // Type detection from overall text & designation
        if (detectedType.isBlank()) {
            val upper = allRawTextForType.uppercase()
            when {
                detectedDesig.startsWith("OB") || upper.contains("OBYTNÝ") || upper.contains("OBYTNY") -> detectedType = "OB"
                detectedDesig.startsWith("SK") || upper.contains("SKLADOVÝ") || upper.contains("SKLADOVY") -> detectedType = "SK"
                upper.contains("40 HC") || upper.contains("40HC") || upper.contains("40' HC") || upper.contains("HIGH CUBE") || upper.contains("45G1") -> detectedType = "40 HC"
                upper.contains("20 DV") || upper.contains("20DV") || upper.contains("20 DC") || upper.contains("20DC") || upper.contains("22G1") -> detectedType = "20 DV"
                upper.contains("40 DV") || upper.contains("40DV") || upper.contains("40 DC") || upper.contains("40DC") || upper.contains("42G1") -> detectedType = "40 DV"
                else -> detectedType = "20 DV"
            }
        }
    } catch (e: Throwable) {
        Log.e("GeminiApi", "Local ML Kit OCR failed: ${e.message}")
    }

    val detectedColor = normalizeContainerColorAdjective(detectContainerDominantColor(bitmap))
    val containerBox = listOf(50, 40, 950, 960)
    val finalType = detectedType.ifBlank { "20 DV" }
    val description = formatContainerShortDescription(detectedColor, finalType, null)
    val normalizedDesig = if (detectedDesig.isNotBlank()) normalizeContainerDesignation(detectedDesig) else ""

    LocalContainerScanResult(
        designation = normalizedDesig,
        type = finalType,
        color = detectedColor,
        description = description,
        spz = detectedSpz,
        containerBox = containerBox,
        designationBox = designationBox,
        rawText = rawStringBuilder.toString()
    )
}

suspend fun analyzeContainerPhoto(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    return@withContext analyzeContainer(bitmap)
}

suspend fun analyzePreprava(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    val localRes = extractLocalContainerOcr(bitmap)
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        try {
            val prompt = """
Z této fotografie vyčti všechny údaje pro přepravu kontejneru. Může to být fotografie samotného kontejneru, nebo předávacího protokolu (papíru).

${ContainerOcrMethodology.AI_METHODOLOGY_PROMPT}

Najdi tyto údaje:
- 'designation': číslo kontejneru podle metodiky výše z prvního řádku v pravém horním rohu nebo štítku SK/OB (např. 'SK 6240221', 'OB 8967', 'MEDU 311432-9', 'CARU 002238-0', nebo při překrytí samolepkou 'U 251501-9'; nikdy 'LBS ...')
- 'date': datum přepravy nebo vrácení, formát DD.MM.YYYY
- 'company': název firmy nebo nájemce
- 'spz': SPZ vozidla
- 'driver': jméno řidiče
- 'containerType': typ kontejneru (např. 'Skladový' pro SK, 'Obytný' pro OB, '20 DV', '40 HC')
- 'color': barva kontejneru (Bílý, Žlutý, Modrý, Zelený, Červený, Černý, Šedý)
- 'description': stručný popis kontejneru (VŽDY začíná barvou kontejneru a má MAXIMÁLNĚ 5 SLOV)
- 'note': poznámky nebo stručný popis kontejneru max 5 slov začínající barvou
- 'rawExtractedText': veškerý text na fotce jako jeden string

Vrať striktně pouze JSON formát bez markdown značek s klíči: 'designation', 'date', 'company', 'spz', 'driver', 'containerType', 'color', 'description', 'note', 'rawExtractedText'. Pokud údaj nenajdeš, nech hodnotu prázdnou string.
""".trimIndent()

            val partsList = mutableListOf(
                Part(text = prompt),
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64(1600, 86)))
            )
            val firstRowCrop = ContainerOcrMethodology.cropFirstRowDoorBand(bitmap)
            if (firstRowCrop != null) {
                partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = firstRowCrop.toBase64(1500, 92))))
            }
            val cornerCrop = ContainerOcrMethodology.cropTopRightCorner(bitmap)
            if (cornerCrop != null) {
                partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = cornerCrop.toBase64(1400, 92))))
            }
            val stickerCrop = ContainerOcrMethodology.cropStickerRegion(bitmap)
            if (stickerCrop != null) {
                partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = stickerCrop.toBase64(1200, 90))))
            }

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = partsList)),
                generationConfig = GenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )
            val response = callGeminiApi(apiKey, request)
            val txt = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!txt.isNullOrBlank()) {
                val clean = txt.trim().removePrefix("```json").removeSuffix("```").trim()
                val json = JSONObject(clean)
                val cColor = normalizeContainerColorAdjective(json.optString("color").ifBlank { localRes.color })
                val geminiRawDesig = json.optString("designation").trim()
                val normGemini = if (geminiRawDesig.isNotBlank()) normalizeContainerDesignation(geminiRawDesig) else ""
                val chosenRawDesig = when {
                    localRes.designation.startsWith("SK ") || localRes.designation.startsWith("OB ") -> localRes.designation
                    normGemini.startsWith("SK ") || normGemini.startsWith("OB ") -> normGemini
                    localRes.designation.length >= 11 && normGemini.length < 11 -> localRes.designation
                    normGemini.isNotBlank() && !normGemini.startsWith("XXX") -> normGemini
                    localRes.designation.isNotBlank() -> localRes.designation
                    else -> ""
                }
                val cType = when {
                    chosenRawDesig.startsWith("SK ") -> "Skladový"
                    chosenRawDesig.startsWith("OB ") -> "Obytný"
                    else -> json.optString("containerType").ifBlank { localRes.type }
                }
                val shortDesc = formatContainerShortDescription(cColor, cType, json.optString("description"))
                json.put("designation", chosenRawDesig)
                json.put("color", cColor)
                json.put("containerType", cType)
                json.put("description", shortDesc)
                if (json.optString("note").isBlank()) {
                    json.put("note", shortDesc)
                }
                if (json.optString("designation").isNotBlank() || json.optString("rawExtractedText").isNotBlank() || shortDesc.isNotBlank()) {
                    return@withContext json.toString()
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiApi", "Cloud analyzePreprava failed, using local OCR: ${e.message}")
        }
    }

    // Local OCR extraction for Preprava
    val datePattern = Regex("""\b([0-3]?[0-9]\.[0-1]?[0-9]\.202[0-9])\b""")
    val foundDate = datePattern.find(localRes.rawText)?.groupValues?.get(1) ?: ""

    val fallbackJson = JSONObject().apply {
        put("designation", localRes.designation)
        put("date", foundDate)
        put("company", "")
        put("spz", localRes.spz)
        put("driver", "")
        put("containerType", localRes.type)
        put("color", localRes.color)
        put("description", localRes.description)
        put("note", localRes.description)
        put("rawExtractedText", localRes.rawText)
    }
    return@withContext fallbackJson.toString()
}

suspend fun analyzeProtocol(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        try {
            val prompt = "Přečti z tohoto předávacího protokolu nebo smlouvy nebo faktury informace o zákazníkovi a prodeji/pronájmu. Vrať striktně pouze JSON formát bez markdown značek s klíči: 'soldCustomerName' (Jméno zákazníka/Firma), 'soldCustomerId' (IČO nebo ID), 'soldPhone' (Telefon), 'soldEmail' (E-mail), 'soldDelivery' (Adresa dopravy nebo dodání). Pokud nějaký údaj chybí, nech ho prázdný."
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64()))
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )
            val response = callGeminiApi(apiKey, request)
            val txt = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!txt.isNullOrBlank()) return@withContext txt
        } catch (e: Exception) {
            Log.w("GeminiApi", "Cloud analyzeProtocol failed: ${e.message}")
        }
    }

    // Fallback: local OCR regex
    var raw = ""
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        raw = recognizer.process(image).await().text
    } catch (_: Throwable) {}

    val ic = Regex("""\b(?:IČO?|Identifikační číslo)[:\s]*([0-9]{8})\b""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)
        ?: Regex("""\b([0-9]{8})\b""").find(raw)?.groupValues?.get(1) ?: ""
    val phone = Regex("""(?:\+?420\s*)?[0-9]{3}\s*[0-9]{3}\s*[0-9]{3}""").find(raw)?.value ?: ""
    val email = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").find(raw)?.value ?: ""

    val fallbackJson = JSONObject().apply {
        put("soldCustomerName", "")
        put("soldCustomerId", ic)
        put("soldPhone", phone)
        put("soldEmail", email)
        put("soldDelivery", "")
    }
    return@withContext fallbackJson.toString()
}

/**
 * Main container photo analysis using the highest paid Gemini Pro model (gemini-3.1-pro-preview)
 * combined with super-resolution sharpened crops (1st-row door band + top-right corner + central sticker crop for SK/OB)
 * and on-device ML Kit OCR & HSV Color Detection fallback.
 */
suspend fun analyzeContainer(bitmap: Bitmap, photoHint: String? = null): String = withContext(Dispatchers.IO) {
    // 1. Run on-device OCR (with super-resolution sharpened crops & top-row Y-sorting) and Color Detection first
    val localRes = extractLocalContainerOcr(bitmap)
    Log.d("GeminiApi", "Local OCR result: desig='${localRes.designation}', type='${localRes.type}', color='${localRes.color}', spz='${localRes.spz}'")

    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        val prompt = """
Vytěž z této fotografie kontejneru (a přiložených přiostřených detailních výřezů 1. řádku s texty na vratech a oblasti štítku SK/OB) všechny informace včetně čísla/označení kontejneru, typu, barvy a stručného popisu.

${ContainerOcrMethodology.AI_METHODOLOGY_PROMPT}

Doplňující nápověda z lokálního OCR skeneru (první řádek po odfiltrování technických jednotek): '${localRes.designation.ifBlank { localRes.rawText.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(60) }}'

KRITICKÉ PRAVIDLO PRO POLE 'description':
- Popis kontejneru v poli 'description' má VŽDY MAXIMÁLNĚ PĚT SLOV (max 5 slov).
- Popis VŽDY ZAČÍNÁ BARVOU KONTEJNERU (např. 'Červený lodní kontejner 20 DV', 'Modrý lodní kontejner 20 DV', 'Bílý obytný kontejner s oknem', 'Zelený skladový kontejner s vraty').

Pokud je na fotce viditelná SPZ vozidla, doplň ji do pole 'spz'.
Dále urči ohraničující obdélníky v rozsahu 0 až 1000: 'containerBox' [ymin, xmin, ymax, xmax] pro celý kontejner a 'designationBox' [ymin, xmin, ymax, xmax] pro samotné označení/číslo kontejneru.
Vrať striktně pouze JSON formát bez markdown značek s klíči: 'designation', 'type', 'color', 'description', 'spz', 'containerBox', 'designationBox'.
""".trimIndent()

        val partsList = mutableListOf(
            Part(text = prompt),
            Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64(1600, 86)))
        )
        val firstRowCrop = ContainerOcrMethodology.cropFirstRowDoorBand(bitmap)
        if (firstRowCrop != null) {
            partsList.add(
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = firstRowCrop.toBase64(1500, 92)))
            )
        }
        val topRightCrop = ContainerOcrMethodology.cropTopRightCorner(bitmap)
        if (topRightCrop != null) {
            partsList.add(
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = topRightCrop.toBase64(1400, 92)))
            )
        }
        val stickerCrop = ContainerOcrMethodology.cropStickerRegion(bitmap)
        if (stickerCrop != null) {
            partsList.add(
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = stickerCrop.toBase64(1200, 90)))
            )
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = partsList)),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                responseMimeType = "application/json"
            )
        )

        try {
            val response = callGeminiApi(apiKey, request)
            val txt = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!txt.isNullOrBlank()) {
                val clean = txt.trim().removePrefix("```json").removeSuffix("```").trim()
                val json = JSONObject(clean)
                val geminiDesig = json.optString("designation", "").trim()
                val geminiColor = json.optString("color", "").trim()
                val geminiType = json.optString("type", "").trim()
                val geminiDesc = json.optString("description", "").trim()

                val normGeminiDesig = if (geminiDesig.isNotBlank()) normalizeContainerDesignation(geminiDesig, photoHint) else ""
                val normLocalDesig = if (localRes.designation.isNotBlank()) normalizeContainerDesignation(localRes.designation, photoHint) else ""

                // Enforce Priority 1 (SK / OB sticker wins over ISO if either detector found it),
                // then Priority 2/3 (non-XXX Gemini designation, then non-XXX Local OCR designation)
                val finalDesig = when {
                    normGeminiDesig.startsWith("SK ") || normGeminiDesig.startsWith("OB ") -> normGeminiDesig
                    normLocalDesig.startsWith("SK ") || normLocalDesig.startsWith("OB ") -> normLocalDesig
                    normGeminiDesig.isNotBlank() && !normGeminiDesig.startsWith("XXX") -> normGeminiDesig
                    normLocalDesig.isNotBlank() && !normLocalDesig.startsWith("XXX") -> normLocalDesig
                    normGeminiDesig.isNotBlank() -> normGeminiDesig
                    else -> normalizeContainerDesignation("", photoHint)
                }

                val finalColor = normalizeContainerColorAdjective(if (geminiColor.isNotBlank()) geminiColor else localRes.color)
                val finalType = when {
                    finalDesig.startsWith("SK ") -> "SK"
                    finalDesig.startsWith("OB ") -> "OB"
                    geminiType.isNotBlank() -> geminiType
                    else -> localRes.type
                }
                val finalDesc = formatContainerShortDescription(
                    finalColor,
                    finalType,
                    if (geminiDesc.isNotBlank()) geminiDesc else localRes.description
                )
                val finalSpz = json.optString("spz", "").ifBlank { localRes.spz }

                val finalObj = JSONObject(clean).apply {
                    put("designation", finalDesig)
                    put("color", finalColor)
                    put("type", finalType)
                    put("description", finalDesc)
                    put("spz", finalSpz)
                    if (!has("containerBox") || isNull("containerBox")) {
                        put("containerBox", JSONArray(localRes.containerBox ?: listOf(50, 40, 950, 960)))
                    }
                    if ((!has("designationBox") || isNull("designationBox")) && localRes.designationBox != null) {
                        put("designationBox", JSONArray(localRes.designationBox))
                    }
                }
                Log.d("GeminiApi", "Merged Cloud Pro+Local result: ${finalObj.toString()}")
                return@withContext finalObj.toString()
            }
        } catch (e: Exception) {
            Log.w("GeminiApi", "Cloud Gemini call failed (${e.message}), using local ML Kit OCR result")
        }
    }

    // Return complete local OCR and Color detection result
    val fallbackDesig = normalizeContainerDesignation(localRes.designation, photoHint)
    val fallbackType = when {
        fallbackDesig.startsWith("SK ") -> "SK"
        fallbackDesig.startsWith("OB ") -> "OB"
        else -> localRes.type
    }
    val localJson = JSONObject().apply {
        put("designation", fallbackDesig)
        put("type", fallbackType)
        put("color", localRes.color)
        put("description", formatContainerShortDescription(localRes.color, fallbackType, localRes.description))
        put("spz", localRes.spz)
        put("containerBox", JSONArray(localRes.containerBox ?: listOf(50, 40, 950, 960)))
        if (localRes.designationBox != null) {
            put("designationBox", JSONArray(localRes.designationBox))
        } else {
            put("designationBox", JSONObject.NULL)
        }
    }
    Log.d("GeminiApi", "Returning local OCR JSON: ${localJson.toString()}")
    return@withContext localJson.toString()
}

suspend fun analyzeVizitka(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        try {
            val prompt = "Toto je fotografie vizitky, dokladu, faktury, objednávky, smlouvy nebo předávacího protokolu. " +
                    "Přečti a vytěž z ní všechny dostupné identifikační a kontaktní údaje: " +
                    "1. Jméno a příjmení osoby (včetně titulů). " +
                    "2. Název firmy / společnosti. " +
                    "3. Telefonní číslo (včetně mezinárodní předvolby). " +
                    "4. E-mailová adresa. " +
                    "5. IČ / IČO (8místné identifikační číslo). " +
                    "6. DIČ (daňové identifikační číslo např. CZ...). " +
                    "7. Ulice a číslo popisné (sídlo/adresa). " +
                    "8. Město / obec. " +
                    "9. PSČ (poštovní směrovací číslo). " +
                    "10. SPZ / RZ vozidla (pokud se vyskytuje). " +
                    "11. Označení nebo kód kontejneru (pokud se vyskytuje). " +
                    "12. Datum (pokud se na dokladu/fotce vyskytuje, ve formátu DD.MM.YYYY). " +
                    "13. Poznámka (pozice/funkce, web, doplňující text). " +
                    "Vrať STRIKTNĚ POUZE validní JSON formát bez jakéhokoliv dalšího textu s klíči: " +
                    "'jmeno', 'firma', 'telefon', 'email', 'ic', 'dic', 'ulice', 'mesto', 'psc', 'spz', 'designation', 'datum', 'poznamka'. " +
                    "Pokud některý údaj chybí, vyplň pro něj prázdný řetězec \"\"."

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64()))
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )

            val response = callGeminiApi(apiKey, request)
            val txt = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!txt.isNullOrBlank()) {
                return@withContext txt.trim().removePrefix("```json").removeSuffix("```").trim()
            }
        } catch (e: Exception) {
            Log.w("GeminiApi", "Cloud analyzeVizitka failed, using local OCR: ${e.message}")
        }
    }

    // Local ML Kit OCR fallback for cards and documents
    var rawText = ""
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        rawText = recognizer.process(image).await().text
    } catch (_: Throwable) {}

    val email = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").find(rawText)?.value ?: ""
    val phone = Regex("""(?:\+?420\s*)?[0-9]{3}\s*[0-9]{3}\s*[0-9]{3}""").find(rawText)?.value ?: ""
    val ic = Regex("""\b(?:IČO?|IČ)[:\s]*([0-9]{8})\b""", RegexOption.IGNORE_CASE).find(rawText)?.groupValues?.get(1)
        ?: Regex("""\b([0-9]{8})\b""").find(rawText)?.groupValues?.get(1) ?: ""
    val dic = Regex("""\b(CZ[0-9]{8,10})\b""", RegexOption.IGNORE_CASE).find(rawText)?.groupValues?.get(1) ?: ""
    val spz = Regex("""\b([0-9][A-Z0-9][0-9]\s*[0-9]{4})\b""").find(rawText)?.groupValues?.get(1) ?: ""
    val psc = Regex("""\b([0-9]{3}\s*[0-9]{2})\b""").find(rawText)?.groupValues?.get(1) ?: ""

    val fallbackJson = JSONObject().apply {
        put("jmeno", "")
        put("firma", "")
        put("telefon", phone)
        put("email", email)
        put("ic", ic)
        put("dic", dic)
        put("ulice", "")
        put("mesto", "")
        put("psc", psc)
        put("spz", spz)
        put("designation", "")
        put("datum", "")
        put("poznamka", rawText.take(150))
    }
    return@withContext fallbackJson.toString()
}
