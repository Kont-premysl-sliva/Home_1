package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDepoSelected: (String) -> Unit,
    onPolozkySelected: () -> Unit = {}
) {
    var showHtmlDialog by remember { mutableStateOf(false) }

    if (showHtmlDialog) {
        HtmlDownloadDialog(onDismiss = { showHtmlDialog = false })
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Oficiální Logo Kontejnery Ostrava v přesném formátu dle zadání
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                CompanyLogo(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .widthIn(max = 380.dp)
                        .clickable { showHtmlDialog = true }
                )
            }

            // Spodní lišta v logu: Správné číslo verze a malý datum poslední aktualizace
            Surface(
                color = Color(0xFF011C45),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .widthIn(max = 380.dp)
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val unifiedVersionBadge = rememberUnifiedVersionBadge()
                    val latestDate = rememberHighestVersionDate()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = unifiedVersionBadge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = latestDate,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }

            DepoCard("DEPO1", onClick = { onDepoSelected("Depo 1") })
            Spacer(modifier = Modifier.height(12.dp))
            DepoCard("DEPO2", onClick = { onDepoSelected("Depo 2") })
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clickable { onPolozkySelected() },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B5E8B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "POLOŽKY",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun DepoCard(title: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("depo_prefs", Context.MODE_PRIVATE)
    var bgUri by remember { mutableStateOf(prefs.getString("bg_$title", null)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs.edit().putString("bg_$title", uri.toString()).apply()
            bgUri = uri.toString()
        }
    }

    var clickCount by remember { mutableIntStateOf(0) }
    var clickJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable {
                clickCount++
                if (clickCount == 3) {
                    clickJob?.cancel()
                    clickCount = 0
                    launcher.launch(arrayOf("image/*"))
                } else if (clickCount == 1) {
                    clickJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(400)
                        if (clickCount == 1) {
                            onClick()
                        }
                        clickCount = 0
                    }
                }
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bgUri != null) {
                AsyncImage(
                    model = bgUri,
                    contentDescription = "Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Poloprůhledná vrstva pro lepší čitelnost textu
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            } else {
                val fallbackColor = if (title.contains("2")) Color(0xFFE65100) else Color(0xFF0D47A1)
                Box(modifier = Modifier.fillMaxSize().background(fallbackColor.copy(alpha = 0.8f)))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
