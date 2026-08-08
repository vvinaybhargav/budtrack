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
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.monthsToDate
import com.vinay.fintrack.data.Seed

private const val ALERT_PCT = 0.90f

@Composable
fun HomeScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6)
    ) {
        item { QuickAdd(vm) }
        item { BalanceCard(vm) }
        item { AccountsSection(vm) }
        item { MonthStats(vm) }
        item { BudgetsSection(vm) }
        item { LoansSection(vm) }
        item { CardsSection(vm) }
        item { CommitmentsSection(vm) }
    }
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
                        Text("Add a transaction… e.g. 22k EMI", color = Pf.Muted, fontSize = 14.sp)
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
        Muted("Across ${vm.visibleAccounts.size} accounts", Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AccountsSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Accounts", Modifier.padding(bottom = Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.visibleAccounts.forEach { a ->
                PfCard {
                    if (vm.editingAccountId == a.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PfField(value = vm.accountDraft.name, onValueChange = { vm.accountDraft = vm.accountDraft.copy(name = it) }, placeholder = "Account name")
                            PfField(value = vm.accountDraft.owner, onValueChange = { vm.accountDraft = vm.accountDraft.copy(owner = it) }, placeholder = "Owner")
                            PfField(value = vm.accountDraft.balanceText, onValueChange = { vm.accountDraft = vm.accountDraft.copy(balanceText = it) }, placeholder = "Balance", numeric = true)
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
                                    Muted(a.owner)
                                }
                            }
                            Text(inr(a.balance), color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
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
    val stats = listOf(
        "Income" to (inr(vm.monthlyIncome) to Pf.Accent400),
        "Expenses" to (inr(vm.monthlyExpense) to Pf.Text),
        "Savings" to (inr(vm.monthlySavings) to Pf.Text),
        "Investment" to (inr(vm.monthlyInvestment) to Pf.Text)
    )
    Column {
        SectionTitle("This month", Modifier.padding(bottom = Space.s3))
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
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                Seed.budgets.forEach { (cat, limit) ->
                    val spend = vm.spendFor(cat)
                    val pct = (spend / limit).toFloat().coerceAtMost(1f)
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
            vm.visibleLoans.forEach { l ->
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
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                                    ) {
                                        Text(l.name, color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        OutlineTag("Loan")
                                    }
                                    Muted(l.person, Modifier.padding(top = 2.dp))
                                }
                                Text("${inr(l.monthlyEmi)}/mo", color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                IconButton(onClick = { vm.startEditLoan(l) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, "Edit loan", Modifier.size(16.dp), tint = Pf.Accent400)
                                }
                            }
                            if (vm.expandedLoan == l.id) {
                                val paid = l.totalMonths - l.remainingMonths
                                Column(Modifier.padding(top = Space.s3)) {
                                    Muted("$paid of ${l.totalMonths} months paid", Modifier.padding(bottom = Space.s2))
                                    ProgressBar(paid.toFloat() / l.totalMonths, Pf.Text, height = 6)
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
    if (vm.visibleCards.isEmpty()) return
    Column {
        SectionTitle("Credit cards", Modifier.padding(bottom = Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.visibleCards.forEach { c ->
                PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                    if (vm.editingCardId == c.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PfField(value = vm.cardDraft.name, onValueChange = { vm.cardDraft = vm.cardDraft.copy(name = it) }, placeholder = "Card name")
                            PfField(value = vm.cardDraft.owner, onValueChange = { vm.cardDraft = vm.cardDraft.copy(owner = it) }, placeholder = "Owner")
                            PfField(value = vm.cardDraft.limitText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(limitText = it) }, placeholder = "Credit limit", numeric = true)
                            PfField(value = vm.cardDraft.balanceText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(balanceText = it) }, placeholder = "Current balance", numeric = true)
                            PfField(value = vm.cardDraft.minDueText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(minDueText = it) }, placeholder = "Minimum due", numeric = true)
                            PfField(value = vm.cardDraft.due, onValueChange = { vm.cardDraft = vm.cardDraft.copy(due = it) }, placeholder = "Due date")
                            EditorActions({ vm.deleteCard(c.id) }, vm::cancelEditCard, vm::saveCard)
                        }
                    } else {
                        val pct = (c.balance / c.limit).toFloat().coerceAtMost(1f)
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Space.s2),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Muted("${c.owner} · Due ${c.due}")
                                }
                                Text(
                                    "${inr(c.balance)} / ${inr(c.limit)}",
                                    color = Pf.Text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
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
                                SecondaryButton(
                                    if (c.paid) "Paid" else "Pay bill",
                                    { vm.payCard(c.id) },
                                    enabled = !c.paid
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
        SectionTitle("Confirm this month", Modifier.padding(bottom = Space.s1))
        Muted(
            "Recurring savings & investments — log them once they've gone through.",
            Modifier.padding(bottom = Space.s3)
        )
        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s2)) {
            vm.commitments.forEach { e ->
                val done = e.id in vm.confirmed
                val kind = vm.commitmentKind(e)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.category, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Muted("${e.person} · ${inr(e.monthly)}/mo", Modifier.padding(top = 2.dp, bottom = 6.dp))
                        when (kind) {
                            "Investment" -> Tag(kind, Pf.Accent100, Pf.Accent800)
                            "Savings" -> Tag(kind, Pf.Accent2_100, Pf.Accent2_800)
                            else -> Tag(kind, Pf.Neutral100, Pf.Neutral800)
                        }
                    }
                    if (done) {
                        SecondaryButton("Confirmed", { vm.toggleCommitment(e.id) })
                    } else {
                        PrimaryButton("Confirm", { vm.toggleCommitment(e.id) })
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
