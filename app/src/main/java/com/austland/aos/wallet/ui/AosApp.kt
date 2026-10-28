package com.austland.aos.wallet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.austland.aos.wallet.data.WalletStore
import com.austland.aos.wallet.ui.explorer.ExplorerScreen
import com.austland.aos.wallet.ui.mine.MineScreen
import com.austland.aos.wallet.ui.onboarding.OnboardingScreen
import com.austland.aos.wallet.ui.send.SendScreen
import com.austland.aos.wallet.ui.settings.SettingsScreen
import com.austland.aos.wallet.ui.wallet.WalletScreen

private sealed class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Wallet : Tab("wallet", "Treasury", Icons.Filled.AccountBalanceWallet)
    object Send : Tab("send", "Pay", Icons.Filled.Send)
    object Mine : Tab("mine", "Mine", Icons.Filled.Bolt)
    object Explorer : Tab("explorer", "Ledger", Icons.Filled.Explore)
    object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Wallet, Tab.Send, Tab.Mine, Tab.Explorer, Tab.Settings)

@Composable
fun AosApp() {
    val context = LocalContext.current
    val walletStore = remember { WalletStore(context) }
    var hasWallet by rememberSaveable { mutableStateOf(walletStore.hasWallet()) }

    if (!hasWallet) {
        OnboardingScreen(walletStore = walletStore, onWalletReady = { hasWallet = true })
        return
    }

    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Wallet.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Wallet.route) {
                WalletScreen(
                    walletStore = walletStore,
                    onSend = { navController.navigate(Tab.Send.route) },
                    onMine = { navController.navigate(Tab.Mine.route) }
                )
            }
            composable(Tab.Send.route) { SendScreen(walletStore = walletStore) }
            composable(Tab.Mine.route) { MineScreen(walletStore = walletStore) }
            composable(Tab.Explorer.route) { ExplorerScreen(walletStore = walletStore) }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    walletStore = walletStore,
                    onWalletDeleted = { hasWallet = false }
                )
            }
        }
    }
}
