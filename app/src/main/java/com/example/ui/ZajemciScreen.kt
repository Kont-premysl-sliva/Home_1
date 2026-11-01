package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.api.analyzeVizitka
import com.example.data.ZajemceRecord
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun uriToBitmap(context: Context, uriString: String): Bitmap? {
    if (uriString.isBlank()) return null
    return try {
        if (uriString.trim().startsWith("data:image")) {
            val decoded = decodeBitmapFromAnyUri(uriString)
            if (decoded != null) return decoded.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (uriString.startsWith("/") || !uriString.startsWith("content://")) {
            val file = File(uriString.removePrefix("file://"))
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) return bmp.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
        val uri = Uri.parse(uriString)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            bmp.copy(Bitmap.Config.ARGB_8888, true)
        }
    } catch (e: Exception) {
        try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uriString))
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (_: Exception) {
            null
        }
    }
}

fun parseExtractedVizitkaJson(jsonStr: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    if (jsonStr.isBlank()) return map
    try {
        var str = jsonStr.trim()
        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            str = str.substring(firstBrace, lastBrace + 1)
        }
        val json = JSONObject(str)
        fun cleanVal(vararg keys: String): String {
            for (k in keys) {
                if (json.has(k) && !json.isNull(k)) {
                    val v = json.optString(k, "").trim()
                    if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) {
                        return v
                    }
                }
            }
            return ""
        }

        val jmeno = cleanVal("jmeno", "name", "fullName", "personName", "kontakt", "osoba")
        val firma = cleanVal("firma", "company", "companyName", "nazevFirmy", "spolecnost", "podnik")
        val telefon = cleanVal("telefon", "phone", "phoneNumber", "tel", "mobil", "mobile", "gsm")
        val email = cleanVal("email", "e-mail", "mail", "emailAddress")
        val ic = cleanVal("ic", "ico", "regNumber", "taxId", "soldCustomerId")
        val dic = cleanVal("dic", "vatNumber", "vatId")
        val ulice = cleanVal("ulice", "street", "addressLine", "soldDelivery")
        val mesto = cleanVal("mesto", "city")
        val psc = cleanVal("psc", "zip", "postalCode")
        val spz = cleanVal("spz", "rz", "plate")
        val designation = cleanVal("designation", "container", "cisloKontejneru")
        val datum = cleanVal("datum", "date")
        val poznamka = cleanVal("poznamka", "note", "address", "adresa", "position", "funkce", "web", "site")

        if (jmeno.isNotBlank()) map["jmeno"] = jmeno
        if (firma.isNotBlank()) map["firma"] = firma
        if (telefon.isNotBlank()) map["telefon"] = telefon
        if (email.isNotBlank()) map["email"] = email
        if (ic.isNotBlank()) map["ic"] = ic
        if (dic.isNotBlank()) map["dic"] = dic
        if (ulice.isNotBlank()) map["ulice"] = ulice
        if (mesto.isNotBlank()) map["mesto"] = mesto
        if (psc.isNotBlank()) map["psc"] = psc
        if (spz.isNotBlank()) map["spz"] = spz
        if (designation.isNotBlank()) map["designation"] = designation
        if (datum.isNotBlank()) map["datum"] = datum
        if (poznamka.isNotBlank()) map["poznamka"] = poznamka
    } catch (e: Exception) {
        android.util.Log.e("GeminiVizitka", "Error parsing vizitka json: $jsonStr", e)
    }
    return map
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZajemciScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToNabidky: () -> Unit = {}
) {
    val context = LocalContext.current
    val allZajemci by viewModel.zajemci.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val activeDepo = currentDepo ?: "Depo 1"

    var depoFilterMode by remember { mutableStateOf("VŠE") } // "VŠE", "Depo 1", "Depo 2"
    var statusFilterTab by remember { mutableStateOf("AKTIVNÍ") } // "AKTIVNÍ", "VYŘÍZENO", "ZRUŠENÉ", "VŠE"
    var searchQuery by remember { mutableStateOf("") }

    var editingZajemce by remember { mutableStateOf<ZajemceRecord?>(null) }
    var showAddModal by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentDepo) {
        if (currentDepo == "Depo 1" || currentDepo == "Depo 2") {
            depoFilterMode = currentDepo ?: "VŠE"
        }
    }

    val filteredList = remember(allZajemci, depoFilterMode, statusFilterTab, searchQuery) {
        allZajemci.filter { z ->
            if (z.isDeleted) return@filter false
            val zd = z.depo.ifBlank { "Depo 1" }
            val matchesDepo = when (depoFilterMode) {
                "Depo 1" -> zd == "Depo 1"
                "Depo 2" -> zd == "Depo 2"
                else -> true
            }
            if (!matchesDepo) return@filter false

            val st = z.status.uppercase()
            val matchesStatus = when (statusFilterTab) {
                "AKTIVNÍ" -> !st.contains("VYŘÍZENO") && !st.contains("ZRUŠENO")
                "VYŘÍZENO" -> st.contains("VYŘÍZENO") || st.contains("NABÍDKA") || st.contains("ZAKÁZKA")
                "ZRUŠENÉ" -> st.contains("ZRUŠENO")
                else -> true
            }
            if (!matchesStatus) return@filter false

            if (searchQuery.isBlank()) return@filter true
            val q = searchQuery.trim().lowercase()
            z.jmeno.lowercase().contains(q) ||
                    z.firma.lowercase().contains(q) ||
                    z.telefon.lowercase().contains(q) ||
                    z.email.lowercase().contains(q) ||
                    z.typKontejneru.lowercase().contains(q) ||
                    z.poznamka.lowercase().contains(q) ||
                    z.zamer.lowercase().contains(q) ||
                    z.cisloZajemce.lowercase().contains(q)
        }.sortedByDescending { it.timestamp }
    }

    val activeCount = remember(allZajemci, depoFilterMode) {
        allZajemci.count { z ->
            !z.isDeleted &&
                    (depoFilterMode == "VŠE" || z.depo.ifBlank { "Depo 1" } == depoFilterMode) &&
                    !z.status.uppercase().contains("VYŘÍZENO") && !z.status.uppercase().contains("ZRUŠENO")
        }
    }

    Scaffold(
        topBar = {
            // Jeden úzký barevný proužek nahoře (max 1 cm, žádné podtexty)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(Color(0xFF1B5E8B))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ZÁJEMCI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    // Zelené tlačítko + Nový
                    Button(
                        onClick = {
                            editingZajemce = null
                            showAddModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("+ Nový", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF4F6F9))
        ) {
            // Depo selector & Centered Search Field
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DEPO:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E8B)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("VŠE", "Depo 1", "Depo 2").forEach { depo ->
                            val sel = depoFilterMode == depo
                            FilterChip(
                                selected = sel,
                                onClick = { depoFilterMode = depo },
                                label = { Text(depo, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1B5E8B),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFEBF3FA),
                                    labelColor = Color(0xFF1B5E8B)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Centered Search Field (Text zarovnaný přesně na střed buňky, vertikálně i horizontálně, bez oříznutí)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF64748B)
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F172A)
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Hledat zájemce...",
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Smazat text",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Status Tabs (Obdélníková tlačítka s lehce zaoblenými rohy jako u Depo 1/Depo 2)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        "AKTIVNÍ" to "Aktivní ($activeCount)",
                        "VYŘÍZENO" to "Vyřízeno",
                        "ZRUŠENÉ" to "Zrušené",
                        "VŠE" to "Vše"
                    )
                    tabs.forEach { (key, label) ->
                        val selected = statusFilterTab == key
                        FilterChip(
                            selected = selected,
                            onClick = { statusFilterTab = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp), // Obdélníková s lehce zaoblenými rohy
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1B5E8B),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFE2E8F0),
                                labelColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFCBD5E1))

            // List of Zájemci
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Žádné záznamy",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.id }) { zajemce ->
                        ZajemceCard(
                            zajemce = zajemce,
                            onEdit = {
                                editingZajemce = zajemce
                                showAddModal = true
                            },
                            onDelete = { deletingId = zajemce.id },
                            onStatusChange = { newStatus ->
                                viewModel.updateZajemceStatus(zajemce.id, newStatus)
                                Toast.makeText(context, "Status změněn: $newStatus", Toast.LENGTH_SHORT).show()
                            },
                            onConvertToNabidka = {
                                val nid = viewModel.convertZajemceToNabidka(zajemce)
                                Toast.makeText(context, "Vytvořena nová Nabidka! Status zájemce: POSLÁNA NABÍDKA", Toast.LENGTH_LONG).show()
                                onNavigateToNabidky()
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Add / Edit
    if (showAddModal) {
        ZajemceModalDialog(
            zajemce = editingZajemce,
            defaultDepo = if (activeDepo.contains("2")) "Depo 2" else "Depo 1",
            onDismiss = {
                showAddModal = false
                editingZajemce = null
            },
            onDelete = if (editingZajemce != null) {
                {
                    val toDelete = editingZajemce?.id
                    showAddModal = false
                    editingZajemce = null
                    if (!toDelete.isNullOrBlank()) {
                        deletingId = toDelete
                    }
                }
            } else null,
            onSave = { record ->
                viewModel.saveZajemce(
                    id = record.id,
                    jmeno = record.jmeno,
                    firma = record.firma,
                    telefon = record.telefon,
                    email = record.email,
                    depo = record.depo,
                    typKontejneru = record.typKontejneru,
                    zamer = record.zamer,
                    rozpocet = record.rozpocet,
                    status = record.status,
                    poznamka = record.poznamka,
                    vizitkaUri = record.vizitkaUri
                )
                showAddModal = false
                editingZajemce = null
                Toast.makeText(context, "Zájemce uložen", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete confirmation
    if (deletingId != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Smazat zájemce?", fontWeight = FontWeight.Bold) },
            text = { Text("Opravdu chcete smazat tohoto zájemce z databáze?") },
            confirmButton = {
                Button(
                    onClick = {
                        deletingId?.let { viewModel.deleteZajemce(it) }
                        deletingId = null
                        Toast.makeText(context, "Zájemce smazán", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Smazat", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text("Zrušit")
                }
            }
        )
    }
}

@Composable
fun ContainerTypeIllustration(
    typKontejneru: String,
    depo: String,
    vizitkaUri: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        if (vizitkaUri.isNotBlank()) {
            AsyncImage(
                model = resolveCoilImageModel(vizitkaUri),
                contentDescription = "Vizitka zájemce",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "📷 VIZITKA",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color(0xFF16A34A), RoundedCornerShape(bottomEnd = 6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        } else {
            val typLower = typKontejneru.lowercase()
            if (typLower.contains("ob")) {
                // OB 6 - Obytný 6m
                Image(
                    painter = painterResource(id = R.drawable.ic_obytny_kontejner),
                    contentDescription = "Obytný kontejner",
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                val labelText = when {
                    typLower.contains("12") && (typLower.contains("new") || typLower.contains("nov")) -> "SK 12 New"
                    typLower.contains("12") -> "SK 12"
                    typLower.contains("6") && (typLower.contains("new") || typLower.contains("nov")) -> "SK 6 New"
                    else -> "SK 6"
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_skladovy_kontejner),
                        contentDescription = "Skladový lodní kontejner",
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        color = Color(0xCC0F172A),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = labelText,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        // Poznámka: Text "Depo 1" dole v rámečku byl na přání uživatele zrušen
    }
}

@Composable
fun ZajemceCard(
    zajemce: ZajemceRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    onConvertToNabidka: () -> Unit
) {
    val formattedDateShort = remember(zajemce.timestamp) {
        if (zajemce.timestamp > 0) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(zajemce.timestamp))
        } else "Bez data"
    }
    val formattedTime = remember(zajemce.timestamp) {
        if (zajemce.timestamp > 0) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(zajemce.timestamp))
        } else ""
    }
    val dayOfWeek = remember(formattedDateShort) { getCzechDayOfWeek(formattedDateShort) }

    val isDepo2 = zajemce.depo == "Depo 2"
    val cardBgColor = if (isDepo2) Color(0xFFFCF5E8) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDepo2) Color(0xFF8C6239) else Color(0xFF1B5E8B)
    val headerBgColor = if (isDepo2) Color(0xFF5D5342) else Color(0xFF1B5E8B)

    // Zkrácená karta zájemce - výška snížena o cca 0.8 cm, zrušen text Nákup DEPO 1, obytný kontejner má svou fotografii
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp,
            pressedElevation = 2.dp,
            hoveredElevation = 7.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
            contentColor = Color(0xFF0F172A)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Horní pruh karty: Agenda ikona "Z" + datum + den v týdnu + status badge vpravo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgendaLetterIconBadge(
                        letter = "Z",
                        textColor = headerBgColor
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (formattedTime.isNotBlank()) "$formattedDateShort $formattedTime" else formattedDateShort,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (dayOfWeek.isNotBlank()) {
                            Text(
                                text = "($dayOfWeek)",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                StatusDropdownBadge(
                    currentStatus = zajemce.status.ifBlank { "AKTIVNÍ" },
                    onStatusSelected = onStatusChange,
                    shape = RoundedCornerShape(6.dp)
                )
            }

            // 2. Druhý řádek zkrácené karty:
            // VLEVO: Foto/náhled ve formátu 4x4 (56.dp)
            // VPRAVO: Informace o zákazníkovi + tlačítko na smazání
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isObytny = zajemce.typKontejneru.contains("ob", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(68.dp) // Zvětšení o 20% (z 56.dp na 68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, cardBorderColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (zajemce.vizitkaUri.isNotBlank()) {
                        AsyncImage(
                            model = resolveCoilImageModel(zajemce.vizitkaUri),
                            contentDescription = "Foto vizitky",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color(0xCC0F172A),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                        ) {
                            Text(
                                text = "📷 VIZITKA",
                                color = Color.White,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    } else if (isObytny) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_obytny_kontejner),
                            contentDescription = "Obytný kontejner",
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        ContainerTypeIllustration(
                            typKontejneru = zajemce.typKontejneru,
                            depo = zajemce.depo,
                            vizitkaUri = "",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Sloupec údajů o zákazníkovi
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 1.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Řádek 1: Jméno zákazníka a Firma
                    val rawName = zajemce.jmeno.ifBlank { zajemce.firma.ifBlank { "Zájemce bez jména" } }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = rawName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (zajemce.firma.isNotBlank() && zajemce.jmeno.isNotBlank()) {
                            Surface(
                                color = Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = zajemce.firma,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Řádek 2: Typ kontejneru (SK / OB pill + název)
                    val typText = zajemce.typKontejneru.ifBlank { "OB 6" }
                    val isSklad = typText.contains("sk", ignoreCase = true) || typText.contains("lodní", ignoreCase = true)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (isSklad) Color(0xFFFEF3C7) else Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isSklad) "SK" else "OB",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSklad) Color(0xFFB45309) else Color(0xFF0284C7),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = typText.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF0369A1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Řádek 3: Telefon a Email
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (zajemce.telefon.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFF1B5E8B))
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = zajemce.telefon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0284C7),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (zajemce.email.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFF1B5E8B))
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = zajemce.email,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (zajemce.telefon.isBlank() && zajemce.email.isBlank()) {
                            Text("Bez přímého kontaktu", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    // Poznámka: Řádek 4 "Nákup DEPO 1" byl na přání uživatele kompletně zrušen
                }

                // Tlačítko na úplné smazání karty zájemce přímo z přehledu
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Smazat zájemce",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZajemceModalDialog(
    zajemce: ZajemceRecord?,
    defaultDepo: String,
    onDismiss: () -> Unit,
    onSave: (ZajemceRecord) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var jmeno by remember { mutableStateOf(zajemce?.jmeno ?: "") }
    var firma by remember { mutableStateOf(zajemce?.firma ?: "") }
    var telefon by remember { mutableStateOf(zajemce?.telefon ?: "") }
    var email by remember { mutableStateOf(zajemce?.email ?: "") }
    var depo by remember { mutableStateOf(zajemce?.depo?.ifBlank { defaultDepo } ?: defaultDepo) }
    var vizitkaUri by remember { mutableStateOf(zajemce?.vizitkaUri ?: "") }
    var poznamka by remember { mutableStateOf(zajemce?.poznamka ?: "") }

    var isExtracting by remember { mutableStateOf(false) }
    var showZoomModal by remember { mutableStateOf(false) }

    // Automatické vytěžení dat z vizitky po vyfocení/výběru fotky
    val executeCardExtraction: (String) -> Unit = { uriStr ->
        coroutineScope.launch {
            isExtracting = true
            try {
                val bmp = uriToBitmap(context, uriStr)
                if (bmp != null) {
                    val rawJson = analyzeVizitka(bmp)
                    val parsed = parseExtractedVizitkaJson(rawJson)
                    val contJson = com.example.api.analyzeContainer(bmp)
                    val contParsed = com.example.data.PhotoDataExtractor.parseJsonFields(contJson)
                    var count = 0
                    if (parsed["jmeno"]?.isNotBlank() == true) {
                        jmeno = parsed["jmeno"]!!
                        count++
                    }
                    if (parsed["firma"]?.isNotBlank() == true) {
                        firma = parsed["firma"]!!
                        count++
                    }
                    if (parsed["telefon"]?.isNotBlank() == true) {
                        telefon = parsed["telefon"]!!
                        count++
                    }
                    if (parsed["email"]?.isNotBlank() == true) {
                        email = parsed["email"]!!
                        count++
                    }
                    val contDesig = contParsed["designation"].orEmpty().ifBlank { parsed["designation"].orEmpty() }
                    val contDesc = contParsed["description"].orEmpty()
                    if (contDesig.isNotBlank() || contDesc.isNotBlank()) {
                        val contNote = listOf(contDesig, contDesc).filter { it.isNotBlank() }.joinToString(" – ")
                        poznamka = if (poznamka.isBlank()) contNote else "$poznamka\n$contNote"
                        count++
                    } else if (parsed["poznamka"]?.isNotBlank() == true) {
                        poznamka = if (poznamka.isBlank()) parsed["poznamka"]!! else "$poznamka\n${parsed["poznamka"]}"
                        count++
                    }

                    if (count > 0) {
                        Toast.makeText(context, "✅ Informace z fotografie byly načteny do políček", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Fotografie načtena, údaje nebyly rozpoznány.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Nepodařilo se načíst fotografii vizitky.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Chyba při vytěžování: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isExtracting = false
            }
        }
    }

    // Photo Picker Launcher for Business Card - ukládá kopii obrázku na disk a vytěží data
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val destFile = File(context.filesDir, "vizitka_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists()) {
                    val savedPath = destFile.absolutePath
                    vizitkaUri = savedPath
                    executeCardExtraction(savedPath)
                } else {
                    val uStr = uri.toString()
                    vizitkaUri = uStr
                    executeCardExtraction(uStr)
                }
            } catch (e: Exception) {
                val uStr = uri.toString()
                vizitkaUri = uStr
                executeCardExtraction(uStr)
            }
        }
    }

    // Multi-select Container Types: Pouze kategorie dle požadavku, ostatní smazány
    val containerOptions = listOf(
        "OB 6 - Obytný 6m",
        "SK 6 - Lodní 6m použitý",
        "SK 6 New - Lodní 6m Nový",
        "SK 12 - Lodní 12m použitý",
        "SK 12 New - Lodní 12 nový"
    )
    var selectedContainerTypes by remember {
        mutableStateOf(
            zajemce?.typKontejneru
                ?.split(", ")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: setOf("OB 6 - Obytný 6m")
        )
    }

    // Multi-select / Radio Záměr (Nákup, Prodej, Pronájem, Jen dotaz)
    val zamerOptions = listOf("Nákup", "Prodej", "Pronájem", "Jen dotaz")
    var selectedZamer by remember {
        mutableStateOf(
            zajemce?.zamer
                ?.split(", ")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: setOf("Nákup")
        )
    }

    // Status Dropdown Options
    val statusOptions = listOf("Aktivní", "Nový", "Poslaná nabídka", "Vyřízeno", "Zrušeno")
    var status by remember { mutableStateOf(zajemce?.status?.ifBlank { "Aktivní" } ?: "Aktivní") }
    var statusExpanded by remember { mutableStateOf(false) }

    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Zájemce",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFF1B5E8B)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorText.isNotEmpty()) {
                    Text(errorText, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // SEKCE VIZITKY / SKENOVÁNÍ KARTY
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Vizitka",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E8B)
                        )

                        if (vizitkaUri.isBlank()) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nahrát", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Náhled vizitky a akce vytěžení / úpravy
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = resolveCoilImageModel(vizitkaUri),
                                    contentDescription = "Fotografie vizitky",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            if (isExtracting) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI vytěžuje kontakty z vizitky...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { executeCardExtraction(vizitkaUri) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        Text("⚡ Vytěžit AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    OutlinedButton(
                                        onClick = { showZoomModal = true },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🔍 Detail", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = { vizitkaUri = "" },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // INFORMACE O ZÁKAZNÍKOVI - HNED POD NASKENOVANOU VIZITKOU
                OutlinedTextField(
                    value = jmeno,
                    onValueChange = { jmeno = it },
                    label = { Text("Jméno") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = firma,
                    onValueChange = { firma = it },
                    label = { Text("Firma") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = telefon,
                    onValueChange = { telefon = it },
                    label = { Text("Telefon") },
                    placeholder = { Text("Telefon") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = poznamka,
                    onValueChange = { poznamka = it },
                    label = { Text("Poznámka") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )

                // Depo Selection
                Column {
                    Text("Depo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E8B))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Depo 1", "Depo 2").forEach { d ->
                            FilterChip(
                                selected = depo == d,
                                onClick = { depo = d },
                                label = { Text(d, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1B5E8B),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Typ Kontejneru Selection - STACKED BUTTONS
                Column {
                    Text(
                        text = "Typ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E8B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        containerOptions.forEach { option ->
                            val isChecked = selectedContainerTypes.contains(option)
                            Surface(
                                onClick = {
                                    selectedContainerTypes = if (isChecked) {
                                        if (selectedContainerTypes.size > 1) selectedContainerTypes - option else selectedContainerTypes
                                    } else {
                                        selectedContainerTypes + option
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) Color(0xFFE0F2FE) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isChecked) Color(0xFF0284C7) else Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedContainerTypes = if (checked == true) {
                                                selectedContainerTypes + option
                                            } else {
                                                if (selectedContainerTypes.size > 1) selectedContainerTypes - option else selectedContainerTypes
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0284C7))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = option,
                                        fontSize = 13.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChecked) Color(0xFF0369A1) else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }
                }

                // Záměr Selection - Kompaktní rozbalovací tlačítko (šetří místo na displeji)
                var zamerDropdownExpanded by remember { mutableStateOf(false) }
                val displayZamer = if (selectedZamer.isEmpty()) "Vybrat" else selectedZamer.joinToString(", ")
                Column {
                    Text(
                        text = "Záměr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E8B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { zamerDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFFFFBEB),
                                contentColor = Color(0xFF92400E)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFD97706)),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = displayZamer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Rozbalit záměr",
                                tint = Color(0xFFD97706)
                            )
                        }
                        DropdownMenu(
                            expanded = zamerDropdownExpanded,
                            onDismissRequest = { zamerDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).background(Color.White)
                        ) {
                            zamerOptions.forEach { zm ->
                                val isChecked = selectedZamer.contains(zm)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedZamer = if (checked == true) {
                                                        selectedZamer + zm
                                                    } else {
                                                        if (selectedZamer.size > 1) selectedZamer - zm else selectedZamer
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFD97706))
                                            )
                                            Text(
                                                text = zm,
                                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChecked) Color(0xFF92400E) else Color(0xFF1E293B)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedZamer = if (isChecked) {
                                            if (selectedZamer.size > 1) selectedZamer - zm else selectedZamer
                                        } else {
                                            selectedZamer + zm
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Status Dropdown Selection
                Column {
                    Text(
                        text = "Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E8B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = status,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Stav") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            statusOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            fontWeight = if (status == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (status == option) Color(0xFF1B5E8B) else Color.Unspecified
                                        )
                                    },
                                    onClick = {
                                        status = option
                                        statusExpanded = false
                                    }
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
                    if (jmeno.isBlank() && firma.isBlank()) {
                        errorText = "Prosím vyplňte jméno nebo název firmy zájemce"
                        return@Button
                    }
                    val finalTypes = selectedContainerTypes.joinToString(", ").ifBlank { "obytný" }
                    val finalZamer = selectedZamer.joinToString(", ").ifBlank { "Nákup" }

                    onSave(
                        ZajemceRecord(
                            id = zajemce?.id ?: "",
                            jmeno = jmeno,
                            firma = firma,
                            telefon = telefon,
                            email = email,
                            depo = depo,
                            typKontejneru = finalTypes,
                            zamer = finalZamer,
                            status = status,
                            poznamka = poznamka,
                            vizitkaUri = vizitkaUri
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
            ) {
                Text("Uložit", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (zajemce != null && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Smazat", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zrušit")
                }
            }
        }
    )

    // Modal na plné přizoomování a úpravu prohlížení vizitky
    if (showZoomModal && vizitkaUri.isNotBlank()) {
        VizitkaZoomDialog(
            vizitkaUri = vizitkaUri,
            onDismiss = { showZoomModal = false }
        )
    }
}

@Composable
fun VizitkaZoomDialog(
    vizitkaUri: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📷 Náhled a úprava vizitky",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B5E8B)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zavřít")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gesta: Stažením/roztažením prstů přizoomujete nebo otočíte obrázek vizitky",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotate ->
                                scale = (scale * zoom).coerceIn(0.5f, 4f)
                                offset += pan
                                rotation += rotate
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = resolveCoilImageModel(vizitkaUri),
                        contentDescription = "Vizitka detail",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotation,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { rotation = (rotation + 90f) % 360f },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("🔄 Otočit 90°", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            rotation = 0f
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("⏪ Obnovit", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E8B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Hotovo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}



