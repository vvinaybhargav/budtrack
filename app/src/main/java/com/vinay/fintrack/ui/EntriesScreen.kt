package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import com.vinay.fintrack.data.prettyDate
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

        // The same switch as Home — one piece of state, so flipping it either
        // place keeps both screens on the same side.
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface2, Radius.Pill)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BucketTab(vm.activeProfile ?: "Personal", vm.bucketView == "PERSONAL", Modifier.weight(1f)) {
                    vm.setScope(false)
                }
                BucketTab("Joint", vm.bucketView == "JOINT", Modifier.weight(1f)) {
                    vm.setScope(true)
                }
            }
        }

        // Unfinished work, above the list rather than buried in it: a row with
        // no account has moved no balance, so the figures are wrong until it is
        // set.
        val needAccount = vm.txnsNeedingAccount.size
        val unsorted = vm.uncategorisedTxns.size
        if (needAccount > 0 || unsorted > 0 || vm.sortMessage.isNotEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Surface2, Radius.Lg)
                        .padding(Space.s3)
                ) {
                    if (needAccount > 0) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.entriesCategoryFilter = "Needs Account"
                                }
                                .padding(vertical = Space.s1)
                        ) {
                            Text(
                                "$needAccount need an account",
                                color = Pf.Accent400, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            Muted("Their balances haven't moved. Tap one to set it.")
                        }
                    }
                    if (unsorted > 0) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.entriesCategoryFilter = "Uncategorised"
                                }
                                .padding(vertical = Space.s1)
                        ) {
                            Text(
                                "$unsorted not categorised",
                                Modifier.padding(top = if (needAccount > 0) Space.s2 else 0.dp),
                                color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            Muted("File one and that payee stays filed.")
                        }
                        Row(Modifier.padding(top = Space.s2)) {
                            SecondaryButton(
                                if (vm.sortingCategories) "Sorting…" else "Sort with AI",
                                vm::sortCategoriesWithAi,
                                enabled = !vm.sortingCategories
                            )
                        }
                    }
                    if (vm.sortMessage.isNotEmpty()) {
                        Muted(vm.sortMessage, Modifier.padding(top = Space.s2))
                    }
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
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                PfField(
                    label = "Min Amount (₹)",
                    value = vm.amountFilterMinText,
                    onValueChange = { vm.amountFilterMinText = it },
                    placeholder = "Min ₹",
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                PfField(
                    label = "Max Amount (₹)",
                    value = vm.amountFilterMaxText,
                    onValueChange = { vm.amountFilterMaxText = it },
                    placeholder = "Max ₹",
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Chip("All", vm.entriesCategoryFilter == null, onClick = { vm.setCategoryFilter(null) })
                if (needAccount > 0) {
                    Chip("Needs Account", vm.entriesCategoryFilter == "Needs Account", onClick = { vm.setCategoryFilter("Needs Account") })
                }
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
                    val elsewhere = vm.otherBucketCount
                    val filtered = vm.entriesSearch.isNotEmpty() || vm.entriesCategoryFilter != null
                    Text(
                        when {
                            elsewhere > 0 -> "Nothing here."
                            filtered -> "Nothing matches."
                            else -> "Nothing recorded yet."
                        },
                        color = Pf.Muted,
                        textAlign = TextAlign.Center
                    )
                    // A transaction on the other side used to look like one that
                    // was never recorded at all.
                    Muted(
                        when {
                            elsewhere > 0 ->
                                "$elsewhere transaction(s) are under " +
                                    if (vm.bucketView == "JOINT") "Personal." else "Joint."
                            filtered -> "Clear the search or category filter to see everything."
                            else -> "Confirm a commitment on Home, or turn on bank SMS in Settings."
                        },
                        Modifier.padding(top = Space.s2)
                    )
                    // Otherwise a payment on the other person's account looks lost.
                    if (vm.otherProfileTxnCount > 0) {
                        Muted(
                            "${vm.otherProfileTxnCount} more are on another profile's own " +
                                "account and show on their phone.",
                            Modifier.padding(top = Space.s1)
                        )
                    }
                }
            }
        } else {
            items(rows, key = { it.id }) { t ->
                PfCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { vm.startEditTxn(t.id) }
                        ) {
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
                                "${t.whenText} · ${t.category}",
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
    EditTxnSheet(vm)
}

/**
 * Moving a transaction to the right account. This also moves it between the
 * Joint and Personal buckets, since a transaction takes its side from the
 * account it went through.
 */
@Composable
private fun EditTxnSheet(vm: FinTrackViewModel) {
    val txn = vm.editingTxn ?: return
    Dialog(onDismissRequest = vm::cancelEditTxn) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            Text(
                txn.note.ifEmpty { txn.category },
                color = Pf.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
            )
            Muted(txn.whenText)

            DebouncedField(
                value = txn.note,
                onSettled = vm::setTxnNote,
                label = "Description (optional)",
                placeholder = txn.category,
                allowBlank = true
            )
            // Writing one files the row by itself, so say so rather than
            // leaving a second button to press.
            Muted(
                when {
                    vm.categorisingTxnId == txn.id -> "Finding a category…"
                    vm.categoriseNote.isNotEmpty() -> vm.categoriseNote
                    txn.category == "Uncategorised" ->
                        "Describe it and it will be filed for you."
                    else -> "Leave it, or describe it in your own words."
                }
            )
            DebouncedField(
                value = if (txn.amount > 0) txn.amount.toLong().toString() else "",
                onSettled = vm::setTxnAmount,
                label = "Amount",
                numeric = true
            )

            Column {
                val account = vm.accounts.firstOrNull {
                    it.id == txn.fromAccountId.ifEmpty { txn.toAccountId }
                }
                // An imported row with no account is unfinished, not merely
                // untidy: nothing has moved off any balance until it is set.
                if (account == null && txn.cardId.isEmpty()) {
                    Text(
                        if (txn.accountTail.isNotBlank())
                            "Account not set — the bank said A/c ••${txn.accountTail}"
                        else "Account not set",
                        color = Pf.Accent400, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Muted(
                        if (txn.accountTail.isNotBlank())
                            "Choose it once and those digits are saved against it, so " +
                                "later messages match on their own."
                        else "No balance moves until this is set."
                    )
                    if (txn.accountTail.isNotBlank()) {
                        Spacer(Modifier.height(Space.s2))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.s2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SecondaryButton(
                                "Add Account (••${txn.accountTail})",
                                { vm.navigateToCreateAccountFromTail(txn.accountTail) },
                                Modifier.weight(1f)
                            )
                            SecondaryButton(
                                "Add Card (••${txn.accountTail})",
                                { vm.navigateToCreateCardFromTail(txn.accountTail) },
                                Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Muted("Account")
                }
                PfSelect(
                    value = account?.name.orEmpty(),
                    // Only yours and the joint ones: listing every account named
                    // the other profile's private accounts and let you move
                    // money onto them.
                    options = vm.visibleAccounts.map { it.name },
                    onSelect = { name ->
                        vm.setTxnAccount(vm.visibleAccounts.firstOrNull { it.name == name }?.id.orEmpty())
                    }
                )
            }
            Column {
                Muted("Category")
                PfSelect(
                    value = txn.category,
                    options = vm.categories,
                    onSelect = vm::setTxnCategory
                )
            }
            Column {
                Muted("Link to Commitment / Loan")
                val currentLinkText = when {
                    txn.loanId.isNotEmpty() -> {
                        val l = vm.loans.firstOrNull { it.id == txn.loanId }
                        if (l != null) {
                            "Loan: ${l.name} (₹${inr(l.monthlyEmi)})"
                        } else {
                            val b = vm.borrowedLentTxns.firstOrNull { it.id == txn.loanId }
                            if (b != null) {
                                val role = if (b.kind == "INCOME" || b.kind == "REFUND") "Borrowed from ${b.borrowedFrom}" else "Lent to ${b.borrowedFrom}"
                                val outstanding = b.amount - b.returnedAmount
                                "Settle Debt: $role (Outstanding ₹${inr(outstanding)})"
                            } else "Linked Loan"
                        }
                    }
                    txn.entryId.isNotEmpty() -> {
                        val e = vm.entries.firstOrNull { it.id == txn.entryId }
                        if (e != null) {
                            val labelPrefix = if (e.isSetAside) "Set aside" else "Recurring"
                            "$labelPrefix: ${e.category} (₹${inr(e.monthly)})"
                        } else "Linked Recurring"
                    }
                    else -> "None / Unlinked"
                }

                val linkOptionsMap = remember(txn.id, vm.loans, vm.entries, vm.borrowedLentTxns) {
                    val m = mutableMapOf<String, Pair<String?, String?>>()
                    m["None / Unlinked"] = Pair(null, null)
                    
                    vm.loans.filter { !vm.isLoanConfirmed(it.id) || it.id == txn.loanId }.forEach { l ->
                        m["Loan: ${l.name} (₹${inr(l.monthlyEmi)})"] = Pair(l.id, null)
                    }
                    vm.entries.filter { !vm.isConfirmed(it.id) || it.id == txn.entryId }.forEach { e ->
                        val labelPrefix = if (e.isSetAside) "Set aside" else "Recurring"
                        m["$labelPrefix: ${e.category} (₹${inr(e.monthly)})"] = Pair(null, e.id)
                    }
                    vm.borrowedLentTxns.filter { it.id != txn.id }.forEach { b ->
                        val outstanding = b.amount - b.returnedAmount
                        val role = if (b.kind == "INCOME" || b.kind == "REFUND") "Borrowed from ${b.borrowedFrom}" else "Lent to ${b.borrowedFrom}"
                        m["Settle Debt: $role (Outstanding ₹${inr(outstanding)})"] = Pair(b.id, null)
                    }
                    m
                }

                PfSelect(
                    value = currentLinkText,
                    options = linkOptionsMap.keys.toList(),
                    onSelect = { selectedLabel ->
                        val pair = linkOptionsMap[selectedLabel]
                        vm.linkTxnToCommitment(txn.id, pair?.first, pair?.second)
                    }
                )
            }

            Column {
                Muted("Borrowed From / Lent To (Optional)")
                PfField(
                    value = txn.borrowedFrom,
                    onValueChange = { vm.setTxnBorrowedFrom(txn.id, it) },
                    placeholder = "e.g. Wife, Friend name"
                )
                val chips = vm.profileNames.filter { it != vm.activeProfile }
                if (chips.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.s1),
                        horizontalArrangement = Arrangement.spacedBy(Space.s1)
                    ) {
                        chips.forEach { name ->
                            val selected = txn.borrowedFrom == name
                            Text(
                                name,
                                modifier = Modifier
                                    .background(if (selected) Pf.Accent else Pf.Surface2, Radius.Pill)
                                    .clickable {
                                        vm.setTxnBorrowedFrom(txn.id, if (selected) "" else name)
                                    }
                                    .padding(horizontal = Space.s2, vertical = 4.dp),
                                color = if (selected) Color.White else Pf.Text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (txn.borrowedFrom.isNotEmpty()) {
                val context = LocalContext.current
                val calendar = Calendar.getInstance()
                
                if (txn.returnDate.isNotEmpty()) {
                    val parts = txn.returnDate.split("-")
                    if (parts.size == 3) {
                        calendar.set(Calendar.YEAR, parts[0].toIntOrNull() ?: calendar.get(Calendar.YEAR))
                        calendar.set(Calendar.MONTH, (parts[1].toIntOrNull() ?: 1) - 1)
                        calendar.set(Calendar.DAY_OF_MONTH, parts[2].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH))
                    }
                }

                val datePickerDialog = remember(txn.id, txn.returnDate) {
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val formatted = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                            vm.setTxnReturnDate(txn.id, formatted)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PfField(
                        label = "Target Return Date (Optional)",
                        value = if (txn.returnDate.isNotEmpty()) prettyDate(txn.returnDate) else "",
                        onValueChange = { /* read only */ },
                        placeholder = "Select date...",
                        trailingIcon = {
                            SecondaryButton(
                                text = "Pick Date",
                                onClick = { datePickerDialog.show() }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (txn.returnDate.isNotEmpty()) {
                        SecondaryButton(
                            text = "Clear",
                            onClick = { vm.setTxnReturnDate(txn.id, "") }
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = txn.returned,
                        onCheckedChange = { vm.setTxnReturned(txn.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = Pf.Accent)
                    )
                    Text("Returned / Settled", color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Muted("An account owned by you puts this under Personal; a joint account puts it under Joint.")

            // What the bank actually said, for an imported one. Kept on this
            // phone only — it is here so a misread can be checked against the
            // original, not so it can travel.
            val message = vm.smsBodyFor(txn.id)
            if (message.isNotEmpty()) {
                Column(Modifier.padding(top = Space.s2)) {
                    Muted("The message this came from")
                    SelectionContainer {
                        Text(
                            message,
                            Modifier.padding(top = 4.dp),
                            color = Pf.Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                SecondaryButton("Delete", {
                    vm.deleteTxn(txn.id); vm.cancelEditTxn()
                }, Modifier.weight(1f))
                PrimaryButton("Done", vm::cancelEditTxn, Modifier.weight(1f))
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
