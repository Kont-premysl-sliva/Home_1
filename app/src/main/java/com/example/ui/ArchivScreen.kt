package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit, onNavigateToDetail: (String) -> Unit) {
    val archive by viewModel.archive.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val isDepo2 = currentDepo == "Depo 2"
    val depoWorkspaceBg = if (isDepo2) androidx.compose.ui.graphics.Color(0xFFF7F0E3) else androidx.compose.ui.graphics.Color(0xFFE6EFF7)
    var editingImageUri by remember { mutableStateOf<String?>(null) }
    var deleteConfirmCard by remember { mutableStateOf<com.example.data.ContainerCard?>(null) }
    
    if (editingImageUri != null) {
        val targetUri = editingImageUri!!
        EditImageScreen(
            imagePath = targetUri,
            onSave = { editingImageUri = null },
            onCancel = { editingImageUri = null },
            onSaveImage = { updatedDataUri ->
                val matched = archive.find { c ->
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

    if (deleteConfirmCard != null) {
        val target = deleteConfirmCard!!
        DoubleConfirmDeleteDialog(
            itemTitle = "Kontejner z Archivu: ${target.designation}",
            itemDescription = "Typ: ${target.type} • Středisko: ${target.depo}",
            onDismiss = { deleteConfirmCard = null },
            onConfirmedDelete = {
                viewModel.deleteCard(target)
                deleteConfirmCard = null
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
                        "ARCHIV (${archive.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
        if (archive.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "Žádné kontejnery v archivu",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(archive, key = { it.id }) { container ->
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
