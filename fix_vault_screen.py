import re

filepath = "app/src/main/java/com/example/ui/screens/VaultScreen.kt"
with open(filepath, "r") as f:
    content = f.read()

imports = """
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import android.widget.Toast
"""

if "androidx.biometric.BiometricPrompt" not in content:
    content = content.replace("import androidx.compose.runtime.Composable", imports + "\nimport androidx.compose.runtime.Composable")

# Add biometric logic inside VaultScreen Composable
biometric_logic = """
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    
    fun authenticateWithBiometrics() {
        if (fragmentActivity != null) {
            val executor = ContextCompat.getMainExecutor(fragmentActivity)
            val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Unlock vault
                        viewModel.unlockVaultWithBiometrics()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use PIN")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(context, "Biometric authentication not supported", Toast.LENGTH_SHORT).show()
        }
    }
"""

if "authenticateWithBiometrics" not in content:
    content = content.replace("val changePinNew by viewModel.changePinNew.collectAsState()", "val changePinNew by viewModel.changePinNew.collectAsState()\n" + biometric_logic)
    
login_button_replacement = """
                        Button(
                            onClick = { authenticateWithBiometrics() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Use Biometrics")
                        }
                        Button(
                            onClick = { viewModel.unlockVault() },
"""

content = content.replace("Button(\n                            onClick = { viewModel.unlockVault() },", login_button_replacement)

with open(filepath, "w") as f:
    f.write(content)
