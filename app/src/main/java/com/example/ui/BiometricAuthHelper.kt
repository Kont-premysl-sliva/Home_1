package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricStatus {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    UNSUPPORTED
}

object BiometricAuthHelper {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_BIOMETRIC_AUTO_PROMPT = "biometric_auto_prompt"
    private const val KEY_APP_LOCKED = "is_app_locked"
    private const val KEY_LAST_USER = "last_user_email"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        // Defaults to true so users can immediately use fingerprint/face unlock
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isAutoPromptEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_AUTO_PROMPT, true)
    }

    fun setAutoPromptEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_AUTO_PROMPT, enabled).apply()
    }

    fun isAppLocked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_APP_LOCKED, false)
    }

    fun setAppLocked(context: Context, locked: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_LOCKED, locked).apply()
    }

    fun getLastUserEmail(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_USER, "premysl.sliva@gmail.com") ?: "premysl.sliva@gmail.com"
    }

    fun setLastUserEmail(context: Context, email: String) {
        if (email.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_LAST_USER, email.trim()).apply()
        }
    }

    fun checkBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun getStatusDescription(status: BiometricStatus): String {
        return when (status) {
            BiometricStatus.AVAILABLE -> "Biometrie je připravena (Otisk prstu / Face ID / Zámek zařízení)"
            BiometricStatus.NONE_ENROLLED -> "V systému Android není nastaven žádný otisk prstu ani zámek obrazovky"
            BiometricStatus.NO_HARDWARE -> "Zařízení nepodporuje biometrické snímače"
            BiometricStatus.HARDWARE_UNAVAILABLE -> "Biometrický snímač je dočasně nedostupný"
            BiometricStatus.UNSUPPORTED -> "Biometrie není na tomto zařízení plně podporována"
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Přihlášení do aplikace",
        subtitle: String = "Ověřte se otiskem prstu nebo obličejem",
        description: String = "Kontejnery Ostrava v$APP_VERSION_NAME",
        allowDeviceCredential: Boolean = true,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                setAppLocked(activity, false)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)

        if (allowDeviceCredential && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            promptInfoBuilder.setNegativeButtonText("Zadat heslo / Zrušit")
        }

        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            // Fallback in case device credential fails on specific vendor ROM
            try {
                val fallbackBuilder = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setNegativeButtonText("Zrušit")
                prompt.authenticate(fallbackBuilder.build())
            } catch (ex: Exception) {
                onError(-1, ex.message ?: "Chyba inicializace biometrie")
            }
        }
    }
}
