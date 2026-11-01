package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.*
import com.example.api.PohodaSyncResult
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

private fun fmtKc(v: Double): String {
    val nf = NumberFormat.getInstance(Locale.forLanguageTag("cs-CZ"))
    return "${nf.format(v.roundToLong())} Kč"
}

@Composable
fun FakturaceTabContent(
    isZalohovaMode: Boolean,
    fakturyList: List<FakturaRecord>,
    allContainers: List<ContainerCard>,
    adresarList: List<CustomerRecord>,
    catalogItems: List<CatalogItemRecord>,
    activeDepo: String,
    pohodaIco: String,
    viewModel: MainViewModel,
    onSwitchToRegularFaktury: () -> Unit = {}
) {
    val context = LocalContext.current
    var ucelFilter by remember { mutableStateOf("VŠE") } // "VŠE", "PRODEJ", "PRONAJEM"
    var searchQuery by remember { mutableStateOf("") }
    var editingFaktura by remember { mutableStateOf<FakturaRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<FakturaRecord?>(null) }
    var pohodaTargetList by remember { mutableStateOf<List<FakturaRecord>?>(null) }
    var showMPohodaSettingsInFakturace by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filteredList = remember(fakturyList, ucelFilter, searchQuery) {
        fakturyList.filter { rec ->
            val matchUcel = when (ucelFilter) {
                "PRODEJ" -> rec.ucelFaktury == "PRODEJ"
                "PRONAJEM" -> rec.ucelFaktury == "PRONAJEM"
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchQ = q.isEmpty() ||
                rec.cislo.lowercase().contains(q) ||
                rec.firma.lowercase().contains(q) ||
                rec.jmeno.lowercase().contains(q) ||
                rec.oznaceniKontejneru.lowercase().contains(q) ||
                rec.text.lowercase().contains(q)
            matchUcel && matchQ
        }
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        DoubleConfirmDeleteDialog(
            itemTitle = "${if (isZalohovaMode) "Zálohová faktura" else "Faktura"} č. ${target.cislo}",
            itemDescription = "Odběratel: ${target.firma.ifBlank { target.jmeno }} • Kontejner: ${target.oznaceniKontejneru}",
            onDismiss = { deleteTarget = null },
            onConfirmedDelete = {
                viewModel.deleteFaktura(target.id, isZalohovaMode)
                Toast.makeText(context, "Doklad ${target.cislo} byl smazán", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (pohodaTargetList != null) {
        PohodaInvoiceExportDialog(
            invoices = pohodaTargetList!!,
            defaultIco = pohodaIco.ifBlank { "25388720" },
            viewModel = viewModel,
            onDismiss = { pohodaTargetList = null }
        )
    }

    if (showMPohodaSettingsInFakturace) {
        MPohodaSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showMPohodaSettingsInFakturace = false }
        )
    }

    if (editingFaktura != null) {
        FakturaEditDialog(
            initial = editingFaktura!!,
            allContainers = allContainers,
            adresarList = adresarList,
            catalogItems = catalogItems,
            viewModel = viewModel,
            onDismiss = { editingFaktura = null },
            onSave = { saved ->
                viewModel.saveFaktura(saved)
                editingFaktura = null
                Toast.makeText(context, "Doklad ${saved.cislo} uložen", Toast.LENGTH_SHORT).show()
            },
            onExportPohoda = { rec ->
                viewModel.saveFaktura(rec)
                pohodaTargetList = listOf(rec)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header card with quick creation for 1. Prodej kontejneru and 2. Pronájem kontejneru + Pohoda export
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isZalohovaMode) Color(0xFFFFFBEB) else Color(0xFFF0F9FF)
            ),
            border = BorderStroke(1.5.dp, if (isZalohovaMode) Color(0xFFD97706) else Color(0xFF0284C7))
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isZalohovaMode) "ZÁLOHOVÉ" else "FAKTURY",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (isZalohovaMode) Color(0xFF92400E) else Color(0xFF0F3D5E)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                if (viewModel.mpohodaApiKey.value.isBlank()) {
                                    showMPohodaSettingsInFakturace = true
                                } else if (filteredList.isEmpty()) {
                                    Toast.makeText(context, "Žádné doklady k odeslání do mPohody", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Odesílám ${filteredList.size} dokladů do mPohody...", Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        val (success, errors) = viewModel.sendInvoicesBatchToMPohoda(filteredList)
                                        Toast.makeText(
                                            context,
                                            "mPohoda: Odesláno $success dokladů${if (errors > 0) ", selhalo: $errors" else ""}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("mPOHODA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Button(
                            onClick = {
                                if (filteredList.isEmpty()) {
                                    Toast.makeText(context, "Žádné doklady k exportu do Pohody", Toast.LENGTH_SHORT).show()
                                } else {
                                    pohodaTargetList = filteredList
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("XML", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Two main creation buttons: 1. Za Prodej kontejneru | 2. Za Pronájem kontejneru
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            val today = sdf.format(Date())
                            val due = sdf.format(Date(System.currentTimeMillis() + 14L * 86400000L))
                            val num = viewModel.getNextFakturaNumber(isZalohovaMode)
                            editingFaktura = FakturaRecord(
                                cislo = num,
                                variabilniSymbol = num.filter { it.isDigit() },
                                typDokladu = if (isZalohovaMode) "ZALOHOVA_FAKTURA" else "FAKTURA",
                                ucelFaktury = "PRODEJ",
                                depo = activeDepo,
                                stredisko = "Prodej",
                                datumVystaveni = today,
                                datumZdanitelnehoPlneni = today,
                                datumSplatnosti = due,
                                text = if (isZalohovaMode) "Zálohová faktura za prodej kontejneru" else "Faktura za prodej kontejneru",
                                items = listOf(
                                    NabidkaItemRecord(
                                        id = "fi_${System.currentTimeMillis()}",
                                        kod = "PRODEJ",
                                        text = "Prodej kontejneru",
                                        mnozstvi = 1.0,
                                        mj = "ks",
                                        jCena = 65000.0,
                                        dphPct = 21.0,
                                        castkaBez = 65000.0,
                                        dphVal = 13650.0
                                    )
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "PRODEJ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Button(
                        onClick = {
                            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            val today = sdf.format(Date())
                            val due = sdf.format(Date(System.currentTimeMillis() + 14L * 86400000L))
                            val num = viewModel.getNextFakturaNumber(isZalohovaMode)
                            editingFaktura = FakturaRecord(
                                cislo = num,
                                variabilniSymbol = num.filter { it.isDigit() },
                                typDokladu = if (isZalohovaMode) "ZALOHOVA_FAKTURA" else "FAKTURA",
                                ucelFaktury = "PRONAJEM",
                                depo = activeDepo,
                                stredisko = "Pronájem",
                                datumVystaveni = today,
                                datumZdanitelnehoPlneni = today,
                                datumSplatnosti = due,
                                text = if (isZalohovaMode) "Zálohová faktura za pronájem kontejneru" else "Faktura za pronájem kontejneru",
                                items = listOf(
                                    NabidkaItemRecord(
                                        id = "fi_${System.currentTimeMillis()}",
                                        kod = "PRON",
                                        text = "Pronájem kontejneru (1 měsíc)",
                                        mnozstvi = 1.0,
                                        mj = "měs.",
                                        jCena = 3500.0,
                                        dphPct = 21.0,
                                        castkaBez = 3500.0,
                                        dphVal = 735.0
                                    )
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "PRONÁJEM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Filter chips + search
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "VŠE" to "Vše (${fakturyList.size})",
                        "PRODEJ" to "Prodej (${fakturyList.count { it.ucelFaktury == "PRODEJ" }})",
                        "PRONAJEM" to "Pronájem (${fakturyList.count { it.ucelFaktury == "PRONAJEM" }})"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = ucelFilter == key,
                            onClick = { ucelFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Hledat...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (filteredList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Žádné záznamy",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { fa ->
                    val sumBez = fa.items.sumOf { it.castkaBez }
                    val sumS = fa.items.sumOf { it.castkaBez + it.dphVal }
                    val isPronajem = fa.ucelFaktury == "PRONAJEM"
                    val isDepo2 = fa.depo == "Depo 2"
                    val headerCol = if (isZalohovaMode) Color(0xFFB45309) else if (isPronajem) Color(0xFF047857) else Color(0xFF1B5E8B)
                    val borderCol = if (isPronajem) Color(0xFF047857) else Color(0xFF1B5E8B)

                    Card(
                        onClick = { editingFaktura = fa },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isZalohovaMode) Color(0xFFFFFBEB) else Color(0xFFF0F9FF)),
                        border = BorderStroke(2.dp, borderCol)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(headerCol)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AgendaLetterIconBadge(
                                        letter = if (isZalohovaMode) "ZF" else "FA",
                                        textColor = headerCol
                                    )
                                    Text(
                                        text = "${fa.cislo.ifBlank { "FA" }} • ${if (isPronajem) "PRONÁJEM" else "PRODEJ"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SingleLineDepoBadge(
                                        depoText = fa.depo.ifBlank { "Depo 1" },
                                        badgeColor = if (isDepo2) Color(0xFFE65100) else Color(0xFF0D47A1),
                                        onDepoSelected = { newDepo ->
                                            viewModel.saveFaktura(fa.copy(depo = newDepo))
                                        }
                                    )
                                    val stavCol = when (fa.stavUhrady) {
                                        "Uhrazeno", "Vyúčtováno" -> Color(0xFF15803D)
                                        "Storno" -> Color(0xFFB91C1C)
                                        "Částečně uhrazeno" -> Color(0xFF0284C7)
                                        else -> Color(0xFFD97706)
                                    }
                                    StatusDropdownBadge(
                                        currentStatus = fa.stavUhrady.ifBlank { "Neuhrazeno" },
                                        options = listOf("Neuhrazeno", "Uhrazeno", "Částečně uhrazeno", "Vyúčtováno", "Storno"),
                                        customColor = stavCol,
                                        onStatusSelected = { newStav ->
                                            viewModel.saveFaktura(fa.copy(stavUhrady = newStav))
                                        }
                                    )
                                }
                            }

                            // Zjednodušený náhled faktury přesně na 3 řádky
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fa.firma.ifBlank { fa.jmeno }.ifBlank { "Odběratel neuveden" },
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, borderCol.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = fmtKc(sumS),
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = headerCol,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val contactLine = listOf(
                                    if (fa.ic.isNotBlank()) "IČ: ${fa.ic}" else "",
                                    fa.telefon,
                                    fa.email,
                                    "Splatnost: ${fa.datumSplatnosti}"
                                ).filter { it.isNotBlank() }.joinToString(" • ")
                                Text(
                                    text = "Kontakt: $contactLine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val zamerLine = listOf(
                                    if (fa.oznaceniKontejneru.isNotBlank()) "Kontejner: ${fa.oznaceniKontejneru}" else "",
                                    fa.text,
                                    "Bez DPH: ${fmtKc(sumBez)}"
                                ).filter { it.isNotBlank() }.joinToString(" • ")
                                Text(
                                    text = "Záměr: $zamerLine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = borderCol,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (fa.pohodaStatus == "MPOHODA") {
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFFE0F2FE),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, Color(0xFF7DD3FC))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(11.dp))
                                                Text(
                                                    text = "mPOHODA: ${fa.pohodaId.ifBlank { "OK" }}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0369A1)
                                                )
                                            }
                                        }
                                    }
                                } else if (fa.pohodaStatus == "SYNCED" || fa.pohodaId.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFFDCFCE7),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(11.dp))
                                                Text(
                                                    text = "POHODA: ${fa.pohodaId.ifBlank { "OK" }}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }
                                    }
                                } else if (fa.pohodaStatus == "ERROR") {
                                    Surface(
                                        color = Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFECACA))
                                    ) {
                                        Text(
                                            text = "⚠ POHODA CHYBA",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakturaEditDialog(
    initial: FakturaRecord,
    allContainers: List<ContainerCard>,
    adresarList: List<CustomerRecord>,
    catalogItems: List<CatalogItemRecord>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (FakturaRecord) -> Unit,
    onExportPohoda: (FakturaRecord) -> Unit = {}
) {
    val context = LocalContext.current
    var typDokladu by remember { mutableStateOf(initial.typDokladu) }
    var ucelFaktury by remember { mutableStateOf(initial.ucelFaktury) }
    var cislo by remember { mutableStateOf(initial.cislo) }
    var variabilniSymbol by remember { mutableStateOf(initial.variabilniSymbol) }
    var depo by remember { mutableStateOf(initial.depo.ifBlank { "Depo 1" }) }
    var stredisko by remember { mutableStateOf(initial.stredisko.ifBlank { if (initial.ucelFaktury == "PRONAJEM") "Pronájem" else "Prodej" }) }
    var datumVystaveni by remember { mutableStateOf(initial.datumVystaveni) }
    var datumDuzp by remember { mutableStateOf(initial.datumZdanitelnehoPlneni) }
    var datumSplatnosti by remember { mutableStateOf(initial.datumSplatnosti) }
    var formaUhrady by remember { mutableStateOf(initial.formaUhrady) }
    var stavUhrady by remember { mutableStateOf(initial.stavUhrady) }
    var firma by remember { mutableStateOf(initial.firma) }
    var jmeno by remember { mutableStateOf(initial.jmeno) }
    var ic by remember { mutableStateOf(initial.ic) }
    var dic by remember { mutableStateOf(initial.dic) }
    var ulice by remember { mutableStateOf(initial.ulice) }
    var telefon by remember { mutableStateOf(initial.telefon) }
    var email by remember { mutableStateOf(initial.email) }
    var oznaceniKontejneru by remember { mutableStateOf(initial.oznaceniKontejneru) }
    var typKontejneru by remember { mutableStateOf(initial.typKontejneru) }
    var obdobiPronajmu by remember { mutableStateOf(initial.obdobiPronajmu) }
    var mistoDodani by remember { mutableStateOf(initial.mistoDodani) }
    var text by remember { mutableStateOf(initial.text) }
    var poznamka by remember { mutableStateOf(initial.poznamka) }
    var items by remember { mutableStateOf(initial.items) }

    var showContainerPicker by remember { mutableStateOf(false) }
    var showAdresarPicker by remember { mutableStateOf(false) }
    var showCatalogPicker by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<NabidkaItemRecord?>(null) }

    fun buildCurrentRecord(): FakturaRecord = initial.copy(
        cislo = cislo.trim(),
        variabilniSymbol = variabilniSymbol.trim(),
        typDokladu = typDokladu,
        ucelFaktury = ucelFaktury,
        depo = depo,
        stredisko = stredisko,
        datumVystaveni = datumVystaveni.trim(),
        datumZdanitelnehoPlneni = datumDuzp.trim(),
        datumSplatnosti = datumSplatnosti.trim(),
        formaUhrady = formaUhrady,
        stavUhrady = stavUhrady,
        firma = firma.trim(),
        jmeno = jmeno.trim(),
        ic = ic.trim(),
        dic = dic.trim(),
        ulice = ulice.trim(),
        telefon = telefon.trim(),
        email = email.trim(),
        oznaceniKontejneru = oznaceniKontejneru.trim(),
        typKontejneru = typKontejneru.trim(),
        obdobiPronajmu = obdobiPronajmu.trim(),
        mistoDodani = mistoDodani.trim(),
        text = text.trim(),
        poznamka = poznamka.trim(),
        items = items
    )

    if (itemToDelete != null) {
        DoubleConfirmDeleteDialog(
            itemTitle = "Položka faktury: ${itemToDelete!!.text}",
            onDismiss = { itemToDelete = null },
            onConfirmedDelete = {
                items = items.filterNot { it.id == itemToDelete!!.id }
                itemToDelete = null
            }
        )
    }

    if (showContainerPicker) {
        AlertDialog(
            onDismissRequest = { showContainerPicker = false },
            title = { Text("Kontejner", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allContainers) { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val normC = c.designation.uppercase().replace(Regex("[^A-Z0-9]"), "")
                                val alreadyLoaded = normC.length >= 2 && (
                                    oznaceniKontejneru.uppercase().replace(Regex("[^A-Z0-9]"), "") == normC ||
                                    items.any { it.text.uppercase().replace(Regex("[^A-Z0-9]"), "").contains(normC) }
                                )
                                if (alreadyLoaded) {
                                    Toast.makeText(context, "Duplicita: Kontejner ${c.designation} je již na dokladu načten!", Toast.LENGTH_LONG).show()
                                    return@clickable
                                }
                                oznaceniKontejneru = c.designation
                                typKontejneru = c.type
                                if (firma.isBlank()) firma = c.customerCompany.ifBlank { c.soldCustomerName }.ifBlank { c.reservedName }
                                val jc = if (c.price > 0) c.price.toDouble() else if (ucelFaktury == "PRONAJEM") 3500.0 else 65000.0
                                val dph = kotlin.math.round(jc * 0.21)
                                val newItem = NabidkaItemRecord(
                                    id = "fi_${System.currentTimeMillis()}",
                                    kod = if (ucelFaktury == "PRONAJEM") "PRON" else "PRODEJ",
                                    text = if (ucelFaktury == "PRONAJEM") "Pronájem kontejneru ${c.type} č. ${c.designation}" else "Prodej kontejneru ${c.type} č. ${c.designation}",
                                    mnozstvi = 1.0,
                                    mj = if (ucelFaktury == "PRONAJEM") "měs." else "ks",
                                    jCena = jc,
                                    dphPct = 21.0,
                                    castkaBez = jc,
                                    dphVal = dph
                                )
                                items = items + newItem
                                showContainerPicker = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${c.designation} • ${c.type}", fontWeight = FontWeight.Bold)
                                Text("${c.depo} • ${c.status} • ${formatPrice(c.price)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContainerPicker = false }) { Text("Zavřít") }
            }
        )
    }

    if (showAdresarPicker) {
        AlertDialog(
            onDismissRequest = { showAdresarPicker = false },
            title = { Text("Adresář", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(adresarList) { adr ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                firma = adr.company.ifBlank { adr.name }
                                jmeno = adr.name
                                ic = adr.ic
                                dic = adr.dic
                                ulice = adr.ulice
                                telefon = adr.phone
                                email = adr.email
                                showAdresarPicker = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(adr.company.ifBlank { adr.name }, fontWeight = FontWeight.Bold)
                                Text("IČ: ${adr.ic.ifBlank { "-" }} • Bonita: ${adr.bonita}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdresarPicker = false }) { Text("Zavřít") }
            }
        )
    }

    if (showCatalogPicker) {
        AlertDialog(
            onDismissRequest = { showCatalogPicker = false },
            title = { Text("Položka", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(catalogItems) { cat ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val bez = cat.cenaBezDph
                                val dph = kotlin.math.round(bez * (cat.dphPct / 100.0))
                                items = items + NabidkaItemRecord(
                                    id = "fi_${System.currentTimeMillis()}",
                                    kod = cat.oznaceni,
                                    text = "${cat.kategorie} – ${cat.nazev}",
                                    mnozstvi = 1.0,
                                    mj = "ks",
                                    jCena = bez,
                                    dphPct = cat.dphPct,
                                    castkaBez = bez,
                                    dphVal = dph
                                )
                                showCatalogPicker = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${cat.kategorie}: ${cat.nazev}", fontWeight = FontWeight.Bold)
                                Text("Cena bez DPH: ${fmtKc(cat.cenaBezDph)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCatalogPicker = false }) { Text("Zavřít") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (typDokladu == "ZALOHOVA_FAKTURA") "Zálohová" else "Faktura",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Print / WhatsApp / Email bar right at the top of the card
                AgendaPrintShareBar(
                    compact = true,
                    onPrint = {
                        val rec = buildCurrentRecord()
                        val pdf = generateFakturaPdf(context, rec)
                        printPdfFile(context, pdf, "Faktura_${rec.cislo}")
                    },
                    onWhatsApp = {
                        val rec = buildCurrentRecord()
                        val pdf = generateFakturaPdf(context, rec)
                        val sumS = rec.items.sumOf { it.castkaBez + it.dphVal }
                        sharePdfViaWhatsApp(
                            context,
                            pdf,
                            "${if (rec.typDokladu == "ZALOHOVA_FAKTURA") "Zálohová faktura" else "Faktura"} č. ${rec.cislo} (${if (rec.ucelFaktury == "PRONAJEM") "Pronájem" else "Prodej"} kontejneru ${rec.oznaceniKontejneru}) • Celkem s DPH: ${fmtKc(sumS)}",
                            rec.telefon
                        )
                    },
                    onEmail = {
                        val rec = buildCurrentRecord()
                        val pdf = generateFakturaPdf(context, rec)
                        val sumS = rec.items.sumOf { it.castkaBez + it.dphVal }
                        sharePdfViaEmail(
                            context,
                            pdf,
                            rec.email,
                            "${if (rec.typDokladu == "ZALOHOVA_FAKTURA") "Zálohová faktura" else "Faktura"} č. ${rec.cislo} – Kontejnery Ostrava s.r.o.",
                            "zasíláme Vám doklad č. ${rec.cislo} v celkové výši ${fmtKc(sumS)} s DPH."
                        )
                    }
                )

                // Switch: 1. Za Prodej kontejneru vs 2. Za Pronájem kontejneru
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = ucelFaktury == "PRODEJ",
                        onClick = {
                            ucelFaktury = "PRODEJ"
                            stredisko = "Prodej"
                        },
                        label = { Text("Prodej", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = ucelFaktury == "PRONAJEM",
                        onClick = {
                            ucelFaktury = "PRONAJEM"
                            stredisko = "Pronájem"
                        },
                        label = { Text("Pronájem", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Switch: Faktura vs Zálohová faktura
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = typDokladu == "FAKTURA",
                        onClick = { typDokladu = "FAKTURA" },
                        label = { Text("Faktura", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = typDokladu == "ZALOHOVA_FAKTURA",
                        onClick = { typDokladu = "ZALOHOVA_FAKTURA" },
                        label = { Text("Zálohová", fontSize = 11.sp) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = cislo,
                        onValueChange = { cislo = it },
                        label = { Text("Doklad", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = variabilniSymbol,
                        onValueChange = { variabilniSymbol = it },
                        label = { Text("Symbol", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = datumVystaveni,
                        onValueChange = { datumVystaveni = it },
                        label = { Text("Vystaveno", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = datumSplatnosti,
                        onValueChange = { datumSplatnosti = it },
                        label = { Text("Splatnost", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick pickers for Odběratel & Kontejner
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showAdresarPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Adresář", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { showContainerPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kontejner", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }

                OutlinedTextField(
                    value = firma,
                    onValueChange = { firma = it },
                    label = { Text("Odběratel", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = ic,
                        onValueChange = { ic = it },
                        label = { Text("IČO", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dic,
                        onValueChange = { dic = it },
                        label = { Text("DIČ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = ulice,
                    onValueChange = { ulice = it },
                    label = { Text("Adresa", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = telefon,
                        onValueChange = { telefon = it },
                        label = { Text("Telefon", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = oznaceniKontejneru,
                        onValueChange = { oznaceniKontejneru = it },
                        label = { Text("Označení", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = typKontejneru,
                        onValueChange = { typKontejneru = it },
                        label = { Text("Typ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (ucelFaktury == "PRONAJEM") {
                    OutlinedTextField(
                        value = obdobiPronajmu,
                        onValueChange = { obdobiPronajmu = it },
                        label = { Text("Období", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Předmět", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Payment status
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Neuhrazeno", "Uhrazeno", "Částečně uhrazeno").forEach { st ->
                        FilterChip(
                            selected = stavUhrady == st,
                            onClick = { stavUhrady = st },
                            label = { Text(st, fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Položky (${items.size})", fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showCatalogPicker = true }) {
                            Text("+ Katalog", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = {
                                items = items + NabidkaItemRecord(
                                    id = "fi_${System.currentTimeMillis()}",
                                    kod = if (ucelFaktury == "PRONAJEM") "PRON" else "PRODEJ",
                                    text = if (ucelFaktury == "PRONAJEM") "Pronájem kontejneru $oznaceniKontejneru" else "Prodej kontejneru $oznaceniKontejneru",
                                    mnozstvi = 1.0,
                                    mj = if (ucelFaktury == "PRONAJEM") "měs." else "ks",
                                    jCena = 0.0,
                                    dphPct = 21.0
                                )
                            }
                        ) {
                            Text("+ Řádek", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                items.forEachIndexed { idx, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = item.text,
                                    onValueChange = { newTxt ->
                                        items = items.toMutableList().also { list ->
                                            list[idx] = item.copy(text = newTxt)
                                        }
                                    },
                                    label = { Text("Položka", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { itemToDelete = item }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Smazat řádek", tint = Color(0xFFDC2626))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = item.mnozstvi.toString(),
                                    onValueChange = { v ->
                                        val mn = v.toDoubleOrNull() ?: 1.0
                                        val bez = kotlin.math.round(mn * item.jCena * (1.0 - item.sleva / 100.0))
                                        val dph = if (item.pdp) 0.0 else kotlin.math.round(bez * (item.dphPct / 100.0))
                                        items = items.toMutableList().also { list ->
                                            list[idx] = item.copy(mnozstvi = mn, castkaBez = bez, dphVal = dph)
                                        }
                                    },
                                    label = { Text("Počet", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = item.jCena.toLong().toString(),
                                    onValueChange = { v ->
                                        val jc = v.toDoubleOrNull() ?: 0.0
                                        val bez = kotlin.math.round(item.mnozstvi * jc * (1.0 - item.sleva / 100.0))
                                        val dph = if (item.pdp) 0.0 else kotlin.math.round(bez * (item.dphPct / 100.0))
                                        items = items.toMutableList().also { list ->
                                            list[idx] = item.copy(jCena = jc, castkaBez = bez, dphVal = dph)
                                        }
                                    },
                                    label = { Text("Cena", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f)
                                )
                            }
                        }
                    }
                }

                val sumBez = items.sumOf { it.castkaBez }
                val sumDph = items.sumOf { it.dphVal }
                Surface(
                    color = Color(0xFFE0F2FE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bez DPH: ${fmtKc(sumBez)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("CELKEM S DPH: ${fmtKc(sumBez + sumDph)}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF0F3D5E))
                    }
                }
            }
        },
        confirmButton = {
            var isSendingMpohoda by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val record = buildCurrentRecord()
                        onSave(record)
                        isSendingMpohoda = true
                        coroutineScope.launch {
                            val res = viewModel.sendInvoiceToMPohoda(record)
                            isSendingMpohoda = false
                            if (res.success) {
                                Toast.makeText(context, "Doklad byl úspěšně odeslán do mPohody!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "mPohoda: ${res.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSendingMpohoda,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    if (isSendingMpohoda) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Do mPohody", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = { onExportPohoda(buildCurrentRecord()) },
                    border = BorderStroke(1.dp, Color(0xFFD97706))
                ) {
                    Text("Pohoda XML", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                }
                Button(
                    onClick = { onSave(buildCurrentRecord()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B))
                ) {
                    Text("Uložit", fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        }
    )
}

/**
 * Dialog shown when user clicks "+ Fakturace" on Nabídka, Zakázka, or Karta kontejneru.
 * Lets user choose:
 * 1. Faktura za Prodej kontejneru
 * 2. Faktura za Pronájem kontejneru
 * 3. Zálohová faktura za Prodej kontejneru
 * 4. Zálohová faktura za Pronájem kontejneru
 */
@Composable
fun QuickInvoiceFromSourceDialog(
    sourceTitle: String,
    onDismiss: () -> Unit,
    onSelectOption: (ucelFaktury: String, isZalohova: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Fakturace", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sourceTitle, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E8B))

                Button(
                    onClick = { onSelectOption("PRODEJ", false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Faktura – Prodej", fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = { onSelectOption("PRONAJEM", false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Faktura – Pronájem", fontWeight = FontWeight.ExtraBold)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedButton(
                    onClick = { onSelectOption("PRODEJ", true) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zálohová – Prodej", fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                }

                OutlinedButton(
                    onClick = { onSelectOption("PRONAJEM", true) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zálohová – Pronájem", fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        }
    )
}

@Composable
fun PohodaInvoiceExportDialog(
    invoices: List<FakturaRecord>,
    defaultIco: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val serverConfig by viewModel.pohodaServerConfig.collectAsState()

    var ico by remember { mutableStateOf(defaultIco.ifBlank { serverConfig.ico }.ifBlank { "25388720" }) }
    val xmlText = remember(invoices, ico) { buildPohodaInvoicesXml(invoices, ico) }

    var isSending by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<PohodaSyncResult?>(null) }
    var showConfigModal by remember { mutableStateOf(false) }

    if (showConfigModal) {
        PohodaMServerDialog(
            viewModel = viewModel,
            onDismiss = { showConfigModal = false }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE0F2FE)
                ) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.padding(4.dp).size(22.dp)
                    )
                }
                Column {
                    Text("Pohoda", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${invoices.size} ks",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Color(0xFF0F172A)
                    )
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "XML",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = ico,
                    onValueChange = { ico = it },
                    label = { Text("IČO", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = xmlText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("XML", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                // Optional HTTP mServer send section (if configured)
                if (serverConfig.serverUrl.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Volitelné přímé HTTP odeslání (mServer):", fontSize = 10.sp, color = Color(0xFF64748B))
                                TextButton(
                                    onClick = { showConfigModal = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("Nastavení", fontSize = 10.sp)
                                }
                            }
                            if (isSending) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        isSending = true
                                        syncResult = null
                                        coroutineScope.launch {
                                            val res = viewModel.sendInvoicesToPohoda(invoices)
                                            syncResult = res
                                            isSending = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Odeslat přes HTTP na ${serverConfig.serverUrl.take(28)}", fontSize = 11.sp)
                                }
                            }
                            if (syncResult != null) {
                                Text(
                                    text = syncResult!!.message,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (syncResult!!.success) Color(0xFF16A34A) else Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("POHODA_XML", xmlText))
                        Toast.makeText(context, "POHODA XML zkopírováno do schránky", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Kopírovat XML", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        try {
                            val fileName = if (invoices.size == 1) "POHODA_Faktura_${invoices.first().cislo}.xml" else "POHODA_Faktury_${System.currentTimeMillis()}.xml"
                            val file = File(context.cacheDir, fileName)
                            file.writeText(xmlText, Charsets.UTF_8)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/xml"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Import faktur do SW Pohoda ($fileName)")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Uložit / Odeslat POHODA XML"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Chyba exportu XML: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text("Sdílet XML soubor", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zavřít") }
        }
    )
}
