package com.austland.aos.wallet.ui.explorer

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.austland.aos.wallet.data.AosApi
import com.austland.aos.wallet.data.AosTransaction
import com.austland.aos.wallet.data.SupplyInfo
import com.austland.aos.wallet.data.WalletStore
import com.austland.aos.wallet.ui.common.SimpleViewModelFactory
import com.austland.aos.wallet.ui.common.formatEpochSeconds
import com.austland.aos.wallet.ui.common.shortenAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExplorerViewModel(private val store: WalletStore) : ViewModel() {
    private val api = AosApi { store.gatewayUrl }
    val myAddress: String = store.currentKeyPair()?.address ?: ""

    var loading by mutableStateOf(true); private set
    var supply by mutableStateOf<SupplyInfo?>(null); private set
    var transactions by mutableStateOf<List<AosTransaction>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var myTransactionsOnly by mutableStateOf(false); private set

    fun setFilter(mine: Boolean) {
        myTransactionsOnly = mine
        refresh()
    }

    fun refresh() {
        loading = true
        viewModelScope.launch {
            try {
                val sup = withContext(Dispatchers.IO) { runCatching { api.fetchSupply() }.getOrNull() }
                val page = withContext(Dispatchers.IO) {
                    api.fetchTransactions(limit = 50, addressFilter = if (myTransactionsOnly) myAddress else null)
                }
                supply = sup
                transactions = page.transactions
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun ExplorerScreen(walletStore: WalletStore) {
    val viewModel: ExplorerViewModel = viewModel(factory = SimpleViewModelFactory { ExplorerViewModel(walletStore) })
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Estbrugh Public Ledger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Confirmed Ed25519 + PoW 0000 transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.refresh() }) {
                if (viewModel.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Ledger")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        viewModel.supply?.let { sup ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "ESTBRUGH TREASURY SUPPLY (${sup.chainId})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Minted: ${sup.totalMinted} AOS",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "Remaining to 67M Cap: ${sup.remaining} AOS",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row {
            FilterChip(
                selected = !viewModel.myTransactionsOnly,
                onClick = { viewModel.setFilter(false) },
                label = { Text("All Network (${viewModel.transactions.size})") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = viewModel.myTransactionsOnly,
                onClick = { viewModel.setFilter(true) },
                label = { Text("My Au5 Wallet") }
            )
        }

        Spacer(Modifier.height(10.dp))

        viewModel.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(viewModel.transactions) { tx ->
                TransactionRow(tx, myAddress = viewModel.myAddress)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: AosTransaction, myAddress: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val label = when {
                    tx.txType == "MINT" && tx.recipient == myAddress -> "Mined (Au5 Reward)"
                    tx.txType == "MINT" -> "Treasury Mint / PoW"
                    tx.sender == myAddress -> "Sent Transfer"
                    tx.recipient == myAddress -> "Received Transfer"
                    else -> "Feeless Transfer"
                }
                Text(label, fontWeight = FontWeight.Bold)
                Text(
                    "${tx.amount} AOS",
                    color = if (tx.txType == "MINT") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "From ${shortenAddress(tx.sender)}  →  ${shortenAddress(tx.recipient)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "${formatEpochSeconds(tx.createdAt)} · Nonce ${tx.powNonce} · Hash ${shortenAddress(tx.txHash, 10, 6)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
