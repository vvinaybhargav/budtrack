package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.monthsToDate

private const val ALERT_PCT = 0.90f

@Composable
fun HomeScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6)
    ) {
        item { ScopeSwitch(vm) }
        item { QuickAdd(vm) }
        item { BalanceCard(vm) }
        item { AccountsSection(vm) }
        item { MonthStats(vm) }
        item { BudgetsSection(vm) }
        item { LoansSection(vm) }
        item { CardsSection(vm) }
        item { CommitmentsSection(vm) }
        item { AnnualSetAsidesSection(vm) }
    }
    ConfirmSheet(vm)
}

/**
 * Shown when a confirm needs an account. An expense asks where the money left
 * from, income where it landed, and a set-aside asks both — debit and credit —
 * because both sides are yours.
 */
@Composable
private fun ConfirmSheet(vm: FinTrackViewModel) {
    val pending = vm.pendingConfirm ?: return
    val accountName = { id: String -> vm.accounts.firstOrNull { it.id == id }?.name.orEmpty() }
    val options = vm.visibleAccounts.map { it.name }
    val idFor = { name: String -> vm.visibleAccounts.firstOrNull { it.name == name }?.id.orEmpty() }

    Dialog(onDismissRequest = vm::cancelConfirm) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            Text(
                when (pending.kind) {
                    "TRANSFER" -> "Move to set-aside"
                    "INCOME" -> "Confirm income"
                    else -> "Confirm payment"
                },
                color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
            )
            Text(
                "${pending.title} · ${inr(pending.amount)}",
                color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            if (pending.kind == "TRANSFER") {
                Muted("This money stays yours — it just moves between your accounts.")
            }

            if (pending.needsFrom) {
                Column {
                    Muted(if (pending.kind == "TRANSFER") "Debit from" else "Paid from")
                    PfSelect(
                        value = accountName(pending.fromAccountId),
                        options = options,
                        onSelect = { vm.setConfirmFrom(idFor(it)) }
                    )
                }
            }
            if (pending.needsTo) {
                Column {
                    Muted(if (pending.kind == "TRANSFER") "Credit to" else "Received in")
                    PfSelect(
                        value = accountName(pending.toAccountId),
                        options = options,
                        onSelect = { vm.setConfirmTo(idFor(it)) }
                    )
                }
            }
            if (pending.kind == "TRANSFER" &&
                pending.fromAccountId.isNotEmpty() &&
                pending.fromAccountId == pending.toAccountId
            ) {
                Text(
                    "Pick two different accounts.",
                    color = Pf.Accent400, fontSize = 13.sp
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                SecondaryButton("Cancel", vm::cancelConfirm, Modifier.weight(1f))
                PrimaryButton(
                    if (pending.kind == "TRANSFER") "Transfer" else "Confirm",
                    vm::commitConfirm,
                    Modifier.weight(1f),
                    enabled = pending.isReady
                )
            }
        }
    }
}

/**
 * Flips the whole screen — accounts, loans, cards, commitments, set-asides and
 * the month's figures — between your own side and the shared one, and sets
 * what a new entry defaults to. Joint is a view, not a separate sign-in.
 */
@Composable
private fun ScopeSwitch(vm: FinTrackViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Pf.Surface2, Radius.Pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ScopeTab(vm.activeProfile ?: "Personal", vm.bucketView == "PERSONAL", Modifier.weight(1f)) {
            vm.setScope(false)
        }
        ScopeTab("Joint", vm.bucketView == "JOINT", Modifier.weight(1f)) { vm.setScope(true) }
    }
}

@Composable
private fun ScopeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier
            .background(if (selected) Pf.Accent else Color.Transparent, Radius.Pill)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (selected) Color.White else Pf.Text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun QuickAdd(vm: FinTrackViewModel) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Pill)
                .border(1.dp, Pf.Hairline, Radius.Pill)
                .padding(horizontal = Space.s4, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = Pf.Muted)
            androidx.compose.foundation.text.BasicTextField(
                value = vm.homeQuickText,
                onValueChange = { vm.homeQuickText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Pf.Accent),
                decorationBox = { inner ->
                    if (vm.homeQuickText.isEmpty()) {
                        // It creates a recurring entry, not a one-off payment —
                        // saying "transaction" made people add ₹500/month by
                        // accident. Actual payments arrive from bank SMS.
                        Text("Add a monthly commitment… e.g. 22k EMI", color = Pf.Muted, fontSize = 14.sp)
                    }
                    inner()
                }
            )
            IconButton(
                onClick = vm::quickAddFromHome,
                modifier = Modifier
                    .size(30.dp)
                    .background(Pf.Accent, Radius.Pill)
            ) {
                Icon(Icons.Default.Send, "Add", Modifier.size(15.dp), tint = Color.White)
            }
        }
        if (vm.homeQuickConfirm.isNotEmpty()) {
            Muted(vm.homeQuickConfirm, Modifier.padding(top = 6.dp, start = Space.s1))
        }
    }
}

@Composable
private fun BalanceCard(vm: FinTrackViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Pf.Surface2, Pf.Bg)),
                Radius.Xl
            )
            .border(1.dp, Pf.Hairline, Radius.Xl)
            .padding(horizontal = Space.s4, vertical = Space.s6)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "TOTAL ACROSS ACCOUNTS",
                color = Pf.Muted,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp
            )
            IconButton(onClick = vm::toggleBalanceVisible, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (vm.balanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    "Toggle balance visibility",
                    Modifier.size(16.dp),
                    tint = Pf.Accent400
                )
            }
        }
        Text(
            if (vm.balanceHidden) "••••••" else inr(vm.totalBalance),
            Modifier.padding(top = 6.dp),
            color = Pf.Text,
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Muted("Across ${vm.scopedAccounts.size} accounts", Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AccountsSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Accounts", Modifier.padding(bottom = Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.scopedAccounts.forEach { a ->
                PfCard {
                    if (vm.editingAccountId == a.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PfField(value = vm.accountDraft.name, onValueChange = { vm.accountDraft = vm.accountDraft.copy(name = it) }, placeholder = "Account name")
                            PfField(value = vm.accountDraft.owner, onValueChange = { vm.accountDraft = vm.accountDraft.copy(owner = it) }, placeholder = "Owner")
                            PfField(value = vm.accountDraft.balanceText, onValueChange = { vm.accountDraft = vm.accountDraft.copy(balanceText = it) }, placeholder = "Balance", numeric = true)
                            // Lets a bank SMS land on this account instead of the default.
                            PfField(value = vm.accountDraft.numberTail, onValueChange = { vm.accountDraft = vm.accountDraft.copy(numberTail = it) }, placeholder = "Last 3-4 digits, as the bank SMS shows", numeric = true)
                            EditorActions({ vm.deleteAccount(a.id) }, vm::cancelEditAccount, vm::saveAccount)
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s3),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .background(Pf.Surface2, Radius.Sm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalance, null, Modifier.size(18.dp), tint = Pf.Accent400)
                                }
                                Column {
                                    Text(a.name, color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    // Shown so it's obvious at a glance which
                                    // account a bank alert will land on.
                                    Muted(
                                        if (a.numberTail.isNotBlank()) "${a.owner} · ••${a.numberTail}"
                                        else "${a.owner} · no digits set"
                                    )
                                }
                            }
                            Text(inr(vm.balanceOf(a)), color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            IconButton(onClick = { vm.startEditAccount(a) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "Edit account", Modifier.size(16.dp), tint = Pf.Accent400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStats(vm: FinTrackViewModel) {
    // Money that actually moved. These were the planned figures, so they showed
    // the same numbers whether or not anything had been recorded.
    val stats = listOf(
        "Received" to (inr(vm.actualIncome) to Pf.Accent400),
        "Spent" to (inr(vm.actualSpent) to Pf.Text),
        "Set aside" to (inr(vm.actualSaved) to Pf.Text),
        "Invested" to (inr(vm.actualInvested) to Pf.Text)
    )
    Column {
        SectionTitle("This month · ${vm.bucketLabel}", Modifier.padding(bottom = Space.s1))
        Muted(
            "Recorded on this side so far. Planned: ${inr(vm.plannedIncome)} in, " +
                "${inr(vm.plannedExpense)} out — expenses, set-asides and EMIs.",
            Modifier.padding(bottom = Space.s3)
        )
        PfCard(padding = PaddingValues(0.dp)) {
            stats.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (label, valueColor) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = Space.s4, vertical = Space.s3)
                        ) {
                            Text(label.uppercase(), color = Pf.Muted, fontSize = 11.sp, letterSpacing = 0.7.sp)
                            Text(
                                valueColor.first,
                                Modifier.padding(top = 2.dp),
                                color = valueColor.second,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Category budgets", Modifier.padding(bottom = Space.s3))
        PfCard(padding = PaddingValues(Space.s4)) {
            if (vm.budgets.isEmpty()) {
                Muted("No budgets set. Add one in Settings to track a category here.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                vm.budgets.forEach { (cat, limit) ->
                    val spend = vm.spendFor(cat)
                    val pct = safeFraction(spend, limit)
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Space.s2),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat, color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Muted("${inr(spend)} / ${inr(limit)}", size = 13)
                        }
                        ProgressBar(pct, if (pct >= ALERT_PCT) Pf.Accent else Pf.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoansSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Loans", Modifier.padding(bottom = Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.scopedLoans.forEach { l ->
                PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                    if (vm.editingLoanId == l.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PfField(value = vm.loanDraft.name, onValueChange = { vm.loanDraft = vm.loanDraft.copy(name = it) }, placeholder = "Loan name")
                            PfSelect(value = vm.loanDraft.person, options = vm.draftPersonOptions, onSelect = { vm.loanDraft = vm.loanDraft.copy(person = it) })
                            PfField(value = vm.loanDraft.emiText, onValueChange = { vm.loanDraft = vm.loanDraft.copy(emiText = it) }, placeholder = "Monthly EMI", numeric = true)
                            PfField(value = vm.loanDraft.totalMonthsText, onValueChange = { vm.loanDraft = vm.loanDraft.copy(totalMonthsText = it) }, placeholder = "Total months (tenure)", numeric = true)
                            PfField(value = vm.loanDraft.remainingMonthsText, onValueChange = { vm.loanDraft = vm.loanDraft.copy(remainingMonthsText = it) }, placeholder = "Months remaining", numeric = true)
                            EditorActions({ vm.deleteLoan(l.id) }, vm::cancelEditLoan, vm::saveLoan)
                        }
                    } else {
                        Column(Modifier.clickable { vm.toggleLoanDetail(l.id) }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Same shape as the set-aside rows: name, then
                                // the money on the muted line, then the tag.
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        l.name,
                                        color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Muted(
                                        "${inr(l.monthlyEmi)}/mo · ${l.remainingMonths} of " +
                                            "${l.totalMonths} months left",
                                        Modifier.padding(top = 2.dp, bottom = 6.dp)
                                    )
                                    OutlineTag("Loan")
                                }
                                // No account prompt — the loan already knows where the EMI comes from.
                                if (vm.isLoanConfirmed(l.id)) {
                                    SecondaryButton("Paid", { vm.confirmLoan(l) })
                                } else {
                                    PrimaryButton("Pay EMI", { vm.confirmLoan(l) })
                                }
                                IconButton(onClick = { vm.startEditLoan(l) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, "Edit loan", Modifier.size(16.dp), tint = Pf.Accent400)
                                }
                            }
                            if (vm.expandedLoan == l.id) {
                                val paid = l.totalMonths - l.remainingMonths
                                Column(Modifier.padding(top = Space.s3)) {
                                    Muted("$paid of ${l.totalMonths} months paid", Modifier.padding(bottom = Space.s2))
                                    ProgressBar(safeFraction(paid, l.totalMonths), Pf.Text, height = 6)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Space.s2),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Muted("Ends ${monthsToDate(l.remainingMonths)}")
                                        Text(
                                            "${inr(l.monthlyEmi * l.remainingMonths)} left",
                                            color = Pf.Text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
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
}

@Composable
private fun CardsSection(vm: FinTrackViewModel) {
    if (vm.scopedCards.isEmpty()) return
    Column {
        SectionTitle("Credit cards", Modifier.padding(bottom = Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.scopedCards.forEach { c ->
                PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                    if (vm.editingCardId == c.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PfField(value = vm.cardDraft.name, onValueChange = { vm.cardDraft = vm.cardDraft.copy(name = it) }, placeholder = "Card name")
                            PfField(value = vm.cardDraft.owner, onValueChange = { vm.cardDraft = vm.cardDraft.copy(owner = it) }, placeholder = "Owner")
                            PfField(value = vm.cardDraft.limitText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(limitText = it) }, placeholder = "Credit limit", numeric = true)
                            PfField(value = vm.cardDraft.balanceText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(balanceText = it) }, placeholder = "Current balance", numeric = true)
                            PfField(value = vm.cardDraft.minDueText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(minDueText = it) }, placeholder = "Minimum due", numeric = true)
                            PfField(value = vm.cardDraft.due, onValueChange = { vm.cardDraft = vm.cardDraft.copy(due = it) }, placeholder = "Due date")
                            // Lets a card spend in a bank SMS find this card.
                            PfField(value = vm.cardDraft.numberTail, onValueChange = { vm.cardDraft = vm.cardDraft.copy(numberTail = it) }, placeholder = "Last 3-4 digits of the card", numeric = true)
                            EditorActions({ vm.deleteCard(c.id) }, vm::cancelEditCard, vm::saveCard)
                        }
                    } else {
                        val pct = safeFraction(c.balance, c.limit)
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Space.s2),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name,
                                        color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Muted(
                                        "${inr(c.balance)} of ${inr(c.limit)} · due ${c.due}",
                                        Modifier.padding(top = 2.dp, bottom = 6.dp)
                                    )
                                    OutlineTag("Card")
                                }
                                IconButton(onClick = { vm.startEditCard(c) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, "Edit card", Modifier.size(16.dp), tint = Pf.Accent400)
                                }
                            }
                            ProgressBar(pct, if (pct >= ALERT_PCT) Pf.Accent else Pf.Text, height = 6)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Space.s3),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Muted("Min due ${inr(c.minDue)}")
                                // Enabled when paid too — tapping again undoes it.
                                SecondaryButton(
                                    if (c.paid) "Paid" else "Pay bill",
                                    { vm.requestCardPayment(c) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentsSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Recurring · this month", Modifier.padding(bottom = Space.s1))
        Muted(
            "Paid every month. Confirm each once it has actually gone through.",
            Modifier.padding(bottom = Space.s3)
        )
        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s2)) {
            vm.commitments.forEach { e ->
                val done = vm.isConfirmed(e.id)
                val kind = vm.commitmentKind(e)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Entries are edited from here now that the Transactions
                    // screen shows only recorded movements.
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { vm.openEditEntry(e) }
                    ) {
                        Text(e.category, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Muted("${e.person} · ${inr(e.monthly)}/mo", Modifier.padding(top = 2.dp, bottom = 6.dp))
                        when (kind) {
                            "Investment" -> Tag(kind, Pf.Accent100, Pf.Accent800)
                            "Savings" -> Tag(kind, Pf.Accent2_100, Pf.Accent2_800)
                            else -> Tag(kind, Pf.Neutral100, Pf.Neutral800)
                        }
                    }
                    if (done) {
                        SecondaryButton("Confirmed", { vm.requestConfirm(e) })
                    } else {
                        PrimaryButton("Confirm", { vm.requestConfirm(e) })
                    }
                    IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = Pf.Accent400)
                    }
                }
                Hairline()
            }
        }
    }
}

/**
 * Annual commitments, each shown at amount / 12. Confirming one is a self
 * transfer, not a spend: the money leaves the spending account and lands in a
 * set-aside account, so the yearly bill is already funded when it arrives.
 */
@Composable
private fun AnnualSetAsidesSection(vm: FinTrackViewModel) {
    val items = vm.annualSetAsides
    if (items.isEmpty()) return
    Column {
        SectionTitle("Set aside · this month", Modifier.padding(bottom = Space.s1))
        Muted(
            "Repeating commitments split across the months between payments. " +
                "Confirming moves the money to your set-aside account — it isn't spent.",
            Modifier.padding(bottom = Space.s3)
        )
        PfCard(padding = PaddingValues(Space.s4)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Muted("Needed each month")
                    Text(
                        inr(vm.annualSetAsideMonthly),
                        color = Pf.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
                Muted("${inr(vm.annualSetAsideDone)} done", size = 13)
            }
            ProgressBar(
                if (vm.annualSetAsideMonthly > 0)
                    (vm.annualSetAsideDone / vm.annualSetAsideMonthly).toFloat().coerceAtMost(1f)
                else 0f,
                Pf.Accent2, height = 6
            )
            Column(Modifier.padding(top = Space.s2)) {
                items.forEach { e ->
                    val done = vm.isConfirmed(e.id)
                    Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.s3),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { vm.openEditEntry(e) }
                        ) {
                            Text(
                                e.note.ifEmpty { e.category },
                                color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            Muted(
                                "${inr(e.monthly)}/mo · ${inr(e.amount)} every " +
                                    if (e.everyMonths == 12) "year" else "${e.everyMonths} months",
                                Modifier.padding(top = 2.dp, bottom = 6.dp)
                            )
                            Tag("Set aside", Pf.Accent2_100, Pf.Accent2_800)
                        }
                        if (done) SecondaryButton("Set aside", { vm.requestConfirm(e) })
                        else PrimaryButton("Set aside", { vm.requestConfirm(e) })
                        IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = Pf.Accent400)
                        }
                    }
                }
            }
        }
    }
}
