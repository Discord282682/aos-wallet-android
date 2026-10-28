package com.austland.aos.wallet.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.austland.aos.wallet.data.AosApi
import com.austland.aos.wallet.data.WalletStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "temporary node" toggle's background half.
 *
 * Be straight about what this is: `feeless-coin` is a **centralized** ledger — one Postgres
 * database behind one gateway (see the server's own README: "This is one hosted ledger... This
 * is not decentralized immutability"). There is no mempool of unconfirmed transactions and no
 * peer-to-peer network for a phone to actually join or help validate; every transfer/claim is
 * submitted directly to the gateway and confirmed instantly in one request.
 *
 * So rather than fake real P2P relay work this can't truthfully do, this worker does the
 * honest, useful version while the toggle is on: it periodically pulls the latest public ledger
 * state (recent transactions + total supply) in the background so the Explorer/Wallet screens
 * open with fresh data and the gateway sees light, predictable read traffic instead of a burst
 * every time the user opens the app. It also tracks a local "node uptime" counter shown in
 * Settings. If a future version of the server exposes a real pending-transaction queue, this is
 * the worker to extend with actual relay/broadcast logic.
 */
class NodeSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = WalletStore(applicationContext)
        if (!store.nodeModeEnabled) return@withContext Result.success()

        val api = AosApi { store.gatewayUrl }
        try {
            api.fetchSupply()
            api.fetchTransactions(limit = 25)
            store.nodeSyncCount += 1
            store.nodeLastSyncAt = System.currentTimeMillis() / 1000
            Result.success()
        } catch (e: Exception) {
            Log.w("NodeSyncWorker", "Sync pass failed: ${e.message}")
            Result.retry()
        }
    }
}
