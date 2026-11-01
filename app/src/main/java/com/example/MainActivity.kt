package com.example

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Aplikace potřebuje povolení k fotoaparátu.", Toast.LENGTH_LONG).show()
            }
        }

    private var lastInteractionTime = System.currentTimeMillis()
    private val TIMEOUT_MS = 5 * 60 * 1000L
    private val lockTriggered = mutableStateOf(false)

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        // Keep screen on for 5 minutes (300,000 ms) after resume/unlock
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.postDelayed({
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }, 300000L)

        // Check inactivity timeout (5 minutes)
        if (System.currentTimeMillis() - lastInteractionTime > TIMEOUT_MS &&
            BiometricAuthHelper.isBiometricEnabled(this) &&
            BiometricAuthHelper.checkBiometricStatus(this) == BiometricStatus.AVAILABLE
        ) {
            lockTriggered.value = true
        }
        lastInteractionTime = System.currentTimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } catch (_: Throwable) {}

        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (_: Throwable) {}

        try {
            com.example.data.GitHubBackupManager.getLastUpdateVersion(this)
        } catch (_: Throwable) {}

        val repository = com.example.data.ContainerRepository()
        val factory = MainViewModelFactory(repository, filesDir, applicationContext)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("is_logged_in")) {
            prefs.edit().putBoolean("is_logged_in", true).apply()
        }
        val hasLoggedInBefore = prefs.getBoolean("is_logged_in", true)
        val isBiometricEnabled = BiometricAuthHelper.isBiometricEnabled(this)
        val isBiometricAvailable = BiometricAuthHelper.checkBiometricStatus(this) == BiometricStatus.AVAILABLE

        setContent {
            val viewModel: MainViewModel = viewModel(factory = factory)
            val currentDepo by viewModel.currentDepo.collectAsState()
            val themePreference by viewModel.themePreference.collectAsState()
            val isDark = when (themePreference) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                com.example.data.GitHubBackupManager.checkAndAutoUpdateOnStartup(applicationContext) {
                    viewModel.reloadLocalCaches()
                }
            }

            MyApplicationTheme(darkTheme = isDark, currentDepo = currentDepo) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val rootNavController = rememberNavController()

                    val initialDestination = when {
                        !hasLoggedInBefore && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null -> "login"
                        isBiometricEnabled && isBiometricAvailable -> "biometric_lock"
                        else -> "app_root"
                    }

                    // Handle background timeout lock
                    val shouldLock by lockTriggered
                    androidx.compose.runtime.LaunchedEffect(shouldLock) {
                        if (shouldLock) {
                            lockTriggered.value = false
                            rootNavController.navigate("biometric_lock") {
                                popUpTo("app_root") { inclusive = true }
                            }
                        }
                    }

                    NavHost(
                        navController = rootNavController,
                        startDestination = initialDestination
                    ) {
                        composable("biometric_lock") {
                            BiometricLockScreen(
                                userEmail = BiometricAuthHelper.getLastUserEmail(this@MainActivity),
                                onUnlockSuccess = {
                                    rootNavController.navigate("app_root") {
                                        popUpTo("biometric_lock") { inclusive = true }
                                    }
                                },
                                onFallbackToPasswordLogin = {
                                    rootNavController.navigate("login") {
                                        popUpTo("biometric_lock") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = { username ->
                                    val finalUser = username.ifBlank { "premysl.sliva@gmail.com" }
                                    viewModel.setUserName(finalUser)
                                    BiometricAuthHelper.setLastUserEmail(this@MainActivity, finalUser)
                                    prefs.edit().putBoolean("is_logged_in", true).commit()
                                    rootNavController.navigate("app_root") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("app_root") {
                            AppNavigation(
                                viewModel = viewModel,
                                onNavigateToCamera = {
                                    rootNavController.navigate("camera")
                                },
                                onLockApp = {
                                    rootNavController.navigate("biometric_lock") {
                                        popUpTo("app_root") { inclusive = true }
                                    }
                                },
                                onLogout = {
                                    prefs.edit().putBoolean("is_logged_in", false).commit()
                                    try {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                    } catch (_: Exception) {}
                                    rootNavController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("camera") {
                            CameraScreen(
                                outputDirectory = filesDir,
                                onImageCaptured = { file ->
                                    viewModel.analyzeAndSave(file)
                                    runOnUiThread {
                                        rootNavController.popBackStack()
                                    }
                                },
                                onError = { exception ->
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "Chyba: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        rootNavController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
