package com.vinay.fintrack.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.SyncStatus

@Composable
fun SettingsScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6)
    ) {
        item {
            Column {
                // One Profiles section, not a "Profile" and a "Profiles".
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Heading("Profiles")
                    SecondaryButton("Switch profile", vm::switchProfile)
                }
                Muted("Each profile sees only its own. Joint is the switch on Home.")
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
                if (vm.profileMsg.isNotEmpty()) {
                    Muted(vm.profileMsg, Modifier.padding(top = Space.s2))
                }
            }
        }

        item { Hairline() }

        item {
            Column {
                // No PIN toggle: the app opens where it left off, and the
                // choice to remember is made once on the lock screen.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Heading("Lock")
                    GhostButton("Lock now", vm::lockNow)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Muted("Change PIN for ${vm.activeProfile.orEmpty()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        PfField(
                            value = vm.pinNew,
                            onValueChange = { vm.setPinField(true, it) },
                            placeholder = "New PIN",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                        PfField(
                            value = vm.pinConfirm,
                            onValueChange = { vm.setPinField(false, it) },
                            placeholder = "Confirm",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (vm.pinMsg.isNotEmpty()) {
                        Text(
                            vm.pinMsg,
                            color = if (vm.pinMsgIsError) Pf.Accent400 else Pf.Text,
                            fontSize = 13.sp
                        )
                    }
                    PrimaryButton("Save PIN", vm::savePin)
                }
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
                Heading("Budgets")
                Muted("A monthly limit per category.")
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
                // For pay that doesn't arrive on the 1st: set-asides and
                // confirmations follow this rather than the calendar.
                Heading("Month starts on")
                PfSelect(
                    value = vm.cycleResetDay.toString(),
                    options = (1..28).map { it.toString() },
                    onSelect = { vm.setCycleResetDay(it.toIntOrNull() ?: 1) }
                )
                Muted("Set-asides and confirmations reset on this day.")
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
                PrimaryButton("Connect", vm::applyFirebaseConfig)
                Muted(
                    if (vm.syncConfigLooksValid) "Reads OK — ${vm.syncConfigSummary}"
                    else "Need at least apiKey, projectId and appId."
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

        // Last, and only while any is left: a one-off cleanup, not a setting.
        if (vm.sampleDataCount > 0) {
            item { Hairline() }
            item {
                Column {
                    Heading("Sample data")
                    Muted(
                        "${vm.sampleDataCount} made-up record(s) from first launch. " +
                            "They inflate every figure once your own numbers are in."
                    )
                    Row(Modifier.padding(top = Space.s3)) {
                        SecondaryButton("Remove sample data", { vm.clearSamples() })
                    }
                    if (vm.sampleNote.isNotEmpty()) {
                        Muted(vm.sampleNote, Modifier.padding(top = Space.s2))
                    }
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
    val activity = context as? Activity
    var hasPermission by remember { mutableStateOf(hasSmsPermission(context)) }

    /**
     * Android's three states, which the app has to tell apart:
     *   GRANTED    — nothing to do.
     *   ASKABLE    — the prompt will appear.
     *   BLOCKED    — declined for good; the prompt no longer appears at all, so
     *                only the system settings page can turn it on.
     *
     * shouldShowRequestPermissionRationale is what distinguishes the last two,
     * and only after a first attempt — before that it is false for a permission
     * never requested, which is why the attempt has to be recorded.
     */
    val asked = vm.smsAsked
    val blocked = asked && !hasPermission && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)

    // Coming back from the settings page: without this the screen still says
    // access is missing until the app is restarted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasSmsPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        vm.markSmsAsked()
        hasPermission = grants.values.all { it }
        if (hasPermission) vm.setSmsImport(true)
    }

    Column {
        Heading("Bank SMS")
        Muted("Records payments from your bank's alerts — UPI, card, ATM and EMI.")

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
            if (!hasPermission && !blocked) {
                PrimaryButton("Allow SMS access", onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                    )
                })
            } else if (!hasPermission) {
                PrimaryButton("Open permissions", onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                // For imports made before the account digits were filled in.
                SecondaryButton("Re-check accounts", vm::rematchImports)
            }
        }

        // One line that says exactly where you are, rather than a button that
        // silently does nothing.
        if (!hasPermission) {
            Column(Modifier.padding(top = Space.s2)) {
                Muted(
                    when {
                        blocked -> "Android won't ask again. Two steps in app info:"
                        asked -> "Declined. Nothing is read until you allow it."
                        else -> "Messages are read on this phone only. Amount, payee " +
                            "and reference are kept — nothing else."
                    }
                )
                // Android blocks SMS outright for apps not installed from a
                // store, and hides the unblock in an overflow menu. Nobody
                // finds "Allow restricted settings" without being told.
                if (blocked) {
                    Column(Modifier.padding(top = Space.s1)) {
                        Muted("1. Tap ⋮ at the top right → Allow restricted settings")
                        Muted("2. Permissions → SMS → Allow")
                    }
                    Muted(
                        "That first step exists because the app was installed from a " +
                            "file rather than a store. It is asked once per install.",
                        Modifier.padding(top = Space.s2)
                    )
                }
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
            "Set each account's last digits on Home, or every message lands on " +
                "the default account.",
            Modifier.padding(top = Space.s2)
        )
    }
}

private fun hasSmsPermission(context: android.content.Context): Boolean =
    listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
