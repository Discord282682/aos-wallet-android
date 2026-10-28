package com.austland.aos.wallet.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.austland.aos.wallet.crypto.HexUtils
import com.austland.aos.wallet.data.WalletStore

private enum class OnboardingMode { CHOOSE, IMPORT }

@Composable
fun OnboardingScreen(walletStore: WalletStore, onWalletReady: () -> Unit) {
    var mode by remember { mutableStateOf(OnboardingMode.CHOOSE) }
    var importKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            "KINGDOM OF AUSTLAND • ESTBRUGH TREASURY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Austlander Ounce Stralen (Au5)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Self-custodial Ed25519 wallet & Proof-of-Work miner. Zero transaction fees, 67,000,000 AOS hard cap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (mode) {
                    OnboardingMode.CHOOSE -> {
                        Button(
                            onClick = {
                                creating = true
                                walletStore.createNewWallet()
                                onWalletReady()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !creating
                        ) {
                            if (creating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Create New Au5 Wallet", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        OutlinedButton(onClick = { mode = OnboardingMode.IMPORT }, modifier = Modifier.fillMaxWidth()) {
                            Text("Import Existing 64-Hex Private Key")
                        }
                    }

                    OnboardingMode.IMPORT -> {
                        Text(
                            "Paste your 64-character lowercase hex Ed25519 seed (compatible with test_tx.py wallet files).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = importKey,
                            onValueChange = { importKey = it.trim(); error = null },
                            label = { Text("Private key (64 hex chars)") },
                            singleLine = false,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            isError = error != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(Modifier.size(16.dp))
                        Button(
                            onClick = {
                                val cleaned = importKey.lowercase()
                                if (!HexUtils.isLowercaseHex(cleaned, 64)) {
                                    error = "Must be exactly 64 lowercase hex characters."
                                    return@Button
                                }
                                try {
                                    walletStore.importWallet(cleaned)
                                    onWalletReady()
                                } catch (e: Exception) {
                                    error = e.message ?: "Couldn't import that key."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import Au5 Wallet", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(
                            onClick = { mode = OnboardingMode.CHOOSE; error = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Back") }
                    }
                }
            }
        }
    }
}
