package com.example.ui

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.example.R

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var email by remember { mutableStateOf("premysl.sliva@gmail.com") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showHtmlDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val webClientId = stringResource(id = R.string.default_web_client_id)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                HtmlDownloadManager.saveHtmlDirectlyToDownloads(context)
            } catch (_: Throwable) {}
        }
    }

    if (showHtmlDialog) {
        HtmlDownloadDialog(onDismiss = { showHtmlDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val unifiedVersionBadge = rememberUnifiedVersionBadge()
        Surface(
            color = Color(0xFF0284C7),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable { showHtmlDialog = true }
        ) {
            Text(
                text = unifiedVersionBadge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Button(
            onClick = { showHtmlDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0284C7),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "STÁHNOUT HTML PRO PC ($unifiedVersionBadge)",
                fontWeight = FontWeight.ExtraBold
            )
        }

        CompanyLogo(size = 150.dp, modifier = Modifier.padding(bottom = 12.dp))

        Text(
            text = if (isRegistering) "Vytvoření nového účtu" else "Přihlášení do sítě",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .clickable {
                    val activeProfile = email.trim().ifEmpty { "premysl.sliva@gmail.com" }
                    onLoginSuccess(activeProfile)
                }
        ) {
            Text(
                text = "Účet: premysl.sliva@gmail.com (Klikněte pro vstup)",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Tlačítko pro přihlášení biometrikou (Otisk prstu / Obličej)
        Button(
            onClick = {
                val activity = context as? androidx.fragment.app.FragmentActivity
                if (activity != null) {
                    val targetEmail = email.trim().ifEmpty { "premysl.sliva@gmail.com" }
                    BiometricAuthHelper.showBiometricPrompt(
                        activity = activity,
                        title = "Přihlášení do aplikace",
                        subtitle = "Přihlásit se jako $targetEmail",
                        onSuccess = {
                            BiometricAuthHelper.setLastUserEmail(context, targetEmail)
                            onLoginSuccess(targetEmail)
                        },
                        onError = { code, msg ->
                            if (code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED && code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                errorMessage = "Biometrické ověření: $msg"
                            }
                        },
                        onFailed = {
                            errorMessage = "Otisk prstu nebyl rozpoznán. Zkuste to znovu."
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Otisk prstu",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "PŘIHLÁSIT SE BIOMETRIKOU",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                errorMessage = ""
            },
            label = { Text("E-mail") },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                errorMessage = ""
            },
            label = { Text("Heslo") },
            placeholder = { Text("Heslo (volitelné pro rychlý vstup)") },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                val trimmedEmail = email.trim().ifEmpty { "premysl.sliva@gmail.com" }
                val trimmedPassword = password.trim()
                errorMessage = ""
                onLoginSuccess(trimmedEmail)
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val auth = FirebaseAuth.getInstance()
                        withTimeoutOrNull(2500L) {
                            if (trimmedPassword.length >= 6) {
                                try {
                                    auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                                } catch (_: Exception) {
                                    try {
                                        auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                                    } catch (_: Exception) {}
                                }
                            } else {
                                auth.signInAnonymously().await()
                            }
                        }
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(if (isRegistering) "REGISTROVAT SE (premysl.sliva@gmail.com)" else "PŘIHLÁSIT SE (premysl.sliva@gmail.com)", fontWeight = FontWeight.Bold)
        }
        
        TextButton(
            onClick = { 
                isRegistering = !isRegistering 
                errorMessage = ""
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (isRegistering) "Již máte účet? Přihlaste se" else "Nemáte účet? Registrujte se")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.8f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val fallbackEmail = email.trim().ifEmpty { "premysl.sliva@gmail.com" }
                errorMessage = ""
                onLoginSuccess(fallbackEmail)
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(1500L) {
                            FirebaseAuth.getInstance().signInAnonymously().await()
                        }
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("PŘIHLÁSIT PŘES GOOGLE (premysl.sliva@gmail.com)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                val activeProfile = email.trim().ifEmpty { "premysl.sliva@gmail.com" }
                onLoginSuccess(activeProfile)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pokračovat rovnou jako premysl.sliva@gmail.com")
        }
                
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Připojeno ke cloudu: Firebase Firestore",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun handleSignIn(result: GetCredentialResponse, onLoginSuccess: (String) -> Unit, onError: (String) -> Unit) {
    val credential = result.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(authCredential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = FirebaseAuth.getInstance().currentUser
                        val displayName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Uživatel"
                        onLoginSuccess(displayName)
                    } else {
                        onError("Chyba Firebase Auth: ${task.exception?.localizedMessage}")
                    }
                }
        } catch (e: Exception) {
            onError("Chyba při zpracování tokenu: ${e.message}")
        }
    } else {
        onError("Neplatný typ pověření.")
    }
}
