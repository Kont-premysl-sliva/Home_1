package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

const val APP_VERSION_NUMBER = 404
const val APP_VERSION_NAME = "404"
const val APP_VERSION_BADGE = "v 404 • 1.11.2026"
const val APP_LAST_UPDATE_DATE = "1.11.2026"

fun formatUnifiedVersionBadge(versionNumber: Int): String = "v $versionNumber"

@Composable
fun rememberHighestVersionNumber(): Int {
    val context = androidx.compose.ui.platform.LocalContext.current
    val flowVer by com.example.data.GitHubBackupManager.highestVersionFlow.collectAsState()
    val localVer = remember(context, flowVer) {
        com.example.data.GitHubBackupManager.getLastUpdateVersion(context).toIntOrNull() ?: APP_VERSION_NUMBER
    }
    return maxOf(flowVer, localVer, APP_VERSION_NUMBER, AppUpdateManager.LATEST_CLOUD_VERSION_CODE)
}

@Composable
fun rememberHighestVersionDate(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val flowDate by com.example.data.GitHubBackupManager.highestDateFlow.collectAsState()
    val localDate = remember(context, flowDate) {
        com.example.data.GitHubBackupManager.getLastUpdateDate(context)
    }
    return localDate.ifBlank { APP_LAST_UPDATE_DATE }
}

@Composable
fun rememberUnifiedVersionBadge(): String {
    val ver = rememberHighestVersionNumber()
    return formatUnifiedVersionBadge(ver)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    onNavigateToCamera: () -> Unit,
    onLockApp: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userRole by viewModel.userRole.collectAsState()
    val roleConfirmedOnStartup by viewModel.roleConfirmedOnStartup.collectAsState()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                HtmlDownloadManager.saveHtmlDirectlyToDownloads(context)
            } catch (_: Throwable) {}
        }
        com.example.data.GitHubBackupManager.checkAndAutoUpdateOnStartup(context) {
            viewModel.reloadLocalCaches()
        }
    }

    if (!roleConfirmedOnStartup) {
        UserRoleSelectionScreen(
            currentRole = userRole,
            onSelectRole = { selectedRole ->
                viewModel.setUserRole(selectedRole)
            }
        )
    } else {
        key(userRole) {
            AppRoleNavigationContent(
                viewModel = viewModel,
                userRole = userRole,
                onNavigateToCamera = onNavigateToCamera,
                onLockApp = onLockApp,
                onLogout = onLogout
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoleNavigationContent(
    viewModel: MainViewModel,
    userRole: UserRole,
    onNavigateToCamera: () -> Unit,
    onLockApp: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val startDest = remember(userRole) {
        when (userRole) {
            UserRole.ADMIN -> "zakaznici"
            UserRole.DEPO -> "seznam"
            UserRole.PREPRAVCE -> "preprava"
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = navBackStackEntry?.destination?.route
    val currentRoute = activeRoute ?: startDest

    val currentDepo by viewModel.currentDepo.collectAsState()
    val allCustomers by viewModel.customers.collectAsState()
    val allContainers by viewModel.containers.collectAsState()
    val allZajemci by viewModel.zajemci.collectAsState()
    val allTransports by viewModel.transports.collectAsState()
    val allZakazky by viewModel.zakazky.collectAsState()
    val allInventories by viewModel.inventories.collectAsState()

    val objednavkyCount = remember(allCustomers, currentDepo) {
        allCustomers.count { c ->
            c.isOrder && (currentDepo == null || c.depo.isBlank() || c.depo == currentDepo)
        }
    }
    val kontejneryCount = allContainers.size
    val inventuraCount = allInventories.size
    val zajemciCount = remember(allZajemci, currentDepo) {
        allZajemci.count { z ->
            !z.isDeleted && (currentDepo == null || z.depo.isBlank() || z.depo == currentDepo)
        }
    }
    val zakazkyCount = remember(allZakazky, currentDepo) {
        allZakazky.count { z ->
            currentDepo == null || z.depo.isBlank() || z.depo == currentDepo
        }
    }
    val prepravaCount = allTransports.size

    val isDepo2 = currentDepo == "Depo 2"
    val pohodaWorkspaceBg = if (isDepo2) Color(0xFFF3E6D8) else Color(0xFFDCE6F2)
    val pohodaHeaderBg = if (isDepo2) Color(0xFF5C3409) else Color(0xFF163656)
    var showHtmlDialog by remember { mutableStateOf(false) }
    var showWebPreview by remember { mutableStateOf(false) }
    var showIphoneDialog by remember { mutableStateOf(false) }
    var zakazniciTab by remember { mutableIntStateOf(0) }
    var lastMainSubRoute by remember(userRole) {
        mutableStateOf(
            when (userRole) {
                UserRole.ADMIN -> "objednavky"
                UserRole.DEPO -> "kontejnery"
                UserRole.PREPRAVCE -> "preprava"
            }
        )
    }

    fun navigateToSubMenu(subKey: String) {
        if (navController.currentDestination == null) return
        val allowedKey = when (userRole) {
            UserRole.ADMIN -> subKey
            UserRole.DEPO -> if (subKey in listOf("kontejnery", "preprava", "zajemci", "inventura")) subKey else "kontejnery"
            UserRole.PREPRAVCE -> "preprava"
        }
        lastMainSubRoute = allowedKey
        when (allowedKey) {
            "objednavky" -> {
                zakazniciTab = 0
                navController.navigate("zakaznici") { launchSingleTop = true }
            }
            "zajemci" -> {
                navController.navigate("zajemci") { launchSingleTop = true }
            }
            "zakazky" -> {
                zakazniciTab = 2
                navController.navigate("zakaznici") { launchSingleTop = true }
            }
            "preprava" -> {
                navController.navigate("preprava") { launchSingleTop = true }
            }
            "kontejnery" -> {
                navController.navigate("seznam") { launchSingleTop = true }
            }
            "inventura" -> {
                navController.navigate("inventury") { launchSingleTop = true }
            }
        }
    }

    // Vynucení povolených obrazovek podle zvolené role (pouze po inicializaci grafu NavHostu)
    LaunchedEffect(userRole, activeRoute) {
        if (activeRoute == null) return@LaunchedEffect
        when (userRole) {
            UserRole.PREPRAVCE -> {
                if (activeRoute != "preprava") {
                    lastMainSubRoute = "preprava"
                    navController.navigate("preprava") { launchSingleTop = true }
                }
            }
            UserRole.DEPO -> {
                val forbiddenForDepo = setOf("zakaznici", "polozky", "pronajem", "sdileni", "upravy")
                if (activeRoute in forbiddenForDepo) {
                    lastMainSubRoute = "kontejnery"
                    navController.navigate("seznam") { launchSingleTop = true }
                }
            }
            UserRole.ADMIN -> {}
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = showHtmlDialog || showWebPreview || showIphoneDialog) {
        if (showWebPreview) showWebPreview = false
        else if (showIphoneDialog) showIphoneDialog = false
        else showHtmlDialog = false
    }

    if (showHtmlDialog) {
        HtmlDownloadDialog(
            onDismiss = {
                showHtmlDialog = false
                viewModel.reloadLocalCaches()
            }
        )
    }

    if (showWebPreview) {
        HtmlWebViewPreviewDialog(
            onDismiss = {
                showWebPreview = false
                viewModel.reloadLocalCaches()
            }
        )
    }

    if (showIphoneDialog) {
        IphoneAppDialog(
            onDismiss = {
                showIphoneDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pohodaHeaderBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Color(0xFF011C45),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "K",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Středisko: ${currentDepo ?: "Depo 1"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Uživatel: ${userRole.shortLabel}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBAE6FD)
                                )
                            }
                        }
                        val unifiedVersionBadge = rememberUnifiedVersionBadge()
                        Surface(
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(6.dp),
                            modifier = if (userRole == UserRole.ADMIN) {
                                Modifier.clickable {
                                    scope.launch { drawerState.close() }
                                    showHtmlDialog = true
                                }
                            } else {
                                Modifier
                            }
                        ) {
                            Text(
                                text = unifiedVersionBadge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                HorizontalDivider()

                // Přepnutí role uživatele
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "ZMĚNIT UŽIVATELE (${userRole.shortLabel})",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4F46E5)
                        )
                    },
                    selected = false,
                    icon = { Icon(Icons.Default.SwitchAccount, contentDescription = "Změnit uživatele", tint = Color(0xFF4F46E5)) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.reopenRoleSelection()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "POD MENU",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                if (userRole == UserRole.ADMIN) {
                    NavigationDrawerItem(
                        label = { Text("OBJEDNÁVKY") },
                        selected = currentRoute == "zakaznici" && zakazniciTab == 0,
                        icon = { Icon(Icons.Default.Email, null) },
                        onClick = {
                            navigateToSubMenu("objednavky")
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                if (userRole == UserRole.ADMIN || userRole == UserRole.DEPO) {
                    NavigationDrawerItem(
                        label = { Text("KONTEJNERY") },
                        selected = currentRoute == "seznam",
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        onClick = {
                            navigateToSubMenu("kontejnery")
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                NavigationDrawerItem(
                    label = { Text("PŘEPRAVA") },
                    selected = currentRoute == "preprava",
                    icon = { Icon(Icons.Default.LocalShipping, null) },
                    onClick = {
                        navigateToSubMenu("preprava")
                        scope.launch { drawerState.close() }
                    }
                )

                if (userRole == UserRole.ADMIN || userRole == UserRole.DEPO) {
                    NavigationDrawerItem(
                        label = { Text("ZÁJEMCI (POPTÁVKY)") },
                        selected = currentRoute == "zajemci",
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        onClick = {
                            navigateToSubMenu("zajemci")
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("INVENTURA") },
                        selected = currentRoute == "inventury" || currentRoute == "rychla_inventura",
                        icon = { Icon(Icons.Default.Inventory, null) },
                        onClick = {
                            navigateToSubMenu("inventura")
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                if (userRole == UserRole.ADMIN) {
                    NavigationDrawerItem(
                        label = { Text("ZAKÁZKY") },
                        selected = currentRoute == "zakaznici" && zakazniciTab == 2,
                        icon = { Icon(Icons.Default.Email, null) },
                        onClick = {
                            navigateToSubMenu("zakazky")
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                if (userRole == UserRole.ADMIN || userRole == UserRole.DEPO) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = if (userRole == UserRole.DEPO) "MENU NASTAVENÍ (DEPO)" else "MENU NASTAVENÍ (OSTATNÍ POLOŽKY)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("NASTAVENÍ (ROZBOČOVAČ)") },
                        selected = currentRoute == "nastaveni",
                        icon = { Icon(Icons.Default.Settings, null) },
                        onClick = {
                            navController.navigate("nastaveni") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                if (userRole == UserRole.ADMIN) {
                    NavigationDrawerItem(
                        label = { Text("NABÍDKY") },
                        selected = currentRoute == "zakaznici" && zakazniciTab == 1,
                        icon = { Icon(Icons.Default.Email, null) },
                        onClick = {
                            zakazniciTab = 1
                            navController.navigate("zakaznici") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("FAKTURY") },
                        selected = currentRoute == "zakaznici" && zakazniciTab == 4,
                        icon = { Icon(Icons.Default.Email, null) },
                        onClick = {
                            zakazniciTab = 4
                            navController.navigate("zakaznici") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("ZÁLOHOVÉ") },
                        selected = currentRoute == "zakaznici" && zakazniciTab == 5,
                        icon = { Icon(Icons.Default.Email, null) },
                        onClick = {
                            zakazniciTab = 5
                            navController.navigate("zakaznici") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("ADRESÁŘ") },
                        selected = currentRoute == "zakaznici" && zakazniciTab == 3,
                        icon = { Icon(Icons.Default.Email, null) },
                        onClick = {
                            zakazniciTab = 3
                            navController.navigate("zakaznici") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("POLOŽKY") },
                        selected = currentRoute == "polozky",
                        icon = { Icon(Icons.Default.Inventory, null) },
                        onClick = {
                            navController.navigate("polozky") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("PRONÁJEM") },
                        selected = currentRoute == "pronajem",
                        icon = { Icon(Icons.Default.Home, null) },
                        onClick = {
                            navController.navigate("pronajem") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                if (userRole == UserRole.ADMIN || userRole == UserRole.DEPO) {
                    NavigationDrawerItem(
                        label = { Text("ARCHIV") },
                        selected = currentRoute == "archiv",
                        icon = { Icon(Icons.Default.Archive, null) },
                        onClick = {
                            navController.navigate("archiv") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("INVENTURY") },
                        selected = currentRoute == "inventury",
                        icon = { Icon(Icons.Default.Inventory, null) },
                        onClick = {
                            navController.navigate("inventury") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("ROZDÍLY") },
                        selected = currentRoute == "rozdily",
                        icon = { Icon(Icons.Default.Difference, null) },
                        onClick = {
                            navController.navigate("rozdily") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("UDÁLOSTI") },
                        selected = currentRoute == "udalosti",
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        onClick = {
                            navController.navigate("udalosti") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )
                    val drawerProgramBadge = rememberUnifiedVersionBadge()
                    NavigationDrawerItem(
                        label = { Text("PROGRAM • $drawerProgramBadge") },
                        selected = currentRoute == "program",
                        icon = { Icon(Icons.Default.Settings, null) },
                        onClick = {
                            navController.navigate("program") { launchSingleTop = true }
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("iPHONE (APLIKACE iOS)", fontWeight = FontWeight.Bold)
                                Surface(
                                    color = Color(0xFF0284C7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "iOS",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        selected = false,
                        icon = { Icon(Icons.Default.PhoneIphone, contentDescription = "Apple iPhone", tint = Color(0xFF0284C7)) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            showIphoneDialog = true
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                NavigationDrawerItem(
                    label = { Text("ZAMKNOUT APLIKACI", fontWeight = FontWeight.Bold) },
                    selected = false,
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Zamknout", tint = Color(0xFF0284C7)) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLockApp()
                    }
                )

                NavigationDrawerItem(
                    label = { Text("ODHLÁSIT SE", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Odhlásit se", tint = Color(0xFFDC2626)) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pohodaWorkspaceBg)
                .navigationBarsPadding()
        ) {
            // Horní systémový status bar
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color(0xFF0F172A))
            )

            if (currentRoute != "home" && !currentRoute.startsWith("detail")) {
                val isSettingsMenuSelected = (userRole != UserRole.PREPRAVCE) && (
                    currentRoute == "nastaveni" ||
                    currentRoute == "polozky" ||
                    currentRoute == "pronajem" ||
                    currentRoute == "archiv" ||
                    currentRoute == "rozdily" ||
                    currentRoute == "udalosti" ||
                    currentRoute == "upravy" ||
                    currentRoute == "sdileni" ||
                    currentRoute == "program" ||
                    (currentRoute == "zakaznici" && zakazniciTab in listOf(1, 3, 4, 5))
                )

                val isDepo1Active = !isSettingsMenuSelected && (currentDepo == "Depo 1" || currentDepo == null)
                val isDepo2Active = !isSettingsMenuSelected && currentDepo == "Depo 2"

                val topNavRowHeight = 40.dp

                // ==================== 1. HLAVNÍ MENU: DEPO 1 | DEPO 2 | NASTAVENÍ | ROLE ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topNavRowHeight)
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDepo1Active) Color(0xFF1565C0)
                                else if (currentDepo == "Depo 1" || currentDepo == null) Color(0xFF1E3A5F)
                                else Color(0xFF1E293B)
                            )
                            .clickable {
                                viewModel.setCurrentDepo("Depo 1")
                                if (isSettingsMenuSelected) {
                                    navigateToSubMenu(lastMainSubRoute)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DEPO 1",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (currentDepo == "Depo 1" || currentDepo == null) Color.White else Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (currentDepo == "Depo 1" || currentDepo == null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color(0xFF38BDF8), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDepo2Active) Color(0xFFB45309)
                                else if (currentDepo == "Depo 2") Color(0xFF78350F)
                                else Color(0xFF33261A)
                            )
                            .clickable {
                                viewModel.setCurrentDepo("Depo 2")
                                if (isSettingsMenuSelected) {
                                    navigateToSubMenu(lastMainSubRoute)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DEPO 2",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (currentDepo == "Depo 2") Color.White else Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (currentDepo == "Depo 2") {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color(0xFFFBBF24), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            )
                        }
                    }

                    if (userRole == UserRole.ADMIN || userRole == UserRole.DEPO) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSettingsMenuSelected) Color(0xFF0284C7) else Color(0xFF1E293B)
                                )
                                .clickable {
                                    navController.navigate("nastaveni") { launchSingleTop = true }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NASTAVENÍ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSettingsMenuSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isSettingsMenuSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                )
                            }
                        }
                    }

                    // Tlačítko rychlé změny role uživatele (Admin / Depo / Přepravce)
                    val roleBadgeBg = when (userRole) {
                        UserRole.ADMIN -> Color(0xFF312E81)
                        UserRole.DEPO -> Color(0xFF065F46)
                        UserRole.PREPRAVCE -> Color(0xFF9A3412)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(roleBadgeBg)
                            .clickable { viewModel.reopenRoleSelection() }
                            .padding(horizontal = 8.dp)
                            .testTag("switch_role_top_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Změnit uživatele",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = userRole.shortLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (!isSettingsMenuSelected) {
                    // ==================== 2. POD MENU DLE ROLE ====================
                    val isObjednavkySelected = currentRoute == "zakaznici" && zakazniciTab == 0
                    val isPrepravaSelected = currentRoute == "preprava"
                    val isKontejnerySelected = currentRoute == "seznam"
                    val isInventuraSelected = currentRoute == "inventury" || currentRoute == "rychla_inventura"

                    val subBarBg = if (isDepo2) Color(0xFF4A2C0A) else Color(0xFF102A45)
                    val activeSubBg = if (isDepo2) Color(0xFFD97706) else Color(0xFF0284C7)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topNavRowHeight)
                            .background(subBarBg)
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        @Composable
                        fun RowScope.SubMenuTab(
                            label: String,
                            selected: Boolean,
                            onClick: () -> Unit
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) activeSubBg else Color.Transparent)
                                    .clickable(onClick = onClick)
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center
                                )
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .background(Color.White, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    )
                                }
                            }
                        }

                        when (userRole) {
                            UserRole.ADMIN -> {
                                SubMenuTab(
                                    label = "OBJEDN. ($objednavkyCount)",
                                    selected = isObjednavkySelected,
                                    onClick = { navigateToSubMenu("objednavky") }
                                )
                                SubMenuTab(
                                    label = "KONTEJN. ($kontejneryCount)",
                                    selected = isKontejnerySelected,
                                    onClick = { navigateToSubMenu("kontejnery") }
                                )
                                SubMenuTab(
                                    label = "ZÁJEMCI ($zajemciCount)",
                                    selected = currentRoute == "zajemci",
                                    onClick = { navigateToSubMenu("zajemci") }
                                )
                                SubMenuTab(
                                    label = "PŘEPRAVA ($prepravaCount)",
                                    selected = isPrepravaSelected,
                                    onClick = { navigateToSubMenu("preprava") }
                                )
                                SubMenuTab(
                                    label = "INVENTURA ($inventuraCount)",
                                    selected = isInventuraSelected,
                                    onClick = { navigateToSubMenu("inventura") }
                                )
                            }
                            UserRole.DEPO -> {
                                SubMenuTab(
                                    label = "KONTEJNERY ($kontejneryCount)",
                                    selected = isKontejnerySelected,
                                    onClick = { navigateToSubMenu("kontejnery") }
                                )
                                SubMenuTab(
                                    label = "PŘEPRAVA ($prepravaCount)",
                                    selected = isPrepravaSelected,
                                    onClick = { navigateToSubMenu("preprava") }
                                )
                                SubMenuTab(
                                    label = "ZÁJEMCI ($zajemciCount)",
                                    selected = currentRoute == "zajemci",
                                    onClick = { navigateToSubMenu("zajemci") }
                                )
                                SubMenuTab(
                                    label = "INVENTURA ($inventuraCount)",
                                    selected = isInventuraSelected,
                                    onClick = { navigateToSubMenu("inventura") }
                                )
                            }
                            UserRole.PREPRAVCE -> {
                                SubMenuTab(
                                    label = "PŘEPRAVA ($prepravaCount)",
                                    selected = true,
                                    onClick = { navigateToSubMenu("preprava") }
                                )
                            }
                        }
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = startDest,
                modifier = Modifier.weight(1f)
            ) {
                composable("home") {
                    HomeScreen(
                        onDepoSelected = { depo ->
                            viewModel.setCurrentDepo(depo)
                            when (userRole) {
                                UserRole.ADMIN -> navigateToSubMenu("objednavky")
                                UserRole.DEPO -> navigateToSubMenu("kontejnery")
                                UserRole.PREPRAVCE -> navigateToSubMenu("preprava")
                            }
                        },
                        onPolozkySelected = {
                            if (userRole == UserRole.ADMIN) {
                                navController.navigate("polozky") {
                                    popUpTo("home") { inclusive = false }
                                }
                            }
                        }
                    )
                }
                composable("polozky") {
                    PolozkyScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("seznam") {
                    MainScreen(
                        viewModel = viewModel,
                        onNavigateToCamera = onNavigateToCamera,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } },
                        onNavigateToDetail = { id ->
                            navController.navigate("detail/$id")
                        }
                    )
                }
                composable("detail/{id}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")
                    if (id != null) {
                        DetailScreen(
                            containerId = id,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable("archiv") {
                    ArchivScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } },
                        onNavigateToDetail = { id -> navController.navigate("detail/$id") }
                    )
                }
                composable("pronajem") {
                    PronajemScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } },
                        onNavigateToDetail = { id -> navController.navigate("detail/$id") }
                    )
                }
                composable("preprava") {
                    PrepravaScreen(
                        viewModel = viewModel,
                        onOpenDrawer = {
                            if (userRole == UserRole.PREPRAVCE) {
                                scope.launch { drawerState.open() }
                            } else {
                                navController.navigate("nastaveni") { launchSingleTop = true }
                            }
                        }
                    )
                }
                composable("inventury") {
                    InventuryScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("rychla_inventura") {
                    RychlaInventuraScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("rozdily") {
                    RozdilyScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("program") {
                    ProgramScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("udalosti") {
                    UdalostiScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("upravy") {
                    UpravyScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("sdileni") {
                    SdileniScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } }
                    )
                }
                composable("zakaznici") {
                    ZakazniciScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { navController.navigate("nastaveni") { launchSingleTop = true } },
                        initialTab = zakazniciTab,
                        onTabChanged = { zakazniciTab = it }
                    )
                }
                composable("zajemci") {
                    ZajemciScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToNabidky = {
                            if (userRole == UserRole.ADMIN) {
                                zakazniciTab = 1
                                navController.navigate("zakaznici") { launchSingleTop = true }
                            }
                        }
                    )
                }
                composable("nastaveni") {
                    NastaveniScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToRoute = { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        },
                        onNavigateToZakazniciTab = { tab ->
                            if (userRole == UserRole.ADMIN) {
                                zakazniciTab = tab
                                navController.navigate("zakaznici") { launchSingleTop = true }
                            }
                        },
                        onOpenHtmlDialog = { if (userRole == UserRole.ADMIN) showHtmlDialog = true },
                        onOpenWebPreview = { if (userRole == UserRole.ADMIN) showWebPreview = true },
                        onLockApp = onLockApp,
                        onChangeRole = { viewModel.reopenRoleSelection() }
                    )
                }
            }
        }
    }
}

@Composable
fun UserRoleSelectionScreen(
    currentRole: UserRole,
    onSelectRole: (UserRole) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Horní hlavička
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF163656))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFF011C45),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "K",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Column {
                    Text(
                        text = "VÝBĚR UŽIVATELE",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Zvolte režim přístupu zaměstnance",
                        color = Color(0xFFBAE6FD),
                        fontSize = 11.sp
                    )
                }
            }
            val unifiedVersionBadge = rememberUnifiedVersionBadge()
            Surface(
                color = Color(0xFF0284C7),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = unifiedVersionBadge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFDCE6F2))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Kdo s aplikací pracuje?",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )

            // 1. ADMIN
            RoleOptionCard(
                title = "1. Admin",
                subtitle = "Vidí a edituje vše v celé aplikaci",
                sections = listOf(
                    "OBJEDNÁVKY",
                    "KONTEJNERY",
                    "PŘEPRAVA",
                    "ZÁJEMCI",
                    "INVENTURA",
                    "ZAKÁZKY & FAKTURY",
                    "KOMPLETNÍ NASTAVENÍ"
                ),
                icon = Icons.Default.AdminPanelSettings,
                accentColor = Color(0xFF1565C0),
                isLastSelected = currentRole == UserRole.ADMIN,
                testTag = "role_admin_card",
                onClick = { onSelectRole(UserRole.ADMIN) }
            )

            // 2. DEPO (správce DEPA)
            RoleOptionCard(
                title = "2. DEPO (správce DEPA)",
                subtitle = "Vidí a edituje agendy správy depa:",
                sections = listOf(
                    "KONTEJNERY",
                    "PŘEPRAVA",
                    "ZÁJEMCI",
                    "INVENTURA",
                    "NASTAVENÍ (související agendy)"
                ),
                icon = Icons.Default.Inventory,
                accentColor = Color(0xFF047857),
                isLastSelected = currentRole == UserRole.DEPO,
                testTag = "role_depo_card",
                onClick = { onSelectRole(UserRole.DEPO) }
            )

            // 3. PŘEPRAVCE
            RoleOptionCard(
                title = "3. PŘEPRAVCE",
                subtitle = "Vidí a edituje pouze agendu dopravy:",
                sections = listOf(
                    "PŘEPRAVA"
                ),
                icon = Icons.Default.LocalShipping,
                accentColor = Color(0xFFEA580C),
                isLastSelected = currentRole == UserRole.PREPRAVCE,
                testTag = "role_prepravce_card",
                onClick = { onSelectRole(UserRole.PREPRAVCE) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Poznámka: Zvoleného uživatele můžete kdykoliv přepnout v horní liště pomocí tlačítka s názvem role.",
                fontSize = 11.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    subtitle: String,
    sections: List<String>,
    icon: ImageVector,
    accentColor: Color,
    isLastSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(if (isLastSelected) 2.5.dp else 1.5.dp, if (isLastSelected) accentColor else accentColor.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accentColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
                if (isLastSelected) {
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, accentColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "AKTIVNÍ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor
                            )
                        }
                    }
                }
            }

            // Přehled přístupných sekcí
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sections.chunked(2).forEach { rowSecs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowSecs.forEach { sec ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = accentColor.copy(alpha = 0.09f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "• $sec",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (rowSecs.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("${testTag}_button")
            ) {
                Text(
                    text = "Spustit jako ${title.substringAfter(". ").uppercase()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

