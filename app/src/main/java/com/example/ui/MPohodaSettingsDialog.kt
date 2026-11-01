package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.api.MPohodaConfig
import com.example.api.MPohodaSyncResponse
import kotlinx.coroutines.launch

@Composable
fun MPohodaSettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val savedConfig by viewModel.mpohodaConfig.collectAsState()

    var companyGuidInput by remember(savedConfig) {
        mutableStateOf(savedConfig.companyGuid.ifBlank { "501ebfc5-fec2-46db-a9a3-365be2848df1" })
    }
    var commKeyInput by remember(savedConfig) {
        mutableStateOf(savedConfig.communicationKey.ifBlank { "D94ABB55A16E4C0599E3DCC6660E41A4" })
    }
    var apiKeyInput by remember(savedConfig) {
        mutableStateOf(savedConfig.apiKey.ifBlank { "D94ABB55A16E4C0599E3DCC6660E41A4" })
    }
    var autoSyncOnSave by remember(savedConfig) {
        mutableStateOf(savedConfig.autoSyncOnSave)
    }

    var showCommKey by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<MPohodaSyncResponse?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Přehled & Údaje, 1: Návod POHODA Komplet

    fun copyToClipboard(text: String, label: String) {
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "$label zkopírován do schránky", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.padding(6.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "mPOHODA & POHODA Komplet",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Synchronizace s účetnictvím Stormware POHODA",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFFF1F5F9),
                    contentColor = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Nastavení a údaje", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Postup pro PC (POHODA)", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }

                if (selectedTab == 0) {
                    // TAB 0: NASTAVENÍ A ÚDAJE

                    // Company Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "ARCHIVACE SKARTACE OSTRAVA, s.r.o.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "IČ 25388720",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Licence: POHODA Komplet N5MAI (Uživatel: Prema) • Propojení aktivní",
                                fontSize = 11.sp,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    // Field 1: Unikátní identifikátor firmy
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UNIKÁTNÍ IDENTIFIKÁTOR FIRMY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF64748B)
                                )
                                OutlinedButton(
                                    onClick = { copyToClipboard(companyGuidInput, "Identifikátor firmy") },
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF0284C7))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Kopírovat", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                }
                            }
                            OutlinedTextField(
                                value = companyGuidInput,
                                onValueChange = { companyGuidInput = it },
                                label = { Text("Identifikátor", maxLines = 1, softWrap = false) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(
                                text = "Přiřazuje mobilní aplikaci k účetní jednotce v mPohodě.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Field 2: Klíč pro komunikaci s programem POHODA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "KLÍČ PRO KOMUNIKACI S PROGRAMEM POHODA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF92400E)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { copyToClipboard(commKeyInput, "Klíč pro komunikaci") },
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD97706))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFD97706))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Kopírovat pro PC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }
                            OutlinedTextField(
                                value = commKeyInput,
                                onValueChange = { commKeyInput = it },
                                label = { Text("Klíč", maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    IconButton(onClick = { showCommKey = !showCommKey }) {
                                        Icon(
                                            if (showCommKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showCommKey) "Skrýt" else "Zobrazit",
                                            tint = Color(0xFF92400E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (showCommKey) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(
                                text = "Tento klíč se vkládá v POHODA Komplet do Nastavení -> mPohoda...",
                                fontSize = 10.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }

                    // Field 3: API Klíč / Token pro mPohoda Cloud REST API
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "REST API KLÍČ PRO CLOUD mPOHODA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF64748B)
                                )
                                OutlinedButton(
                                    onClick = { copyToClipboard(apiKeyInput, "API klíč") },
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF64748B))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF64748B))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Kopírovat", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                }
                            }
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = {
                                    apiKeyInput = it
                                    testResult = null
                                },
                                label = { Text("Token", maxLines = 1, softWrap = false) },
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showApiKey) "Skrýt" else "Zobrazit",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(
                                text = "Pokud nepoužíváte separátní Bearer token z app.mpohoda.cz, slouží komunikační klíč.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Auto-sync Switch Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (autoSyncOnSave) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (autoSyncOnSave) Color(0xFF93C5FD) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Automaticky odeslat při každém uložení faktury",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (autoSyncOnSave) Color(0xFF1E40AF) else Color(0xFF334155)
                                )
                                Text(
                                    text = "Každá nově vystavená nebo upravená faktura se ihned zaeviduje do mPohody bez nutnosti ručního klikání.",
                                    fontSize = 11.sp,
                                    color = if (autoSyncOnSave) Color(0xFF3B82F6) else Color(0xFF64748B),
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = autoSyncOnSave,
                                onCheckedChange = { autoSyncOnSave = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0284C7)
                                )
                            )
                        }
                    }

                    // Testing status / feedback
                    if (isTesting) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2563EB))
                                    Text("Ověřuji spojení se službou mPohoda...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                }
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2563EB))
                            }
                        }
                    } else if (testResult != null) {
                        val res = testResult!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (res.success) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (res.success) Color(0xFF86EFAC) else Color(0xFFFECACA))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (res.success) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        res.message,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.success) Color(0xFF166534) else Color(0xFF991B1B)
                                    )
                                }
                                if (res.errorDetails.isNotBlank()) {
                                    Text(
                                        res.errorDetails,
                                        fontSize = 11.sp,
                                        color = if (res.success) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isTesting = true
                                testResult = null
                                coroutineScope.launch {
                                    val cfg = MPohodaConfig(
                                        companyGuid = companyGuidInput.trim(),
                                        communicationKey = commKeyInput.trim(),
                                        apiKey = apiKeyInput.trim(),
                                        autoSyncOnSave = autoSyncOnSave
                                    )
                                    val res = viewModel.testMpohodaConnection(config = cfg)
                                    testResult = res
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && (commKeyInput.isNotBlank() || apiKeyInput.isNotBlank()),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                            border = BorderStroke(1.dp, Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Otestovat spojení", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val cfg = MPohodaConfig(
                                    companyGuid = companyGuidInput.trim(),
                                    communicationKey = commKeyInput.trim(),
                                    apiKey = apiKeyInput.trim(),
                                    autoSyncOnSave = autoSyncOnSave
                                )
                                viewModel.setMpohodaConfig(cfg)
                                Toast.makeText(context, "Nastavení mPohody uloženo", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B1B3D))
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Uložit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                } else {
                    // TAB 1: POSTUP PRO POHODA KOMPLET NA PC
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        text = "POHODA Komplet na vašem PC (Windows 10)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "Vlastník licence: ARCHIVACE SKARTACE OSTRAVA, s.r.o. (IČ 25388720)",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Kroky pro jednorázové spárování v programu POHODA na PC:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        // Krok 1
                        StepCard(
                            stepNumber = "1",
                            title = "Otevřete firmu v programu POHODA",
                            description = "Spusťte POHODU Komplet na PC pod uživatelem 'Prema' a zvolte účetní jednotku ARCHIVACE SKARTACE OSTRAVA, s.r.o. (IČ 25388720)."
                        )

                        // Krok 2
                        StepCard(
                            stepNumber = "2",
                            title = "Otevřete Nastavení mPohoda",
                            description = "V horním menu programu POHODA klikněte na:\nNastavení -> mPohoda...\n(případně Soubor -> Datová komunikace -> mPohoda -> Nastavení komunikace)."
                        )

                        // Krok 3 with Copy action
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF16A34A), modifier = Modifier.size(20.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("3", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                        Text("Vložte Klíč pro komunikaci", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                    }
                                    Button(
                                        onClick = { copyToClipboard(commKeyInput, "Klíč pro komunikaci") },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Zkopírovat klíč", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = "Do pole 'Klíč pro komunikaci s programem POHODA' vložte hodnotu:\n$commKeyInput",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF15803D),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "POHODA automaticky ověří spojení a potvrdí propojení s identifikátorem: $companyGuidInput",
                                    fontSize = 10.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                        }

                        // Krok 4
                        StepCard(
                            stepNumber = "4",
                            title = "Nastavte číselné řady dokladů",
                            description = "V dialogu mPohody v POHODĚ vyberte, do které číselné řady se mají ukládat Vydané faktury a Zálohové faktury z mobilní aplikace."
                        )

                        // Krok 5
                        StepCard(
                            stepNumber = "5",
                            title = "Spusťte stažení faktur do účetnictví",
                            description = "Kdykoliv v aplikaci vystavíte nebo upravíte fakturu, ihned se odešle. V POHODĚ na PC pak stačí kliknout na:\nSoubor -> Datová komunikace -> mPohoda...\na stisknout 'Spustit komunikaci'. Všechny faktury se okamžitě stáhnou do agendy Fakturace!"
                        )

                        // Back to settings button
                        Button(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3D5E))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Zpět na nastavení a údaje", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0284C7),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stepNumber, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFF475569),
                lineHeight = 15.sp
            )
        }
    }
}
