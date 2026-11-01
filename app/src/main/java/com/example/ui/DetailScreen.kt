package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import java.io.File
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ContainerCard
import androidx.compose.material.icons.automirrored.filled.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContainerHistoryItem(
    val id: String,
    val type: String,
    val company: String,
    val docNumber: String,
    val date: String,
    val detail: String = "",
    val timestamp: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    containerId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var container by remember { mutableStateOf<ContainerCard?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showImageZoom by remember { mutableStateOf<String?>(null) }
    var editingImageUri by remember { mutableStateOf<String?>(null) }
    var isDeleted by remember { mutableStateOf(false) }
    var TopBarDeleteConfirm by remember { mutableStateOf(false) }
    val allZakazky by viewModel.zakazky.collectAsState()
    val allTransports by viewModel.transports.collectAsState()
    val allFaktury by viewModel.faktury.collectAsState()
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val currentExtra = parseExtraImageUris(container?.extraImages)
                .filter { !isGeneratedFallbackPhoto(it) }
                .toMutableList()
            var latestAddedPath: String? = null
            for (uri in uris.take((10 - currentExtra.size).coerceAtLeast(1))) {
                val stream = contentResolver.openInputStream(uri)
                stream?.use { s ->
                    val file = File(context.filesDir, "${java.util.UUID.randomUUID()}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        s.copyTo(out)
                    }
                    val photoTime = extractPhotoTimestamp(context, uri, file)
                    try {
                        val exif = android.media.ExifInterface(file.absolutePath)
                        val orient = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                        val deg = when (orient) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                        if (deg != 0f) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) {
                                val mat = android.graphics.Matrix().apply { postRotate(deg) }
                                val rotated = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
                                java.io.FileOutputStream(file).use { out ->
                                    rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    currentExtra.add(file.absolutePath)
                    latestAddedPath = file.absolutePath
                    if (container?.arrivalDate == null) {
                        container = container?.copy(timestamp = photoTime, arrivalDate = photoTime)
                    }
                }
            }
            val existingMain = sanitizeImageUri(container?.imageUri)
            val isFirstMain = existingMain.isNullOrBlank() || isGeneratedFallbackPhoto(existingMain)
            val newMain = if (isFirstMain) {
                latestAddedPath ?: currentExtra.firstOrNull()
            } else {
                existingMain
            }
            val firstPhotoTime = uris.firstOrNull()?.let { extractPhotoTimestamp(context, it, null) }
            val updated = container?.copy(
                imageUri = newMain,
                extraImages = currentExtra.distinct().joinToString("|"),
                timestamp = if (isFirstMain && firstPhotoTime != null) firstPhotoTime else (container?.timestamp ?: System.currentTimeMillis()),
                arrivalDate = if (isFirstMain && firstPhotoTime != null) firstPhotoTime else (container?.arrivalDate ?: container?.timestamp)
            )
            container = updated
            updated?.let { savedCard ->
                viewModel.saveContainer(savedCard)
                val pathForAi = latestAddedPath
                if (pathForAi != null) {
                    coroutineScope.launch {
                        val aiRes = withContext(Dispatchers.IO) {
                            try {
                                val bmp = BitmapFactory.decodeFile(pathForAi)
                                if (bmp != null) viewModel.analyzeContainerBitmap(bmp) else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (aiRes != null) {
                            val current = container ?: savedCard
                            val nextDesig = if (current.designation.isBlank() && aiRes.designation.isNotBlank()) aiRes.designation else current.designation
                            val nextType = if (current.type.isBlank() && aiRes.type.isNotBlank()) aiRes.type else current.type
                            val nextColor = if (aiRes.color.isNotBlank()) aiRes.color else current.color
                            val nextSpz = if (current.spz.isBlank() && !aiRes.spz.isNullOrBlank()) aiRes.spz else current.spz
                            val nextDesc = if (aiRes.description.isNotBlank()) {
                                com.example.api.formatContainerShortDescription(nextColor, nextType, aiRes.description)
                            } else current.description
                            val enriched = current.copy(
                                designation = nextDesig,
                                type = nextType,
                                color = nextColor,
                                spz = nextSpz,
                                description = nextDesc
                            )
                            container = enriched
                            viewModel.saveContainer(enriched)
                            android.widget.Toast.makeText(
                                context,
                                "✅ Z fotografie vyčteno: ${nextDesig.ifBlank { "kontejner" }} ($nextDesc)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            android.widget.Toast.makeText(context, "Fotografie uložena a synchronizována do PC", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Fotografie uložena a synchronizována do PC", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Loading initial data
    LaunchedEffect(containerId) {
        if (container == null) {
            container = viewModel.getContainerById(containerId)
        }
    }

    BackHandler {
        if (showImageZoom != null) {
            showImageZoom = null
        } else if (editingImageUri != null) {
            editingImageUri = null
        } else if (TopBarDeleteConfirm) {
            TopBarDeleteConfirm = false
        } else if (showDeleteDialog) {
            showDeleteDialog = false
        } else {
            onBack()
        }
    }

    if (editingImageUri != null) {
        val targetUri = editingImageUri!!
        EditImageScreen(
            imagePath = targetUri,
            onSave = { editingImageUri = null },
            onCancel = { editingImageUri = null },
            onSaveImage = { updatedDataUri ->
                val current = container
                if (current != null) {
                    val oldExtras = parseExtraImageUris(current.extraImages)
                    val newExtras = if (oldExtras.contains(targetUri)) {
                        oldExtras.map { if (it == targetUri) updatedDataUri else it }
                    } else {
                        listOf(updatedDataUri) + oldExtras
                    }
                    val currentMain = sanitizeImageUri(current.imageUri)
                    val newMain = if (currentMain == null || currentMain == targetUri) {
                        updatedDataUri
                    } else {
                        currentMain
                    }
                    val updated = current.copy(
                        imageUri = newMain,
                        extraImages = newExtras.distinct().joinToString("|")
                    )
                    container = updated
                    viewModel.saveContainer(updated)
                    android.widget.Toast.makeText(
                        context,
                        "Upravená fotografie uložena a synchronizována s PC",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        return
    }

    val latestContainer by rememberUpdatedState(container)
    val latestIsDeleted by rememberUpdatedState(isDeleted)

    DisposableEffect(Unit) {
        onDispose {
            val finalContainer = latestContainer
            if (!latestIsDeleted && finalContainer != null) {
                val dup = viewModel.findDuplicateContainer(finalContainer.designation, excludeId = finalContainer.id)
                if (dup == null) {
                    viewModel.saveContainer(finalContainer)
                }
            }
        }
    }

    if (TopBarDeleteConfirm && container != null) {
        DoubleConfirmDeleteDialog(
            itemTitle = "Kontejner ${container?.designation ?: ""}",
            itemDescription = "Typ: ${container?.type ?: ""} • Stav: ${container?.status ?: ""}",
            onDismiss = { TopBarDeleteConfirm = false },
            onConfirmedDelete = {
                TopBarDeleteConfirm = false
                isDeleted = true
                container?.let { viewModel.deleteCard(it) }
                onBack()
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(width = 40.dp, height = 34.dp).clip(RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zpět",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = if (container != null && container!!.systemNumber > 0) "#${container!!.systemNumber} • ${container!!.designation.ifBlank { "Kontejner" }}" else "Kontejner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                    if (container != null) {
                        Button(
                            onClick = {
                                val cur = container
                                if (cur != null) {
                                    val dup = viewModel.findDuplicateContainer(cur.designation, excludeId = cur.id)
                                    if (dup != null) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Duplicita: Kontejner s označením ${cur.designation} již existuje!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }
                                    viewModel.saveContainer(cur)
                                }
                                onBack()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Uložit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { TopBarDeleteConfirm = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Vymazat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    ) { padding ->
        val currentContainer = container
        if (currentContainer == null) {
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photo
                val cardBgColor = when(currentContainer.status) {
                    "DEPO 01" -> Color.White
                    "PRONAJ." -> Color.White
                    "OPRAVY" -> Color.White
                    "ARCHIV" -> Color.White
                    "DEPO 02" -> Color.White
                    "REZERV." -> Color.White // Nechat původní pro rezervaci
                    "PRODEJ" -> Color.White
                    "PRONÁJ" -> Color.White
                    else -> MaterialTheme.colorScheme.surface
                }

                val isWhiteBg = cardBgColor == Color.White
                val textColor = if (isWhiteBg) Color.Black else MaterialTheme.colorScheme.onSurface

                val textFieldColors = if (isWhiteBg) {
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        disabledTextColor = Color.Black,
                        errorTextColor = Color.Black,
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.5f),
                        disabledBorderColor = Color.Black.copy(alpha = 0.5f),
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                        disabledLabelColor = Color.Black.copy(alpha = 0.7f),
                        focusedTrailingIconColor = Color.Black,
                        unfocusedTrailingIconColor = Color.Black,
                        disabledTrailingIconColor = Color.Black.copy(alpha = 0.5f),
                        cursorColor = Color.Black
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                }

                val disabledTextFieldColors = if (isWhiteBg) {
                    OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color.Black,
                        disabledBorderColor = Color.Black.copy(alpha = 0.5f),
                        disabledPlaceholderColor = Color.Black.copy(alpha = 0.5f),
                        disabledLabelColor = Color.Black,
                        disabledTrailingIconColor = Color.Black.copy(alpha = 0.5f)
                    )
                } else {
                    OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Tisk karty / WhatsApp / E-mail na kartě kontejneru
                AgendaPrintShareBar(
                    onPrint = {
                        val pdf = generateContainerCardPdf(context, currentContainer)
                        printPdfFile(context, pdf, "Karta_${currentContainer.designation}")
                    },
                    onWhatsApp = {
                        val pdf = generateContainerCardPdf(context, currentContainer)
                        val summary = "Karta kontejneru / Předávací protokol: ${currentContainer.designation} (${currentContainer.type})\nStředisko: ${currentContainer.depo} • Stav: ${currentContainer.status}\nCena bez DPH: ${formatPrice(currentContainer.price)}"
                        sharePdfViaWhatsApp(context, pdf, summary, currentContainer.soldPhone.ifBlank { currentContainer.reservedPhone })
                    },
                    onEmail = {
                        val pdf = generateContainerCardPdf(context, currentContainer)
                        sharePdfViaEmail(
                            context,
                            pdf,
                            currentContainer.soldEmail.ifBlank { currentContainer.reservedEmail },
                            "Karta kontejneru / Předávací protokol ${currentContainer.designation} – Kontejnery Ostrava s.r.o.",
                            "zasíláme Vám kartu kontejneru a předávací protokol pro kontejner ${currentContainer.designation} (${currentContainer.type})."
                        )
                    }
                )

                val mainPhotoUri = remember(currentContainer.imageUri, currentContainer.extraImages) {
                    primaryContainerPhoto(currentContainer)
                }

                var isAiLoadingOnDetail by remember { mutableStateOf(false) }
                var aiDetailMessage by remember { mutableStateOf<String?>(null) }
                val detailAiCamFile = remember { File(context.filesDir, "detail_ai_cam_temp.jpg") }

                fun runDetailAiPhotoAnalysis(sourceFile: File, timestampOverride: Long? = null) {
                    isAiLoadingOnDetail = true
                    aiDetailMessage = null
                    viewModel.analyzePhotoForDraftContainer(
                        photoFile = sourceFile,
                        photoTimestampOverride = timestampOverride,
                        onResult = { res, savedPath, photoTimeMs ->
                            isAiLoadingOnDetail = false
                            val currentCard = container ?: currentContainer
                            val currentExtra = parseExtraImageUris(currentCard.extraImages)
                                .filter { !isGeneratedFallbackPhoto(it) }
                                .toMutableList()
                            if (savedPath !in currentExtra && currentExtra.size < 10) {
                                currentExtra.add(0, savedPath)
                            }
                            val updatedCard = currentCard.copy(
                                designation = if (res.designation.isNotBlank()) res.designation else currentCard.designation,
                                type = if (res.type.isNotBlank()) res.type else currentCard.type,
                                color = if (res.color.isNotBlank()) res.color else currentCard.color,
                                description = if (res.description.isNotBlank()) res.description else currentCard.description,
                                poznamka = if (res.description.isNotBlank() && currentCard.poznamka.isBlank()) res.description else currentCard.poznamka,
                                spz = if (!res.spz.isNullOrBlank()) res.spz else currentCard.spz,
                                imageUri = savedPath,
                                extraImages = currentExtra.distinct().joinToString("|"),
                                timestamp = photoTimeMs,
                                arrivalDate = photoTimeMs
                            )
                            container = updatedCard
                            viewModel.saveContainer(updatedCard)
                            aiDetailMessage = if (res.designation.isNotBlank()) {
                                "Vyčteno: ${res.designation} (${res.color.ifBlank { res.type }})"
                            } else {
                                "Fotka uložena. Kód na fotce nerozpoznán."
                            }
                        },
                        onError = { err ->
                            isAiLoadingOnDetail = false
                            aiDetailMessage = err
                        }
                    )
                }

                val detailAiCameraLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        val photoTime = extractPhotoTimestamp(context, null, detailAiCamFile)
                        runDetailAiPhotoAnalysis(detailAiCamFile, photoTime)
                    }
                }

                val detailAiGalleryLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        try {
                            val tempFile = File(context.filesDir, "detail_ai_gal_${System.currentTimeMillis()}.jpg")
                            context.contentResolver.openInputStream(it)?.use { input ->
                                java.io.FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val photoTime = extractPhotoTimestamp(context, it, tempFile)
                            runDetailAiPhotoAnalysis(tempFile, photoTime)
                        } catch (e: Exception) {
                            aiDetailMessage = "Chyba načtení z galerie: ${e.localizedMessage}"
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                detailAiCamFile
                            )
                            detailAiCameraLauncher.launch(uri)
                        },
                        enabled = !isAiLoadingOnDetail,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp).width(115.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Vyfotit", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Vyfotit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { detailAiGalleryLauncher.launch("image/*") },
                        enabled = !isAiLoadingOnDetail,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp).width(115.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Z galerie", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Galerie", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (isAiLoadingOnDetail) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (!aiDetailMessage.isNullOrBlank()) {
                        val isOk = aiDetailMessage!!.startsWith("Vyčteno") || aiDetailMessage!!.startsWith("Fotka uložena")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = aiDetailMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOk) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }

                if (!mainPhotoUri.isNullOrBlank()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = resolveCoilImageModel(mainPhotoUri),
                            contentDescription = "Fotografie kontejneru",
                            contentScale = ContentScale.Crop,
                            alignment = containerPreviewAlignment(mainPhotoUri),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showImageZoom = mainPhotoUri }
                        )
                    }
                }


                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBgColor,
                        contentColor = textColor
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        
                        // Systémové číslo kontejneru (v detailu se dále používá)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#${currentContainer.effectiveSystemNumber()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0284C7),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = currentContainer.depo.ifBlank { "Depo 1" },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF475569)
                            )
                        }

                        val owners by viewModel.owners.collectAsState()
                        val prepravci by viewModel.prepravci.collectAsState()
                        val carrierOptions = remember(prepravci, allTransports) {
                            (prepravci + allTransports.map { it.company.trim() })
                                .filter { it.isNotBlank() }
                                .distinct()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var typeDropdownExpanded by remember { mutableStateOf(false) }
                            val isSk = currentContainer.type.contains("Sklad", ignoreCase = true) ||
                                currentContainer.type.equals("SK", ignoreCase = true) ||
                                currentContainer.designation.uppercase().startsWith("SK")
                            val is20 = currentContainer.type.contains("20", ignoreCase = true)
                            val is40 = currentContainer.type.contains("40", ignoreCase = true)
                            val displayType = when {
                                isSk -> "SK"
                                is20 -> "20 DV"
                                is40 -> "40 HC"
                                else -> "OB"
                            }
                            val duplicateInDetail = remember(currentContainer.designation, currentContainer.id) {
                                viewModel.findDuplicateContainer(currentContainer.designation, excludeId = currentContainer.id)
                            }

                            Box {
                                Surface(
                                    onClick = { typeDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                    color = Color(0xFFF0F9FF),
                                    modifier = Modifier.height(52.dp).width(90.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(displayType, fontSize = 14.sp, color = Color(0xFF0C4A6E), fontWeight = FontWeight.ExtraBold)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Vybrat typ", tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = typeDropdownExpanded,
                                    onDismissRequest = { typeDropdownExpanded = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    listOf("OB", "SK", "20 DV", "40 HC").forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                                            onClick = {
                                                typeDropdownExpanded = false
                                                val desig = when (t) {
                                                    "OB" -> if (currentContainer.designation.uppercase().startsWith("SK")) "OB" + currentContainer.designation.drop(2) else currentContainer.designation
                                                    "SK" -> if (currentContainer.designation.uppercase().startsWith("OB")) "SK" + currentContainer.designation.drop(2) else currentContainer.designation
                                                    else -> currentContainer.designation
                                                }
                                                container = currentContainer.copy(type = t, designation = desig)
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                colors = textFieldColors,
                                value = currentContainer.designation,
                                onValueChange = { 
                                    var newType = currentContainer.type
                                    if (it.uppercase().startsWith("OB")) {
                                        newType = "OB"
                                    } else if (it.uppercase().startsWith("SK")) {
                                        newType = "SK"
                                    }
                                    container = currentContainer.copy(designation = it, type = newType)
                                },
                                placeholder = { Text("OB12 / MSKU 123456-7", fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                isError = duplicateInDetail != null,
                                textStyle = LocalTextStyle.current.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(52.dp)
                            )
                        }
                        val dupWarning = viewModel.findDuplicateContainer(currentContainer.designation, excludeId = currentContainer.id)
                        if (dupWarning != null) {
                            Text(
                                text = "Duplicita: Kontejner ${dupWarning.designation} již v agendě existuje!",
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Status selection moved here
                        Text("Stav", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        
                        var statusExpanded by remember { mutableStateOf(false) }
                        
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            OutlinedButton(
                                onClick = { statusExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(currentContainer.status.ifEmpty { "Vyberte stav" }, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            DropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                val statuses = if (currentContainer.depo == "Depo 1") {
                                    listOf("DEPO 01", "PRONÁJEM", "PRONAJATO", "OPRAVA", "PRODEJ", "ARCHIV")
                                } else {
                                    listOf("DEPO 02", "PRONÁJEM", "PRONAJATO", "OPRAVA", "PRODEJ", "ARCHIV")
                                }
                                statuses.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status) },
                                        onClick = {
                                            container = currentContainer.copy(status = status)
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Položka CENA přesunuta hned nahoru mezi Stav a Vlastník
                        OutlinedTextField(
                            colors = textFieldColors,
                            value = if (currentContainer.price == 0L) "" else currentContainer.price.toString(),
                            onValueChange = { 
                                val amount = it.toLongOrNull() ?: 0L
                                container = currentContainer.copy(price = amount) 
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            label = { Text("Cena", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        var ownerExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = ownerExpanded,
                            onExpandedChange = { ownerExpanded = !ownerExpanded }
                        ) {
                            OutlinedTextField(
                                colors = textFieldColors,
                                value = currentContainer.vlastnik,
                                onValueChange = { container = currentContainer.copy(vlastnik = it) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("Vlastník", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("Vyberte vlastníka", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ownerExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = ownerExpanded,
                                onDismissRequest = { ownerExpanded = false }
                            ) {
                                owners.forEach { selOwner ->
                                    DropdownMenuItem(
                                        text = { Text(selOwner) },
                                        onClick = {
                                            container = currentContainer.copy(vlastnik = selOwner)
                                            ownerExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            colors = textFieldColors,
                            value = currentContainer.color,
                            onValueChange = { container = currentContainer.copy(color = it) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            label = { Text("Barva", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            val normalizedDisplayType = when {
                                currentContainer.type.equals("Obytný", ignoreCase = true) -> "OB"
                                currentContainer.type.equals("Skladový", ignoreCase = true) -> "SK"
                                else -> currentContainer.type
                            }
                            OutlinedTextField(
                                colors = textFieldColors,
                                value = normalizedDisplayType,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("Typ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                val types = listOf("OB", "SK", "20 DV", "40 HC")
                                types.forEach { selType ->
                                    DropdownMenuItem(
                                        text = { Text(selType) },
                                        onClick = {
                                            container = currentContainer.copy(type = selType)
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            colors = textFieldColors,
                            value = currentContainer.spz,
                            onValueChange = { container = currentContainer.copy(spz = it) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            label = { Text("SPZ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (currentContainer.depo == "Depo 1" && currentContainer.type != "Skladový" && currentContainer.type != "SK") {
                            var showRevDatePicker by remember { mutableStateOf(false) }
                            if (showRevDatePicker) {
                                val revDatePickerState = rememberDatePickerState(initialSelectedDateMillis = currentContainer.revisionDate ?: System.currentTimeMillis())
                                DatePickerDialog(
                                    onDismissRequest = { showRevDatePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = { 
                                            container = currentContainer.copy(revisionDate = revDatePickerState.selectedDateMillis)
                                            showRevDatePicker = false 
                                        }) { Text("OK") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showRevDatePicker = false }) { Text("Zrušit") }
                                    }
                                ) {
                                    DatePicker(state = revDatePickerState)
                                }
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = currentContainer.revisionDate?.let { formatDate(it) } ?: "",
                                    onValueChange = { },
                                    readOnly = true,
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    label = { Text("Revize", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = false,
                                    colors = disabledTextFieldColors
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { showRevDatePicker = true })
                            }
                        }

                        var transporterExpanded by remember { mutableStateOf(false) }
                        var showCustomTransporterDialog by remember { mutableStateOf(false) }
                        var customTransporterInput by remember { mutableStateOf("") }

                        if (showCustomTransporterDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showCustomTransporterDialog = false
                                    customTransporterInput = ""
                                },
                                title = { Text("Přepravce", fontWeight = FontWeight.Bold) },
                                text = {
                                    OutlinedTextField(
                                        value = customTransporterInput,
                                        onValueChange = { customTransporterInput = it },
                                        label = { Text("Přepravce", maxLines = 1, softWrap = false) },
                                        placeholder = { Text("Zadejte přepravce", maxLines = 1, softWrap = false) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val trimmed = customTransporterInput.trim()
                                            if (trimmed.isNotBlank()) {
                                                viewModel.addPrepravce(trimmed)
                                                container = currentContainer.copy(transporter = trimmed)
                                            }
                                            showCustomTransporterDialog = false
                                            customTransporterInput = ""
                                        }
                                    ) {
                                        Text("Uložit")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showCustomTransporterDialog = false
                                            customTransporterInput = ""
                                        }
                                    ) {
                                        Text("Zrušit")
                                    }
                                }
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                colors = textFieldColors,
                                value = currentContainer.transporter,
                                onValueChange = { container = currentContainer.copy(transporter = it) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("Přepravce", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("Vyberte nebo zadejte vlastního", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                textStyle = LocalTextStyle.current.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { transporterExpanded = !transporterExpanded }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Vybrat přepravce ze seznamu",
                                            tint = Color(0xFF0284C7)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = transporterExpanded,
                                onDismissRequest = { transporterExpanded = false },
                                modifier = Modifier.background(Color.White).fillMaxWidth(0.9f)
                            ) {
                                carrierOptions.forEach { selCarrier ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = selCarrier,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                        },
                                        onClick = {
                                            container = currentContainer.copy(transporter = selCarrier)
                                            transporterExpanded = false
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
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "+ Přidat nového do stálého seznamu",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0284C7)
                                            )
                                        }
                                    },
                                    onClick = {
                                        transporterExpanded = false
                                        customTransporterInput = currentContainer.transporter
                                        showCustomTransporterDialog = true
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            colors = textFieldColors,
                            value = currentContainer.poznamka,
                            onValueChange = { container = currentContainer.copy(poznamka = it) },
                            label = { Text("Poznámka", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        
                        OutlinedTextField(
                            colors = textFieldColors,
                            value = currentContainer.description,
                            onValueChange = {
                                val twoLines = it.lines().take(2).joinToString("\n")
                                container = currentContainer.copy(description = twoLines)
                            },
                            label = { Text("Popis", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("šedý\nobytný", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 2
                        )
                        
                        var showArrivalDatePicker by remember { mutableStateOf(false) }
                        if (showArrivalDatePicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = currentContainer.arrivalDate ?: currentContainer.timestamp
                            )
                            DatePickerDialog(
                                onDismissRequest = { showArrivalDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = { 
                                        val picked = datePickerState.selectedDateMillis ?: currentContainer.timestamp
                                        container = currentContainer.copy(arrivalDate = picked, timestamp = picked)
                                        showArrivalDatePicker = false 
                                    }) { Text("OK") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showArrivalDatePicker = false }) { Text("Zrušit") }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = formatDate(currentContainer.arrivalDate ?: currentContainer.timestamp),
                                onValueChange = { },
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("Datum", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                colors = disabledTextFieldColors
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showArrivalDatePicker = true })
                        }
                        

                        HorizontalDivider()
                        val extraList = parseExtraImageUris(currentContainer.extraImages)
                        Text("Fotografie", fontWeight = FontWeight.SemiBold)
                        
                        // temp file for camera
                        val tempPhotoFile = remember { File(context.filesDir, "temp_camera.jpg") }
                        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                            if (success) {
                                val currentExtra = parseExtraImageUris(container?.extraImages)
                                    .filter { !isGeneratedFallbackPhoto(it) }
                                    .toMutableList()
                                if (currentExtra.size < 10) {
                                    val finalFile = File(context.filesDir, "${java.util.UUID.randomUUID()}.jpg")
                                    tempPhotoFile.copyTo(finalFile, overwrite = true)
                                    currentExtra.add(finalFile.absolutePath)
                                    val existingMain = sanitizeImageUri(container?.imageUri)
                                    val newMain = if (existingMain.isNullOrBlank() || isGeneratedFallbackPhoto(existingMain)) {
                                        finalFile.absolutePath
                                    } else {
                                        existingMain
                                    }
                                    val updated = container?.copy(
                                        imageUri = newMain,
                                        extraImages = currentExtra.distinct().joinToString("|")
                                    )
                                    container = updated
                                    updated?.let {
                                        viewModel.saveContainer(it)
                                        android.widget.Toast.makeText(context, "Fotografie uložena a synchronizována do PC", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    val photoTime = extractPhotoTimestamp(context, null, finalFile)
                                    runDetailAiPhotoAnalysis(finalFile, photoTime)
                                }
                            }
                        }

                        Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            extraList.forEach { path ->
                                val isMain = path == sanitizeImageUri(currentContainer.imageUri)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(if (isMain) 2.dp else 1.dp, if (isMain) Color(0xFF009FE3) else Color.LightGray, RoundedCornerShape(8.dp))
                                            .clickable { showImageZoom = path }
                                    ) {
                                        AsyncImage(
                                            model = resolveCoilImageModel(path),
                                            contentDescription = "Další foto",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (!isMain) {
                                        Text(
                                            text = "Nastavit hlavní",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clickable {
                                                    val updated = currentContainer.copy(imageUri = path)
                                                    container = updated
                                                    viewModel.saveContainer(updated)
                                                    android.widget.Toast.makeText(context, "Nastaveno jako hlavní foto", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                    } else {
                                        Text(
                                            text = "HLAVNÍ FOTO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF047857),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Smazat",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626),
                                        modifier = Modifier
                                            .padding(top = 1.dp)
                                            .clickable {
                                                val remaining = extraList.filter { it != path }
                                                val nextMain = if (isMain) remaining.firstOrNull() else currentContainer.imageUri
                                                val updated = currentContainer.copy(
                                                    imageUri = nextMain,
                                                    extraImages = remaining.joinToString("|")
                                                )
                                                container = updated
                                                viewModel.saveContainer(updated)
                                                android.widget.Toast.makeText(context, "Fotografie odstraněna a synchronizována", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                    )
                                }
                            }
                            if (extraList.size < 10) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE0F2FE))
                                        .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.provider",
                                                    tempPhotoFile
                                                )
                                                cameraLauncher.launch(uri)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Nelze otevřít fotoaparát", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Vyfotit další foto", tint = Color(0xFF0284C7))
                                        Text("Vyfotit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.LightGray)
                                        .clickable {
                                            try {
                                                galleryLauncher.launch("image/*")
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Nelze otevřít galerii", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Přidat foto z galerie")
                                        Text("Galerie", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        
                        // Author Dropdown
                        var authorExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = authorExpanded,
                            onExpandedChange = { authorExpanded = !authorExpanded }
                        ) {
                            OutlinedTextField(
                                colors = textFieldColors,
                                value = currentContainer.author,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("Autor", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authorExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = authorExpanded,
                                onDismissRequest = { authorExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Přema") },
                                    onClick = { 
                                        container = currentContainer.copy(author = "Přema")
                                        authorExpanded = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Vojta") },
                                    onClick = { 
                                        container = currentContainer.copy(author = "Vojta")
                                        authorExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                }

                // ==================== SEKCE: HISTORIE ====================
                val desig = currentContainer.designation.trim()
                val sysNum = currentContainer.systemNumber
                val containerHistory = remember(currentContainer, allZakazky, allTransports, allFaktury) {
                    val list = mutableListOf<ContainerHistoryItem>()

                    // 1. Ze Zakázek (kde je kontejner přiřazen)
                    for (z in allZakazky) {
                        val matchesItem = z.items.any { item ->
                            (desig.isNotBlank() && item.text.contains(desig, ignoreCase = true)) ||
                                (sysNum > 0 && item.text.contains("#$sysNum"))
                        }
                        val matchesText = desig.isNotBlank() && (
                            z.prepravaInfo.contains(desig, ignoreCase = true) ||
                            z.lokalita.contains(desig, ignoreCase = true)
                        )
                        if (matchesItem || matchesText) {
                            val companyName = z.firma.ifBlank { z.vyrizuje }.ifBlank { "Zákazník" }
                            val docNum = z.cisloZakazky.ifBlank { "ZAK-${z.id.takeLast(6)}" }
                            val dateStr = if (z.timestamp > 0) formatDate(z.timestamp) else "Neuvedeno"
                            val detail = z.stredisko.ifBlank { "Pronájem" }
                            list.add(
                                ContainerHistoryItem(
                                    id = "zak_${z.id}",
                                    type = "Zakázka",
                                    company = companyName,
                                    docNumber = docNum,
                                    date = dateStr,
                                    detail = detail,
                                    timestamp = z.timestamp
                                )
                            )
                        }
                    }

                    // 2. Z Přeprav
                    for (t in allTransports) {
                        val matchesTransport = (desig.isNotBlank() && (
                            t.designation.contains(desig, ignoreCase = true) ||
                            t.containersJson.contains(desig, ignoreCase = true)
                        )) || (sysNum > 0 && t.designation.contains("#$sysNum"))
                        if (matchesTransport) {
                            val companyName = t.company.ifBlank { t.customerCompany }.ifBlank { t.customerName }.ifBlank { "Zákazník" }
                            val docNum = t.cisloDokladuZdroj.ifBlank { "Přeprava ${t.dateText}" }
                            val dateStr = t.dateText.ifBlank { formatDate(t.timestamp) }
                            list.add(
                                ContainerHistoryItem(
                                    id = "prep_${t.id}",
                                    type = "Přeprava",
                                    company = companyName,
                                    docNumber = docNum,
                                    date = dateStr,
                                    detail = t.status.ifBlank { "Realizováno" },
                                    timestamp = t.timestamp
                                )
                            )
                        }
                    }

                    // 3. Z Faktur
                    for (f in allFaktury) {
                        if (desig.isNotBlank() && (f.oznaceniKontejneru.equals(desig, ignoreCase = true) || f.text.contains(desig, ignoreCase = true))) {
                            val companyName = f.firma.ifBlank { f.jmeno }.ifBlank { "Odběratel" }
                            val docNum = "Faktura č. ${f.cislo}"
                            val dateStr = f.datumVystaveni.ifBlank { formatDate(f.timestamp) }
                            val detail = if (f.obdobiPronajmu.isNotBlank()) "Pronájem: ${f.obdobiPronajmu}" else f.ucelFaktury
                            list.add(
                                ContainerHistoryItem(
                                    id = "fak_${f.id}",
                                    type = "Faktura",
                                    company = companyName,
                                    docNumber = docNum,
                                    date = dateStr,
                                    detail = detail,
                                    timestamp = f.timestamp
                                )
                            )
                        }
                    }

                    // 4. Pokud má kontejner evidovaného zákazníka přímo v kartě
                    if (currentContainer.customerCompany.isNotBlank() && list.none { it.company.equals(currentContainer.customerCompany, ignoreCase = true) }) {
                        list.add(
                            ContainerHistoryItem(
                                id = "cur_cust",
                                type = "Evidovaný nájemce",
                                company = currentContainer.customerCompany,
                                docNumber = "Evidováno v kartě",
                                date = formatDate(currentContainer.lastModified),
                                detail = currentContainer.status,
                                timestamp = currentContainer.lastModified
                            )
                        )
                    }

                    list.sortedByDescending { it.timestamp }
                }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF0369A1),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Historie",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0C4A6E)
                                )
                            }
                            Surface(
                                color = Color(0xFF0284C7),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${containerHistory.size} záznamů",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (containerHistory.isEmpty()) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Žádné záznamy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            containerHistory.forEach { entry ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = entry.company,
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF0F172A),
                                                modifier = Modifier.weight(1f).padding(end = 6.dp)
                                            )
                                            Surface(
                                                color = when (entry.type) {
                                                    "Zakázka" -> Color(0xFFE0F2FE)
                                                    "Přeprava" -> Color(0xFFFEF3C7)
                                                    else -> Color(0xFFDCFCE7)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = entry.type,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = entry.docNumber,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0369A1)
                                            )
                                            Text(
                                                text = entry.date,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                        if (entry.detail.isNotBlank()) {
                                            Text(
                                                text = entry.detail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("SMAZAT")
                }
            }
        }
    }

    if (showDeleteDialog && container != null) {
        DoubleConfirmDeleteDialog(
            itemTitle = "Kontejner ${container?.designation ?: ""}",
            itemDescription = "Typ: ${container?.type ?: ""} • Stav: ${container?.status ?: ""}",
            onDismiss = { showDeleteDialog = false },
            onConfirmedDelete = {
                isDeleted = true
                viewModel.deleteContainer(container!!)
                showDeleteDialog = false
                onBack()
            }
        )
    }

    if (showImageZoom != null) {
        Dialog(onDismissRequest = { showImageZoom = null }) {
            ZoomableImage(imageUri = showImageZoom!!, onEditClick = {
                val uri = showImageZoom
                showImageZoom = null
                editingImageUri = uri
            }, onCloseClick = { showImageZoom = null })
        }
    }
}

@Composable
fun StatusOption(modifier: Modifier = Modifier, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color else color.copy(alpha = 0.5f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(2.dp).height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                label, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

fun shareRezervacePdf(context: android.content.Context, container: ContainerCard) {
    val document = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val paint = android.graphics.Paint()
    val paintBold = android.graphics.Paint()
    val paintBlue = android.graphics.Paint()
    val paintBlueBold = android.graphics.Paint()
    
    val blueColor = android.graphics.Color.parseColor("#000080")
    
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paintBold.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paintBlue.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paintBlue.color = blueColor
    paintBlueBold.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paintBlueBold.color = blueColor
    
    // Header
    paintBold.textSize = 14f
    canvas.drawText("POTVRZENÍ O REZERVACI", 30f, 45f, paintBold)
    paintBlueBold.textSize = 12f
    canvas.drawText("PŘÍLOHA K REZERVACI", 400f, 45f, paintBlueBold)
    
    // Draw outer boxes
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawRect(30f, 50f, 565f, 210f, paint) // main border
    canvas.drawLine(297f, 50f, 297f, 210f, paint) // center split
    paint.style = android.graphics.Paint.Style.FILL

    // Dodavatel & Odberatel label
    paintBlue.textSize = 8f
    canvas.drawText("Dodavatel:", 35f, 62f, paintBlue)
    paintBlue.textSize = 8f
    canvas.drawText("Odběratel:", 302f, 62f, paintBlue)
    
    // Logo
    val logoX = 35f
    val logoY = 70f
    val logoW = 95f
    val logoH = 95f

    val logoBgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0B1B3D")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(logoX, logoY, logoX + logoW, logoY + logoH, logoBgPaint)

    val logoWhitePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val logoLinePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // Draw the house/container outline
    val path = android.graphics.Path()
    val cx = logoX + logoW / 2f
    val cy = logoY + 12f
    path.moveTo(cx, cy)
    path.lineTo(logoX + 20f, cy + 18f)
    path.lineTo(logoX + 20f, cy + 45f)
    path.moveTo(cx, cy)
    path.lineTo(logoX + logoW - 20f, cy + 18f)
    path.lineTo(logoX + logoW - 20f, cy + 45f)
    // Draw the inner walls
    path.moveTo(logoX + 32f, cy + 12f)
    path.lineTo(logoX + 32f, cy + 45f)
    path.moveTo(logoX + logoW - 32f, cy + 12f)
    path.lineTo(logoX + logoW - 32f, cy + 45f)
    canvas.drawPath(path, logoLinePaint)

    // Draw texts
    logoWhitePaint.textSize = 12f
    canvas.drawText("KONTEJNERY", logoX + logoW / 2f, logoY + 70f, logoWhitePaint)
    canvas.drawText("OSTRAVA", logoX + logoW / 2f, logoY + 82f, logoWhitePaint)

    logoWhitePaint.textSize = 5f
    logoWhitePaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    canvas.drawText("PRODEJ | PRONÁJEM | PŘEPRAVA", logoX + logoW / 2f, logoY + 91f, logoWhitePaint)
    // Dodavatel texts
    var dx = 135f
    var dy = 85f
    paintBold.textSize = 10f
    canvas.drawText("Kontejnery Ostrava s.r.o.", dx, dy, paintBold)
    paint.textSize = 10f
    dy += 12f
    canvas.drawText("Pavlovova 2701/50", dx, dy, paint)
    dy += 12f
    canvas.drawText("700 30 Ostrava - Jih", dx, dy, paint)
    dy += 16f
    paintBlue.textSize = 9f
    canvas.drawText("IČ: 27771164", dx, dy, paintBlue)
    dy += 12f
    canvas.drawText("DIČ: CZ27771164", dx, dy, paintBlue)
    paint.textSize = 9f
    dy += 12f
    canvas.drawText("Telefon: +420 733 609 813", dx, dy, paint)
    dy += 12f
    canvas.drawText("Mobil: +420 608 813 484", dx, dy, paint)
    dy += 12f
    canvas.drawText("E-mail: info@kontejnery-ostrava.cz", dx, dy, paint)
    dy += 12f
    canvas.drawText("www.kontejnery-ostrava.cz", dx, dy, paint)
    
    // Odběratel texts
    var ox = 302f
    var oy = 85f
    paintBold.textSize = 11f
    canvas.drawText(container.reservedName, ox, oy, paintBold)
    oy += 14f
    paint.textSize = 10f
    canvas.drawText(container.reservedPhone, ox, oy, paint)
    oy += 14f
    canvas.drawText(container.reservedEmail, ox, oy, paint)
    
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    val vystaveno = sdf.format(java.util.Date())
    val rezDo = container.reservedUntil?.let { sdf.format(java.util.Date(it)) } ?: "Neurčeno"
    
    oy += 20f
    paintBold.textSize = 10f
    canvas.drawText("Rezervace do: $rezDo", ox, oy, paintBold)

    // Sub-box bottom Dodavatel
    paintBlueBold.textSize = 9f
    canvas.drawText("Doklad č.:", 35f, 192f, paintBlueBold)
    canvas.drawText("Vystaveno:", 35f, 204f, paintBlueBold)
    
    // Draw the 1x2 box for values
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(140f, 182f, 290f, 208f, paint)
    canvas.drawLine(140f, 195f, 290f, 195f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    
    val cisloDoc = container.timestamp.toString().takeLast(8)
    
    paint.textSize = 9f
    // Align text securely inside the box
    canvas.drawText(cisloDoc, 145f, 192f, paint)
    canvas.drawText(vystaveno, 145f, 204f, paint)
    
    // Specifikace 
    paint.color = android.graphics.Color.parseColor("#F0F0F0")
    canvas.drawRect(30f, 220f, 565f, 238f, paint)
    paint.color = android.graphics.Color.BLACK
    paintBold.textSize = 10f
    canvas.drawText("Specifikace rezervovaného kontejneru:", 35f, 232f, paintBold)
    
    paintBold.textSize = 11f
    canvas.drawText("1 ks     Kontejner ${container.type} ${container.designation}", 35f, 255f, paintBold)
    
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(30f, 265f, 565f, 265f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    
    // Depo section
    var ty = 285f
    paintBlueBold.textSize = 10f
    canvas.drawText("Depo : ${container.depo}", 35f, ty, paintBlueBold)
    ty += 14f
    canvas.drawText("CN nezahrnuje naložení kontejneru, ani přepravu.", 35f, ty, paintBlueBold)
    
    ty += 35f
    paintBold.color = android.graphics.Color.BLACK
    paintBold.textSize = 13f
    canvas.drawText("Osobně zastupuji kupujícího a potvrzuji rezervaci kontejneru", 35f, ty, paintBold)
    
    ty += 25f
    paintBold.textSize = 11f
    canvas.drawText("Jméno: ..................................................................................................................", 35f, ty, paintBold)
    ty += 20f
    canvas.drawText("Adresa: .................................................................................................................", 35f, ty, paintBold)
    ty += 20f
    canvas.drawText("Telefon: ................................................................................................................", 35f, ty, paintBold)
    ty += 20f
    canvas.drawText("E-mail: ..................................................................................................................", 35f, ty, paintBold)
    
    ty += 35f
    paint.textSize = 9f
    paint.color = android.graphics.Color.BLACK
    canvas.drawText("1. Odběratel prohlašuje, že kontejner(y) zkontroloval osobně nebo z fotodokumentace a je srozuměn se stavem.", 35f, ty, paint)
    ty += 12f
    canvas.drawText("2. Zákazník rezervuje kontejner v technickém stavu odpovídajícím jeho stáří a opotřebení.", 35f, ty, paint)
    ty += 12f
    canvas.drawText("3. Po dobu rezervace je kontejner blokován na daném depu a připraven případně k odběru.", 35f, ty, paint)
    
    ty += 25f
    paint.textSize = 10f
    if (container.type.contains("40")) {
        canvas.drawText("Kontejner 40HC - délka 12m", 35f, ty, paint)
        ty += 12f
        canvas.drawText("Vnější rozměry /d/š/v/ 12.192 x 2438 x 2891", 35f, ty, paint)
    } else if (container.type.contains("20")) {
        canvas.drawText("Kontejner 20DV - délka 6m", 35f, ty, paint)
        ty += 12f
        canvas.drawText("Vnější rozměry /d/š/v/ 6058 x 2438 x 2591", 35f, ty, paint)
        ty += 12f
        canvas.drawText("Vnitřní rozměry /d/š/v/ 5898 x 2350 x 2390", 35f, ty, paint)
    }
    
    ty = 680f
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(35f, ty, 255f, ty + 80f, paint)
    canvas.drawRect(340f, ty, 560f, ty + 80f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paintBold.textSize = 10f
    canvas.drawText("Převzal podpis:", 40f, ty + 12f, paintBold)
    canvas.drawText("Předal podpis:", 345f, ty + 12f, paintBold)
    paint.textSize = 10f
    canvas.drawText("Kontejnery Ostrava, s.r.o.", 345f, ty + 26f, paint)
    
    paintBold.textSize = 10f
    canvas.drawText("Pohodové zázemí za zvýhodněné ceny !!", 35f, 790f, paintBold)
    canvas.drawText("www.kontejnery-ostrava.cz", 410f, 790f, paintBold)

    document.finishPage(page)
    
    val file = java.io.File(context.cacheDir, "Rezervace_${container.designation}.pdf")
    document.writeTo(java.io.FileOutputStream(file))
    document.close()
    
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Potvrzení rezervace ${container.designation}")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Sdílet potvrzení rezervace"))
}

fun shareProdejPdf(context: android.content.Context, container: ContainerCard) {
    val document = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val paint = android.graphics.Paint()
    val paintBold = android.graphics.Paint()
    val paintBlue = android.graphics.Paint()
    val paintBlueBold = android.graphics.Paint()
    
    val blueColor = android.graphics.Color.parseColor("#000080")
    
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paintBold.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paintBlue.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paintBlue.color = blueColor
    paintBlueBold.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paintBlueBold.color = blueColor
    
    // Header
    paintBold.textSize = 16f
    canvas.drawText("PŘEDÁVACÍ PROTOKOL", 30f, 45f, paintBold)
    val cisloDoc = container.timestamp.toString().takeLast(8)
    paintBlueBold.textSize = 12f
    canvas.drawText("DODACÍ LIST K FAKTUŘE č. $cisloDoc", 350f, 45f, paintBlueBold)
    
    // Draw outer boxes
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawRect(30f, 50f, 565f, 210f, paint) // main border
    canvas.drawLine(297f, 50f, 297f, 210f, paint) // center split
    paint.style = android.graphics.Paint.Style.FILL

    // Dodavatel & Odberatel label
    paintBlue.textSize = 8f
    canvas.drawText("Dodavatel:", 35f, 62f, paintBlue)
    paintBlue.textSize = 8f
    canvas.drawText("Odběratel:", 302f, 62f, paintBlue)
    
    // Logo
    val logoX = 35f
    val logoY = 70f
    val logoW = 95f
    val logoH = 95f

    val logoBgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0B1B3D")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(logoX, logoY, logoX + logoW, logoY + logoH, logoBgPaint)

    val logoWhitePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val logoLinePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // Draw the house/container outline
    val path = android.graphics.Path()
    val cx = logoX + logoW / 2f
    val cy = logoY + 12f
    path.moveTo(cx, cy)
    path.lineTo(logoX + 20f, cy + 18f)
    path.lineTo(logoX + 20f, cy + 45f)
    path.moveTo(cx, cy)
    path.lineTo(logoX + logoW - 20f, cy + 18f)
    path.lineTo(logoX + logoW - 20f, cy + 45f)
    // Draw the inner walls
    path.moveTo(logoX + 32f, cy + 12f)
    path.lineTo(logoX + 32f, cy + 45f)
    path.moveTo(logoX + logoW - 32f, cy + 12f)
    path.lineTo(logoX + logoW - 32f, cy + 45f)
    canvas.drawPath(path, logoLinePaint)

    // Draw texts
    logoWhitePaint.textSize = 12f
    canvas.drawText("KONTEJNERY", logoX + logoW / 2f, logoY + 70f, logoWhitePaint)
    canvas.drawText("OSTRAVA", logoX + logoW / 2f, logoY + 82f, logoWhitePaint)

    logoWhitePaint.textSize = 5f
    logoWhitePaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    canvas.drawText("PRODEJ | PRONÁJEM | PŘEPRAVA", logoX + logoW / 2f, logoY + 91f, logoWhitePaint)
    // Dodavatel texts
    var dx = 135f
    var dy = 85f
    paintBold.textSize = 10f
    canvas.drawText("Kontejnery Ostrava s.r.o.", dx, dy, paintBold)
    paint.textSize = 10f
    dy += 12f
    canvas.drawText("Pavlovova 2701/50", dx, dy, paint)
    dy += 12f
    canvas.drawText("700 30 Ostrava - Jih", dx, dy, paint)
    dy += 16f
    paintBlue.textSize = 9f
    canvas.drawText("IČ: 27771164", dx, dy, paintBlue)
    dy += 12f
    canvas.drawText("DIČ: CZ27771164", dx, dy, paintBlue)
    paint.textSize = 9f
    dy += 12f
    canvas.drawText("Telefon: +420 733 609 813", dx, dy, paint)
    dy += 12f
    canvas.drawText("Mobil: +420 608 813 484", dx, dy, paint)
    dy += 12f
    canvas.drawText("E-mail: info@kontejnery-ostrava.cz", dx, dy, paint)
    dy += 12f
    canvas.drawText("www.kontejnery-ostrava.cz", dx, dy, paint)
    
    // Odběratel texts
    var ox = 302f
    var oy = 85f
    paintBold.textSize = 11f
    if (container.customerCompany.isNotBlank()) {
        canvas.drawText(container.customerCompany, ox, oy, paintBold)
        oy += 14f
    }
    paint.textSize = 10f
    if (container.soldCustomerName.isNotBlank()) {
        canvas.drawText(container.soldCustomerName, ox, oy, paintBold)
        oy += 14f
    }
    if (container.soldCustomerId.isNotBlank()) {
        canvas.drawText("IČO / Zákazník ID: ${container.soldCustomerId}", ox, oy, paint)
        oy += 14f
    }
    canvas.drawText(container.soldEmail, ox, oy, paint)
    oy += 14f
    canvas.drawText(container.soldPhone, ox, oy, paint)
    oy += 14f
    if (container.soldDelivery.isNotBlank()) {
        canvas.drawText("Doprava: ${container.soldDelivery}", ox, oy, paint)
    }
    
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    val vystaveno = sdf.format(java.util.Date())
    
    // Sub-box bottom Dodavatel
    paintBlueBold.textSize = 9f
    canvas.drawText("DODACÍ LIST č.", 35f, 192f, paintBlueBold)
    canvas.drawText("Vystaveno:", 35f, 204f, paint)
    
    // Draw the 1x2 box for values
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(140f, 182f, 290f, 208f, paint)
    canvas.drawLine(140f, 195f, 290f, 195f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    
    paint.textSize = 9f
    canvas.drawText(cisloDoc, 145f, 192f, paint)
    canvas.drawText(vystaveno, 145f, 204f, paint)
    
    // Specifikace 
    paint.color = android.graphics.Color.parseColor("#F0F0F0")
    canvas.drawRect(30f, 220f, 565f, 238f, paint)
    paint.color = android.graphics.Color.BLACK
    paintBold.textSize = 11f
    canvas.drawText("Specifikace prodaného a předaného kontejneru:", 35f, 233f, paintBold)
    
    paintBold.textSize = 11f
    val desc = "Lodní kontejner ${container.type} č.${container.designation} barva ${container.color}"
    canvas.drawText(desc, 35f, 255f, paintBold)
    canvas.drawText("1   ks", 520f, 255f, paintBold)
    canvas.drawText("Naložení", 35f, 270f, paintBold)
    canvas.drawText("1", 520f, 270f, paintBold)
    
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(30f, 280f, 565f, 280f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    
    var ty = 300f
    paintBlueBold.textSize = 10f
    canvas.drawText("1. Kupující prohlašuje, že si kontejner(y) před úhradou této faktury řádně prohlédl.", 35f, ty, paintBlueBold)
    ty += 14f
    canvas.drawText("2. Zkontroloval jeho (jejich) stav osobně nebo vlastními odborníky a je s jeho stavem a", 35f, ty, paintBlueBold)
    ty += 14f
    canvas.drawText("opotřebením srozumněn.", 35f, ty, paintBlueBold)
    ty += 14f
    canvas.drawText("3. Kontejner v tomto stavu jak leží a běží kupuje bez výhrad.", 35f, ty, paintBlueBold)
    ty += 14f
    canvas.drawText("4. Předáním kontejneru(ů) je považováno za splnění dodávky a veškerá nebezpečí a škody", 35f, ty, paintBlueBold)
    ty += 14f
    canvas.drawText("na kontejneru přechází z prodávajícího na kupujícího.", 35f, ty, paintBlueBold)
    
    ty += 40f
    paintBold.color = android.graphics.Color.BLACK
    paintBold.textSize = 14f
    canvas.drawText("Osobně zastupuji kupujícího a potvrzuji převzetí kontejneru(ů)", 35f, ty, paintBold)
    
    ty += 30f
    paintBold.textSize = 12f
    canvas.drawText("Převzal - hůlkově......  ${container.reservedName.ifBlank { "......................................................................" }}", 35f, ty, paintBold)
    ty += 25f
    canvas.drawText("Adresa : ......................  ${container.customerCompany.ifBlank { "......................................................................" }}", 35f, ty, paintBold)
    ty += 25f
    canvas.drawText("Číslo OP: ........................  ........................................................................................", 35f, ty, paintBold)
    ty += 25f
    canvas.drawText("Místo předání : ..........  ........................................................................................", 35f, ty, paintBold)
    
    ty += 40f
    paint.textSize = 10f
    paint.color = android.graphics.Color.BLACK
    canvas.drawText("1. Odběratel prohlašuje, že kontejner(y) zkontroloval osobně nebo vlastními odborníky a se stavem a", 35f, ty, paint)
    ty += 12f
    canvas.drawText("opotřebením je srozuměn.", 35f, ty, paint)
    ty += 12f
    canvas.drawText("2. Odběratel kupuje kontejner v technickém stavu odpovídajícím jeho stáří a opotřebení, s nímž", 35f, ty, paint)
    ty += 12f
    canvas.drawText("se seznámil při prohlídce. Kontejner v tomto stavu jak leží a běží kupuje bez výhrad.", 35f, ty, paint)
    ty += 12f
    canvas.drawText("3. Předáním kontejneru(ů) je považováno za splnění dodávky a veškerá nebezpečí a škody na kontejneru", 35f, ty, paint)
    ty += 12f
    canvas.drawText("přechází z prodávajícího na odběratele.", 35f, ty, paint)
    
    ty += 30f
    if (container.type.contains("40")) {
        canvas.drawText("Kontejner 40HC - délka 12m", 35f, ty, paint)
        canvas.drawText("Vnější rozměry /d/š/v/ 12.192 x 2438 x 2891", 35f, ty + 12f, paint)
    } else if (container.type.contains("20")) {
        canvas.drawText("Kontejner 20DV - délka 6m", 35f, ty, paint)
        canvas.drawText("Vnější rozměry /d/š/v/ 6058 x 2438 x 2591", 35f, ty + 12f, paint)
        canvas.drawText("Vnitřní rozměry /d/š/v/ 5898 x 2350 x 2390", 35f, ty + 24f, paint)
    }
    
    ty = 680f
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(35f, ty, 255f, ty + 80f, paint)
    canvas.drawRect(340f, ty, 560f, ty + 80f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paintBold.textSize = 11f
    canvas.drawText("Převzal podpis:", 40f, ty + 15f, paintBold)
    canvas.drawText("Předal podpis:", 345f, ty + 15f, paintBold)
    paint.textSize = 11f
    canvas.drawText("Kontejnery Ostrava, s.r.o.", 345f, ty + 35f, paint)
    
    paintBold.textSize = 10f
    canvas.drawText("Pohodové zázemí za zvýhodněné ceny !!!", 35f, 790f, paintBold)
    canvas.drawText("www.kontejnery-ostrava.cz", 410f, 790f, paintBold)

    document.finishPage(page)
    
    val file = java.io.File(context.cacheDir, "Predavaci_protokol_${container.designation}.pdf")
    document.writeTo(java.io.FileOutputStream(file))
    document.close()
    
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Předávací protokol ${container.designation}")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Sdílet Předávací protokol"))
}
