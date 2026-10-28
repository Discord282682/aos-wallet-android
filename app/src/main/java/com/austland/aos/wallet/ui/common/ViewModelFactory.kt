package com.austland.aos.wallet.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.math.BigDecimal

/** Tiny generic factory so each screen's ViewModel can take constructor args without Hilt/Koin. */
class SimpleViewModelFactory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

/** Formats a server-side unix-seconds timestamp as a short local date/time string. */
fun formatEpochSeconds(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "—"
    val millis = epochSeconds * 1000
    val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}

/** Formats a countdown given in seconds as e.g. "23h 59m 12s" or "42s". */
fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0s"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** Shortens "Au5abcdef...1234" to "Au5abcd…1234" for compact display. */
fun shortenAddress(address: String, head: Int = 8, tail: Int = 6): String {
    if (address.length <= head + tail + 1) return address
    return address.take(head) + "…" + address.takeLast(tail)
}

/** Formats an AOS decimal string in Kingdom of Austland lore ("1 Ounce Stralen", "5 Ounces Stralen"). */
fun formatStralenLore(amountStr: String): String {
    val bd = try { BigDecimal(amountStr).stripTrailingZeros() } catch (_: Exception) { BigDecimal.ZERO }
    val plain = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    val unit = if (bd.compareTo(BigDecimal.ONE) == 0) "Ounce Stralen" else "Ounces Stralen"
    return "$plain $unit"
}
