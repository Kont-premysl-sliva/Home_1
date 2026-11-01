package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Settings
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
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import com.example.data.CatalogItemRecord
import com.example.data.CustomerRecord
import com.example.data.FakturaRecord
import com.example.data.NabidkaItemRecord
import com.example.data.NabidkaRecord
import com.example.data.ZakazkaRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakazniciScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    initialTab: Int = 0,
    onTabChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val allCustomers by viewModel.customers.collectAsState()
    val allNabidky by viewModel.nabidky.collectAsState()
    val allZakazky by viewModel.zakazky.collectAsState()
    val allFaktury by viewModel.faktury.collectAsState()
    val allZalohoveFaktury by viewModel.zalohoveFaktury.collectAsState()
    val allContainers by viewModel.containers.collectAsState()
    val catalogItems by viewModel.catalogItems.collectAsState()
    val zakazkaStrediska by viewModel.zakazkaStrediska.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pohodaIco by viewModel.pohodaIco.collectAsState()
    val activeDepo = currentDepo ?: "Depo 1"

    var depoFilterMode by remember { mutableStateOf("VŠE") } // "VŠE", "Depo 1", "Depo 2"
    var selectedStrediskoFilter by remember { mutableStateOf("VŠE") }
    var showManageStrediskaDialog by remember { mutableStateOf(false) }
    var prepravaModalZakazka by remember { mutableStateOf<ZakazkaRecord?>(null) }
    var udalostiModalCustomer by remember { mutableStateOf<CustomerRecord?>(null) }

    LaunchedEffect(currentDepo) {
        if (currentDepo == "Depo 1" || currentDepo == "Depo 2") {
            depoFilterMode = currentDepo ?: "VŠE"
        }
    }

    val ordersList = remember(allCustomers, depoFilterMode) {
        val onlyOrders = allCustomers.filter { it.isOrder }
        when (depoFilterMode) {
            "Depo 1" -> onlyOrders.filter { it.depo == "Depo 1" || it.depo.isBlank() }
            "Depo 2" -> onlyOrders.filter { it.depo == "Depo 2" || it.depo.isBlank() }
            else -> onlyOrders
        }
    }

    val adresarList = remember(allCustomers, depoFilterMode) {
        val contacts = allCustomers.filter { it.isAdresar }
        val filtered = when (depoFilterMode) {
            "Depo 1" -> contacts.filter { it.depo == "Depo 1" || it.depo.isBlank() }
            "Depo 2" -> contacts.filter { it.depo == "Depo 2" || it.depo.isBlank() }
            else -> contacts
        }
        val czCollator = java.text.Collator.getInstance(Locale.forLanguageTag("cs-CZ")).apply {
            strength = java.text.Collator.PRIMARY
        }
        filtered.sortedWith { a, b ->
            czCollator.compare(
                a.company.ifBlank { a.name }.trim(),
                b.company.ifBlank { b.name }.trim()
            )
        }
    }

    val nabidky = remember(allNabidky, depoFilterMode) {
        when (depoFilterMode) {
            "Depo 1" -> allNabidky.filter { it.depo.ifBlank { "Depo 1" } == "Depo 1" }
            "Depo 2" -> allNabidky.filter { it.depo == "Depo 2" }
            else -> allNabidky
        }
    }

    val zakazky = remember(allZakazky, depoFilterMode, selectedStrediskoFilter) {
        val byDepo = when (depoFilterMode) {
            "Depo 1" -> allZakazky.filter { it.depo.ifBlank { "Depo 1" } == "Depo 1" }
            "Depo 2" -> allZakazky.filter { it.depo == "Depo 2" }
            else -> allZakazky
        }
        if (selectedStrediskoFilter == "VŠE") {
            byDepo
        } else {
            byDepo.filter { it.stredisko.ifBlank { "Pronájem" }.equals(selectedStrediskoFilter, ignoreCase = true) }
        }
    }

    val faktury = remember(allFaktury, depoFilterMode) {
        when (depoFilterMode) {
            "Depo 1" -> allFaktury.filter { it.depo.ifBlank { "Depo 1" } == "Depo 1" }
            "Depo 2" -> allFaktury.filter { it.depo == "Depo 2" }
            else -> allFaktury
        }
    }

    val zalohoveFaktury = remember(allZalohoveFaktury, depoFilterMode) {
        when (depoFilterMode) {
            "Depo 1" -> allZalohoveFaktury.filter { it.depo.ifBlank { "Depo 1" } == "Depo 1" }
            "Depo 2" -> allZalohoveFaktury.filter { it.depo == "Depo 2" }
            else -> allZalohoveFaktury
        }
    }

    var deleteConfirmCustomer by remember { mutableStateOf<CustomerRecord?>(null) }
    var deleteConfirmNabidka by remember { mutableStateOf<NabidkaRecord?>(null) }
    var deleteConfirmZakazka by remember { mutableStateOf<ZakazkaRecord?>(null) }
    var quickCreatedFaktura by remember { mutableStateOf<FakturaRecord?>(null) }
    var invoiceChoiceNabidka by remember { mutableStateOf<NabidkaRecord?>(null) }
    var invoiceChoiceZakazka by remember { mutableStateOf<ZakazkaRecord?>(null) }

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }
    var ordersStatusSubTab by remember { mutableStateOf("NOVÉ") }
    var nabidkyStatusSubTab by remember { mutableStateOf("NOVÉ") }
    var zakazkyStatusSubTab by remember { mutableStateOf("NOVÉ") }
    var adresarSearchQuery by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingOrder by remember { mutableStateOf<CustomerRecord?>(null) }
    var editingContact by remember { mutableStateOf<CustomerRecord?>(null) }
    var editingNabidka by remember { mutableStateOf<NabidkaRecord?>(null) }
    var editingZakazka by remember { mutableStateOf<ZakazkaRecord?>(null) }

    var pohodaExportTarget by remember { mutableStateOf<List<NabidkaRecord>?>(null) }
    var editingItemsNabidka by remember { mutableStateOf<NabidkaRecord?>(null) }
    var editingItemsZakazka by remember { mutableStateOf<ZakazkaRecord?>(null) }
    var pendingXmlToSave by remember { mutableStateOf("") }
    var showPohodaMServerDialog by remember { mutableStateOf(false) }
    var isSyncingMServerAdresar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.syncAllAgendasNow()
    }

    val saveXmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        if (uri != null && pendingXmlToSave.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingXmlToSave.toByteArray(Charsets.UTF_8))
                }
                scope.launch {
                    snackbarHostState.showSnackbar("XML soubor pro SW POHODA byl uložen")
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Chyba při ukládání XML: ${e.message}")
                }
            }
        }
    }

    var editingImageUri by remember { mutableStateOf<String?>(null) }
    var showImageZoom by remember { mutableStateOf<String?>(null) }
    var onImageSavedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var onPhotoPickedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    BackHandler(enabled = showImageZoom != null) {
        showImageZoom = null
    }

    if (showImageZoom != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showImageZoom = null }) {
            ZoomableImage(
                imageUri = showImageZoom!!,
                onEditClick = {
                    val uri = showImageZoom
                    showImageZoom = null
                    editingImageUri = uri
                },
                onCloseClick = { showImageZoom = null }
            )
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val destFile = File(context.filesDir, "cust_photo_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val path = destFile.absolutePath
                onPhotoPickedCallback?.invoke(path)
            } catch (e: Exception) {
                onPhotoPickedCallback?.invoke(uri.toString())
            }
        }
    }

    if (editingImageUri != null) {
        val targetUri = editingImageUri!!
        EditImageScreen(
            imagePath = targetUri,
            onSave = {
                editingImageUri = null
                onImageSavedCallback = null
            },
            onCancel = {
                editingImageUri = null
                onImageSavedCallback = null
            },
            onSaveImage = { updatedDataUri ->
                onImageSavedCallback?.invoke(updatedDataUri)
                editingImageUri = null
                onImageSavedCallback = null
            }
        )
        return
    }

    val workspaceBgColor = when (depoFilterMode) {
        "Depo 2" -> Color(0xFFF7F0E3)
        "Depo 1" -> Color(0xFFE6EFF7)
        else -> Color(0xFFEDF2F7)
    }

    val topBarColor = when (selectedTab) {
        0 -> when (depoFilterMode) {
            "Depo 1" -> Color(0xFF1B5E8B)
            "Depo 2" -> Color(0xFF5D5342)
            else -> Color(0xFF1E3A5F)
        }
        1 -> when (depoFilterMode) {
            "Depo 2" -> Color(0xFF1E40AF)
            else -> Color(0xFF1D4ED8)
        }
        2 -> when (depoFilterMode) {
            "Depo 2" -> Color(0xFF0369A1)
            else -> Color(0xFF0284C7)
        }
        4 -> Color(0xFF0B1B3D)
        5 -> Color(0xFF7C2D12)
        else -> when (depoFilterMode) {
            "Depo 2" -> Color(0xFF6B532B)
            else -> Color(0xFF0277BD)
        }
    }

    if (deleteConfirmCustomer != null) {
        val target = deleteConfirmCustomer!!
        DoubleConfirmDeleteDialog(
            itemTitle = "${if (target.isOrder) "Objednávka" else "Kontakt"}: ${target.company.ifBlank { target.name }}",
            itemDescription = "IČ: ${target.ic.ifBlank { "-" }} • Tel: ${target.phone}",
            onDismiss = { deleteConfirmCustomer = null },
            onConfirmedDelete = { viewModel.deleteCustomer(target.id) }
        )
    }

    if (deleteConfirmNabidka != null) {
        val target = deleteConfirmNabidka!!
        DoubleConfirmDeleteDialog(
            itemTitle = "CN Nabídka č. ${target.cislo}",
            itemDescription = "Odběratel: ${target.firma.ifBlank { target.jmeno }} (${target.items.size} položek)",
            onDismiss = { deleteConfirmNabidka = null },
            onConfirmedDelete = { viewModel.deleteNabidka(target.id) }
        )
    }

    if (deleteConfirmZakazka != null) {
        val target = deleteConfirmZakazka!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Zakázka č. ${target.cisloZakazky}",
            itemDescription = "Odběratel: ${target.firma.ifBlank { target.vyrizuje }} • Středisko: ${target.stredisko}",
            onDismiss = { deleteConfirmZakazka = null },
            onConfirmedDelete = { viewModel.deleteZakazka(target.id) }
        )
    }

    if (quickCreatedFaktura != null) {
        FakturaEditDialog(
            initial = quickCreatedFaktura!!,
            allContainers = allContainers,
            adresarList = adresarList,
            catalogItems = catalogItems,
            viewModel = viewModel,
            onDismiss = { quickCreatedFaktura = null },
            onSave = { saved ->
                viewModel.saveFaktura(saved)
                val targetTab = if (saved.typDokladu == "ZALOHOVA_FAKTURA") 5 else 4
                quickCreatedFaktura = null
                selectedTab = targetTab
                onTabChanged(targetTab)
                scope.launch {
                    snackbarHostState.showSnackbar("${if (saved.typDokladu == "ZALOHOVA_FAKTURA") "Zálohová faktura" else "Faktura"} ${saved.cislo} uložena")
                }
            }
        )
    }

    if (invoiceChoiceNabidka != null || invoiceChoiceZakazka != null) {
        val nab = invoiceChoiceNabidka
        val zak = invoiceChoiceZakazka
        val docLabel = if (nab != null) "CN Nabídky ${nab.cislo}" else "Zakázky ${zak?.cisloZakazky}"
        AlertDialog(
            onDismissRequest = {
                invoiceChoiceNabidka = null
                invoiceChoiceZakazka = null
            },
            title = {
                Text("Fakturace", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FAKTURA:", fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B1B3D), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val created = if (nab != null) {
                                    viewModel.createFakturaFromNabidka(nab, isZalohova = false, ucelFaktury = "PRODEJ")
                                } else {
                                    viewModel.createFakturaFromZakazka(zak!!, isZalohova = false, ucelFaktury = "PRODEJ")
                                }
                                invoiceChoiceNabidka = null
                                invoiceChoiceZakazka = null
                                quickCreatedFaktura = created
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Prodej", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                val created = if (nab != null) {
                                    viewModel.createFakturaFromNabidka(nab, isZalohova = false, ucelFaktury = "PRONAJEM")
                                } else {
                                    viewModel.createFakturaFromZakazka(zak!!, isZalohova = false, ucelFaktury = "PRONAJEM")
                                }
                                invoiceChoiceNabidka = null
                                invoiceChoiceZakazka = null
                                quickCreatedFaktura = created
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pronájem", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider()
                    Text("Záloha", fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C2D12), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val created = if (nab != null) {
                                    viewModel.createFakturaFromNabidka(nab, isZalohova = true, ucelFaktury = "PRODEJ")
                                } else {
                                    viewModel.createFakturaFromZakazka(zak!!, isZalohova = true, ucelFaktury = "PRODEJ")
                                }
                                invoiceChoiceNabidka = null
                                invoiceChoiceZakazka = null
                                quickCreatedFaktura = created
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Prodej", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                val created = if (nab != null) {
                                    viewModel.createFakturaFromNabidka(nab, isZalohova = true, ucelFaktury = "PRONAJEM")
                                } else {
                                    viewModel.createFakturaFromZakazka(zak!!, isZalohova = true, ucelFaktury = "PRONAJEM")
                                }
                                invoiceChoiceNabidka = null
                                invoiceChoiceZakazka = null
                                quickCreatedFaktura = created
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pronájem", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        invoiceChoiceNabidka = null
                        invoiceChoiceZakazka = null
                    }
                ) {
                    Text("Zavřít")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = workspaceBgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab != 4 && selectedTab != 5) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingOrder = null
                        editingContact = null
                        editingNabidka = null
                        editingZakazka = null
                        showCreateDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(
                            when (selectedTab) {
                                0, 1, 2 -> "NOVÁ"
                                else -> "NOVÝ"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = topBarColor,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (selectedTab in listOf(1, 3, 4, 5)) {
                Surface(
                    color = topBarColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedTab) {
                                1 -> "NABÍDKY"
                                4 -> "FAKTURY (${faktury.size})"
                                5 -> "ZÁLOHOVÉ (${zalohoveFaktury.size})"
                                else -> "ADRESÁŘ"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(
                            onClick = onOpenDrawer,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("← NASTAVENÍ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                AgendaStatusSubMenuBar(
                    selectedSubTab = ordersStatusSubTab,
                    onSubTabSelected = { ordersStatusSubTab = it },
                    noveCount = ordersList.count { matchesAgendaStatusTab(it.status, "NOVÉ") },
                    realizovaneCount = ordersList.count { matchesAgendaStatusTab(it.status, "REALIZOVANÉ") },
                    zruseneCount = ordersList.count { matchesAgendaStatusTab(it.status, "ZRUŠENÉ") },
                    isDepo2 = depoFilterMode == "Depo 2"
                )
            } else if (selectedTab == 2) {
                AgendaStatusSubMenuBar(
                    selectedSubTab = zakazkyStatusSubTab,
                    onSubTabSelected = { zakazkyStatusSubTab = it },
                    noveCount = zakazky.count { matchesAgendaStatusTab(it.status, "NOVÉ") },
                    realizovaneCount = zakazky.count { matchesAgendaStatusTab(it.status, "REALIZOVANÉ") },
                    zruseneCount = zakazky.count { matchesAgendaStatusTab(it.status, "ZRUŠENÉ") },
                    isDepo2 = depoFilterMode == "Depo 2"
                )
            } else if (selectedTab == 1) {
                AgendaStatusSubMenuBar(
                    selectedSubTab = nabidkyStatusSubTab,
                    onSubTabSelected = { nabidkyStatusSubTab = it },
                    noveCount = nabidky.count { matchesAgendaStatusTab(it.status, "NOVÉ") },
                    realizovaneCount = nabidky.count { matchesAgendaStatusTab(it.status, "REALIZOVANÉ") },
                    zruseneCount = nabidky.count { matchesAgendaStatusTab(it.status, "ZRUŠENÉ") },
                    isDepo2 = depoFilterMode == "Depo 2"
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(workspaceBgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                when (selectedTab) {
                    // ==================== TAB 0: OBJEDNÁVKY ====================
                    0 -> {
                        val filteredOrders = remember(ordersList, ordersStatusSubTab) {
                            ordersList.filter { matchesAgendaStatusTab(it.status, ordersStatusSubTab) }
                        }
                        if (filteredOrders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Žádné poptávky / objednávky v této kategorii", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                            ) {
                                items(filteredOrders, key = { it.id }) { customer ->
                                    val isDepo1 = customer.depo == "Depo 1" || customer.depo.isBlank()
                                    val isDepo2 = customer.depo == "Depo 2"

                                    val cardBgColor = when {
                                        isDepo2 -> Color(0xFFFCF5E8)
                                        isDepo1 -> Color(0xFFEAF3FC)
                                        else -> Color(0xFFFFFBEB)
                                    }
                                    val cardBorderColor = when {
                                        isDepo2 -> Color(0xFF8C6239)
                                        isDepo1 -> Color(0xFF1B5E8B)
                                        else -> Color(0xFFD97706)
                                    }
                                    val headerBgColor = when {
                                        isDepo2 -> Color(0xFF6B532B)
                                        isDepo1 -> Color(0xFF1B5E8B)
                                        else -> Color(0xFFD97706)
                                    }
                                    val depoBadgeColor = when {
                                        isDepo2 -> Color(0xFFE65100)
                                        else -> Color(0xFF0D47A1)
                                    }

                                    Card(
                                        onClick = {
                                            editingOrder = customer
                                            showCreateDialog = true
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = cardBgColor,
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        border = BorderStroke(2.dp, cardBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            // Barevný horní pruh karty Objednávky (vše na 1 řádek + rozbalovací status)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(headerBgColor)
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
                                                        letter = "O",
                                                        textColor = headerBgColor
                                                    )
                                                    if (customer.cisloZakaznika.isNotBlank()) {
                                                        Text(
                                                            text = customer.cisloZakaznika,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SingleLineDepoBadge(
                                                        depoText = customer.depo.ifBlank { "Depo 1" },
                                                        badgeColor = depoBadgeColor,
                                                        onDepoSelected = { newDepo ->
                                                            viewModel.updateCustomerDepo(customer.id, newDepo)
                                                        },
                                                        includeBothOption = true,
                                                        onBothSelected = {
                                                            viewModel.splitOrderIntoBothDepos(customer)
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Objednávka rozdělena pro Depo 1 i Depo 2")
                                                            }
                                                        }
                                                    )
                                                    StatusDropdownBadge(
                                                        currentStatus = customer.status.ifBlank { "NOVÁ" },
                                                        onStatusSelected = { newSt ->
                                                            viewModel.updateCustomerStatus(customer.id, newSt)
                                                        }
                                                    )
                                                }
                                            }

                                            // Zjednodušený náhled objednávky přesně na 3 řádky:
                                            // 1. Název firmy na jeden řádek
                                            // 2. Kontakt
                                            // 3. Záměr
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = customer.company.ifBlank { customer.name.ifBlank { "Zákazník" } },
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val contactParts = listOf(
                                                    customer.name.takeIf { it.isNotBlank() && it != customer.company } ?: "",
                                                    customer.phone,
                                                    customer.email
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Kontakt: ${contactParts.joinToString(" • ").ifBlank { "-" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val zamerParts = listOf(
                                                    customer.inquiryType,
                                                    customer.message
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Záměr: ${zamerParts.joinToString(" – ").ifBlank { "Neuvedeno" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = cardBorderColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (customer.photos.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        customer.photos.take(3).forEachIndexed { pIdx, photoUri ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(width = 54.dp, height = 44.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(Color.DarkGray)
                                                                    .clickable {
                                                                        onImageSavedCallback = { updatedPhoto ->
                                                                            val updatedPhotos = customer.photos.toMutableList()
                                                                            if (pIdx < updatedPhotos.size) {
                                                                                updatedPhotos[pIdx] = updatedPhoto
                                                                            }
                                                                            viewModel.updateCustomerPhotos(customer.id, updatedPhotos)
                                                                        }
                                                                        showImageZoom = photoUri
                                                                    }
                                                            ) {
                                                                AsyncImage(
                                                                    model = resolveCoilImageModel(photoUri),
                                                                    contentDescription = "Foto ${pIdx + 1}",
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "${customer.photos.size}/3 FOTO",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF475569)
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

                    // ==================== TAB 1: CN NABÍDKY ====================
                    1 -> {
                        val filteredNabidky = remember(nabidky, nabidkyStatusSubTab) {
                            nabidky.filter { matchesAgendaStatusTab(it.status, nabidkyStatusSubTab) }
                        }
                        if (nabidky.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Pohoda",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB45309),
                                            maxLines = 1
                                        )
                                    }
                                    Button(
                                        onClick = { pohodaExportTarget = nabidky },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD97706),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Import CN", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (filteredNabidky.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Žádné cenové nabídky v této kategorii", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                            ) {
                                items(filteredNabidky, key = { it.id }) { nabidka ->
                                    val isDepo2 = nabidka.depo == "Depo 2"
                                    val cnBgColor = if (isDepo2) Color(0xFFE0F2FE) else Color(0xFFDBEAFE)
                                    val cnBorderColor = if (isDepo2) Color(0xFF0369A1) else Color(0xFF1D4ED8)
                                    val cnHeaderColor = if (isDepo2) Color(0xFF075985) else Color(0xFF1E40AF)
                                    val cnDepoBadgeColor = if (isDepo2) Color(0xFFE65100) else Color(0xFF0D47A1)

                                    Card(
                                        onClick = {
                                            editingNabidka = nabidka
                                            showCreateDialog = true
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = cnBgColor,
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        border = BorderStroke(2.dp, cnBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(cnHeaderColor)
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
                                                        letter = "CN",
                                                        textColor = cnHeaderColor
                                                    )
                                                    Text(
                                                        text = nabidka.cislo.ifBlank { "NABÍDKA" },
                                                        color = Color.White,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SingleLineDepoBadge(
                                                        depoText = nabidka.depo.ifBlank { "Depo 1" },
                                                        badgeColor = cnDepoBadgeColor,
                                                        onDepoSelected = { newDepo ->
                                                            viewModel.updateNabidkaDepo(nabidka.id, newDepo)
                                                        }
                                                    )
                                                    StatusDropdownBadge(
                                                        currentStatus = nabidka.status.ifBlank { "NOVÁ" },
                                                        onStatusSelected = { newSt ->
                                                            viewModel.updateNabidkaStatus(nabidka.id, newSt)
                                                        }
                                                    )
                                                }
                                            }

                                            // Zjednodušený náhled CN Nabídky na 3 řádky
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                val celkemSDph = nabidka.items.sumOf { it.castkaBez + it.dphVal }.toLong()
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = nabidka.firma.ifBlank { nabidka.jmeno.ifBlank { "Cenová nabídka" } },
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color(0xFF0F172A),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                                                    )
                                                    if (celkemSDph > 0) {
                                                        Surface(
                                                            color = Color.White,
                                                            shape = RoundedCornerShape(6.dp),
                                                            border = BorderStroke(1.dp, cnBorderColor.copy(alpha = 0.5f))
                                                        ) {
                                                            Text(
                                                                text = formatPrice(celkemSDph),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = cnHeaderColor,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                val cnContactParts = listOf(
                                                    nabidka.jmeno.takeIf { it.isNotBlank() && it != nabidka.firma } ?: "",
                                                    nabidka.telefon,
                                                    nabidka.email
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Kontakt: ${cnContactParts.joinToString(" • ").ifBlank { "-" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val zamerCnParts = listOf(
                                                    nabidka.text,
                                                    if (nabidka.items.isNotEmpty()) "Položek: ${nabidka.items.size}" else ""
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Záměr: ${zamerCnParts.joinToString(" • ").ifBlank { "Cenová nabídka" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = cnBorderColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==================== TAB 2: ZAKÁZKY ====================
                    2 -> {
                        val filteredZakazky = remember(zakazky, zakazkyStatusSubTab) {
                            zakazky.filter { matchesAgendaStatusTab(it.status, zakazkyStatusSubTab) }
                        }
                        Surface(
                            color = Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = selectedStrediskoFilter == "VŠE",
                                        onClick = { selectedStrediskoFilter = "VŠE" },
                                        label = { Text("VŠE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                                    )
                                    zakazkaStrediska.forEach { strName ->
                                        FilterChip(
                                            selected = selectedStrediskoFilter.equals(strName, ignoreCase = true),
                                            onClick = { selectedStrediskoFilter = strName },
                                            label = { Text(strName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showManageStrediskaDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Střediska", tint = Color(0xFF0C4A6E), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (filteredZakazky.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Žádné zakázky v této kategorii", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                            ) {
                                items(filteredZakazky, key = { it.id }) { zakazka ->
                                    val isDepo2 = zakazka.depo == "Depo 2"
                                    val zakBgColor = if (isDepo2) Color(0xFFE0F2FE) else Color(0xFFEFF6FF)
                                    val zakBorderColor = if (isDepo2) Color(0xFF0369A1) else Color(0xFF0284C7)
                                    val zakHeaderColor = if (isDepo2) Color(0xFF075985) else Color(0xFF0369A1)
                                    val zakDepoBadgeColor = if (isDepo2) Color(0xFFE65100) else Color(0xFF0D47A1)

                                    Card(
                                        onClick = {
                                            editingZakazka = zakazka
                                            showCreateDialog = true
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = zakBgColor,
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        border = BorderStroke(2.dp, zakBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            // Barevný horní pruh Zakázky (vše na 1 řádek + rozbalovací status)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(zakHeaderColor)
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
                                                        letter = "Z",
                                                        textColor = zakHeaderColor
                                                    )
                                                    Text(
                                                        text = "${zakazka.cisloZakazky} • ${zakazka.stredisko.ifBlank { "Pronájem" }.uppercase()}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SingleLineDepoBadge(
                                                        depoText = zakazka.depo.ifBlank { "Depo 1" },
                                                        badgeColor = zakDepoBadgeColor,
                                                        onDepoSelected = { newDepo ->
                                                            viewModel.updateZakazkaDepo(zakazka.id, newDepo)
                                                        }
                                                    )
                                                    StatusDropdownBadge(
                                                        currentStatus = zakazka.status.ifBlank { "NOVÁ" },
                                                        onStatusSelected = { newSt ->
                                                            viewModel.updateZakazkaStatus(zakazka.id, newSt)
                                                        }
                                                    )
                                                }
                                            }

                                            // Zjednodušený náhled Zakázky přesně na 3 řádky:
                                            // 1. Název firmy na jeden řádek + cena
                                            // 2. Kontakt
                                            // 3. Záměr
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                val celkemSDph = zakazka.items.sumOf { it.castkaBez + it.dphVal }.toLong()
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = zakazka.firma.ifBlank { zakazka.vyrizuje.ifBlank { "Zakázka" } },
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color(0xFF0F172A),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                                                    )
                                                    if (celkemSDph > 0) {
                                                        Surface(
                                                            color = Color.White,
                                                            shape = RoundedCornerShape(6.dp),
                                                            border = BorderStroke(1.dp, zakBorderColor.copy(alpha = 0.5f))
                                                        ) {
                                                            Text(
                                                                text = formatPrice(celkemSDph),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = zakHeaderColor,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                val zakContactParts = listOf(
                                                    zakazka.vyrizuje.takeIf { it.isNotBlank() && it != zakazka.firma } ?: "",
                                                    zakazka.telefon,
                                                    zakazka.email
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Kontakt: ${zakContactParts.joinToString(" • ").ifBlank { "-" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val zakZamerParts = listOf(
                                                    zakazka.stredisko.ifBlank { "Pronájem" },
                                                    zakazka.lokalita.ifBlank { zakazka.ulice }
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Záměr: ${zakZamerParts.joinToString(" • ").ifBlank { "Zakázka" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = zakBorderColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==================== TAB 3: ADRESÁŘ ====================
                    3 -> {
                        var showConfirmClearAllAdresarDialog by remember { mutableStateOf(false) }

                        if (showConfirmClearAllAdresarDialog) {
                            AlertDialog(
                                onDismissRequest = { showConfirmClearAllAdresarDialog = false },
                                title = { Text("Vymazat adresář firem?", fontWeight = FontWeight.Bold) },
                                text = { Text("Opravdu si přejete smazat všechny firmy a kontakty z adresáře (${adresarList.size})? Tato akce se ihned synchronizuje i s PC a Cloudem.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showConfirmClearAllAdresarDialog = false
                                            val count = viewModel.clearAllAdresarCompanies()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Adresář byl kompletně vymazán ($count kontaktů odstraněno).")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                    ) {
                                        Text("Smazat všechny firmy", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showConfirmClearAllAdresarDialog = false }) {
                                        Text("Zrušit")
                                    }
                                }
                            )
                        }

                        val duplicateIcoGroups = remember(allCustomers) {
                            viewModel.findDuplicateIcoGroupsInAdresar()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = adresarSearchQuery,
                                onValueChange = { adresarSearchQuery = it },
                                label = { Text("Hledat") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val loaded = viewModel.reloadPohodaAdresar()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Do adresáře načteno kontaktů z Pohoda souboru: ${viewModel.customers.value.count { it.isAdresar }}")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Načíst Pohoda Adresář", tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Soubor (1 333)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            Button(
                                onClick = {
                                    isSyncingMServerAdresar = true
                                    scope.launch {
                                        val (res, added) = viewModel.fetchAddressBookFromPohoda()
                                        isSyncingMServerAdresar = false
                                        if (res.success) {
                                            snackbarHostState.showSnackbar("Z Pohoda mServeru načteno ${res.syncedCount} kontaktů (nových: $added)")
                                        } else {
                                            snackbarHostState.showSnackbar("mServer: ${res.message}")
                                        }
                                    }
                                },
                                enabled = !isSyncingMServerAdresar,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                if (isSyncingMServerAdresar) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "mServer Sync", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("mServer", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            IconButton(
                                onClick = { showPohodaMServerDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Nastavení mServeru", tint = Color(0xFFD97706))
                            }
                            if (adresarList.isNotEmpty()) {
                                Button(
                                    onClick = { showConfirmClearAllAdresarDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Vymazat adresář", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Vymazat", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                        if (duplicateIcoGroups.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFFBEB),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚠️ Duplicitní firmy se stejným IČ (${duplicateIcoGroups.size})",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (duplicateIcoGroups.size > 1) {
                                            Button(
                                                onClick = {
                                                    val count = viewModel.mergeAllDuplicateIcoInAdresar()
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Sloučeno $count duplicitních firem (průnik všech informací)")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFD97706),
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Sloučit vše", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            }
                                        }
                                    }
                                    duplicateIcoGroups.forEach { (icoNorm, recs) ->
                                        val unionCompanies = recs.map { it.company.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / ")
                                        val unionNames = recs.flatMap { it.name.split(",", ";") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
                                        val unionPhones = recs.flatMap { it.phone.split(",", ";") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
                                        val unionEmails = recs.flatMap { it.email.split(",", ";") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(
                                                        text = "${unionCompanies.ifBlank { unionNames }} • IČ: $icoNorm (${recs.size}×)",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    val unionSummary = listOf(
                                                        if (unionNames.isNotBlank()) "Osoby: $unionNames" else "",
                                                        if (unionPhones.isNotBlank()) "Tel: $unionPhones" else "",
                                                        if (unionEmails.isNotBlank()) "E-mail: $unionEmails" else ""
                                                    ).filter { it.isNotBlank() }.joinToString(" • ")
                                                    if (unionSummary.isNotBlank()) {
                                                        Text(
                                                            text = "Průnik: $unionSummary",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF334155),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Button(
                                                    onClick = {
                                                        val merged = viewModel.mergeCustomerRecordsByIco(icoNorm)
                                                        if (merged != null) {
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Firma ${merged.company} (IČ $icoNorm) sloučena – zachován průnik všech údajů")
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF047857),
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Sloučit", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val filteredAdresar = remember(adresarList, adresarSearchQuery) {
                            val q = adresarSearchQuery.trim().lowercase()
                            if (q.isEmpty()) adresarList else adresarList.filter { c ->
                                c.company.lowercase().contains(q) ||
                                    c.name.lowercase().contains(q) ||
                                    c.ic.lowercase().contains(q) ||
                                    c.dic.lowercase().contains(q) ||
                                    c.phone.lowercase().contains(q) ||
                                    c.email.lowercase().contains(q) ||
                                    c.ulice.lowercase().contains(q) ||
                                    c.cisloZakaznika.lowercase().contains(q)
                            }
                        }
                        if (filteredAdresar.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Žádné kontakty v adresáři", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                            ) {
                                items(filteredAdresar, key = { it.id }) { contact ->
                                    val isDepo2 = contact.depo == "Depo 2"
                                    val adrBgColor = if (isDepo2) Color(0xFFFFECCC) else Color(0xFFDFF2FE)
                                    val adrBorderColor = if (isDepo2) Color(0xFFEF6C00) else Color(0xFF0277BD)
                                    val adrHeaderColor = if (isDepo2) Color(0xFFD84315) else Color(0xFF0277BD)
                                    val adrDepoBadgeColor = if (isDepo2) Color(0xFFE65100) else Color(0xFF0D47A1)

                                    Card(
                                        onClick = {
                                            editingContact = contact
                                            showCreateDialog = true
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = adrBgColor,
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        border = BorderStroke(2.dp, adrBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(adrHeaderColor)
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
                                                        letter = "A",
                                                        textColor = adrHeaderColor
                                                    )
                                                    Text(
                                                        text = contact.cisloZakaznika.ifBlank { "ZÁKAZNÍK" },
                                                        color = Color.White,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val bonitaVal = contact.bonita.ifBlank { "AAA" }.uppercase()
                                                    val bonitaColor = when (bonitaVal) {
                                                        "AAA" -> Color(0xFF15803D)
                                                        "AA" -> Color(0xFF0284C7)
                                                        "A", "BBB" -> Color(0xFFD97706)
                                                        else -> Color(0xFFB91C1C)
                                                    }
                                                    SingleLineDepoBadge(
                                                        depoText = contact.depo.ifBlank { "Depo 1" },
                                                        badgeColor = adrDepoBadgeColor,
                                                        onDepoSelected = { newDepo ->
                                                            viewModel.updateCustomerDepo(contact.id, newDepo)
                                                        }
                                                    )
                                                    StatusDropdownBadge(
                                                        currentStatus = "BONITA: $bonitaVal",
                                                        options = listOf("AAA", "AA", "A", "BBB", "CCC"),
                                                        customColor = bonitaColor,
                                                        onStatusSelected = { sym ->
                                                            val det = when (sym) {
                                                                "AAA" -> "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ"
                                                                "AA" -> "Dobrá platební morálka • Mírné zpoždění splatnosti / nová firma"
                                                                else -> "Riziková platební morálka • Těsně před exekucí / splátkový kalendář FÚ"
                                                            }
                                                            viewModel.updateCustomerBonita(contact.id, sym, det)
                                                        }
                                                    )
                                                }
                                            }
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = contact.company.ifBlank { contact.name.ifBlank { "Zákazník" } },
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val adrContacts = listOf(
                                                    contact.name.takeIf { it.isNotBlank() && it != contact.company } ?: "",
                                                    contact.phone,
                                                    contact.email
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = "Kontakt: ${adrContacts.joinToString(" • ").ifBlank { "-" }}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val adrZamerParts = listOf(
                                                    if (contact.ic.isNotBlank()) "IČ: ${contact.ic}" else "",
                                                    contact.ulice,
                                                    contact.inquiryType.ifBlank { "Zákazník v adresáři" }
                                                ).filter { it.isNotBlank() }
                                                Text(
                                                    text = adrZamerParts.joinToString(" • ").ifBlank { "Zákazník" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = adrBorderColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (contact.photos.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        contact.photos.take(3).forEachIndexed { pIdx, photoUri ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(width = 54.dp, height = 44.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(Color.DarkGray)
                                                                    .clickable {
                                                                        editingImageUri = photoUri
                                                                        onImageSavedCallback = { updatedPhoto ->
                                                                            val updatedPhotos = contact.photos.toMutableList()
                                                                            if (pIdx < updatedPhotos.size) {
                                                                                updatedPhotos[pIdx] = updatedPhoto
                                                                            }
                                                                            viewModel.updateCustomerPhotos(contact.id, updatedPhotos)
                                                                        }
                                                                    }
                                                            ) {
                                                                AsyncImage(
                                                                    model = resolveCoilImageModel(photoUri),
                                                                    contentDescription = "Foto ${pIdx + 1}",
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "${contact.photos.size}/3 FOTO",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF475569)
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

                    // ==================== TAB 4: FAKTURY (PRODEJ / PRONÁJEM) ====================
                    4 -> {
                        FakturaceTabContent(
                            isZalohovaMode = false,
                            fakturyList = faktury,
                            allContainers = allContainers,
                            adresarList = adresarList,
                            catalogItems = catalogItems,
                            activeDepo = activeDepo,
                            pohodaIco = pohodaIco,
                            viewModel = viewModel
                        )
                    }

                    // ==================== TAB 5: ZÁLOHOVÉ FAKTURY ====================
                    5 -> {
                        FakturaceTabContent(
                            isZalohovaMode = true,
                            fakturyList = zalohoveFaktury,
                            allContainers = allContainers,
                            adresarList = adresarList,
                            catalogItems = catalogItems,
                            activeDepo = activeDepo,
                            pohodaIco = pohodaIco,
                            viewModel = viewModel,
                            onSwitchToRegularFaktury = {
                                selectedTab = 4
                                onTabChanged(4)
                            }
                        )
                    }
                }
            }
        }
    }

    // ==================== DIALOG EXPORTU PRO SW POHODA ====================
    if (pohodaExportTarget != null) {
        val targetList = pohodaExportTarget ?: emptyList()
        var icoInput by remember(pohodaIco) { mutableStateOf(pohodaIco) }
        val xmlContent = remember(targetList, icoInput) {
            viewModel.buildPohodaOffersXml(targetList, icoInput)
        }
        val fileName = remember(targetList, activeDepo) {
            if (targetList.size == 1) {
                "POHODA_Import_${targetList.first().cislo.ifBlank { "CN" }}.xml"
            } else {
                "POHODA_Import_Vsechny_CN_${activeDepo.replace(" ", "")}.xml"
            }
        }

        AlertDialog(
            onDismissRequest = { pohodaExportTarget = null },
            title = {
                Text(
                    text = "Pohoda",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = icoInput,
                        onValueChange = {
                            icoInput = it
                            viewModel.setPohodaIco(it)
                        },
                        label = { Text("IČO") },
                        placeholder = { Text("Např. 12345678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Náhled generovaného XML ($fileName):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp)
                    ) {
                        Text(
                            text = xmlContent,
                            color = Color(0xFF7DD3FC),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/xml"
                                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                                    putExtra(Intent.EXTRA_TEXT, xmlContent)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Odeslat XML pro Pohodu"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sdílet XML")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingXmlToSave = xmlContent
                        saveXmlLauncher.launch(fileName)
                        pohodaExportTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Uložit XML pro Pohodu", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pohodaExportTarget = null }) {
                    Text("Zavřít")
                }
            }
        )
    }

    // ==================== DIALOG VÝBĚRU POLOŽEK PRO CN / ZAKÁZKU ====================
    if (editingItemsNabidka != null || editingItemsZakazka != null) {
        val isNab = editingItemsNabidka != null
        val docTitle = if (isNab) "Položky CN ${editingItemsNabidka?.cislo}" else "Položky Zakázky ${editingItemsZakazka?.cisloZakazky}"
        var currentItems by remember(editingItemsNabidka, editingItemsZakazka) {
            mutableStateOf(editingItemsNabidka?.items ?: editingItemsZakazka?.items ?: emptyList())
        }
        var filterCat by remember { mutableStateOf("Vše") }

        var customKod by remember { mutableStateOf("POL") }
        var customText by remember { mutableStateOf("") }
        var customQty by remember { mutableStateOf("1") }
        var customPrice by remember { mutableStateOf("0") }
        var customDph by remember { mutableStateOf("21") }

        AlertDialog(
            onDismissRequest = {
                editingItemsNabidka = null
                editingItemsZakazka = null
            },
            title = {
                Text("Položky", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentItems.isNotEmpty()) {
                        currentItems.forEachIndexed { idx, item ->
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = item.text,
                                            onValueChange = { newTxt ->
                                                currentItems = currentItems.toMutableList().also { list ->
                                                    list[idx] = item.copy(text = newTxt)
                                                }
                                            },
                                            label = { Text("Položka") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { currentItems = currentItems.filterIndexed { i, _ -> i != idx } }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Odebrat", tint = Color(0xFFC62828))
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = if (item.mnozstvi % 1.0 == 0.0) item.mnozstvi.toInt().toString() else item.mnozstvi.toString(),
                                            onValueChange = { v ->
                                                val q = v.replace(",", ".").toDoubleOrNull() ?: 0.0
                                                val base = q * item.jCena * (1.0 - item.sleva / 100.0)
                                                val dphV = if (item.pdp) 0.0 else base * (item.dphPct / 100.0)
                                                currentItems = currentItems.toMutableList().also { list ->
                                                    list[idx] = item.copy(mnozstvi = q, castkaBez = base, dphVal = dphV)
                                                }
                                            },
                                            label = { Text("Ks") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.7f)
                                        )
                                        OutlinedTextField(
                                            value = if (item.jCena % 1.0 == 0.0) item.jCena.toLong().toString() else item.jCena.toString(),
                                            onValueChange = { v ->
                                                val p = v.replace(",", ".").toDoubleOrNull() ?: 0.0
                                                val base = item.mnozstvi * p * (1.0 - item.sleva / 100.0)
                                                val dphV = if (item.pdp) 0.0 else base * (item.dphPct / 100.0)
                                                currentItems = currentItems.toMutableList().also { list ->
                                                    list[idx] = item.copy(jCena = p, castkaBez = base, dphVal = dphV)
                                                }
                                            },
                                            label = { Text("Cena") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.3f)
                                        )
                                        OutlinedTextField(
                                            value = item.sleva.toInt().toString(),
                                            onValueChange = { v ->
                                                val sl = v.replace(",", ".").toDoubleOrNull() ?: 0.0
                                                val base = item.mnozstvi * item.jCena * (1.0 - sl / 100.0)
                                                val dphV = if (item.pdp) 0.0 else base * (item.dphPct / 100.0)
                                                currentItems = currentItems.toMutableList().also { list ->
                                                    list[idx] = item.copy(sleva = sl, castkaBez = base, dphVal = dphV)
                                                }
                                            },
                                            label = { Text("Sleva") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.8f)
                                        )
                                    }
                                    Text(
                                        text = "Bez DPH: ${item.castkaBez.toLong()} Kč | DPH (${item.dphPct.toInt()}%): ${item.dphVal.toLong()} Kč | Celkem s DPH: ${(item.castkaBez + item.dphVal).toLong()} Kč",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E8B)
                                    )
                                }
                            }
                        }
                        val sumBez = currentItems.sumOf { it.castkaBez }
                        val sumSDph = currentItems.sumOf { it.castkaBez + it.dphVal }
                        Text(
                            text = "Celkem bez DPH: ${sumBez.toLong()} Kč | Celkem s DPH: ${sumSDph.toLong()} Kč",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F3D5E),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider()
                    }

                    // Přidání vlastní položky s cenou
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Položka", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF1E3A8A))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = customKod,
                                    onValueChange = { customKod = it },
                                    label = { Text("Kód") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { customText = it },
                                    label = { Text("Název") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.8f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customQty,
                                    onValueChange = { customQty = it },
                                    label = { Text("Ks") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.7f)
                                )
                                OutlinedTextField(
                                    value = customPrice,
                                    onValueChange = { customPrice = it },
                                    label = { Text("Cena") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )
                                Button(
                                    onClick = {
                                        val q = customQty.replace(",", ".").toDoubleOrNull() ?: 1.0
                                        val p = customPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
                                        val dph = customDph.toDoubleOrNull() ?: 21.0
                                        val base = q * p
                                        val dphV = base * (dph / 100.0)
                                        val newItem = NabidkaItemRecord(
                                            id = "ITEM-" + System.currentTimeMillis(),
                                            kod = customKod.ifBlank { "POL" },
                                            text = customText.ifBlank { "Položka zakázky / CN" },
                                            mnozstvi = q,
                                            jCena = p,
                                            sleva = 0.0,
                                            dphPct = dph,
                                            pdp = false,
                                            castkaBez = base,
                                            dphVal = dphV
                                        )
                                        currentItems = currentItems + newItem
                                        customText = ""
                                        customPrice = "0"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B))
                                ) {
                                    Text("+ Přidat", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Text("Katalog", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = filterCat == "Vše",
                            onClick = { filterCat = "Vše" },
                            label = { Text("Vše") }
                        )
                        FilterChip(
                            selected = filterCat == "Kontejnery",
                            onClick = { filterCat = "Kontejnery" },
                            label = { Text("Kontejnery") }
                        )
                        FilterChip(
                            selected = filterCat == "Vybavení",
                            onClick = { filterCat = "Vybavení" },
                            label = { Text("Vybavení") }
                        )
                        FilterChip(
                            selected = filterCat == "Přeprava",
                            onClick = { filterCat = "Přeprava" },
                            label = { Text("Přeprava") }
                        )
                    }
                    val filteredCatalog = when (filterCat) {
                        "Vše" -> catalogItems
                        "Kontejnery" -> catalogItems.filter { it.sekce == "KONTEJNERY" }
                        "Vybavení" -> catalogItems.filter { it.kategorie == "Vybavení" }
                        "Přeprava" -> catalogItems.filter { it.kategorie == "Přeprava" || it.sekce == "PŘEPRAVA" }
                        else -> catalogItems.filter { it.kategorie == filterCat }
                    }
                    val grouped = filteredCatalog.groupBy { it.kategorie }
                    grouped.forEach { (catName, list) ->
                        Text(
                            text = when (catName) {
                                "Přeprava" -> "SEKCE: PŘEPRAVA"
                                "Vybavení" -> "SEKCE: VYBAVENÍ"
                                else -> catName.uppercase()
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = when (catName) {
                                "Přeprava" -> Color(0xFFE65100)
                                "Vybavení" -> Color(0xFF0284C7)
                                else -> Color(0xFF0D47A1)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        list.sortedWith(compareBy<CatalogItemRecord> { it.poradi }.thenBy { it.oznaceni }).forEach { catItem ->
                            OutlinedButton(
                                onClick = {
                                    currentItems = currentItems + viewModel.catalogItemToNabidkaItem(catItem)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${catItem.oznaceni} – ${catItem.nazev}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${catItem.pocet.toInt()} ks | Bez DPH: ${catItem.cenaBezDph.toLong()} Kč | DPH ${catItem.dphPct.toInt()}% | Sleva ${catItem.slevaPct.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = "+ ${catItem.celkemSDph.toLong()} Kč",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0284C7),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isNab && editingItemsNabidka != null) {
                            viewModel.updateNabidkaItems(editingItemsNabidka!!.id, currentItems)
                        } else if (!isNab && editingItemsZakazka != null) {
                            viewModel.updateZakazkaItems(editingItemsZakazka!!.id, currentItems)
                        }
                        editingItemsNabidka = null
                        editingItemsZakazka = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Položky dokladu byly uloženy a synchronizovány")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Uložit položky (${currentItems.size})", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingItemsNabidka = null
                        editingItemsZakazka = null
                    }
                ) {
                    Text("Zavřít")
                }
            }
        )
    }

    // ==================== UNIVERZÁLNÍ DIALOG PRO TVORBU / ÚPRAVU (OBJEDNÁVKA, CN, ZAKÁZKA, ADRESÁŘ) ====================
    if (showCreateDialog) {
        val isObjednavka = editingOrder != null || (editingContact == null && editingNabidka == null && editingZakazka == null && selectedTab == 0)
        val isNabidka = editingNabidka != null || (editingOrder == null && editingContact == null && editingZakazka == null && selectedTab == 1)
        val isZakazka = editingZakazka != null || (editingOrder == null && editingContact == null && editingNabidka == null && selectedTab == 2)
        val isAdresar = editingContact != null || (editingOrder == null && editingNabidka == null && editingZakazka == null && selectedTab == 3)

        var firma by remember {
            mutableStateOf(
                editingOrder?.company
                    ?: editingContact?.company
                    ?: editingNabidka?.firma
                    ?: editingZakazka?.firma
                    ?: ""
            )
        }
        var jmeno by remember {
            mutableStateOf(
                editingOrder?.name
                    ?: editingContact?.name
                    ?: editingNabidka?.jmeno
                    ?: editingZakazka?.vyrizuje
                    ?: ""
            )
        }
        var ic by remember {
            mutableStateOf(
                editingOrder?.ic
                    ?: editingContact?.ic
                    ?: editingNabidka?.ic
                    ?: editingZakazka?.ic
                    ?: ""
            )
        }
        var dic by remember {
            mutableStateOf(
                editingOrder?.dic
                    ?: editingContact?.dic
                    ?: editingNabidka?.dic
                    ?: editingZakazka?.dic
                    ?: ""
            )
        }
        var telefon by remember {
            mutableStateOf(
                editingOrder?.phone
                    ?: editingContact?.phone
                    ?: editingNabidka?.telefon
                    ?: editingZakazka?.telefon
                    ?: ""
            )
        }
        var email by remember {
            mutableStateOf(
                editingOrder?.email
                    ?: editingContact?.email
                    ?: editingNabidka?.email
                    ?: editingZakazka?.email
                    ?: ""
            )
        }
        var ulice by remember {
            mutableStateOf(
                editingOrder?.ulice
                    ?: editingContact?.ulice
                    ?: editingNabidka?.ulice
                    ?: editingZakazka?.ulice
                    ?: ""
            )
        }
        var platnost by remember { mutableStateOf(editingNabidka?.platnost ?: "") }
        var popis by remember {
            mutableStateOf(
                editingOrder?.message
                    ?: editingNabidka?.text
                    ?: editingZakazka?.lokalita
                    ?: ""
            )
        }
        var zamer by remember { mutableStateOf(editingOrder?.inquiryType?.ifBlank { "Koupě kontejneru" } ?: "Koupě kontejneru") }
        var orderStatus by remember { mutableStateOf(editingOrder?.status?.ifBlank { "NOVÁ" } ?: "NOVÁ") }
        var cisloDokladu by remember {
            mutableStateOf(
                editingContact?.cisloZakaznika
                    ?: editingNabidka?.cislo
                    ?: editingZakazka?.cisloZakazky
                    ?: ""
            )
        }
        var cisloNabidkyVazba by remember { mutableStateOf(editingZakazka?.cisloNabidky ?: "") }
        var zdrojNabidkaId by remember { mutableStateOf(editingZakazka?.zdrojNabidkaId ?: "") }
        var adresarIdVazba by remember {
            mutableStateOf(
                editingNabidka?.adresarId
                    ?: editingZakazka?.adresarId
                    ?: ""
            )
        }
        var depoChoice by remember {
            mutableStateOf(
                (editingOrder?.depo
                    ?: editingContact?.depo
                    ?: editingNabidka?.depo
                    ?: editingZakazka?.depo
                    ?: activeDepo).ifBlank { activeDepo }
            )
        }
        var strediskoChoice by remember {
            mutableStateOf(editingZakazka?.stredisko?.ifBlank { "Pronájem" } ?: "Pronájem")
        }
        var prepravaInfoChoice by remember {
            mutableStateOf(editingZakazka?.prepravaInfo ?: "")
        }
        var cisloObjednavky by remember { mutableStateOf(editingZakazka?.cisloObjednavky ?: "") }
        var osobaKPrevzeti by remember { mutableStateOf(editingZakazka?.osobaKPrevzeti ?: "") }
        var kontaktPrevzeti by remember { mutableStateOf(editingZakazka?.kontaktPrevzeti ?: "") }
        var pocetOB by remember { mutableStateOf(editingZakazka?.pocetOB?.ifBlank { "0" } ?: "0") }
        var pocetSK by remember { mutableStateOf(editingZakazka?.pocetSK?.ifBlank { "0" } ?: "0") }
        var pocetSA by remember { mutableStateOf(editingZakazka?.pocetSA?.ifBlank { "0" } ?: "0") }
        var pocetMA by remember { mutableStateOf(editingZakazka?.pocetMA?.ifBlank { "0" } ?: "0") }
        var pocetZI by remember { mutableStateOf(editingZakazka?.pocetZI?.ifBlank { "0" } ?: "0") }
        var pocetST by remember { mutableStateOf(editingZakazka?.pocetST?.ifBlank { "0" } ?: "0") }
        var pocetSK2 by remember { mutableStateOf(editingZakazka?.pocetSK2?.ifBlank { "0" } ?: "0") }
        var pocetSA2 by remember { mutableStateOf(editingZakazka?.pocetSA2?.ifBlank { "0" } ?: "0") }
        var poznamkaZObjednavky by remember { mutableStateOf(editingZakazka?.poznamkaZObjednavky ?: "") }
        var splatnostZak by remember { mutableStateOf(editingZakazka?.splatnost?.ifBlank { "14" } ?: "14") }
        var obdobiOd by remember { mutableStateOf(editingZakazka?.obdobiOd ?: "") }
        var obdobiDo by remember { mutableStateOf(editingZakazka?.obdobiDo ?: "") }
        var datumDodano by remember { mutableStateOf(editingZakazka?.datumDodano ?: "") }
        var datumVraceno by remember { mutableStateOf(editingZakazka?.datumVraceno ?: "") }
        var preprava1 by remember { mutableStateOf(editingZakazka?.preprava1 ?: "") }
        var zpet1 by remember { mutableStateOf(editingZakazka?.zpet1 ?: "") }
        var preprava2 by remember { mutableStateOf(editingZakazka?.preprava2 ?: "") }
        var zpet2 by remember { mutableStateOf(editingZakazka?.zpet2 ?: "") }
        var cenovaNabidkaCislo by remember { mutableStateOf(editingZakazka?.cenovaNabidkaCislo?.ifBlank { editingZakazka?.cisloNabidky.orEmpty() } ?: "") }
        var protokolTam by remember { mutableStateOf(editingZakazka?.protokolTam ?: "") }
        var smlouvaCislo by remember { mutableStateOf(editingZakazka?.smlouvaCislo ?: "") }
        var protokolZpet by remember { mutableStateOf(editingZakazka?.protokolZpet ?: "") }
        var kauceDatum by remember { mutableStateOf(editingZakazka?.kauceDatum ?: "") }
        var kauceCastka by remember { mutableStateOf(editingZakazka?.kauceCastka ?: "") }
        var kauceVraceni by remember { mutableStateOf(editingZakazka?.kauceVraceni ?: "") }
        var kauceVraceniCastka by remember { mutableStateOf(editingZakazka?.kauceVraceniCastka ?: "") }
        var interniPoznamka by remember { mutableStateOf(editingZakazka?.interniPoznamka ?: "") }
        var fakturaceDatum by remember { mutableStateOf(editingZakazka?.fakturaceDatum ?: "") }
        var fakturaceObdobi by remember { mutableStateOf(editingZakazka?.fakturaceObdobi ?: "") }
        var zakazkaPhotos by remember {
            val initZPhotos = (editingZakazka?.photos ?: emptyList()).take(4).toMutableList()
            while (initZPhotos.size < 4) initZPhotos.add("")
            mutableStateOf(initZPhotos)
        }

        var bonitaChoice by remember {
            mutableStateOf(editingContact?.bonita?.ifBlank { "AAA" } ?: "AAA")
        }
        var bonitaDetailChoice by remember {
            mutableStateOf(editingContact?.bonitaDetail?.ifBlank { "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ" } ?: "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ")
        }
        var pickedItems by remember {
            mutableStateOf(
                editingNabidka?.items
                    ?: editingZakazka?.items
                    ?: emptyList()
            )
        }
        var showCatalogSelectorInCreate by remember { mutableStateOf(false) }
        var showContainerSelectorInCreate by remember { mutableStateOf(false) }
        var showAdresarPicker by remember { mutableStateOf(false) }
        var showNabidkaPicker by remember { mutableStateOf(false) }

        var kodPolozky by remember { mutableStateOf("KONT") }
        var mnozstvi by remember { mutableStateOf("1") }
        var jCena by remember { mutableStateOf("0") }
        var dphPct by remember { mutableStateOf("21") }
        var sleva by remember { mutableStateOf("0") }
        var pdp by remember { mutableStateOf(false) }

        var customerPhotos by remember {
            val initPhotos = (editingContact?.photos ?: editingOrder?.photos ?: emptyList()).take(3).toMutableList()
            while (initPhotos.size < 3) initPhotos.add("")
            mutableStateOf(initPhotos)
        }

        val doSaveFullZakazka: (String) -> ZakazkaRecord = { firmVal ->
            viewModel.saveFullZakazka(
                id = editingZakazka?.id ?: "",
                cisloZakazky = cisloDokladu,
                datumCasZakazky = editingZakazka?.datumCasZakazky ?: "",
                cisloNabidky = cisloNabidkyVazba.ifBlank { cenovaNabidkaCislo },
                cisloObjednavky = cisloObjednavky,
                stredisko = strediskoChoice,
                prepravaInfo = prepravaInfoChoice,
                firma = firmVal,
                vyrizuje = jmeno,
                telefon = telefon,
                email = email,
                ic = ic,
                dic = dic,
                ulice = ulice,
                lokalita = popis,
                osobaKPrevzeti = osobaKPrevzeti,
                kontaktPrevzeti = kontaktPrevzeti,
                pocetOB = pocetOB,
                pocetSK = pocetSK,
                pocetSA = pocetSA,
                pocetMA = pocetMA,
                pocetZI = pocetZI,
                pocetST = pocetST,
                pocetSK2 = pocetSK2,
                pocetSA2 = pocetSA2,
                poznamkaZObjednavky = poznamkaZObjednavky,
                splatnost = splatnostZak,
                obdobiOd = obdobiOd,
                obdobiDo = obdobiDo,
                datumDodano = datumDodano,
                datumVraceno = datumVraceno,
                preprava1 = preprava1,
                zpet1 = zpet1,
                preprava2 = preprava2,
                zpet2 = zpet2,
                cenovaNabidkaCislo = cenovaNabidkaCislo,
                protokolTam = protokolTam,
                smlouvaCislo = smlouvaCislo,
                protokolZpet = protokolZpet,
                kauceDatum = kauceDatum,
                kauceCastka = kauceCastka,
                kauceVraceni = kauceVraceni,
                kauceVraceniCastka = kauceVraceniCastka,
                interniPoznamka = interniPoznamka,
                photos = zakazkaPhotos.filter { it.isNotBlank() },
                fakturaceDatum = fakturaceDatum,
                fakturaceObdobi = fakturaceObdobi,
                depo = depoChoice,
                adresarId = adresarIdVazba,
                zdrojNabidkaId = zdrojNabidkaId,
                status = editingZakazka?.status ?: "NOVÁ",
                items = pickedItems
            )
        }

        val closeDialog = {
            showCreateDialog = false
            editingOrder = null
            editingContact = null
            editingNabidka = null
            editingZakazka = null
        }

        AlertDialog(
            onDismissRequest = closeDialog,
            title = {
                Text(
                    when {
                        editingOrder != null -> "Objednávka"
                        editingContact != null -> "Zákazník"
                        editingNabidka != null -> "Nabídka"
                        editingZakazka != null -> "Zakázka"
                        isObjednavka -> "Objednávka"
                        isNabidka -> "Nabídka"
                        isZakazka -> "Zakázka"
                        else -> "Zákazník"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isObjednavka && editingOrder != null) {
                        val ord = editingOrder!!
                        Surface(
                            color = Color(0xFFEAF3FC),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF1B5E8B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val created = viewModel.createNabidkaFromCustomer(ord)
                                            closeDialog()
                                            selectedTab = 1
                                            onTabChanged(1)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Vytvořena CN Nabídka ${created.cislo}")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ CN Nabídka", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            val created = viewModel.createZakazkaFromCustomer(ord)
                                            closeDialog()
                                            selectedTab = 2
                                            onTabChanged(2)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Vytvořena Zakázka ${created.cisloZakazky}")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ Zakázka", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = {
                                            closeDialog()
                                            deleteConfirmCustomer = ord
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFD32F2F))
                                    }
                                }
                                PrintShareActionBar(
                                    onPrint = {
                                        val pdf = generateCustomerCardPdf(context, ord)
                                        printPdfFile(context, pdf, "Objednavka_${ord.cisloZakaznika.ifBlank { ord.id }}")
                                    },
                                    onWhatsApp = {
                                        val pdf = generateCustomerCardPdf(context, ord)
                                        sharePdfViaWhatsApp(context, pdf, "OBJEDNÁVKA ${ord.cisloZakaznika}\nFirma: ${ord.company.ifBlank { ord.name }}\nZáměr: ${ord.inquiryType}")
                                    },
                                    onEmail = {
                                        val pdf = generateCustomerCardPdf(context, ord)
                                        sharePdfViaEmail(context, pdf, ord.email, "Objednávka ${ord.cisloZakaznika}", "Dobrý den,\nv příloze zasíláme kartu objednávky ${ord.cisloZakaznika}.")
                                    }
                                )
                            }
                        }
                    }
                    // NAHOŘE U POLOŽKY ULOŽIT ZAKÁZKU NEBO CN PŘIDÁNO | PŘEPRAVA | A | FAKTURACE |
                    if (isNabidka) {
                        Surface(
                            color = Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF1D4ED8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val saved = if (editingNabidka != null) {
                                            viewModel.saveFullNabidka(
                                                id = editingNabidka!!.id,
                                                cislo = cisloDokladu,
                                                datum = editingNabidka!!.datum,
                                                platnost = platnost,
                                                firma = firma.ifBlank { "Zákazník" },
                                                jmeno = jmeno,
                                                telefon = telefon,
                                                email = email,
                                                text = popis,
                                                depo = depoChoice,
                                                ic = ic,
                                                dic = dic,
                                                ulice = ulice,
                                                adresarId = adresarIdVazba,
                                                items = pickedItems
                                            )
                                        } else {
                                            viewModel.createCustomNabidka(
                                                firma = firma.ifBlank { "Zákazník" },
                                                jmeno = jmeno,
                                                telefon = telefon,
                                                email = email,
                                                text = popis,
                                                depo = depoChoice,
                                                ic = ic,
                                                dic = dic,
                                                ulice = ulice,
                                                platnost = platnost,
                                                kodPolozky = kodPolozky,
                                                mnozstvi = mnozstvi.toDoubleOrNull() ?: 1.0,
                                                jCena = jCena.toDoubleOrNull() ?: 0.0,
                                                dphPct = dphPct.toDoubleOrNull() ?: 21.0,
                                                sleva = sleva.toDoubleOrNull() ?: 0.0,
                                                pdp = pdp,
                                                selectedCatalogItems = pickedItems
                                            )
                                        }
                                        closeDialog()
                                        invoiceChoiceNabidka = saved
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0B1B3D),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Text(
                                        "Faktura / Záloha",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (isZakazka) {
                        Surface(
                            color = Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF0284C7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (firma.isNotBlank() || jmeno.isNotBlank()) {
                                                val saved = doSaveFullZakazka(firma)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Zakázka ${saved.cisloZakazky} (${saved.stredisko}) uložena")
                                                }
                                                closeDialog()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0284C7),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Text(
                                            "Uložit",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val saved = doSaveFullZakazka(firma.ifBlank { "Zákazník" })
                                            closeDialog()
                                            prepravaModalZakazka = saved
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE65100),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Text(
                                            "Přeprava",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val saved = doSaveFullZakazka(firma.ifBlank { "Zákazník" })
                                            closeDialog()
                                            invoiceChoiceZakazka = saved
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0B1B3D),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Text(
                                            "Fakturace",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Středisko", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0C4A6E))
                                    TextButton(
                                        onClick = { showManageStrediskaDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Středisko", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    zakazkaStrediska.forEach { stOpt ->
                                        FilterChip(
                                            selected = strediskoChoice.equals(stOpt, ignoreCase = true),
                                            onClick = { strediskoChoice = stOpt },
                                            label = { Text(stOpt, fontWeight = FontWeight.Bold, maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tlačítka pro načtení z Adresáře nebo z CN Nabídky
                    if (!isAdresar) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showAdresarPicker = !showAdresarPicker
                                    showNabidkaPicker = false
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (showAdresarPicker) "Skrýt Adresář" else "Z Adresáře (${adresarList.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isZakazka && allNabidky.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        showNabidkaPicker = !showNabidkaPicker
                                        showAdresarPicker = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        if (showNabidkaPicker) "Skrýt CN" else "Z CN Nabídky (${allNabidky.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (showAdresarPicker) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Adresář", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                                    adresarList.take(25).forEach { adr ->
                                        OutlinedButton(
                                            onClick = {
                                                firma = adr.company.ifBlank { adr.name }
                                                jmeno = adr.name
                                                ic = adr.ic
                                                dic = adr.dic
                                                ulice = adr.ulice
                                                telefon = adr.phone
                                                email = adr.email
                                                adresarIdVazba = adr.id
                                                if (adr.depo.isNotBlank()) depoChoice = adr.depo
                                                if (popis.isBlank() && adr.message.isNotBlank()) popis = adr.message
                                                showAdresarPicker = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${adr.company.ifBlank { adr.name }} (${adr.name}) • IČ: ${adr.ic.ifBlank { "-" }} • ${adr.phone}",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showNabidkaPicker) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Nabídka", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                                    allNabidky.take(25).forEach { nab ->
                                        OutlinedButton(
                                            onClick = {
                                                firma = nab.firma
                                                jmeno = nab.jmeno
                                                ic = nab.ic
                                                dic = nab.dic
                                                ulice = nab.ulice
                                                telefon = nab.telefon
                                                email = nab.email
                                                popis = nab.text.ifBlank { nab.ulice }
                                                cisloNabidkyVazba = nab.cislo
                                                zdrojNabidkaId = nab.id
                                                adresarIdVazba = nab.adresarId
                                                if (nab.depo.isNotBlank()) depoChoice = nab.depo
                                                if (nab.items.isNotEmpty()) pickedItems = nab.items
                                                showNabidkaPicker = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${nab.cislo} – ${nab.firma.ifBlank { nab.jmeno }} (${nab.items.size} pol.)",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = depoChoice == "Depo 1",
                            onClick = { depoChoice = "Depo 1" },
                            label = { Text("Depo 1") }
                        )
                        FilterChip(
                            selected = depoChoice == "Depo 2",
                            onClick = { depoChoice = "Depo 2" },
                            label = { Text("Depo 2") }
                        )
                    }

                    var isAresLoading by remember { mutableStateOf(false) }
                    var aresCandidates by remember { mutableStateOf<List<AresSubjectResult>>(emptyList()) }
                    var aresStatusMessage by remember { mutableStateOf<String?>(null) }

                    if (isAdresar || isNabidka || isZakazka) {
                        OutlinedTextField(
                            value = cisloDokladu,
                            onValueChange = { cisloDokladu = it },
                            label = { Text("Číslo") },
                            placeholder = {
                                Text(
                                    when {
                                        isAdresar -> "CUS-..."
                                        isNabidka -> "CN-..."
                                        else -> "ZAK-..."
                                    }
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ARES VYHLEDÁVÁNÍ PODLE IČ NEBO NÁZVU FIRMY
                    Surface(
                        color = Color(0xFFEAF3FC),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1B5E8B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ARES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1B5E8B),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val q = ic.trim().ifBlank { firma.trim() }
                                        if (q.isBlank()) {
                                            aresStatusMessage = "Zadejte nejprve IČ nebo Název firmy"
                                        } else {
                                            isAresLoading = true
                                            aresStatusMessage = "Hledám v ARES..."
                                            viewModel.lookupAresSubjects(
                                                query = q,
                                                onResult = { list ->
                                                    isAresLoading = false
                                                    if (list.size == 1) {
                                                        val hit = list.first()
                                                        firma = hit.company
                                                        ic = hit.ico
                                                        if (hit.dic.isNotBlank()) dic = hit.dic
                                                        if (hit.address.isNotBlank()) ulice = hit.address
                                                        bonitaChoice = hit.bonita
                                                        bonitaDetailChoice = hit.bonitaDetail
                                                        aresCandidates = emptyList()
                                                        aresStatusMessage = "Doplněno z ARES: ${hit.company} • Bonita: ${hit.bonita} (${hit.bonitaDetail})"
                                                    } else {
                                                        aresCandidates = list
                                                        aresStatusMessage = "Nalezeno ${list.size} subjektů – klikněte pro výběr:"
                                                    }
                                                },
                                                onError = { err ->
                                                    isAresLoading = false
                                                    aresStatusMessage = err
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isAresLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isAresLoading) "Hledám..." else "ARES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!aresStatusMessage.isNullOrBlank()) {
                                Text(
                                    text = aresStatusMessage!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF0F3D5E),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (aresCandidates.isNotEmpty()) {
                                aresCandidates.forEach { cand ->
                                    OutlinedButton(
                                        onClick = {
                                            firma = cand.company
                                            ic = cand.ico
                                            if (cand.dic.isNotBlank()) dic = cand.dic
                                            if (cand.address.isNotBlank()) ulice = cand.address
                                            bonitaChoice = cand.bonita
                                            bonitaDetailChoice = cand.bonitaDetail
                                            aresCandidates = emptyList()
                                            aresStatusMessage = "Doplněno z ARES: ${cand.company} • Bonita: ${cand.bonita}"
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${cand.company} • IČ: ${cand.ico} • ${cand.address}",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = firma,
                        onValueChange = { firma = it },
                        label = { Text("Firma") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = jmeno,
                        onValueChange = { jmeno = it },
                        label = { Text("Jméno") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ic,
                            onValueChange = { ic = it },
                            label = { Text("IČ") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dic,
                            onValueChange = { dic = it },
                            label = { Text("DIČ") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val duplicateIcoMatches = remember(ic, allCustomers, editingContact) {
                        viewModel.findExistingAdresarByIco(ic, editingContact?.id)
                    }
                    if (duplicateIcoMatches.isNotEmpty()) {
                        val matchFirst = duplicateIcoMatches.first()
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFD97706)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⚠️ Firma s tímto IČ (${ic.trim()}) již v Adresáři existuje: ${matchFirst.company.ifBlank { matchFirst.name }}",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF92400E)
                                )
                                Button(
                                    onClick = {
                                        viewModel.saveAdresarContact(
                                            id = editingContact?.id ?: "",
                                            cisloZakaznika = cisloDokladu,
                                            company = firma,
                                            name = jmeno,
                                            phone = telefon,
                                            email = email,
                                            ic = ic,
                                            dic = dic,
                                            ulice = ulice,
                                            depo = depoChoice,
                                            bonita = bonitaChoice,
                                            bonitaDetail = bonitaDetailChoice
                                        )
                                        val merged = viewModel.mergeCustomerRecordsByIco(ic)
                                        if (merged != null) {
                                            firma = merged.company
                                            jmeno = merged.name
                                            telefon = merged.phone
                                            email = merged.email
                                            ic = merged.ic
                                            dic = merged.dic
                                            ulice = merged.ulice
                                            bonitaChoice = merged.bonita
                                            bonitaDetailChoice = merged.bonitaDetail
                                            adresarIdVazba = merged.id
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Duplicitní záznamy pro IČ ${merged.ic} byly sloučeny (průnik všech dat)")
                                            }
                                            if (isAdresar) {
                                                closeDialog()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF047857),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                ) {
                                    Text(
                                        "Sloučit s existující firmou (průnik informací)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = ulice,
                        onValueChange = { ulice = it },
                        label = { Text("Adresa") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = telefon,
                        onValueChange = { telefon = it },
                        label = { Text("Telefon") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isAdresar) {
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF15803D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Bonita",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF166534)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("AAA", "AA", "A", "BBB", "CCC").forEach { sym ->
                                        FilterChip(
                                            selected = bonitaChoice == sym,
                                            onClick = {
                                                bonitaChoice = sym
                                                bonitaDetailChoice = when (sym) {
                                                    "AAA" -> "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ"
                                                    "AA" -> "Dobrá platební morálka • Bez záznamu v ISIR a exekucích"
                                                    "A", "BBB" -> "Střední bonita • Splátkový kalendář s Fin. úřadem / delší splatnost"
                                                    else -> "Riziková bonita CCC • Těsně před exekucí / záznam v ISIR"
                                                }
                                            },
                                            label = { Text(sym, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = bonitaDetailChoice,
                                    onValueChange = { bonitaDetailChoice = it },
                                    label = { Text("Bonita") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (isObjednavka) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = zamer.contains("Koupě", ignoreCase = true) || zamer == "PRODEJ",
                                onClick = { zamer = "Koupě kontejneru" },
                                label = { Text("Koupě / Prodej") }
                            )
                            FilterChip(
                                selected = zamer.contains("Pronájem", ignoreCase = true) || zamer == "PRONÁJEM",
                                onClick = { zamer = "Pronájem kontejneru" },
                                label = { Text("Pronájem") }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("NOVÁ", "V ŘEŠENÍ", "REALIZOVÁNO", "ZRUŠENO").forEach { st ->
                                FilterChip(
                                    selected = formatAgendaStatusLabel(orderStatus) == st,
                                    onClick = { orderStatus = st },
                                    label = { Text(st, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }

                    if (isNabidka) {
                        OutlinedTextField(
                            value = platnost,
                            onValueChange = { platnost = it },
                            label = { Text("Platnost") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isZakazka && cisloNabidkyVazba.isNotBlank()) {
                        Text(
                            text = "Propojeno s CN Nabídkou: $cisloNabidkyVazba",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF3949AB),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isAdresar) {
                        OutlinedTextField(
                            value = popis,
                            onValueChange = { popis = it },
                            label = {
                                Text(
                                    when {
                                        isObjednavka -> "Zpráva"
                                        isNabidka -> "Předmět"
                                        else -> "Lokalita"
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isZakazka) {
                        OutlinedTextField(
                            value = prepravaInfoChoice,
                            onValueChange = { prepravaInfoChoice = it },
                            label = { Text("Přeprava", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("Trasa, termín, SPZ, řidič...") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isNabidka || isZakazka) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Položky (${pickedItems.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0C4A6E),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { showContainerSelectorInCreate = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "+ Kontejner",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Button(
                                            onClick = { showCatalogSelectorInCreate = !showCatalogSelectorInCreate },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                if (showCatalogSelectorInCreate) "Skrýt" else "+ Položka",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (showContainerSelectorInCreate) {
                                    AlertDialog(
                                        onDismissRequest = { showContainerSelectorInCreate = false },
                                        title = { Text("Kontejner", fontWeight = FontWeight.Bold) },
                                        text = {
                                            LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(allContainers) { c ->
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth().clickable {
                                                            val jc = if (c.price > 0) c.price.toDouble() else 3500.0
                                                            val newItem = NabidkaItemRecord(
                                                                id = java.util.UUID.randomUUID().toString(),
                                                                kod = "KONT",
                                                                text = "Kontejner ${c.designation} (${c.type})",
                                                                mnozstvi = 1.0,
                                                                mj = "ks",
                                                                jCena = jc,
                                                                dphPct = 21.0,
                                                                castkaBez = jc,
                                                                dphVal = jc * 0.21,
                                                                stav = "Vybráno"
                                                            )
                                                            pickedItems = pickedItems + newItem
                                                            if (firma.isBlank()) {
                                                                firma = c.customerCompany.ifBlank { c.soldCustomerName }.ifBlank { c.reservedName }
                                                            }
                                                            showContainerSelectorInCreate = false
                                                        }
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text("${c.designation} • ${c.type}", fontWeight = FontWeight.Bold)
                                                            Text("${c.depo} • ${c.status} • #${c.systemNumber}", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showContainerSelectorInCreate = false }) { Text("Zavřít") }
                                        }
                                    )
                                }
                                if (pickedItems.isNotEmpty()) {
                                    pickedItems.forEachIndexed { pIdx, pItem ->
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF7DD3FC)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = pItem.text,
                                                        onValueChange = { newText ->
                                                            pickedItems = pickedItems.toMutableList().also { list ->
                                                                list[pIdx] = pItem.copy(text = newText)
                                                            }
                                                        },
                                                        label = { Text("Položka") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(onClick = { pickedItems = pickedItems.filterIndexed { idx, _ -> idx != pIdx } }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Odebrat", tint = Color.Red)
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedTextField(
                                                        value = if (pItem.mnozstvi % 1.0 == 0.0) pItem.mnozstvi.toInt().toString() else pItem.mnozstvi.toString(),
                                                        onValueChange = { v ->
                                                            val q = v.replace(",", ".").toDoubleOrNull() ?: 0.0
                                                            val base = q * pItem.jCena * (1.0 - pItem.sleva / 100.0)
                                                            val dphV = if (pItem.pdp) 0.0 else base * (pItem.dphPct / 100.0)
                                                            pickedItems = pickedItems.toMutableList().also { list ->
                                                                list[pIdx] = pItem.copy(mnozstvi = q, castkaBez = base, dphVal = dphV)
                                                            }
                                                        },
                                                        label = { Text("Ks") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(0.7f)
                                                    )
                                                    OutlinedTextField(
                                                        value = if (pItem.jCena % 1.0 == 0.0) pItem.jCena.toLong().toString() else pItem.jCena.toString(),
                                                        onValueChange = { v ->
                                                            val p = v.replace(",", ".").toDoubleOrNull() ?: 0.0
                                                            val base = pItem.mnozstvi * p * (1.0 - pItem.sleva / 100.0)
                                                            val dphV = if (pItem.pdp) 0.0 else base * (pItem.dphPct / 100.0)
                                                            pickedItems = pickedItems.toMutableList().also { list ->
                                                                list[pIdx] = pItem.copy(jCena = p, castkaBez = base, dphVal = dphV)
                                                            }
                                                        },
                                                        label = { Text("Cena") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1.3f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    val sumSDph = pickedItems.sumOf { it.castkaBez + it.dphVal }
                                    Text(
                                        text = "Celkem vybrané položky s DPH: ${sumSDph.toLong()} Kč",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F3D5E),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val q = mnozstvi.toDoubleOrNull() ?: 1.0
                                        val p = jCena.toDoubleOrNull() ?: 0.0
                                        val dph = dphPct.toDoubleOrNull() ?: 21.0
                                        val base = q * p
                                        val dphV = base * (dph / 100.0)
                                        pickedItems = pickedItems + NabidkaItemRecord(
                                            id = "ITEM-" + System.currentTimeMillis(),
                                            kod = kodPolozky.ifBlank { "POL" },
                                            text = popis.ifBlank { "Položka dokladu" },
                                            mnozstvi = q,
                                            jCena = p,
                                            sleva = 0.0,
                                            dphPct = dph,
                                            pdp = pdp,
                                            castkaBez = base,
                                            dphVal = dphV
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("+ Přidat vlastní položku s cenou", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                                if (showCatalogSelectorInCreate) {
                                    catalogItems.groupBy { it.kategorie }.forEach { (catName, list) ->
                                        Text(
                                            text = when (catName) {
                                                "Přeprava" -> "SEKCE: PŘEPRAVA"
                                                "Vybavení" -> "SEKCE: VYBAVENÍ"
                                                else -> catName.uppercase()
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when (catName) {
                                                "Přeprava" -> Color(0xFFE65100)
                                                "Vybavení" -> Color(0xFF0284C7)
                                                else -> Color(0xFF0D47A1)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        list.sortedWith(compareBy<CatalogItemRecord> { it.poradi }.thenBy { it.oznaceni }).forEach { catItem ->
                                            OutlinedButton(
                                                onClick = {
                                                    pickedItems = pickedItems + viewModel.catalogItemToNabidkaItem(catItem)
                                                    if (popis.isBlank()) {
                                                        popis = "${catItem.oznaceni} - ${catItem.nazev}"
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "+ ${catItem.oznaceni} ${catItem.nazev} (${catItem.pocet.toInt()} ks, DPH ${catItem.dphPct.toInt()}%, ${catItem.celkemSDph.toLong()} Kč)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isNabidka && pickedItems.isEmpty() && editingNabidka == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = mnozstvi,
                                onValueChange = { mnozstvi = it },
                                label = { Text("Množství") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = jCena,
                                onValueChange = { jCena = it },
                                label = { Text("Cena") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = dphPct == "21",
                                onClick = { dphPct = "21" },
                                label = { Text("DPH 21 %") }
                            )
                            FilterChip(
                                selected = dphPct == "5",
                                onClick = { dphPct = "5" },
                                label = { Text("DPH 5 %") }
                            )
                            OutlinedTextField(
                                value = sleva,
                                onValueChange = { sleva = it },
                                label = { Text("Sleva") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pdp, onCheckedChange = { pdp = it })
                            Text("Přenesená daňová povinnost (PDP)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (isAdresar || isObjednavka) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = Color(0xFF1B5E8B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Fotografie",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F3D5E)
                                        )
                                    }
                                    Text(
                                        text = "${customerPhotos.filter { it.isNotBlank() }.size} / 3",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (slotIdx in 0..2) {
                                        val slotPhoto = customerPhotos.getOrNull(slotIdx).orEmpty()
                                        val slotModifier = Modifier
                                            .weight(1f)
                                            .height(105.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (slotPhoto.isNotBlank()) Color(0xFF0F172A) else Color.White)
                                            .border(1.dp, if (slotPhoto.isNotBlank()) Color(0xFF0284C7) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))

                                        Box(
                                            modifier = if (slotPhoto.isBlank()) {
                                                slotModifier.clickable {
                                                    onPhotoPickedCallback = { pickedPath ->
                                                        val next = customerPhotos.toMutableList()
                                                        while (next.size <= slotIdx) next.add("")
                                                        next[slotIdx] = pickedPath
                                                        customerPhotos = next

                                                        scope.launch {
                                                            Toast.makeText(context, "Analyzuji fotografii pro vytěžení údajů...", Toast.LENGTH_SHORT).show()
                                                            val extracted = com.example.data.PhotoDataExtractor.extractCustomerData(context, pickedPath)
                                                            val filled = mutableListOf<String>()
                                                            val parsedFirma = extracted["firma"].orEmpty().ifBlank { extracted["company"].orEmpty() }
                                                            val parsedJmeno = extracted["jmeno"].orEmpty().ifBlank { extracted["name"].orEmpty() }
                                                            val parsedTelefon = extracted["telefon"].orEmpty().ifBlank { extracted["phone"].orEmpty() }
                                                            val parsedEmail = extracted["email"].orEmpty().ifBlank { extracted["mail"].orEmpty() }
                                                            val parsedIc = extracted["ic"].orEmpty().ifBlank { extracted["ico"].orEmpty() }
                                                            val parsedDic = extracted["dic"].orEmpty()
                                                            val parsedUlice = extracted["ulice"].orEmpty().ifBlank { extracted["street"].orEmpty() }
                                                            val parsedPoznamka = extracted["poznamka"].orEmpty().ifBlank { extracted["note"].orEmpty() }

                                                            if (firma.isBlank() && parsedFirma.isNotBlank()) {
                                                                firma = parsedFirma
                                                                filled.add("Firma")
                                                            }
                                                            if (jmeno.isBlank() && parsedJmeno.isNotBlank()) {
                                                                jmeno = parsedJmeno
                                                                filled.add("Jméno")
                                                            }
                                                            if (telefon.isBlank() && parsedTelefon.isNotBlank()) {
                                                                telefon = parsedTelefon
                                                                filled.add("Telefon")
                                                            }
                                                            if (email.isBlank() && parsedEmail.isNotBlank()) {
                                                                email = parsedEmail
                                                                filled.add("Email")
                                                            }
                                                            if (ic.isBlank() && parsedIc.isNotBlank()) {
                                                                ic = parsedIc
                                                                filled.add("IČO")
                                                            }
                                                            if (dic.isBlank() && parsedDic.isNotBlank()) {
                                                                dic = parsedDic
                                                                filled.add("DIČ")
                                                            }
                                                            if (ulice.isBlank() && parsedUlice.isNotBlank()) {
                                                                ulice = parsedUlice
                                                                filled.add("Adresa")
                                                            }
                                                            val contExtracted = com.example.data.PhotoDataExtractor.extractContainerData(context, pickedPath)
                                                            val contDesig = contExtracted["designation"].orEmpty()
                                                            val contType = contExtracted["type"].orEmpty()
                                                            val contColor = contExtracted["color"].orEmpty()
                                                            val contDesc = com.example.api.formatContainerShortDescription(contColor, contType, contExtracted["description"])
                                                            val contSummary = listOf(contDesig, contDesc).filter { it.isNotBlank() }.joinToString(" - ")
                                                            val combinedNote = listOf(parsedPoznamka, contSummary).filter { it.isNotBlank() }.joinToString(" | ")
                                                            if (combinedNote.isNotBlank()) {
                                                                poznamkaZObjednavky = if (poznamkaZObjednavky.isBlank()) combinedNote else "$poznamkaZObjednavky\n$combinedNote"
                                                                filled.add("Kontejner/Popis")
                                                            }
                                                            if (filled.isNotEmpty()) {
                                                                Toast.makeText(context, "✅ Z fotografie byla načtena data do polí: ${filled.joinToString(", ")}", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "Fotografie uložena", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            } else {
                                                slotModifier
                                            },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (slotPhoto.isNotBlank()) {
                                                AsyncImage(
                                                    model = resolveCoilImageModel(slotPhoto),
                                                    contentDescription = "Foto ${slotIdx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable {
                                                            onImageSavedCallback = { updatedDataUri ->
                                                                val next = customerPhotos.toMutableList()
                                                                while (next.size <= slotIdx) next.add("")
                                                                next[slotIdx] = updatedDataUri
                                                                customerPhotos = next
                                                            }
                                                            showImageZoom = slotPhoto
                                                        }
                                                )
                                                Surface(
                                                    color = Color(0xFFDC2626),
                                                    shape = RoundedCornerShape(bottomStart = 6.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .clickable {
                                                            val next = customerPhotos.toMutableList()
                                                            while (next.size <= slotIdx) next.add("")
                                                            next[slotIdx] = ""
                                                            customerPhotos = next
                                                        }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Smazat",
                                                        tint = Color.White,
                                                        modifier = Modifier
                                                            .padding(3.dp)
                                                            .size(13.dp)
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AddPhotoAlternate,
                                                        contentDescription = null,
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Foto ${slotIdx + 1}",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF334155)
                                                    )
                                                    Text(
                                                        text = "+ Vložit",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF1B5E8B)
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (firma.isNotBlank() || jmeno.isNotBlank()) {
                            when {
                                isAdresar -> {
                                    val validPhotos = customerPhotos.filter { it.isNotBlank() }
                                    val saved = viewModel.saveAdresarContact(
                                        id = editingContact?.id ?: "",
                                        cisloZakaznika = cisloDokladu,
                                        company = firma,
                                        name = jmeno,
                                        phone = telefon,
                                        email = email,
                                        ic = ic,
                                        dic = dic,
                                        ulice = ulice,
                                        depo = depoChoice,
                                        bonita = bonitaChoice,
                                        bonitaDetail = bonitaDetailChoice,
                                        photos = validPhotos
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Kontakt ${saved.company.ifBlank { saved.name }} uložen (${validPhotos.size} foto)")
                                    }
                                }
                                isObjednavka -> {
                                    val validPhotos = customerPhotos.filter { it.isNotBlank() }
                                    viewModel.saveOrderRecord(
                                        id = editingOrder?.id ?: "",
                                        name = jmeno.ifBlank { firma },
                                        company = firma,
                                        phone = telefon,
                                        email = email,
                                        ic = ic,
                                        dic = dic,
                                        ulice = ulice,
                                        inquiryType = zamer,
                                        message = popis,
                                        status = orderStatus,
                                        depo = depoChoice,
                                        photos = validPhotos
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Objednávka uložena a synchronizována (${validPhotos.size} foto)")
                                    }
                                }
                                isNabidka -> {
                                    if (editingNabidka != null) {
                                        val saved = viewModel.saveFullNabidka(
                                            id = editingNabidka!!.id,
                                            cislo = cisloDokladu,
                                            datum = editingNabidka!!.datum,
                                            platnost = platnost,
                                            firma = firma,
                                            jmeno = jmeno,
                                            telefon = telefon,
                                            email = email,
                                            text = popis,
                                            depo = depoChoice,
                                            ic = ic,
                                            dic = dic,
                                            ulice = ulice,
                                            adresarId = adresarIdVazba,
                                            items = pickedItems
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar("CN Nabídka ${saved.cislo} upravena a synchronizována")
                                        }
                                    } else {
                                        val created = viewModel.createCustomNabidka(
                                            firma = firma,
                                            jmeno = jmeno,
                                            telefon = telefon,
                                            email = email,
                                            text = popis,
                                            depo = depoChoice,
                                            ic = ic,
                                            dic = dic,
                                            ulice = ulice,
                                            platnost = platnost,
                                            kodPolozky = kodPolozky,
                                            mnozstvi = mnozstvi.toDoubleOrNull() ?: 1.0,
                                            jCena = jCena.toDoubleOrNull() ?: 0.0,
                                            dphPct = dphPct.toDoubleOrNull() ?: 21.0,
                                            sleva = sleva.toDoubleOrNull() ?: 0.0,
                                            pdp = pdp,
                                            selectedCatalogItems = pickedItems
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Uložena CN Nabídka ${created.cislo}")
                                        }
                                    }
                                }
                                else -> {
                                    val saved = viewModel.saveFullZakazka(
                                        id = editingZakazka?.id ?: "",
                                        cisloZakazky = cisloDokladu,
                                        cisloNabidky = cisloNabidkyVazba,
                                        stredisko = strediskoChoice,
                                        prepravaInfo = prepravaInfoChoice,
                                        firma = firma,
                                        vyrizuje = jmeno,
                                        telefon = telefon,
                                        email = email,
                                        ic = ic,
                                        dic = dic,
                                        ulice = ulice,
                                        lokalita = popis,
                                        depo = depoChoice,
                                        adresarId = adresarIdVazba,
                                        zdrojNabidkaId = zdrojNabidkaId,
                                        items = pickedItems
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Zakázka ${saved.cisloZakazky} (${saved.stredisko}) uložena a synchronizována")
                                    }
                                }
                            }
                            closeDialog()
                        }
                    }
                ) {
                    Text(
                        "Uložit",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = closeDialog) {
                    Text("Zrušit")
                }
            }
        )
    }

    // ==================== DIALOG SPRÁVY STŘEDISEK ZAKÁZEK (Pronájem | Prodej | Ostatní | + další) ====================
    if (showManageStrediskaDialog) {
        var newStrediskoName by remember { mutableStateOf("") }
        var editingOldName by remember { mutableStateOf<String?>(null) }
        var editingNewName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManageStrediskaDialog = false },
            title = { Text("Střediska", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newStrediskoName,
                            onValueChange = { newStrediskoName = it },
                            label = { Text("Středisko") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newStrediskoName.isNotBlank()) {
                                    viewModel.addZakazkaStredisko(newStrediskoName.trim())
                                    newStrediskoName = ""
                                }
                            }
                        ) {
                            Text("+ Přidat", fontWeight = FontWeight.Bold)
                        }
                    }
                    zakazkaStrediska.forEach { stName ->
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editingOldName == stName) {
                                    OutlinedTextField(
                                        value = editingNewName,
                                        onValueChange = { editingNewName = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            viewModel.renameZakazkaStredisko(stName, editingNewName)
                                            editingOldName = null
                                        }
                                    ) {
                                        Text("Uložit", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(stName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingOldName = stName
                                                editingNewName = stName
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Přejmenovat", tint = Color(0xFF0284C7))
                                        }
                                        IconButton(onClick = { viewModel.removeZakazkaStredisko(stName) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFD32F2F))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageStrediskaDialog = false }) {
                    Text("Hotovo", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ==================== DIALOG | PŘEPRAVA | U ZAKÁZKY ====================
    if (prepravaModalZakazka != null) {
        val zak = prepravaModalZakazka!!
        var spz by remember(zak) { mutableStateOf("") }
        var driver by remember(zak) { mutableStateOf("") }
        var dateText by remember(zak) {
            mutableStateOf(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()))
        }
        var note by remember(zak) { mutableStateOf(zak.prepravaInfo.ifBlank { "Přeprava pro zakázku ${zak.cisloZakazky} – ${zak.lokalita}" }) }
        val transportCatalog = remember(catalogItems) {
            catalogItems.filter { it.kategorie.equals("Přeprava", ignoreCase = true) || it.sekce.equals("PŘEPRAVA", ignoreCase = true) }
        }
        var selectedTransportItem by remember { mutableStateOf<CatalogItemRecord?>(null) }

        AlertDialog(
            onDismissRequest = { prepravaModalZakazka = null },
            title = { Text("Přeprava", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Firma: ${zak.firma} • Středisko: ${zak.stredisko}", fontWeight = FontWeight.Bold, color = Color(0xFF0C4A6E), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Datum", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = spz,
                            onValueChange = { spz = it },
                            label = { Text("SPZ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = driver,
                            onValueChange = { driver = it },
                            label = { Text("Řidič", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Trasa", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (transportCatalog.isNotEmpty()) {
                        Text("Položka přepravy:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        transportCatalog.forEach { trItem ->
                            FilterChip(
                                selected = selectedTransportItem?.id == trItem.id,
                                onClick = {
                                    selectedTransportItem = if (selectedTransportItem?.id == trItem.id) null else trItem
                                },
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("${trItem.oznaceni} ${trItem.nazev} (${trItem.celkemSDph.toLong()} Kč)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPrepravaFromZakazka(
                            zakazka = zak,
                            spz = spz,
                            driver = driver,
                            dateText = dateText,
                            note = note,
                            transportCatalogItem = selectedTransportItem
                        )
                        prepravaModalZakazka = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Přeprava uložena k zakázce ${zak.cisloZakazky}")
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Text("Uložit", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { prepravaModalZakazka = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // ==================== DIALOG UDÁLOSTÍ A HISTORIE KOMUNIKACE S FIRMOU V ADRESÁŘI ====================
    if (udalostiModalCustomer != null) {
        val currentCust = allCustomers.find { it.id == udalostiModalCustomer!!.id } ?: udalostiModalCustomer!!
        val allEvents = remember(currentCust, allNabidky, allZakazky, allCustomers) {
            viewModel.getCustomerFullEngagementEvents(currentCust)
        }
        var evTyp by remember { mutableStateOf("FAKTURA") }
        var evDoklad by remember { mutableStateOf("") }
        var evPopis by remember { mutableStateOf("") }
        var evCastka by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { udalostiModalCustomer = null },
            title = {
                Text(
                    "Události",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Událost", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("FAKTURA", "CN", "ZAKÁZKA", "KOMUNIKACE").forEach { t ->
                                    FilterChip(
                                        selected = evTyp == t,
                                        onClick = { evTyp = t },
                                        label = { Text(t, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = evDoklad,
                                    onValueChange = { evDoklad = it },
                                    label = { Text("Doklad") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = evCastka,
                                    onValueChange = { evCastka = it },
                                    label = { Text("Částka") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                            OutlinedTextField(
                                value = evPopis,
                                onValueChange = { evPopis = it },
                                label = { Text("Popis") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (evDoklad.isNotBlank() || evPopis.isNotBlank()) {
                                        viewModel.addManualCustomerEvent(
                                            customerId = currentCust.id,
                                            typ = evTyp,
                                            cisloDokladu = evDoklad,
                                            popis = evPopis,
                                            castka = evCastka.replace(",", ".").toDoubleOrNull() ?: 0.0
                                        )
                                        evDoklad = ""
                                        evPopis = ""
                                        evCastka = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Přidat do historie firmy", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (allEvents.isEmpty()) {
                        Text("Žádné záznamy v historii této firmy.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        allEvents.forEach { ev ->
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "[${ev.typ}] ${ev.cisloDokladu}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F3D5E),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = ev.datum,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(ev.popis, style = MaterialTheme.typography.bodySmall)
                                    if (ev.castka > 0.0) {
                                        Text(
                                            text = "Částka: ${ev.castka.toLong()} Kč",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { udalostiModalCustomer = null }) {
                    Text("Zavřít", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPohodaMServerDialog) {
        PohodaMServerDialog(
            viewModel = viewModel,
            onDismiss = { showPohodaMServerDialog = false }
        )
    }
}
