package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ContainerCard
import java.io.InputStream
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

fun formatPrice(price: Long): String {
    val formatter = NumberFormat.getInstance(Locale.forLanguageTag("cs-CZ"))
    return "${formatter.format(price)} Kč"
}

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

fun formatDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToCamera: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val containers by viewModel.containers.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val isDepo2 = currentDepo == "Depo 2"
    val depoThemeColor = if (isDepo2) Color(0xFF5D5342) else Color(0xFF1B5E8B)
    val depoWorkspaceBg = if (isDepo2) Color(0xFFF7F0E3) else Color(0xFFE6EFF7)
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pendingScannedResult by viewModel.pendingScannedResult.collectAsState()
    val duplicationWarning by viewModel.duplicationWarning.collectAsState()
    var editingImageUri by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf("CARDS") } // "CARDS" (s FOTO - výchozí) nebo "LIST" (databázové zobrazení)
    var deleteConfirmCard by remember { mutableStateOf<ContainerCard?>(null) }
    var showManualCreateDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    if (editingImageUri != null) {
        val targetUri = editingImageUri!!
        EditImageScreen(
            imagePath = targetUri,
            onSave = { editingImageUri = null },
            onCancel = { editingImageUri = null },
            onSaveImage = { updatedDataUri ->
                val matched = containers.find { c ->
                    primaryContainerPhoto(c) == targetUri ||
                        sanitizeImageUri(c.imageUri) == targetUri ||
                        parseExtraImageUris(c.extraImages).contains(targetUri)
                }
                if (matched != null) {
                    val oldExtras = parseExtraImageUris(matched.extraImages)
                    val newExtras = if (oldExtras.contains(targetUri)) {
                        oldExtras.map { if (it == targetUri) updatedDataUri else it }
                    } else {
                        listOf(updatedDataUri) + oldExtras
                    }
                    val newMain = if (sanitizeImageUri(matched.imageUri) == targetUri || matched.imageUri.isNullOrBlank()) {
                        updatedDataUri
                    } else {
                        matched.imageUri
                    }
                    viewModel.saveContainer(
                        matched.copy(
                            imageUri = newMain,
                            extraImages = newExtras.distinct().joinToString("|")
                        )
                    )
                }
            }
        )
        return
    }
    val contentResolver = context.contentResolver

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream: InputStream? = contentResolver.openInputStream(it)
            stream?.use { s ->
                val file = java.io.File(context.filesDir, "gallery_import_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    s.copyTo(out)
                }
                val photoTime = extractPhotoTimestamp(context, it, file)
                viewModel.analyzeAndSave(file, photoTime)
            }
        }
    }

    if (deleteConfirmCard != null) {
        val target = deleteConfirmCard!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Kontejner ${target.designation} (${target.status})",
            itemDescription = "Typ: ${target.type} • Depo: ${target.depo}",
            onDismiss = { deleteConfirmCard = null },
            onConfirmedDelete = {
                viewModel.deleteCard(target)
                deleteConfirmCard = null
            }
        )
    }

    if (showManualCreateDialog) {
        var newDesig by remember { mutableStateOf("") }
        var newType by remember { mutableStateOf(if (isDepo2) "20 DV" else "OB") }
        var newStatus by remember { mutableStateOf(if (isDepo2) "DEPO 02" else "DEPO 01") }
        var newOwner by remember { mutableStateOf("") }
        var newColor by remember { mutableStateOf("") }
        var newSpz by remember { mutableStateOf("") }
        var newPrice by remember { mutableStateOf("65000") }
        var newNote by remember { mutableStateOf("") }
        var newPhotoPath by remember { mutableStateOf<String?>(null) }
        var newPhotoTimestamp by remember { mutableStateOf<Long?>(null) }
        var isDialogAnalyzing by remember { mutableStateOf(false) }
        var aiStatusMessage by remember { mutableStateOf<String?>(null) }
        var editingDraftPhoto by remember { mutableStateOf(false) }
        val duplicateCard = remember(newDesig, containers) {
            viewModel.findDuplicateContainer(newDesig)
        }

        val dialogTempCameraFile = remember { File(context.filesDir, "new_container_cam_temp.jpg") }

        fun handleDraftPhotoAnalysis(file: File, timestampOverride: Long? = null) {
            isDialogAnalyzing = true
            aiStatusMessage = null
            viewModel.analyzePhotoForDraftContainer(
                photoFile = file,
                photoTimestampOverride = timestampOverride,
                onResult = { res, savedPath, photoTimeMs ->
                    isDialogAnalyzing = false
                    newPhotoPath = savedPath
                    newPhotoTimestamp = photoTimeMs
                    if (res.designation.isNotBlank()) newDesig = res.designation
                    if (res.type.isNotBlank()) {
                        newType = when {
                            res.type.contains("Obyt", ignoreCase = true) || res.type.equals("OB", ignoreCase = true) -> "OB"
                            res.type.contains("Sklad", ignoreCase = true) || res.type.equals("SK", ignoreCase = true) -> "SK"
                            else -> res.type
                        }
                    }
                    if (res.color.isNotBlank()) newColor = res.color
                    if (res.description.isNotBlank()) newNote = res.description
                    if (!res.spz.isNullOrBlank()) newSpz = res.spz
                    val dup = if (res.designation.isNotBlank()) viewModel.findDuplicateContainer(res.designation) else null
                    aiStatusMessage = if (dup != null) {
                        "Duplicita: Kontejner ${dup.designation} již v agendě existuje!"
                    } else if (res.designation.isNotBlank()) {
                        "Kód z fotky rozpoznán: ${res.designation} (${res.color.ifBlank { res.type }})"
                    } else {
                        "Kód kontejneru nebyl z fotky jednoznačně přečten. Doplňte prosím označení ručně."
                    }
                },
                onError = { err ->
                    isDialogAnalyzing = false
                    aiStatusMessage = err
                }
            )
        }

        val dialogCameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                val photoTime = extractPhotoTimestamp(context, null, dialogTempCameraFile)
                handleDraftPhotoAnalysis(dialogTempCameraFile, photoTime)
            }
        }

        val dialogGalleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val tempFile = File(context.filesDir, "new_container_gal_temp_${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(it)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val photoTime = extractPhotoTimestamp(context, it, tempFile)
                    handleDraftPhotoAnalysis(tempFile, photoTime)
                } catch (e: Exception) {
                    aiStatusMessage = "Chyba při načtení z galerie: ${e.localizedMessage}"
                }
            }
        }

        if (editingDraftPhoto && newPhotoPath != null) {
            Dialog(
                onDismissRequest = { editingDraftPhoto = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                EditImageScreen(
                    imagePath = newPhotoPath!!,
                    onSave = { editingDraftPhoto = false },
                    onCancel = { editingDraftPhoto = false },
                    onSaveImage = { updatedDataUri ->
                        newPhotoPath = updatedDataUri
                    }
                )
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isDialogAnalyzing) showManualCreateDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Kontejner",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    dialogTempCameraFile
                                )
                                dialogCameraLauncher.launch(uri)
                            },
                            enabled = !isDialogAnalyzing,
                            shape = RoundedCornerShape(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.size(width = 46.dp, height = 38.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Vyfotit kontejner (AI)", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        FilledIconButton(
                            onClick = { dialogGalleryLauncher.launch("image/*") },
                            enabled = !isDialogAnalyzing,
                            shape = RoundedCornerShape(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0369A1)),
                            modifier = Modifier.size(width = 46.dp, height = 38.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Načíst z galerie (AI)", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDialogAnalyzing) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Načítání...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1)
                            )
                        }
                    } else if (!aiStatusMessage.isNullOrBlank()) {
                        val isSuccess = aiStatusMessage!!.startsWith("Kód z fotky rozpoznán")
                        Text(
                            text = aiStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }

                    if (!newPhotoPath.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                .clickable { editingDraftPhoto = true }
                        ) {
                            AsyncImage(
                                model = resolveCoilImageModel(newPhotoPath),
                                contentDescription = "Náhled fotky kontejneru",
                                contentScale = ContentScale.Crop,
                                alignment = containerPreviewAlignment(newPhotoPath),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newDesig,
                        onValueChange = {
                            newDesig = it
                            if (it.uppercase().startsWith("OB")) newType = "OB"
                            else if (it.uppercase().startsWith("SK")) newType = "SK"
                        },
                        label = {
                            Text(
                                "Označení",
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        placeholder = {
                            Text("např. HLXU 123456-7 nebo OB 105", fontSize = 12.sp)
                        },
                        isError = duplicateCard != null || (newPhotoPath != null && newDesig.isBlank()),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (duplicateCard != null) {
                        Text(
                            text = "Duplicita: Kontejner ${duplicateCard.designation} již v agendě existuje!",
                            color = Color(0xFFDC2626),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OB", "SK", "20 DV", "40 HC").forEach { t ->
                            val isSelected = newType.equals(t, ignoreCase = true) ||
                                (t == "OB" && newType.contains("Obyt", ignoreCase = true)) ||
                                (t == "SK" && newType.contains("Sklad", ignoreCase = true))
                            FilterChip(
                                selected = isSelected,
                                onClick = { newType = t },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        t,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(if (isDepo2) "DEPO 02" else "DEPO 01", "PRONÁJEM", "PRODEJ", "OPRAVA").forEach { st ->
                            FilterChip(
                                selected = newStatus == st,
                                onClick = { newStatus = st },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        st,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newColor,
                            onValueChange = { newColor = it },
                            label = { Text("Barva", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newSpz,
                            onValueChange = { newSpz = it },
                            label = { Text("SPZ", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newOwner,
                            onValueChange = { newOwner = it },
                            label = { Text("Vlastník", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newPrice,
                            onValueChange = { newPrice = it },
                            label = { Text("Cena", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        label = { Text("Poznámka", maxLines = 1, softWrap = false) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDesig.isBlank()) {
                            Toast.makeText(
                                context,
                                "Zadejte prosím označení kontejneru",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (duplicateCard != null) {
                            Toast.makeText(
                                context,
                                "Duplicita: Kontejner ${duplicateCard.designation} již v agendě existuje!",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        val created = viewModel.createManualContainer(
                            designation = newDesig.trim(),
                            type = newType,
                            status = newStatus,
                            depo = currentDepo ?: "Depo 1",
                            vlastnik = newOwner,
                            price = newPrice.toLongOrNull() ?: 0L,
                            poznamka = newNote,
                            description = newNote,
                            color = newColor,
                            spz = newSpz,
                            imageUri = newPhotoPath,
                            extraImages = newPhotoPath ?: "",
                            photoTimestamp = newPhotoTimestamp
                        )
                        showManualCreateDialog = false
                        onNavigateToDetail(created.id)
                    },
                    enabled = !isDialogAnalyzing && duplicateCard == null && newDesig.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = depoThemeColor)
                ) {
                    Text("Vytvořit", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualCreateDialog = false },
                    enabled = !isDialogAnalyzing,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (duplicationWarning && pendingScannedResult != null) {
        val dupDesig = pendingScannedResult?.designation ?: ""
        val existingDup = remember(dupDesig, containers) { viewModel.findDuplicateContainer(dupDesig) }
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingResult() },
            title = { Text("Duplicita označení", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Kontejner s označením $dupDesig již v agendě existuje. V agendě nesmí být načtený kontejner 2× pod stejným číslem."
                )
            },
            confirmButton = {
                if (existingDup != null) {
                    Button(
                        onClick = {
                            val targetId = existingDup.id
                            viewModel.clearPendingResult()
                            onNavigateToDetail(targetId)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Otevřít kartu", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearPendingResult() },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zavřít")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = depoWorkspaceBg,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.padding(bottom = 10.dp).size(width = 52.dp, height = 42.dp),
                    shape = RoundedCornerShape(10.dp),
                    containerColor = depoThemeColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Z galerie")
                }
                FloatingActionButton(
                    onClick = onNavigateToCamera,
                    modifier = Modifier.size(width = 52.dp, height = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    containerColor = depoThemeColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Vyfotit")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(depoWorkspaceBg)
                .padding(padding)
        ) {
            val isAnalyzingSearch by viewModel.isAnalyzingSearch.collectAsState()
            val tempSearchFile = remember { File(context.filesDir, "search_temp.jpg") }
            val searchCameraLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    viewModel.analyzeForSearch(tempSearchFile)
                }
            }

            var localSearchQuery by remember { mutableStateOf(searchQuery) }
            var isSearchVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
            
            // Sync when external things updates query (like deep link or OCR)
            LaunchedEffect(searchQuery) {
                localSearchQuery = searchQuery
                if (searchQuery.isNotEmpty()) {
                    isSearchVisible = true
                }
            }

            val currentDepo by viewModel.currentDepo.collectAsState()
            val initialDepoSection = if (currentDepo == "Depo 2") "DEPO 02" else "DEPO 01"
            var selectedSection by remember(currentDepo) { mutableStateOf(initialDepoSection) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.padding(start = 10.dp).size(18.dp)
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = localSearchQuery,
                        onValueChange = {
                            localSearchQuery = it
                            viewModel.updateSearchQuery(it)
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        singleLine = true,
                        enabled = !isAnalyzingSearch,
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (localSearchQuery.isEmpty()) {
                                    Text(
                                        text = if (isAnalyzingSearch) "Analyzuji..." else "Vyhledat kontejner...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF64748B),
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                localSearchQuery = ""
                                viewModel.updateSearchQuery("")
                            },
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Vymazat vyhledávání",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", tempSearchFile)
                            searchCameraLauncher.launch(uri)
                        },
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    ) {
                        if (isAnalyzingSearch) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Vyfotit pro hledání", modifier = Modifier.size(18.dp), tint = depoThemeColor)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { viewMode = if (viewMode == "CARDS") "LIST" else "CARDS" },
                    modifier = Modifier.height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (viewMode == "CARDS") Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "Přepnout zobrazení",
                        modifier = Modifier.size(18.dp),
                        tint = depoThemeColor
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { showManualCreateDialog = true },
                    modifier = Modifier.height(42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = depoThemeColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nový kontejner", modifier = Modifier.size(18.dp))
                }
            }

            if (currentDepo == "Depo 1" || currentDepo == "Depo 2") {
                val depoName = if (currentDepo == "Depo 1") "DEPO 01" else "DEPO 02"
                val depoTabLabel = if (currentDepo == "Depo 2") "Depo 2" else "Depo 1"
                val isDepo2Tab = currentDepo == "Depo 2"
                ScrollableTabRow(
                    selectedTabIndex = when (selectedSection) {
                        depoName -> 0
                        "PRONÁJEM" -> 1
                        "OPRAVA" -> 2
                        "PRODEJ" -> 3
                        "ARCHIV" -> 4
                        "VŠE" -> 5
                        else -> 0
                    },
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = selectedSection == depoName,
                        onClick = { selectedSection = depoName },
                        text = {
                            Surface(
                                color = if (isDepo2Tab) Color(0xFFE65100) else Color(0xFF0D47A1),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = depoTabLabel,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedSection == "PRONÁJEM",
                        onClick = { selectedSection = "PRONÁJEM" },
                        text = { Text("PRONÁJEM", fontWeight = FontWeight.Bold, color = if (selectedSection == "PRONÁJEM") Color(0xFF2196F3) else Color(0xFF1E293B)) }
                    )
                    Tab(
                        selected = selectedSection == "OPRAVA",
                        onClick = { selectedSection = "OPRAVA" },
                        text = { Text("OPRAVA", fontWeight = FontWeight.Bold, color = if (selectedSection == "OPRAVA") Color(0xFFF44336) else Color(0xFF1E293B)) }
                    )
                    Tab(
                        selected = selectedSection == "PRODEJ",
                        onClick = { selectedSection = "PRODEJ" },
                        text = { Text("PRODEJ", fontWeight = FontWeight.Bold, color = if (selectedSection == "PRODEJ") Color(0xFF0284C7) else Color(0xFF1E293B)) }
                    )
                    Tab(
                        selected = selectedSection == "ARCHIV",
                        onClick = { selectedSection = "ARCHIV" },
                        text = { Text("ARCHIV", fontWeight = FontWeight.Bold, color = if (selectedSection == "ARCHIV") Color(0xFF757575) else Color(0xFF1E293B)) }
                    )
                    Tab(
                        selected = selectedSection == "VŠE",
                        onClick = { selectedSection = "VŠE" },
                        text = { Text("VŠE", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) }
                    )
                }
            }

            val filteredContainers = remember(containers, selectedSection) {
                val base = if (selectedSection != "VŠE") {
                    containers.filter {
                        when (selectedSection) {
                            "DEPO 01" -> it.status == "DEPO 01" || it.status == "DEPO 1" || it.status == "SKLADEM" || it.status.isBlank()
                            "DEPO 02" -> it.status == "DEPO 02" || it.status == "DEPO 2" || it.status == "SKLADEM" || it.status.isBlank()
                            "PRONÁJEM" -> it.status == "PRONÁJEM" || it.status == "PRONAJATO" || it.status == "PRONAJ." || it.status == "PRONÁJ" || it.status == "REZ" || it.status == "REZERV." || it.status == "REZERVACE"
                            "OPRAVA" -> it.status == "OPRAVA" || it.status == "OPRAVY"
                            "PRODEJ" -> it.status == "PRODEJ" || it.status == "PRODÁNO"
                            else -> it.status == selectedSection
                        }
                    }
                } else {
                    containers.filter { it.status != "ARCHIV" }
                }
                base.sortedWith(
                    compareByDescending<ContainerCard> { it.effectiveSystemNumber() }
                        .thenByDescending { it.timestamp }
                )
            }

            if (filteredContainers.isEmpty() && !isAnalyzing && analysisError == null) {
                Column(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (containers.isEmpty()) "Zatím nemáte žádné kontejnerové karty." else "V této sekci nejsou žádné kontejnery.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(if (viewMode == "LIST") 6.dp else 12.dp)
                ) {
                    if (isAnalyzing) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Analyzuji kontejner pomocí AI...")
                                }
                            }
                        }
                    }

                    if (analysisError != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(
                                    text = analysisError!!,
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (viewMode == "LIST") {
                        item {
                            Surface(
                                color = Color(0xFFD0DCE5),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("OZNAČENÍ (TYP)", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E293B), modifier = Modifier.weight(1.5f))
                                    Text("STAV", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E293B), modifier = Modifier.weight(1.0f).padding(horizontal = 4.dp))
                                    Text("CENA", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E293B), modifier = Modifier.weight(0.8f).padding(horizontal = 2.dp))
                                    Text("AKCE", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E293B), modifier = Modifier.padding(end = 4.dp))
                                }
                            }
                        }
                    }

                    items(filteredContainers, key = { it.id }) { container ->
                        if (viewMode == "LIST") {
                            DatabaseContainerRowItem(
                                card = container,
                                onEditClick = { onNavigateToDetail(container.id) },
                                onDeleteClick = { deleteConfirmCard = container }
                            )
                        } else {
                            ShortContainerCardItem(
                                card = container,
                                onStatusChange = { viewModel.updateStatus(container, it) },
                                onInfoClick = { onNavigateToDetail(container.id) },
                                onEditImage = { uri -> editingImageUri = uri },
                                onDeleteClick = { deleteConfirmCard = container }
                            )
                        }
                    }
                }
        }
    }
}

}

@Composable
fun DepoShortIconBadge(
    isDepo2: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isDepo2) Color(0xFFE65100) else Color(0xFF0D47A1),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = if (isDepo2) "D2" else "D1",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun DatabaseContainerRowItem(
    card: ContainerCard,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isCardDepo2 = card.depo == "Depo 2" || card.status == "DEPO 02"
    val borderCol = if (isCardDepo2) Color(0xFFB48A4E) else Color(0xFF8FB3CE)
    val bgCol = if (isCardDepo2) Color(0xFFFFFBF4) else Color(0xFFFFFFFF)
    val sanitizedDesignation = card.designation.replace("\n", " ").replace("\r", "").trim().ifBlank { "Bez označení" }
    val mappedType = when (card.type) {
        "45G1" -> "40 HC"
        "22G1" -> "20 DV"
        "42G1" -> "40 DV"
        else -> card.type.ifBlank { "20 DV" }
    }

    Card(
        onClick = onEditClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, borderCol),
        colors = CardDefaults.cardColors(containerColor = bgCol, contentColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Označení a typ v 1 řádku (ve zkráceném náhledu se interní číslo ani označení depa nezobrazují)
            Row(
                modifier = Modifier.weight(1.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$sanitizedDesignation ($mappedType)",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Stav v 1 řádku
            Text(
                text = card.status,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1B5E8B),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.0f).padding(horizontal = 4.dp)
            )

            // Cena v 1 řádku
            Text(
                text = if (card.price > 0) formatPrice(card.price) else "-",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1E293B),
                maxLines = 1,
                modifier = Modifier.weight(0.8f).padding(horizontal = 2.dp)
            )

            // Akce
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Upravit kartu", tint = Color(0xFF1B5E8B), modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Smazat kartu", tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ShortContainerCardItem(
    card: ContainerCard,
    onStatusChange: (String) -> Unit,
    onInfoClick: () -> Unit,
    onEditImage: (String) -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    var showImageZoom by remember { mutableStateOf(false) }

    val isCardDepo2 = card.depo == "Depo 2" || card.status == "DEPO 02"
    val cardBgColor = when (card.status) {
        "REZERV." -> Color(0xFFFFFBEB)
        "PLATBA" -> Color(0xFFF0F9FF)
        "PRONÁJ", "PRONÁJEM" -> Color(0xFFF0FDF4)
        else -> if (isCardDepo2) Color(0xFFFCF5E8) else Color(0xFFEAF3FC)
    }
    val cardBorderColor = if (isCardDepo2) Color(0xFF8C6239) else Color(0xFF1B5E8B)
    val headerBgColor = if (isCardDepo2) Color(0xFF5D5342) else Color(0xFF1B5E8B)
    val sanitizedDesignation = card.designation.replace("\n", " ").replace("\r", "").trim().ifBlank { "KONTEJNER" }

    // Typ kontejneru: OB (obytný) nebo SK (skladový)
    val isObytny = card.type.contains("obyt", ignoreCase = true) ||
        card.description.contains("obyt", ignoreCase = true) ||
        card.type.startsWith("OB", ignoreCase = true) ||
        card.type.contains("kancelář", ignoreCase = true) ||
        card.type.contains("buňka", ignoreCase = true)
    val shortTypeCode = if (isObytny) "OB" else "SK"

    val statusBg = when (card.status) {
        "DEPO 01" -> Color(0xFF0284C7)
        "PRONÁJEM", "PRONAJATO", "PRONÁJ" -> Color(0xFF15803D)
        "OPRAVA" -> Color(0xFFB91C1C)
        "ARCHIV" -> Color(0xFF475569)
        "DEPO 02" -> Color(0xFFD97706)
        "PRODEJ" -> Color(0xFF0369A1)
        else -> Color(0xFF0284C7)
    }
    val containerStatusOptions = listOf("DEPO 01", "DEPO 02", "PRONÁJEM", "PRODEJ", "OPRAVA", "REZERV.", "ARCHIV")

    Card(
        onClick = onInfoClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
            contentColor = Color(0xFF0F172A)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Horní barevný pruh karty Kontejneru (OB / SK ikona + označení kontejneru + status DEPO 1)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Místo "K" je typ kontejneru OB nebo SK
                    AgendaLetterIconBadge(
                        letter = shortTypeCode,
                        textColor = headerBgColor
                    )
                    // Z náhledu zkrácené karty je systémové číslo vymazáno, zobrazuje se označení
                    Text(
                        text = sanitizedDesignation,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (card.depo == "Depo 1" && card.revisionDate != null) {
                        val days30InMs = 30L * 24 * 60 * 60 * 1000
                        val now = System.currentTimeMillis()
                        if (card.revisionDate - now < days30InMs) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 18.dp)
                                    .background(Color(0xFFDC2626), shape = RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "R",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                // Na stejném řádku je ikona depa vymazána, zůstává pouze status v rámečku
                StatusDropdownBadge(
                    currentStatus = card.status.ifBlank { if (isCardDepo2) "DEPO 02" else "DEPO 01" },
                    options = containerStatusOptions,
                    customColor = statusBg,
                    onStatusSelected = { newSt -> onStatusChange(newSt) }
                )
            }

            // Obsah karty: vlevo fotografie (kliknutím možnost editace), vpravo 2 řádky: 1. Popis vzhledu, 2. Vlastník
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayPhoto = remember(card.imageUri, card.extraImages) { primaryContainerPhoto(card) }
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, cardBorderColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (!displayPhoto.isNullOrBlank()) {
                                showImageZoom = true
                            } else {
                                onInfoClick()
                            }
                        }
                ) {
                    if (!displayPhoto.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(resolveCoilImageModel(displayPhoto))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Fotografie kontejneru",
                            contentScale = ContentScale.Crop,
                            alignment = containerPreviewAlignment(displayPhoto),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Foto kontejneru",
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(26.dp)
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Řádek 1: Popis kontejneru jak vypadá
                    val popisVzhledu = card.description.replace("\n", " ").trim().ifBlank {
                        if (card.color.isNotBlank()) "Barva: ${card.color}" else "Standardní kontejner"
                    }
                    Text(
                        text = popisVzhledu,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0F172A),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    // Řádek 2: Kdo je vlastníkem toho kontejneru (a smazat ikona pokud je akce k dispozici)
                    val vlastnikName = card.vlastnik.trim().ifBlank {
                        card.depo.ifBlank { "Vlastní sklad" }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vlastník: $vlastnikName",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        if (onDeleteClick != null) {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Smazat",
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    // Žádný další řádek zde již není - končí vlastníkem!
                }
            }
        }
    }
    val zoomPhoto = remember(card.imageUri, card.extraImages) { primaryContainerPhoto(card) }
    if (showImageZoom && !zoomPhoto.isNullOrBlank()) {
        Dialog(onDismissRequest = { showImageZoom = false }) {
            ZoomableImage(imageUri = zoomPhoto, onEditClick = {
                showImageZoom = false
                onEditImage(zoomPhoto)
            }, onCloseClick = { showImageZoom = false })
        }
    }
}

@Composable
fun ZoomableImage(imageUri: String, onEditClick: () -> Unit, onCloseClick: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    val newX = offset.x + pan.x * scale
                    val newY = offset.y + pan.y * scale
                    offset = androidx.compose.ui.geometry.Offset(newX, newY)
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(resolveCoilImageModel(imageUri))
                .crossfade(true)
                .build(),
            contentDescription = "Zvětšený obrázek",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
        
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(width = 46.dp, height = 38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Upravit fotku", tint = Color.White)
            }
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .size(width = 46.dp, height = 38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
            }
        }
    }
}
