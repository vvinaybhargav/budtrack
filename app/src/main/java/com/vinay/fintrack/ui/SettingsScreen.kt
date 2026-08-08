package com.vinay.fintrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel

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
                Heading("Default account")
                PfSelect(
                    value = vm.defaultAccount,
                    options = vm.visibleAccounts.map { it.name },
                    onSelect = vm::setDefaultAccount
                )
            }
        }

        item { Hairline() }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Heading("Sync")
                PfField(
                    "Firebase config — apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId",
                    vm.firebaseConfigText,
                    vm::setFirebaseConfig,
                    placeholder = "paste comma-separated values here"
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Muted("Firebase")
                    if (vm.firebaseConfigText.isNotBlank()) Tag("Configured", Pf.Accent100, Pf.Accent800)
                    else OutlineTag("Not connected")
                }
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
