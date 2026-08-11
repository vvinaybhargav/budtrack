package com.vinay.fintrack.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.SyncStatus
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.today

@Composable
fun SettingsScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6)
    ) {
        item {
            Column {
                Heading("Profile")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(vm.activeProfile.orEmpty(), color = Pf.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    SecondaryButton("Switch profile", vm::switchProfile)
                }
            }
        }

        item { Hairline() }

        // Only while any of it is left.
        if (vm.sampleDataCount > 0) {
            item {
                Column {
                    Heading("Sample data")
                    Muted(
                        "${vm.sampleDataCount} made-up record(s) from first launch — " +
                            "salaries, accounts, cards and loans. They inflate every " +
                            "planned figure and balance once your own numbers are in."
                    )
                    Row(Modifier.padding(top = Space.s3)) {
                        SecondaryButton("Remove sample data", { vm.clearSamples() })
                    }
                    if (vm.sampleNote.isNotEmpty()) {
                        Muted(vm.sampleNote, Modifier.padding(top = Space.s2))
                    }
                }
            }

            item { Hairline() }
        }

        item {
            Column {
                Heading("Profiles")
                Muted(
                    "Each profile sees only its own accounts, cards, loans, " +
                        "investments and set-asides. Joint isn't a sign-in — " +
                        "switch to it at the top of Home to see the shared side."
                )
                Column(
                    Modifier.padding(top = Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    vm.profileNames.forEach { name ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            if (vm.renamingProfile == name) {
                                PfField(
                                    value = vm.renameText,
                                    onValueChange = vm::editRenameText,
                                    modifier = Modifier.weight(1f)
                                )
                                PrimaryButton("Save", vm::saveRenameProfile)
                                SecondaryButton("Cancel", vm::cancelRenameProfile)
                            } else {
                                Text(name, Modifier.weight(1f), color = Pf.Text, fontSize = 14.sp)
                                if (name == vm.activeProfile) Tag("You", Pf.Accent100, Pf.Accent800)
                                SmallIcon(Icons.Default.Edit, "Rename profile", true) {
                                    vm.startRenameProfile(name)
                                }
                                if (name != vm.activeProfile) {
                                    SmallIcon(Icons.Default.Delete, "Remove profile", true) {
                                        vm.removeProfile(name)
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.s3),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PfField(
                        value = vm.newProfileName,
                        onValueChange = vm::editProfileName,
                        placeholder = "New profile",
                        modifier = Modifier.weight(1f)
                    )
                    PfField(
                        value = vm.newProfilePin,
                        onValueChange = vm::editProfilePin,
                        placeholder = "PIN",
                        numeric = true,
                        modifier = Modifier.width(90.dp)
                    )
                    PrimaryButton("Add", vm::addProfile)
                }
                if (vm.profileMsg.isNotEmpty()) {
                    Muted(vm.profileMsg, Modifier.padding(top = Space.s2))
                }
            }
        }

        item { Hairline() }

        item {
            Column {
                Heading("Lock")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Muted(
                        if (vm.askPinOnLaunch) "PIN asked every launch"
                        else "Opens straight to ${vm.activeProfile.orEmpty()}"
                    )
                    if (vm.askPinOnLaunch) SecondaryButton("Skip PIN", { vm.setAskPinOnLaunch(false) })
                    else SecondaryButton("Ask for PIN", { vm.setAskPinOnLaunch(true) })
                }
                GhostButton("Lock now", vm::lockNow, Modifier.padding(top = Space.s2))
            }
        }

        item { Hairline() }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Heading("Change PIN")
                PfField("New 4-digit PIN", vm.pinNew, { vm.setPinField(true, it) }, numeric = true)
                PfField("Confirm PIN", vm.pinConfirm, { vm.setPinField(false, it) }, numeric = true)
                if (vm.pinMsg.isNotEmpty()) {
                    Text(vm.pinMsg, color = if (vm.pinMsgIsError) Pf.Accent400 else Pf.Text, fontSize = 13.sp)
                }
                PrimaryButton("Save PIN", vm::savePin)
            }
        }

        item { Hairline() }

        item {
            Column {
                Heading("Categories")
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    vm.categories.forEachIndexed { i, cat ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            if (vm.editingCategory == cat) {
                                PfField(
                                    value = vm.categoryDraftText,
                                    onValueChange = { vm.categoryDraftText = it },
                                    modifier = Modifier.weight(1f)
                                )
                                PrimaryButton("Save", vm::saveCategory)
                            } else {
                                Text(cat, Modifier.weight(1f), color = Pf.Text, fontSize = 14.sp)
                                SmallIcon(Icons.Default.ArrowUpward, "Move up", i > 0) { vm.moveCategory(i, -1) }
                                SmallIcon(Icons.Default.ArrowDownward, "Move down", i < vm.categories.size - 1) { vm.moveCategory(i, 1) }
                                SmallIcon(Icons.Default.Edit, "Rename", true) { vm.startEditCategory(cat) }
                                SmallIcon(Icons.Default.Delete, "Remove", true) { vm.removeCategory(cat) }
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.s3),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PfField(
                        value = vm.newCategoryText,
                        onValueChange = { vm.newCategoryText = it },
                        placeholder = "New category",
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton("Add", vm::addCategory, enabled = vm.newCategoryText.isNotBlank())
                }
            }
        }

        item { Hairline() }

        item {
            Column {
                Heading("Category budgets")
                Muted("A monthly limit per category. The Home bars measure real confirmed spend against these.")
                Column(
                    Modifier.padding(top = Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    vm.budgets.forEach { (cat, limit) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            Text(cat, Modifier.weight(1f), color = Pf.Text, fontSize = 14.sp)
                            // Debounced, and blank is ignored: saving on every
                            // keystroke stored 0 the moment you cleared it,
                            // which then crashed Home on a zero denominator.
                            DebouncedField(
                                value = limit.toLong().toString(),
                                onSettled = { v ->
                                    v.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.setBudget(cat, it) }
                                },
                                numeric = true,
                                modifier = Modifier.width(110.dp)
                            )
                            SmallIcon(Icons.Default.Delete, "Remove budget", true) { vm.removeBudget(cat) }
                        }
                    }
                }
                if (vm.budgetableCategories.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.s3),
                        horizontalArrangement = Arrangement.spacedBy(Space.s2),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(Modifier.weight(1f)) {
                            PfSelect(
                                value = vm.budgetDraftCategory,
                                options = vm.budgetableCategories,
                                onSelect = { vm.budgetDraftCategory = it }
                            )
                        }
                        PfField(
                            value = vm.budgetDraftAmount,
                            onValueChange = { vm.budgetDraftAmount = it },
                            placeholder = "Limit",
                            numeric = true,
                            modifier = Modifier.width(110.dp)
                        )
                        PrimaryButton(
                            "Add",
                            vm::addBudgetFromDraft,
                            enabled = vm.budgetDraftCategory.isNotBlank() && vm.budgetDraftAmount.isNotBlank()
                        )
                    }
                }
            }
        }

        item { Hairline() }

        item {
            Column {
                Heading("Default account")
                PfSelect(
                    value = vm.defaultAccount,
                    options = vm.visibleAccounts.map { it.name },
                    onSelect = vm::setDefaultAccount
                )
            }
        }

        item { Hairline() }

        item { SmsImportSection(vm) }

        item { Hairline() }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Heading("Sync")
                DebouncedField(
                    label = "Firebase config — apiKey, projectId, storageBucket, messagingSenderId, appId",
                    value = vm.firebaseConfigText,
                    onSettled = vm::setFirebaseConfig,
                    placeholder = "paste the values, separated by commas",
                    singleLine = false,
                    allowBlank = true
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Muted("Firestore")
                    when (vm.syncStatus) {
                        SyncStatus.LIVE -> Tag("Live", Pf.Accent2_100, Pf.Accent2_800)
                        SyncStatus.CONNECTING -> Tag("Connecting…", Pf.Neutral100, Pf.Neutral800)
                        SyncStatus.ERROR -> Tag("Error", Pf.Accent100, Pf.Accent800)
                        SyncStatus.OFF -> OutlineTag("Not connected")
                    }
                }
                if (vm.syncError.isNotEmpty()) {
                    Text(vm.syncError, color = Pf.Accent400, fontSize = 12.sp)
                }
                // Always enabled: a dead button explains nothing, whereas trying
                // and reporting the reason does.
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    PrimaryButton(
                        if (vm.syncStatus == SyncStatus.LIVE) "Reconnect" else "Connect",
                        vm::applyFirebaseConfig
                    )
                    if (vm.syncStatus == SyncStatus.LIVE) {
                        SecondaryButton("Push now", vm::pushNow)
                    }
                    if (vm.syncStatus != SyncStatus.OFF) {
                        SecondaryButton("Disconnect", vm::disconnectSync)
                    }
                }
                Muted(
                    if (vm.syncConfigLooksValid) {
                        "Config reads OK — ${vm.syncConfigSummary}"
                    } else {
                        "Config not readable — found ${vm.syncConfigPartCount} values, " +
                            "need at least apiKey, projectId and appId."
                    }
                )
                if (vm.syncedAt > 0L) {
                    Muted("Last sent ${prettyDate(today())} at ${vm.syncedAtClock}")
                }
                Muted("Syncing to workspaces/household/budtrack/state")
                if (vm.syncDeviceId.isEmpty() && vm.syncAuthNote.isNotEmpty()) {
                    Muted("Not signed in — fine if your rules allow this path. ${vm.syncAuthNote}")
                }
                if (vm.syncDeviceId.isNotEmpty()) {
                    Column(Modifier.padding(top = Space.s2)) {
                        Muted("This device's ID — add it to the members doc in your rules")
                        SelectionContainer {
                            Text(
                                vm.syncDeviceId,
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Muted(
                    "Both profiles share one household document. The config and API key " +
                        "stay on this device — they are never uploaded."
                )
                DebouncedField(
                    label = "OpenAI API key — for Smart Add",
                    value = vm.openaiKeyText,
                    onSettled = vm::setOpenaiKey,
                    placeholder = "sk-…",
                    allowBlank = true
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Muted("OpenAI")
                    if (vm.openaiKeyText.isNotBlank()) Tag("Configured", Pf.Accent100, Pf.Accent800)
                    else OutlineTag("Not set")
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(bottom = Space.s2), color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        Icon(
            icon,
            description,
            Modifier.size(14.dp),
            tint = if (enabled) Pf.Accent400 else Pf.Muted.copy(alpha = 0.4f)
        )
    }
}

/**
 * Bank SMS import. Unlike reading a receipt image, the message states which
 * account moved the money, so a transaction lands on the right one rather than
 * a guess — and it covers card, ATM and EMI debits too, not just UPI.
 */
@Composable
private fun SmsImportSection(vm: FinTrackViewModel) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasSmsPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants.values.all { it }
        if (hasPermission) vm.setSmsImport(true)
    }

    Column {
        Heading("Bank SMS")
        Muted(
            "Records payments from your bank's alerts as they arrive — UPI, card, " +
                "ATM and EMI alike. Messages are read on this phone and only the " +
                "parsed amount, payee and reference are kept."
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Muted("Automatic import")
            if (vm.smsImportOn && hasPermission) Tag("On", Pf.Accent2_100, Pf.Accent2_800)
            else OutlineTag("Off")
        }

        Row(
            Modifier.padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            if (!hasPermission) {
                PrimaryButton("Allow SMS access", onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                    )
                })
            } else if (vm.smsImportOn) {
                SecondaryButton("Turn off", { vm.setSmsImport(false) })
            } else {
                PrimaryButton("Turn on", { vm.setSmsImport(true) })
            }
            if (hasPermission && vm.smsImportOn) {
                SecondaryButton(
                    if (vm.scanning) "Reading…" else "Import past 60 days",
                    { vm.backfillSms() },
                    enabled = !vm.scanning
                )
            }
        }

        if (vm.scanNote.isNotEmpty()) {
            Muted(vm.scanNote, Modifier.padding(top = Space.s2))
        }
        Muted("${vm.importedCount} imported from SMS", Modifier.padding(top = Space.s1))

        // Without this, a message that didn't import is indistinguishable from
        // one that never arrived.
        if (vm.smsLog.isNotEmpty()) {
            Column(Modifier.padding(top = Space.s3)) {
                Muted("Recent messages")
                vm.smsLog.take(8).forEach { line ->
                    Text(
                        line,
                        Modifier.padding(top = 2.dp),
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Muted(
            "Set each account's last digits in its editor on Home — that's how a " +
                "message finds the right account. Without it everything lands on " +
                "your default account.",
            Modifier.padding(top = Space.s2)
        )
    }
}

private fun hasSmsPermission(context: android.content.Context): Boolean =
    listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
