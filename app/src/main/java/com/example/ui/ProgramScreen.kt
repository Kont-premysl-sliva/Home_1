package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GitHubBackupManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showWebPreview by remember { mutableStateOf(false) }
    var showIphoneDialog by remember { mutableStateOf(false) }

    // GitHub konfigurace
    var repo by remember { mutableStateOf(GitHubBackupManager.getRepo(context)) }
    var branch by remember { mutableStateOf(GitHubBackupManager.getBranch(context)) }
    var token by remember { mutableStateOf(GitHubBackupManager.getToken(context)) }

    val highestVerNum = rememberHighestVersionNumber()
    val highestDateStr = rememberHighestVersionDate()
    val unifiedVersionBadge = rememberUnifiedVersionBadge()

    // Informace o nahrané aktualizaci
    var lastUpdateVersion by remember(highestVerNum) { mutableStateOf(highestVerNum.toString()) }
    var lastUpdateDate by remember(highestDateStr) { mutableStateOf(highestDateStr) }

    // Stav aktualizace a tachometru
    var isUpdating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(100f) }
    var updateStatusText by remember(unifiedVersionBadge, lastUpdateDate) {
        mutableStateOf("Stažená verze: $unifiedVersionBadge • ze dne $lastUpdateDate")
    }

    // Stav ověření GitHubu
    var isVerifying by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(true) }

    // Při otevření zkontrolujeme dostupnost nové verze na GitHubu; pokud je lokální verze novější, automaticky ji pošleme na GitHub
    LaunchedEffect(Unit) {
        val (hasNewer, _) = GitHubBackupManager.checkIfNewerVersionOnGitHub(context, repo, branch, token)
        if (hasNewer && !isUpdating) {
            isUpdating = true
            updateProgress = 0f
            updateStatusText = "Nalezena nová verze na GitHubu – stahuji..."
            val (success, details) = GitHubBackupManager.performSoftwareUpdateFromGitHub(
                context = context,
                repoInput = repo,
                branchInput = branch,
                tokenInput = token,
                onProgress = { pct, status ->
                    updateProgress = pct.toFloat()
                    updateStatusText = status
                }
            )
            isUpdating = false
            if (success) {
                lastUpdateVersion = details.version
                lastUpdateDate = details.date
                updateProgress = 100f
                val badge = formatUnifiedVersionBadge(details.version.toIntOrNull() ?: highestVerNum)
                updateStatusText = "Stažená verze: $badge • ze dne ${details.date}"
                viewModel.reloadLocalCaches()
                Toast.makeText(
                    context,
                    "Automaticky nainstalována $badge (${details.date}) z GitHubu!",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            val pushed = GitHubBackupManager.pushCurrentAppHtmlToGitHubIfNewer(context, repo, branch, token)
            if (pushed) {
                updateStatusText = "$unifiedVersionBadge automaticky odeslána na GitHub ($repo)"
            }
        }
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
        IphoneAppDialog(onDismiss = { showIphoneDialog = false })
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        "PROGRAM • $unifiedVersionBadge",
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
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val logoResId = remember {
                context.resources.getIdentifier("logo", "drawable", context.packageName)
            }
            if (logoResId != 0) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }

            // =========================================================================
            // HLAVNÍ SEKTOR: GITHUB SYNCHRONIZACE & AKTUALIZACE SOFTWARU (JEDINÁ VARIANTA)
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                border = BorderStroke(1.5.dp, Color(0xFF16A34A).copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // VODOROVNÉ TLAČÍTKO OVĚŘENÍ GITHUB (NIKDY SE NEROZHAZUJE NA VÍCE ŘÁDKŮ)
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                isVerifying = true
                                val res = GitHubBackupManager.testConnection(
                                    context = context,
                                    repoInput = repo,
                                    tokenInput = token
                                )
                                isVerified = res is GitHubBackupManager.GitHubResult.Success
                                isVerifying = false
                                Toast.makeText(
                                    context,
                                    if (isVerified) "✅ GitHub spojení ověřeno!" else "❌ Chyba ověření spojení",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isVerifying && !isUpdating,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isVerified) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        border = BorderStroke(1.2.dp, if (isVerified) Color(0xFF86EFAC) else Color(0xFFFDE68A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF15803D),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Ověření GitHub",
                                        tint = if (isVerified) Color(0xFF15803D) else Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "GitHub: $repo ($branch)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isVerified) Color(0xFF14532D) else Color(0xFF92400E),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                color = if (isVerified) Color(0xFF15803D) else Color(0xFFD97706),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVerified) Icons.Default.Check else Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isVerifying) "OVĚŘUJI..." else if (isVerified) "OVĚŘENO" else "OVĚŘIT",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // JEDINÉ TLAČÍTKO AKTUALIZACE Z GITHUBU
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isUpdating = true
                                updateProgress = 0f
                                updateStatusText = "Zahajuji aktualizaci z GitHubu..."

                                val (success, details) = GitHubBackupManager.performSoftwareUpdateFromGitHub(
                                    context = context,
                                    repoInput = repo,
                                    branchInput = branch,
                                    tokenInput = token,
                                    onProgress = { pct, status ->
                                        updateProgress = pct.toFloat()
                                        updateStatusText = status
                                    }
                                )

                                isUpdating = false
                                if (success) {
                                    lastUpdateVersion = details.version
                                    lastUpdateDate = details.date
                                    updateProgress = 100f
                                    val badge = formatUnifiedVersionBadge(details.version.toIntOrNull() ?: highestVerNum)
                                    updateStatusText = "Stažená verze: $badge • ze dne ${details.date}"
                                    viewModel.reloadLocalCaches()
                                    if (details.apkDownloaded && details.apkFile != null && (details.version.toIntOrNull() ?: 0) > APP_VERSION_NUMBER) {
                                        AppUpdateManager.installApk(context, details.apkFile)
                                    }
                                    Toast.makeText(
                                        context,
                                        "Software a databáze byly úspěšně zaktualizovány na $badge (${details.date})!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Chyba při aktualizaci: ${details.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isUpdating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isUpdating) "AKTUALIZUJI Z GITHUB..." else "AKTUALIZOVAT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // =========================================================================
            // TACHOMETR V PROCENTECH (KOLIK BYLO NAČTENO A ZAKTUALIZOVÁNO)
            // =========================================================================
            TachometerGauge(
                progress = updateProgress,
                statusText = updateStatusText
            )

            // =========================================================================
            // ČÍSLO VERZE A DATUM PO DOTOČENÍ RYCHLOMĚRU NA 100 %
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.2.dp, Color(0xFF86EFAC))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Stažená verze a datum:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                        }
                        Text(
                            text = if (updateProgress >= 100f) "$unifiedVersionBadge • $lastUpdateDate" else "Stahuji z GitHubu (${updateProgress.toInt()} %)...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF14532D)
                        )
                    }

                    // Tlačítka Terminál, iPhone a Reindexace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { showWebPreview = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0A2E6E),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "TERMINÁL",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { showIphoneDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1565C0),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.PhoneIphone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "iPHONE",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.reindexEntireDatabase { msg ->
                                    updateStatusText = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF991B1B),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "REINDEXACE",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // ROZDĚLENÍ REPOZITÁŘE NA 3 PLATFORMY (PC, ANDROID, IPHONE) – POSLEDNÍ 3 VERZE
            // =========================================================================
            GitHubThreePlatformsSection(
                currentVersion = highestVerNum,
                repo = repo,
                branch = branch,
                token = token
            )

            // =========================================================================
            // GITHUB ZÁLOHA A SYNCHRONIZACE DATABÁZE
            // =========================================================================
            ZalohovaniSection(viewModel)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun GitHubThreePlatformsSection(
    currentVersion: Int,
    repo: String,
    branch: String,
    token: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSyncingPlatforms by remember { mutableStateOf(false) }
    var syncNote by remember { mutableStateOf("") }
    val keptVersions = remember(currentVersion) {
        GitHubBackupManager.getKeptVersionsList(currentVersion, 3)
    }
    val versionsLabel = remember(keptVersions) {
        keptVersions.joinToString(", ") { "v$it" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.2.dp, Color(0xFF94A3B8))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = Color(0xFF0A2E6E),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Rozdělení GitHub repozitáře (3 platformy)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Automatické navýšení verze • Uloženy poslední 3 verze: $versionsLabel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            // 1. WEB PC Aplikace
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "1. WEB PC Aplikace (složka pc/)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color(0xFF1E3A8A)
                        )
                        Text(
                            text = "pc/index.html • pc/S_POHODA_Kontejnery_PC_v*.html",
                            fontSize = 10.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                    Surface(
                        color = Color(0xFF1D4ED8),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = versionsLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 2. Android verze
            Surface(
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. Android verze (složka android/)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color(0xFF14532D)
                        )
                        Text(
                            text = "android/version.json • android/Kontejnery_Ostrava_Android_v*.json",
                            fontSize = 10.sp,
                            color = Color(0xFF166534)
                        )
                    }
                    Surface(
                        color = Color(0xFF15803D),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = versionsLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 3. iPhone verze
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "3. iPhone verze (složka iphone/)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color(0xFF7C2D12)
                        )
                        Text(
                            text = "iphone/iphone.html • Kontejnery_iPhone_v*.html / .ipa / .mobileconfig",
                            fontSize = 10.sp,
                            color = Color(0xFF9A3412)
                        )
                    }
                    Surface(
                        color = Color(0xFFC2410C),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = versionsLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (syncNote.isNotBlank()) {
                Text(
                    text = syncNote,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isSyncingPlatforms = true
                        syncNote = "Kontroluji složky pc/, android/, iphone/ a nahrávám verzi $currentVersion na GitHub..."
                        val currentTk = GitHubBackupManager.getToken(context)
                        if (currentTk.isBlank()) {
                            isSyncingPlatforms = false
                            syncNote = "⚠️ Pro nahrání verze $versionsLabel na GitHub zadejte svůj Classic Token v záložce NASTAVENÍ (v horní liště)."
                            Toast.makeText(context, syncNote, Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        val pushed = GitHubBackupManager.pushCurrentAppHtmlToGitHubIfNewer(context, repo, branch, currentTk)
                        val deleted = GitHubBackupManager.pruneOldGitHubVersions(context, repo, branch, currentTk, maxKeep = 3)
                        isSyncingPlatforms = false
                        syncNote = if (pushed) {
                            "✅ Verze v$currentVersion byla úspěšně nahrána do složek pc/, android/, iphone/ na GitHub!" +
                                (if (deleted > 0) " • Smazáno starších souborů: $deleted" else "")
                        } else {
                            "✅ Složky pc/, android/, iphone/ na GitHubu zkontrolovány a synchronizovány!" +
                                (if (deleted > 0) " • Smazáno starších souborů: $deleted" else "")
                        }
                        Toast.makeText(context, syncNote, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isSyncingPlatforms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSyncingPlatforms) "NAHRÁVÁM & KONTROLUJI 3 SLOŽKY..." else "ZKONTROLOVAT 3 SLOŽKY & PONECHAT POSLEDNÍ 3 VERZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Plochý 2D tachometr v procentech ve formátu 4:4 (čtvercová kostička bez 3D efektu).
 */
@Composable
fun TachometerGauge(
    progress: Float,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "tachometer_progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.2.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ČTVERCOVÁ KOSTIČKA TACHOMETRU (přesný formát 4:4 = 1:1, čistý plochý 2D styl bez 3D efektů)
            Surface(
                modifier = Modifier.size(125.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.2.dp, if (animatedProgress >= 100f) Color(0xFF22C55E) else Color(0xFF38BDF8))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val center = Offset(w / 2f, h * 0.54f)
                        val radius = w * 0.36f

                        val startAngle = 145f
                        val sweepAngle = 250f
                        val strokeWidth = 8.dp.toPx()

                        // Plochý podkladový oblouk (čistý 2D, žádné stíny)
                        drawArc(
                            color = Color(0xFF334155),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Plochý aktivní oblouk postupu (jednolitá barva, bez 3D přechodů)
                        val activeSweep = (animatedProgress / 100f).coerceIn(0f, 1f) * sweepAngle
                        if (activeSweep > 0f) {
                            drawArc(
                                color = if (animatedProgress >= 100f) Color(0xFF22C55E) else Color(0xFF0284C7),
                                startAngle = startAngle,
                                sweepAngle = activeSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Plochá 2D ručička tachometru (žádná záře ani 3D stíny)
                        val needleAngleDeg = startAngle + (animatedProgress / 100f).coerceIn(0f, 1f) * sweepAngle
                        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
                        val needleLength = radius * 0.82f

                        val needleTipX = (center.x + needleLength * Math.cos(needleAngleRad)).toFloat()
                        val needleTipY = (center.y + needleLength * Math.sin(needleAngleRad)).toFloat()

                        drawLine(
                            color = if (animatedProgress >= 100f) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                            start = center,
                            end = Offset(needleTipX, needleTipY),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Plochý středový čep
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = center
                        )
                    }

                    // Digitální procenta uprostřed kostičky
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${animatedProgress.toInt()} %",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = if (animatedProgress >= 100f) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                        )
                        Text(
                            text = if (animatedProgress >= 100f) "100% HOTOVO" else "POSTUP",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Jednodušší status text / komentář ve formátu 4:4 (nízký profil, 1 řádek)
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { (animatedProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(11.dp),
                        color = if (animatedProgress >= 100f) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                        strokeWidth = 1.6.dp
                    )
                    val tachoFallbackBadge = rememberUnifiedVersionBadge()
                    Text(
                        text = statusText.ifBlank { "Připraveno ($tachoFallbackBadge)" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ZalohovaniSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRestoring by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<GitHubBackupManager.GitHubResult?>(null) }

    var repo by remember { mutableStateOf(GitHubBackupManager.getRepo(context)) }
    var branch by remember { mutableStateOf(GitHubBackupManager.getBranch(context)) }
    var token by remember { mutableStateOf(GitHubBackupManager.getToken(context)) }
    var showTokenField by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Záloha a Uložení Dat na GitHub",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF15803D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GitHub Wi-Fi Synchronizace Databáze",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF14532D)
                        )
                        Text(
                            text = "Kontejnery, Přepravy, Zákazníci, Zakázky a Faktury",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                    }
                    TextButton(onClick = { showTokenField = !showTokenField }) {
                        Text(
                            text = if (showTokenField) "Skrýt" else "Upravit Token",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                val tokenExpStatus = remember { GitHubBackupManager.getTokenExpirationStatus() }

                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                        Text(
                            text = "Přednastavený token aktivní • Expirace: ${tokenExpStatus.expirationDate} (zbývá ${tokenExpStatus.daysRemaining} dní)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                }

                if (showTokenField) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                GitHubBackupManager.setToken(context, it)
                                testResult = null
                            },
                            label = { Text("Token", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (restoreStatus.isNotBlank()) {
                    Text(
                        text = restoreStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isRestoring = true
                                restoreStatus = "Stahuji zálohu dat z GitHubu $repo přes Wi-Fi..."
                                val res = GitHubBackupManager.restoreFromGitHub(
                                    context = context,
                                    repoInput = repo,
                                    branchInput = branch,
                                    tokenInput = token,
                                    onStatus = { restoreStatus = it }
                                )
                                isRestoring = false
                                when (res) {
                                    is GitHubBackupManager.GitHubResult.Success -> {
                                        viewModel.reloadLocalCaches()
                                        restoreStatus = "✅ ${res.message}"
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                    is GitHubBackupManager.GitHubResult.Error -> {
                                        restoreStatus = "❌ ${res.message}"
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isRestoring && !isUploading,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isRestoring) "NAČÍTÁM..." else "NAČÍST",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isUploading = true
                                restoreStatus = "Nahrávám aktuální data na GitHub ($repo)..."
                                val res = GitHubBackupManager.uploadToGitHub(
                                    context = context,
                                    repoInput = repo,
                                    branchInput = branch,
                                    tokenInput = token,
                                    onStatus = { restoreStatus = it }
                                )
                                isUploading = false
                                when (res) {
                                    is GitHubBackupManager.GitHubResult.Success -> {
                                        restoreStatus = "✅ ${res.message}"
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                    is GitHubBackupManager.GitHubResult.Error -> {
                                        restoreStatus = "❌ ${res.message}"
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isRestoring && !isUploading,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isUploading) "UKLÁDÁM..." else "ULOŽIT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
