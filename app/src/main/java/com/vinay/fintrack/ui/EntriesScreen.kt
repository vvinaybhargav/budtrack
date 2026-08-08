package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.inr

@Composable
fun EntriesScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s3)
    ) {
        item { Text("Transactions", color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface2, Radius.Pill)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BucketTab(vm.activeProfile ?: "Personal", vm.bucketView == "PERSONAL", Modifier.weight(1f)) {
                    vm.bucketView = "PERSONAL"
                }
                BucketTab("Joint", vm.bucketView == "JOINT", Modifier.weight(1f)) {
                    vm.bucketView = "JOINT"
                }
            }
        }

        item {
            PfField(
                value = vm.entriesSearch,
                onValueChange = { vm.entriesSearch = it },
                placeholder = "Search category or note…"
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Chip("All", vm.entriesCategoryFilter == null, onClick = { vm.setCategoryFilter(null) })
                vm.availableChips.forEach { c ->
                    Chip(c, vm.entriesCategoryFilter == c, onClick = { vm.setCategoryFilter(c) })
                }
            }
        }

        val rows = vm.filteredEntries
        if (rows.isEmpty()) {
            item {
                Text(
                    "No entries match.",
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s6),
                    color = Pf.Muted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(rows, key = { it.id }) { e ->
                PfCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { vm.openEditEntry(e) }
                        ) {
                            Text(
                                if (vm.bucketView == "JOINT") "${e.category} — ${e.person}" else e.category,
                                color = Pf.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Muted("${e.type} · ${e.frequency}" + if (e.note.isNotEmpty()) " · ${e.note}" else "")
                            Text(
                                inr(e.amount) + if (e.frequency == "ANNUAL") "/yr" else "/mo",
                                Modifier.padding(top = 2.dp),
                                color = Pf.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp), tint = Pf.Accent400)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier
            .background(if (selected) Pf.Accent else androidx.compose.ui.graphics.Color.Transparent, Radius.Pill)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (selected) androidx.compose.ui.graphics.Color.White else Pf.Text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}
