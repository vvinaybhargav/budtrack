package com.vinay.fintrack.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.ScreenshotMover
import com.vinay.fintrack.data.SyncStatus
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.today
import com.vinay.fintrack.work.ScreenshotWorker

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
                            PfField(
                                value = limit.toLong().toString(),
                                onValueChange = { v ->
                                    vm.setBudget(cat, v.toDoubleOrNull() ?: 0.0)
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

        item { ScreenshotImportSection(vm) }

        item { Hairline() }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Heading("Sync")
                PfField(
                    "Firebase config — apiKey, projectId, storageBucket, messagingSenderId, appId",
                    vm.firebaseConfigText,
                    vm::setFirebaseConfig,
                    placeholder = "paste the six values, separated by commas",
                    singleLine = false
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
                PfField(
                    "OpenAI API key — for Smart Add",
                    vm.openaiKeyText,
                    vm::setOpenaiKey,
                    placeholder = "sk-…"
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
 * PhonePe screenshot import. Two system prompts are involved and neither can be
 * avoided: reading images at all, and — on Android 11+ — moving files the app
 * didn't create. The move consent is asked once per batch, not per file.
 */
@Composable
private fun ScreenshotImportSection(vm: FinTrackViewModel) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ScreenshotWorker.hasMediaPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) vm.setScreenshotImport(true)
    }

    val moveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val moved = ScreenshotMover(context).move(vm.pendingMoveUris())
            vm.clearPendingMoves()
            vm.noteMoved(moved)
        }
    }

    Column {
        Heading("PhonePe screenshots")
        Muted(
            "Reads receipts from your Screenshots folder with on-device OCR and " +
                "adds them as transactions automatically."
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Muted("Automatic scan")
            if (vm.screenshotImportOn) Tag("On", Pf.Accent2_100, Pf.Accent2_800)
            else OutlineTag("Off")
        }

        Row(
            Modifier.padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            if (!hasPermission) {
                PrimaryButton("Allow access") {
                    permissionLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )
                }
            } else if (vm.screenshotImportOn) {
                SecondaryButton("Turn off", { vm.setScreenshotImport(false) })
            } else {
                PrimaryButton("Turn on", { vm.setScreenshotImport(true) })
            }
            if (hasPermission) {
                SecondaryButton(
                    if (vm.scanning) "Scanning…" else "Scan now",
                    vm::scanScreenshotsNow,
                    enabled = !vm.scanning
                )
            }
        }

        if (vm.scanNote.isNotEmpty()) {
            Muted(vm.scanNote, Modifier.padding(top = Space.s2))
        }
        Muted(
            "${vm.importedCount} imported so far" +
                if (vm.lastScanAt > 0L) " · last scan ${vm.lastScanClock}" else "",
            Modifier.padding(top = Space.s1)
        )

        // Android will not relocate another app's images silently, so this is a
        // deliberate button rather than something that happens behind your back.
        if (vm.pendingMoveCount > 0) {
            Column(Modifier.padding(top = Space.s3)) {
                Muted("${vm.pendingMoveCount} screenshot(s) ready to move into Pictures/PhonePe")
                PrimaryButton("Move to PhonePe folder") {
                    val sender = ScreenshotMover(context).consentRequest(vm.pendingMoveUris())
                    if (sender != null) {
                        moveLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        val moved = ScreenshotMover(context).move(vm.pendingMoveUris())
                        vm.clearPendingMoves()
                        vm.noteMoved(moved)
                    }
                }
            }
        }
    }
}
