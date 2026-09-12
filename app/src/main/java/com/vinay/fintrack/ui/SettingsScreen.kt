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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import com.vinay.fintrack.sms.FinTrackNotificationListener
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
import androidx.compose.ui.window.Dialog
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.inr

@Composable
fun SettingsScreen(vm: FinTrackViewModel) {
    val profile = vm.activeProfile ?: vm.profileNames.firstOrNull() ?: "Vinay"
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
        // 0. APP FEATURES & USER GUIDE CARD
        // ════════════════════════════════════════════════════════════════
        item {
            FeaturesGuideSection()
        }

        // ════════════════════════════════════════════════════════════════
        // 0.5. PROFILE MANAGEMENT CARD
        // ════════════════════════════════════════════════════════════════
        item {
            ProfileManagementSection(vm)
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

                        // 100% On-Device Storage & Local Backup Section
                        LocalStorageBackupSection(vm)

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
 * Bank SMS and UPI Notification Auto-Tracking section.
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
    var hasNotifAccess by remember { mutableStateOf(FinTrackNotificationListener.isNotificationAccessGranted(context)) }
    var hasSmsPerm by remember { mutableStateOf(hasSmsPermission(context)) }
    var canNotify by remember { mutableStateOf(hasNotifyPermission(context)) }

    val asked = vm.smsAsked
    val blocked = asked && !hasSmsPerm && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifAccess = FinTrackNotificationListener.isNotificationAccessGranted(context)
                hasSmsPerm = hasSmsPermission(context)
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
        hasSmsPerm = hasSmsPermission(context)
        canNotify = hasNotifyPermission(context)
        if (hasSmsPerm) vm.setSmsImport(true)
    }

    PfCard(padding = PaddingValues(Space.s4)) {
        SettingHeader(
            icon = Icons.Default.NotificationsActive,
            title = "Bank SMS & UPI Auto-Tracking",
            subtitle = "Real-time tracking for Bank SMS, GPay, PhonePe, Paytm & CRED"
        )
        Muted("Catches transaction alerts from Bank SMS and UPI apps in real time without manual entry.")

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Muted("Tracking Status")
            when {
                vm.smsImportOn && hasNotifAccess -> Tag("Active (SMS + UPI)", Pf.Accent2_100, Pf.Accent2_800)
                vm.smsImportOn && hasSmsPerm -> Tag("Active (SMS only)", Pf.Accent2_100, Pf.Accent2_800)
                vm.smsImportOn -> Tag("Permission Needed", Pf.Neutral100, Pf.Neutral800)
                else -> OutlineTag("Paused")
            }
        }

        FlowRow(
            Modifier.padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            if (!hasNotifAccess) {
                PrimaryButton("Enable Live Tracking (SMS & UPI)", onClick = {
                    FinTrackNotificationListener.openNotificationAccessSettings(context)
                })
            } else if (vm.smsImportOn) {
                SecondaryButton("Pause auto-tracking", { vm.setSmsImport(false) })
                GhostButton("Tracking Access Settings", onClick = {
                    FinTrackNotificationListener.openNotificationAccessSettings(context)
                })
            } else {
                PrimaryButton("Resume auto-tracking", { vm.setSmsImport(true) })
            }

            if (hasSmsPerm) {
                SecondaryButton(
                    if (vm.scanning) "Reading…" else "Import past 60 days (SMS)",
                    { vm.backfillSms() },
                    enabled = !vm.scanning
                )
                SecondaryButton("Re-check accounts", { vm.rematchImports() })
            } else if (!blocked) {
                SecondaryButton("Allow SMS for past 60d import", onClick = {
                    permissionLauncher.launch(smsPermissions())
                })
            }
        }

        if (hasNotifAccess) {
            Column(Modifier.padding(top = Space.s2)) {
                Muted("✓ Live tracking active: Bank SMS, Google Pay, PhonePe, Paytm & CRED alerts are logged automatically.")
            }
        } else {
            Column(Modifier.padding(top = Space.s2)) {
                Muted("Enable Live Tracking to allow FinTrack to parse incoming Bank SMS and UPI payment banners. 100% private, processed on device.")
            }
        }

        if ((hasNotifAccess || hasSmsPerm) && !canNotify) {
            Column(Modifier.padding(top = Space.s3)) {
                Muted("Posting notifications is off, so recorded expenses happen silently.")
                Row(Modifier.padding(top = Space.s2)) {
                    SecondaryButton("Notify me when expense is recorded", onClick = {
                        permissionLauncher.launch(smsPermissions())
                    })
                }
            }
        }

        if (vm.scanNote.isNotEmpty()) {
            Muted(vm.scanNote, Modifier.padding(top = Space.s2))
        }
        Muted("${vm.importedCount} imported transaction(s)", Modifier.padding(top = Space.s1))

        // Collapsible Debug Log
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
                    if (showSmsLog) "Hide Import Log ▲" else "View Recent Captured Alerts (${vm.smsLog.size}) ▼",
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
    val defaultResetDay = vm.salaryResetDayFor(profile)

    PfCard(padding = PaddingValues(Space.s4)) {
        SettingHeader(
            icon = Icons.Default.Payments,
            title = "Payday & Salary Settings",
            subtitle = "Choose payday reset cycle and upcoming monthly salaries"
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            // Payday selector
            Column(Modifier.fillMaxWidth()) {
                Muted("Payday / Reset Day of Month")
                Spacer(Modifier.height(4.dp))
                PfSelect(
                    value = "${defaultResetDay}th of each month",
                    options = (1..28).map { "${it}th of each month" },
                    onSelect = { selected ->
                        val day = selected.substringBefore("th").toIntOrNull() ?: 1
                        vm.setSalaryDate(profile, day)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Muted("Budget, dues, and cash flow reset on the ${defaultResetDay}th of each month.")
            }

            Spacer(Modifier.height(Space.s1))
            Hairline()

            // Upcoming 3 Months Salary Section
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Column {
                    Text(
                        "Upcoming 3 Months Salary",
                        color = Pf.Accent400,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Set salary for each upcoming month. Leave blank (or clear) if none.",
                        color = Pf.Muted,
                        fontSize = 11.5.sp
                    )
                }

                val months = (1..3).map { vm.upcomingPaydayDate(profile, it).take(7) }
                months.forEach { ym ->
                    SalaryOverrideMonthItem(vm, profile, ym)
                }
            }
        }
    }
}

@Composable
private fun SalaryOverrideMonthItem(
    vm: FinTrackViewModel,
    profile: String,
    yearMonth: String
) {
    val override = vm.getSalaryOverride(profile, yearMonth)
    val defaultSalary = vm.salaryFor(profile)
    val isExplicitZero = override != null && override.amount == 0.0
    val isCustom = override != null && override.amount > 0.0

    var draftText by remember(override?.amount) {
        mutableStateOf(
            when {
                isCustom -> override!!.amount.toLong().toString()
                isExplicitZero -> "0"
                else -> ""
            }
        )
    }

    val displayMonth = formatYearMonth(yearMonth)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(Radius.Sm)
            .background(
                when {
                    isCustom -> Pf.Accent.copy(alpha = 0.08f)
                    isExplicitZero -> Pf.Rose.copy(alpha = 0.06f)
                    else -> Pf.Surface
                }
            )
            .border(
                1.dp,
                when {
                    isCustom -> Pf.Accent.copy(alpha = 0.35f)
                    isExplicitZero -> Pf.Rose.copy(alpha = 0.35f)
                    else -> Pf.Hairline
                },
                Radius.Sm
            )
            .padding(Space.s3)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        displayMonth,
                        color = Pf.Text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            isCustom -> "Custom: ₹${inr(override!!.amount)}"
                            isExplicitZero -> "Cleared (₹0 salary)"
                            else -> "Default: ₹${inr(defaultSalary)}"
                        },
                        color = when {
                            isCustom -> Pf.Accent400
                            isExplicitZero -> Pf.Rose
                            else -> Pf.Muted
                        },
                        fontSize = 11.5.sp,
                        fontWeight = if (isCustom || isExplicitZero) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s1),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (override != null) {
                        Row(
                            Modifier
                                .clip(Radius.Sm)
                                .clickable {
                                    draftText = ""
                                    vm.resetSalaryOverride(profile, yearMonth)
                                }
                                .background(Pf.Surface2)
                                .padding(horizontal = Space.s2, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Reset Default",
                                color = Pf.Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!isExplicitZero) {
                        Row(
                            Modifier
                                .clip(Radius.Sm)
                                .clickable {
                                    draftText = "0"
                                    vm.clearSalaryOverride(profile, yearMonth)
                                }
                                .background(Pf.Rose.copy(alpha = 0.12f))
                                .padding(horizontal = Space.s2, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear salary",
                                tint = Pf.Rose,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "Clear",
                                color = Pf.Rose,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draftText,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    draftText = clean
                    val amt = clean.toDoubleOrNull()
                    if (amt != null) {
                        vm.setSalaryOverride(profile, yearMonth, amt, null)
                    } else {
                        vm.clearSalaryOverride(profile, yearMonth)
                    }
                },
                placeholder = {
                    Text(
                        if (isExplicitZero) "Cleared (₹0)" else "Default: ₹${inr(defaultSalary)} (enter custom)",
                        color = Pf.Muted.copy(alpha = 0.6f),
                        fontSize = 12.5.sp
                    )
                },
                singleLine = true,
                shape = Radius.Sm,
                textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 13.sp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                trailingIcon = {
                    if (draftText.isNotEmpty() && draftText != "0") {
                        IconButton(
                            onClick = {
                                draftText = "0"
                                vm.clearSalaryOverride(profile, yearMonth)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear input",
                                tint = Pf.Muted,
                                modifier = Modifier.size(14.dp)
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
    }
}

@Composable
private fun ProfileManagementSection(vm: FinTrackViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newNameDraft by remember { mutableStateOf("") }
    var newPinDraft by remember { mutableStateOf("1234") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renamingTarget by remember { mutableStateOf("") }
    var renameDraft by remember { mutableStateOf("") }

    var deleteConfirmTarget by remember { mutableStateOf<String?>(null) }

    PfCard(padding = PaddingValues(Space.s4)) {
        SettingHeader(
            icon = Icons.Default.Person,
            title = "Profile Management",
            subtitle = "Manage household members and active profile",
            action = {
                GhostButton("+ Add Profile", onClick = {
                    newNameDraft = ""
                    newPinDraft = "1234"
                    showAddDialog = true
                })
            }
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.profileNames.forEachIndexed { idx, name ->
                if (idx > 0) Hairline()
                val isActive = name == vm.activeProfile

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s3)
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(if (isActive) Pf.Accent else Pf.Surface2, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(1).uppercase(),
                                color = if (isActive) Pf.OnAccent else Pf.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    name,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isActive) {
                                    Tag("Active", Pf.Accent.copy(alpha = 0.15f), Pf.Accent400)
                                }
                            }
                            val salary = vm.salaryFor(name)
                            val resetDay = vm.salaryResetDayFor(name)
                            Muted("Payday: ${resetDay}th · Salary: ${inr(salary)}", size = 11)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s1)
                    ) {
                        if (!isActive) {
                            GhostButton("Switch", onClick = {
                                vm.setActiveProfileFromSettings(name)
                            })
                        }
                        IconButton(
                            onClick = {
                                renamingTarget = name
                                renameDraft = name
                                showRenameDialog = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename profile",
                                tint = Pf.Muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (vm.profileNames.size > 1 && !isActive) {
                            IconButton(
                                onClick = { deleteConfirmTarget = name },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete profile",
                                    tint = Pf.Rose,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (vm.profileMsg.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(vm.profileMsg, color = Pf.Accent400, fontSize = 12.sp)
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface, Radius.Lg)
                    .border(1.dp, Pf.Hairline, Radius.Lg)
                    .padding(Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Text("Add New Profile", color = Pf.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                PfField(
                    label = "Profile Name",
                    value = newNameDraft,
                    onValueChange = { newNameDraft = it.take(20) },
                    placeholder = "e.g. Bhargav"
                )
                PfField(
                    label = "4-digit PIN (default: 1234)",
                    value = newPinDraft,
                    onValueChange = { newPinDraft = it.filter { c -> c.isDigit() }.take(4) },
                    numeric = true
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    SecondaryButton("Cancel", { showAddDialog = false }, Modifier.weight(1f))
                    PrimaryButton(
                        "Create",
                        {
                            vm.addProfile(newNameDraft, newPinDraft)
                            showAddDialog = false
                        },
                        Modifier.weight(1f),
                        enabled = newNameDraft.trim().isNotEmpty()
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        Dialog(onDismissRequest = { showRenameDialog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface, Radius.Lg)
                    .border(1.dp, Pf.Hairline, Radius.Lg)
                    .padding(Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Text("Rename Profile", color = Pf.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Muted("Renaming will update all associated accounts, cards, and transactions.")
                PfField(
                    label = "New Name",
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(20) }
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    SecondaryButton("Cancel", { showRenameDialog = false }, Modifier.weight(1f))
                    PrimaryButton(
                        "Save",
                        {
                            vm.startRenameProfile(renamingTarget)
                            vm.editRenameText(renameDraft)
                            vm.saveRenameProfile()
                            showRenameDialog = false
                        },
                        Modifier.weight(1f),
                        enabled = renameDraft.trim().isNotEmpty() && renameDraft.trim() != renamingTarget
                    )
                }
            }
        }
    }

    deleteConfirmTarget?.let { target ->
        Dialog(onDismissRequest = { deleteConfirmTarget = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface, Radius.Lg)
                    .border(1.dp, Pf.Hairline, Radius.Lg)
                    .padding(Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Text("Delete Profile?", color = Pf.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Muted("Are you sure you want to remove '$target'? Their existing entries and accounts will remain intact.")
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    SecondaryButton("Cancel", { deleteConfirmTarget = null }, Modifier.weight(1f))
                    PrimaryButton(
                        "Delete",
                        {
                            vm.removeProfile(target)
                            deleteConfirmTarget = null
                        },
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalStorageBackupSection(vm: FinTrackViewModel) {
    val context = LocalContext.current
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreInputText by remember { mutableStateOf("") }

    PfCard(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(Space.s3)
    ) {
        SettingHeader(
            icon = Icons.Default.PhoneAndroid,
            title = "Data Storage & Backup",
            subtitle = "100% On-Device & Free Forever"
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Storage Engine", color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Muted("Data stays strictly on your phone — ₹0 cloud bills", size = 11)
                }
                Tag("On-Device (Free)", Pf.Accent2_100, Pf.Accent2_800)
            }

            Muted(
                "All accounts, transactions, and budgets are saved locally. " +
                    "Your financial data is private and never uploaded to any remote server."
            )

            // Backup & Restore Action Buttons
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                PrimaryButton("Export Backup (Copy JSON)", onClick = {
                    val json = vm.exportBackupJson()
                    if (json.isNotBlank()) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("FinTrack Backup", json)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Backup copied to clipboard! Paste it anywhere to save.", android.widget.Toast.LENGTH_LONG).show()
                    }
                })

                SecondaryButton(
                    if (showRestoreDialog) "Cancel Restore" else "Restore from Backup",
                    onClick = {
                        showRestoreDialog = !showRestoreDialog
                        vm.clearBackupStatus()
                    }
                )
            }

            if (showRestoreDialog) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Surface2, Radius.Md)
                        .border(1.dp, Pf.Hairline, Radius.Md)
                        .padding(Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    Text(
                        "Paste Backup JSON below:",
                        color = Pf.Text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = restoreInputText,
                        onValueChange = { restoreInputText = it },
                        placeholder = { Text("Paste JSON backup here…", color = Pf.Muted, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        shape = Radius.Sm,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Pf.Surface,
                            unfocusedContainerColor = Pf.Surface,
                            focusedBorderColor = Pf.Accent,
                            unfocusedBorderColor = Pf.Hairline,
                            cursorColor = Pf.Accent,
                            focusedTextColor = Pf.Text,
                            unfocusedTextColor = Pf.Text
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.s2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrimaryButton("Apply Restore", onClick = {
                            val ok = vm.importBackupJson(restoreInputText)
                            if (ok) {
                                restoreInputText = ""
                                showRestoreDialog = false
                            }
                        }, enabled = restoreInputText.isNotBlank())
                    }
                }
            }

            if (vm.backupStatusMsg.isNotEmpty()) {
                Text(
                    vm.backupStatusMsg,
                    color = if (vm.backupStatusIsError) Pf.Accent400 else Color(0xFF34D399),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(Space.s1))
            Hairline()
            Spacer(Modifier.height(Space.s1))

            // OpenAI API Key
            DebouncedField(
                label = "OpenAI API Key (Optional for Smart Assistant)",
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
}

@Composable
private fun FeaturesGuideSection() {
    var expanded by remember { mutableStateOf(false) }

    PfCard(padding = PaddingValues(Space.s4)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius.Sm)
                .clickable { expanded = !expanded }
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
                        .background(Pf.Accent700.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Pf.Accent400,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        "Features & User Guide",
                        color = Pf.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (expanded) "Tap to collapse guide ▲" else "Learn how FinTrack manages your money ▼",
                        color = Pf.Accent400,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Pf.Accent400,
                modifier = Modifier.size(22.dp)
            )
        }

        if (expanded) {
            Column(
                Modifier.padding(top = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Hairline()

                // Feature 1: Live SMS & UPI Auto-Tracking
                FeatureGuideItem(
                    emoji = "⚡",
                    title = "Live SMS & UPI Auto-Tracking",
                    tag = "Zero Effort",
                    tagColor = Pf.Accent2_100,
                    tagTextColor = Pf.Accent2_800,
                    description = "FinTrack catches incoming Bank SMS (HDFC, ICICI, SBI, Axis, etc.) and UPI alerts (Google Pay, PhonePe, Paytm, CRED) directly from your status bar in real-time. It parses the amount, merchant, and account digits instantly with zero manual entry!"
                )

                // Feature 2: Hero Card & True Net Balance
                FeatureGuideItem(
                    emoji = "💰",
                    title = "Hero Card & Net Cash Flow",
                    tag = "Cash Flow",
                    tagColor = Pf.Accent100,
                    tagTextColor = Pf.Accent800,
                    description = "Unlike traditional apps that only show bank balances, FinTrack calculates your true disposable income:\n• Bank Balances — Upcoming Expenses + Upcoming Salary = Net Balance.\nIt also previews your next month's projected cash flow so you never overspend."
                )

                // Feature 3: Set-A-Side (Sinking Funds)
                FeatureGuideItem(
                    emoji = "🎯",
                    title = "Set-A-Side (Sinking Funds)",
                    tag = "Smart Planning",
                    tagColor = Pf.Accent.copy(alpha = 0.2f),
                    tagTextColor = Pf.Accent400,
                    description = "Never get caught off-guard by big annual or periodic expenses (e.g. ₹24,000 Car Insurance in 6 months).\n• FinTrack splits the total into ₹4,000/month from each salary.\n• You can set a Start Pay Month so expenses start counting exactly when you want."
                )

                // Feature 4: Dynamic Payday & Salary Rollover
                FeatureGuideItem(
                    emoji = "📅",
                    title = "Payday Reset & Salary Rollovers",
                    tag = "Dynamic Cycles",
                    tagColor = Pf.Neutral100,
                    tagTextColor = Pf.Neutral800,
                    description = "Choose your payday reset day (e.g., 6th of each month). FinTrack aligns budgets, dues, and cash flow to your salary cycle rather than the rigid 1st of the month. After your payday passes, the next 3 months roll over automatically!"
                )

                // Feature 5: Loans & Credit Card Debt Manager
                FeatureGuideItem(
                    emoji = "💳",
                    title = "Credit Cards & Loan EMIs",
                    tag = "Debt Free",
                    tagColor = Pf.Rose.copy(alpha = 0.2f),
                    tagTextColor = Pf.Rose,
                    description = "Track credit card statements, due dates, and minimum dues in one clean list. For loans and EMIs, FinTrack counts down remaining tenure months and automatically stops subtracting the EMI when the loan is finished."
                )

                // Feature 6: Monthly Budgets & Rollovers
                FeatureGuideItem(
                    emoji = "📊",
                    title = "Monthly Budgets & Leftover Rollover",
                    tag = "Disciplined Spends",
                    tagColor = Pf.Accent2_100,
                    tagTextColor = Pf.Accent2_800,
                    description = "Set spending caps for categories like Food, Fuel, or Shopping. Turn on 'Rollover leftover' to carry underspent amounts into next month's budget!"
                )

                // Feature 7: 100% On-Device & Total Privacy
                FeatureGuideItem(
                    emoji = "🛡️",
                    title = "100% On-Device & ₹0 Cost",
                    tag = "Private & Free",
                    tagColor = Pf.Accent2_100,
                    tagTextColor = Pf.Accent2_800,
                    description = "Your financial data and bank messages are processed strictly on your phone and never uploaded to any cloud server. You get total privacy, offline capability, and 1-tap JSON backups at ₹0 cost forever."
                )
            }
        }
    }
}

@Composable
private fun FeatureGuideItem(
    emoji: String,
    title: String,
    tag: String,
    tagColor: Color,
    tagTextColor: Color,
    description: String
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(Pf.Surface2.copy(alpha = 0.55f))
            .border(1.dp, Pf.Hairline, Radius.Md)
            .padding(Space.s3)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(emoji, fontSize = 16.sp)
                    Text(
                        title,
                        color = Pf.Text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Tag(tag, tagColor, tagTextColor)
            }
            Text(
                description,
                color = Pf.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


