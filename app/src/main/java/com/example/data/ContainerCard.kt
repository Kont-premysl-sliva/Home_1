package com.example.data

data class RentEvent(
    val tenantName: String,
    val startDate: Long,
    val returnDate: Long? = null,
    val photos: List<String> = emptyList() // up to 3 photos
)


data class ContainerCard(
    val id: String = "",
    val designation: String = "",
    val type: String = "",
    val color: String = "",
    val description: String = "",
    val imageUri: String? = null,
    val extraImages: String = "", // comma-separated
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "DEPO 02", // DEPO 02, REZERV., PLATBA, PRODEJ
    val price: Long = 0,
    val author: String = "Přema",
    val reservedUntil: Long? = null,
    val reservedName: String = "",
    val reservedEmail: String = "",
    val reservedPhone: String = "",
    val depo: String = "Depo 1",
    val poznamka: String = "",
    val spz: String = "",
    val vlastnik: String = "KONTEJNERY",
    val revisionDate: Long? = null,
    val customerCompany: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val sellDate: Long? = null,
    val rentHistory: String = "[]",
    val arrivalDate: Long? = null,
    val soldCustomerId: String = "",
    val soldCustomerName: String = "",
    val soldPhone: String = "",
    val soldEmail: String = "",
    val soldDelivery: String = "",
    val soldTransportId: Int? = null,
    val transporter: String = "",
    val systemNumber: Int = 0
) {
    fun effectiveSystemNumber(): Int {
        if (systemNumber > 0) return systemNumber
        val hashMatch = Regex("""#(\d+)""").find(designation)
        if (hashMatch != null) {
            hashMatch.groupValues[1].toIntOrNull()?.let { return it }
        }
        val digitMatch = Regex("""\b(\d+)\b""").find(designation)
        if (digitMatch != null) {
            digitMatch.groupValues[1].toIntOrNull()?.let { return it }
        }
        return 0
    }
}


data class InventoryRecord(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val dateText: String = "",
    val author: String = "",
    val containerDesignations: String = "", // Comma separated list of active designations
    val depo: String = "Depo 1",
    val status: String = "Nové",
    val summaryNote: String = "",
    val sessionDataJson: String = ""
)


data class CustomerEventRecord(
    val id: String = "",
    val datum: String = "",
    val typ: String = "ZAKÁZKA", // FAKTURA, CN, ZAKÁZKA, OBJEDNÁVKA, PŘEPRAVA, KOMUNIKACE
    val cisloDokladu: String = "",
    val popis: String = "",
    val castka: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomerRecord(
    val id: String = "",
    val name: String = "",
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val ic: String = "",
    val dic: String = "",
    val ulice: String = "",
    val mesto: String = "",
    val psc: String = "",
    val inquiryType: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "NOVÁ",
    val depo: String = "",
    val cisloZakaznika: String = "",
    val isOrder: Boolean = true,
    val isAdresar: Boolean = false,
    val bonita: String = "AAA", // AAA, AA, A, BBB, CCC
    val bonitaDetail: String = "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ",
    val udalosti: List<CustomerEventRecord> = emptyList(),
    val photos: List<String> = emptyList(), // up to 3 photos of customer / premises / documents
    val pohodaId: String = "",
    val pohodaSyncedAt: Long? = null,
    val pohodaStatus: String = "",
    val pohodaError: String = ""
)

data class NabidkaItemRecord(
    val id: String = "",
    val kod: String = "KONT",
    val text: String = "",
    val mnozstvi: Double = 1.0,
    val mj: String = "ks",
    val jCena: Double = 0.0,
    val dphPct: Double = 21.0,
    val sleva: Double = 0.0,
    val pdp: Boolean = false,
    val castkaBez: Double = 0.0,
    val dphVal: Double = 0.0,
    val stav: String = "Předán",
    val komentar: String = "",
    val datumPredani: String = "",
    val datumVraceni: String = "",
    val predkontace: String = "Skladové"
)

data class NabidkaRecord(
    val id: String = "",
    val cislo: String = "",
    val depo: String = "Depo 1",
    val datum: String = "",
    val platnost: String = "",
    val ic: String = "",
    val dic: String = "",
    val firma: String = "",
    val jmeno: String = "",
    val telefon: String = "",
    val email: String = "",
    val ulice: String = "",
    val text: String = "",
    val zdrojObjednavkaId: String = "",
    val adresarId: String = "",
    val status: String = "NOVÁ",
    val items: List<NabidkaItemRecord> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class ZakazkaRecord(
    val id: String = "",
    val cisloZakazky: String = "",
    val datumCasZakazky: String = "",
    val cisloNabidky: String = "",
    val cisloObjednavky: String = "",
    val depo: String = "Depo 1",
    val stredisko: String = "Pronájem", // Pronájem, Prodej, Ostatní, nebo vlastní
    val prepravaInfo: String = "",
    val ic: String = "",
    val dic: String = "",
    val ulice: String = "",
    val firma: String = "",
    val vyrizuje: String = "",
    val email: String = "",
    val telefon: String = "",
    val lokalita: String = "",
    val osobaKPrevzeti: String = "",
    val kontaktPrevzeti: String = "",
    val pocetOB: String = "0",
    val pocetSK: String = "0",
    val pocetSA: String = "0",
    val pocetMA: String = "0",
    val pocetZI: String = "0",
    val pocetST: String = "0",
    val pocetSK2: String = "0",
    val pocetSA2: String = "0",
    val poznamkaZObjednavky: String = "",
    val splatnost: String = "14",
    val obdobiOd: String = "",
    val obdobiDo: String = "",
    val datumDodano: String = "",
    val datumVraceno: String = "",
    val preprava1: String = "",
    val zpet1: String = "",
    val preprava2: String = "",
    val zpet2: String = "",
    val cenovaNabidkaCislo: String = "",
    val protokolTam: String = "",
    val smlouvaCislo: String = "",
    val protokolZpet: String = "",
    val kauceDatum: String = "",
    val kauceCastka: String = "",
    val kauceVraceni: String = "",
    val kauceVraceniCastka: String = "",
    val interniPoznamka: String = "",
    val photos: List<String> = emptyList(),
    val fakturaceDatum: String = "",
    val fakturaceObdobi: String = "",
    val zdrojObjednavkaId: String = "",
    val zdrojNabidkaId: String = "",
    val adresarId: String = "",
    val status: String = "NOVÁ",
    val items: List<NabidkaItemRecord> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class CatalogItemRecord(
    val id: String = "",
    val sekce: String = "KONTEJNERY", // "KONTEJNERY" nebo "VYBAVENÍ"
    val kategorie: String = "Obytné kontejnery", // "Obytné kontejnery", "Skladové kontejnery", "Skladové námořní kontejnery", "Speciální kontejnery", "Vybavení"
    val oznaceni: String = "001",
    val nazev: String = "",
    val pocet: Double = 1.0,
    val cenaBezDph: Double = 0.0,
    val dphPct: Double = 21.0, // 21.0 nebo 5.0
    val slevaPct: Double = 0.0,
    val celkemBezDph: Double = 0.0,
    val celkemSDph: Double = 0.0,
    val poradi: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

data class TransportContainerItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val designation: String = "",
    val containerType: String = "OB",
    val photos: List<String> = emptyList(),
    val equipment: List<String> = listOf("", "", "", "")
)

data class TransportRecord(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "čeká", // "čeká", "vyřízeno", "zrušeno", "HOTOVO"
    val designation: String = "",
    val dateText: String = "",
    val company: String = "",
    val spz: String = "",
    val driver: String = "",
    val containerType: String = "",
    val note: String = "",
    val imageUri: String? = null,
    val containerImageUri: String? = null,
    val extraImages: String = "",
    val rawExtractedText: String = "", // In case we want to see everything
    val depo: String = "Depo 1",
    val customerName: String = "",
    val customerCompany: String = "",
    val customerIc: String = "",
    val customerDic: String = "",
    val customerAddress: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val customerInitials: String = "",
    val customerSource: String = "", // "OBJEDNÁVKA", "ZAKÁZKA", "RUČNĚ"
    val sourceOrderId: String = "",
    val sourceZakazkaId: String = "",
    val cisloDokladuZdroj: String = "",
    val destinationLocation: String = "",
    val containersJson: String = "",
    val deliveryPhotos: String = "",
    val isVraceni: Boolean = false,
    val prevzalaOsoba: String = "",
    val prevzalaMobil: String = "",
    val internalNote: String = "",
    val transportDate: String = ""
)

fun normalizeCompanyIco(ic: String): String {
    return ic.replace(Regex("[^0-9A-Za-z]"), "").trim()
}

fun parseTransportDateToEpoch(dateText: String, fallbackTimestamp: Long = 0L): Long =
    parseTransportDateToMillis(dateText, fallbackTimestamp)

fun parseTransportDateToMillis(dateText: String, fallbackTimestamp: Long = 0L): Long {
    val clean = dateText.trim()
        .replace(Regex("\\s*\\.\\s*"), ".")
        .replace(Regex("\\s+"), " ")
    if (clean.isBlank()) return fallbackTimestamp
    val formats = listOf(
        "dd.MM.yyyy HH:mm",
        "d.M.yyyy HH:mm",
        "dd.MM.yyyy",
        "d.M.yyyy",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "d.M.yy"
    )
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault()).apply {
                isLenient = false
            }
            val parsed = sdf.parse(clean)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    val dateToken = Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|\\d{4}-\\d{2}-\\d{2})").find(clean)?.value
    if (dateToken != null && dateToken != clean) {
        for (fmt in listOf("dd.MM.yyyy", "d.M.yyyy", "yyyy-MM-dd", "d.M.yy")) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault()).apply {
                    isLenient = false
                }
                val parsed = sdf.parse(dateToken)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
    }
    return fallbackTimestamp
}

fun computeCustomerInitials(name: String, company: String = ""): String {
    val cleanName = name
        .replace(Regex("(?i)\\b(ing|mgr|bc|mudr|judr|phdr|rndr|paeddr|doc|prof|dis|phd|csc|mba)\\.?\\s*"), " ")
        .trim()
    val source = if (cleanName.isNotBlank()) {
        cleanName
    } else {
        company
            .replace(Regex("(?i)\\b(s\\.\\s*r\\.\\s*o\\.|spol\\.\\s*s\\s*r\\.\\s*o\\.|a\\.\\s*s\\.|v\\.\\s*o\\.\\s*s\\.|k\\.\\s*s\\.|s\\.r\\.o|a\\.s)\\b"), " ")
            .trim()
    }
    if (source.isBlank()) return ""
    val parts = source
        .split(Regex("[\\s,\\-/]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    if (parts.isEmpty()) return ""
    return parts.take(3).joinToString(" ") { "${it.first().uppercaseChar()}." }
}

data class FakturaRecord(
    val id: String = "",
    val cislo: String = "",
    val variabilniSymbol: String = "",
    val typDokladu: String = "FAKTURA", // "FAKTURA" nebo "ZALOHOVA_FAKTURA"
    val ucelFaktury: String = "PRODEJ", // "PRODEJ" (za Prodej kontejneru) nebo "PRONAJEM" (za Pronájem kontejneru)
    val depo: String = "Depo 1",
    val stredisko: String = "Prodej",
    val datumVystaveni: String = "",
    val datumZdanitelnehoPlneni: String = "",
    val datumSplatnosti: String = "",
    val formaUhrady: String = "Příkazem",
    val cisloUctu: String = "2101234567/2010",
    val iban: String = "CZ6520100000002101234567",
    val ic: String = "",
    val dic: String = "",
    val firma: String = "",
    val jmeno: String = "",
    val ulice: String = "",
    val telefon: String = "",
    val email: String = "",
    val oznaceniKontejneru: String = "",
    val typKontejneru: String = "",
    val obdobiPronajmu: String = "",
    val mistoDodani: String = "",
    val cisloZakazky: String = "",
    val cisloNabidky: String = "",
    val zdrojNabidkaId: String = "",
    val zdrojZakazkaId: String = "",
    val zdrojKontejnerId: String = "",
    val adresarId: String = "",
    val stavUhrady: String = "Neuhrazeno",
    val text: String = "",
    val poznamka: String = "",
    val items: List<NabidkaItemRecord> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val pohodaId: String = "",
    val pohodaSyncedAt: Long? = null,
    val pohodaStatus: String = "",
    val pohodaError: String = ""
)

data class ZajemceRecord(
    val id: String = "",
    val cisloZajemce: String = "",
    val depo: String = "Depo 1",
    val jmeno: String = "",
    val firma: String = "",
    val telefon: String = "",
    val email: String = "",
    val typKontejneru: String = "OB 6 - Obytný 6m",
    val zamer: String = "Nákup",
    val rozpocet: String = "",
    val status: String = "AKTIVNÍ",
    val poznamka: String = "",
    val vizitkaUri: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val photos: List<String> = emptyList()
)


