package com.austland.aos.wallet.ui.wallet

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.austland.aos.wallet.data.AosApi
import com.austland.aos.wallet.data.AosTransaction
import com.austland.aos.wallet.data.SupplyInfo
import com.austland.aos.wallet.data.WalletInfo
import com.austland.aos.wallet.data.WalletStore
import com.austland.aos.wallet.ui.common.SimpleViewModelFactory
import com.austland.aos.wallet.ui.common.formatEpochSeconds
import com.austland.aos.wallet.ui.common.formatStralenLore
import com.austland.aos.wallet.ui.common.shortenAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletViewModel(private val store: WalletStore) : ViewModel() {
    private val api = AosApi { store.gatewayUrl }

    var loading by mutableStateOf(false); private set
    var wallet by mutableStateOf<WalletInfo?>(null); private set
    var supply by mutableStateOf<SupplyInfo?>(null); private set
    var recentTxs by mutableStateOf<List<AosTransaction>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set

    val address: String = store.currentKeyPair()?.address ?: ""

    fun refresh() {
        if (address.isEmpty()) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val w = withContext(Dispatchers.IO) { api.fetchWallet(address) }
                val s = withContext(Dispatchers.IO) { runCatching { api.fetchSupply() }.getOrNull() }
                val txs = withContext(Dispatchers.IO) {
                    runCatching { api.fetchTransactions(limit = 5, addressFilter = address).transactions }
                        .getOrDefault(emptyList())
                }
                wallet = w
                supply = s
                recentTxs = txs
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun WalletScreen(walletStore: WalletStore, onSend: () -> Unit, onMine: () -> Unit) {
    val viewModel: WalletViewModel = viewModel(factory = SimpleViewModelFactory { WalletViewModel(walletStore) })
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "KINGDOM OF AUSTLAND",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text("Estbrugh Treasury Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { viewModel.refresh() }) {
                if (viewModel.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Estbrugh Treasury Au5 Banknote Card
        val balStr = viewModel.wallet?.balance ?: "0.00000000"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ESTBRUGH TREASURY • SERIES V2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Au5",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = formatStralenLore(balStr),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$balStr AOS • Zero Fee (0.00000000)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary
                )
                viewModel.supply?.let { sup ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Circulating: ${sup.totalMinted} / 67,000,000 AOS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                viewModel.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Address Copy + Share Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    clipboard.setText(AnnotatedString(viewModel.address))
                    copiedNotice = true
                },
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (copiedNotice) "Copied Au5 address to clipboard!" else "Your Au5 Receiving Address (tap to copy)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (copiedNotice) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        viewModel.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(viewModel.address))
                        copiedNotice = true
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy address")
                    }
                    IconButton(onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, viewModel.address)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Au5 Address"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share address")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pay / Send", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onMine, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Mine Au5", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(22.dp))

        Text("Recent Wallet Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (viewModel.recentTxs.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "No transactions yet for this Au5 wallet. Tap 'Mine Au5' to mint your first Ounce Stralen reward!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            viewModel.recentTxs.forEach { tx ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isIncoming = tx.recipient == viewModel.address
                        val title = when {
                            tx.txType == "MINT" && isIncoming -> "Mining Reward"
                            isIncoming -> "Received from ${shortenAddress(tx.sender)}"
                            else -> "Sent to ${shortenAddress(tx.recipient)}"
                        }
                        Column {
                            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatEpochSeconds(tx.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = (if (isIncoming) "+" else "-") + "${tx.amount} AOS",
                            color = if (isIncoming) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
