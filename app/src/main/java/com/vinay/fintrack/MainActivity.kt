package com.vinay.fintrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.sms.DueReminder
import com.vinay.fintrack.sms.Notifier
import com.vinay.fintrack.ui.AddScreen
import com.vinay.fintrack.ui.ChatScreen
import com.vinay.fintrack.ui.EntriesScreen
import com.vinay.fintrack.ui.FinTrackTheme
import com.vinay.fintrack.ui.Hairline
import com.vinay.fintrack.ui.HomeScreen
import com.vinay.fintrack.ui.LockScreen
import com.vinay.fintrack.ui.Muted
import com.vinay.fintrack.ui.Pf
import com.vinay.fintrack.ui.SettingsScreen
import com.vinay.fintrack.ui.Space
import com.vinay.fintrack.ui.Tag

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.IconButton
import com.vinay.fintrack.ui.AccountsScreen

class MainActivity : ComponentActivity() {

    private val vm: FinTrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Re-armed on every launch: an inexact repeating alarm is cheap to set,
        // and this covers the app being force-stopped or updated, which cancels
        // whatever was pending.
        DueReminder.schedule(applicationContext)
        setContent {
            FinTrackTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Pf.Bg)
                        .systemBarsPadding()
                ) {
                    if (vm.isLocked) LockScreen(vm) else UnlockedShell(vm)
                }
            }
        }
    }

    /** Already running when the notification is tapped: without this the
     *  activity keeps its original intent and nothing opens. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** The SMS receiver records payments while the app is closed, so pick up
     *  whatever it wrote rather than showing stale state. */
    override fun onResume() {
        super.onResume()
        vm.refreshFromDisk()
        openTappedTransaction()
        if (intent?.getBooleanExtra("com.vinay.fintrack.GO_TO_ENTRIES", false) == true) {
            intent.removeExtra("com.vinay.fintrack.GO_TO_ENTRIES")
            vm.tab = Tab.ENTRIES
            vm.entriesCategoryFilter = "Needs Account"
        }
    }

    /**
     * Runs after the reload above, because a transaction imported while the app
     * was closed isn't in memory until then.
     *
     * The extra is consumed, or rotating the screen would reopen the sheet
     * every time.
     */
    private fun openTappedTransaction() {
        val id = intent?.getStringExtra(Notifier.EXTRA_TXN_ID) ?: return
        intent.removeExtra(Notifier.EXTRA_TXN_ID)
        vm.openImportedTxn(id)
    }
}

@Composable
private fun UnlockedShell(vm: FinTrackViewModel) {
    val canGoBack = vm.tab != Tab.HOME ||
        vm.editingTxnId != null ||
        vm.editingAccountId != null ||
        vm.editingCardId != null ||
        vm.editingLoanId != null ||
        vm.editingEntryId != null ||
        vm.settlingCardId != null ||
        vm.pendingConfirm != null ||
        vm.pendingDeletion != null

    BackHandler(enabled = canGoBack) {
        when {
            vm.pendingDeletion != null -> vm.cancelDeletion()
            vm.editingTxnId != null -> vm.cancelEditTxn()
            vm.editingAccountId != null -> vm.cancelEditAccount()
            vm.editingCardId != null -> vm.cancelEditCard()
            vm.editingLoanId != null -> vm.cancelEditLoan()
            vm.editingEntryId != null -> vm.cancelEdit()
            vm.settlingCardId != null -> vm.cancelSettleCard()
            vm.pendingConfirm != null -> vm.cancelConfirm()
            vm.tab != Tab.HOME -> vm.tab = Tab.HOME
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(vm)
        Box(Modifier.weight(1f)) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.ACCOUNTS -> AccountsScreen(vm)
                Tab.ENTRIES -> EntriesScreen(vm)
                Tab.ADD -> AddScreen(vm)
                Tab.CHAT -> ChatScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
        BottomNav(vm)
    }
    com.vinay.fintrack.ui.DetectedAccountDialog(vm)
}

@Composable
private fun Header(vm: FinTrackViewModel) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Pf.Surface2, CircleShape)
                        .border(1.dp, Pf.Hairline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        vm.activeProfile?.take(1).orEmpty(),
                        color = Pf.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column {
                    Muted(greeting, size = 11)
                    Text(
                        vm.activeProfile.orEmpty(),
                        color = Pf.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                val isSynced = vm.syncedAt > 0L
                Row(
                    Modifier
                        .background(Pf.Surface2, com.vinay.fintrack.ui.Radius.Pill)
                        .border(1.dp, Pf.Hairline, com.vinay.fintrack.ui.Radius.Pill)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(if (isSynced) Color(0xFF10B981) else Color(0xFFFFA726), CircleShape)
                    )
                    Text(
                        if (isSynced) "Synced" else "Local",
                        color = Pf.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Tag(vm.bucketLabel, Pf.Accent100, Pf.Accent800)

                IconButton(
                    onClick = { vm.tab = if (vm.tab == Tab.CHAT) Tab.HOME else Tab.CHAT },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        "Assistant",
                        Modifier.size(18.dp),
                        tint = if (vm.tab == Tab.CHAT) Pf.Text else Pf.Muted
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Pf.Hairline)
        )
    }
}

@Composable
private fun BottomNav(vm: FinTrackViewModel) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Pf.Hairline)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface.copy(alpha = 0.98f))
                .padding(horizontal = Space.s2, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Default.Home, "Home", vm.tab == Tab.HOME, Modifier.weight(1f)) { vm.tab = Tab.HOME }
            NavItem(Icons.Default.AccountBalanceWallet, "Accounts", vm.tab == Tab.ACCOUNTS, Modifier.weight(1f)) { vm.tab = Tab.ACCOUNTS }
            
            // Center Floating Elevated Add Button
            Box(
                Modifier
                    .weight(1.1f)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(Pf.Accent, CircleShape)
                        .border(1.dp, Pf.Hairline, CircleShape)
                        .clickable { vm.tab = Tab.ADD },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Add",
                        Modifier.size(24.dp),
                        tint = Pf.OnAccent
                    )
                }
            }

            NavItem(Icons.AutoMirrored.Filled.List, "Transactions", vm.tab == Tab.ENTRIES, Modifier.weight(1f)) { vm.tab = Tab.ENTRIES }
            NavItem(Icons.Default.Settings, "Settings", vm.tab == Tab.SETTINGS, Modifier.weight(1f)) { vm.tab = Tab.SETTINGS }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val tint = if (selected) Pf.Text else Pf.Muted
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .background(if (selected) Pf.Surface2 else Color.Transparent, com.vinay.fintrack.ui.Radius.Pill)
                .padding(horizontal = 12.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = tint)
        }
        Text(
            label,
            Modifier.padding(top = 2.dp),
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
