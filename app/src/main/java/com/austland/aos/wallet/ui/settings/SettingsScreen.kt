package com.austland.aos.wallet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.austland.aos.wallet.data.WalletStore
import com.austland.aos.wallet.ui.common.formatEpochSeconds
import com.austland.aos.wallet.work.WorkScheduler

@Composable
fun SettingsScreen(walletStore: WalletStore, onWalletDeleted: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyPair = remember { walletStore.currentKeyPair() }

    var showPrivateKey by remember { mutableStateOf(false) }
    var gatewayUrl by remember { mutableStateOf(walletStore.gatewayUrl) }
    var nodeEnabled by remember { mutableStateOf(walletStore.nodeModeEnabled) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionCard(title = "Wallet options") {
            if (keyPair == null) {
                Text("No wallet loaded.", color = MaterialTheme.colorScheme.error)
            } else {
                KeyRow(label = "Address", value = keyPair.address, onCopy = { clipboard.setText(AnnotatedString(keyPair.address)) })
                Spacer(Modifier.height(10.dp))
                KeyRow(label = "Public key", value = keyPair.publicKeyHex, onCopy = { clipboard.setText(AnnotatedString(keyPair.publicKeyHex)) })
                Spacer(Modifier.height(10.dp))

                Text("Private key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showPrivateKey) {
                    Text(
                        keyPair.privateKeyHex,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(keyPair.privateKeyHex)) }) { Text("Copy") }
                        TextButton(onClick = { showPrivateKey = false }) { Text("Hide") }
                    }
                } else {
                    OutlinedButton(onClick = { showPrivateKey = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reveal private key")
                    }
                }
                Text(
                    "Anyone with this key has full control of this wallet's funds. Never share it or enter it anywhere else.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Gateway") {
            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                label = { Text("Gateway URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { walletStore.gatewayUrl = gatewayUrl.trim() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Node") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Run as a temporary node")
                Switch(
                    checked = nodeEnabled,
                    onCheckedChange = {
                        nodeEnabled = it
                        walletStore.nodeModeEnabled = it
                        WorkScheduler.setNodeSyncEnabled(context, it)
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Background syncs so far: ${walletStore.nodeSyncCount} · last sync: ${formatEpochSeconds(walletStore.nodeLastSyncAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Danger zone") {
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete wallet from this device") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this wallet?") },
            text = { Text("This removes the private key from this device. Without a backup of it elsewhere, any AOS balance at this address becomes permanently unreachable from here.") },
            confirmButton = {
                TextButton(onClick = {
                    WorkScheduler.cancelMining(context)
                    WorkScheduler.setNodeSyncEnabled(context, false)
                    walletStore.deleteWallet()
                    confirmDelete = false
                    onWalletDeleted()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun KeyRow(label: String, value: String, onCopy: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
        TextButton(onClick = onCopy) { Text("Copy") }
    }
}
