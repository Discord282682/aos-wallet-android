package com.austland.aos.wallet.ui.send

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.austland.aos.wallet.crypto.AosKeys
import com.austland.aos.wallet.data.AosApi
import com.austland.aos.wallet.data.TransactionBuilder
import com.austland.aos.wallet.data.WalletStore
import com.austland.aos.wallet.ui.common.SimpleViewModelFactory
import com.austland.aos.wallet.ui.common.formatStralenLore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

sealed class SendState {
    object Idle : SendState()
    object Mining : SendState()
    object Broadcasting : SendState()
    data class Success(val txHash: String, val attempts: Long) : SendState()
    data class Failed(val message: String) : SendState()
}

class SendViewModel(private val store: WalletStore) : ViewModel() {
    private val api = AosApi { store.gatewayUrl }
    var state by mutableStateOf<SendState>(SendState.Idle); private set
    var availableBalance by mutableStateOf("0.00000000"); private set

    fun loadBalance() {
        val addr = store.currentKeyPair()?.address ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.fetchWallet(addr).balance }
            }.onSuccess { availableBalance = it }
        }
    }

    fun send(recipient: String, amount: String) {
        val keyPair = store.currentKeyPair() ?: run {
            state = SendState.Failed("No wallet loaded.")
            return
        }
        val cleanRecipient = recipient.trim()
        if (!AosKeys.isValidAddress(cleanRecipient)) {
            state = SendState.Failed("Recipient must be Au5 followed by 40 lowercase hex characters.")
            return
        }
        if (cleanRecipient == keyPair.address) {
            state = SendState.Failed("You can't send AOS to your own address.")
            return
        }
        val value = amount.toBigDecimalOrNull()
        if (value == null || value <= BigDecimal.ZERO) {
            state = SendState.Failed("Enter a valid amount greater than zero.")
            return
        }

        state = SendState.Mining
        viewModelScope.launch {
            try {
                val signed = withContext(Dispatchers.IO) {
                    val config = api.fetchConfig()
                    TransactionBuilder.buildTransfer(
                        keyPair = keyPair, chainId = config.chainId, recipient = cleanRecipient,
                        amount = value, powPrefix = config.powPrefix
                    )
                }
                state = SendState.Broadcasting
                val tx = withContext(Dispatchers.IO) {
                    api.submitTransfer(
                        chainId = signed.chainId, sender = signed.sender, recipient = signed.recipient,
                        amount = signed.amount, requestId = signed.requestId, powNonce = signed.powNonce,
                        publicKey = signed.publicKey, signature = signed.signature
                    )
                }
                state = SendState.Success(tx.txHash, signed.attempts)
                loadBalance()
            } catch (e: Exception) {
                state = SendState.Failed(e.message ?: "Transfer failed.")
            }
        }
    }

    fun reset() { state = SendState.Idle }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (this.isBlank()) null else BigDecimal(this)
} catch (e: Exception) {
    null
}

@Composable
fun SendScreen(walletStore: WalletStore) {
    val viewModel: SendViewModel = viewModel(factory = SimpleViewModelFactory { SendViewModel(walletStore) })
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var itemNote by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadBalance() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Feeless Stralen Transfer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "0.00000000 AOS network fee. Your phone solves a SHA-256 0000 Proof-of-Work stamp and signs with your Ed25519 key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Available Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${viewModel.availableBalance} AOS (${formatStralenLore(viewModel.availableBalance)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { amount = viewModel.availableBalance }) {
                    Text("MAX", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Estbrugh Marketplace Presets:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = amount == "1" && itemNote == "1 candy",
                onClick = { amount = "1"; itemNote = "1 candy" },
                label = { Text("1 Ounce • Candy") }
            )
            FilterChip(
                selected = amount == "3" && itemNote == "that one Burger",
                onClick = { amount = "3"; itemNote = "that one Burger" },
                label = { Text("3 Ounce • Burger") }
            )
            FilterChip(
                selected = amount == "6" && itemNote == "8 apples",
                onClick = { amount = "6"; itemNote = "8 apples" },
                label = { Text("6 Ounces • Apples") }
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it.trim() },
            label = { Text("Recipient Au5 address") },
            placeholder = { Text("Au5…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (AOS / Ounces Stralen)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (amount.isNotBlank()) {
            val loreQuote = formatStralenLore(amount) + (if (itemNote.isNotBlank()) " for $itemNote" else "")
            Text(
                text = "Estbrugh Quote: $loreQuote",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        when (val state = viewModel.state) {
            is SendState.Idle -> {
                Button(
                    onClick = { viewModel.send(recipient, amount) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Solve PoW & Send (0.00 Fee)", fontWeight = FontWeight.Bold) }
            }
            is SendState.Mining -> StatusCard("Solving SHA-256 Proof-of-Work (0000)…", busy = true)
            is SendState.Broadcasting -> StatusCard("Broadcasting signed transfer to Estbrugh gateway…", busy = true)
            is SendState.Success -> {
                StatusCard(
                    "Confirmed on ledger after ${state.attempts} PoW hashes!
Tx Hash: ${state.txHash}",
                    busy = false,
                    isError = false
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.reset(); recipient = ""; amount = ""; itemNote = "" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send another transfer")
                }
            }
            is SendState.Failed -> {
                StatusCard(state.message, busy = false, isError = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.send(recipient, amount) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, busy: Boolean, isError: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                Spacer(Modifier.height(8.dp))
            }
            Text(
                message,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
