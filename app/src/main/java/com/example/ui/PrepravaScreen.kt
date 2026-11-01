package com.example.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.api.analyzePreprava
import com.example.data.ContainerCard
import com.example.data.CustomerRecord
import com.example.data.TransportContainerItem
import com.example.data.TransportRecord
import com.example.data.ZakazkaRecord
import com.example.data.computeCustomerInitials
import com.example.data.parseTransportDateToEpoch
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepravaScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val transports by viewModel.transports.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val zakazky by viewModel.zakazky.collectAsState()
    val containers by viewModel.containers.collectAsState()
    val prepravci by viewModel.prepravci.collectAsState()
    val equipmentList by viewModel.equipmentList.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val drivers by viewModel.drivers.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()

    var isAnalyzing by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var isCreatingVraceni by remember { mutableStateOf(false) }
    var transportStatusSubTab by remember { mutableStateOf("NOVÉ") }
    var creatingOrEditingTransport by remember { mutableStateOf<TransportRecord?>(null) }
    var completingTransport by remember { mutableStateOf<TransportRecord?>(null) }
    var showPickOrderForNew by remember { mutableStateOf(false) }
    var showPickZakazkaForNew by remember { mutableStateOf(false) }
    var deleteConfirmTransport by remember { mutableStateOf<TransportRecord?>(null) }

    val tempPhotoFile = remember { File(context.filesDir, "temp_preprava.jpg") }
    val todayStr = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()) }

    fun processPhoto(file: File) {
        coroutineScope.launch {
            isAnalyzing = true
            try {
                val options = BitmapFactory.Options()
                options.inSampleSize = 2
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                val resultJson = if (bitmap != null) {
                    try {
                        analyzePreprava(bitmap)
                    } catch (_: Exception) {
                        "{}"
                    }
                } else "{}"

                val cleanJson = if (resultJson.startsWith("Chybí") || resultJson.startsWith("Chyba")) "{}" else resultJson.trim().removePrefix("```json").removeSuffix("```").trim()
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(PrepravaAnalysisResult::class.java)
                val res = try {
                    adapter.fromJson(cleanJson) ?: PrepravaAnalysisResult()
                } catch (_: Exception) {
                    PrepravaAnalysisResult()
                }

                val destFile = File(context.filesDir, "transport_${System.currentTimeMillis()}.jpg")
                file.copyTo(destFile, overwrite = true)

                val autoInitials = computeCustomerInitials("", res.company)
                val shortNote = res.note.ifBlank {
                    if (res.description.isNotBlank() || res.color.isNotBlank()) {
                        com.example.api.formatContainerShortDescription(res.color, res.containerType, res.description)
                    } else ""
                }
                val record = TransportRecord(
                    designation = res.designation,
                    dateText = res.date.ifBlank { todayStr },
                    company = res.company,
                    customerCompany = res.company,
                    customerInitials = autoInitials,
                    spz = res.spz,
                    driver = res.driver,
                    containerType = res.containerType,
                    note = shortNote,
                    rawExtractedText = res.rawExtractedText,
                    status = "NOVÁ",
                    imageUri = destFile.absolutePath,
                    containerImageUri = destFile.absolutePath,
                    depo = currentDepo ?: "Depo 1",
                    customerSource = if (isCreatingVraceni) "VRÁCENÍ" else "",
                    isVraceni = isCreatingVraceni
                )
                viewModel.saveTransport(record)
                Toast.makeText(context, "Karta přepravy vytvořena z fotografie", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Chyba při zpracování fotky: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isAnalyzing = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            processPhoto(tempPhotoFile)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val tempFile = File(context.cacheDir, "gallery_temp.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    processPhoto(tempFile)
                }
            }
        }
    }

    if (deleteConfirmTransport != null) {
        val target = deleteConfirmTransport!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Karta přepravy: ${target.designation.ifBlank { target.spz.ifBlank { target.id } }}",
            itemDescription = "Zákazník: ${target.customerCompany.ifBlank { target.company }.ifBlank { "-" }} • SPZ: ${target.spz.ifBlank { "-" }}",
            onDismiss = { deleteConfirmTransport = null },
            onConfirmedDelete = {
                viewModel.deleteTransport(target.id)
                deleteConfirmTransport = null
            }
        )
    }

    if (showPickOrderForNew) {
        TransportOrderPickerDialog(
            customers = customers,
            isVraceni = isCreatingVraceni,
            onDismiss = { showPickOrderForNew = false },
            onSelectOrder = { order ->
                showPickOrderForNew = false
                val initials = computeCustomerInitials(order.name, order.company)
                creatingOrEditingTransport = TransportRecord(
                    dateText = todayStr,
                    company = order.company.ifBlank { order.name },
                    customerName = order.name,
                    customerCompany = order.company,
                    customerIc = order.ic,
                    customerDic = order.dic,
                    customerAddress = order.ulice,
                    customerPhone = order.phone,
                    customerEmail = order.email,
                    customerInitials = initials,
                    customerSource = if (isCreatingVraceni) "VRÁCENÍ (OBJ)" else "OBJEDNÁVKA",
                    sourceOrderId = order.id,
                    cisloDokladuZdroj = order.cisloZakaznika.ifBlank { "OBJ" },
                    containerType = order.inquiryType,
                    note = order.message,
                    status = "NOVÁ",
                    depo = order.depo.ifBlank { currentDepo ?: "Depo 1" },
                    isVraceni = isCreatingVraceni
                )
            }
        )
    }

    if (showPickZakazkaForNew) {
        TransportZakazkaPickerDialog(
            zakazky = zakazky,
            isVraceni = isCreatingVraceni,
            onDismiss = { showPickZakazkaForNew = false },
            onSelectZakazka = { zak ->
                showPickZakazkaForNew = false
                val initials = computeCustomerInitials(zak.vyrizuje, zak.firma)
                creatingOrEditingTransport = TransportRecord(
                    designation = zak.cisloZakazky,
                    dateText = todayStr,
                    company = zak.firma.ifBlank { zak.vyrizuje },
                    customerName = zak.vyrizuje,
                    customerCompany = zak.firma,
                    customerIc = zak.ic,
                    customerDic = zak.dic,
                    customerAddress = zak.lokalita.ifBlank { zak.ulice },
                    customerPhone = zak.telefon,
                    customerEmail = zak.email,
                    customerInitials = initials,
                    customerSource = if (isCreatingVraceni) "VRÁCENÍ (ZAK)" else "ZAKÁZKA",
                    sourceZakazkaId = zak.id,
                    cisloDokladuZdroj = zak.cisloZakazky,
                    containerType = zak.stredisko,
                    note = zak.prepravaInfo.ifBlank { "Zakázka ${zak.cisloZakazky} (${zak.lokalita})" },
                    internalNote = zak.interniPoznamka,
                    transportDate = todayStr,
                    status = "NOVÁ",
                    depo = zak.depo.ifBlank { currentDepo ?: "Depo 1" },
                    isVraceni = isCreatingVraceni
                )
            }
        )
    }

    if (completingTransport != null) {
        TransportCompleteScreenDialog(
            transport = completingTransport!!,
            prepravci = prepravci,
            vehicles = vehicles,
            drivers = drivers,
            onDismiss = { completingTransport = null },
            onAddPrepravce = { name -> viewModel.addPrepravce(name) },
            onAddVehicle = { veh -> viewModel.addVehicle(veh) },
            onAddDriver = { drv -> viewModel.addDriver(drv) },
            onComplete = { updated ->
                viewModel.saveTransport(updated)
                completingTransport = null
                Toast.makeText(context, "Přeprava uložena a přesunuta do HOTOVO", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (creatingOrEditingTransport != null) {
        TransportEditDialog(
            transport = creatingOrEditingTransport!!,
            allTransports = transports,
            customers = customers,
            zakazky = zakazky,
            containers = containers,
            prepravci = prepravci,
            equipmentList = equipmentList,
            vehicles = vehicles,
            drivers = drivers,
            onDismiss = { creatingOrEditingTransport = null },
            onAutoSave = { updated ->
                viewModel.saveTransport(updated)
            },
            onSave = { updated ->
                viewModel.saveTransport(updated)
                creatingOrEditingTransport = null
                Toast.makeText(context, "Karta přepravy uložena", Toast.LENGTH_SHORT).show()
            },
            onDelete = { toDelete ->
                creatingOrEditingTransport = null
                if (toDelete.id.isNotBlank()) {
                    deleteConfirmTransport = toDelete
                }
            },
            onAddPrepravce = { name -> viewModel.addPrepravce(name) },
            onAddVehicle = { veh -> viewModel.addVehicle(veh) },
            onAddDriver = { drv -> viewModel.addDriver(drv) }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vlevo: Tlačítko EXPEDICE (3D plastické s výrazným stínem)
                Button(
                    onClick = {
                        isCreatingVraceni = false
                        showSourceDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 4.dp, hoveredElevation = 14.dp),
                    border = BorderStroke(2.dp, if (currentDepo == "Depo 2") Color(0xFFFBBF24) else Color(0xFF60A5FA)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentDepo == "Depo 2") Color(0xFFB45309) else Color(0xFF0D47A1),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .shadow(10.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x66000000))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Expedice", tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EXPEDICE", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }

                // Vpravo: Vínově červené tlačítko VRÁCENÍ (3D plastické s výrazným stínem)
                Button(
                    onClick = {
                        isCreatingVraceni = true
                        showSourceDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 4.dp, hoveredElevation = 14.dp),
                    border = BorderStroke(2.dp, Color(0xFFFDA4AF)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF881337), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .shadow(10.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x66000000))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Vrácení", tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VRÁCENÍ", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val depoTransports = remember(transports, currentDepo) {
                val filteredByDepo = when (currentDepo) {
                    "Depo 1" -> transports.filter { it.depo == "Depo 1" || it.depo.isBlank() }
                    "Depo 2" -> transports.filter { it.depo == "Depo 2" }
                    else -> transports
                }
                filteredByDepo.sortedWith(
                    compareBy<TransportRecord> {
                        val m = parseTransportDateToEpoch(it.dateText, 0L)
                        if (m > 0L) m else Long.MAX_VALUE
                    }.thenBy { it.timestamp }
                )
            }

            AgendaStatusSubMenuBar(
                selectedSubTab = transportStatusSubTab,
                onSubTabSelected = { transportStatusSubTab = it },
                noveCount = depoTransports.count { matchesAgendaStatusTab(it.status, "NOVÉ") },
                realizovaneCount = depoTransports.count { matchesAgendaStatusTab(it.status, "REALIZOVANÉ") },
                zruseneCount = depoTransports.count { matchesAgendaStatusTab(it.status, "ZRUŠENÉ") },
                isDepo2 = currentDepo == "Depo 2",
                noveLabel = "NOVÁ",
                realizovaneLabel = "HOTOVO",
                zruseneLabel = "ZRUŠENO"
            )

            Box(modifier = Modifier.fillMaxSize()) {
                val filteredTransports = remember(depoTransports, transportStatusSubTab) {
                    val base = depoTransports.filter { matchesAgendaStatusTab(it.status, transportStatusSubTab) }
                    if (transportStatusSubTab == "REALIZOVANÉ") {
                        base.sortedWith(
                            compareByDescending<TransportRecord> {
                                parseTransportDateToEpoch(it.dateText, it.timestamp)
                            }.thenByDescending { it.timestamp }
                        )
                    } else {
                        base.sortedWith(
                            compareBy<TransportRecord> {
                                val m = parseTransportDateToEpoch(it.dateText, 0L)
                                if (m > 0L) m else Long.MAX_VALUE
                            }.thenBy { it.timestamp }
                        )
                    }
                }

                if (isAnalyzing) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Analyzuji fotografii...", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                } else if (filteredTransports.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Zatím žádné přepravy v této záložce.",
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    isCreatingVraceni = false
                                    showSourceDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 4.dp, hoveredElevation = 14.dp),
                                border = BorderStroke(2.dp, if (currentDepo == "Depo 2") Color(0xFFFBBF24) else Color(0xFF60A5FA)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentDepo == "Depo 2") Color(0xFFB45309) else Color(0xFF0D47A1),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .shadow(10.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x66000000))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EXPEDICE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                            }
                            Button(
                                onClick = {
                                    isCreatingVraceni = true
                                    showSourceDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 4.dp, hoveredElevation = 14.dp),
                                border = BorderStroke(2.dp, Color(0xFFFDA4AF)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF881337), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .shadow(10.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x66000000))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("VRÁCENÍ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTransports, key = { it.id.ifBlank { it.timestamp.toString() } }) { transport ->
                            TransportCardItem(
                                transport = transport,
                                containers = containers,
                                onUpdate = { updated -> viewModel.saveTransport(updated) },
                                onOpenEdit = { creatingOrEditingTransport = transport },
                                onOpenComplete = { completingTransport = transport },
                                onDelete = { deleteConfirmTransport = transport }
                            )
                        }
                    }
                }
            }
        }

        if (showSourceDialog) {
            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                titleContentColor = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF0F172A),
                textContentColor = Color(0xFF1E293B),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isCreatingVraceni) {
                            Surface(
                                color = Color(0xFF881337),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("VRÁCENÍ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(if (isCreatingVraceni) "Vrácení" else "Expedice", fontWeight = FontWeight.Bold, color = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF0F172A), maxLines = 1, softWrap = false)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                showSourceDialog = false
                                showPickOrderForNew = true
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, if (isCreatingVraceni) Color(0xFF4C0519) else Color(0xFF0A3780)),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF0D47A1), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Objednávky", fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = {
                                showSourceDialog = false
                                showPickZakazkaForNew = true
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, if (isCreatingVraceni) Color(0xFF4C0519) else Color(0xFF065F46)),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF047857), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Zakázky", fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                showSourceDialog = false
                                creatingOrEditingTransport = TransportRecord(
                                    dateText = todayStr,
                                    status = "NOVÁ",
                                    customerSource = if (isCreatingVraceni) "VRÁCENÍ" else "RUČNĚ",
                                    depo = currentDepo ?: "Depo 1",
                                    isVraceni = isCreatingVraceni
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF1B5E8B)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isCreatingVraceni) Color(0xFFFFF1F2) else Color(0xFFF0F9FF), contentColor = Color(0xFF0F172A))
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF1B5E8B))
                            Spacer(Modifier.width(8.dp))
                            Text("Ručně", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 1, softWrap = false)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showSourceDialog = false
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempPhotoFile)
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.5.dp, if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF1B5E8B)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isCreatingVraceni) Color(0xFFFFF1F2) else Color.Transparent,
                                    contentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Text("Vyfotit", fontWeight = FontWeight.Bold, color = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF0F172A), maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    showSourceDialog = false
                                    galleryLauncher.launch("image/*")
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.5.dp, if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF1B5E8B)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isCreatingVraceni) Color(0xFFFFF1F2) else Color.Transparent,
                                    contentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Text("Galerie", fontWeight = FontWeight.Bold, color = if (isCreatingVraceni) Color(0xFF881337) else Color(0xFF0F172A), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSourceDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF0D47A1))
                    ) {
                        Text("Zavřít", color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            )
        }
    }
}

@Composable
private fun transportTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF0F172A),
    unfocusedTextColor = Color(0xFF0F172A),
    disabledTextColor = Color(0xFF334155),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color(0xFFF8FAFC),
    focusedBorderColor = Color(0xFF0284C7),
    unfocusedBorderColor = Color(0xFF94A3B8),
    focusedLabelColor = Color(0xFF0C4A6E),
    unfocusedLabelColor = Color(0xFF334155),
    focusedPlaceholderColor = Color(0xFF64748B),
    unfocusedPlaceholderColor = Color(0xFF64748B),
    cursorColor = Color(0xFF0284C7)
)

@Composable
private fun CompactSection2InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    placeholderColor: Color = Color(0xFF0C4A6E),
    borderColor: Color = Color(0xFF94A3B8),
    focusedBorderColor: Color = Color(0xFF0284C7),
    backgroundColor: Color = Color.White,
    trailingEndPadding: androidx.compose.ui.unit.Dp = 10.dp,
    trailingContent: (@Composable BoxScope.() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) focusedBorderColor else borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("\n", " ")) },
            singleLine = true,
            maxLines = 1,
            textStyle = TextStyle(
                textAlign = TextAlign.Start,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            ),
            cursorBrush = SolidColor(focusedBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = trailingEndPadding)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = placeholderColor,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

val PRESET_EQUIPMENT = listOf(
    "Přímotop", "Židle", "Stůl", "Skříňka", "Šatní", "Klimatizace"
)

fun parseEquipmentLine(raw: String): Pair<String, String> {
    val clean = raw.trim()
    if (clean.isBlank()) return Pair("", "0")
    val regex = Regex("""^(.*?)(?:[ \-–:(]+(\d+)\s*(?:ks|kus|kusů)?[)]?)?$""", RegexOption.IGNORE_CASE)
    val match = regex.find(clean)
    if (match != null) {
        val name = match.groupValues[1].trim()
        val count = match.groupValues[2].trim().ifBlank { "0" }
        return Pair(name, count)
    }
    return Pair(clean, "0")
}

fun formatEquipmentLine(name: String, count: String): String {
    if (name.isBlank()) return ""
    val c = count.trim().ifBlank { "0" }
    return "$name ($c ks)"
}

fun getCzechDayOfWeek(dateText: String): String {
    val clean = dateText.trim()
    if (clean.isBlank()) return ""
    val epoch = parseTransportDateToEpoch(clean, 0L)
    if (epoch <= 0L) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = epoch }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "pondělí"
        Calendar.TUESDAY -> "úterý"
        Calendar.WEDNESDAY -> "středa"
        Calendar.THURSDAY -> "čtvrtek"
        Calendar.FRIDAY -> "pátek"
        Calendar.SATURDAY -> "sobota"
        Calendar.SUNDAY -> "neděle"
        else -> ""
    }
}

fun parseTransportContainersJson(
    json: String,
    fallbackDesignation: String = "",
    fallbackType: String = "OB",
    fallbackPhotos: List<String> = emptyList()
): List<TransportContainerItem> {
    val normFallbackType = if (fallbackType.trim().uppercase().startsWith("SK")) "SK" else "OB"
    if (json.isNotBlank()) {
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, TransportContainerItem::class.java)
            val adapter = moshi.adapter<List<TransportContainerItem>>(type)
            val parsed = adapter.fromJson(json)
            if (!parsed.isNullOrEmpty()) {
                return parsed.map { item ->
                    val eq = if (item.equipment.size < 4) {
                        item.equipment + List(4 - item.equipment.size) { "" }
                    } else item.equipment
                    val normType = if (item.containerType.trim().uppercase().startsWith("SK")) "SK" else "OB"
                    item.copy(containerType = normType, equipment = eq)
                }
            }
        } catch (_: Exception) {}
    }
    return listOf(
        TransportContainerItem(
            designation = fallbackDesignation,
            containerType = normFallbackType,
            photos = fallbackPhotos.take(3),
            equipment = listOf("", "", "", "")
        )
    )
}

fun serializeTransportContainersJson(containers: List<TransportContainerItem>): String {
    return try {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, TransportContainerItem::class.java)
        val adapter = moshi.adapter<List<TransportContainerItem>>(type)
        adapter.toJson(containers)
    } catch (_: Exception) {
        ""
    }
}

fun formatContainerSummary(containers: List<TransportContainerItem>, fallbackType: String): String {
    if (containers.isEmpty()) {
        return if (fallbackType.trim().uppercase().startsWith("SK")) "SK" else "OB"
    }
    val counts = mutableMapOf<String, Int>()
    containers.forEach { c ->
        val raw = if (c.containerType.trim().uppercase().startsWith("SK")) "SK" else "OB"
        counts[raw] = (counts[raw] ?: 0) + 1
    }
    if (counts.size == 1) {
        val (type, count) = counts.entries.first()
        return if (count > 1) "$count× $type" else type
    }
    return counts.entries.joinToString(", ") { (type, count) ->
        if (count > 1) "$count× $type" else type
    }
}

private fun extractAllTransportPhotos(transport: TransportRecord): List<String> {
    val list = mutableListOf<String>()
    if (transport.containersJson.isNotBlank()) {
        val items = parseTransportContainersJson(transport.containersJson)
        items.forEach { item ->
            item.photos.forEach { p ->
                val tr = p.trim()
                if (tr.isNotEmpty() && !list.contains(tr)) list.add(tr)
            }
        }
    }
    if (transport.deliveryPhotos.isNotBlank()) {
        transport.deliveryPhotos.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !list.contains(it) }
            .forEach { list.add(it) }
    }
    transport.containerImageUri?.trim()?.takeIf { it.isNotEmpty() && !list.contains(it) }?.let { list.add(it) }
    if (transport.extraImages.isNotBlank()) {
        transport.extraImages.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !list.contains(it) }
            .forEach { list.add(it) }
    }
    transport.imageUri?.trim()?.takeIf { it.isNotEmpty() && !list.contains(it) }?.let { list.add(it) }
    return list
}

@Composable
fun TransportCardItem(
    transport: TransportRecord,
    containers: List<ContainerCard> = emptyList(),
    onUpdate: (TransportRecord) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenComplete: () -> Unit = {},
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showImageDialog by remember { mutableStateOf(false) }
    var activePhotoIndex by remember { mutableIntStateOf(0) }
    var editingImageUri by remember { mutableStateOf<String?>(null) }

    val custCompany = transport.customerCompany.ifBlank {
        if (transport.company.equals("Kon. Autodoprava", ignoreCase = true) ||
            transport.company.equals("Kontejnery Autodoprava", ignoreCase = true)
        ) "" else transport.company
    }
    val custName = transport.customerName
    val allPhotos = remember(transport.containerImageUri, transport.extraImages, transport.imageUri, transport.containersJson, transport.deliveryPhotos) {
        extractAllTransportPhotos(transport)
    }
    val parsedContainers = remember(transport.containersJson, transport.designation, transport.containerType) {
        parseTransportContainersJson(
            transport.containersJson,
            fallbackDesignation = transport.designation,
            fallbackType = transport.containerType
        )
    }
    val containerSummary = remember(parsedContainers, transport.containerType) {
        formatContainerSummary(parsedContainers, transport.containerType)
    }
    val containerDesignationText = remember(parsedContainers, transport.designation) {
        val desigs = parsedContainers.map { it.designation.trim() }.filter { it.isNotEmpty() }
        if (desigs.isNotEmpty()) desigs.joinToString(", ") else transport.designation.ifBlank { "KONTEJNER" }
    }

    val isVraceni = transport.isVraceni ||
        transport.customerSource.contains("VRÁCENÍ", ignoreCase = true) ||
        transport.note.contains("VRÁCENÍ", ignoreCase = true) ||
        transport.designation.contains("VRÁCENÍ", ignoreCase = true) ||
        transport.containerType.contains("VRÁCENÍ", ignoreCase = true)
    val isDepo2 = transport.depo == "Depo 2"
    val headerBg = if (isVraceni) Color(0xFF881337) else if (isDepo2) Color(0xFF6B532B) else Color(0xFF1B5E8B)
    val borderCol = if (isVraceni) Color(0xFF881337) else if (isDepo2) Color(0xFF8C6239) else Color(0xFF1B5E8B)
    val cardBg = if (isVraceni) Color(0xFFFFF1F2) else if (isDepo2) Color(0xFFFCF5E8) else Color(0xFFFFFFFF)

    val destinationLoc = remember(transport.destinationLocation, transport.customerAddress) {
        transport.destinationLocation.trim().ifBlank { transport.customerAddress.trim() }
    }

    // 3D vystouplý efekt karty přepravy (defaultElevation 6.dp) se zaoblenými rohy
    Card(
        onClick = onOpenEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(if (isVraceni) 2.dp else 1.5.dp, borderCol),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
            hoveredElevation = 8.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Horní pruh karty: ikona "P" (nebo "V" pro Vrácení) + datum přepravy a pod tím malinkým písmem den v týdnu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isVraceni) Icons.AutoMirrored.Filled.KeyboardReturn else Icons.Default.ArrowUpward,
                                contentDescription = if (isVraceni) "Vrácení" else "Expedice",
                                tint = if (isVraceni) Color(0xFF881337) else headerBg,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val effDate = transport.transportDate.ifBlank { transport.dateText }
                    val dayOfWeek = remember(effDate) { getCzechDayOfWeek(effDate) }
                    val displayDate = effDate.ifBlank { "Bez data" }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayDate,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (dayOfWeek.isNotBlank()) {
                            Text(
                                text = "($dayOfWeek)",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Status v pravém rohu nahoru o 20% vyšší (NOVÁ / HOTOVO / ZRUŠENO)
                StatusDropdownBadge(
                    currentStatus = transport.status.ifBlank { "NOVÁ" },
                    options = TRANSPORT_STATUS_OPTIONS,
                    customColor = if (isVraceni) Color(0xFF881337) else null,
                    onStatusSelected = { newStatus ->
                        if (newStatus.equals("REALIZOVÁNO", ignoreCase = true) || newStatus.equals("HOTOVO", ignoreCase = true)) {
                            onOpenComplete()
                        } else {
                            onUpdate(transport.copy(status = newStatus))
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(29.dp)
                )
            }

            // Druhý řádek zkrácené karty:
            // - Vlevo foto + počet fotek
            // - Druhý řádek: název společnosti, které se to veze
            // - Pod tím označení kontejneru a stručně typ
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val matchedContainerPhoto = remember(transport, containers, allPhotos) {
                    if (allPhotos.isNotEmpty()) {
                        allPhotos.first()
                    } else {
                        val parsed = parseTransportContainersJson(
                            transport.containersJson,
                            fallbackDesignation = transport.designation,
                            fallbackType = transport.containerType
                        )
                        val desigs = parsed.map { it.designation.trim() }.filter { it.isNotEmpty() }
                        val matched = containers.firstOrNull { c ->
                            desigs.any { d -> c.designation.equals(d, ignoreCase = true) || c.id.equals(d, ignoreCase = true) } ||
                            (transport.designation.isNotBlank() && (c.designation.equals(transport.designation, ignoreCase = true) || c.id.equals(transport.designation, ignoreCase = true)))
                        }
                        if (matched != null) primaryContainerPhoto(matched) else null
                    }
                }
                val previewUri = matchedContainerPhoto ?: allPhotos.firstOrNull()

                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(118.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVraceni) Color(0xFF4C0519) else Color(0xFF0F172A))
                        .border(if (isVraceni) 1.5.dp else 1.dp, if (isVraceni) Color(0xFF881337) else borderCol.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (!previewUri.isNullOrBlank()) {
                                activePhotoIndex = 0
                                showImageDialog = true
                            } else {
                                onOpenEdit()
                            }
                        }
                ) {
                    if (!previewUri.isNullOrBlank()) {
                        AsyncImage(
                            model = resolveCoilImageModel(previewUri),
                            contentDescription = "Foto kontejneru",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (allPhotos.size > 1) {
                            Surface(
                                color = if (isVraceni) Color(0xFF881337) else Color(0xCC0F172A),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                            ) {
                                Text(
                                    text = "📷 ${allPhotos.size}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Foto kontejneru",
                                tint = if (isVraceni) Color(0xFFFECDD3) else Color(0xFF93C5FD),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "FOTO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val rawCompany = custCompany.ifBlank { custName.ifBlank { transport.company.ifBlank { "—" } } }
                val displayCompany = remember(rawCompany) {
                    if (rawCompany.length > 26) rawCompany.take(26) else rawCompany
                }

                // 3. řádek: Pouze označení (číslo) kontejneru bez OB/SK; při více kontejnerech odděleno " | "
                val containerLineText = remember(parsedContainers, transport.designation) {
                    val list = if (parsedContainers.isNotEmpty()) {
                        parsedContainers.map { it.designation.trim() }.filter { it.isNotEmpty() }
                    } else {
                        listOf(transport.designation.trim()).filter { it.isNotEmpty() }
                    }
                    if (list.isNotEmpty()) list.joinToString(" | ") else "—"
                }

                val noteLineText = remember(transport.note) {
                    transport.note.trim()
                }
                val internalNoteLineText = remember(transport.internalNote) {
                    transport.internalNote.trim()
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. Název firmy, pro kterou se kontejner veze
                    Text(
                        text = displayCompany,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A),
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 2. Adresa (lokalita)
                    Text(
                        text = destinationLoc.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isVraceni) Color(0xFF881337) else Color(0xFF334155),
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 3. Označení kontejneru (pouze číslo kontejneru)
                    Text(
                        text = containerLineText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isVraceni) Color(0xFF881337) else borderCol,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 4. Poznámka
                    Text(
                        text = noteLineText.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (noteLineText.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (noteLineText.isBlank()) Color(0xFF94A3B8) else Color(0xFF0F172A),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 5. Interní poznámka
                    Text(
                        text = internalNoteLineText.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (internalNoteLineText.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (internalNoteLineText.isBlank()) Color(0xFFCBD5E1) else Color(0xFF881337),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Spodní panel s tlačítky ve dvou řadách (vzor iPhone):
            // 1. řada: VOLAT (volání zákazníkovi) | NAVIGOVAT (podle lokality spustit navigaci)
            // 2. řada: HOTOVO široké 3D tlačítko přes celý řádek
            HorizontalDivider(color = borderCol.copy(alpha = 0.2f), thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tlačítko VOLAT (bez vrženého stínu)
                    Button(
                        onClick = {
                            val cleanPhone = transport.customerPhone.replace(Regex("[^0-9+]"), "").trim()
                            if (cleanPhone.isNotBlank()) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                        data = android.net.Uri.parse("tel:$cleanPhone")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Nelze vytočit číslo", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Telefonní číslo není zadáno", Toast.LENGTH_SHORT).show()
                                onOpenEdit()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF15803D),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Volat zákazníkovi",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "VOLAT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Tlačítko NAVIGOVAT (bez vrženého stínu)
                    Button(
                        onClick = {
                            if (destinationLoc.isNotBlank()) {
                                try {
                                    val encoded = android.net.Uri.encode(destinationLoc)
                                    val mapIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("geo:0,0?q=$encoded")
                                    )
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    try {
                                        val encoded = android.net.Uri.encode(destinationLoc)
                                        val webIntent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
                                        )
                                        context.startActivity(webIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Nelze spustit navigaci", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Lokalita není zadána", Toast.LENGTH_SHORT).show()
                                onOpenEdit()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = "Navigovat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "NAVIGOVAT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 2. řada: Široké 3D tlačítko HOTOVO přes celý řádek s tmavě zelenou konturou a menším rozprachem stínu.
                // Pokud je přeprava již v sekci HOTOVO, kliknutím na HOTOVO se znovu obnoví do sekce NOVÁ.
                val cardHotovoInteraction = remember { MutableInteractionSource() }
                val isCardHotovoPressed by cardHotovoInteraction.collectIsPressedAsState()
                val cardHotovoBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF4ADE80), Color(0xFF15803D), Color(0xFF14532D))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .graphicsLayer {
                            translationY = if (isCardHotovoPressed) 2.dp.toPx() else 0f
                            scaleX = if (isCardHotovoPressed) 0.99f else 1f
                            scaleY = if (isCardHotovoPressed) 0.99f else 1f
                        }
                        .shadow(
                            elevation = if (isCardHotovoPressed) 1.dp else 2.5.dp,
                            shape = RoundedCornerShape(10.dp),
                            ambientColor = Color(0x33000000),
                            spotColor = Color(0x44000000)
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardHotovoBrush)
                        .border(
                            width = 2.dp,
                            color = Color(0xFF064E3B),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            interactionSource = cardHotovoInteraction,
                            indication = null
                        ) {
                            val isAlreadyHotovo = transport.status.equals("HOTOVO", ignoreCase = true) ||
                                transport.status.equals("REALIZOVÁNO", ignoreCase = true) ||
                                transport.status.equals("DOKONČENO", ignoreCase = true)
                            if (isAlreadyHotovo) {
                                onUpdate(transport.copy(status = "NOVÁ", timestamp = System.currentTimeMillis()))
                                Toast.makeText(context, "Přeprava obnovena do sekce NOVÁ", Toast.LENGTH_SHORT).show()
                            } else {
                                onOpenComplete()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isCardHotovoPressed) 0.15f else 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Hotovo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "HOTOVO",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showImageDialog && allPhotos.isNotEmpty()) {
        val safeIndex = activePhotoIndex.coerceIn(0, allPhotos.lastIndex)
        val imgToView = allPhotos[safeIndex]
        Dialog(onDismissRequest = { showImageDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                var scale by remember(safeIndex) { mutableFloatStateOf(1f) }
                var offset by remember(safeIndex) { mutableStateOf(Offset.Zero) }
                val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    offset += offsetChange
                }

                AsyncImage(
                    model = resolveCoilImageModel(imgToView),
                    contentDescription = "Detailní foto",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state),
                    contentScale = ContentScale.Fit
                )

                if (allPhotos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { activePhotoIndex = (safeIndex - 1 + allPhotos.size) % allPhotos.size }
                        ) {
                            Text("◀ Předchozí", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${safeIndex + 1} / ${allPhotos.size}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        TextButton(
                            onClick = { activePhotoIndex = (safeIndex + 1) % allPhotos.size }
                        ) {
                            Text("Další ▶", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showImageDialog = false
                            editingImageUri = imgToView
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upravit foto (Ořez / Přiblížit / Otočit 90°)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                IconButton(
                    onClick = { showImageDialog = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
                }
            }
        }
    }

    if (editingImageUri != null) {
        val targetUri = editingImageUri!!
        Dialog(
            onDismissRequest = { editingImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditImageScreen(
                imagePath = targetUri,
                onSave = { editingImageUri = null },
                onCancel = { editingImageUri = null },
                onSaveImage = { updatedDataUri ->
                    val newContainerImageUri = if (sanitizeImageUri(transport.containerImageUri) == targetUri || transport.containerImageUri.isNullOrBlank()) updatedDataUri else transport.containerImageUri
                    val oldExtras = parseExtraImageUris(transport.extraImages)
                    val newExtras = if (oldExtras.contains(targetUri)) {
                        oldExtras.map { if (it == targetUri) updatedDataUri else it }.joinToString("|")
                    } else {
                        (listOf(updatedDataUri) + oldExtras).joinToString("|")
                    }
                    val oldDelivery = if (transport.deliveryPhotos.isNotBlank()) {
                        transport.deliveryPhotos.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    } else emptyList()
                    val newDelivery = oldDelivery.map { if (it == targetUri) updatedDataUri else it }.joinToString("|")

                    val parsed = parseTransportContainersJson(
                        transport.containersJson,
                        fallbackDesignation = transport.designation,
                        fallbackType = transport.containerType
                    )
                    val updatedContainers = parsed.map { itm ->
                        itm.copy(photos = itm.photos.map { if (it == targetUri) updatedDataUri else it })
                    }
                    val newContainersJson = serializeTransportContainersJson(updatedContainers)

                    onUpdate(
                        transport.copy(
                            containerImageUri = newContainerImageUri,
                            extraImages = newExtras,
                            deliveryPhotos = newDelivery,
                            containersJson = newContainersJson,
                            imageUri = if (sanitizeImageUri(transport.imageUri) == targetUri) updatedDataUri else transport.imageUri
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportCompleteScreenDialog(
    transport: TransportRecord,
    prepravci: List<String>,
    vehicles: List<String>,
    drivers: List<String>,
    onDismiss: () -> Unit,
    onAddPrepravce: ((String) -> Unit)? = null,
    onAddVehicle: ((String) -> Unit)? = null,
    onAddDriver: ((String) -> Unit)? = null,
    onComplete: (TransportRecord) -> Unit
) {
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()) }
    var completionDate by remember {
        mutableStateOf(todayStr)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val defaultCustomerFirm = remember(transport) {
        val c = transport.customerCompany.trim().ifBlank {
            if (transport.company.equals("Kon. Autodoprava", ignoreCase = true) ||
                transport.company.equals("Kontejnery Autodoprava", ignoreCase = true)
            ) "" else transport.company.trim()
        }
        c.ifBlank { transport.customerName.trim() }
    }
    var customerFirm by remember { mutableStateOf(defaultCustomerFirm) }
    var locality by remember {
        mutableStateOf(transport.destinationLocation.trim().ifBlank { transport.customerAddress.trim() })
    }

    val driverPrefs = remember {
        context.getSharedPreferences("transport_driver_prefs", android.content.Context.MODE_PRIVATE)
    }

    var carrier by remember {
        val savedCarrier = driverPrefs.getString("last_carrier", null)?.trim().orEmpty()
        val transportCarrier = if (transport.company.isNotBlank() &&
            !transport.company.equals(transport.customerCompany, ignoreCase = true) &&
            !transport.company.equals("Kon. Autodoprava", ignoreCase = true) &&
            !transport.company.equals("Kontejnery Autodoprava", ignoreCase = true)
        ) {
            transport.company.trim()
        } else ""
        val initCarrier = transportCarrier.ifBlank { savedCarrier.ifBlank { "Kontejnery Autodoprava" } }
        mutableStateOf(initCarrier)
    }
    var carrierExpanded by remember { mutableStateOf(false) }
    var showAddCarrierDialog by remember { mutableStateOf(false) }
    var newCarrierText by remember { mutableStateOf("") }

    val allCarriers = remember(prepravci) {
        val base = mutableListOf("Kontejnery Autodoprava", "Jirka", "Martin")
        prepravci.forEach { p ->
            val trimmed = p.trim()
            if (trimmed.isNotBlank() && !base.any { it.equals(trimmed, ignoreCase = true) }) {
                base.add(trimmed)
            }
        }
        base
    }

    val allVehicles = remember(vehicles) {
        val base = mutableListOf("Volvo", "Mercedes")
        vehicles.forEach { v ->
            val trimmed = v.trim()
            if (trimmed.isNotBlank() && !base.any { it.equals(trimmed, ignoreCase = true) }) {
                base.add(trimmed)
            }
        }
        base
    }

    val allDrivers = remember(drivers) {
        val base = mutableListOf("Jirka", "Martin", "Petr", "Pavel")
        drivers.forEach { d ->
            val trimmed = d.trim()
            if (trimmed.isNotBlank() && !base.any { it.equals(trimmed, ignoreCase = true) }) {
                base.add(trimmed)
            }
        }
        base
    }

    var selectedVehicle by remember {
        val savedVehicle = driverPrefs.getString("last_vehicle", null)?.trim().orEmpty()
        mutableStateOf(transport.spz.trim().ifBlank { savedVehicle.ifBlank { "Volvo" } })
    }
    var vehicleExpanded by remember { mutableStateOf(false) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var newVehicleText by remember { mutableStateOf("") }

    var selectedDriver by remember {
        val savedDriver = driverPrefs.getString("last_driver", null)?.trim().orEmpty()
        mutableStateOf(transport.driver.trim().ifBlank { savedDriver.ifBlank { "Martin" } })
    }
    var driverExpanded by remember { mutableStateOf(false) }
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var newDriverText by remember { mutableStateOf("") }

    // Automatické zapamatování posledního zvoleného Přepravce, Vozidla a Řidiče při každé změně
    LaunchedEffect(carrier) {
        val c = carrier.trim()
        if (c.isNotBlank()) {
            driverPrefs.edit().putString("last_carrier", c).apply()
        }
    }
    LaunchedEffect(selectedVehicle) {
        val v = selectedVehicle.trim()
        if (v.isNotBlank()) {
            driverPrefs.edit().putString("last_vehicle", v).apply()
        }
    }
    LaunchedEffect(selectedDriver) {
        val d = selectedDriver.trim()
        if (d.isNotBlank()) {
            driverPrefs.edit().putString("last_driver", d).apply()
        }
    }

    var deliveryPhotosList by remember {
        val initial = if (transport.deliveryPhotos.isNotBlank()) {
            transport.deliveryPhotos.split("|").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        } else emptyList()
        mutableStateOf(initial)
    }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var previewPhotoUri by remember { mutableStateOf<String?>(null) }
    var editingPhotoUri by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists()) {
            if (deliveryPhotosList.size < 3) {
                deliveryPhotosList = (deliveryPhotosList + file.absolutePath).take(3)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val added = mutableListOf<String>()
            val remaining = (3 - deliveryPhotosList.size).coerceAtLeast(0)
            uris.take(remaining).forEachIndexed { idx, uri ->
                try {
                    val tempFile = File(context.filesDir, "complete_photo_${System.currentTimeMillis()}_$idx.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists()) {
                        added.add(tempFile.absolutePath)
                    }
                } catch (_: Exception) {}
            }
            if (added.isNotEmpty()) {
                deliveryPhotosList = (deliveryPhotosList + added).take(3)
            }
        }
    }

    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        try {
            val parts = completionDate.split(".")
            if (parts.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, parts[0].trim().toInt())
                calendar.set(Calendar.MONTH, parts[1].trim().toInt() - 1)
                calendar.set(Calendar.YEAR, parts[2].trim().toInt())
            }
        } catch (_: Exception) {}
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                completionDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showDatePicker = false }
            show()
        }
    }

    if (showAddCarrierDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddCarrierDialog = false
                newCarrierText = ""
            },
            title = { Text("Přidat přepravce", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newCarrierText,
                    onValueChange = { newCarrierText = it },
                    placeholder = { Text("Název přepravce") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCarrierText.trim()
                        if (trimmed.isNotBlank()) {
                            onAddPrepravce?.invoke(trimmed)
                            carrier = trimmed
                        }
                        showAddCarrierDialog = false
                        newCarrierText = ""
                    }
                ) { Text("Přidat") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddCarrierDialog = false
                        newCarrierText = ""
                    }
                ) { Text("Zrušit") }
            }
        )
    }

    if (showAddVehicleDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddVehicleDialog = false
                newVehicleText = ""
            },
            title = { Text("Přidat vozidlo", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newVehicleText,
                    onValueChange = { newVehicleText = it },
                    placeholder = { Text("např. Scania, Iveco, SPZ...") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newVehicleText.trim()
                        if (trimmed.isNotBlank()) {
                            onAddVehicle?.invoke(trimmed)
                            selectedVehicle = trimmed
                        }
                        showAddVehicleDialog = false
                        newVehicleText = ""
                    }
                ) { Text("Přidat") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddVehicleDialog = false
                        newVehicleText = ""
                    }
                ) { Text("Zrušit") }
            }
        )
    }

    if (showAddDriverDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDriverDialog = false
                newDriverText = ""
            },
            title = { Text("Přidat řidiče", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newDriverText,
                    onValueChange = { newDriverText = it },
                    placeholder = { Text("Jméno řidiče") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newDriverText.trim()
                        if (trimmed.isNotBlank()) {
                            onAddDriver?.invoke(trimmed)
                            selectedDriver = trimmed
                        }
                        showAddDriverDialog = false
                        newDriverText = ""
                    }
                ) { Text("Přidat") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddDriverDialog = false
                        newDriverText = ""
                    }
                ) { Text("Zrušit") }
            }
        )
    }

    val tfColors = transportTextFieldColors()
    val compactTextStyle = TextStyle(
        textAlign = TextAlign.Start,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF0F172A)
    )

    Dialog(
        onDismissRequest = {
            when {
                editingPhotoUri != null -> editingPhotoUri = null
                previewPhotoUri != null -> previewPhotoUri = null
                else -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF1F5F9),
            contentColor = Color(0xFF0F172A)
        ) {
            if (editingPhotoUri != null) {
                val targetPhoto = editingPhotoUri!!
                EditImageScreen(
                    imagePath = targetPhoto,
                    onSave = { editingPhotoUri = null },
                    onCancel = { editingPhotoUri = null },
                    onSaveImage = { updatedUri ->
                        deliveryPhotosList = deliveryPhotosList.map { if (it == targetPhoto) updatedUri else it }
                        if (previewPhotoUri == targetPhoto) previewPhotoUri = updatedUri
                        editingPhotoUri = null
                    }
                )
            } else if (previewPhotoUri != null) {
                val currPhoto = previewPhotoUri!!
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(
                        model = resolveCoilImageModel(currPhoto),
                        contentDescription = "Náhled fotky",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Button(
                        onClick = {
                            previewPhotoUri = null
                            editingPhotoUri = currPhoto
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upravit foto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { previewPhotoUri = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Horní lišta mezikroku
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF15803D))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                "DOKONČENÍ PŘEPRAVY",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. ŘÁDEK 1: Datum přepravy (font zmenšen o 20%, bez tlačítka upravit, změna kliknutím přímo na datum)
                        item {
                            val dayName = remember(completionDate) { getCzechDayOfWeek(completionDate) }
                            Card(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFECFDF5),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                border = BorderStroke(2.dp, Color(0xFF15803D)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDatePicker = true }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        "1. DATUM PŘEPRAVY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF15803D)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (dayName.isNotBlank()) "$completionDate • ${dayName.uppercase()}" else completionDate,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.clickable { showDatePicker = true }
                                    )
                                }
                            }
                        }

                        // 2. ŘÁDEK 2: Firma + lokalita
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                border = BorderStroke(1.5.dp, Color(0xFF0284C7)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "2. FIRMA + LOKALITA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0284C7)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customerFirm,
                                            onValueChange = { customerFirm = it },
                                            placeholder = {
                                                Text(
                                                    "Firma / Zákazník",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF64748B),
                                                    textAlign = TextAlign.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            textStyle = compactTextStyle,
                                            colors = tfColors,
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = locality,
                                            onValueChange = { locality = it },
                                            placeholder = {
                                                Text(
                                                    "Lokalita",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF64748B),
                                                    textAlign = TextAlign.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            textStyle = compactTextStyle,
                                            colors = tfColors,
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. SEKCE 3: Sloučená oranžová sekce Přepravce + Vozidlo a řidič
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                border = BorderStroke(1.5.dp, Color(0xFFEA580C)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "3. PŘEPRAVCE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFEA580C)
                                    )
                                    ExposedDropdownMenuBox(
                                        expanded = carrierExpanded,
                                        onExpandedChange = { carrierExpanded = !carrierExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = carrier,
                                            onValueChange = { carrier = it },
                                            placeholder = {
                                                Text(
                                                    "Kontejnery Autodoprava",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF64748B),
                                                    textAlign = TextAlign.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = carrierExpanded) },
                                            textStyle = compactTextStyle,
                                            colors = tfColors,
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true)
                                                .fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = carrierExpanded,
                                            onDismissRequest = { carrierExpanded = false },
                                            modifier = Modifier.background(Color.White)
                                        ) {
                                            allCarriers.forEach { p ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                                                            Text(p, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                        }
                                                    },
                                                    onClick = {
                                                        carrier = p
                                                        carrierExpanded = false
                                                    }
                                                )
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                                                        Text("+ Přidat dalšího přepravce", fontWeight = FontWeight.Bold, color = Color(0xFFEA580C), fontSize = 12.sp)
                                                    }
                                                },
                                                onClick = {
                                                    carrierExpanded = false
                                                    showAddCarrierDialog = true
                                                }
                                            )
                                        }
                                    }

                                    // Vozidlo a řidič na stejném řádku přímo ve sloučené oranžové sekci 3
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Vozidlo (Volvo, Mercedes + přidat další)
                                        ExposedDropdownMenuBox(
                                            expanded = vehicleExpanded,
                                            onExpandedChange = { vehicleExpanded = !vehicleExpanded },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedVehicle,
                                                onValueChange = { selectedVehicle = it },
                                                placeholder = {
                                                    Text(
                                                        "Vozidlo",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF64748B),
                                                        textAlign = TextAlign.Start,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleExpanded) },
                                                textStyle = compactTextStyle,
                                                colors = tfColors,
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true)
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = vehicleExpanded,
                                                onDismissRequest = { vehicleExpanded = false },
                                                modifier = Modifier.background(Color.White)
                                            ) {
                                                allVehicles.forEach { v ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                                                                Text(v, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                            }
                                                        },
                                                        onClick = {
                                                            selectedVehicle = v
                                                            vehicleExpanded = false
                                                        }
                                                    )
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                                                            Text("+ Přidat další", fontWeight = FontWeight.Bold, color = Color(0xFFEA580C), fontSize = 12.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        vehicleExpanded = false
                                                        showAddVehicleDialog = true
                                                    }
                                                )
                                            }
                                        }

                                        // Řidič (rozbalovací tlačítko + možnost úpravy jména řidiče)
                                        ExposedDropdownMenuBox(
                                            expanded = driverExpanded,
                                            onExpandedChange = { driverExpanded = !driverExpanded },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedDriver,
                                                onValueChange = { selectedDriver = it },
                                                placeholder = {
                                                    Text(
                                                        "Řidič",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF64748B),
                                                        textAlign = TextAlign.Start,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driverExpanded) },
                                                textStyle = compactTextStyle,
                                                colors = tfColors,
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true)
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = driverExpanded,
                                                onDismissRequest = { driverExpanded = false },
                                                modifier = Modifier.background(Color.White)
                                            ) {
                                                allDrivers.forEach { d ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                                                                Text(d, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                            }
                                                        },
                                                        onClick = {
                                                            selectedDriver = d
                                                            driverExpanded = false
                                                        }
                                                    )
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                                                            Text("+ Přidat další", fontWeight = FontWeight.Bold, color = Color(0xFFEA580C), fontSize = 12.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        driverExpanded = false
                                                        showAddDriverDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. SEKCE 4: Vložení skutečných fotografií z přepravy řidičem (3 připravená prázdná políčka + FOTIT / GALERIE)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                border = BorderStroke(1.5.dp, Color(0xFF0F766E)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "4. VLOŽIT FOTOGRAFIE Z PŘEPRAVY (MAX. 3)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F766E)
                                        )
                                        Text(
                                            "${deliveryPhotosList.size}/3",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F766E)
                                        )
                                    }

                                    // 3 políčka (prázdná nebo vyplněná skutečnou fotografií z přepravy)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (slotIdx in 0 until 3) {
                                            val pUri = deliveryPhotosList.getOrNull(slotIdx)
                                            if (pUri != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(86.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.5.dp, Color(0xFF0F766E), RoundedCornerShape(8.dp))
                                                        .clickable { previewPhotoUri = pUri }
                                                ) {
                                                    AsyncImage(
                                                        model = resolveCoilImageModel(pUri),
                                                        contentDescription = "Foto ${slotIdx + 1}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Surface(
                                                        color = Color(0xCC0F172A),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                                                    ) {
                                                        Text(
                                                            "${slotIdx + 1}/3",
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        color = Color(0xFF881337),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(3.dp)
                                                            .clickable {
                                                                deliveryPhotosList = deliveryPhotosList.filterIndexed { i, _ -> i != slotIdx }
                                                            }
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Smazat foto",
                                                            tint = Color.White,
                                                            modifier = Modifier.padding(2.dp).size(12.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Prázdné políčko pro vložení fotografie z přepravy
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(86.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFF0FDFA))
                                                        .border(1.dp, Color(0xFF0F766E).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            try {
                                                                val newFile = File(context.filesDir, "complete_cam_${System.currentTimeMillis()}.jpg")
                                                                pendingCameraFile = newFile
                                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)
                                                                cameraLauncher.launch(uri)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít fotoaparát", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.AddAPhoto,
                                                            contentDescription = "Vložit foto ${slotIdx + 1}",
                                                            tint = Color(0xFF0F766E).copy(alpha = 0.65f),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(Modifier.height(3.dp))
                                                        Text(
                                                            "Foto ${slotIdx + 1}/3",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F766E).copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Tlačítka VYFOTIT a GALERIE
                                    if (deliveryPhotosList.size < 3) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val newFile = File(context.filesDir, "complete_cam_${System.currentTimeMillis()}.jpg")
                                                        pendingCameraFile = newFile
                                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)
                                                        cameraLauncher.launch(uri)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Nelze otevřít fotoaparát", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(44.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF0F766E),
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = "Fotit", modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("FOTIT", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        galleryLauncher.launch("image/*")
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Nelze otevřít galerii", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(44.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.5.dp, Color(0xFF0F766E)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = Color(0xFFF0FDFA),
                                                    contentColor = Color(0xFF0F766E)
                                                )
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = "Galerie", modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("GALERIE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Tlačítko HOTOVO s tmavě zelenou konturkou a menším rozprachem vrženého stínu
                        item {
                            val hotovoInteraction = remember { MutableInteractionSource() }
                            val isHotovoPressed by hotovoInteraction.collectIsPressedAsState()
                            val green3dBrush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF4ADE80), Color(0xFF15803D), Color(0xFF14532D))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .graphicsLayer {
                                        translationY = if (isHotovoPressed) 2.dp.toPx() else 0f
                                        scaleX = if (isHotovoPressed) 0.99f else 1f
                                        scaleY = if (isHotovoPressed) 0.99f else 1f
                                    }
                                    .shadow(
                                        elevation = if (isHotovoPressed) 1.dp else 3.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        ambientColor = Color(0x33000000),
                                        spotColor = Color(0x44000000)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(green3dBrush)
                                    .border(
                                        width = 2.dp,
                                        color = Color(0xFF064E3B),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable(
                                        interactionSource = hotovoInteraction,
                                        indication = null
                                    ) {
                                        val finalCarrier = carrier.trim().ifBlank { "Kontejnery Autodoprava" }
                                        val finalVeh = selectedVehicle.trim().ifBlank { "Volvo" }
                                        val finalDrv = selectedDriver.trim()
                                        driverPrefs.edit()
                                            .putString("last_carrier", finalCarrier)
                                            .putString("last_vehicle", finalVeh)
                                            .putString("last_driver", finalDrv)
                                            .apply()
                                        if (finalCarrier.isNotBlank()) onAddPrepravce?.invoke(finalCarrier)
                                        if (finalVeh.isNotBlank()) onAddVehicle?.invoke(finalVeh)
                                        if (finalDrv.isNotBlank()) onAddDriver?.invoke(finalDrv)

                                        val updated = transport.copy(
                                            transportDate = completionDate.trim(),
                                            dateText = completionDate.trim(),
                                            customerCompany = customerFirm.trim().ifBlank { transport.customerCompany },
                                            destinationLocation = locality.trim(),
                                            company = finalCarrier,
                                            spz = finalVeh,
                                            driver = finalDrv,
                                            deliveryPhotos = deliveryPhotosList.joinToString("|"),
                                            status = "HOTOVO",
                                            timestamp = System.currentTimeMillis()
                                        )
                                        onComplete(updated)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = if (isHotovoPressed) 0.15f else 0.45f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Hotovo",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "HOTOVO",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 6. Tlačítko ZRUŠIT s menším rozprachem vrženého stínu
                        item {
                            val cancelInteraction = remember { MutableInteractionSource() }
                            val isCancelPressed by cancelInteraction.collectIsPressedAsState()
                            val red3dBrush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFF87171), Color(0xFFDC2626), Color(0xFF7F1D1D))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .graphicsLayer {
                                        translationY = if (isCancelPressed) 2.dp.toPx() else 0f
                                        scaleX = if (isCancelPressed) 0.99f else 1f
                                        scaleY = if (isCancelPressed) 0.99f else 1f
                                    }
                                    .shadow(
                                        elevation = if (isCancelPressed) 1.dp else 3.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        ambientColor = Color(0x33000000),
                                        spotColor = Color(0x44000000)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(red3dBrush)
                                    .border(
                                        width = 2.dp,
                                        color = Color(0xFF7F1D1D),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable(
                                        interactionSource = cancelInteraction,
                                        indication = null
                                    ) {
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = if (isCancelPressed) 0.15f else 0.45f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Zrušit",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "ZRUŠIT",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportEditDialog(
    transport: TransportRecord,
    allTransports: List<TransportRecord> = emptyList(),
    customers: List<CustomerRecord>,
    zakazky: List<ZakazkaRecord>,
    containers: List<ContainerCard>,
    prepravci: List<String>,
    equipmentList: List<String> = PRESET_EQUIPMENT,
    vehicles: List<String> = emptyList(),
    drivers: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (TransportRecord) -> Unit,
    onDelete: (TransportRecord) -> Unit,
    onAddPrepravce: ((String) -> Unit)? = null,
    onAddVehicle: ((String) -> Unit)? = null,
    onAddDriver: ((String) -> Unit)? = null,
    onAutoSave: ((TransportRecord) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordId = remember(transport.id) {
        transport.id.ifBlank { "tr_${System.currentTimeMillis()}" }
    }
    var company by remember {
        val initComp = transport.company.trim()
        mutableStateOf(
            if (initComp.isBlank() || initComp.equals("Kon. Autodoprava", ignoreCase = true)) {
                "Kontejnery Autodoprava"
            } else {
                initComp
            }
        )
    }
    var dateText by remember { mutableStateOf(transport.dateText) }
    var transportDate by remember { mutableStateOf(transport.transportDate.ifBlank { transport.dateText.ifBlank { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()) } }) }
    var spz by remember { mutableStateOf(transport.spz) }
    var driver by remember { mutableStateOf(transport.driver) }
    var note by remember { mutableStateOf(transport.note) }
    var internalNote by remember { mutableStateOf(transport.internalNote) }
    var rawText by remember { mutableStateOf(transport.rawExtractedText) }
    var status by remember { mutableStateOf(formatTransportStatusLabel(transport.status)) }
    var depo by remember { mutableStateOf(transport.depo.ifBlank { "Depo 1" }) }
    var showTransportDatePickerDialog by remember { mutableStateOf(false) }
    var editingPhotoUri by remember { mutableStateOf<String?>(null) }

    // Delivery photos (fotografie z vykládky / přepravy - max. 3)
    var deliveryPhotosList by remember {
        val initial = if (transport.deliveryPhotos.isNotBlank()) {
            transport.deliveryPhotos.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        } else emptyList()
        mutableStateOf(initial)
    }
    var pendingDeliveryCameraFile by remember { mutableStateOf<File?>(null) }

    // Multi-container list (podpora více kontejnerů na jednu přepravu)
    var containersList by remember {
        val initialPhotos = mutableListOf<String>()
        transport.containerImageUri?.trim()?.takeIf { it.isNotEmpty() }?.let { initialPhotos.add(it) }
        if (transport.extraImages.isNotBlank()) {
            transport.extraImages.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !initialPhotos.contains(it) }
                .forEach { initialPhotos.add(it) }
        }
        val parsed = parseTransportContainersJson(
            transport.containersJson,
            fallbackDesignation = transport.designation,
            fallbackType = transport.containerType,
            fallbackPhotos = initialPhotos
        )
        mutableStateOf(if (parsed.isEmpty()) listOf(TransportContainerItem(containerType = "OB")) else parsed)
    }

    var previewPhotoUri by remember { mutableStateOf<String?>(null) }
    var activePhotoContainerIndex by remember { mutableIntStateOf(0) }
    var activeContainerTargetIndex by remember { mutableIntStateOf(-1) }

    // Customer fields (Firma, Osoba, Telefon, Adresa, IČ) - iniciály zrušeny
    var customerCompany by remember {
        val rawCust = transport.customerCompany.trim()
        val cleaned = if (
            rawCust.equals("Kon. Autodoprava", ignoreCase = true) ||
            rawCust.equals("Kontejnery Autodoprava", ignoreCase = true)
        ) "" else rawCust
        mutableStateOf(cleaned)
    }
    var customerName by remember { mutableStateOf(transport.customerName) }
    var customerIc by remember { mutableStateOf(transport.customerIc) }
    var customerAddress by remember { mutableStateOf(transport.customerAddress) }
    var customerPhone by remember { mutableStateOf(transport.customerPhone) }
    var customerSource by remember { mutableStateOf(transport.customerSource.ifBlank { "RUČNĚ" }) }
    var sourceOrderId by remember { mutableStateOf(transport.sourceOrderId) }
    var sourceZakazkaId by remember { mutableStateOf(transport.sourceZakazkaId) }
    var cisloDokladuZdroj by remember { mutableStateOf(transport.cisloDokladuZdroj) }
    var isVraceni by remember {
        mutableStateOf(
            transport.isVraceni ||
            transport.customerSource.contains("VRÁCENÍ", ignoreCase = true) ||
            transport.note.contains("VRÁCENÍ", ignoreCase = true) ||
            transport.designation.contains("VRÁCENÍ", ignoreCase = true) ||
            transport.containerType.contains("VRÁCENÍ", ignoreCase = true)
        )
    }

    // Krok 4: Lokalita (kam se to má vést)
    var destinationLocation by remember {
        mutableStateOf(transport.destinationLocation.ifBlank { transport.customerAddress })
    }

    var showContainerPicker by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var prepravceExpanded by remember { mutableStateOf(false) }
    var spzExpanded by remember { mutableStateOf(false) }
    var driverExpanded by remember { mutableStateOf(false) }
    var showAddPrepravceDialog by remember { mutableStateOf(false) }
    var newPrepravceName by remember { mutableStateOf("") }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var newVehicleName by remember { mutableStateOf("") }
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var newDriverName by remember { mutableStateOf("") }
    var showOrderPicker by remember { mutableStateOf(false) }
    var showZakazkaPicker by remember { mutableStateOf(false) }
    var showAdresarPicker by remember { mutableStateOf(false) }
    var pendingCameraPhotoFile by remember { mutableStateOf<File?>(null) }

    // Seznam přepravců obsahuje standardně Kontejnery Autodoprava, Jirka a Martin
    val allCarriers = remember(prepravci) {
        val base = mutableListOf("Kontejnery Autodoprava", "Jirka", "Martin")
        prepravci.forEach { p ->
            val trimmed = p.trim()
            if (trimmed.isNotBlank() && !base.any { it.equals(trimmed, ignoreCase = true) }) {
                base.add(trimmed)
            }
        }
        base
    }

    // Možnosti vozidel (standardně Volvo, Mercedes + možnost přidat další)
    val vehicleOptions = remember(vehicles) {
        val base = mutableListOf("Volvo", "Mercedes")
        vehicles.forEach { v ->
            val t = v.trim()
            if (t.isNotBlank() && !base.any { it.equals(t, ignoreCase = true) }) base.add(t)
        }
        base
    }

    // Možnosti řidičů ze správy nastavení
    val driverOptions = remember(drivers) {
        val base = mutableListOf("Jirka", "Martin", "Petr", "Pavel")
        drivers.forEach { d ->
            val t = d.trim()
            if (t.isNotBlank() && !base.any { it.equals(t, ignoreCase = true) }) base.add(t)
        }
        base
    }

    val containerCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraPhotoFile
        if (success && file != null && file.exists()) {
            val targetIdx = activePhotoContainerIndex.coerceIn(0, containersList.lastIndex)
            val currentPhotos = containersList[targetIdx].photos
            if (currentPhotos.size < 3) {
                val updatedPhotos = currentPhotos + file.absolutePath
                containersList = containersList.mapIndexed { idx, itm ->
                    if (idx == targetIdx) itm.copy(photos = updatedPhotos) else itm
                }
            }
            scope.launch {
                val extracted = com.example.data.PhotoDataExtractor.extractContainerData(context, file.absolutePath)
                val exDesig = extracted["designation"].orEmpty()
                val exType = extracted["type"].orEmpty()
                val exColor = extracted["color"].orEmpty()
                val exSpz = extracted["spz"].orEmpty()
                val exDesc = com.example.api.formatContainerShortDescription(exColor, exType, extracted["description"])
                if (exDesig.isNotBlank() || exType.isNotBlank()) {
                    containersList = containersList.mapIndexed { idx, itm ->
                        if (idx == targetIdx) {
                            itm.copy(
                                designation = if (itm.designation.isBlank() && exDesig.isNotBlank()) exDesig else itm.designation,
                                containerType = if (itm.containerType.isBlank() && exType.isNotBlank()) exType else itm.containerType
                            )
                        } else itm
                    }
                }
                if (spz.isBlank() && exSpz.isNotBlank()) spz = exSpz
                if (note.isBlank() && exDesc.isNotBlank()) note = exDesc
                Toast.makeText(
                    context,
                    "✅ Z fotografie kontejneru vyčteno: ${exDesig.ifBlank { "kontejner" }} ($exDesc)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val containerMultiGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val addedPaths = mutableListOf<String>()
            uris.forEachIndexed { idx, uri ->
                try {
                    val tempFile = File(context.filesDir, "container_photo_${System.currentTimeMillis()}_$idx.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists()) {
                        addedPaths.add(tempFile.absolutePath)
                    }
                } catch (_: Exception) {}
            }
            if (addedPaths.isNotEmpty()) {
                val targetIdx = activePhotoContainerIndex.coerceIn(0, containersList.lastIndex)
                val currentPhotos = containersList[targetIdx].photos
                val remaining = (3 - currentPhotos.size).coerceAtLeast(0)
                val updatedPhotos = currentPhotos + addedPaths.take(remaining)
                containersList = containersList.mapIndexed { idx, itm ->
                    if (idx == targetIdx) itm.copy(photos = updatedPhotos) else itm
                }
                scope.launch {
                    val firstPath = addedPaths.first()
                    val extracted = com.example.data.PhotoDataExtractor.extractContainerData(context, firstPath)
                    val exDesig = extracted["designation"].orEmpty()
                    val exType = extracted["type"].orEmpty()
                    val exColor = extracted["color"].orEmpty()
                    val exSpz = extracted["spz"].orEmpty()
                    val exDesc = com.example.api.formatContainerShortDescription(exColor, exType, extracted["description"])
                    if (exDesig.isNotBlank() || exType.isNotBlank()) {
                        containersList = containersList.mapIndexed { idx, itm ->
                            if (idx == targetIdx) {
                                itm.copy(
                                    designation = if (itm.designation.isBlank() && exDesig.isNotBlank()) exDesig else itm.designation,
                                    containerType = if (itm.containerType.isBlank() && exType.isNotBlank()) exType else itm.containerType
                                )
                            } else itm
                        }
                    }
                    if (spz.isBlank() && exSpz.isNotBlank()) spz = exSpz
                    if (note.isBlank() && exDesc.isNotBlank()) note = exDesc
                    Toast.makeText(
                        context,
                        "✅ Z fotografie kontejneru vyčteno: ${exDesig.ifBlank { "kontejner" }} ($exDesc)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val deliveryCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingDeliveryCameraFile
        if (success && file != null && file.exists()) {
            if (deliveryPhotosList.size < 3) {
                deliveryPhotosList = deliveryPhotosList + file.absolutePath
            }
            scope.launch {
                Toast.makeText(context, "Vytěžuji údaje z vloženého dokladu...", Toast.LENGTH_SHORT).show()
                val extracted = com.example.data.PhotoDataExtractor.extractTransportData(context, file.absolutePath)
                val filled = mutableListOf<String>()
                val exSpz = extracted["spz"].orEmpty()
                val exComp = extracted["company"].orEmpty().ifBlank { extracted["firma"].orEmpty() }
                val exDriver = extracted["driver"].orEmpty().ifBlank { extracted["ridic"].orEmpty() }
                val exDate = extracted["date"].orEmpty().ifBlank { extracted["datum"].orEmpty() }
                val exDesig = extracted["designation"].orEmpty().ifBlank { extracted["container"].orEmpty() }
                val exNote = extracted["note"].orEmpty().ifBlank { extracted["poznamka"].orEmpty() }

                if (spz.isBlank() && exSpz.isNotBlank()) {
                    spz = exSpz
                    filled.add("SPZ")
                }
                if (company.isBlank() && exComp.isNotBlank()) {
                    company = exComp
                    filled.add("Firma")
                }
                if (driver.isBlank() && exDriver.isNotBlank()) {
                    driver = exDriver
                    filled.add("Řidič")
                }
                if (dateText.isBlank() && exDate.isNotBlank()) {
                    dateText = exDate
                    filled.add("Datum")
                }
                if (exDesig.isNotBlank()) {
                    val firstEmpty = containersList.indexOfFirst { it.designation.isBlank() }
                    if (firstEmpty != -1) {
                        containersList = containersList.mapIndexed { i, itm ->
                            if (i == firstEmpty) itm.copy(designation = exDesig) else itm
                        }
                        filled.add("Kontejner")
                    }
                }
                if (note.isBlank() && exNote.isNotBlank()) {
                    note = exNote
                    filled.add("Poznámka")
                }
                if (filled.isNotEmpty()) {
                    Toast.makeText(context, "✅ Z dokladu byla vyčtena data: ${filled.joinToString(", ")}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val deliveryGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val addedPaths = mutableListOf<String>()
            val remaining = (3 - deliveryPhotosList.size).coerceAtLeast(0)
            uris.take(remaining).forEachIndexed { idx, uri ->
                try {
                    val tempFile = File(context.filesDir, "delivery_photo_${System.currentTimeMillis()}_$idx.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists()) {
                        addedPaths.add(tempFile.absolutePath)
                    }
                } catch (_: Exception) {}
            }
            if (addedPaths.isNotEmpty()) {
                deliveryPhotosList = (deliveryPhotosList + addedPaths).take(3)
                val photoToAnalyze = addedPaths.first()
                scope.launch {
                    Toast.makeText(context, "Vytěžuji údaje z vloženého dokladu...", Toast.LENGTH_SHORT).show()
                    val extracted = com.example.data.PhotoDataExtractor.extractTransportData(context, photoToAnalyze)
                    val filled = mutableListOf<String>()
                    val exSpz = extracted["spz"].orEmpty()
                    val exComp = extracted["company"].orEmpty().ifBlank { extracted["firma"].orEmpty() }
                    val exDriver = extracted["driver"].orEmpty().ifBlank { extracted["ridic"].orEmpty() }
                    val exDate = extracted["date"].orEmpty().ifBlank { extracted["datum"].orEmpty() }
                    val exDesig = extracted["designation"].orEmpty().ifBlank { extracted["container"].orEmpty() }
                    val exNote = extracted["note"].orEmpty().ifBlank { extracted["poznamka"].orEmpty() }

                    if (spz.isBlank() && exSpz.isNotBlank()) {
                        spz = exSpz
                        filled.add("SPZ")
                    }
                    if (company.isBlank() && exComp.isNotBlank()) {
                        company = exComp
                        filled.add("Firma")
                    }
                    if (driver.isBlank() && exDriver.isNotBlank()) {
                        driver = exDriver
                        filled.add("Řidič")
                    }
                    if (dateText.isBlank() && exDate.isNotBlank()) {
                        dateText = exDate
                        filled.add("Datum")
                    }
                    if (exDesig.isNotBlank()) {
                        val firstEmpty = containersList.indexOfFirst { it.designation.isBlank() }
                        if (firstEmpty != -1) {
                            containersList = containersList.mapIndexed { i, itm ->
                                if (i == firstEmpty) itm.copy(designation = exDesig) else itm
                            }
                            filled.add("Kontejner")
                        }
                    }
                    if (note.isBlank() && exNote.isNotBlank()) {
                        note = exNote
                        filled.add("Poznámka")
                    }
                    if (filled.isNotEmpty()) {
                        Toast.makeText(context, "✅ Z dokladu byla vyčtena data: ${filled.joinToString(", ")}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val calendar = Calendar.getInstance()
        try {
            val parts = dateText.split(".")
            if (parts.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, parts[0].trim().toInt())
                calendar.set(Calendar.MONTH, parts[1].trim().toInt() - 1)
                calendar.set(Calendar.YEAR, parts[2].trim().toInt())
            }
        } catch (_: Exception) {}
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                val formatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
                dateText = formatted
                transportDate = formatted
                showDatePickerDialog = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showDatePickerDialog = false }
            show()
        }
    }

    if (showTransportDatePickerDialog) {
        val calendar = Calendar.getInstance()
        try {
            val parts = transportDate.split(".")
            if (parts.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, parts[0].trim().toInt())
                calendar.set(Calendar.MONTH, parts[1].trim().toInt() - 1)
                calendar.set(Calendar.YEAR, parts[2].trim().toInt())
            }
        } catch (_: Exception) {}
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                val formatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
                transportDate = formatted
                dateText = formatted
                showTransportDatePickerDialog = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showTransportDatePickerDialog = false }
            show()
        }
    }

    if (showContainerPicker) {
        TransportContainerPickerDialog(
            containers = containers,
            onDismiss = { showContainerPicker = false },
            onSelectContainer = { cont ->
                val desig = cont.designation.ifBlank { "Kontejner #${cont.systemNumber}" }
                val normDesig = desig.uppercase().replace(Regex("[^A-Z0-9]"), "")
                val dupInCurrent = normDesig.length >= 2 && containersList.withIndex().any { (idx, itm) ->
                    idx != activeContainerTargetIndex &&
                    itm.designation.uppercase().replace(Regex("[^A-Z0-9]"), "") == normDesig
                }
                if (dupInCurrent) {
                    Toast.makeText(context, "Duplicita: Kontejner $desig je již v této přepravě načten!", Toast.LENGTH_LONG).show()
                    return@TransportContainerPickerDialog
                }
                val dupInOther = if (normDesig.length >= 2) {
                    allTransports.firstOrNull { tr ->
                        tr.id != recordId &&
                        !tr.status.equals("ZRUŠENO", ignoreCase = true) &&
                        !tr.status.equals("ZRUŠENÉ", ignoreCase = true) &&
                        parseTransportContainersJson(tr.containersJson, tr.designation, tr.containerType).any { c ->
                            c.designation.uppercase().replace(Regex("[^A-Z0-9]"), "") == normDesig
                        }
                    }
                } else null
                if (dupInOther != null) {
                    Toast.makeText(context, "Duplicita: Kontejner $desig je již načten v přepravě (${dupInOther.company.ifBlank { dupInOther.customerCompany }})!", Toast.LENGTH_LONG).show()
                    return@TransportContainerPickerDialog
                }

                showContainerPicker = false
                val rawType = cont.type.trim()
                val cType = if (rawType.uppercase().startsWith("SK")) "SK" else if (rawType.uppercase().startsWith("OB") || rawType.isBlank()) "OB" else rawType
                val contPhoto = primaryContainerPhoto(cont)
                val contPhotos = if (!contPhoto.isNullOrBlank()) listOf(contPhoto) else emptyList()

                if (activeContainerTargetIndex in 0..containersList.lastIndex) {
                    containersList = containersList.mapIndexed { idx, itm ->
                        if (idx == activeContainerTargetIndex) {
                            itm.copy(
                                designation = desig,
                                containerType = cType,
                                photos = if (contPhotos.isNotEmpty() && itm.photos.isEmpty()) contPhotos else itm.photos
                            )
                        } else itm
                    }
                } else if (containersList.size == 1 && containersList[0].designation.isBlank()) {
                    containersList = listOf(
                        containersList[0].copy(
                            designation = desig,
                            containerType = cType,
                            photos = if (contPhotos.isNotEmpty() && containersList[0].photos.isEmpty()) contPhotos else containersList[0].photos
                        )
                    )
                } else {
                    containersList = containersList + TransportContainerItem(
                        designation = desig,
                        containerType = cType,
                        photos = contPhotos
                    )
                }
                if (cont.depo.isNotBlank()) depo = cont.depo
                Toast.makeText(context, "Kontejner $desig ($cType) načten", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showOrderPicker) {
        TransportOrderPickerDialog(
            customers = customers,
            onDismiss = { showOrderPicker = false },
            onSelectOrder = { order ->
                showOrderPicker = false
                customerCompany = order.company
                customerName = order.name
                customerIc = order.ic
                customerAddress = order.ulice
                customerPhone = order.phone
                customerSource = "OBJEDNÁVKA"
                sourceOrderId = order.id
                sourceZakazkaId = ""
                cisloDokladuZdroj = order.cisloZakaznika.ifBlank { "OBJ" }
                if (company.isBlank()) company = order.company.ifBlank { order.name }
                if (destinationLocation.isBlank() || destinationLocation == transport.customerAddress) {
                    destinationLocation = order.ulice
                }
                if (containersList.isNotEmpty() && containersList[0].designation.isBlank() && order.inquiryType.isNotBlank()) {
                    containersList = containersList.mapIndexed { i, itm ->
                        if (i == 0) itm.copy(containerType = order.inquiryType) else itm
                    }
                }
                Toast.makeText(context, "Zákazník načten z objednávky", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showZakazkaPicker) {
        TransportZakazkaPickerDialog(
            zakazky = zakazky,
            onDismiss = { showZakazkaPicker = false },
            onSelectZakazka = { zak ->
                showZakazkaPicker = false
                customerCompany = zak.firma
                customerName = zak.vyrizuje
                customerIc = zak.ic
                customerAddress = zak.lokalita.ifBlank { zak.ulice }
                customerPhone = zak.telefon
                customerSource = "ZAKÁZKA"
                sourceZakazkaId = zak.id
                sourceOrderId = ""
                cisloDokladuZdroj = zak.cisloZakazky
                if (company.isBlank()) company = zak.firma.ifBlank { zak.vyrizuje }
                destinationLocation = zak.lokalita.ifBlank { zak.ulice }
                if (containersList.isNotEmpty() && containersList[0].designation.isBlank()) {
                    containersList = containersList.mapIndexed { i, itm ->
                        if (i == 0) itm.copy(designation = zak.cisloZakazky, containerType = zak.stredisko.ifBlank { itm.containerType }) else itm
                    }
                }
                Toast.makeText(context, "Zákazník načten ze zakázky ${zak.cisloZakazky}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAdresarPicker) {
        val adresarContacts = remember(customers) {
            customers.filter { it.isAdresar }
        }
        TransportAdresarPickerDialog(
            adresar = adresarContacts,
            isVraceni = isVraceni,
            onDismiss = { showAdresarPicker = false },
            onSelectAdresar = { adr ->
                showAdresarPicker = false
                customerCompany = adr.company.ifBlank { adr.name }
                customerName = adr.name
                customerIc = adr.ic
                val adrLocation = adr.ulice
                customerAddress = adrLocation
                customerPhone = adr.phone
                customerSource = if (isVraceni) "VRÁCENÍ (ADRESÁŘ)" else "ADRESÁŘ"
                cisloDokladuZdroj = adr.cisloZakaznika.ifBlank { "ADR" }
                if (company.isBlank()) company = adr.company.ifBlank { adr.name }
                if (destinationLocation.isBlank() || destinationLocation == transport.customerAddress) {
                    destinationLocation = adrLocation
                }
                Toast.makeText(context, "Zákazník načten z adresáře: ${adr.company.ifBlank { adr.name }}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddPrepravceDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddPrepravceDialog = false
                newPrepravceName = ""
            },
            title = {
                Text("Přepravce", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = newPrepravceName,
                    onValueChange = { newPrepravceName = it },
                    placeholder = { Text("např. Tomáš") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPrepravceName.trim()
                        if (trimmed.isNotBlank()) {
                            onAddPrepravce?.invoke(trimmed)
                            company = trimmed
                        }
                        showAddPrepravceDialog = false
                        newPrepravceName = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Přidat")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddPrepravceDialog = false
                        newPrepravceName = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showAddVehicleDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddVehicleDialog = false
                newVehicleName = ""
            },
            title = {
                Text("Přidat vozidlo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = newVehicleName,
                    onValueChange = { newVehicleName = it },
                    placeholder = { Text("např. Volvo, Mercedes...") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newVehicleName.trim()
                        if (trimmed.isNotBlank()) {
                            onAddVehicle?.invoke(trimmed)
                            spz = trimmed
                        }
                        showAddVehicleDialog = false
                        newVehicleName = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Přidat")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddVehicleDialog = false
                        newVehicleName = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showAddDriverDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDriverDialog = false
                newDriverName = ""
            },
            title = {
                Text("Přidat řidiče", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = newDriverName,
                    onValueChange = { newDriverName = it },
                    placeholder = { Text("např. Tomáš") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newDriverName.trim()
                        if (trimmed.isNotBlank()) {
                            onAddDriver?.invoke(trimmed)
                            driver = trimmed
                        }
                        showAddDriverDialog = false
                        newDriverName = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Přidat")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddDriverDialog = false
                        newDriverName = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    fun findContainerDuplicateError(): String? {
        val seen = mutableSetOf<String>()
        for (item in containersList) {
            val d = item.designation.trim()
            val norm = d.uppercase().replace(Regex("[^A-Z0-9]"), "")
            if (norm.length >= 2) {
                if (!seen.add(norm)) {
                    return "Duplicita: Kontejner $d je v této přepravě uveden 2x!"
                }
                val other = allTransports.firstOrNull { tr ->
                    tr.id != recordId &&
                    !tr.status.equals("ZRUŠENO", ignoreCase = true) &&
                    !tr.status.equals("ZRUŠENÉ", ignoreCase = true) &&
                    parseTransportContainersJson(tr.containersJson, tr.designation, tr.containerType).any { c ->
                        c.designation.uppercase().replace(Regex("[^A-Z0-9]"), "") == norm
                    }
                }
                if (other != null) {
                    return "Duplicita: Kontejner $d je již načten v jiné přepravě (${other.company.ifBlank { other.customerCompany }})!"
                }
            }
        }
        return null
    }

    fun buildCurrentRecord(): TransportRecord {
        val finalContainersJson = serializeTransportContainersJson(containersList)
        val allPhotosList = containersList.flatMap { it.photos }.filter { it.isNotBlank() }
        val primaryPhoto = allPhotosList.firstOrNull()
        val extraPhotosStr = allPhotosList.drop(1).joinToString("|")
        val summaryType = formatContainerSummary(containersList, "OB")
        val summaryDesignation = containersList.map { it.designation.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            .ifBlank { "KONTEJNER" }

        val effDate = dateText.ifBlank { transportDate }.trim()

        return transport.copy(
            id = recordId,
            designation = summaryDesignation,
            company = company.trim().ifBlank { "Kontejnery Autodoprava" },
            dateText = effDate,
            transportDate = effDate,
            spz = spz.trim(),
            driver = driver.trim(),
            containerType = summaryType,
            note = note.trim(),
            internalNote = internalNote.trim(),
            rawExtractedText = rawText,
            containerImageUri = primaryPhoto,
            extraImages = extraPhotosStr,
            customerName = customerName.trim(),
            customerCompany = customerCompany.trim(),
            customerIc = customerIc.trim(),
            customerDic = "",
            customerAddress = customerAddress.trim(),
            customerPhone = customerPhone.trim(),
            customerEmail = "",
            customerInitials = "",
            customerSource = customerSource,
            sourceOrderId = sourceOrderId,
            sourceZakazkaId = sourceZakazkaId,
            cisloDokladuZdroj = cisloDokladuZdroj,
            destinationLocation = destinationLocation.trim(),
            containersJson = finalContainersJson,
            deliveryPhotos = deliveryPhotosList.joinToString("|"),
            status = status,
            depo = depo,
            isVraceni = isVraceni
        )
    }

    // Automatické ukládání při jakékoli změně v aplikaci (vložení vybavení / židle, fotky, úprava textu atd.)
    var isInitializedAutoSave by remember { mutableStateOf(false) }
    LaunchedEffect(
        containersList,
        deliveryPhotosList,
        status,
        depo,
        dateText,
        transportDate,
        company,
        spz,
        driver,
        note,
        internalNote,
        customerCompany,
        customerName,
        customerPhone,
        customerAddress,
        customerIc,
        destinationLocation,
        isVraceni
    ) {
        if (!isInitializedAutoSave) {
            isInitializedAutoSave = true
            return@LaunchedEffect
        }
        if (findContainerDuplicateError() == null) {
            val current = buildCurrentRecord()
            onAutoSave?.invoke(current)
        }
    }

    val tfColors = transportTextFieldColors()
    val compactTextStyle = TextStyle(textAlign = TextAlign.Start, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

    Dialog(
        onDismissRequest = {
            when {
                editingPhotoUri != null -> editingPhotoUri = null
                previewPhotoUri != null -> previewPhotoUri = null
                else -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF1F5F9),
            contentColor = Color(0xFF0F172A)
        ) {
            if (editingPhotoUri != null) {
                val targetPhoto = editingPhotoUri!!
                EditImageScreen(
                    imagePath = targetPhoto,
                    onSave = { editingPhotoUri = null },
                    onCancel = { editingPhotoUri = null },
                    onSaveImage = { updatedUri ->
                        containersList = containersList.map { itm ->
                            val newPhotos = itm.photos.map { if (it == targetPhoto) updatedUri else it }
                            itm.copy(photos = newPhotos)
                        }
                        deliveryPhotosList = deliveryPhotosList.map { if (it == targetPhoto) updatedUri else it }
                        if (previewPhotoUri == targetPhoto) {
                            previewPhotoUri = updatedUri
                        }
                        editingPhotoUri = null
                        Toast.makeText(context, "Fotografie upravena a uložena", Toast.LENGTH_SHORT).show()
                    }
                )
            } else if (previewPhotoUri != null) {
                val currPhoto = previewPhotoUri!!
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(
                        model = resolveCoilImageModel(currPhoto),
                        contentDescription = "Náhled fotky kontejneru",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                previewPhotoUri = null
                                editingPhotoUri = currPhoto
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upravit (Ořez / Přiblížit / Otočit 90°)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { previewPhotoUri = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Kompaktní horní lišta
                    val dialogHeaderBg = if (isVraceni) Color(0xFF881337) else Color(0xFF1B5E8B)
                    val dialogAccentColor = if (isVraceni) Color(0xFF881337) else Color(0xFF0284C7)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dialogHeaderBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            isVraceni = !isVraceni
                            if (isVraceni && !customerSource.contains("VRÁCENÍ")) {
                                customerSource = if (customerSource.isBlank() || customerSource == "RUČNĚ") "VRÁCENÍ" else "VRÁCENÍ ($customerSource)"
                            }
                        },
                        color = if (isVraceni) Color(0xFFFEE2E2) else Color.White,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.5.dp, if (isVraceni) Color(0xFF881337) else Color(0xFF009FE3)),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isVraceni) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null, tint = Color(0xFF881337), modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "VRÁCENÍ",
                                    color = Color(0xFF881337),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            } else {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = dialogHeaderBg, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "EXPEDICE",
                                    color = dialogHeaderBg,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Text(
                        if (isVraceni) "Vrácení" else "Přeprava",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                    SingleLineDepoBadge(
                        depoText = depo,
                        badgeColor = if (depo == "Depo 2") Color(0xFFE65100) else Color(0xFF0D47A1),
                        onDepoSelected = { depo = it },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    StatusDropdownBadge(
                        currentStatus = status,
                        customColor = if (isVraceni) Color(0xFF881337) else null,
                        options = TRANSPORT_STATUS_OPTIONS,
                        onStatusSelected = { status = it },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = {
                            val currentRec = buildCurrentRecord()
                            val pdf = generateTransportCardPdf(context, currentRec)
                            printPdfFile(context, pdf, "Preprava_${currentRec.designation.ifBlank { "protokol" }}")
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.5.dp, Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Tisk protokolu", modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(3.dp))
                        Text("Protokol", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val dupErr = findContainerDuplicateError()
                            if (dupErr != null) {
                                Toast.makeText(context, dupErr, Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            onSave(buildCurrentRecord())
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.5.dp, if (isVraceni) Color(0xFF4C0519) else Color(0xFF059669)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVraceni) Color(0xFF881337) else Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("ULOŽIT", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ==================== 1. KROK: DATUM ====================
                    // Na jednom řádku datum, skutečné datum + den v týdnu, malá ikona pro změnu.
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isVraceni) Color(0xFFFFF1F2) else Color.White,
                                contentColor = Color(0xFF0F172A)
                            ),
                            border = BorderStroke(1.5.dp, dialogHeaderBg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDatePickerDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = dialogHeaderBg,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("1", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    "DATUM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = dialogHeaderBg,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                val dayOfWeek = remember(dateText) { getCzechDayOfWeek(dateText) }
                                val displayDate = dateText.ifBlank { "Zvolte datum" }
                                Text(
                                    text = if (dayOfWeek.isNotBlank()) "$displayDate • $dayOfWeek" else displayDate,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { showDatePickerDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = "Změnit datum",
                                        tint = dialogHeaderBg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ==================== 2. KROK: ZÁKAZNÍK ====================
                    // Pouze osoba, telefon (přesně stejné výšky) a pod tím lokalita kam se to veze
                    item {
                        val step2Color = if (isVraceni) Color(0xFF881337) else Color(0xFF15803D)
                        val step2Bg = if (isVraceni) Color(0xFFFFF1F2) else Color(0xFFF0FDF4)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = step2Bg,
                                contentColor = Color(0xFF0F172A)
                            ),
                            border = BorderStroke(1.5.dp, step2Color),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = step2Color,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("2", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                    Text(
                                        "ZÁKAZNÍK",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = step2Color,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(Modifier.weight(1f))
                                    StatusDropdownBadge(
                                        currentStatus = status.ifBlank { "NOVÁ" },
                                        customColor = if (isVraceni) Color(0xFF881337) else null,
                                        options = TRANSPORT_STATUS_OPTIONS,
                                        onStatusSelected = { status = it },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(29.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { showOrderPicker = true },
                                        modifier = Modifier.height(30.dp).weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, if (isVraceni) Color(0xFF4C0519) else Color(0xFF0A3780)),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isVraceni) Color(0xFF881337) else Color(0xFF0D47A1))
                                    ) {
                                        Text("Objednávka", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
                                    }
                                    Button(
                                        onClick = { showZakazkaPicker = true },
                                        modifier = Modifier.height(30.dp).weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, if (isVraceni) Color(0xFF4C0519) else Color(0xFF065F46)),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isVraceni) Color(0xFF7F1D1D) else Color(0xFF047857))
                                    ) {
                                        Text("Zakázka", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
                                    }
                                    Button(
                                        onClick = { showAdresarPicker = true },
                                        modifier = Modifier.height(30.dp).weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, if (isVraceni) Color(0xFF4C0519) else Color(0xFF0F3D5E)),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isVraceni) Color(0xFF9F1239) else Color(0xFF1B5E8B))
                                    ) {
                                        Text("Adresář", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            customerSource = if (isVraceni) "VRÁCENÍ (RUČNĚ)" else "RUČNĚ"
                                            sourceOrderId = ""
                                            sourceZakazkaId = ""
                                            cisloDokladuZdroj = ""
                                        },
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        border = BorderStroke(1.5.dp, step2Color),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isVraceni) Color(0xFFFFF1F2) else Color.White)
                                    ) {
                                        Text("Ručně", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = step2Color, maxLines = 1, softWrap = false)
                                    }
                                }

                                // 1. Zákazník (zúžené políčko, název uvnitř formuláře)
                                CompactSection2InputField(
                                    value = customerCompany,
                                    onValueChange = { customerCompany = it },
                                    placeholderText = "Zákazník",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 2. Pod zákazník: Převzetí (osoba) a vedle Mobil (číslo) - zúžené a zarovnané
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CompactSection2InputField(
                                        value = customerName,
                                        onValueChange = { customerName = it },
                                        placeholderText = "Převzetí",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactSection2InputField(
                                        value = customerPhone,
                                        onValueChange = { customerPhone = it },
                                        placeholderText = "Mobil",
                                        trailingEndPadding = if (customerPhone.isNotBlank()) 36.dp else 10.dp,
                                        modifier = Modifier.weight(1f),
                                        trailingContent = if (customerPhone.isNotBlank()) {
                                            {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .padding(end = 4.dp)
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFFDCFCE7))
                                                        .clickable {
                                                            try {
                                                                val clean = customerPhone.replace(Regex("[^0-9+]"), "").trim()
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                                    data = android.net.Uri.parse("tel:$clean")
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Nelze vytočit číslo", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Phone, contentDescription = "Volat zákazníkovi", tint = Color(0xFF15803D), modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        } else null
                                    )
                                }

                                // 3. Lokalita + ikona vyhledávat na mapách vedle / na pravém kraji políčka
                                CompactSection2InputField(
                                    value = destinationLocation,
                                    onValueChange = { destinationLocation = it },
                                    placeholderText = "Lokalita",
                                    trailingEndPadding = 38.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 4.dp)
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFE0F2FE))
                                                .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val query = destinationLocation.ifBlank { customerAddress }.trim()
                                                    if (query.isBlank()) {
                                                        Toast.makeText(context, "Zadejte lokalitu pro vyhledání na mapě", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        try {
                                                            val encoded = android.net.Uri.encode(query)
                                                            val mapIntent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse("geo:0,0?q=$encoded")
                                                            )
                                                            context.startActivity(mapIntent)
                                                        } catch (_: Exception) {
                                                            try {
                                                                val encoded = android.net.Uri.encode(query)
                                                                val webIntent = android.content.Intent(
                                                                    android.content.Intent.ACTION_VIEW,
                                                                    android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
                                                                )
                                                                context.startActivity(webIntent)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít mapy", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = "Vyhledávat na mapách",
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )

                                // 4. Poznámka (vždy na jeden řádek, zúžené políčko)
                                CompactSection2InputField(
                                    value = note,
                                    onValueChange = { note = it },
                                    placeholderText = "Poznámka",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 5. Interní poznámka (vždy na jeden řádek, zúžené políčko)
                                CompactSection2InputField(
                                    value = internalNote,
                                    onValueChange = { internalNote = it },
                                    placeholderText = "Interní poznámka",
                                    placeholderColor = Color(0xFF881337),
                                    borderColor = Color(0xFFFDA4AF),
                                    focusedBorderColor = Color(0xFF881337),
                                    backgroundColor = Color(0xFFFFF1F2).copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // ==================== 3. KROK: KONTEJNER ====================
                    // Kontejner zůstává, tlačítko načíst vedle textu Kontejner.
                    // Na jednom řádku: typ kontejneru, označení, vyfotit, galerie + malé plus vpravo pro přidání dalšího kontejneru.
                    // Až 3 fotografie na každý kontejner.
                    // Pod každým kontejnerem sekce vybavení se 4 řádky a načítáním z předem uložené databáze.
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isVraceni) Color(0xFFFFF1F2) else Color.White,
                                contentColor = Color(0xFF0F172A)
                            ),
                            border = BorderStroke(1.5.dp, dialogAccentColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = dialogAccentColor,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("3", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                    Text(
                                        "KONTEJNER",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = dialogAccentColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                     Button(
                                        onClick = {
                                            activeContainerTargetIndex = if (containersList.isNotEmpty() && containersList[0].designation.isBlank()) 0 else -1
                                            showContainerPicker = true
                                        },
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, if (isVraceni) Color(0xFF4C0519) else Color(0xFF0369A1)),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = dialogAccentColor,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "NAČÍST",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    // Malé plus po pravé straně pro přidání dalšího kontejneru z databáze
                                    IconButton(
                                        onClick = {
                                            activeContainerTargetIndex = -1
                                            showContainerPicker = true
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF0284C7),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = "Přidat kontejner z databáze", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                // Seznam kontejnerů
                                containersList.forEachIndexed { cIndex, cItem ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF0F9FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Řádek kontejneru: Pouze číslo (označení) kontejneru z databáze (typ SK/OB zrušen)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Označení (číslo) kontejneru - načteno z databáze kontejnerů
                                            Surface(
                                                onClick = {
                                                    activeContainerTargetIndex = cIndex
                                                    showContainerPicker = true
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.2.dp, Color(0xFF0284C7)),
                                                color = Color.White,
                                                modifier = Modifier.weight(1f).height(46.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (cItem.designation.isBlank()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Start
                                                        ) {
                                                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(
                                                                "Vybrat z databáze",
                                                                fontSize = 13.sp,
                                                                color = Color(0xFF0284C7),
                                                                fontWeight = FontWeight.Bold,
                                                                textAlign = TextAlign.Start,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    } else {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Start
                                                        ) {
                                                            Text(
                                                                text = cItem.designation,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFF0F172A),
                                                                textAlign = TextAlign.Start,
                                                                modifier = Modifier.weight(1f),
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                            Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Změnit",
                                                                tint = Color(0xFF0284C7).copy(alpha = 0.7f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (containersList.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        containersList = containersList.filterIndexed { i, _ -> i != cIndex }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Smazat kontejner", tint = Color(0xFF881337), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }

                                        // Fotografie kontejneru: možnost vložit až 3 fotografie
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            cItem.photos.take(3).forEachIndexed { pIndex, pUri ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.5.dp, dialogAccentColor, RoundedCornerShape(8.dp))
                                                        .clickable { previewPhotoUri = pUri }
                                                ) {
                                                    AsyncImage(
                                                        model = resolveCoilImageModel(pUri),
                                                        contentDescription = "Foto ${pIndex + 1}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Surface(
                                                        color = Color(0xCC0F172A),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${pIndex + 1}/3",
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        color = Color(0xFF881337),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(2.dp)
                                                            .clickable {
                                                                val filteredPhotos = cItem.photos.filterIndexed { pi, _ -> pi != pIndex }
                                                                containersList = containersList.mapIndexed { idx, itm ->
                                                                    if (idx == cIndex) itm.copy(photos = filteredPhotos) else itm
                                                                }
                                                            }
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Odebrat foto",
                                                            tint = Color.White,
                                                            modifier = Modifier.padding(2.dp).size(12.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        color = Color(0xDD0284C7),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(2.dp)
                                                            .clickable {
                                                                editingPhotoUri = pUri
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Upravit foto",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                            Spacer(Modifier.width(2.dp))
                                                            Text("Upravit", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                            if (cItem.photos.size < 3) {
                                                // Tlačítko fotoaparátu (vyfotit)
                                                Box(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .border(1.dp, dialogAccentColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                        .background(if (isVraceni) Color(0xFFFEF2F2) else Color(0xFFF0F9FF))
                                                        .clickable {
                                                            activePhotoContainerIndex = cIndex
                                                            try {
                                                                val newFile = File(context.filesDir, "container_cam_${System.currentTimeMillis()}_$cIndex.jpg")
                                                                pendingCameraPhotoFile = newFile
                                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)
                                                                containerCameraLauncher.launch(uri)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít fotoaparát", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(Icons.Default.PhotoCamera, contentDescription = "Vyfotit", tint = dialogAccentColor, modifier = Modifier.size(20.dp))
                                                        Spacer(Modifier.height(2.dp))
                                                        Text("Vyfotit", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = dialogAccentColor)
                                                    }
                                                }
                                                // Tlačítko galerie
                                                Box(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .border(1.dp, dialogAccentColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                        .background(Color.White)
                                                        .clickable {
                                                            activePhotoContainerIndex = cIndex
                                                            try {
                                                                containerMultiGalleryLauncher.launch("image/*")
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít galerii", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(Icons.Default.Image, contentDescription = "Galerie", tint = dialogAccentColor, modifier = Modifier.size(20.dp))
                                                        Spacer(Modifier.height(2.dp))
                                                        Text("Galerie", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = dialogAccentColor)
                                                    }
                                                }
                                            }
                                        }

                                        // Sekce vybavení kontejneru: zúžit, zarovnat, počet kusů, předdefinovaný přímotop na řádku 1
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                "Vybavení kontejneru",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isVraceni) Color(0xFF881337) else Color(0xFF0369A1)
                                            )
                                            val combinedOptions = remember(equipmentList) {
                                                val base = listOf("Přímotop", "Židle", "Stůl", "Skříňka", "Šatní", "Klimatizace")
                                                val all = base.toMutableList()
                                                equipmentList.forEach { eq ->
                                                    val tr = eq.trim()
                                                    if (tr.isNotBlank() && !all.any { it.equals(tr, ignoreCase = true) }) {
                                                        all.add(tr)
                                                    }
                                                }
                                                all
                                            }
                                            val eqItems = remember(cItem.equipment) {
                                                val list = cItem.equipment.toMutableList()
                                                while (list.size < 4) list.add("")
                                                list.take(4)
                                            }
                                            var eqCountStates by remember(cIndex, cItem.id) {
                                                val initialCounts = mutableMapOf<Int, String>()
                                                eqItems.forEachIndexed { i, s ->
                                                    val p = parseEquipmentLine(s)
                                                    initialCounts[i] = if (p.second.isNotBlank()) p.second else "0"
                                                }
                                                mutableStateOf(initialCounts.toMap())
                                            }
                                            eqItems.forEachIndexed { eqIdx, rawEqText ->
                                                val (parsedName, parsedCount) = remember(rawEqText, eqIdx) {
                                                    parseEquipmentLine(rawEqText)
                                                }
                                                val currentCount = eqCountStates[eqIdx] ?: (if (parsedCount.isNotBlank()) parsedCount else "0")
                                                var eqDropdownExpanded by remember { mutableStateOf(false) }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Dropdown výběr položky vybavení (výška 40.dp)
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        Surface(
                                                            onClick = { eqDropdownExpanded = true },
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                            color = if (parsedName.isNotBlank()) Color(0xFFF8FAFC) else Color.White,
                                                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = parsedName.ifBlank { "— Zvolte vybavení —" },
                                                                    fontSize = 12.sp,
                                                                    fontWeight = if (parsedName.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (parsedName.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                        DropdownMenu(
                                                            expanded = eqDropdownExpanded,
                                                            onDismissRequest = { eqDropdownExpanded = false },
                                                            modifier = Modifier.background(Color.White)
                                                        ) {
                                                            combinedOptions.forEach { preset ->
                                                                DropdownMenuItem(
                                                                    text = { Text(preset, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                                                    onClick = {
                                                                        val countToUse = currentCount.ifBlank { "0" }
                                                                        val mutableEq = eqItems.toMutableList()
                                                                        mutableEq[eqIdx] = formatEquipmentLine(preset, countToUse)
                                                                        containersList = containersList.mapIndexed { idx, itm ->
                                                                            if (idx == cIndex) itm.copy(equipment = mutableEq) else itm
                                                                        }
                                                                        eqDropdownExpanded = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Počet kusů: zarovnané na výšku 40.dp, implicitně hodnota 0
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                        color = Color.White,
                                                        modifier = Modifier.width(68.dp).height(40.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            BasicTextField(
                                                                value = currentCount,
                                                                onValueChange = { newVal ->
                                                                    val clean = newVal.filter { it.isDigit() }.take(3)
                                                                    eqCountStates = eqCountStates + (eqIdx to clean)
                                                                    val countToSave = clean.ifBlank { "0" }
                                                                    val mutableEq = eqItems.toMutableList()
                                                                    val nameToSave = if (eqIdx == 0 && parsedName.isBlank()) "Přímotop" else parsedName
                                                                    if (nameToSave.isNotBlank()) {
                                                                        mutableEq[eqIdx] = formatEquipmentLine(nameToSave, countToSave)
                                                                        containersList = containersList.mapIndexed { idx, itm ->
                                                                            if (idx == cIndex) itm.copy(equipment = mutableEq) else itm
                                                                        }
                                                                    }
                                                                },
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                textStyle = TextStyle(
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF0F172A),
                                                                    textAlign = TextAlign.Start
                                                                ),
                                                                modifier = Modifier.weight(1f),
                                                                decorationBox = { innerTextField ->
                                                                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(start = 4.dp)) {
                                                                        if (currentCount.isEmpty()) {
                                                                            Text("0", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                                        }
                                                                        innerTextField()
                                                                    }
                                                                }
                                                            )
                                                            Column(
                                                                modifier = Modifier.fillMaxHeight().width(18.dp),
                                                                verticalArrangement = Arrangement.Center,
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxWidth()
                                                                        .clickable {
                                                                            val cur = currentCount.toIntOrNull() ?: 0
                                                                            val nextStr = (cur + 1).toString()
                                                                            eqCountStates = eqCountStates + (eqIdx to nextStr)
                                                                            val mutableEq = eqItems.toMutableList()
                                                                            val nameToSave = if (eqIdx == 0 && parsedName.isBlank()) "Přímotop" else parsedName
                                                                            if (nameToSave.isNotBlank()) {
                                                                                mutableEq[eqIdx] = formatEquipmentLine(nameToSave, nextStr)
                                                                                containersList = containersList.mapIndexed { idx, itm ->
                                                                                    if (idx == cIndex) itm.copy(equipment = mutableEq) else itm
                                                                                }
                                                                            }
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Zvýšit", tint = Color(0xFF0284C7), modifier = Modifier.size(15.dp))
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxWidth()
                                                                        .clickable {
                                                                            val cur = currentCount.toIntOrNull() ?: 0
                                                                            val prevStr = (cur - 1).coerceAtLeast(0).toString()
                                                                            eqCountStates = eqCountStates + (eqIdx to prevStr)
                                                                            val mutableEq = eqItems.toMutableList()
                                                                            val nameToSave = if (eqIdx == 0 && parsedName.isBlank()) "Přímotop" else parsedName
                                                                            if (nameToSave.isNotBlank()) {
                                                                                mutableEq[eqIdx] = formatEquipmentLine(nameToSave, prevStr)
                                                                                containersList = containersList.mapIndexed { idx, itm ->
                                                                                    if (idx == cIndex) itm.copy(equipment = mutableEq) else itm
                                                                                }
                                                                            }
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Snížit", tint = Color(0xFF0284C7), modifier = Modifier.size(15.dp))
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Smazat/resetovat řádek
                                                    IconButton(
                                                        onClick = {
                                                            eqCountStates = eqCountStates + (eqIdx to "0")
                                                            val mutableEq = eqItems.toMutableList()
                                                            mutableEq[eqIdx] = if (eqIdx == 0) "Přímotop (0 ks)" else ""
                                                            containersList = containersList.mapIndexed { idx, itm ->
                                                                if (idx == cIndex) itm.copy(equipment = mutableEq) else itm
                                                            }
                                                        },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Vymazat",
                                                            tint = if (parsedName.isNotBlank() && eqIdx != 0) Color(0xFF881337) else Color(0xFF94A3B8),
                                                            modifier = Modifier.size(18.dp)
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


                    // ==================== ULOŽIT A SMAZAT (SEKCE 4 PŘEPRAVCE ODSTRANĚNA) ====================
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val saveInteractionSource = remember { MutableInteractionSource() }
                            val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                            val saveBrush = if (isVraceni) {
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFE11D48), Color(0xFF9F1239), Color(0xFF881337))
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF4ADE80), Color(0xFF16A34A), Color(0xFF14532D))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .graphicsLayer {
                                        translationY = if (isSavePressed) 3.dp.toPx() else 0f
                                        scaleX = if (isSavePressed) 0.985f else 1f
                                        scaleY = if (isSavePressed) 0.985f else 1f
                                    }
                                    .shadow(
                                        elevation = if (isSavePressed) 1.dp else 3.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = if (isVraceni) Color(0xFF4C0519) else Color(0xFF064E3B),
                                        spotColor = if (isVraceni) Color(0xFF4C0519) else Color(0xFF064E3B)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(saveBrush)
                                    .border(
                                        width = 2.dp,
                                        color = if (isVraceni) Color(0xFF4C0519) else Color(0xFF064E3B),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        interactionSource = saveInteractionSource,
                                        indication = null
                                    ) {
                                        val dupErr = findContainerDuplicateError()
                                        if (dupErr != null) {
                                            Toast.makeText(context, dupErr, Toast.LENGTH_LONG).show()
                                            return@clickable
                                        }
                                        val updated = buildCurrentRecord()
                                        onSave(updated)
                                        Toast.makeText(context, "Přeprava uložena", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Horní skleněný odlesk (3D glass shine)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = if (isSavePressed) 0.15f else 0.4f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ULOŽIT",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            if (transport.id.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { onDelete(transport) },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, Color(0xFF881337)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFFFF1F2),
                                        contentColor = Color(0xFF881337)
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFF881337))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Smazat", fontWeight = FontWeight.Bold, color = Color(0xFF881337), maxLines = 1, softWrap = false)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun TransportContainerPickerDialog(
    containers: List<ContainerCard>,
    onDismiss: () -> Unit,
    onSelectContainer: (ContainerCard) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(containers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val base = containers.sortedWith(
            compareByDescending<ContainerCard> { it.effectiveSystemNumber() }
                .thenByDescending { it.timestamp }
        )
        if (q.isEmpty()) base
        else base.filter {
            it.designation.lowercase().contains(q) ||
                it.type.lowercase().contains(q) ||
                it.systemNumber.toString().contains(q) ||
                it.depo.lowercase().contains(q) ||
                it.status.lowercase().contains(q) ||
                it.vlastnik.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = Color(0xFF0F172A),
        textContentColor = Color(0xFF0F172A),
        title = { Text("Kontejnery", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 1, softWrap = false) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Hledat", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF334155)) },
                    colors = transportTextFieldColors(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Žádný kontejner nenalezen.", color = Color(0xFF334155), maxLines = 1, softWrap = false)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.id }) { cont ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectContainer(cont) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF0F9FF),
                                    contentColor = Color(0xFF0C4A6E)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val photo = primaryContainerPhoto(cont)
                                    if (!photo.isNullOrBlank()) {
                                        AsyncImage(
                                            model = resolveCoilImageModel(photo),
                                            contentDescription = cont.designation,
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            color = Color(0xFF0284C7),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White)
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                cont.designation.ifBlank { "Kontejner #${cont.systemNumber}" },
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF0C4A6E),
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = if (cont.depo == "Depo 2") Color(0xFFD97706) else Color(0xFF0284C7),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    cont.depo.ifBlank { "Depo 1" },
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            "Typ: ${cont.type.ifBlank { "Standard" }} • Stav: ${cont.status.ifBlank { "DEPO" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF0369A1),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (cont.vlastnik.isNotBlank()) {
                                            Text(
                                                "Vlastník: ${cont.vlastnik}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF475569),
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, Color(0xFF0284C7))
            ) {
                Text("Zavřít", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun TransportOrderPickerDialog(
    customers: List<CustomerRecord>,
    isVraceni: Boolean = false,
    onDismiss: () -> Unit,
    onSelectOrder: (CustomerRecord) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(customers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val base = customers.sortedByDescending { it.timestamp }
        if (q.isEmpty()) base
        else base.filter {
            it.company.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.ic.lowercase().contains(q) ||
                it.phone.lowercase().contains(q) ||
                it.ulice.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A),
        textContentColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isVraceni) {
                    Surface(
                        color = Color(0xFF881337),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("VRÁCENÍ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text("Objednávky", fontWeight = FontWeight.Bold, color = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A), maxLines = 1, softWrap = false)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Hledat", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF334155)) },
                    colors = transportTextFieldColors(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Žádná objednávka nenalezena.", color = Color(0xFF334155), maxLines = 1, softWrap = false)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.id }) { cust ->
                            val ini = computeCustomerInitials(cust.name, cust.company)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectOrder(cust) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF8FAFC),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            cust.company.ifBlank { cust.name },
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (ini.isNotBlank()) {
                                            Surface(
                                                color = Color(0xFF0D47A1),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Iniciály: $ini",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (cust.name.isNotBlank() && cust.name != cust.company) {
                                        Text("Osoba: ${cust.name}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E293B), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                    val sub = listOf(
                                        if (cust.ic.isNotBlank()) "IČ: ${cust.ic}" else "",
                                        cust.phone,
                                        cust.ulice
                                    ).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, Color(0xFF0D47A1))
            ) {
                Text("Zavřít", color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun TransportZakazkaPickerDialog(
    zakazky: List<ZakazkaRecord>,
    isVraceni: Boolean = false,
    onDismiss: () -> Unit,
    onSelectZakazka: (ZakazkaRecord) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(zakazky, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val base = zakazky.sortedByDescending { it.timestamp }
        if (q.isEmpty()) base
        else base.filter {
            it.cisloZakazky.lowercase().contains(q) ||
                it.firma.lowercase().contains(q) ||
                it.vyrizuje.lowercase().contains(q) ||
                it.ic.lowercase().contains(q) ||
                it.lokalita.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A),
        textContentColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isVraceni) {
                    Surface(
                        color = Color(0xFF881337),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("VRÁCENÍ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text("Zakázky", fontWeight = FontWeight.Bold, color = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A), maxLines = 1, softWrap = false)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Hledat", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF334155)) },
                    colors = transportTextFieldColors(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Žádná zakázka nenalezena.", color = Color(0xFF334155), maxLines = 1, softWrap = false)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.id }) { zak ->
                            val ini = computeCustomerInitials(zak.vyrizuje, zak.firma)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectZakazka(zak) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF0FDF4),
                                    contentColor = Color(0xFF064E3B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${zak.cisloZakazky} • ${zak.firma.ifBlank { zak.vyrizuje }}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF064E3B),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (ini.isNotBlank()) {
                                            Surface(
                                                color = Color(0xFF047857),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Iniciály: $ini",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (zak.vyrizuje.isNotBlank()) {
                                        Text("Vyřizuje: ${zak.vyrizuje}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF065F46), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                    val sub = listOf(
                                        if (zak.ic.isNotBlank()) "IČ: ${zak.ic}" else "",
                                        zak.lokalita.ifBlank { zak.ulice },
                                        zak.telefon
                                    ).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, Color(0xFF047857))
            ) {
                Text("Zavřít", color = Color(0xFF047857), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun TransportAdresarPickerDialog(
    adresar: List<CustomerRecord>,
    isVraceni: Boolean = false,
    onDismiss: () -> Unit,
    onSelectAdresar: (CustomerRecord) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(adresar, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val base = adresar.sortedBy { it.company.ifBlank { it.name } }
        if (q.isEmpty()) base
        else base.filter {
            it.company.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.ic.lowercase().contains(q) ||
                it.phone.lowercase().contains(q) ||
                it.ulice.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A),
        textContentColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isVraceni) {
                    Surface(
                        color = Color(0xFF881337),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("VRÁCENÍ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text("Adresář firem a kontaktů", fontWeight = FontWeight.Bold, color = if (isVraceni) Color(0xFF881337) else Color(0xFF0F172A), maxLines = 1, softWrap = false)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Hledat firmu, jméno, IČ...", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF334155)) },
                    colors = transportTextFieldColors(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Žádný kontakt nenalezen.", color = Color(0xFF334155), maxLines = 1, softWrap = false)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.id }) { cust ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAdresar(cust) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF8FAFC),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        cust.company.ifBlank { cust.name },
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (cust.name.isNotBlank() && cust.name != cust.company) {
                                        Text("Osoba: ${cust.name}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1E293B), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                    val sub = listOf(
                                        if (cust.ic.isNotBlank()) "IČ: ${cust.ic}" else "",
                                        cust.phone,
                                        cust.ulice
                                    ).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, Color(0xFF0284C7))
            ) {
                Text("Zavřít", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}
