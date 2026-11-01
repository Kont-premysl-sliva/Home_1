package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.material.icons.filled.Share
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RozdilyScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit) {
    val inventories by viewModel.inventories.collectAsState()

    var inventoryAIndex by remember { mutableStateOf<Int?>(null) }
    var inventoryBIndex by remember { mutableStateOf<Int?>(null) }

    var inventoryAExpanded by remember { mutableStateOf(false) }
    var inventoryBExpanded by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        "ROZDÍLY",
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Select A (Older or newer, logically just comparison)
            ExposedDropdownMenuBox(
                expanded = inventoryAExpanded,
                onExpandedChange = { inventoryAExpanded = !inventoryAExpanded }
            ) {
                val label = inventoryAIndex?.let { inventories.getOrNull(it) }?.let { "Inventura ${formatDate(it.timestamp)}" } ?: "Vybrat"
                OutlinedTextField(
                    value = label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Inventura1") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inventoryAExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = inventoryAExpanded,
                    onDismissRequest = { inventoryAExpanded = false }
                ) {
                    inventories.forEachIndexed { index, inv ->
                        DropdownMenuItem(
                            text = { Text(formatDate(inv.timestamp) + " - " + inv.author) },
                            onClick = {
                                inventoryAIndex = index
                                inventoryAExpanded = false
                            }
                        )
                    }
                }
            }

            // Select B
            ExposedDropdownMenuBox(
                expanded = inventoryBExpanded,
                onExpandedChange = { inventoryBExpanded = !inventoryBExpanded }
            ) {
                val label = inventoryBIndex?.let { inventories.getOrNull(it) }?.let { "Inventura ${formatDate(it.timestamp)}" } ?: "Vybrat"
                OutlinedTextField(
                    value = label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Inventura2") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inventoryBExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = inventoryBExpanded,
                    onDismissRequest = { inventoryBExpanded = false }
                ) {
                    inventories.forEachIndexed { index, inv ->
                        DropdownMenuItem(
                            text = { Text(formatDate(inv.timestamp) + " - " + inv.author) },
                            onClick = {
                                inventoryBIndex = index
                                inventoryBExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (inventoryAIndex != null && inventoryBIndex != null) {
                val invA = inventories[inventoryAIndex!!]
                val invB = inventories[inventoryBIndex!!]

                val setA = if (invA.containerDesignations.isBlank()) emptySet() else invA.containerDesignations.split(",").toSet()
                val setB = if (invB.containerDesignations.isBlank()) emptySet() else invB.containerDesignations.split(",").toSet()

                val oldSet = if (invA.timestamp < invB.timestamp) setA else setB
                val newSet = if (invA.timestamp < invB.timestamp) setB else setA

                val leftContainers = oldSet - newSet
                val addedContainers = newSet - oldSet

                Text("Rozdíly", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Chybí", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (leftContainers.isEmpty()) {
                            Text("-")
                        } else {
                            leftContainers.forEach { Text(it) }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Přibylo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (addedContainers.isEmpty()) {
                            Text("-")
                        } else {
                            addedContainers.forEach { Text(it) }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        try {
                            shareRozdilyPdf(context, invA, invB, leftContainers, addedContainers)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Chyba při tvorbě PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("PDF")
                }
            }
        }
    }
}

fun shareRozdilyPdf(
    context: Context, 
    invA: com.example.data.InventoryRecord, 
    invB: com.example.data.InventoryRecord,
    leftContainers: Set<String>,
    addedContainers: Set<String>
) {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val paint = Paint()
    
    // Header background
    paint.color = Color.parseColor("#2B4CB2")
    canvas.drawRect(20f, 20f, 575f, 100f, paint)

    // Header Text
    paint.color = Color.WHITE
    paint.textSize = 24f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Kontejnery pod Žofinkou", 40f, 55f, paint)
    
    paint.textSize = 14f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    canvas.drawText("Srovnání inventur", 40f, 80f, paint)

    // Subheader details
    paint.color = Color.BLACK
    paint.textSize = 14f
    var yPos = 130f
    
    val dateA = formatDateTime(invA.timestamp)
    val dateB = formatDateTime(invB.timestamp)
    
    canvas.drawText("Inventura 1: $dateA - Autor: ${invA.author}", 30f, yPos, paint)
    yPos += 25f
    canvas.drawText("Inventura 2: $dateB - Autor: ${invB.author}", 30f, yPos, paint)
    
    yPos += 40f

    // Missing
    paint.color = Color.parseColor("#B00020")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Vyexpedováno (chybí):", 30f, yPos, paint)
    yPos += 25f
    
    paint.color = Color.BLACK
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    if (leftContainers.isEmpty()) {
        canvas.drawText("- Žádné -", 50f, yPos, paint)
        yPos += 20f
    } else {
        leftContainers.forEach {
            canvas.drawText(it, 50f, yPos, paint)
            yPos += 20f
            if (yPos > 750f) return@forEach // simple pagination avoidance
        }
    }
    
    yPos += 20f
    
    // Added
    paint.color = Color.parseColor("#006400")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Přibylo nově:", 30f, yPos, paint)
    yPos += 25f
    
    paint.color = Color.BLACK
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    if (addedContainers.isEmpty()) {
        canvas.drawText("- Žádné -", 50f, yPos, paint)
        yPos += 20f
    } else {
        addedContainers.forEach {
            canvas.drawText(it, 50f, yPos, paint)
            yPos += 20f
            if (yPos > 750f) return@forEach
        }
    }

    document.finishPage(page)
    
    val file = File(context.cacheDir, "Rozdily.pdf")
    document.writeTo(FileOutputStream(file))
    document.close()
    
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Rozdíly mezi inventurami")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Sdílet dokument rozdílů jako PDF"))
}
