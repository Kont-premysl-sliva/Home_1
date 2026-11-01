package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SettingsMenuEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NastaveniScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {},
    onNavigateToZakazniciTab: (Int) -> Unit = {},
    onOpenHtmlDialog: () -> Unit = {},
    onOpenWebPreview: () -> Unit = {},
    onLockApp: () -> Unit = {},
    onChangeRole: () -> Unit = {}
) {
    val themePreference by viewModel.themePreference.collectAsState()
    val owners by viewModel.owners.collectAsState()
    val prepravci by viewModel.prepravci.collectAsState()
    val drivers by viewModel.drivers.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val equipmentList by viewModel.equipmentList.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val currentDepo by viewModel.currentDepo.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPrepravaDialog by remember { mutableStateOf(false) }
    var showEquipmentDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showTokenHelpDialog by remember { mutableStateOf(false) }
    var showWipeAllDataDialog by remember { mutableStateOf(false) }
    var showPohodaDialog by remember { mutableStateOf(false) }
    var showMPohodaDialog by remember { mutableStateOf(false) }
    var showIphoneDialog by remember { mutableStateOf(false) }

    val unifiedVersionBadge = rememberUnifiedVersionBadge()

    LaunchedEffect(Unit) {
        com.example.data.GitHubBackupManager.checkIfNewerVersionOnGitHub(context)
    }

    if (showIphoneDialog) {
        IphoneAppDialog(onDismiss = { showIphoneDialog = false })
    }

    val allMenuItems = listOf(
        SettingsMenuEntry(
            title = "ZÁJEMCI",
            subtitle = "Poptávky",
            icon = Icons.Default.PersonAdd,
            color = Color(0xFF0A2E6E),
            onClick = { onNavigateToRoute("zajemci") }
        ),
        SettingsMenuEntry(
            title = "NABÍDKY",
            subtitle = "Nabídky",
            icon = Icons.Default.Description,
            color = Color(0xFF1565C0),
            onClick = { onNavigateToZakazniciTab(1) }
        ),
        SettingsMenuEntry(
            title = "FAKTURY",
            subtitle = "Doklady",
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            color = Color(0xFF0F172A),
            onClick = { onNavigateToZakazniciTab(4) }
        ),
        SettingsMenuEntry(
            title = "POLOŽKY",
            subtitle = "Ceník",
            icon = Icons.Default.Category,
            color = Color(0xFF1B5E8B),
            onClick = { onNavigateToRoute("polozky") }
        ),
        SettingsMenuEntry(
            title = "PRONÁJEM",
            subtitle = "Pronájem",
            icon = Icons.Default.Home,
            color = Color(0xFF1E293B),
            onClick = { onNavigateToRoute("pronajem") }
        ),
        SettingsMenuEntry(
            title = "INVENTURA",
            subtitle = "Z fotografií",
            icon = Icons.Default.FactCheck,
            color = Color(0xFFC2410C),
            onClick = { onNavigateToRoute("inventury") }
        ),
        SettingsMenuEntry(
            title = "ARCHIV",
            subtitle = "Archiv",
            icon = Icons.Default.Archive,
            color = Color(0xFF475569),
            onClick = { onNavigateToRoute("archiv") }
        ),
        SettingsMenuEntry(
            title = "ROZDÍLY",
            subtitle = "Rozdíly",
            icon = Icons.Default.Difference,
            color = Color(0xFF991B1B),
            onClick = { onNavigateToRoute("rozdily") }
        ),
        SettingsMenuEntry(
            title = "UDÁLOSTI",
            subtitle = "Historie",
            icon = Icons.AutoMirrored.Filled.List,
            color = Color(0xFF334155),
            onClick = { onNavigateToRoute("udalosti") }
        ),
        SettingsMenuEntry(
            title = "PROGRAM",
            subtitle = unifiedVersionBadge,
            icon = Icons.Default.Computer,
            color = Color(0xFF1B5E8B),
            onClick = { onNavigateToRoute("program") }
        ),
        SettingsMenuEntry(
            title = "iPHONE",
            subtitle = "Aplikace iOS",
            icon = Icons.Default.PhoneIphone,
            color = Color(0xFF0A2E6E),
            onClick = { showIphoneDialog = true }
        ),
        SettingsMenuEntry(
            title = "NASTAVENÍ",
            subtitle = "Motiv",
            icon = Icons.Default.Settings,
            color = Color(0xFF334155),
            onClick = { showSettingsDialog = true }
        ),
        SettingsMenuEntry(
            title = "PŘEPRAVA",
            subtitle = "Doprava",
            icon = Icons.Default.LocalShipping,
            color = Color(0xFFEA580C),
            onClick = { showPrepravaDialog = true }
        ),
        SettingsMenuEntry(
            title = "VYBAVENÍ",
            subtitle = "Kontejnery",
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF1565C0),
            onClick = { showEquipmentDialog = true }
        ),
        SettingsMenuEntry(
            title = "ZÁLOHA",
            subtitle = "Cloud",
            icon = Icons.Default.CloudUpload,
            color = Color(0xFF0F172A),
            onClick = { showBackupDialog = true }
        ),
        SettingsMenuEntry(
            title = "ADRESÁŘ",
            subtitle = "Kontakty",
            icon = Icons.Default.Contacts,
            color = Color(0xFF0284C7),
            onClick = { onNavigateToZakazniciTab(3) }
        ),
        SettingsMenuEntry(
            title = "mSERVER",
            subtitle = "XML API",
            icon = Icons.Default.Sync,
            color = Color(0xFF475569),
            onClick = { showPohodaDialog = true }
        ),
        SettingsMenuEntry(
            title = "mPOHODA",
            subtitle = "Komplet",
            icon = Icons.Default.CloudUpload,
            color = Color(0xFF1B5E8B),
            onClick = { showMPohodaDialog = true }
        ),
        SettingsMenuEntry(
            title = "REINDEXACE",
            subtitle = "Databáze",
            icon = Icons.Default.Autorenew,
            color = Color(0xFF991B1B),
            onClick = {
                viewModel.reindexEntireDatabase { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        ),
        SettingsMenuEntry(
            title = "UŽIVATEL",
            subtitle = "Role: ${userRole.shortLabel}",
            icon = Icons.Default.SwitchAccount,
            color = Color(0xFF1E293B),
            onClick = { onChangeRole() }
        )
    )

    val depoAllowedTitles = setOf(
        "ZÁJEMCI",
        "PŘEPRAVA",
        "VYBAVENÍ",
        "NASTAVENÍ",
        "INVENTURA",
        "INVENTURY",
        "ARCHIV",
        "ROZDÍLY",
        "UDÁLOSTI",
        "PROGRAM",
        "iPHONE",
        "REINDEXACE",
        "UŽIVATEL"
    )

    val menuItems = remember(userRole, unifiedVersionBadge) {
        when (userRole) {
            UserRole.ADMIN -> allMenuItems
            UserRole.DEPO -> allMenuItems.filter { it.title in depoAllowedTitles }
            UserRole.PREPRAVCE -> allMenuItems.filter { it.title == "PŘEPRAVA" || it.title == "iPHONE" || it.title == "UŽIVATEL" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // ==================== ZÚŽENÝ HORNÍ OKRAJ ====================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                // 1. Řádek: zúžený okraj na jednom řádku
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NASTAVENÍ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        softWrap = false
                    )
                    Surface(
                        color = Color(0xFF0284C7),
                        shape = RoundedCornerShape(6.dp),
                        modifier = if (userRole == UserRole.ADMIN) {
                            Modifier.clickable { onOpenHtmlDialog() }
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = unifiedVersionBadge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 2. Řádek: Středisko a aktivní role
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Středisko: ${currentDepo ?: "Depo 1"} • Režim: ${userRole.shortLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Přepnout uživatele →",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.clickable { onChangeRole() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mřížka kostiček
                menuItems.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { item.onClick() },
                                shape = RoundedCornerShape(10.dp),
                                color = item.color.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, item.color.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = item.color,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = item.color,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 10.sp,
                                            color = Color(0xFF475569),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (userRole == UserRole.ADMIN) {
            Spacer(modifier = Modifier.height(10.dp))

            // Karta: POHODA Komplet & mPOHODA integrace
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMPohodaDialog = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF86EFAC))
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "POHODA Komplet & mPohoda",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF166534)
                                )
                                Surface(
                                    color = Color(0xFFBBF7D0),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PROPOJENO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF14532D),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "ARCHIVACE SKARTACE OSTRAVA • Auto-odesílání zapnuto",
                                fontSize = 10.sp,
                                color = Color(0xFF15803D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ==================== MODAL: NASTAVENÍ (Motiv, Informace, Vlastník) ====================
    if (showSettingsDialog) {
        var newOwnerText by remember { mutableStateOf("") }
        var currentUserNameText by remember(userName) { mutableStateOf(userName) }

        Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 600.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nastavení",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(onClick = { showSettingsDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider()

                    // 1. Motiv
                    Text("Motiv", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = themePreference == "system",
                            onClick = { viewModel.setThemePreference("system") }
                        )
                        Text("Systémový", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(
                            selected = themePreference == "light",
                            onClick = { viewModel.setThemePreference("light") }
                        )
                        Text("Světlý", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(
                            selected = themePreference == "dark",
                            onClick = { viewModel.setThemePreference("dark") }
                        )
                        Text("Tmavý", fontSize = 12.sp)
                    }

                    HorizontalDivider()

                    // 2. Informace
                    Text("Uživatel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = currentUserNameText,
                            onValueChange = { currentUserNameText = it },
                            label = { Text("Jméno") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.setUserName(currentUserNameText) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Uložit", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider()

                    // 3. Vlastník
                    Text("Vlastník", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newOwnerText,
                            onValueChange = { newOwnerText = it },
                            label = { Text("Vlastník") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newOwnerText.isNotBlank()) {
                                    viewModel.addOwner(newOwnerText.trim())
                                    newOwnerText = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Přidat", fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        owners.forEach { owner ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = owner, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    IconButton(
                                        onClick = { viewModel.removeOwner(owner) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // 4. Zabezpečení a biometrika
                    Text("Zabezpečení a biometrika", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    
                    var biometricEnabled by remember { mutableStateOf(BiometricAuthHelper.isBiometricEnabled(context)) }
                    var autoPromptEnabled by remember { mutableStateOf(BiometricAuthHelper.isAutoPromptEnabled(context)) }
                    val bioStatus = remember { BiometricAuthHelper.checkBiometricStatus(context) }

                    // Stav biometrie
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (bioStatus == BiometricStatus.AVAILABLE) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (bioStatus == BiometricStatus.AVAILABLE) Color(0xFF86EFAC) else Color(0xFFFCD34D)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (bioStatus == BiometricStatus.AVAILABLE) Color(0xFF16A34A) else Color(0xFFD97706),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = BiometricAuthHelper.getStatusDescription(bioStatus),
                                fontSize = 11.sp,
                                color = if (bioStatus == BiometricStatus.AVAILABLE) Color(0xFF166534) else Color(0xFF92400E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Přepínač: Povolit biometrii
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Přihlašování biometrikou", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                            Text("Otisk prstu, obličej nebo kód obrazovky", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                biometricEnabled = checked
                                BiometricAuthHelper.setBiometricEnabled(context, checked)
                                Toast.makeText(context, if (checked) "Biometrické přihlašování zapnuto" else "Biometrické přihlašování vypnuto", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Přepínač: Automaticky vyžadovat při spuštění
                    if (biometricEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vyžadovat při spuštění", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                Text("Ihned zobrazit výzvu k ověření po otevření aplikace", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Switch(
                                checked = autoPromptEnabled,
                                onCheckedChange = { checked ->
                                    autoPromptEnabled = checked
                                    BiometricAuthHelper.setAutoPromptEnabled(context, checked)
                                }
                            )
                        }

                        // Tlačítko: Vyzkoušet biometrii
                        OutlinedButton(
                            onClick = {
                                val activity = context as? FragmentActivity
                                if (activity != null) {
                                    BiometricAuthHelper.showBiometricPrompt(
                                        activity = activity,
                                        title = "Biometrie",
                                        subtitle = "Ověření",
                                        onSuccess = {
                                            Toast.makeText(context, "Biometrické ověření úspěšné!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { _, msg ->
                                            Toast.makeText(context, "Chyba biometrie: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vyzkoušet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Tlačítko: Zamknout aplikaci
                        Button(
                            onClick = {
                                showSettingsDialog = false
                                onLockApp()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Zamknout", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { showSettingsDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zavřít", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ==================== MODAL: PŘEPRAVA (Autodoprava / Přepravce, Řidiči, Auta) ====================
    if (showPrepravaDialog) {
        var newPrepravceText by remember { mutableStateOf("") }
        var newDriverText by remember { mutableStateOf("") }
        var newVehicleText by remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = { showPrepravaDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Přeprava",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(onClick = { showPrepravaDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider()

                    // Sekce 1: Přepravce (autodoprava)
                    Text("Přepravce", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEA580C))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPrepravceText,
                            onValueChange = { newPrepravceText = it },
                            label = { Text("Dopravce") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newPrepravceText.isNotBlank()) {
                                    viewModel.addPrepravce(newPrepravceText.trim())
                                    newPrepravceText = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Přidat", fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        prepravci.forEach { p ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                                border = BorderStroke(1.dp, Color(0xFFFED7AA))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = p, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    IconButton(
                                        onClick = { viewModel.removePrepravce(p) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Podsekce 2: Řidiči
                    Text("Řidiči", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0284C7))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDriverText,
                            onValueChange = { newDriverText = it },
                            label = { Text("Řidič") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newDriverText.isNotBlank()) {
                                    viewModel.addDriver(newDriverText.trim())
                                    newDriverText = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Přidat", fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        drivers.forEach { d ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = d, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    IconButton(
                                        onClick = { viewModel.removeDriver(d) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Podsekce 3: Auta (SPZ)
                    Text("Auta", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF047857))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newVehicleText,
                            onValueChange = { newVehicleText = it },
                            label = { Text("Auto") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newVehicleText.isNotBlank()) {
                                    viewModel.addVehicle(newVehicleText.trim())
                                    newVehicleText = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Přidat", fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        vehicles.forEach { v ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = v, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    IconButton(
                                        onClick = { viewModel.removeVehicle(v) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { showPrepravaDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zavřít", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ==================== MODAL: VYBAVENÍ KONTEJNERU ====================
    if (showEquipmentDialog) {
        var newEquipmentText by remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = { showEquipmentDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vybavení",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(onClick = { showEquipmentDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newEquipmentText,
                            onValueChange = { newEquipmentText = it },
                            label = { Text("Vybavení") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newEquipmentText.isNotBlank()) {
                                    viewModel.addEquipment(newEquipmentText.trim())
                                    newEquipmentText = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Přidat", fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        equipmentList.forEach { eqItem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = eqItem, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    IconButton(
                                        onClick = { viewModel.removeEquipment(eqItem) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { showEquipmentDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zavřít", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ==================== MODAL: ZÁLOHA A OBNOVA (Cloud & Disk) ====================
    if (showBackupDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var isProcessing by remember { mutableStateOf(false) }
        var backupProgressPct by remember { mutableIntStateOf(0) }
        var backupTimerSeconds by remember { mutableIntStateOf(0) }
        var backupStatusMessage by remember { mutableStateOf("") }

        var githubRepo by remember { mutableStateOf(com.example.data.GitHubBackupManager.getRepo(context)) }
        var githubBranch by remember { mutableStateOf(com.example.data.GitHubBackupManager.getBranch(context)) }
        var githubToken by remember { mutableStateOf(com.example.data.GitHubBackupManager.getToken(context)) }
        var githubLastSync by remember { mutableStateOf(com.example.data.GitHubBackupManager.getLastSync(context)) }
        var showTokenField by remember { mutableStateOf(true) }
        var showTokenPassword by remember { mutableStateOf(false) }
        var isTestingConnection by remember { mutableStateOf(false) }
        var testConnectionResult by remember { mutableStateOf<com.example.data.GitHubBackupManager.GitHubResult?>(null) }

        LaunchedEffect(isProcessing) {
            if (isProcessing) {
                backupTimerSeconds = 0
                while (isProcessing) {
                    kotlinx.coroutines.delay(1000)
                    backupTimerSeconds++
                }
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    isProcessing = true
                    backupProgressPct = 0
                    backupStatusMessage = "Zahajuji kompresi databáze a fotografií do ZIP..."
                    try {
                        withContext(Dispatchers.IO) {
                            val backupManager = com.example.data.BackupManager(context)
                            val backupPath = backupManager.createDataBackup { pct ->
                                backupProgressPct = pct
                                backupStatusMessage = "Exportováno a zabaleno $pct % dat..."
                            }
                            val backupFile = java.io.File(backupPath)
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                backupFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            backupProgressPct = 100
                            backupStatusMessage = "Export dokončen! Uloženo na disk (${backupFile.length() / 1024} KB)."
                        }
                        android.widget.Toast.makeText(context, "Záloha byla úspěšně vytvořena a uložena na disk!", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(context, "Chyba při ukládání: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    isProcessing = true
                    backupProgressPct = 0
                    backupStatusMessage = "Čtení ZIP balíčku z disku..."
                    try {
                        withContext(Dispatchers.IO) {
                            val tempFile = java.io.File(context.cacheDir, "import_backup.zip")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val backupManager = com.example.data.BackupManager(context)
                            backupManager.restoreDataBackup(tempFile) { pct ->
                                backupProgressPct = pct
                                backupStatusMessage = "Obnoveno $pct % dat z disku..."
                            }
                            backupProgressPct = 100
                        }
                        viewModel.reloadLocalCaches()
                        android.widget.Toast.makeText(context, "Data byla úspěšně načtena.", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(context, "Chyba při obnově: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }

        Dialog(
            onDismissRequest = { if (!isProcessing) showBackupDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(24.dp))
                            Text(
                                text = "Zálohování & Obnova dat",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                        IconButton(onClick = { if (!isProcessing) showBackupDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider()

                    // --- SEKTOR 1: GITHUB CLOUD ZÁLOHA PŘES WI-FI (BEZ KABELU) ---
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF15803D),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = "GitHub Wi-Fi Záloha (Bez kabelu)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF14532D)
                                )
                            }
                            Text(
                                text = "Načtěte nebo uložte data přímo ze soukromého či veřejného GitHubu přes Wi-Fi bez nutnosti propojovat telefon s počítačem kabelem.",
                                fontSize = 11.sp,
                                color = Color(0xFF166534),
                                lineHeight = 15.sp
                            )

                            OutlinedTextField(
                                value = githubRepo,
                                onValueChange = {
                                    githubRepo = it
                                    com.example.data.GitHubBackupManager.setRepo(context, it)
                                    testConnectionResult = null
                                },
                                label = { Text("Repozitář", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = githubBranch,
                                    onValueChange = {
                                        githubBranch = it
                                        com.example.data.GitHubBackupManager.setBranch(context, it)
                                    },
                                    label = { Text("Větev", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { showTokenField = !showTokenField }
                                ) {
                                    Text(
                                        text = if (showTokenField) "Skrýt Token" else "Zadat Token",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }

                            val tokenExpStatus = remember { com.example.data.GitHubBackupManager.getTokenExpirationStatus() }

                            // Stavový proužek expirace tokenu
                            if (tokenExpStatus.isWarningPeriod) {
                                Surface(
                                    color = if (tokenExpStatus.isExpired) Color(0xFFFEF2F2) else Color(0xFFFFFBEB),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (tokenExpStatus.isExpired) Color(0xFFF87171) else Color(0xFFFBBF24)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (tokenExpStatus.isExpired) Icons.Default.Error else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (tokenExpStatus.isExpired) Color(0xFFDC2626) else Color(0xFFD97706),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = tokenExpStatus.warningMessage ?: "",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tokenExpStatus.isExpired) Color(0xFF991B1B) else Color(0xFF92400E),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    color = if (githubToken.isNotBlank()) Color(0xFFF0FDF4) else Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (githubToken.isNotBlank()) Color(0xFFBBF7D0) else Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = if (githubToken.isNotBlank()) Color(0xFF15803D) else Color(0xFF1D4ED8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (githubToken.isNotBlank()) "Personal Access Token je uložen v aplikaci" else "Veřejný přístup aktivní (aktualizace funkční). Pro zápis vložte Classic token.",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (githubToken.isNotBlank()) Color(0xFF166534) else Color(0xFF1E40AF)
                                        )
                                    }
                                }
                            }

                            if (showTokenField) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = githubToken,
                                        onValueChange = {
                                            githubToken = it
                                            com.example.data.GitHubBackupManager.setToken(context, it)
                                            testConnectionResult = null
                                        },
                                        label = { Text("Personal Access Token (Classic)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        placeholder = { Text("ghp_... s oprávněním [x] repo", fontSize = 10.sp) },
                                        singleLine = true,
                                        visualTransformation = if (showTokenPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showTokenPassword = !showTokenPassword }) {
                                                Icon(
                                                    imageVector = if (showTokenPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (showTokenPassword) "Skrýt token" else "Zobrazit token",
                                                    tint = Color(0xFF15803D),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Token potřebujete pro odesílání nových verzí a záloh na GitHub.",
                                            fontSize = 9.sp,
                                            color = Color(0xFF166534)
                                        )
                                        TextButton(
                                            onClick = { showTokenHelpDialog = true },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                text = "Návod na vygenerování",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0284C7)
                                            )
                                        }
                                    }
                                }
                            }

                            // Tlačítko pro test připojení
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingConnection = true
                                        testConnectionResult = null
                                        val res = com.example.data.GitHubBackupManager.testConnection(
                                            context = context,
                                            repoInput = githubRepo,
                                            tokenInput = githubToken
                                        )
                                        testConnectionResult = res
                                        isTestingConnection = false
                                    }
                                },
                                enabled = !isTestingConnection && !isProcessing,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF15803D))
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF15803D))
                                    Spacer(Modifier.width(8.dp))
                                    Text("OVĚŘUJI...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("OTESTOVAT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Zobrazení výsledku testu spojení
                            testConnectionResult?.let { res ->
                                when (res) {
                                    is com.example.data.GitHubBackupManager.GitHubResult.Success -> {
                                        Surface(
                                            color = Color(0xFFDCFCE7),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                                    Text(res.message, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF14532D))
                                                }
                                                if (res.details.isNotBlank()) {
                                                    Text(res.details, fontSize = 10.sp, color = Color(0xFF166534), lineHeight = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                    is com.example.data.GitHubBackupManager.GitHubResult.Error -> {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                                    Text("Chyba spojení s GitHubem", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF991B1B))
                                                }
                                                Text(res.message, fontSize = 10.sp, color = Color(0xFFB91C1C), lineHeight = 14.sp)

                                                if (res.message.contains("403") || res.message.contains("přístup") || res.message.contains("oprávnění")) {
                                                    Button(
                                                        onClick = {
                                                            try {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/settings/tokens/new"))
                                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít prohlížeč: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                    ) {
                                                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("VYTVOŘIT NOVÝ TOKEN NA GITHUB (KLASICKÝ)", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    }
                                                }

                                                if (res.message.contains("404") || res.message.contains("nebyl")) {
                                                    Button(
                                                        onClick = {
                                                            try {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Kont-premysl-sliva/Kont_2026"))
                                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít prohlížeč: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                    ) {
                                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("OTEVŘÍT REPOZITÁŘ KONT_2026 NA GITHUB", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            try {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/settings/tokens"))
                                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Nelze otevřít prohlížeč: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("NASTAVENÍ TOKENU NA GITHUB", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Poslední stav: $githubLastSync",
                                fontSize = 10.sp,
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isProcessing = true
                                            backupProgressPct = 25
                                            backupStatusMessage = "Připojuji k GitHub ($githubRepo) přes Wi-Fi..."
                                            val res = com.example.data.GitHubBackupManager.restoreFromGitHub(
                                                context = context,
                                                repoInput = githubRepo,
                                                branchInput = githubBranch,
                                                tokenInput = githubToken,
                                                onStatus = { backupStatusMessage = it }
                                            )
                                            isProcessing = false
                                            when (res) {
                                                is com.example.data.GitHubBackupManager.GitHubResult.Success -> {
                                                    githubLastSync = com.example.data.GitHubBackupManager.getLastSync(context)
                                                    viewModel.reloadLocalCaches()
                                                    Toast.makeText(context, "${res.message}\n${res.details}", Toast.LENGTH_LONG).show()
                                                }
                                                is com.example.data.GitHubBackupManager.GitHubResult.Error -> {
                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("NAČÍST", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isProcessing = true
                                            backupProgressPct = 30
                                            backupStatusMessage = "Nahrávám zálohu na GitHub ($githubRepo)..."
                                            val res = com.example.data.GitHubBackupManager.uploadToGitHub(
                                                context = context,
                                                repoInput = githubRepo,
                                                branchInput = githubBranch,
                                                tokenInput = githubToken,
                                                onStatus = { backupStatusMessage = it }
                                            )
                                            isProcessing = false
                                            when (res) {
                                                is com.example.data.GitHubBackupManager.GitHubResult.Success -> {
                                                    githubLastSync = com.example.data.GitHubBackupManager.getLastSync(context)
                                                    Toast.makeText(context, "${res.message}\n${res.details}", Toast.LENGTH_LONG).show()
                                                }
                                                is com.example.data.GitHubBackupManager.GitHubResult.Error -> {
                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ULOŽIT", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.reindexEntireDatabase { msg ->
                                        backupStatusMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("REINDEXACE", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    // Pouze Wi-Fi synchronizace s GitHubem

                    if (isProcessing) {
                        Surface(
                            color = Color(0xFFF3E8FF),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⏱️ ", fontSize = 14.sp)
                                        Text(
                                            text = String.format("Čas: %02d:%02d", backupTimerSeconds / 60, backupTimerSeconds % 60),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF6B21A8)
                                        )
                                    }
                                    Text(
                                        text = "$backupProgressPct %",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF6B21A8)
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { backupProgressPct / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = Color(0xFF9333EA)
                                )

                                Text(
                                    text = backupStatusMessage,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF581C87)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { if (!isProcessing) showBackupDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zavřít", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPohodaDialog) {
        PohodaMServerDialog(
            viewModel = viewModel,
            onDismiss = { showPohodaDialog = false }
        )
    }

    if (showMPohodaDialog) {
        MPohodaSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showMPohodaDialog = false }
        )
    }

    if (showTokenHelpDialog) {
        AlertDialog(
            onDismissRequest = { showTokenHelpDialog = false },
            icon = {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Návod: Jak vytvořit GitHub Token",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Pro nahrávání nových verzí a záloh databáze do vašeho repozitáře Kont-premysl-sliva/Kont_2026 vyžaduje GitHub Personal Access Token (PAT) s právem zápisu:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("1. Přihlaste se na GitHub.com", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("2. Vpravo nahoře klikněte na váš profil ➔ Settings", fontSize = 11.sp)
                            Text("3. V levém menu sjeďte dolů na Developer Settings", fontSize = 11.sp)
                            Text("4. Klikněte na Personal access tokens ➔ Tokens (classic)", fontSize = 11.sp)
                            Text("5. Zvolte Generate new token (classic)", fontSize = 11.sp)
                            Text("6. Zaškrtněte políčko oprávnění: 'repo' (Full control of private repositories)", fontWeight = FontWeight.Bold, color = Color(0xFF15803D), fontSize = 11.sp)
                            Text("7. Klikněte dole na Generate token a zkopírujte vygenerovaný kód (začíná na ghp_)", fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = "Token vložte do pole v aplikaci a klikněte na 'OTESTOVAT SPOJENÍ S GITHUBEM'. Token se uloží do telefonu a spojení začne ihned fungovat.",
                        fontSize = 11.sp,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTokenHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("ROZUMÍM")
                }
            }
        )
    }
}
