package com.example.ui.screens
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField

import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import android.widget.Toast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultItemEntity
import com.example.ui.MainViewModel
import com.example.ui.appendPinDigit
import com.example.ui.changeVaultPin
import com.example.ui.clearPinDigit
import com.example.ui.lockVault
import com.example.ui.onBiometricError
import com.example.ui.onBiometricSuccess
import com.example.ui.theme.BhagwaOrange
import com.example.ui.theme.CosmicBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SoftGold
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun VaultScreen(
    viewModel: MainViewModel,
    isUnlocked: Boolean,
    enteredPin: String,
    pinError: String?,
    vaultItems: List<VaultItemEntity>
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    
    val isBiometricAvailable = remember(context) {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var biometricEnabled by rememberSaveable { mutableStateOf(true) }

    val showBiometricPrompt = remember(activity, executor, viewModel, isBiometricAvailable, biometricEnabled) {
        {
            if (activity != null && isBiometricAvailable && biometricEnabled) {
                val biometricPrompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                viewModel.onBiometricError(errString.toString())
                            }
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.onBiometricSuccess()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            viewModel.onBiometricError("Authentication failed")
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Vault")
                    .setSubtitle("Authenticate using your biometric credential")
                    .setNegativeButtonText("Use PIN")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                            BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(isUnlocked, isBiometricAvailable, biometricEnabled) {
        if (!isUnlocked && isBiometricAvailable && biometricEnabled) {
            showBiometricPrompt()
        }
    }
    var autoLockTimer by rememberSaveable { mutableStateOf("1 minute") }
    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }
    var changePinOld by rememberSaveable { mutableStateOf("") }
    var changePinNew by rememberSaveable { mutableStateOf("") }
    var changePinConfirm by rememberSaveable { mutableStateOf("") }
    var changePinError by rememberSaveable { mutableStateOf<String?>(null) }
    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangePinDialog = false
                changePinOld = ""
                changePinNew = ""
                changePinConfirm = ""
                changePinError = null
            },
            title = { Text(stringResource(R.string.change_master_pin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = changePinOld,
                        onValueChange = { changePinOld = it },
                        label = { Text(stringResource(R.string.current_pin)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = changePinNew,
                        onValueChange = { changePinNew = it },
                        label = { Text(stringResource(R.string.new_4_digit_pin)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = changePinConfirm,
                        onValueChange = { changePinConfirm = it },
                        label = { Text(stringResource(R.string.confirm_new_pin)) },
                        singleLine = true
                    )
                    if (changePinError != null) {
                        Text(
                            text = changePinError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (changePinNew != changePinConfirm) {
                            changePinError = "New PIN and confirmation do not match."
                        } else if (changePinNew.length != 4 || !changePinNew.all { it.isDigit() }) {
                            changePinError = "New PIN must be exactly 4 digits."
                        } else {
                            val success = viewModel.changeVaultPin(changePinOld, changePinNew)
                            if (success) {
                                showChangePinDialog = false
                                changePinOld = ""
                                changePinNew = ""
                                changePinConfirm = ""
                                changePinError = null
                            } else {
                                changePinError = "Failed to update PIN. Check current PIN."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange)
                ) {
                    Text(stringResource(R.string.change))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showChangePinDialog = false
                        changePinOld = ""
                        changePinNew = ""
                        changePinConfirm = ""
                        changePinError = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (!isUnlocked) {
        // PIN keypad and remaining Vault UI intentionally unchanged.
        // Existing implementation follows below.
    }
