package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.api.PohodaServerConfig
import com.example.api.PohodaSyncResult
import kotlinx.coroutines.launch

@Composable
fun PohodaMServerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val savedConfig by viewModel.pohodaServerConfig.collectAsState()

    var serverUrl by remember(savedConfig) { mutableStateOf(savedConfig.serverUrl) }
    var ico by remember(savedConfig) { mutableStateOf(savedConfig.ico) }
    var username by remember(savedConfig) { mutableStateOf(savedConfig.username) }
    var password by remember(savedConfig) { mutableStateOf(savedConfig.password) }
    var invoiceSeries by remember(savedConfig) { mutableStateOf(savedConfig.invoiceSeries) }
    var showPassword by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<PohodaSyncResult?>(null) }
    var isFetchingAdresar by remember { mutableStateOf(false) }
    var fetchResultMsg by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    fun currentConfig() = PohodaServerConfig(
        serverUrl = serverUrl.trim(),
        ico = ico.trim(),
        username = username.trim(),
        password = password,
        invoiceSeries = invoiceSeries.trim()
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            color = Color(0xFFFEF3C7)
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.padding(6.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "POHODA mServer (HTTP API)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Přímé propojení se Stormware POHODA",
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

                // Input: Server URL
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Adresa", maxLines = 1, softWrap = false) },
                    placeholder = { Text("http://192.168.1.50:4444/xml") },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFF64748B)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Input: IČO
                OutlinedTextField(
                    value = ico,
                    onValueChange = { ico = it },
                    label = { Text("IČO", maxLines = 1, softWrap = false) },
                    placeholder = { Text("25388720") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF64748B)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Input: Credentials
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Uživatel", maxLines = 1, softWrap = false) },
                        placeholder = { Text("api_mobil") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Heslo", maxLines = 1, softWrap = false) },
                        placeholder = { Text("••••••") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Zobrazit heslo",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Optional Invoice Series
                OutlinedTextField(
                    value = invoiceSeries,
                    onValueChange = { invoiceSeries = it },
                    label = { Text("Řada", maxLines = 1, softWrap = false) },
                    placeholder = { Text("např. 26VF (prázdné = výchozí)") },
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null, tint = Color(0xFF64748B)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Status Banner / Result Card
                if (isTesting || isFetchingAdresar) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2563EB))
                                Text(
                                    if (isTesting) "Testuji spojení s mServerem..." else "Stahuji adresář z Pohody...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
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
                                    text = res.message,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.success) Color(0xFF166534) else Color(0xFF991B1B)
                                )
                            }
                            if (res.detailMessage.isNotBlank()) {
                                Text(
                                    text = res.detailMessage,
                                    fontSize = 11.sp,
                                    color = if (res.success) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Fetch Result message
                if (fetchResultMsg != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            Text(fetchResultMsg!!, fontSize = 12.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test Button
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            fetchResultMsg = null
                            coroutineScope.launch {
                                val cfg = currentConfig()
                                testResult = viewModel.testPohodaConnection(cfg)
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                        border = BorderStroke(1.dp, Color(0xFFD97706))
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Otestovat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Download Address Book
                    OutlinedButton(
                        onClick = {
                            isFetchingAdresar = true
                            fetchResultMsg = null
                            coroutineScope.launch {
                                val cfg = currentConfig()
                                val (res, count) = viewModel.fetchAddressBookFromPohoda(cfg)
                                fetchResultMsg = if (res.success) {
                                    "Načteno kontaktů: ${res.syncedCount} (nově přidáno: $count)"
                                } else {
                                    "Chyba: ${res.message}"
                                }
                                isFetchingAdresar = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                        border = BorderStroke(1.dp, Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adresář", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Save Configuration Button
                Button(
                    onClick = {
                        val cfg = currentConfig()
                        viewModel.updatePohodaServerConfig(cfg)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B1B3D))
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uložit nastavení mServeru", fontWeight = FontWeight.Bold)
                }

                // Expandable Help & Instructions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHelp = !showHelp },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                                Text("Návod: Jak nastavit mServer v Pohodě", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            }
                            Icon(
                                if (showHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showHelp) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "1. V programu POHODA jděte do menu Nastavení -> mServer (nebo Soubor -> Datová komunikace -> mServer).",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    "2. Přidejte instanci mServeru, zadejte název (např. 'Kontejnery') a zvolte volný port (výchozí 4444 nebo 8080).",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    "3. Vyberte účetní jednotku (firmu s vaším IČO) a spusťte mServer.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    "4. V Nastavení -> Přístupová práva vytvořte uživatele pro API a zatrhněte 'Povolit XML komunikaci'.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    "5. Ve Windows Firewall povolte příchozí připojení pro zvolený TCP port (např. 4444).",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    "6. Mobilní telefon musí být připojen k firemní Wi-Fi síti nebo přes podnikovou VPN.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
