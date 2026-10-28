package com.austland.aos.wallet.ui.mine

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.austland.aos.wallet.data.AosApi
import com.austland.aos.wallet.data.GatewayConfig
import com.austland.aos.wallet.data.MinerInfo
import com.austland.aos.wallet.data.TransactionBuilder
import com.austland.aos.wallet.data.WalletStore
import com.austland.aos.wallet.ui.common.SimpleViewModelFactory
import com.austland.aos.wallet.ui.common.formatDuration
import com.austland.aos.wallet.ui.common.formatStralenLore
import com.austland.aos.wallet.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MineViewModel(private val store: WalletStore) : ViewModel() {
    private val api = AosApi { store.gatewayUrl }

    var loading by mutableStateOf(true); private set
    var claimingNow by mutableStateOf(false); private set
    var miner by mutableStateOf<MinerInfo?>(null); private set
    var config by mutableStateOf<GatewayConfig?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var lastClaimReceipt by mutableStateOf<String?>(null); private set
    var nodeEnabled by mutableStateOf(store.nodeModeEnabled); private set
    var sessionActive by mutableStateOf(store.miningSessionActive); private set

    val address: String = store.currentKeyPair()?.address ?: ""

    fun refresh() {
        if (address.isEmpty()) return
        loading = true
        sessionActive = store.miningSessionActive
        viewModelScope.launch {
            try {
                val cfg = withContext(Dispatchers.IO) { api.fetchConfig() }
                val m = withContext(Dispatchers.IO) { api.fetchMiner(address) }
                config = cfg
                miner = m
                error = store.lastMiningError
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    /**
     * If the wallet is eligible right now (`canClaimNow == true`), solves the SHA-256 `0000`
     * Proof-of-Work and submits `POST /earn/claim` immediately so the user gets instant feedback
     * and sees their new balance + block hash right away, then schedules WorkManager for the next
     * cooldown cycle. If the wallet is currently on cooldown, queues WorkManager to auto-claim
     * as soon as the cooldown expires (even if the app is closed).
     */
    fun mineOrQueueSession(context: Context) {
        val kp = store.currentKeyPair() ?: return
        val currentMiner = miner
        val currentConfig = config

        if (currentMiner != null && currentMiner.canClaimNow && currentConfig != null) {
            claimingNow = true
            error = null
            lastClaimReceipt = null
            store.lastMiningError = null
            viewModelScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val claim = TransactionBuilder.buildEarnClaim(
                            keyPair = kp,
                            chainId = currentConfig.chainId,
                            rewardAmount8dp = currentConfig.earnReward,
                            powPrefix = currentConfig.powPrefix
                        )
                        val resp = api.submitEarnClaim(
                            chainId = claim.chainId,
                            recipient = claim.recipient,
                            amount = claim.amount,
                            requestId = claim.requestId,
                            powNonce = claim.powNonce,
                            publicKey = claim.publicKey,
                            signature = claim.signature
                        )
                        Pair(claim, resp)
                    }
                    val solved = result.first
                    val resp = result.second
                    lastClaimReceipt = "Mined +${resp.transaction.amount} AOS in ${solved.attempts} hashes (Nonce ${solved.powNonce})
Block: ${resp.transaction.txHash.take(20)}…"
                    // Automatically queue the next background cycle for when cooldown finishes
                    store.miningSessionActive = true
                    sessionActive = true
                    WorkScheduler.scheduleMiningClaim(context, currentConfig.earnCooldownSeconds.coerceAtLeast(1L))
                    refresh()
                } catch (e: Exception) {
                    error = e.message
                    store.lastMiningError = e.message
                } finally {
                    claimingNow = false
                }
            }
        } else {
            val remaining = (currentMiner?.cooldownRemainingSeconds ?: 60L).coerceAtLeast(1L)
            store.miningSessionActive = true
            store.lastMiningError = null
            sessionActive = true
            error = null
            WorkScheduler.scheduleMiningClaim(context, remaining)
            refresh()
        }
    }

    fun cancelSession(context: Context) {
        store.miningSessionActive = false
        sessionActive = false
        WorkScheduler.cancelMining(context)
        refresh()
    }

    fun setNodeEnabled(context: Context, enabled: Boolean) {
        nodeEnabled = enabled
        store.nodeModeEnabled = enabled
        WorkScheduler.setNodeSyncEnabled(context, enabled)
    }
}

@Composable
fun MineScreen(walletStore: WalletStore) {
    val context = LocalContext.current
    val viewModel: MineViewModel = viewModel(factory = SimpleViewModelFactory { MineViewModel(walletStore) })

    LaunchedEffect(Unit) { viewModel.refresh() }

    var tick by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val job = kotlinx.coroutines.MainScope().launch {
            while (true) {
                delay(1000)
                tick++
                // Auto-refresh when cooldown timer hits 0 so button unlocks cleanly
                val m = viewModel.miner
                if (m != null && !m.canClaimNow && m.nextClaimAt > 0 && (System.currentTimeMillis() / 1000) >= m.nextClaimAt && tick % 3L == 0L) {
                    viewModel.refresh()
                }
            }
        }
        onDispose { job.cancel() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        Text(
            "ESTBRUGH MINTING FOUNDRY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text("Proof-of-Work Stralen Mining", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Your phone computes SHA-256 hashes until it finds a nonce starting with "${viewModel.config?.powPrefix ?: "0000"}", signs the claim with your Ed25519 key, and mints new Au5 directly from the Estbrugh Treasury.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        if (viewModel.loading && viewModel.miner == null) {
            CircularProgressIndicator()
        }

        viewModel.error?.let {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        viewModel.lastClaimReceipt?.let { receipt ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    receipt,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        viewModel.config?.let { cfg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Live Server Mining Reward", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatStralenLore(cfg.earnReward),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${cfg.earnReward} AOS per PoW block • Target prefix: ${cfg.powPrefix}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cooldown: ${formatDuration(cfg.earnCooldownSeconds)} per wallet · Total claims by you: ${viewModel.miner?.claims ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            val miner = viewModel.miner
            val nowSec = System.currentTimeMillis() / 1000
            val liveRemaining = if (miner != null && miner.nextClaimAt > nowSec) {
                (miner.nextClaimAt - nowSec).coerceAtLeast(0L)
            } else {
                0L
            }
            val canClaimLive = miner != null && (miner.canClaimNow || liveRemaining == 0L)

            when {
                viewModel.claimingNow -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Column {
                                Text("Solving SHA-256 PoW (0000) & Signing…", fontWeight = FontWeight.Bold)
                                Text(
                                    "Broadcasting POST /earn/claim to Estbrugh Treasury",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                canClaimLive -> {
                    Button(
                        onClick = { viewModel.mineOrQueueSession(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mine & Claim ${formatStralenLore(cfg.earnReward)} Now", fontWeight = FontWeight.Bold)
                    }
                }
                viewModel.sessionActive -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "Background auto-claim queued — WorkManager will solve PoW & claim automatically when cooldown finishes (even if the app is closed).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                formatDuration(liveRemaining),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelSession(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel background auto-claim")
                            }
                        }
                    }
                }
                else -> {
                    Text("Next mining claim unlocks in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatDuration(liveRemaining),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.mineOrQueueSession(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Queue Background Auto-Claim When Timer Ends", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Estbrugh Auxiliary Sync Node", fontWeight = FontWeight.Bold)
                        Text(
                            "Periodically syncs circulating supply & recent ledger transactions in the background every 15m.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.nodeEnabled,
                        onCheckedChange = { viewModel.setNodeEnabled(context, it) }
                    )
                }
            }
        }
    }
}
