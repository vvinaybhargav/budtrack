package com.vinay.fintrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
    Column(Modifier.fillMaxSize()) {
        Header(vm)
        Box(Modifier.weight(1f)) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.ENTRIES -> EntriesScreen(vm)
                Tab.ADD -> AddScreen(vm)
                Tab.CHAT -> ChatScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
            if (vm.parsingSmsWithAi) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Pf.Accent)
                        Text(
                            "Analyzing bank SMS with AI...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        BottomNav(vm)
    }
}

@Composable
private fun Header(vm: FinTrackViewModel) {
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
                        .background(Pf.Accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        vm.activeProfile?.take(1).orEmpty(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column {
                    Muted("Good to see you")
                    Text(
                        vm.activeProfile.orEmpty(),
                        color = Pf.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Tag(vm.bucketLabel, Pf.Accent100, Pf.Accent800)
        }
        Hairline()
    }
}

@Composable
private fun BottomNav(vm: FinTrackViewModel) {
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface)
                .padding(horizontal = Space.s3, vertical = 6.dp)
        ) {
            NavItem(Icons.Default.Home, "Home", vm.tab == Tab.HOME, Modifier.weight(1f)) { vm.tab = Tab.HOME }
            NavItem(Icons.AutoMirrored.Filled.List, "Transactions", vm.tab == Tab.ENTRIES, Modifier.weight(1f)) { vm.tab = Tab.ENTRIES }
            NavItem(Icons.Default.Add, "Add", vm.tab == Tab.ADD, Modifier.weight(1f)) { vm.tab = Tab.ADD }
            NavItem(Icons.AutoMirrored.Filled.Chat, "Chat", vm.tab == Tab.CHAT, Modifier.weight(1f)) { vm.tab = Tab.CHAT }
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
    val tint = if (selected) Pf.Accent400 else Pf.Muted
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(top = Space.s2, bottom = Space.s1),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint)
        Text(
            label,
            Modifier.padding(top = 4.dp),
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
