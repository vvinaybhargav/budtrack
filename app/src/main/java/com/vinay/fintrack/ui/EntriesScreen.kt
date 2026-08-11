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
import com.vinay.fintrack.data.prettyDate

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
                placeholder = "Search payee, category or reference…"
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
                vm.txnChips.forEach { c ->
                    Chip(c, vm.entriesCategoryFilter == c, onClick = { vm.setCategoryFilter(c) })
                }
            }
        }

        // Only recorded movements — confirmed commitments and imported bank
        // alerts. The recurring plan lives on Home, not here.
        val rows = vm.filteredTxns
        if (rows.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s6),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Nothing recorded yet.",
                        color = Pf.Muted,
                        textAlign = TextAlign.Center
                    )
                    Muted(
                        "Confirm a commitment on Home, or turn on bank SMS in Settings.",
                        Modifier.padding(top = Space.s2)
                    )
                }
            }
        } else {
            items(rows, key = { it.id }) { t ->
                PfCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                Text(
                                    t.note.ifEmpty { t.category },
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (t.source == "sms") OutlineTag("SMS")
                            }
                            Muted(
                                "${prettyDate(t.date)} · ${t.category}",
                                Modifier.padding(top = 2.dp)
                            )
                            Muted(vm.txnAccountLabel(t))
                            if (t.ref.isNotEmpty()) Muted("Ref ${t.ref}")
                        }
                        Text(
                            when (t.kind) {
                                "INCOME" -> "+${inr(t.amount)}"
                                "TRANSFER" -> "↔ ${inr(t.amount)}"
                                else -> "−${inr(t.amount)}"
                            },
                            color = when (t.kind) {
                                "INCOME" -> Pf.Accent2
                                "TRANSFER" -> Pf.Muted
                                else -> Pf.Text
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(onClick = { vm.deleteTxn(t.id) }, modifier = Modifier.size(36.dp)) {
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
