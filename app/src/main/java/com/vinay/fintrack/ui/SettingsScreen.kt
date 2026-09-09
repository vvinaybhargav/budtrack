package com.vinay.fintrack.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.SyncStatus
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.today

@Composable
fun SettingsScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s4)
    ) {
        // 1. Profiles Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Person,
                    title = "Profiles",
                    subtitle = "Manage & switch user accounts",
                    action = {
                        SecondaryButton("Switch", vm::switchProfile)
                    }
                )
                Muted("Each profile tracks independent transactions. Joint overview is toggleable on Home.")
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

        // 2. Lock Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Lock,
                    title = "App Lock",
                    subtitle = "PIN protection for ${vm.activeProfile.orEmpty()}",
                    action = {
                        GhostButton("Lock now", vm::lockNow)
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Muted("Set or change 4-digit security PIN")
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

        // 3. Categories Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Category,
                    title = "Categories",
                    subtitle = "Organize transaction categories"
                )
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

        // 4. Budgets Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.PieChart,
                    title = "Budgets",
                    subtitle = "Monthly spending caps per category"
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Rollover leftover", color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Muted("Underspend carries over to next month")
                    }
                    if (vm.budgetRollover) {
                        SecondaryButton("On", { vm.setBudgetRollover(false) })
                    } else {
                        SecondaryButton("Off", { vm.setBudgetRollover(true) })
                    }
                }
                Column(
                    Modifier.padding(top = Space.s2),
                    verticalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    vm.budgets.forEach { (cat, limit) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            Text(cat, Modifier.weight(1f), color = Pf.Text, fontSize = 14.sp)
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

        // 5. Salary Settings Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Payments,
                    title = "Salary & Payday",
                    subtitle = "Monthly income & turnover cycle"
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    vm.profileNames.forEach { profile ->
                        val salAmount = vm.salaryAmountFor(profile)
                        val salResetDay = vm.salaryResetDayFor(profile)
                        
                        Column {
                            Text("Profile: $profile", color = Pf.Accent400, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.s2))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.s3)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Muted("Pay Day (Reset)")
                                    PfSelect(
                                        value = salResetDay.toString(),
                                        options = (1..28).map { it.toString() },
                                        onSelect = { vm.setSalaryDate(profile, it.toIntOrNull() ?: 1) }
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Muted("Monthly Salary")
                                    PfField(
                                        value = if (salAmount > 0.0) salAmount.toLong().toString() else "",
                                        onValueChange = {
                                            val amt = it.toDoubleOrNull() ?: 0.0
                                            vm.setSalaryAmountFor(profile, amt)
                                        },
                                        numeric = true,
                                        placeholder = "e.g. 120000"
                                    )
                                }
                            }
                            Muted(
                                "Month turns over on this day. Used to project outlook savings for $profile."
                            )

                            Column(Modifier.padding(top = Space.s3)) {
                                Text(
                                    "Salary Overrides (Next 3 Months)",
                                    color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = Space.s2)
                                )
                                val nextMonths = (1..3).map { ahead ->
                                    val d = Ledger.addMonths(today(), ahead)
                                    val yr = d.substring(0, 4)
                                    val monthInt = d.substring(5, 7).toInt()
                                    val monthLabel = when (monthInt) {
                                        1 -> "Jan"
                                        2 -> "Feb"
                                        3 -> "Mar"
                                        4 -> "Apr"
                                        5 -> "May"
                                        6 -> "Jun"
                                        7 -> "Jul"
                                        8 -> "Aug"
                                        9 -> "Sep"
                                        10 -> "Oct"
                                        11 -> "Nov"
                                        12 -> "Dec"
                                        else -> ""
                                    }
                                    val yearMonth = "%s-%02d".format(yr, monthInt)
                                    val displayLabel = "$monthLabel $yr"
                                    Triple(yearMonth, displayLabel, monthLabel)
                                }

                                nextMonths.forEach { (yearMonth, displayLabel, _) ->
                                    val override = vm.getSalaryOverride(profile, yearMonth)
                                    val amountVal = override?.amount ?: salAmount
                                    val resetDayVal = override?.resetDay ?: salResetDay
                                    
                                    Row(
                                        Modifier.fillMaxWidth().padding(bottom = Space.s2),
                                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            displayLabel,
                                            color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(90.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Muted("Pay Day")
                                            PfSelect(
                                                value = resetDayVal.toString(),
                                                options = (1..28).map { it.toString() },
                                                onSelect = { day ->
                                                    vm.setSalaryOverride(profile, yearMonth, amountVal, day.toIntOrNull() ?: 1)
                                                }
                                            )
                                        }
                                        Column(Modifier.weight(1.5f)) {
                                            Muted("Amount (₹)")
                                            PfField(
                                                value = if (amountVal > 0.0) amountVal.toLong().toString() else "",
                                                onValueChange = { amtText ->
                                                    val amt = amtText.toDoubleOrNull()
                                                    vm.setSalaryOverride(profile, yearMonth, amt, resetDayVal)
                                                },
                                                numeric = true,
                                                placeholder = "Amount"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Default Account Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.AccountBalance,
                    title = "Default Account",
                    subtitle = "Pre-selected account for new records"
                )
                PfSelect(
                    value = vm.defaultAccount,
                    options = vm.visibleAccounts.map { it.name },
                    onSelect = vm::setDefaultAccount
                )
            }
        }

        // 7. SMS Import Section Card
        item { SmsImportSection(vm) }

        // 8. SMS Rules Section Card
        item { SmsRulesSection(vm) }

        // 9. Sync & AI Card
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Sync,
                    title = "Sync & Integrations",
                    subtitle = "Cloud ledger & AI API configuration"
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
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
        }

        // 10. Sample Data Card (if any)
        if (vm.sampleDataCount > 0) {
            item {
                PfCard(padding = PaddingValues(Space.s4)) {
                    SettingHeader(
                        icon = Icons.Default.CleaningServices,
                        title = "Sample Data",
                        subtitle = "Clear starter demonstration data"
                    )
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
private fun SettingHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.s3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Pf.Accent700.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Pf.Accent400,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    title,
                    color = Pf.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (action != null) {
            action()
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsImportSection(vm: FinTrackViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasPermission by remember { mutableStateOf(hasSmsPermission(context)) }
    var canNotify by remember { mutableStateOf(hasNotifyPermission(context)) }

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
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasSmsPermission(context)
                canNotify = hasNotifyPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        vm.markSmsAsked()
        // Only the SMS grants decide this. Notifications are asked for in the
        // same breath, and turning those down is no reason to call import off.
        hasPermission = hasSmsPermission(context)
        canNotify = hasNotifyPermission(context)
        if (hasPermission) vm.setSmsImport(true)
    }

    PfCard(padding = PaddingValues(Space.s4)) {
        SettingHeader(
            icon = Icons.Default.Sms,
            title = "Bank SMS Import",
            subtitle = "Automatic tracking from SMS alerts"
        )
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

        // Wraps: three buttons side by side ran off the edge of the screen and
        // the last one was unreadable.
        FlowRow(
            Modifier.padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            if (!hasPermission && !blocked) {
                PrimaryButton("Allow SMS access", onClick = {
                    permissionLauncher.launch(smsPermissions())
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
                SecondaryButton("Re-check accounts", { vm.rematchImports() })
            }
        }

        // SMS access granted before notifications existed as a separate ask, so
        // there has to be a way to catch up without turning the feature off.
        if (hasPermission && !canNotify) {
            Column(Modifier.padding(top = Space.s3)) {
                Muted("Notifications are off, so imports happen silently.")
                Row(Modifier.padding(top = Space.s2)) {
                    SecondaryButton("Notify me on import", onClick = {
                        permissionLauncher.launch(smsPermissions())
                    })
                }
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

/** Below Android 13 the manifest entry is the whole story. */
private fun hasNotifyPermission(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Asked for together, since they are one feature: reading the bank's message,
 * and telling you what it became. Notifications only became a permission in
 * Android 13 — requesting it below that fails the whole batch.
 */
private fun smsPermissions(): Array<String> {
    val base = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        base + Manifest.permission.POST_NOTIFICATIONS
    } else {
        base
    }
}

private fun hasSmsPermission(context: android.content.Context): Boolean =
    listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun SmsRulesSection(vm: FinTrackViewModel) {
    val rules = vm.smsRules
    if (rules.isEmpty()) return

    PfCard(padding = PaddingValues(Space.s4)) {
        SettingHeader(
            icon = Icons.Default.Rule,
            title = "Saved Categorisation Rules",
            subtitle = "Merchant keyword mapping rules"
        )
        Muted("SMS patterns mapped to categories. Tap the trash icon to delete a rule.")

        Column(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Md)
                .border(1.dp, Pf.Hairline, Radius.Md)
                .padding(horizontal = Space.s4, vertical = Space.s2)
        ) {
            rules.entries.forEachIndexed { index, (pattern, category) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            pattern,
                            color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Muted("Maps to: $category", size = 12)
                    }
                    IconButton(
                        onClick = { vm.deleteSmsRule(pattern) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete rule",
                            tint = Pf.Accent400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (index < rules.size - 1) {
                    Hairline()
                }
            }
        }
    }
}
