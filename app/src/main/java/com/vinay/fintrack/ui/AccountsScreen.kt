package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.Tab
import com.vinay.fintrack.data.Account
import com.vinay.fintrack.data.Card
import com.vinay.fintrack.data.Loan
import com.vinay.fintrack.data.inr

private enum class AccountsSectionFilter(val label: String) {
    ALL("All"),
    BANKS("Bank Accounts"),
    CARDS("Credit Cards"),
    LOANS("Loans & EMIs")
}

@Composable
fun AccountsScreen(vm: FinTrackViewModel) {
    var sectionFilter by remember { mutableStateOf(AccountsSectionFilter.ALL) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 80.dp, top = Space.s2, start = Space.s4, end = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s4)
    ) {
        // 1. Header with Scope Switch
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Accounts & Balances",
                        color = Pf.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Muted("Edit bank balances, card limits & loan EMIs")
                }
                Row(
                    Modifier
                        .background(Pf.Surface2, Radius.Pill)
                        .clickable { vm.toggleBucket() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (vm.bucketView == "JOINT") Icons.Default.People else Icons.Default.Person,
                        null,
                        Modifier.size(13.dp),
                        tint = Pf.Accent400
                    )
                    Text(
                        if (vm.bucketView == "JOINT") "Joint" else (vm.activeProfile ?: "Personal"),
                        color = Pf.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Filter Pills
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                AccountsSectionFilter.values().forEach { filter ->
                    val isSelected = sectionFilter == filter
                    Text(
                        filter.label,
                        modifier = Modifier
                            .background(
                                if (isSelected) Pf.Accent else Pf.Surface2,
                                Radius.Pill
                            )
                            .clickable { sectionFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isSelected) Pf.OnAccent else Pf.Text,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Bank Accounts Section
        if (sectionFilter == AccountsSectionFilter.ALL || sectionFilter == AccountsSectionFilter.BANKS) {
            item {
                ManageBanksSection(vm)
            }
        }

        // 4. Credit Cards Section
        if (sectionFilter == AccountsSectionFilter.ALL || sectionFilter == AccountsSectionFilter.CARDS) {
            item {
                ManageCardsSection(vm)
            }
        }

        // 5. Loans Section
        if (sectionFilter == AccountsSectionFilter.ALL || sectionFilter == AccountsSectionFilter.LOANS) {
            item {
                ManageLoansSection(vm)
            }
        }
    }
}

@Composable
private fun ManageBanksSection(vm: FinTrackViewModel) {
    val accounts = vm.scopedAccounts
    val totalBalance = accounts.sumOf { vm.balanceOf(it) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Icon(Icons.Default.AccountBalance, null, Modifier.size(18.dp), tint = Pf.Accent400)
                Text("Bank Accounts", color = Pf.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Tag(inr(totalBalance), Pf.Accent100, Pf.Accent800)
            }
            GhostButton("+ Add Bank", {
                vm.selectAddKind("BANK_ACCOUNT")
                vm.tab = Tab.ADD
            })
        }

        if (accounts.isEmpty()) {
            PfCard(padding = PaddingValues(Space.s4)) {
                Text("No bank accounts found for this profile.", color = Pf.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(Space.s2))
                SecondaryButton("+ Add Bank Account", {
                    vm.selectAddKind("BANK_ACCOUNT")
                    vm.tab = Tab.ADD
                })
            }
        } else {
            PfCard(padding = PaddingValues(0.dp)) {
                accounts.forEachIndexed { idx, a ->
                    if (idx > 0) Hairline()
                    if (vm.editingAccountId == a.id) {
                        Box(Modifier.padding(Space.s4)) {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                                Text("Edit Bank Account", color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                PfField(
                                    label = "Account Name",
                                    value = vm.accountDraft.name,
                                    onValueChange = { vm.accountDraft = vm.accountDraft.copy(name = it) },
                                    placeholder = "e.g. ICICI Bank, HDFC Savings"
                                )
                                PfSelect(
                                    label = "Belongs To",
                                    value = vm.accountDraft.owner,
                                    options = vm.ownerOptions,
                                    onSelect = { vm.accountDraft = vm.accountDraft.copy(owner = it) }
                                )
                                PfField(
                                    label = "Current Balance (₹)",
                                    value = vm.accountDraft.balanceText,
                                    onValueChange = { vm.accountDraft = vm.accountDraft.copy(balanceText = it) },
                                    placeholder = "Current balance in rupees",
                                    numeric = true
                                )
                                PfField(
                                    label = "Last 3-4 digits (for SMS auto-sync)",
                                    value = vm.accountDraft.numberTail,
                                    onValueChange = { vm.accountDraft = vm.accountDraft.copy(numberTail = it) },
                                    placeholder = "e.g. 1234",
                                    numeric = true
                                )
                                EditorActions({ vm.deleteAccount(a.id) }, vm::cancelEditAccount, vm::saveAccount)
                            }
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.startEditAccount(a) }
                                .padding(horizontal = Space.s4, vertical = Space.s3),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.name,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (a.numberTail.isNotBlank()) "••${a.numberTail} · ${a.owner}" else a.owner,
                                    color = Pf.Muted,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                Text(
                                    inr(vm.balanceOf(a)),
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { vm.startEditAccount(a) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit account", Modifier.size(16.dp), tint = Pf.Accent400)
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
private fun ManageCardsSection(vm: FinTrackViewModel) {
    val cards = vm.scopedCards
    val totalDues = cards.sumOf { it.balance }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Icon(Icons.Default.CreditCard, null, Modifier.size(18.dp), tint = Pf.Accent400)
                Text("Credit Cards", color = Pf.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Tag("Total Due: ${inr(totalDues)}", Pf.Accent100, Pf.Accent800)
            }
            GhostButton("+ Add Card", {
                vm.selectAddKind("CREDIT_CARD")
                vm.tab = Tab.ADD
            })
        }

        if (cards.isEmpty()) {
            PfCard(padding = PaddingValues(Space.s4)) {
                Text("No credit cards found for this profile.", color = Pf.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(Space.s2))
                SecondaryButton("+ Add Credit Card", {
                    vm.selectAddKind("CREDIT_CARD")
                    vm.tab = Tab.ADD
                })
            }
        } else {
            PfCard(padding = PaddingValues(0.dp)) {
                cards.forEachIndexed { idx, c ->
                    if (idx > 0) Hairline()
                    if (vm.editingCardId == c.id) {
                        Box(Modifier.padding(Space.s4)) {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                                Text("Edit Credit Card", color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                PfField(
                                    label = "Card Name",
                                    value = vm.cardDraft.name,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(name = it) },
                                    placeholder = "e.g. Swiggy HDFC, Amazon ICICI"
                                )
                                PfSelect(
                                    label = "Belongs To",
                                    value = vm.cardDraft.owner,
                                    options = vm.ownerOptions,
                                    onSelect = { vm.cardDraft = vm.cardDraft.copy(owner = it) }
                                )
                                PfField(
                                    label = "Credit Limit (₹)",
                                    value = vm.cardDraft.limitText,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(limitText = it) },
                                    placeholder = "Credit limit",
                                    numeric = true
                                )
                                PfField(
                                    label = "Current Balance / Due (₹)",
                                    value = vm.cardDraft.balanceText,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(balanceText = it) },
                                    placeholder = "Current unpaid balance",
                                    numeric = true
                                )
                                PfField(
                                    label = "Minimum Due (₹)",
                                    value = vm.cardDraft.minDueText,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(minDueText = it) },
                                    placeholder = "Minimum due",
                                    numeric = true
                                )
                                PfField(
                                    label = "Due Day of Month (1-31)",
                                    value = vm.cardDraft.dueText,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(dueText = it) },
                                    placeholder = "e.g. 15",
                                    numeric = true
                                )
                                PfField(
                                    label = "Statement Day of Month (1-31)",
                                    value = vm.cardDraft.statementDayText,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(statementDayText = it) },
                                    placeholder = "e.g. 1",
                                    numeric = true
                                )
                                PfField(
                                    label = "Statement Amount (₹)",
                                    value = vm.cardDraft.statementAmountText,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(statementAmountText = it) },
                                    placeholder = "Statement amount",
                                    numeric = true
                                )
                                PfField(
                                    label = "Last 3-4 digits (for SMS auto-sync)",
                                    value = vm.cardDraft.numberTail,
                                    onValueChange = { vm.cardDraft = vm.cardDraft.copy(numberTail = it) },
                                    placeholder = "e.g. 5678",
                                    numeric = true
                                )
                                EditorActions({ vm.deleteCard(c.id) }, vm::cancelEditCard, vm::saveCard)
                            }
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.startEditCard(c) }
                                .padding(horizontal = Space.s4, vertical = Space.s3),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.name,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val info = when {
                                    c.dueText.isNotBlank() && c.numberTail.isNotBlank() -> "Due: ${c.dueText} · ••${c.numberTail}"
                                    c.dueText.isNotBlank() -> "Due: ${c.dueText} · ${c.owner}"
                                    c.numberTail.isNotBlank() -> "••${c.numberTail} · ${c.owner}"
                                    else -> c.owner
                                }
                                Text(info, color = Pf.Muted, fontSize = 12.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                Text(
                                    inr(c.balance),
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (c.balance > 0) {
                                    Box(
                                        Modifier
                                            .background(Pf.Surface2, Radius.Pill)
                                            .clickable { vm.startSettleCard(c.id) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Settle", color = Pf.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(
                                    onClick = { vm.startEditCard(c) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit card", Modifier.size(16.dp), tint = Pf.Accent400)
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
private fun ManageLoansSection(vm: FinTrackViewModel) {
    val loans = vm.scopedLoans.filter { !vm.isLoanCleared(it) }
    val totalEmis = loans.sumOf { it.monthlyEmi }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Icon(Icons.Default.Payments, null, Modifier.size(18.dp), tint = Pf.Accent400)
                Text("Loans & EMIs", color = Pf.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Tag("Total EMI: ${inr(totalEmis)}", Pf.Accent100, Pf.Accent800)
            }
            GhostButton("+ Add Loan", {
                vm.selectAddKind("EMI_LOAN")
                vm.tab = Tab.ADD
            })
        }

        if (loans.isEmpty()) {
            PfCard(padding = PaddingValues(Space.s4)) {
                Text("No active loans or car loans found.", color = Pf.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(Space.s2))
                SecondaryButton("+ Add Loan / EMI", {
                    vm.selectAddKind("EMI_LOAN")
                    vm.tab = Tab.ADD
                })
            }
        } else {
            PfCard(padding = PaddingValues(0.dp)) {
                loans.forEachIndexed { idx, l ->
                    if (idx > 0) Hairline()
                    if (vm.editingLoanId == l.id) {
                        Box(Modifier.padding(Space.s4)) {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                                Text("Edit Loan / EMI", color = Pf.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                PfField(
                                    label = "Loan Name",
                                    value = vm.loanDraft.name,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(name = it) },
                                    placeholder = "e.g. Car Loan, Home Loan, Phone EMI"
                                )
                                PfSelect(
                                    label = "Belongs To",
                                    value = vm.loanDraft.person,
                                    options = vm.draftPersonOptions,
                                    onSelect = { vm.loanDraft = vm.loanDraft.copy(person = it) }
                                )
                                PfField(
                                    label = "Monthly EMI (₹)",
                                    value = vm.loanDraft.emiText,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(emiText = it) },
                                    placeholder = "Monthly installment amount",
                                    numeric = true
                                )
                                PfField(
                                    label = "Total Months (Tenure)",
                                    value = vm.loanDraft.totalMonthsText,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(totalMonthsText = it) },
                                    placeholder = "Total months (e.g. 36)",
                                    numeric = true
                                )
                                PfField(
                                    label = "Months Remaining",
                                    value = vm.loanDraft.remainingMonthsText,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(remainingMonthsText = it) },
                                    placeholder = "Months remaining (e.g. 24)",
                                    numeric = true
                                )
                                PfSelect(
                                    label = "Paid From Account",
                                    value = vm.editLoanSourceName,
                                    options = vm.emiSourceOptions,
                                    onSelect = vm::setEditLoanSource
                                )
                                PfField(
                                    label = "Due Day of Month (1-31)",
                                    value = vm.loanDraft.dueText,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(dueText = it) },
                                    placeholder = "e.g. 5",
                                    numeric = true
                                )
                                EditorActions({ vm.deleteLoan(l.id) }, vm::cancelEditLoan, vm::saveLoan)
                            }
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.startEditLoan(l) }
                                .padding(horizontal = Space.s4, vertical = Space.s3),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    l.name,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${l.remainingMonths} mo left · EMI ${inr(l.monthlyEmi)} · ${l.person}",
                                    color = Pf.Muted,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                val isPaid = vm.isLoanConfirmed(l.id)
                                Box(
                                    Modifier
                                        .background(Pf.Surface2, Radius.Pill)
                                        .clickable { vm.confirmLoan(l) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            if (isPaid) "Paid" else "Pay EMI",
                                            color = if (isPaid) Pf.Muted else Pf.Text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                IconButton(
                                    onClick = { vm.startEditLoan(l) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit loan", Modifier.size(16.dp), tint = Pf.Accent400)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
