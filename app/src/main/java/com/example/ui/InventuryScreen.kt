package com.example.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.InventoryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val InvNavy = Color(0xFF0F2942)
private val InvBlue = Color(0xFF0284C7)
private val InvGreen = Color(0xFF059669)
private val InvAmber = Color(0xFFD97706)
private val InvBorder = Color(0xFFCBD5E1)
private val InvBg = Color(0xFFF1F5F9)

data class ParsedInventorySession(
    val dateText: String,
    val summaryNote: String,
    val items: List<QuickInventoryItem>
)

fun parseInventorySession(record: InventoryRecord): ParsedInventorySession {
    val fallbackDate = record.dateText.ifBlank {
        SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val raw = record.sessionDataJson.trim()
    if (raw.isNotBlank()) {
        try {
            if (raw.startsWith("{")) {
                val obj = JSONObject(raw)
                val dt = obj.optString("dateText", fallbackDate).ifBlank { fallbackDate }
                val note = obj.optString("summaryNote", record.summaryNote)
                val arr = obj.optJSONArray("items") ?: JSONArray()
                val items = parseInventoryItemsJsonArray(arr)
                return ParsedInventorySession(dt, note, items)
            } else if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                val items = parseInventoryItemsJsonArray(arr)
                return ParsedInventorySession(fallbackDate, record.summaryNote, items)
            }
        } catch (_: Exception) {}
    }
    val fallbackItems = record.containerDesignations
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { des ->
            QuickInventoryItem(
                designation = des,
                type = "Lodní 20' DV",
                color = "",
                description = "Kontejner $des evidovaný v inventuře ($fallbackDate)."
            )
        }
    return ParsedInventorySession(fallbackDate, record.summaryNote, fallbackItems)
}

private fun parseInventoryItemsJsonArray(arr: JSONArray): List<QuickInventoryItem> {
    val list = mutableListOf<QuickInventoryItem>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
            QuickInventoryItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                designation = o.optString("designation", ""),
                type = o.optString("type", "Lodní 20' DV"),
                color = o.optString("color", ""),
                description = o.optString("description", ""),
                spz = o.optString("spz", ""),
                imageUri = o.optString("imageUri", "")
            )
        )
    }
    return list
}

fun buildInventorySessionJson(
    dateText: String,
    summaryNote: String,
    items: List<QuickInventoryItem>
): String {
    val arr = JSONArray()
    items.forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("designation", item.designation)
                put("type", item.type)
                put("color", item.color)
                put("description", item.description)
                put("spz", item.spz)
                put("imageUri", item.imageUri)
            }
        )
    }
    return JSONObject().apply {
        put("dateText", dateText)
        put("summaryNote", summaryNote)
        put("items", arr)
    }.toString()
}

fun buildConciseInventorySummaryText(
    dateText: String,
    items: List<QuickInventoryItem>
): String {
    if (items.isEmpty()) {
        return "K datu $dateText zatím nejsou nahrány žádné fotografie kontejnerů."
    }
    val count = items.size
    val designations = items.mapIndexed { idx, it ->
        normalizeDesignationSingleLine(it.designation).ifBlank { "XXX ${String.format("%02d", idx + 1)}" }
    }
    val sb = StringBuilder()
    sb.append("Dne $dateText byla provedena inventura celkem $count ks kontejnerů. ")
    sb.append("Soupis označení kontejnerů: ${designations.joinToString(", ")}.")
    return sb.toString()
}

/**
 * Formats container designation onto a single line (e.g. "MEDU 311432-9") for PDF export and PC sync.
 */
fun normalizeDesignationSingleLine(raw: String): String {
    val oneLine = raw.replace(Regex("""\s+"""), " ").trim()
    return com.example.api.formatContainerDesignationWithHyphen(oneLine)
}

/**
 * Formats container designation onto two lines for the mobile container card:
 * Line 1: Prefix (e.g. "MEDU")
 * Line 2: Number with check digit (e.g. "311432-9")
 */
fun formatDesignationTwoLinesForCard(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.contains("\n")) {
        val lines = trimmed.lines().take(2)
        if (lines.size == 2) {
            val l1 = lines[0].trim().uppercase()
            val l2Raw = lines[1].trim()
            val sevenDigits = Regex("""^(\d{6})[\s_./]*(\d)$""").find(l2Raw)
            val l2 = if (sevenDigits != null) {
                "${sevenDigits.groupValues[1]}-${sevenDigits.groupValues[2]}"
            } else {
                l2Raw
            }
            return "$l1\n$l2"
        }
    }
    val formattedSingle = normalizeDesignationSingleLine(trimmed)
    val spaceIdx = formattedSingle.indexOf(' ')
    return if (spaceIdx > 0 && spaceIdx < formattedSingle.length - 1) {
        val prefix = formattedSingle.substring(0, spaceIdx).trim()
        val numberPart = formattedSingle.substring(spaceIdx + 1).trim()
        "$prefix\n$numberPart"
    } else {
        formattedSingle
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventuryScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateBack: () -> Unit = onOpenDrawer,
    onNavigateToRychlaInventura: () -> Unit = {}
) {
    val inventories by viewModel.inventories.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentDateTimeFormatted = remember {
        SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    // Active inventory workspace state
    var activeRecordId by remember { mutableStateOf<String?>(null) }
    var dateText by remember { mutableStateOf(currentDateTimeFormatted) }
    var authorText by remember { mutableStateOf(userName.ifBlank { "Správce depa" }) }
    var summaryNote by remember { mutableStateOf("") }
    val activeItems = remember { mutableStateListOf<QuickInventoryItem>() }
    var isWorkspaceExpanded by remember { mutableStateOf(true) }

    // Two-stage state:
    // false = Stage 1 (Photos loaded into cards, user reviews & edits designations/descriptions before processing)
    // true  = Stage 2 (Inventory processed with recorded Date & Time, Handover Protocol ready, and synced with photos to PC)
    var isInventoryProcessed by remember { mutableStateOf(false) }
    var isSyncingToPc by remember { mutableStateOf(false) }
    var lastSyncedRecord by remember { mutableStateOf<InventoryRecord?>(null) }

    // Processing state for batch photo analysis
    var isAnalyzingBatch by remember { mutableStateOf(false) }
    var analyzedProgress by remember { mutableStateOf(0) }
    var totalToAnalyze by remember { mutableStateOf(0) }
    var currentAnalyzingLabel by remember { mutableStateOf("") }

    // Dialog states
    var zoomedImageUri by remember { mutableStateOf<String?>(null) }
    var editingPhotoItem by remember { mutableStateOf<QuickInventoryItem?>(null) }
    var recordToDelete by remember { mutableStateOf<InventoryRecord?>(null) }

    // Load a saved inventory record into the active workspace
    fun loadInventoryIntoWorkspace(record: InventoryRecord) {
        val parsed = parseInventorySession(record)
        activeRecordId = record.id
        dateText = parsed.dateText
        authorText = record.author.ifBlank { userName.ifBlank { "Správce depa" } }
        summaryNote = parsed.summaryNote
        activeItems.clear()
        activeItems.addAll(parsed.items)
        isInventoryProcessed = true
        lastSyncedRecord = record
        isWorkspaceExpanded = true
    }

    // Start a fresh inventory workspace
    fun startNewInventoryWorkspace() {
        val freshNow = SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault()).format(Date())
        activeRecordId = null
        dateText = freshNow
        authorText = userName.ifBlank { "Správce depa" }
        summaryNote = ""
        activeItems.clear()
        isInventoryProcessed = false
        lastSyncedRecord = null
        isWorkspaceExpanded = true
    }

    // Encode all local photos into portable Base64 data URLs and synchronize the processed inventory to Firestore / PC app
    suspend fun finalizeAndSyncInventoryToPc(recordNewTimestamp: Boolean, showToast: Boolean = true): InventoryRecord {
        isSyncingToPc = true
        return try {
            val nowMillis = System.currentTimeMillis()
            val formattedNow = SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault()).format(Date(nowMillis))
            if (recordNewTimestamp || dateText.isBlank()) {
                dateText = formattedNow
            } else if (!dateText.contains(":")) {
                val timePart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMillis))
                dateText = "${dateText.trim()} $timePart"
            }
            val cleanDate = dateText.trim().ifBlank { formattedNow }

            // Convert all local photo URIs into compressed Base64 data URLs so PC app displays every container photo
            val syncedItems = withContext(Dispatchers.IO) {
                activeItems.mapIndexed { idx, item ->
                    val portableUri = encodeInventoryPhotoToBase64(context, item.imageUri)
                    val cleanDes = normalizeDesignationSingleLine(item.designation)
                        .ifBlank { "XXX ${String.format("%02d", idx + 1)}" }
                    val cleanDesc = item.description.trim().ifBlank {
                        buildFallbackPhotoDescription(
                            designation = cleanDes,
                            type = item.type,
                            color = item.color,
                            spz = item.spz
                        )
                    }
                    item.copy(
                        designation = cleanDes,
                        description = cleanDesc,
                        imageUri = portableUri
                    )
                }
            }

            activeItems.clear()
            activeItems.addAll(syncedItems)

            val finalId = activeRecordId ?: "inv_$nowMillis"
            val designations = syncedItems.joinToString(", ") { it.designation }
            val autoSummary = buildConciseInventorySummaryText(cleanDate, syncedItems)
            summaryNote = autoSummary

            val sessionJson = buildInventorySessionJson(
                dateText = cleanDate,
                summaryNote = autoSummary,
                items = syncedItems
            )
            val existingStatus = inventories.firstOrNull { it.id == finalId }?.status?.ifBlank { "Nové" } ?: "Nové"
            val record = InventoryRecord(
                id = finalId,
                timestamp = nowMillis,
                dateText = cleanDate,
                author = authorText.trim().ifBlank { "Správce depa" },
                containerDesignations = designations,
                depo = currentDepo ?: "Depo 1",
                status = existingStatus,
                summaryNote = autoSummary,
                sessionDataJson = sessionJson
            )

            // Mark processed and display the completed inventory ("novinka") first, then sync to cloud/PC
            isInventoryProcessed = true
            lastSyncedRecord = record

            if (activeRecordId == null) {
                viewModel.saveNewInventory(record)
                activeRecordId = finalId
            } else {
                viewModel.updateInventory(record)
            }

            if (showToast) {
                Toast.makeText(
                    context,
                    "Inventura ($cleanDate) zpracována a synchronizována včetně fotek do PC (${syncedItems.size} ks)",
                    Toast.LENGTH_LONG
                ).show()
            }
            record
        } finally {
            isSyncingToPc = false
        }
    }

    // Load uploaded photos into individual container cards WITHOUT syncing to PC yet,
    // allowing the user to inspect and edit designations & descriptions on the cards first.
    fun processUploadedPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        coroutineScope.launch {
            isAnalyzingBatch = true
            totalToAnalyze = uris.size
            analyzedProgress = 0
            isWorkspaceExpanded = true
            // Enter staging/review mode until the user confirms and processes the inventory
            isInventoryProcessed = false

            val startingIndex = activeItems.size
            for (idx in uris.indices) {
                val uri = uris[idx]
                val seqNum = startingIndex + idx + 1
                val fileHint = extractCodeFromUriName(context, uri).ifBlank { String.format("%02d", seqNum) }
                analyzedProgress = idx + 1
                currentAnalyzingLabel = "Načítám fotografii ${idx + 1} z ${uris.size} do karty kontejneru..."

                val newItem = withContext(Dispatchers.IO) {
                    try {
                        val savedFile = copyAndNormalizeInventoryPhoto(context, uri)
                        val bitmap = if (savedFile != null && savedFile.exists()) {
                            BitmapFactory.decodeFile(savedFile.absolutePath)
                        } else {
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }
                        if (bitmap != null) {
                            val aiInfo = viewModel.analyzeContainerBitmap(bitmap, fileHint)
                            val finalDesignation = com.example.api.normalizeContainerDesignation(aiInfo.designation, fileHint)
                            val finalType = aiInfo.type.trim().takeIf { it.isNotBlank() } ?: "Lodní 20' DV"
                            val finalColor = com.example.api.normalizeContainerColorAdjective(aiInfo.color)
                            val finalSpz = aiInfo.spz?.trim().orEmpty()
                            val rawDesc = aiInfo.description.trim()
                            val finalDesc = com.example.api.formatContainerShortDescription(
                                color = finalColor,
                                type = finalType,
                                rawDescription = rawDesc
                            )
                            QuickInventoryItem(
                                designation = finalDesignation,
                                type = finalType,
                                color = finalColor,
                                description = finalDesc,
                                spz = finalSpz,
                                imageUri = savedFile?.let { Uri.fromFile(it).toString() } ?: uri.toString()
                            )
                        } else {
                            val finalDesignation = com.example.api.normalizeContainerDesignation("", fileHint)
                            QuickInventoryItem(
                                designation = finalDesignation,
                                type = "Lodní 20' DV",
                                color = "Modrý",
                                description = "modrý\n20 DV",
                                imageUri = uri.toString()
                            )
                        }
                    } catch (e: Exception) {
                        val finalDesignation = com.example.api.normalizeContainerDesignation("", fileHint)
                        QuickInventoryItem(
                            designation = finalDesignation,
                            type = "Lodní 20' DV",
                            color = "Modrý",
                            description = "modrý\n20 DV",
                            imageUri = uri.toString()
                        )
                    }
                }
                activeItems.add(newItem)
            }

            isAnalyzingBatch = false
            currentAnalyzingLabel = ""
            Toast.makeText(
                context,
                "Načteno ${uris.size} fotografií do karet. Zkontrolujte příp. opravte označení a klikněte na ZPRACOVAT INVENTURU.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val multipleDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            processUploadedPhotos(uris)
        }
    }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            processUploadedPhotos(uris)
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            processUploadedPhotos(listOf(captured))
        }
    }

    fun launchCameraCapture() {
        try {
            val file = File(context.filesDir, "inv_cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Nelze spustit fotoaparát: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                dateText = String.format("%02d. %02d. %04d %s", dayOfMonth, month + 1, year, timeStr)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    BackHandler {
        onNavigateBack()
    }

    // Fullscreen zoomed photo dialog with crop & zoom action
    if (zoomedImageUri != null) {
        val currentZoomUri = zoomedImageUri!!
        val matchingItem = activeItems.firstOrNull { it.imageUri == currentZoomUri }
        Dialog(
            onDismissRequest = { zoomedImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = resolveCoilImageModel(currentZoomUri) ?: Uri.parse(currentZoomUri),
                    contentDescription = "Detail fotografie kontejneru",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { zoomedImageUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
                }

                if (matchingItem != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingPhotoItem = matchingItem
                                zoomedImageUri = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009FE3)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Oříznout", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Full photo editor screen (Zoom, Scale, Rotate, Crop)
    if (editingPhotoItem != null) {
        val targetItem = editingPhotoItem!!
        Dialog(
            onDismissRequest = { editingPhotoItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditImageScreen(
                imagePath = targetItem.imageUri,
                onSave = { editingPhotoItem = null },
                onCancel = { editingPhotoItem = null },
                onSaveImage = { updatedDataUri ->
                    val idx = activeItems.indexOfFirst { it.id == targetItem.id || it.imageUri == targetItem.imageUri }
                    if (idx >= 0) {
                        activeItems[idx] = activeItems[idx].copy(imageUri = updatedDataUri)
                    }
                    editingPhotoItem = null
                }
            )
        }
    }

    // Delete inventory confirmation dialog
    if (recordToDelete != null) {
        val target = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Smazat inventuru?", fontWeight = FontWeight.Bold) },
            text = {
                val dt = target.dateText.ifBlank {
                    SimpleDateFormat("dd. MM. yyyy HH:mm", Locale.getDefault()).format(Date(target.timestamp))
                }
                Text("Opravdu chcete trvale odstranit inventuru ze dne $dt?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.deleteInventory(target)
                            if (activeRecordId == target.id) {
                                startNewInventoryWorkspace()
                            }
                            recordToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Smazat", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Zrušit")
                }
            }
        )
    }

    Scaffold(
        containerColor = InvBg,
        topBar = {
            Surface(
                color = InvNavy,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zpět",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "INVENTURA",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Button(
                        onClick = { startNewInventoryWorkspace() },
                        colors = ButtonDefaults.buttonColors(containerColor = InvGreen),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("new_inventory_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nová", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // HLAVIČKA INVENTURY: DATUM, KDO ZAPSAL A TLAČÍTKA NAHRÁT SADU / VYFOTOGRAFOVAT
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, InvBorder, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = dateText,
                                onValueChange = { dateText = it },
                                label = { Text("Datum", fontSize = 11.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { openDatePicker() }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = "Vybrat datum",
                                            tint = InvBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InvNavy),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("inventory_date_input")
                            )

                            OutlinedTextField(
                                value = authorText,
                                onValueChange = { authorText = it },
                                label = { Text("Zapsal", fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InvNavy),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("inventory_author_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        multiplePhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } catch (_: Exception) {
                                        multipleDocumentsLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                enabled = !isAnalyzingBatch && !isSyncingToPc,
                                colors = ButtonDefaults.buttonColors(containerColor = InvBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp)
                                    .testTag("upload_inventory_photos_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NAHRÁT", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { launchCameraCapture() },
                                enabled = !isAnalyzingBatch && !isSyncingToPc,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("capture_inventory_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("VYFOTOGRAFOVAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        if (isAnalyzingBatch) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = InvBlue
                                    )
                                    Text(
                                        "Načítání ($analyzedProgress / $totalToAnalyze)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = InvNavy
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = {
                                        if (totalToAnalyze > 0) analyzedProgress.toFloat() / totalToAnalyze.toFloat() else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = InvBlue
                                )
                            }
                        }
                    }
                }
            }

            if (activeItems.isNotEmpty()) {
                if (isInventoryProcessed) {
                    // PROCESSED INVENTORY ("NOVINKA") + PŘEDÁVACÍ PROTOKOL (E-MAIL, WHATSAPP, PDF)
                    item {
                        val autoSummary = buildConciseInventorySummaryText(dateText, activeItems)
                        val allNames = activeItems.mapIndexed { i, it ->
                            normalizeDesignationSingleLine(it.designation).ifBlank { "XXX ${String.format("%02d", i + 1)}" }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, InvGreen, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                            color = InvGreen,
                                            shape = CircleShape
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Inventura kontejnerů $dateText",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                color = Color(0xFF065F46)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = InvGreen,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${activeItems.size}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val rec = lastSyncedRecord ?: finalizeAndSyncInventoryToPc(
                                                    recordNewTimestamp = false,
                                                    showToast = false
                                                )
                                                val pdfFile = generateInventoryProtocolPdfFile(context, rec, activeItems.toList())
                                                if (pdfFile != null) {
                                                    val waSummary = "📋 *PŘEDÁVACÍ PROTOKOL INVENTURY KONTEJNERŮ*\n" +
                                                        "📅 Datum a čas: ${rec.dateText}\n" +
                                                        "🏢 Depo: ${rec.depo} | 👤 Provedl: ${rec.author}\n" +
                                                        "📦 Počet kontejnerů: ${activeItems.size} ks\n" +
                                                        "🔢 Označení: ${allNames.joinToString(", ")}"
                                                    sharePdfViaWhatsApp(context, pdfFile, waSummary)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("share_inventory_whatsapp_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WHATSAPP", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val rec = lastSyncedRecord ?: finalizeAndSyncInventoryToPc(
                                                    recordNewTimestamp = false,
                                                    showToast = false
                                                )
                                                val pdfFile = generateInventoryProtocolPdfFile(context, rec, activeItems.toList())
                                                if (pdfFile != null) {
                                                    val subject = "Předávací protokol inventury kontejnerů (${rec.depo}) - ${rec.dateText}"
                                                    val bodyIntro = "zasíláme Vám předávací protokol inventury kontejnerů ze dne ${rec.dateText} (${rec.depo}, celkem ${activeItems.size} ks kontejnerů).\n\nSoupis kontejnerů:\n" +
                                                        activeItems.mapIndexed { idx, item ->
                                                            "${idx + 1}. ${item.designation.ifBlank { "XXX" }} — ${item.description}"
                                                        }.joinToString("\n")
                                                    sharePdfViaEmail(
                                                        context = context,
                                                        pdfFile = pdfFile,
                                                        subject = subject,
                                                        bodyIntro = bodyIntro
                                                    )
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = InvBlue),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("share_inventory_email_button")
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("E-MAIL", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val rec = lastSyncedRecord ?: finalizeAndSyncInventoryToPc(
                                                    recordNewTimestamp = false,
                                                    showToast = false
                                                )
                                                exportInventoryToPdf(context, rec, activeItems.toList())
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(42.dp)
                                            .testTag("export_inventory_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                finalizeAndSyncInventoryToPc(
                                                    recordNewTimestamp = false,
                                                    showToast = true
                                                )
                                            }
                                        },
                                        enabled = !isSyncingToPc,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("save_inventory_button")
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "ULOŽIT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val target = lastSyncedRecord ?: inventories.firstOrNull { it.id == activeRecordId }
                                            if (target != null) {
                                                recordToDelete = target
                                            } else {
                                                startNewInventoryWorkspace()
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("delete_opened_inventory_button")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Smazat inventuru",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "SMAZAT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. KARTY KONTEJNERŮ (VŽDY POUZE FOTOGRAFIE, OZNAČENÍ KONTEJNERU A JEHO POPIS — PŘÍMÁ EDITACE V KARTĚ)
                itemsIndexed(activeItems, key = { _, item -> item.id }) { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, InvBorder, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 1. FOTOGRAFIE KONTEJNERU (o 25 % větší: 140.dp x 140.dp, zacentrováno vpravo nahoře na označení)
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .border(1.dp, InvBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (item.imageUri.isNotBlank()) {
                                            zoomedImageUri = item.imageUri
                                        }
                                    }
                            ) {
                                if (item.imageUri.isNotBlank()) {
                                    AsyncImage(
                                        model = resolveCoilImageModel(item.imageUri) ?: Uri.parse(item.imageUri),
                                        contentDescription = "Fotografie kontejneru ${item.designation}",
                                        modifier = Modifier.fillMaxSize(),
                                        alignment = Alignment.TopEnd,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }

                            // 2. OZNAČENÍ KONTEJNERU (na 2 řádky: např. MEDU / 311432-9) & 3. POPIS (na 2 řádky) ZAROVNANÉ NA VÝŠKU FOTOGRAFIE (140.dp)
                            val designationDisplay = remember(item.designation) {
                                formatDesignationTwoLinesForCard(item.designation)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(140.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(1.dp, InvBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (designationDisplay.isEmpty()) {
                                        Text(
                                            text = "MEDU\n311432-9",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            lineHeight = 17.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    BasicTextField(
                                        value = designationDisplay,
                                        onValueChange = { newVal ->
                                            val twoLineFormatted = formatDesignationTwoLinesForCard(newVal)
                                            activeItems[index] = item.copy(designation = twoLineFormatted)
                                        },
                                        maxLines = 2,
                                        cursorBrush = SolidColor(InvNavy),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            lineHeight = 17.sp,
                                            color = InvNavy
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("inventory_card_designation_$index")
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, InvBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (item.description.isEmpty()) {
                                            Text(
                                                text = "šedý\nobytný",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        BasicTextField(
                                            value = item.description,
                                            onValueChange = { newDesc ->
                                                val twoLines = newDesc.lines().take(2).joinToString("\n")
                                                activeItems[index] = item.copy(description = twoLines)
                                            },
                                            maxLines = 2,
                                            cursorBrush = SolidColor(InvNavy),
                                            textStyle = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp,
                                                color = Color(0xFF1E293B)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("inventory_card_description_$index")
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            activeItems.removeAt(index)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Smazat kartu kontejneru",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TLAČÍTKO ULOŽIT INVENTURU POD POSLEDNÍ KARTOU KONTEJNERU
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                for (i in activeItems.indices) {
                                    val formatted = normalizeDesignationSingleLine(activeItems[i].designation)
                                    if (formatted != activeItems[i].designation) {
                                        activeItems[i] = activeItems[i].copy(designation = formatted)
                                    }
                                }
                                finalizeAndSyncInventoryToPc(
                                    recordNewTimestamp = true,
                                    showToast = true
                                )
                            }
                        },
                        enabled = !isAnalyzingBatch && !isSyncingToPc && activeItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = InvGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_inventory_button")
                    ) {
                        if (isSyncingToPc) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "UKLÁDÁM...",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "ULOŽIT",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 4. ULOŽENÉ A ZPRACOVANÉ INVENTURY PODLE DATA A ČASU
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "INVENTURY (${inventories.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = InvNavy
                )
            }

            if (inventories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, InvBorder, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Žádné záznamy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = InvNavy
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(inventories, key = { _, inv -> inv.id }) { _, inv ->
                    val parsed = remember(inv) { parseInventorySession(inv) }
                    val isSelected = activeRecordId == inv.id
                    val count = parsed.items.size
                    val authorDisplay = inv.author.trim().ifBlank { "Správce depa" }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) InvBlue else InvBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { loadInventoryIntoWorkspace(inv) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFF0F9FF) else Color.White
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 1. řádek: Datum a čas, vedle tlačítko Otevřít, vedle počet kontejnerů (pouze číslo)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = parsed.dateText,
                                    color = InvNavy,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = { loadInventoryIntoWorkspace(inv) },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "Otevřít",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }

                                Surface(
                                    color = InvGreen.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        color = InvGreen,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // 2. řádek pod tím: stručný popis kdo inventuru provedl
                            Text(
                                text = "Provedl: $authorDisplay",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
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

private fun buildFallbackPhotoDescription(
    designation: String,
    type: String,
    color: String,
    spz: String
): String {
    return com.example.api.formatContainerShortDescription(color, type, null)
}

private fun extractCodeFromUriName(context: Context, uri: Uri): String {
    return try {
        val pathSeg = uri.lastPathSegment ?: return ""
        val isoRegex = Regex("""\b([A-Z]{3}[UJZ])\s*[-:]?\s*(\d{6})\s*[-/]?\s*(\d)?\b""", RegexOption.IGNORE_CASE)
        isoRegex.find(pathSeg)?.let { m ->
            val p1 = m.groupValues[1].uppercase()
            val p2 = m.groupValues[2]
            val p3 = m.groupValues[3]
            return if (p3.isNotEmpty()) "$p1 $p2-$p3" else "$p1 $p2"
        }
        val obSkRegex = Regex("""\b(OB|SK|KONT)\s*[-_]?\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE)
        obSkRegex.find(pathSeg)?.let { m ->
            return "${m.groupValues[1].uppercase()} ${m.groupValues[2]}"
        }
        ""
    } catch (_: Exception) {
        ""
    }
}

private fun copyAndNormalizeInventoryPhoto(context: Context, uri: Uri): File? {
    return try {
        val tempRaw = File(context.cacheDir, "inv_raw_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempRaw).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        val exif = ExifInterface(tempRaw.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempRaw.absolutePath, opts)
        var sampleSize = 1
        while (opts.outWidth / sampleSize > 1600 || opts.outHeight / sampleSize > 1600) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(tempRaw.absolutePath, decodeOpts) ?: return null
        val upright = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        val finalFile = File(context.filesDir, "inv_photo_${UUID.randomUUID()}.jpg")
        FileOutputStream(finalFile).use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, 86, out)
        }
        tempRaw.delete()
        finalFile
    } catch (_: Exception) {
        null
    }
}

/**
 * Encodes a local `file://` or `content://` container photo into a compressed `data:image/jpeg;base64,...`
 * string so that when the completed inventory is synchronized to Firestore, all container photos
 * render directly in the PC web application under the Inventura agenda.
 */
private fun encodeInventoryPhotoToBase64(context: Context, imageUri: String): String {
    val trimmed = imageUri.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("data:image")) return trimmed
    return try {
        val bitmap: Bitmap? = when {
            trimmed.startsWith("file://") || trimmed.startsWith("/") -> {
                val path = trimmed.removePrefix("file://")
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            }
            trimmed.startsWith("content://") -> {
                context.contentResolver.openInputStream(Uri.parse(trimmed))?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
            else -> null
        }
        if (bitmap != null) {
            val maxDim = 760
            val ratio = minOf(
                maxDim.toFloat() / bitmap.width.coerceAtLeast(1),
                maxDim.toFloat() / bitmap.height.coerceAtLeast(1),
                1f
            )
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } else {
            trimmed
        }
    } catch (_: Exception) {
        trimmed
    }
}

/**
 * Generates an Inventory Handover Protocol PDF ("Inventární soupis") matching the official
 * "Kontejnery pod Žofinkou" A4 landscape graphic format with dark petrol banner (#153D5A),
 * warm orange accent bar (#EA9B2B), steel blue table header (#2E86C1) and structured columns.
 */
fun generateInventoryProtocolPdfFile(
    context: Context,
    record: InventoryRecord,
    items: List<QuickInventoryItem>
): File? {
    return try {
        val pdfDocument = PdfDocument()
        val pageWidth = 842 // A4 Landscape
        val pageHeight = 595
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val headerBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(21, 61, 90) // #153D5A
            isAntiAlias = true
        }
        val accentBarPaint = Paint().apply {
            color = android.graphics.Color.rgb(234, 155, 43) // #EA9B2B
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            textSize = 21f
            isFakeBoldText = true
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            textSize = 11.5f
            color = android.graphics.Color.rgb(224, 242, 254) // #E0F2FE
            isAntiAlias = true
        }
        val tableHeaderPaint = Paint().apply {
            color = android.graphics.Color.rgb(46, 134, 193) // #2E86C1
            isAntiAlias = true
        }
        val tableHeaderTitlePaint = Paint().apply {
            textSize = 10.5f
            isFakeBoldText = true
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        val bodyTextPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.rgb(44, 62, 80) // #2C3E50
            isAntiAlias = true
        }
        val boldBodyPaint = Paint().apply {
            textSize = 10.5f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(15, 23, 42) // #0F172A
            isAntiAlias = true
        }
        val soldPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(192, 57, 43) // #C0392B
            isAntiAlias = true
        }
        val rentPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(29, 78, 216) // #1D4ED8
            isAntiAlias = true
        }
        val stockPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(4, 120, 87) // #047857
            isAntiAlias = true
        }
        val rowDividerPaint = Paint().apply {
            color = android.graphics.Color.rgb(235, 239, 240) // #EBEFF0
            strokeWidth = 1f
        }
        val footerDividerPaint = Paint().apply {
            color = android.graphics.Color.rgb(189, 195, 199) // #BDC3C7
            strokeWidth = 1f
        }
        val footerTextPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.rgb(100, 116, 139) // #64748B
            isAntiAlias = true
        }

        fun drawHeaderAndTableHeader(c: android.graphics.Canvas, isFirstPage: Boolean) {
            // 1. Top banner (Navy background with rounded corners and orange left stripe)
            val bannerRect = RectF(38f, 28f, 804f, 94f)
            c.drawRoundRect(bannerRect, 6f, 6f, headerBgPaint)

            val accentRect = RectF(38f, 28f, 48f, 94f)
            c.drawRect(accentRect, accentBarPaint)

            c.drawText("Kontejnery pod Žofinkou", 64f, 58f, titlePaint)
            val sub = if (isFirstPage) {
                "Inventární soupis - Verze 6 (bez ISO kódu)"
            } else {
                "Inventární soupis (pokračování) - ${record.dateText}"
            }
            c.drawText(sub, 64f, 80f, subtitlePaint)

            // 2. Table Header bar (#2E86C1)
            val thRect = RectF(38f, 112f, 804f, 138f)
            c.drawRect(thRect, tableHeaderPaint)

            val headerY = 129f
            c.drawText("Pořadí", 46f, headerY, tableHeaderTitlePaint)
            c.drawText("Typ", 100f, headerY, tableHeaderTitlePaint)
            c.drawText("Číslo kontejneru", 170f, headerY, tableHeaderTitlePaint)
            c.drawText("Vlastník", 340f, headerY, tableHeaderTitlePaint)
            c.drawText("Stav", 446f, headerY, tableHeaderTitlePaint)
            c.drawText("Popis", 536f, headerY, tableHeaderTitlePaint)
        }

        drawHeaderAndTableHeader(canvas, isFirstPage = true)
        var y = 142f
        val rowHeight = 26f

        fun determineCleanType(type: String, desc: String): String {
            val combined = "$type $desc".uppercase()
            return when {
                combined.contains("40") && combined.contains("HC") -> "40 HC"
                combined.contains("40") -> "40 DV"
                combined.contains("20") && combined.contains("TV") -> "20 TV"
                combined.contains("20") && combined.contains("OB") -> "20 OB"
                combined.contains("20") && combined.contains("SK") -> "20 SK"
                combined.contains("20") -> "20 DV"
                else -> type.trim().ifBlank { "40 HC" }
            }
        }

        fun determineStatus(desc: String): String {
            val d = desc.lowercase()
            return when {
                d.contains("prodan") || d.contains("prodán") -> "Prodaný"
                d.contains("pronáj") || d.contains("pronaj") -> "Pronájem"
                d.contains("rezerv") -> "Rezervace"
                else -> "" // In the template rows 3-6 Stav was empty for stock containers
            }
        }

        items.forEachIndexed { idx, item ->
            if (y > 480f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawHeaderAndTableHeader(canvas, isFirstPage = false)
                y = 142f
            }

            // Bottom border line for row
            canvas.drawLine(38f, y + rowHeight - 2f, 804f, y + rowHeight - 2f, rowDividerPaint)

            val textY = y + 17f
            val seq = "${idx + 1}"
            val cleanType = determineCleanType(item.type, item.description)
            val desig = normalizeDesignationSingleLine(item.designation).ifBlank { "XXX ${String.format("%02d", idx + 1)}" }
            val owner = "Kont. Ostr."
            val status = determineStatus(item.description)
            val desc = item.description.trim().ifBlank { "Lodní kontejner evidovaný v inventuře." }

            canvas.drawText(seq, 46f, textY, bodyTextPaint)
            canvas.drawText(cleanType, 100f, textY, bodyTextPaint)
            canvas.drawText(desig, 170f, textY, boldBodyPaint)
            canvas.drawText(owner, 340f, textY, bodyTextPaint)

            if (status.isNotBlank()) {
                val p = when (status) {
                    "Prodaný" -> soldPaint
                    "Pronájem" -> rentPaint
                    else -> stockPaint
                }
                canvas.drawText(status, 446f, textY, p)
            }

            val maxDescWidth = 260f
            var clippedDesc = desc
            while (clippedDesc.length > 4 && bodyTextPaint.measureText(clippedDesc) > maxDescWidth) {
                clippedDesc = clippedDesc.dropLast(2) + "…"
            }
            canvas.drawText(clippedDesc, 536f, textY, bodyTextPaint)

            y += rowHeight
        }

        // Draw Footer on the final page
        val footerLineY = 512f
        canvas.drawLine(38f, footerLineY, 804f, footerLineY, footerDividerPaint)

        val rawDate = record.dateText.trim()
        val dateOnly = if (rawDate.contains(" ")) rawDate.split(" ").first() else rawDate.ifBlank {
            SimpleDateFormat("dd. MM. yyyy", Locale.getDefault()).format(Date(record.timestamp))
        }
        canvas.drawText("Datum kontroly: $dateOnly", 38f, 536f, footerTextPaint)
        canvas.drawText("Podpis kontrola: ____________________", 38f, 558f, footerTextPaint)

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "Predavaci_protokol_inventury_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        file
    } catch (e: Exception) {
        Toast.makeText(context, "Chyba při generování PDF protokolu: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

private fun exportInventoryToPdf(
    context: Context,
    record: InventoryRecord,
    items: List<QuickInventoryItem>
) {
    try {
        val file = generateInventoryProtocolPdfFile(context, record, items) ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Sdílet předávací protokol inventury (PDF)"))
    } catch (e: Exception) {
        Toast.makeText(context, "Chyba při exportu PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
