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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Receipt
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.today
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.inr

@Composable
private fun CategoryAvatar(category: String, kind: String, modifier: Modifier = Modifier) {
    val (icon, bgColor, tint) = when {
        kind == "INCOME" -> Triple(Icons.Default.ArrowDownward, Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
        kind == "TRANSFER" -> Triple(Icons.Default.SwapHoriz, Color(0xFF9C27B0).copy(alpha = 0.15f), Color(0xFFCE93D8))
        category.contains("Food", ignoreCase = true) || category.contains("Dining", ignoreCase = true) || category.contains("Snack", ignoreCase = true) || category.contains("Groceries", ignoreCase = true) -> Triple(Icons.Default.Restaurant, Color(0xFFFFA726).copy(alpha = 0.15f), Color(0xFFFFA726))
        category.contains("Shopping", ignoreCase = true) || category.contains("Clothes", ignoreCase = true) || category.contains("Electronic", ignoreCase = true) -> Triple(Icons.Default.ShoppingCart, Color(0xFF29B6F6).copy(alpha = 0.15f), Color(0xFF29B6F6))
        category.contains("Bill", ignoreCase = true) || category.contains("Electricity", ignoreCase = true) || category.contains("Recharge", ignoreCase = true) || category.contains("Wifi", ignoreCase = true) || category.contains("Utility", ignoreCase = true) -> Triple(Icons.Default.Bolt, Color(0xFFAB47BC).copy(alpha = 0.15f), Color(0xFFAB47BC))
        category.contains("Travel", ignoreCase = true) || category.contains("Fuel", ignoreCase = true) || category.contains("Cab", ignoreCase = true) || category.contains("Uber", ignoreCase = true) || category.contains("Transport", ignoreCase = true) -> Triple(Icons.Default.DirectionsCar, Color(0xFF5C6BC0).copy(alpha = 0.15f), Color(0xFF5C6BC0))
        category.contains("Health", ignoreCase = true) || category.contains("Med", ignoreCase = true) || category.contains("Doctor", ignoreCase = true) -> Triple(Icons.Default.LocalHospital, Color(0xFF26A69A).copy(alpha = 0.15f), Color(0xFF26A69A))
        category.contains("Invest", ignoreCase = true) || category.contains("SIP", ignoreCase = true) || category.contains("Mutual", ignoreCase = true) -> Triple(Icons.Default.TrendingUp, Color(0xFF66BB6A).copy(alpha = 0.15f), Color(0xFF66BB6A))
        category.contains("Entertainment", ignoreCase = true) || category.contains("Movie", ignoreCase = true) || category.contains("Netflix", ignoreCase = true) -> Triple(Icons.Default.PlayCircle, Color(0xFFEC407A).copy(alpha = 0.15f), Color(0xFFEC407A))
        category == "Needs Account" || category == "Uncategorised" -> Triple(Icons.Default.HelpOutline, Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444))
        else -> Triple(Icons.Default.Receipt, Color(0xFF7E57C2).copy(alpha = 0.15f), Color(0xFF7E57C2))
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(bgColor, Radius.Md),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = tint)
    }
}

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
            val groupedRows = rows.groupBy { it.date }
            groupedRows.forEach { (dateStr, dateTxns) ->
                val headerTitle = when (dateStr) {
                    today() -> "Today"
                    else -> prettyDate(dateStr)
                }
                val daySpent = dateTxns.filter { it.kind == "EXPENSE" }.sumOf { it.amount }
                val dayIncome = dateTxns.filter { it.kind == "INCOME" }.sumOf { it.amount }

                item(key = "header_$dateStr") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.s2, bottom = Space.s1),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            headerTitle,
                            color = Pf.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        val subText = buildString {
                            if (daySpent > 0.0) append("Spent ₹${inr(daySpent)}")
                            if (dayIncome > 0.0) {
                                if (isNotEmpty()) append(" · ")
                                append("+₹${inr(dayIncome)}")
                            }
                        }
                        if (subText.isNotEmpty()) {
                            Text(subText, color = Pf.Muted, fontSize = 11.sp)
                        }
                    }
                }

                items(dateTxns, key = { it.id }) { t ->
                    PfCard(
                        modifier = Modifier.clickable { vm.startEditTxn(t.id) },
                        padding = PaddingValues(horizontal = Space.s4, vertical = 10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s3)
                        ) {
                            CategoryAvatar(t.category, t.kind)

                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        t.note.ifEmpty { t.category },
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (t.source == "sms") OutlineTag("SMS")
                                }
                                Spacer(Modifier.height(2.dp))
                                Muted(
                                    "${t.category} · ${vm.txnAccountLabel(t)}",
                                    size = 11
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    when (t.kind) {
                                        "INCOME" -> "+${inr(t.amount)}"
                                        "TRANSFER" -> "↔ ${inr(t.amount)}"
                                        else -> "−${inr(t.amount)}"
                                    },
                                    color = when (t.kind) {
                                        "INCOME" -> Color(0xFF10B981)
                                        "TRANSFER" -> Pf.Muted
                                        else -> Color.White
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Muted(t.whenText, size = 11)
                            }
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
    var note by remember(txn.id) { mutableStateOf(txn.note) }
    var amountText by remember(txn.id) { mutableStateOf(if (txn.amount > 0) txn.amount.toLong().toString() else "") }
    var selectedAccountId by remember(txn.id) { mutableStateOf(txn.fromAccountId.ifEmpty { txn.toAccountId }) }
    var selectedCategory by remember(txn.id) { mutableStateOf(txn.category) }
    var selectedLoanId by remember(txn.id) { mutableStateOf(txn.loanId) }
    var selectedEntryId by remember(txn.id) { mutableStateOf(txn.entryId) }
    var borrowedFrom by remember(txn.id) { mutableStateOf(txn.borrowedFrom) }
    var returnDate by remember(txn.id) { mutableStateOf(txn.returnDate) }
    var isReturned by remember(txn.id) { mutableStateOf(txn.returned) }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = vm::cancelEditTxn) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Edit Transaction",
                        color = Pf.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Muted(txn.whenText)
                }
                IconButton(onClick = vm::cancelEditTxn, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Pf.Muted, modifier = Modifier.size(18.dp))
                }
            }

            PfField(
                value = note,
                onValueChange = { note = it },
                label = "Description",
                placeholder = txn.category
            )

            PfField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "Amount (₹)",
                numeric = true,
                placeholder = "Amount"
            )

            Column {
                val account = vm.accounts.firstOrNull { it.id == selectedAccountId }
                if (account == null && txn.cardId.isEmpty()) {
                    Text(
                        if (txn.accountTail.isNotBlank())
                            "Account not set — bank said A/c ••${txn.accountTail}"
                        else "Account not set",
                        color = Pf.Accent400, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Muted("Choose an account to record this transaction against.")
                    if (txn.accountTail.isNotBlank()) {
                        Spacer(Modifier.height(Space.s2))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.s2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SecondaryButton(
                                "Add A/c (••${txn.accountTail})",
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
                    value = account?.name.orEmpty().ifEmpty { "Select Account" },
                    options = vm.visibleAccounts.map { it.name },
                    onSelect = { name ->
                        selectedAccountId = vm.visibleAccounts.firstOrNull { it.name == name }?.id.orEmpty()
                    }
                )
            }

            Column {
                Muted("Category")
                PfSelect(
                    value = selectedCategory,
                    options = vm.categories,
                    onSelect = { selectedCategory = it }
                )
            }

            Column {
                Muted("Link to Commitment / Loan")
                val currentLinkText = when {
                    selectedLoanId.isNotEmpty() -> {
                        val l = vm.loans.firstOrNull { it.id == selectedLoanId }
                        if (l != null) {
                            "Loan: ${l.name} (₹${inr(l.monthlyEmi)})"
                        } else {
                            val b = vm.borrowedLentTxns.firstOrNull { it.id == selectedLoanId }
                            if (b != null) {
                                val role = if (b.kind == "INCOME" || b.kind == "REFUND") "Borrowed from ${b.borrowedFrom}" else "Lent to ${b.borrowedFrom}"
                                val outstanding = b.amount - b.returnedAmount
                                "Settle Debt: $role (Outstanding ₹${inr(outstanding)})"
                            } else "Linked Loan"
                        }
                    }
                    selectedEntryId.isNotEmpty() -> {
                        val e = vm.entries.firstOrNull { it.id == selectedEntryId }
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
                        selectedLoanId = pair?.first.orEmpty()
                        selectedEntryId = pair?.second.orEmpty()
                    }
                )
            }

            Column {
                Muted("Borrowed From / Lent To (Optional)")
                PfField(
                    value = borrowedFrom,
                    onValueChange = { borrowedFrom = it },
                    placeholder = "e.g. Wife, Friend name"
                )
                val chips = vm.profileNames.filter { it != vm.activeProfile }
                if (chips.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.s1),
                        horizontalArrangement = Arrangement.spacedBy(Space.s1)
                    ) {
                        chips.forEach { name ->
                            val selected = borrowedFrom == name
                            Text(
                                name,
                                modifier = Modifier
                                    .background(if (selected) Pf.Accent else Pf.Surface2, Radius.Pill)
                                    .clickable {
                                        borrowedFrom = if (selected) "" else name
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

            if (borrowedFrom.isNotEmpty()) {
                val context = LocalContext.current
                val calendar = Calendar.getInstance()
                
                if (returnDate.isNotEmpty()) {
                    val parts = returnDate.split("-")
                    if (parts.size == 3) {
                        calendar.set(Calendar.YEAR, parts[0].toIntOrNull() ?: calendar.get(Calendar.YEAR))
                        calendar.set(Calendar.MONTH, (parts[1].toIntOrNull() ?: 1) - 1)
                        calendar.set(Calendar.DAY_OF_MONTH, parts[2].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH))
                    }
                }

                val datePickerDialog = remember(txn.id, returnDate) {
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            returnDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
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
                        value = if (returnDate.isNotEmpty()) prettyDate(returnDate) else "",
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
                    
                    if (returnDate.isNotEmpty()) {
                        SecondaryButton(
                            text = "Clear",
                            onClick = { returnDate = "" }
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isReturned,
                        onCheckedChange = { isReturned = it },
                        colors = CheckboxDefaults.colors(checkedColor = Pf.Accent)
                    )
                    Text("Returned / Settled", color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Message source
            val message = vm.smsBodyFor(txn.id)
            if (message.isNotEmpty()) {
                Column(Modifier.padding(top = Space.s1)) {
                    Muted("Original Bank SMS")
                    SelectionContainer {
                        Text(
                            message,
                            Modifier.padding(top = 4.dp),
                            color = Pf.Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                SecondaryButton(
                    "Delete",
                    { vm.deleteTxn(txn.id); vm.cancelEditTxn() },
                    Modifier.weight(0.9f)
                )
                SecondaryButton(
                    "Cancel",
                    vm::cancelEditTxn,
                    Modifier.weight(0.9f)
                )
                PrimaryButton(
                    "Save",
                    {
                        val amt = amountText.toDoubleOrNull() ?: txn.amount
                        vm.saveTxnDetails(
                            txnId = txn.id,
                            note = note,
                            amount = amt,
                            accountId = selectedAccountId,
                            category = selectedCategory,
                            loanId = selectedLoanId,
                            entryId = selectedEntryId,
                            borrowedFrom = borrowedFrom,
                            returnDate = returnDate,
                            returned = isReturned
                        )
                    },
                    Modifier.weight(1.2f)
                )
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
