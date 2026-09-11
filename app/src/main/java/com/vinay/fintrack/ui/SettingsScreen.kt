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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.SyncStatus
import com.vinay.fintrack.data.Ledger
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.inr

@Composable
fun SettingsScreen(vm: FinTrackViewModel) {
    val profile = "Vinay"
    var showManageCategories by remember { mutableStateOf(false) }
    var showMoreSettings by remember { mutableStateOf(false) }
    var showSmsLog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 90.dp, top = Space.s3, start = Space.s4, end = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s4)
    ) {
        // Top Title
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Settings",
                    color = Pf.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Muted("Personalize your budget, security & preferences", size = 13)
            }
        }

        // ════════════════════════════════════════════════════════════════
        // 1. SALARY & PAYDAY CARD
        // ════════════════════════════════════════════════════════════════
        item {
            SalarySection(vm, profile)
        }

        // ════════════════════════════════════════════════════════════════
        // 2. APP LOCK CARD
        // ════════════════════════════════════════════════════════════════
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Lock,
                    title = "App Lock",
                    subtitle = "PIN protection",
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

        // ════════════════════════════════════════════════════════════════
        // 3. DEFAULT ACCOUNT CARD
        // ════════════════════════════════════════════════════════════════
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

        // ════════════════════════════════════════════════════════════════
        // 4. CATEGORIES CARD
        // ════════════════════════════════════════════════════════════════
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.Category,
                    title = "Categories",
                    subtitle = "${vm.categories.size} categories configured"
                )

                // Compact Flow View of Categories
                OptInFlowRowCategories(vm)

                Spacer(Modifier.height(Space.s2))

                // Quick Add Category Bar
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PfField(
                        value = vm.newCategoryText,
                        onValueChange = { vm.newCategoryText = it },
                        placeholder = "New category name…",
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton("Add", vm::addCategory, enabled = vm.newCategoryText.isNotBlank())
                }

                Spacer(Modifier.height(Space.s2))
                Hairline()
                Spacer(Modifier.height(Space.s1))

                // Expandable Full Category Manager (Reorder / Rename / Delete)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.Sm)
                        .clickable { showManageCategories = !showManageCategories }
                        .padding(vertical = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showManageCategories) "Hide Category Manager ▲" else "Reorder & Edit Categories (${vm.categories.size}) ▼",
                        color = Pf.Accent400,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showManageCategories) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Pf.Surface2.copy(alpha = 0.5f), Radius.Md)
                            .border(1.dp, Pf.Hairline, Radius.Md)
                            .padding(Space.s3),
                        verticalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
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
                                    Text(cat, Modifier.weight(1f), color = Pf.Text, fontSize = 13.sp)
                                    SmallIcon(Icons.Default.ArrowUpward, "Move up", i > 0) { vm.moveCategory(i, -1) }
                                    SmallIcon(Icons.Default.ArrowDownward, "Move down", i < vm.categories.size - 1) { vm.moveCategory(i, 1) }
                                    SmallIcon(Icons.Default.Edit, "Rename", true) { vm.startEditCategory(cat) }
                                    SmallIcon(Icons.Default.Delete, "Remove", true) { vm.removeCategory(cat) }
                                }
                            }
                            if (i < vm.categories.size - 1) Hairline()
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // 5. BUDGETS CARD
        // ════════════════════════════════════════════════════════════════
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                SettingHeader(
                    icon = Icons.Default.PieChart,
                    title = "Budgets",
                    subtitle = "Monthly category spending caps"
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
                        Muted("Underspend carries over to next month", size = 11)
                    }
                    if (vm.budgetRollover) {
                        SecondaryButton("On", { vm.setBudgetRollover(false) })
                    } else {
                        SecondaryButton("Off", { vm.setBudgetRollover(true) })
                    }
                }

                if (vm.budgets.isNotEmpty()) {
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
                }

                if (vm.budgetableCategories.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s2))
                    Row(
                        Modifier.fillMaxWidth(),
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
                            modifier = Modifier.width(100.dp)
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

        // ════════════════════════════════════════════════════════════════
        // 6. MORE SETTINGS (ADVANCED & INTEGRATIONS)
        // ════════════════════════════════════════════════════════════════
        item {
            PfCard(padding = PaddingValues(Space.s4)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.Sm)
                        .clickable { showMoreSettings = !showMoreSettings }
                        .padding(vertical = Space.s1),
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
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = Pf.Accent400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "More Settings",
                                color = Pf.Text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "SMS import, sync, AI key & sample data",
                                color = Pf.Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        if (showMoreSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Pf.Accent400,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (showMoreSettings) {
                    Column(
                        Modifier.padding(top = Space.s3),
                        verticalArrangement = Arrangement.spacedBy(Space.s4)
                    ) {
                        Hairline()

                        // SMS Import
                        SmsImportSection(vm, showSmsLog) { showSmsLog = it }

                        // SMS Rules
                        SmsRulesSection(vm)

                        // Firestore & OpenAI Sync Card
                        PfCard(
                            Modifier.fillMaxWidth(),
                            padding = PaddingValues(Space.s3)
                        ) {
                            SettingHeader(
                                icon = Icons.Default.Sync,
                                title = "Sync & Integrations",
                                subtitle = "Cloud database & AI configuration"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                                DebouncedField(
                                    label = "Firebase Config (apiKey, projectId, appId…)",
                                    value = vm.firebaseConfigText,
                                    onSettled = vm::setFirebaseConfig,
                                    placeholder = "Paste values separated by commas",
                                    singleLine = false,
                                    allowBlank = true
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Muted("Firestore Cloud Sync")
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

                                Spacer(Modifier.height(Space.s1))
                                Hairline()
                                Spacer(Modifier.height(Space.s1))

                                DebouncedField(
                                    label = "OpenAI API Key (for Smart Assistant)",
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
                                    Muted("OpenAI Assistant")
                                    if (vm.openaiKeyText.isNotBlank()) Tag("Configured", Pf.Accent100, Pf.Accent800)
                                    else OutlineTag("Not set")
                                }
                            }
                        }

                        // Sample Data Card (if any)
                        if (vm.sampleDataCount > 0) {
                            PfCard(
                                Modifier.fillMaxWidth(),
                                padding = PaddingValues(Space.s3)
                            ) {
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
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        color = Pf.Accent400,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = Space.s1, top = Space.s2, bottom = Space.s1)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptInFlowRowCategories(vm: FinTrackViewModel) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        vm.categories.forEach { cat ->
            Box(
                Modifier
                    .background(Pf.Surface2, Radius.Pill)
                    .border(1.dp, Pf.Hairline, Radius.Pill)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(cat, color = Pf.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
 * Bank SMS import section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsImportSection(
    vm: FinTrackViewModel,
    showSmsLog: Boolean,
    onToggleSmsLog: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasPermission by remember { mutableStateOf(hasSmsPermission(context)) }
    var canNotify by remember { mutableStateOf(hasNotifyPermission(context)) }

    val asked = vm.smsAsked
    val blocked = asked && !hasPermission && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)

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
                SecondaryButton("Re-check accounts", { vm.rematchImports() })
            }
        }

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

        if (!hasPermission) {
            Column(Modifier.padding(top = Space.s2)) {
                Muted(
                    when {
                        blocked -> "Android won't ask again. Two steps in app info:"
                        asked -> "Declined. Nothing is read until you allow it."
                        else -> "Messages are read on this phone only. Amount, payee and reference are kept — nothing else."
                    }
                )
                if (blocked) {
                    Column(Modifier.padding(top = Space.s1)) {
                        Muted("1. Tap ⋮ at the top right → Allow restricted settings")
                        Muted("2. Permissions → SMS → Allow")
                    }
                    Muted(
                        "That first step exists because the app was installed from a file rather than a store.",
                        Modifier.padding(top = Space.s2)
                    )
                }
            }
        }

        if (vm.scanNote.isNotEmpty()) {
            Muted(vm.scanNote, Modifier.padding(top = Space.s2))
        }
        Muted("${vm.importedCount} imported from SMS", Modifier.padding(top = Space.s1))

        // Collapsible SMS Debug Log
        if (vm.smsLog.isNotEmpty()) {
            Spacer(Modifier.height(Space.s2))
            Hairline()
            Spacer(Modifier.height(Space.s1))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.Sm)
                    .clickable { onToggleSmsLog(!showSmsLog) }
                    .padding(vertical = Space.s1),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showSmsLog) "Hide SMS Log ▲" else "View Recent SMS Messages (${vm.smsLog.size}) ▼",
                    color = Pf.Accent400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showSmsLog) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Surface2, Radius.Md)
                        .border(1.dp, Pf.Hairline, Radius.Md)
                        .padding(Space.s3)
                ) {
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
        }
    }
}

private fun hasNotifyPermission(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

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
            subtitle = "${rules.size} merchant keyword mapping rules"
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

private fun formatYearMonth(ym: String): String {
    val parts = ym.split("-")
    if (parts.size != 2) return ym
    val y = parts[0]
    val m = parts[1].toIntOrNull() ?: return ym
    val months = listOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val monthName = months.getOrNull(m) ?: ym
    return "$monthName $y"
}

@Composable
private fun SalarySection(vm: FinTrackViewModel, profile: String) {
    val defaultSalary = vm.salaryAmountFor(profile)
    val defaultResetDay = vm.salaryResetDayFor(profile)
    var mainSalaryText by remember(defaultSalary) {
        mutableStateOf(if (defaultSalary > 0.0) defaultSalary.toLong().toString() else "")
    }
    var show3MonthOverrides by remember { mutableStateOf(false) }

    PfCard(padding = PaddingValues(Space.s4)) {
        SettingHeader(
            icon = Icons.Default.Payments,
            title = "Monthly Salary & Payday",
            subtitle = "Base income used for budget & surplus projections"
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            // Row 1: Salary amount and Payday reset
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Salary input
                Column(Modifier.weight(1.3f)) {
                    Muted("Base Salary (₹)")
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = mainSalaryText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            mainSalaryText = clean
                            val amt = clean.toDoubleOrNull() ?: 0.0
                            vm.setSalaryAmountFor(profile, amt)
                        },
                        placeholder = { Text("0", color = Pf.Muted, fontSize = 14.sp) },
                        singleLine = true,
                        shape = Radius.Sm,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        trailingIcon = {
                            if (mainSalaryText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        mainSalaryText = ""
                                        vm.setSalaryAmountFor(profile, 0.0)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear salary",
                                        tint = Pf.Muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Pf.Surface2,
                            unfocusedContainerColor = Pf.Surface2,
                            focusedBorderColor = Pf.Accent,
                            unfocusedBorderColor = Pf.Hairline,
                            cursorColor = Pf.Accent,
                            focusedTextColor = Pf.Text,
                            unfocusedTextColor = Pf.Text
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Payday Day selector
                Column(Modifier.weight(0.9f)) {
                    Muted("Payday / Reset")
                    Spacer(Modifier.height(4.dp))
                    PfSelect(
                        value = "${defaultResetDay}th",
                        options = (1..28).map { "${it}th" },
                        onSelect = { selected ->
                            val day = selected.removeSuffix("th").toIntOrNull() ?: 1
                            vm.setSalaryDate(profile, day)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Muted(
                if (defaultSalary > 0.0) "Budget and projections start fresh on the ${defaultResetDay}th of each month."
                else "Enter your base take-home salary to track monthly cash flow and savings."
            )

            Spacer(Modifier.height(Space.s1))
            Hairline()

            // Expandable 3-Month Salary Overrides
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.Sm)
                    .clickable { show3MonthOverrides = !show3MonthOverrides }
                    .padding(vertical = Space.s1),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (show3MonthOverrides) "Hide Upcoming 3 Months Overrides ▲" else "Upcoming 3 Months Salary Overrides ▼",
                        color = Pf.Accent400,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Set custom salary for bonus/variable months",
                        color = Pf.Muted,
                        fontSize = 11.sp
                    )
                }
            }

            if (show3MonthOverrides) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Surface2.copy(alpha = 0.5f), Radius.Md)
                        .border(1.dp, Pf.Hairline, Radius.Md)
                        .padding(Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Space.s3)
                ) {
                    val months = (1..3).map { Ledger.addMonths(today(), it).take(7) }
                    months.forEach { ym ->
                        SalaryOverrideMonthItem(vm, profile, ym, defaultSalary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryOverrideMonthItem(
    vm: FinTrackViewModel,
    profile: String,
    yearMonth: String,
    defaultSalary: Double
) {
    val override = vm.getSalaryOverride(profile, yearMonth)
    val hasOverride = override != null && override.amount > 0.0
    var draftText by remember(override?.amount) {
        mutableStateOf(if (hasOverride) override!!.amount.toLong().toString() else "")
    }

    val displayMonth = formatYearMonth(yearMonth)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2)
    ) {
        Column(Modifier.weight(1.2f)) {
            Text(
                displayMonth,
                color = Pf.Text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (hasOverride) "Custom: ₹${inr(override!!.amount)}" else "Default: ₹${inr(defaultSalary)}",
                color = if (hasOverride) Pf.Accent400 else Pf.Muted,
                fontSize = 11.sp
            )
        }

        OutlinedTextField(
            value = draftText,
            onValueChange = { input ->
                val clean = input.filter { it.isDigit() }
                draftText = clean
                val amt = clean.toDoubleOrNull()
                if (amt != null && amt > 0.0) {
                    vm.setSalaryOverride(profile, yearMonth, amt, null)
                } else {
                    vm.removeSalaryOverride(profile, yearMonth)
                }
            },
            placeholder = {
                Text(
                    if (defaultSalary > 0.0) "₹${defaultSalary.toLong()}" else "0",
                    color = Pf.Muted,
                    fontSize = 13.sp
                )
            },
            singleLine = true,
            shape = Radius.Sm,
            textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 13.sp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            trailingIcon = {
                if (draftText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            draftText = ""
                            vm.removeSalaryOverride(profile, yearMonth)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Reset override",
                            tint = Pf.Muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Pf.Surface,
                unfocusedContainerColor = Pf.Surface,
                focusedBorderColor = Pf.Accent,
                unfocusedBorderColor = Pf.Hairline,
                cursorColor = Pf.Accent,
                focusedTextColor = Pf.Text,
                unfocusedTextColor = Pf.Text
            ),
            modifier = Modifier.weight(1f)
        )

        if (hasOverride) {
            IconButton(
                onClick = {
                    draftText = ""
                    vm.removeSalaryOverride(profile, yearMonth)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear override",
                    tint = Pf.Accent400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

