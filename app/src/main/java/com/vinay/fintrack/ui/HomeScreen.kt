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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.monthsToDate
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.today

private const val ALERT_PCT = 0.90f

@Composable
fun HomeScreen(vm: FinTrackViewModel) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6)
    ) {
        item { ScopeSwitch(vm) }
        item { CardStatementAlert(vm) }
        item { BalanceCard(vm) }
        item { AccountsSection(vm) }
        item { MonthPlan(vm) }
        item { MonthStats(vm) }
        item { BudgetsSection(vm) }
        item { LoansSection(vm) }
        item { CardsSection(vm) }
        item { CommitmentsSection(vm) }
        item { AnnualSetAsidesSection(vm) }
        item { OutlookSection(vm) }
    }
    ConfirmSheet(vm)
    CardSettleSheet(vm)
}

/**
 * Shown when a confirm needs an account. An expense asks where the money left
 * from, income where it landed, and a set-aside asks both — debit and credit —
 * because both sides are yours.
 */
@Composable
private fun ConfirmSheet(vm: FinTrackViewModel) {
    val pending = vm.pendingConfirm ?: return
    // Labelled with the owner: "SBI Savings · Me" beats "SBI Savings" when both
    // of you bank at the same place and the transfer is between profiles.
    val label = { id: String ->
        vm.accounts.firstOrNull { it.id == id }?.let { "${it.name} · ${it.person}" }.orEmpty()
    }
    val options = vm.transferAccounts.map { "${it.name} · ${it.person}" }
    val idFor = { text: String ->
        vm.transferAccounts.firstOrNull { "${it.name} · ${it.person}" == text }?.id.orEmpty()
    }

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
                pending.title,
                color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            // Editable: a set-aside can be part-paid, and the amount left is a
            // suggestion rather than the only figure allowed.
            PfField(
                label = "Amount (₹)",
                value = pending.amountText,
                onValueChange = vm::setConfirmAmount,
                numeric = true
            )
            if (pending.kind == "TRANSFER") {
                Muted("Stays yours — it moves between accounts rather than being spent.")
            }

            if (pending.needsFrom) {
                Column {
                    Muted(if (pending.kind == "TRANSFER") "Debit from" else "Paid from")
                    PfSelect(
                        value = label(pending.fromAccountId),
                        options = options,
                        onSelect = { vm.setConfirmFrom(idFor(it)) }
                    )
                }
            }
            if (pending.needsTo) {
                Column {
                    Muted(if (pending.kind == "TRANSFER") "Credit to" else "Received in")
                    PfSelect(
                        value = label(pending.toAccountId),
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

@Composable
private fun CardSettleSheet(vm: FinTrackViewModel) {
    val cardId = vm.settlingCardId ?: return
    val card = vm.cards.firstOrNull { it.id == cardId } ?: return

    Dialog(onDismissRequest = vm::cancelSettleCard) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            Text(
                "Settle Credit Card",
                color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Paying off ${card.name} (${card.owner})",
                color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            
            PfField(
                label = "Amount to Settle (₹)",
                value = vm.settleAmountDraft,
                onValueChange = { vm.settleAmountDraft = it },
                numeric = true
            )
            
            Column {
                Muted("Paid from Account")
                PfSelect(
                    value = vm.settleAccountNameDraft,
                    options = vm.visibleAccounts.map { it.name },
                    onSelect = { vm.settleAccountNameDraft = it }
                )
            }
            
            Muted("Settle payment creates an expense transaction from the selected account and reduces the card balance.")
            
            Row(
                Modifier.fillMaxWidth().padding(top = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                SecondaryButton("Cancel", vm::cancelSettleCard, Modifier.weight(1f))
                PrimaryButton(
                    "Settle",
                    vm::confirmSettleCard,
                    Modifier.weight(1f),
                    enabled = (vm.settleAmountDraft.toDoubleOrNull() ?: 0.0) > 0.0 && vm.settleAccountNameDraft.isNotBlank()
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
private fun CardStatementAlert(vm: FinTrackViewModel) {
    val todayStr = today()
    val cardsNeedingInput = vm.cards.filter { c ->
        c.balance > 0.0 && c.statementAmount == 0.0 && !c.paid && 
        (todayStr.substring(8, 10).toIntOrNull() ?: 1).let { day ->
            val dueDayNum = c.dueDate.substringAfterLast("-").toIntOrNull() ?: 11
            day >= c.statementDay || day < dueDayNum
        }
    }

    if (cardsNeedingInput.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Space.s2),
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            cardsNeedingInput.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Accent100, Radius.Md)
                        .border(1.dp, Pf.Accent, Radius.Md)
                        .clickable { vm.startEditCard(c) }
                        .padding(Space.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Statement generated for ${c.name}",
                            color = Pf.Accent800, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Muted(
                            "Outstanding balance is ${inr(c.balance)}. Tap to enter statement amount actually due.",
                            size = 11
                        )
                    }
                    Tag("Billed", Pf.Accent, Color.White)
                }
            }
        }
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

// The quick-add bar lived here: a text box that guessed at what you meant and
// silently produced a monthly commitment. The Chat tab does it properly.

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
                            PfField(label = "Account name", value = vm.accountDraft.name, onValueChange = { vm.accountDraft = vm.accountDraft.copy(name = it) }, placeholder = "Account name")
                            // A picker, not free text: this is the profile the
                            // account belongs to, so it decides which side the
                            // account shows under and where its bank messages go.
                            PfSelect(
                                label = "Belongs to",
                                value = vm.accountDraft.owner,
                                options = vm.ownerOptions,
                                onSelect = { vm.accountDraft = vm.accountDraft.copy(owner = it) }
                            )
                            PfField(label = "Balance (₹)", value = vm.accountDraft.balanceText, onValueChange = { vm.accountDraft = vm.accountDraft.copy(balanceText = it) }, placeholder = "Balance", numeric = true)
                            // Lets a bank SMS land on this account instead of the default.
                            PfField(label = "Last 3-4 digits (for SMS matching)", value = vm.accountDraft.numberTail, onValueChange = { vm.accountDraft = vm.accountDraft.copy(numberTail = it) }, placeholder = "Last 3-4 digits, as the bank SMS shows", numeric = true)
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

/**
 * What is left once the month has been paid for.
 *
 * The four lines never overlap: three are the plan — recurring bills, EMIs,
 * this month's set-aside shares — and the fourth is real spending that none of
 * them accounts for. Overlapping them would subtract a confirmed bill twice and
 * make the figure at the bottom worthless.
 */
/**
 * The next six months, from what is already known.
 *
 * Only the knowable parts: recurring bills, EMIs while they still run, and
 * each set-aside's share for that month. Spending is left out on purpose — an
 * average of past months would look like a forecast without being one, and a
 * figure you cannot rely on is worse here than a missing one.
 */
@Composable
private fun OutlookSection(vm: FinTrackViewModel) {
    val months = vm.outlook(3)
    val totalSavings = months.sumOf { it.left }
    
    Column {
        SectionTitle("Next 3 Months Outlook · ${vm.bucketLabel}", Modifier.padding(bottom = Space.s1))
        Muted(
            "Estimated savings based on your salary, active EMIs, recurring bills, and set-asides.",
            Modifier.padding(bottom = Space.s3)
        )
        
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Pf.Surface.copy(alpha = 0.85f),
                            Pf.Surface2.copy(alpha = 0.95f)
                        )
                    ),
                    Radius.Xl
                )
                .border(1.dp, Pf.Hairline, Radius.Xl)
                .padding(Space.s4)
        ) {
            Column {
                Text(
                    "PROJECTED 6-MONTH SAVINGS",
                    color = Pf.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Text(
                    inr(totalSavings),
                    Modifier.padding(top = 4.dp, bottom = Space.s4),
                    color = if (totalSavings >= 0) Color(0xFF00BFA5) else Color(0xFFFF5252),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    months.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        m.label,
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (m.loanEnding.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Tag(
                                            text = "Last: ${m.loanEnding}",
                                            background = Color(0xFFFFCC80),
                                            contentColor = Color(0xFF5D4037)
                                        )
                                    }
                                }
                                Muted(
                                    "Salary ${inr(m.income)} · Expenses ${inr(m.out)}",
                                    size = 11
                                )
                            }
                            Text(
                                inr(m.left),
                                color = if (m.left >= 0) Color(0xFF00BFA5) else Color(0xFFFF5252),
                                fontSize = 16.sp,
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
private fun MonthPlan(vm: FinTrackViewModel) {
    val nextMonth = vm.outlook(1).firstOrNull()
    val salary = vm.plannedIncome
    val nextLoans = nextMonth?.loans ?: 0.0
    val nextSetAside = nextMonth?.setAside ?: 0.0
    val nextRecurring = nextMonth?.recurring ?: 0.0
    val nextExpenses = nextLoans + nextSetAside + nextRecurring
    val nextLeft = salary - nextExpenses

    // Current Month calculations
    val currentUnplannedSpent = vm.unplannedSpent
    
    val totalLoans = vm.scopedLoans.sumOf { it.monthlyEmi }
    val confirmedLoans = vm.scopedLoans.filter { vm.isLoanConfirmed(it.id) }.sumOf { it.monthlyEmi }
    
    val totalSetAside = vm.annualSetAsides.sumOf { it.monthly }
    val confirmedSetAside = vm.annualSetAsides.sumOf { vm.setAsideDone(it).coerceAtMost(it.monthly) }
    
    val totalRecurring = vm.plannedRecurring
    val confirmedRecurring = vm.scopedEntries
        .filter { it.type == "EXPENSE" && !it.isSetAside && !vm.coveredByLoan(it) && vm.isConfirmed(it.id) }
        .sumOf { it.amount }

    Column {
        SectionTitle("Month Plan · ${vm.bucketLabel}", Modifier.padding(bottom = Space.s3))
        
        // 1. Next Month Estimated Balance Card
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(Pf.Surface, Radius.Lg)
                .border(
                    width = 1.dp,
                    color = Pf.Hairline,
                    shape = Radius.Lg
                )
                .clip(Radius.Lg)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (nextLeft < 0) Pf.Accent400 else Color(0xFF00BFA5))
            )
            Column(
                Modifier.padding(Space.s4)
            ) {
                Text(
                    "NEXT MONTH ESTIMATED BALANCE",
                    color = Pf.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    inr(nextLeft),
                    color = if (nextLeft < 0) Pf.Accent400 else Color(0xFF00BFA5),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Salary: ${inr(salary)} · Expenses: ${inr(nextExpenses)} (Loans: ${inr(nextLoans)}, Set Aside: ${inr(nextSetAside)}, Subs: ${inr(nextRecurring)})",
                    color = Pf.Muted,
                    fontSize = 11.sp
                )
            }
        }
        
        Spacer(Modifier.height(Space.s3))
        
        // 2. Spending Limit (Current Month Expenses)
        KpiCard(
            label = "Expenses this month",
            amountText = inr(currentUnplannedSpent),
            accentColor = Pf.Text,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(Space.s3))
        
        // 3. Grid of Loans & Set Asides
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            KpiCard(
                label = "Loans & EMIs",
                amountText = "${inr(confirmedLoans)} of ${inr(totalLoans)}",
                accentColor = Color(0xFFFF5252),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Set Aside",
                amountText = "${inr(confirmedSetAside)} of ${inr(totalSetAside)}",
                accentColor = Color(0xFFB388FF),
                modifier = Modifier.weight(1f)
            )
        }
        
        if (totalRecurring > 0) {
            Spacer(Modifier.height(Space.s3))
            KpiCard(
                label = "Recurring Subscriptions",
                amountText = "${inr(confirmedRecurring)} of ${inr(totalRecurring)}",
                accentColor = Color(0xFF80D8FF),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    amountText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Pf.Surface, Radius.Md)
            .border(1.dp, Pf.Hairline, Radius.Md)
            .padding(Space.s3)
    ) {
        Text(
            label.uppercase(),
            color = Pf.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            amountText,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PlanRow(label: String, amount: Double, minus: Boolean = false, bold: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s2),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (bold) Pf.Text else Pf.Muted,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            (if (minus) "− " else "") + inr(amount),
            color = Pf.Text,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
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
    
    var expanded by remember { mutableStateOf(false) }

    Column {
        SectionTitle("This month · ${vm.bucketLabel}", Modifier.padding(bottom = Space.s1))
        Muted(
            "Recorded on this side so far. Planned: ${inr(vm.plannedIncome)} in, " +
                "${inr(vm.plannedExpense)} out — expenses, set-asides and EMIs.",
            Modifier.padding(bottom = Space.s3)
        )
        PfCard(padding = PaddingValues(0.dp)) {
            Column {
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
                
                Hairline()
                
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = Space.s4, vertical = Space.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Hide Spend Analytics" else "View Spend Analytics & Savings Rate",
                        color = Pf.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (expanded) "▲" else "▼",
                        color = Pf.Accent, fontSize = 11.sp
                    )
                }

                if (expanded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Space.s4, end = Space.s4, bottom = Space.s4),
                        verticalArrangement = Arrangement.spacedBy(Space.s3)
                    ) {
                        // 1. Savings Rate Calculation
                        val income = vm.actualIncome
                        val spent = vm.actualSpent
                        val savingsRate = if (income > 0.0) {
                            (((income - spent) / income) * 100.0).coerceAtLeast(0.0)
                        } else 0.0
                        
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = Space.s1),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Monthly Savings Rate", color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${"%.1f".format(savingsRate)}%", color = if (savingsRate >= 20.0) Color(0xFF00BFA5) else Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            ProgressBar((savingsRate / 100.0).toFloat(), if (savingsRate >= 20.0) Color(0xFF00BFA5) else Pf.Accent)
                            Muted(
                                "Calculated as actual savings vs. income received.",
                                size = 11
                            )
                        }

                        Hairline()

                        // 2. Category Breakdown Chart
                        Text(
                            "Expense Breakdown by Category",
                            color = Pf.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = Space.s1)
                        )

                        // Compute category spends from transactions of this cycle
                        val cycle = vm.cycle()
                        val currentTxns = vm.filteredTxns.filter { it.kind == "EXPENSE" && it.period == cycle }
                        val totalExpense = currentTxns.sumOf { it.amount }
                        
                        if (totalExpense <= 0.0) {
                            Muted("No expenses recorded this month yet.")
                        } else {
                            val catSpends = currentTxns
                                .groupBy { it.category }
                                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                                .toList()
                                .sortedByDescending { it.second }
                            
                            catSpends.forEach { (cat, amt) ->
                                val pct = amt / totalExpense
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(cat, color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.width(6.dp))
                                            Text("${(pct * 100).toInt()}%", color = Pf.Muted, fontSize = 11.sp)
                                        }
                                        Text(inr(amt), color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    ProgressBar(pct.toFloat(), Pf.Accent)
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
private fun BudgetsSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Category budgets", Modifier.padding(bottom = Space.s3))
        PfCard(padding = PaddingValues(Space.s4)) {
            if (vm.budgets.isEmpty()) {
                Muted("No budgets set. Add one in Settings to track a category here.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                // The limit itself is not read here: what the bar measures
                // against is the allowance below, which is the limit plus
                // whatever last month left over.
                vm.budgets.forEach { (cat, _) ->
                    val spend = vm.spendFor(cat)
                    // The allowance, which is the budget plus whatever last
                    // month left over when rollover is on.
                    val allowed = vm.allowanceFor(cat)
                    val carried = vm.rolloverFor(cat)
                    val pct = safeFraction(spend, allowed)
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Space.s2),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat, color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                if (pct >= 1.0) {
                                    val over = spend - allowed
                                    Tag("Overspent by ${inr(over)}", Pf.Accent, Color.White)
                                } else if (pct >= 0.8) {
                                    Tag("80%+ Used", Color(0xFFFFA726), Color.Black)
                                }
                            }
                            Muted("${inr(spend)} / ${inr(allowed)}", size = 13)
                        }
                        ProgressBar(pct, when {
                            pct >= 1.0 -> Pf.Accent
                            pct >= 0.8 -> Color(0xFFFFA726)
                            else -> Pf.Text
                        })
                        // Says where the extra room came from, or where it went.
                        if (carried != 0.0) {
                            Muted(
                                if (carried > 0) "${inr(carried)} carried over from last month"
                                else "${inr(-carried)} overspent last month, carried in",
                                Modifier.padding(top = 4.dp),
                                size = 12
                            )
                        }
                        // Three months of history: one bar says nothing about
                        // whether a category is drifting.
                        val trend = vm.spendTrendFor(cat)
                        if (trend.any { it > 0.0 }) {
                            Muted(
                                "Last 3 months: " + trend.joinToString(" · ") { inr(it) },
                                Modifier.padding(top = 2.dp),
                                size = 12
                            )
                        }
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
                            PfField(label = "Loan name", value = vm.loanDraft.name, onValueChange = { vm.loanDraft = vm.loanDraft.copy(name = it) }, placeholder = "Loan name")
                            PfSelect(label = "Belongs to", value = vm.loanDraft.person, options = vm.draftPersonOptions, onSelect = { vm.loanDraft = vm.loanDraft.copy(person = it) })
                            PfField(label = "Monthly EMI (₹)", value = vm.loanDraft.emiText, onValueChange = { vm.loanDraft = vm.loanDraft.copy(emiText = it) }, placeholder = "Monthly EMI", numeric = true)
                            PfField(label = "Total months (tenure)", value = vm.loanDraft.totalMonthsText, onValueChange = { vm.loanDraft = vm.loanDraft.copy(totalMonthsText = it) }, placeholder = "Total months (tenure)", numeric = true)
                            PfField(label = "Months remaining", value = vm.loanDraft.remainingMonthsText, onValueChange = { vm.loanDraft = vm.loanDraft.copy(remainingMonthsText = it) }, placeholder = "Months remaining", numeric = true)
                            // Bank accounts and credit cards in one list: an EMI
                            // on a card is billed to the card, not debited.
                            PfSelect(
                                label = "Paid from",
                                value = vm.editLoanSourceName,
                                options = vm.emiSourceOptions,
                                onSelect = vm::setEditLoanSource
                            )
                            PfField(
                                label = "Due day of month (1-31)",
                                value = vm.loanDraft.dueText,
                                onValueChange = { vm.loanDraft = vm.loanDraft.copy(dueText = it) },
                                placeholder = "Due day of month (1-31)",
                                numeric = true
                            )
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
                                    val dueDayText = if (l.dueDay > 0) " · Due day ${l.dueDay}" else ""
                                    Muted(
                                        "${inr(l.monthlyEmi)}/mo · ${l.remainingMonths} of " +
                                            "${l.totalMonths} months left$dueDayText" +
                                            if (l.nextDue.isEmpty()) ""
                                            else " · due in ${Ledger.untilText(today(), l.nextDue)}",
                                        Modifier.padding(top = 2.dp, bottom = 6.dp)
                                    )
                                    // Says where it is charged, since a card EMI
                                    // moves no bank balance until the bill.
                                    Muted(vm.emiSourceLabel(l), Modifier.padding(bottom = 6.dp))
                                    when {
                                        vm.isLoanCleared(l) -> Tag("Paid off", Pf.Accent2_100, Pf.Accent2_800)
                                        l.onCard -> OutlineTag("Card EMI")
                                        else -> OutlineTag("Loan")
                                    }
                                }
                                // No account prompt — the loan already knows where the EMI comes from.
                                when {
                                    // Nothing left to pay: offer to clear it out
                                    // rather than leave a finished loan on Home
                                    // asking for another instalment.
                                    vm.isLoanCleared(l) ->
                                        SecondaryButton("Remove", { vm.deleteLoan(l.id) })
                                    vm.isLoanConfirmed(l.id) ->
                                        SecondaryButton("Paid", { vm.confirmLoan(l) })
                                    else -> PrimaryButton("Pay EMI", { vm.confirmLoan(l) })
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
                            PfField(label = "Card name", value = vm.cardDraft.name, onValueChange = { vm.cardDraft = vm.cardDraft.copy(name = it) }, placeholder = "Card name")
                            PfSelect(label = "Belongs to", value = vm.cardDraft.owner, options = vm.ownerOptions, onSelect = { vm.cardDraft = vm.cardDraft.copy(owner = it) })
                            PfField(label = "Credit limit (₹)", value = vm.cardDraft.limitText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(limitText = it) }, placeholder = "Credit limit", numeric = true)
                            PfField(label = "Current balance (₹)", value = vm.cardDraft.balanceText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(balanceText = it) }, placeholder = "Current balance", numeric = true)
                            PfField(label = "Minimum due (₹)", value = vm.cardDraft.minDueText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(minDueText = it) }, placeholder = "Minimum due", numeric = true)
                            // A real date, so the bill can be reminded about.
                            PfField(label = "Due day of month (1-31)", value = vm.cardDraft.dueText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(dueText = it) }, placeholder = "Due day of month (1-31)", numeric = true)
                            PfField(label = "Statement day of month (1-31)", value = vm.cardDraft.statementDayText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(statementDayText = it) }, placeholder = "Statement day (1-31)", numeric = true)
                            PfField(label = "Statement amount / Actually Due (₹)", value = vm.cardDraft.statementAmountText, onValueChange = { vm.cardDraft = vm.cardDraft.copy(statementAmountText = it) }, placeholder = "Statement amount", numeric = true)
                            // Lets a card spend in a bank SMS find this card.
                            PfField(label = "Last 3-4 digits (for SMS matching)", value = vm.cardDraft.numberTail, onValueChange = { vm.cardDraft = vm.cardDraft.copy(numberTail = it) }, placeholder = "Last 3-4 digits of the card", numeric = true)
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
                                        (if (c.statementAmount > 0.0) "Actually Due: ${inr(c.statementAmount)} (Total Owed: ${inr(c.balance)} of ${inr(c.limit)})"
                                        else "${inr(c.balance)} of ${inr(c.limit)}") +
                                            " · due ${c.dueText}" +
                                            (if (c.nextDue.isEmpty() || c.paid) ""
                                            else " · in ${Ledger.untilText(today(), c.nextDue)}"),
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
                                Muted("Min due ${inr(c.minDue)} · Billed ${c.statementDay}th")
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                                    if (!c.paid && c.balance > 0.0) {
                                        PrimaryButton("Settle Bill", { vm.startSettleCard(c.id) })
                                    }
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
                        // When it is next payable, so a bill isn't sitting there
                        // asking to be confirmed weeks before it goes out.
                        val when_ = if (e.nextDue.isEmpty()) ""
                        else " · due in ${Ledger.untilText(today(), e.nextDue)}"
                        Muted(
                            "${e.person} · ${inr(e.monthly)}/mo$when_",
                            Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
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
    // Shown even when empty. Vanishing entirely read as the feature being gone,
    // when the real cause was a commitment recorded as monthly and therefore
    // sitting under Recurring instead.
    if (items.isEmpty()) {
        Column {
            SectionTitle("Set aside · this month", Modifier.padding(bottom = Space.s1))
            Muted(
                "Nothing set aside on this side yet. Add one from Add → Set aside " +
                    "with the date it is due, and a share of it appears here each " +
                    "month. A bill you pay every month belongs under Recurring."
            )
        }
        return
    }
    Column {
        SectionTitle("Set aside · this month", Modifier.padding(bottom = Space.s1))
        Muted(
            "Put by a share each month. It moves to savings rather than being spent.",
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
                // Figures, not a bar: how much is left is the useful part.
                Muted(
                    "${inr(vm.annualSetAsideDone)} done · " +
                        "${inr(vm.annualSetAsideMonthly - vm.annualSetAsideDone)} left",
                    size = 13
                )
            }
            Column(Modifier.padding(top = Space.s2)) {
                items.forEach { e ->
                    val put = vm.setAsideDone(e)
                    val left = vm.setAsideLeft(e)
                    val pot = vm.setAsidePot(e)
                    val fraction = safeFraction(pot, e.amount)
                    val pct = (fraction * 100).toInt()
                    
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.s2)
                            .background(Pf.Surface.copy(alpha = 0.5f), Radius.Md)
                            .border(1.dp, Pf.Hairline, Radius.Md)
                            .clickable { vm.openEditEntry(e) }
                            .padding(Space.s3)
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    e.note.ifEmpty { e.category },
                                    color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "$pct%",
                                    color = if (pct >= 100) Color(0xFF00BFA5) else Pf.Text,
                                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                                )
                            }
                            
                            ProgressBar(
                                fraction = fraction,
                                color = if (pct >= 100) Color(0xFF00BFA5) else Pf.Accent,
                                height = 5,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Muted(
                                        "${inr(e.monthly(vm.salaryResetDayFor(e.person)))}/mo · " +
                                            if (put > 0) "${inr(put)} put by, ${inr(left)} left"
                                            else "none put by yet",
                                        size = 11
                                    )
                                    Muted(
                                        if (e.nextDue.isNotEmpty()) {
                                            val n = Ledger.instalmentsUntil(today(), e.nextDue, vm.salaryResetDayFor(e.person))
                                            "${inr(pot)} of ${inr(e.amount)} saved · due " +
                                                "${prettyDate(e.nextDue)}, $n month${if (n == 1) "" else "s"} to go"
                                        } else {
                                            "${inr(pot)} of ${inr(e.amount)} saved · every ${e.everyMonths} months"
                                        },
                                        size = 11
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.s1)
                                ) {
                                    if (vm.canPaySetAside(e)) {
                                        PrimaryButton("Pay ${inr(e.amount)}", { vm.paySetAside(e) })
                                    } else if (left <= 0.0) {
                                        SecondaryButton("Undo", { vm.requestConfirm(e) })
                                    } else {
                                        PrimaryButton(if (put > 0) "Add" else "Set aside", { vm.requestConfirm(e) })
                                    }
                                    IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = Pf.Accent400)
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
private fun SmsSuggestionBanner(vm: FinTrackViewModel) {
    val suggestions = vm.smsSuggestions
    if (suggestions.isEmpty()) return
    
    val suggestion = suggestions.entries.first()
    val txnId = suggestion.key
    val entryId = suggestion.value
    
    val txn = vm.txns.firstOrNull { it.id == txnId } ?: return
    val entry = vm.entries.firstOrNull { it.id == entryId } ?: return
    
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Pf.Surface.copy(alpha = 0.9f),
                        Pf.Surface2.copy(alpha = 0.95f)
                    )
                ),
                Radius.Lg
            )
            .border(1.dp, Pf.Accent.copy(alpha = 0.3f), Radius.Lg)
            .padding(Space.s4)
    ) {
        Column {
            Text(
                "SINKING FUND MATCH IMPORTED",
                color = Pf.Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Text(
                "Link imported transaction of ${inr(txn.amount)} for '${txn.note}' to your '${entry.category}' Sinking Fund?",
                Modifier.padding(top = 4.dp, bottom = Space.s3),
                color = Pf.Text,
                fontSize = 13.sp
            )
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.End)
            ) {
                GhostButton("Dismiss", {
                    vm.dismissSmsSuggestion(txnId)
                })
                PrimaryButton("Link Match", {
                    vm.linkSmsToSinkingFund(txnId, entryId)
                })
            }
        }
    }
}

@Composable
private fun SpendVelocityCard(vm: FinTrackViewModel) {
    val velocity = vm.getSpendVelocity()
    
    val speedLabel = when (velocity.speed) {
        FinTrackViewModel.VelocitySpeed.LOW -> "Low Spend Velocity · Under Budget 🎉"
        FinTrackViewModel.VelocitySpeed.ON_TRACK -> "On Track · Perfect Pace 👍"
        FinTrackViewModel.VelocitySpeed.HIGH -> "High Spend Velocity · Over Budget ⚠️"
    }
    
    val speedColor = when (velocity.speed) {
        FinTrackViewModel.VelocitySpeed.LOW -> Color(0xFF00BFA5)
        FinTrackViewModel.VelocitySpeed.ON_TRACK -> Pf.Text
        FinTrackViewModel.VelocitySpeed.HIGH -> Color(0xFFFF5252)
    }
    
    Box(
        Modifier
            .fillMaxWidth()
            .background(Pf.Surface, Radius.Lg)
            .border(1.dp, Pf.Hairline, Radius.Lg)
            .padding(Space.s4)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DISCRETIONARY SPEND VELOCITY",
                    color = Pf.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "${velocity.daysRemaining} days left",
                    color = Pf.Muted,
                    fontSize = 11.sp
                )
            }
            
            Text(
                speedLabel,
                Modifier.padding(top = 4.dp, bottom = Space.s2),
                color = speedColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Muted("Actual Daily Spend", size = 11)
                    Text(inr(velocity.actualDailySpend), color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Muted("Target Daily Limit", size = 11)
                    Text(inr(velocity.targetDailyBudget), color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            val pct = if (velocity.targetDailyBudget > 0.0) {
                (velocity.actualDailySpend / velocity.targetDailyBudget).toFloat().coerceIn(0f, 2f)
            } else 0f
            
            ProgressBar(
                fraction = pct / 2f,
                color = speedColor,
                modifier = Modifier.padding(top = Space.s3)
            )
        }
    }
}
