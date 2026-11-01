package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ContainerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronajemScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit, onNavigateToDetail: (String) -> Unit) {
    val pronajem by viewModel.pronajem.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val isDepo2 = currentDepo == "Depo 2"
    val depoWorkspaceBg = if (isDepo2) Color(0xFFF7F0E3) else Color(0xFFE6EFF7)
    var editingImageUri by remember { mutableStateOf<String?>(null) }
    var deleteConfirmCard by remember { mutableStateOf<ContainerCard?>(null) }
    var secondConfirmDeleteCard by remember { mutableStateOf<ContainerCard?>(null) }
    var viewMode by remember { mutableStateOf("CARDS") }
    var showAddRentalDialog by remember { mutableStateOf(false) }
    
    if (editingImageUri != null) {
        val targetUri = editingImageUri!!
        EditImageScreen(
            imagePath = targetUri,
            onSave = { editingImageUri = null },
            onCancel = { editingImageUri = null },
            onSaveImage = { updatedDataUri ->
                val matched = pronajem.find { c ->
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

    if (secondConfirmDeleteCard != null) {
        val target = secondConfirmDeleteCard!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Kontejner z Pronájmu: ${target.designation}",
            itemDescription = "Nájemce: ${target.vlastnik.ifBlank { "-" }} • Středisko: ${target.depo}",
            onDismiss = { secondConfirmDeleteCard = null },
            onConfirmedDelete = {
                viewModel.deleteCard(target)
                secondConfirmDeleteCard = null
            }
        )
    }

    if (deleteConfirmCard != null) {
        val target = deleteConfirmCard!!
        AlertDialog(
            onDismissRequest = { deleteConfirmCard = null },
            title = { Text("Ukončit pronájem nebo smazat?", fontWeight = FontWeight.Bold) },
            text = { Text("Chcete kartu ${target.designation} vrátit na DEPO nebo trvale smazat?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.updateStatus(target, if (target.depo == "Depo 2") "DEPO 02" else "DEPO 01")
                            deleteConfirmCard = null
                        }
                    ) {
                        Text("Vrátit na Depo", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            deleteConfirmCard = null
                            secondConfirmDeleteCard = target
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Smazat trvale...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmCard = null }) {
                    Text("Zrušit")
                }
            }
        )
    }

    if (showAddRentalDialog) {
        val context = LocalContext.current
        var desig by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(if (isDepo2) "20 DV" else "OB") }
        var customer by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("2500") }
        var note by remember { mutableStateOf("") }
        var color by remember { mutableStateOf("") }
        var spz by remember { mutableStateOf("") }
        var newPhotoPath by remember { mutableStateOf<String?>(null) }
        var newPhotoTimestamp by remember { mutableStateOf<Long?>(null) }
        var isAnalyzingRental by remember { mutableStateOf(false) }
        var aiRentalMessage by remember { mutableStateOf<String?>(null) }
        var editingRentalDraftPhoto by remember { mutableStateOf(false) }
        val rentalCamFile = remember { File(context.filesDir, "rental_cam_temp.jpg") }
        val duplicateCard = remember(desig, pronajem) {
            viewModel.findDuplicateContainer(desig)
        }

        fun handleRentalPhoto(file: File, tsOverride: Long?) {
            isAnalyzingRental = true
            aiRentalMessage = null
            viewModel.analyzePhotoForDraftContainer(
                photoFile = file,
                photoTimestampOverride = tsOverride,
                onResult = { res, savedPath, photoTimeMs ->
                    isAnalyzingRental = false
                    newPhotoPath = savedPath
                    newPhotoTimestamp = photoTimeMs
                    if (res.designation.isNotBlank()) desig = res.designation
                    if (res.type.isNotBlank()) {
                        type = when {
                            res.type.contains("Obyt", ignoreCase = true) || res.type.equals("OB", ignoreCase = true) -> "OB"
                            res.type.contains("Sklad", ignoreCase = true) || res.type.equals("SK", ignoreCase = true) -> "SK"
                            else -> res.type
                        }
                    }
                    if (res.color.isNotBlank()) color = res.color
                    if (res.description.isNotBlank()) note = res.description
                    if (!res.spz.isNullOrBlank()) spz = res.spz
                    aiRentalMessage = if (res.designation.isNotBlank()) {
                        "AI načteno — Označení: ${res.designation} • Popis: ${res.description}"
                    } else {
                        "AI načteno — Popis: ${res.description}"
                    }
                },
                onError = { err ->
                    isAnalyzingRental = false
                    aiRentalMessage = err
                }
            )
        }

        val rentalCamLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                val ts = extractPhotoTimestamp(context, null, rentalCamFile)
                handleRentalPhoto(rentalCamFile, ts)
            }
        }
        val rentalGalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val tempFile = File(context.filesDir, "rental_gal_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    val ts = extractPhotoTimestamp(context, it, tempFile)
                    handleRentalPhoto(tempFile, ts)
                } catch (_: Exception) {}
            }
        }

        if (editingRentalDraftPhoto && newPhotoPath != null) {
            Dialog(
                onDismissRequest = { editingRentalDraftPhoto = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                EditImageScreen(
                    imagePath = newPhotoPath!!,
                    onSave = { editingRentalDraftPhoto = false },
                    onCancel = { editingRentalDraftPhoto = false },
                    onSaveImage = { updatedUri -> newPhotoPath = updatedUri }
                )
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isAnalyzingRental) showAddRentalDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pronájem", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    rentalCamFile
                                )
                                rentalCamLauncher.launch(uri)
                            },
                            enabled = !isAnalyzingRental,
                            shape = RoundedCornerShape(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.size(width = 46.dp, height = 38.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Vyfotit kontejner (AI)", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        FilledIconButton(
                            onClick = { rentalGalLauncher.launch("image/*") },
                            enabled = !isAnalyzingRental,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAnalyzingRental) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (!aiRentalMessage.isNullOrBlank()) {
                        Text(
                            text = aiRentalMessage!!,
                            color = Color(0xFF0369A1),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!newPhotoPath.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                .clickable { editingRentalDraftPhoto = true }
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
                        value = desig,
                        onValueChange = {
                            desig = it
                            if (it.uppercase().startsWith("OB")) type = "OB"
                            else if (it.uppercase().startsWith("SK")) type = "SK"
                        },
                        label = { Text("Označení", maxLines = 1, softWrap = false) },
                        isError = duplicateCard != null,
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
                            softWrap = false
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("OB", "SK", "20 DV", "40 HC").forEach { t ->
                            FilterChip(
                                selected = type.equals(t, ignoreCase = true),
                                onClick = { type = t },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        t,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
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
                            value = customer,
                            onValueChange = { customer = it },
                            label = { Text("Nájemce", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Cena", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
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
                        if (duplicateCard != null) return@Button
                        val created = viewModel.createManualContainer(
                            designation = desig,
                            type = type,
                            status = "PRONÁJEM",
                            depo = currentDepo ?: "Depo 1",
                            vlastnik = customer,
                            price = price.toLongOrNull() ?: 0L,
                            poznamka = note,
                            description = note,
                            color = color,
                            spz = spz,
                            imageUri = newPhotoPath,
                            extraImages = newPhotoPath ?: "",
                            photoTimestamp = newPhotoTimestamp
                        )
                        showAddRentalDialog = false
                        onNavigateToDetail(created.id)
                    },
                    enabled = !isAnalyzingRental && duplicateCard == null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Vytvořit", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddRentalDialog = false },
                    enabled = !isAnalyzingRental,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = depoWorkspaceBg,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        "PRONÁJEM (${pronajem.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewMode = if (viewMode == "CARDS") "LIST" else "CARDS" },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (viewMode == "CARDS") Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (viewMode == "CARDS") "LIST" else "FOTO", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showAddRentalDialog = true }, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                        Icon(Icons.Default.Add, contentDescription = "Nový pronájem")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDepo2) Color(0xFF5D5342) else Color(0xFF1B5E8B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (pronajem.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(depoWorkspaceBg)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Žádné záznamy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showAddRentalDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Přidat")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(depoWorkspaceBg).padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(if (viewMode == "LIST") 6.dp else 12.dp)
            ) {
                items(pronajem, key = { it.id }) { container ->
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
