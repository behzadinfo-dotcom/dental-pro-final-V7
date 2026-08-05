package com.example.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.DentalViewModel

@Composable
fun LockScreen(
    viewModel: DentalViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val isPersian by viewModel.isPersian.collectAsState()
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Locked",
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (isPersian) "ورود امن به نرم‌افزار مطب" else "Secure Clinic Lock Screen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isPersian) "رمز ورود ۴ رقمی خود را وارد کرده یا از اثرانگشت استفاده کنید" else "Enter your 4-digit PIN or use Biometric scan",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(24.dp))

            // PIN Input box
            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    if (it.length <= 4) {
                        pinInput = it
                        pinError = false
                        if (it.length == 4) {
                            if (viewModel.unlockAppWithPin(it)) {
                                onUnlocked()
                            } else {
                                pinError = true
                            }
                        }
                    }
                },
                label = { Text(if (isPersian) "رمز ورود ۴ رقمی" else "4-Digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = pinError,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(220.dp)
            )

            if (pinError) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isPersian) "رمز اشتباه است. دوباره تلاش کنید" else "Incorrect PIN code. Try again",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // Biometric prompt trigger
            Button(
                onClick = {
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        val executor = ContextCompat.getMainExecutor(context)
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(if (isPersian) "ورود به نرم‌افزار مطب" else "Clinic App Login")
                            .setSubtitle(if (isPersian) "احراز هویت بیومتریک" else "Biometric Scan")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            .build()

                        val biometricPrompt = BiometricPrompt(activity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    viewModel.isAppUnlocked.value = true
                                    onUnlocked()
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    pinError = true
                                }
                            })

                        biometricPrompt.authenticate(promptInfo)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isPersian) "ورود با اثر انگشت / تشخیص چهره" else "Biometric Unlock", fontWeight = FontWeight.Bold)
            }
        }
    }
}

