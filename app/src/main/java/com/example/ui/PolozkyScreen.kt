package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CatalogItemRecord
import kotlinx.coroutines.launch
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolozkyScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val catalogItems by viewModel.catalogItems.collectAsState()
    val savedSections by viewModel.catalogSections.collectAsState()
    var selectedSectionTab by remember { mutableIntStateOf(0) } // 0 = VŠE, 1 = KONTEJNERY, 2 = VYBAVENÍ, 3 = PŘEPRAVA
    var editingItem by remember { mutableStateOf<CatalogItemRecord?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var showHtmlDialog by remember { mutableStateOf(false) }
    var showAddSectionDialog by remember { mutableStateOf(false) }
    var showRemoveSectionDialog by remember { mutableStateOf(false) }
    var deleteConfirmItem by remember { mutableStateOf<CatalogItemRecord?>(null) }
    var deleteConfirmSection by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (deleteConfirmItem != null) {
        val target = deleteConfirmItem!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Položka katalogu [${target.oznaceni}] ${target.nazev}",
            itemDescription = "Kategorie: ${target.kategorie} • Cena bez DPH: ${target.cenaBezDph.toLong()} Kč",
            onDismiss = { deleteConfirmItem = null },
            onConfirmedDelete = {
                viewModel.deleteCatalogItem(target.id)
                scope.launch {
                    snackbarHostState.showSnackbar("Položka ${target.oznaceni} – ${target.nazev} smazána")
                }
            }
        )
    }

    if (deleteConfirmSection != null) {
        val secName = deleteConfirmSection!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Celá sekce v Položkách: $secName",
            itemDescription = "Smazáním sekce dojde k odstranění všech položek v této sekci.",
            onDismiss = { deleteConfirmSection = null },
            onConfirmedDelete = {
                viewModel.removeCatalogSection(secName, deleteItems = true)
                scope.launch {
                    snackbarHostState.showSnackbar("Sekce '$secName' odstraněna")
                }
            }
        )
    }

    if (showHtmlDialog) {
        HtmlDownloadDialog(
            onDismiss = {
                showHtmlDialog = false
                viewModel.reloadLocalCaches()
            }
        )
    }

    val allCategories = remember(savedSections, catalogItems) {
        val list = mutableListOf<String>()
        savedSections.forEach { if (it.isNotBlank() && it !in list) list.add(it) }
        catalogItems.map { it.kategorie }.forEach { if (it.isNotBlank() && it !in list) list.add(it) }
        if (list.isEmpty()) listOf("Obytné kontejnery", "Skladové kontejnery", "Skladové námořní kontejnery", "Speciální kontejnery", "Vybavení", "Přeprava") else list
    }

    val containerCategories = remember(allCategories) {
        allCategories.filter { it != "Vybavení" && it != "Přeprava" }
    }

    val visibleCategories = when (selectedSectionTab) {
        1 -> containerCategories
        2 -> allCategories.filter { it.equals("Vybavení", ignoreCase = true) }.ifEmpty { listOf("Vybavení") }
        3 -> allCategories.filter { it.equals("Přeprava", ignoreCase = true) }.ifEmpty { listOf("Přeprava") }
        else -> allCategories
    }

    fun getNextOznaceniForCategory(kategorie: String): String {
        val maxNum = catalogItems
            .filter { it.kategorie == kategorie }
            .mapNotNull { it.oznaceni.filter { ch -> ch.isDigit() }.toIntOrNull() }
            .maxOrNull() ?: 0
        return (maxNum + 1).toString().padStart(3, '0')
    }

    fun getNextPoradiForCategory(kategorie: String): Int {
        val maxPoradi = catalogItems
            .filter { it.kategorie == kategorie }
            .maxOfOrNull { it.poradi } ?: 0
        return maxPoradi + 1
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        "POLOŽKY (${catalogItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E8B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val defaultCat = when (selectedSectionTab) {
                        2 -> "Vybavení"
                        3 -> "Přeprava"
                        else -> "Obytné kontejnery"
                    }
                    val defaultSekce = when (defaultCat) {
                        "Vybavení" -> "VYBAVENÍ"
                        "Přeprava" -> "PŘEPRAVA"
                        else -> "KONTEJNERY"
                    }
                    isAddingNew = true
                    editingItem = CatalogItemRecord(
                        id = "",
                        sekce = defaultSekce,
                        kategorie = defaultCat,
                        oznaceni = getNextOznaceniForCategory(defaultCat),
                        nazev = "",
                        pocet = 1.0,
                        cenaBezDph = 0.0,
                        dphPct = 21.0,
                        slevaPct = 0.0,
                        poradi = getNextPoradiForCategory(defaultCat)
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("NOVÁ", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF1B5E8B),
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedSectionTab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = selectedSectionTab == 0,
                    onClick = { selectedSectionTab = 0 },
                    text = { Text("VŠE", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSectionTab == 1,
                    onClick = { selectedSectionTab = 1 },
                    text = {
                        Text("KONTEJNERY", fontWeight = FontWeight.Bold)
                    }
                )
                Tab(
                    selected = selectedSectionTab == 2,
                    onClick = { selectedSectionTab = 2 },
                    text = {
                        Text("VYBAVENÍ", fontWeight = FontWeight.Bold)
                    }
                )
                Tab(
                    selected = selectedSectionTab == 3,
                    onClick = { selectedSectionTab = 3 },
                    text = {
                        Text("PŘEPRAVA", fontWeight = FontWeight.Bold)
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showAddSectionDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SEKCE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { showRemoveSectionDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFFD32F2F)
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ODEBRAT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                visibleCategories.forEach { catName ->
                    val catItems = catalogItems
                        .filter { it.kategorie == catName }
                        .sortedWith(compareByDescending<CatalogItemRecord> { it.poradi }.thenByDescending { it.timestamp })
                    val isEquipment = catName == "Vybavení"
                    val isPreprava = catName == "Přeprava"
                    val headerColor = when {
                        isPreprava -> Color(0xFFE65100)
                        isEquipment -> Color(0xFF0284C7)
                        else -> Color(0xFF0D47A1)
                    }

                    item(key = "header_$catName") {
                        Surface(
                            color = headerColor,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${catName.uppercase()} (${catItems.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            isAddingNew = true
                                            editingItem = CatalogItemRecord(
                                                id = "",
                                                sekce = when {
                                                    isPreprava -> "PŘEPRAVA"
                                                    isEquipment -> "VYBAVENÍ"
                                                    else -> "KONTEJNERY"
                                                },
                                                kategorie = catName,
                                                oznaceni = getNextOznaceniForCategory(catName),
                                                nazev = "",
                                                pocet = 1.0,
                                                cenaBezDph = 0.0,
                                                dphPct = 21.0,
                                                slevaPct = 0.0,
                                                poradi = getNextPoradiForCategory(catName)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White,
                                            contentColor = headerColor
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Přidat", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = {
                                            deleteConfirmSection = catName
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Odstranit sekci $catName",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(catItems, key = { _, it -> it.id }) { idx, item ->
                        val zakladPoSleve = round(item.pocet * item.cenaBezDph * (1.0 - item.slevaPct / 100.0))
                        val celkemSDph = round(zakladPoSleve * (1.0 + item.dphPct / 100.0))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isAddingNew = false
                                    editingItem = item
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 1. Řádek: Označení + Název + Akční tlačítka (vše čisté)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = headerColor,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = item.oznaceni,
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.labelLarge,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        Text(
                                            text = item.nazev,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.moveCatalogItem(item, moveUp = true) },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = "Posunout nahoru",
                                                tint = if (idx > 0) headerColor else Color.Gray.copy(alpha = 0.4f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveCatalogItem(item, moveUp = false) },
                                            enabled = idx < catItems.lastIndex,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDownward,
                                                contentDescription = "Posunout dolů",
                                                tint = if (idx < catItems.lastIndex) headerColor else Color.Gray.copy(alpha = 0.4f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                isAddingNew = false
                                                editingItem = item
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Upravit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                deleteConfirmItem = item
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                // 2. Přehled parametrů (Strukturovaná mřížka s dynamic weight, žádný přetekající text)
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Řádek A: Počet • Cena • Sleva
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("POČET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                                    Text("${item.pocet.toInt()} ks", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                                }
                                            }
                                            Surface(
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1.3f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("CENA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                                    Text("${item.cenaBezDph.toLong()} Kč", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0284C7))
                                                }
                                            }
                                            Surface(
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("SLEVA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                                    Text("${item.slevaPct.toInt()} %", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706))
                                                }
                                            }
                                        }

                                        // Řádek B: Přepínač DPH + Výpočet základu po slevě
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("DPH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                FilterChip(
                                                    selected = item.dphPct.toInt() == 21,
                                                    onClick = { viewModel.upsertCatalogItem(item.copy(dphPct = 21.0)) },
                                                    label = { Text("21%", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.height(30.dp)
                                                )
                                                FilterChip(
                                                    selected = item.dphPct.toInt() == 5,
                                                    onClick = { viewModel.upsertCatalogItem(item.copy(dphPct = 5.0)) },
                                                    label = { Text("5%", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.height(30.dp)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "${zakladPoSleve.toLong()} Kč",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                        }

                                        // Řádek C: Výrazný celkový banner vč. DPH
                                        Surface(
                                            color = Color(0xFFE0F2FE),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("CELKEM", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0369A1))
                                                Text("${celkemSDph.toLong()} Kč", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F3D5E))
                                            }
                                        }

                                        // Akční lišta pro tisk a sdílení
                                        PrintShareActionBar(
                                            onPrint = {
                                                val pdf = generateCatalogItemPdf(context, item)
                                                printPdfFile(context, pdf, "Polozka_${item.oznaceni}")
                                            },
                                            onWhatsApp = {
                                                val pdf = generateCatalogItemPdf(context, item)
                                                sharePdfViaWhatsApp(
                                                    context,
                                                    pdf,
                                                    "POLOŽKA KATALOGU [${item.oznaceni}] ${item.nazev}\nKategorie: ${item.kategorie}\nCena bez DPH: ${item.cenaBezDph.toLong()} Kč | Celkem s DPH: ${celkemSDph.toLong()} Kč"
                                                )
                                            },
                                            onEmail = {
                                                val pdf = generateCatalogItemPdf(context, item)
                                                sharePdfViaEmail(
                                                    context,
                                                    pdf,
                                                    recipientEmail = "",
                                                    subject = "Katalogová položka [${item.oznaceni}] ${item.nazev} – KONTEJNERY OSTRAVA s.r.o.",
                                                    body = "Dobrý den,\n\nv příloze zasíláme katalogovou kartu položky [${item.oznaceni}] ${item.nazev} (Cena s DPH: ${celkemSDph.toLong()} Kč).\n\nS pozdravem,\nKONTEJNERY OSTRAVA s.r.o."
                                                )
                                            }
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

    if (editingItem != null) {
        val current = editingItem!!
        var kategorie by remember(current) { mutableStateOf(current.kategorie) }
        var oznaceni by remember(current) { mutableStateOf(current.oznaceni) }
        var nazev by remember(current) { mutableStateOf(current.nazev) }
        var pocetStr by remember(current) { mutableStateOf(if (current.pocet % 1.0 == 0.0) current.pocet.toInt().toString() else current.pocet.toString()) }
        var cenaBezStr by remember(current) { mutableStateOf(if (current.cenaBezDph % 1.0 == 0.0) current.cenaBezDph.toLong().toString() else current.cenaBezDph.toString()) }
        var dphPct by remember(current) { mutableDoubleStateOf(if (current.dphPct == 5.0) 5.0 else 21.0) }
        var slevaStr by remember(current) { mutableStateOf(if (current.slevaPct % 1.0 == 0.0) current.slevaPct.toInt().toString() else current.slevaPct.toString()) }

        val pocetVal = (pocetStr.replace(",", ".").toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0)
        val cenaBezVal = (cenaBezStr.replace(",", ".").toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        val slevaVal = (slevaStr.replace(",", ".").toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
        val zakladPoSleve = round(pocetVal * cenaBezVal * (1.0 - slevaVal / 100.0))
        val dphCastka = round(zakladPoSleve * (dphPct / 100.0))
        val celkemSDph = zakladPoSleve + dphCastka

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = {
                Text(
                    "Položka",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Kategorie", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    allCategories.forEach { catOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    kategorie = catOption
                                    if (isAddingNew) {
                                        oznaceni = getNextOznaceniForCategory(catOption)
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = kategorie == catOption,
                                onClick = {
                                    kategorie = catOption
                                    if (isAddingNew) {
                                        oznaceni = getNextOznaceniForCategory(catOption)
                                    }
                                }
                            )
                            Text(catOption, style = MaterialTheme.typography.bodyMedium, fontWeight = if (kategorie == catOption) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = oznaceni,
                            onValueChange = { oznaceni = it },
                            label = { Text("Označení") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                        OutlinedTextField(
                            value = pocetStr,
                            onValueChange = { pocetStr = it },
                            label = { Text("Počet") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = nazev,
                        onValueChange = { nazev = it },
                        label = { Text("Název") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cenaBezStr,
                            onValueChange = { cenaBezStr = it },
                            label = { Text("Cena") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = slevaStr,
                            onValueChange = { slevaStr = it },
                            label = { Text("Sleva") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("DPH", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        FilterChip(
                            selected = dphPct.toInt() == 21,
                            onClick = { dphPct = 21.0 },
                            label = { Text("21 %", fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = dphPct.toInt() == 5,
                            onClick = { dphPct = 5.0 },
                            label = { Text("5 %", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Základ: ${zakladPoSleve.toLong()} Kč",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0369A1)
                            )
                            Text(
                                text = "DPH: ${dphCastka.toLong()} Kč",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0369A1)
                            )
                            Text(
                                text = "Celkem: ${celkemSDph.toLong()} Kč",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0C4A6E)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nazev.isNotBlank()) {
                            val resolvedSekce = when (kategorie) {
                                "Vybavení" -> "VYBAVENÍ"
                                "Přeprava" -> "PŘEPRAVA"
                                else -> "KONTEJNERY"
                            }
                            val resolvedPoradi = if (isAddingNew || current.kategorie != kategorie) {
                                getNextPoradiForCategory(kategorie)
                            } else {
                                current.poradi
                            }
                            viewModel.upsertCatalogItem(
                                current.copy(
                                    sekce = resolvedSekce,
                                    kategorie = kategorie,
                                    oznaceni = oznaceni.ifBlank { "001" },
                                    nazev = nazev.trim(),
                                    pocet = if (pocetVal <= 0.0) 1.0 else pocetVal,
                                    cenaBezDph = cenaBezVal,
                                    dphPct = dphPct,
                                    slevaPct = slevaVal,
                                    celkemBezDph = zakladPoSleve,
                                    celkemSDph = celkemSDph,
                                    poradi = resolvedPoradi
                                )
                            )
                            editingItem = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Položka uložena do katalogu")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B))
                ) {
                    Text("Uložit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Zrušit")
                }
            }
        )
    }

    if (showAddSectionDialog) {
        var newSecName by remember { mutableStateOf("") }
        var parentGroup by remember { mutableStateOf("KONTEJNERY") }
        AlertDialog(
            onDismissRequest = { showAddSectionDialog = false },
            title = { Text("Sekce", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSecName,
                        onValueChange = { newSecName = it },
                        label = { Text("Sekce") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Skupina", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("KONTEJNERY", "VYBAVENÍ", "PŘEPRAVA").forEach { grp ->
                            FilterChip(
                                selected = parentGroup == grp,
                                onClick = { parentGroup = grp },
                                label = { Text(grp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSecName.isNotBlank()) {
                            viewModel.addCatalogSection(newSecName.trim(), parentGroup)
                            showAddSectionDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Sekce '${newSecName.trim()}' byla přidána")
                            }
                        }
                    }
                ) {
                    Text("Přidat", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSectionDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }

    if (showRemoveSectionDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveSectionDialog = false },
            title = { Text("Sekce", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allCategories.forEach { sec ->
                        val count = catalogItems.count { it.kategorie == sec }
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sec, fontWeight = FontWeight.Bold)
                                    Text("$count položek", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Button(
                                    onClick = {
                                        showRemoveSectionDialog = false
                                        deleteConfirmSection = sec
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                ) {
                                    Text("Odstranit", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRemoveSectionDialog = false }) {
                    Text("Hotovo")
                }
            }
        )
    }
}
