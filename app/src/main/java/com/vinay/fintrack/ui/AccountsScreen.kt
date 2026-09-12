package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.Tab
import com.vinay.fintrack.data.Entry
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.MissingConfigItem
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune

@Composable
fun AccountsScreen(vm: FinTrackViewModel) {
    val selectedFilter = vm.accountsFilter

    val confirmedLoansCount = vm.scopedLoans.count { vm.isLoanConfirmed(it.id) }
    val confirmedBillsCount = vm.commitments.count { vm.isConfirmed(it.id) }
    val settledCardsCount = vm.scopedCards.count { it.paid || it.balance == 0.0 }
    val pastCount = vm.pastClosedLoans.size + vm.pastClosedSetAsides.size + vm.pastClosedRecurring.size +
            confirmedLoansCount + confirmedBillsCount + settledCardsCount

    val filterOptions = listOf(
        "All" to "All",
        "Banks" to "Banks (${vm.scopedAccounts.size})",
        "Cards" to "Cards (${vm.scopedCards.size})",
        "Loans" to "Loans (${vm.scopedLoans.filter { !vm.isLoanCleared(it) }.size})",
        "Recurring" to "Recurring (${vm.commitments.size})",
        "Set Aside" to "Set Aside (${vm.annualSetAsides.size})",
        "Past" to "Past ($pastCount)"
    )

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 90.dp, top = Space.s3, start = Space.s4, end = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s5)
    ) {
        // 1. Header & Filter Chips
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Accounts & Balances",
                        color = Pf.Text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Muted("Tap any item to edit balance, card limits, or EMI details", size = 13)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    filterOptions.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            Modifier
                                .background(
                                    if (isSelected) Pf.Accent else Pf.Surface2,
                                    Radius.Pill
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Pf.Accent else Pf.Hairline,
                                    Radius.Pill
                                )
                                .clickable { vm.accountsFilter = key }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 2. Bank Accounts Section
        if (selectedFilter == "All" || selectedFilter == "Banks") {
            item {
                ManageBanksSection(vm)
            }
        }

        // 3. Credit Cards Section
        if (selectedFilter == "All" || selectedFilter == "Cards") {
            item {
                ManageCardsSection(vm)
            }
        }

        // 4. Loans Section
        if (selectedFilter == "All" || selectedFilter == "Loans") {
            item {
                ManageLoansSection(vm)
            }
        }

        // 5. Recurring Section
        if (selectedFilter == "All" || selectedFilter == "Recurring") {
            item {
                ManageRecurringSection(vm)
            }
        }

        // 6. Set Aside Section
        if (selectedFilter == "All" || selectedFilter == "Set Aside") {
            item {
                ManageSetAsidesSection(vm)
            }
        }

        // 7. Past / Completed Payments Section
        if (selectedFilter == "All" || selectedFilter == "Past") {
            item {
                ManagePastPaymentsSection(vm)
            }
        }

        // 8. Incomplete Setup / Missing Details Health Check (at the end of accounts page)
        item {
            IncompleteSetupSection(vm)
        }
    }

    ConfirmSheet(vm)
    CardSettleSheet(vm)
    LoanConfirmSheet(vm)
    SetupFixDialog(vm)
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    badgeText: String,
    onAddClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.s1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(Pf.Surface2, Radius.Sm),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(15.dp), tint = Pf.Accent400)
            }
            Text(
                title,
                color = Pf.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Tag(badgeText, Pf.Accent100, Pf.Accent800)
        }
        GhostButton("+ Add", onClick = onAddClick)
    }
}

@Composable
private fun ManageBanksSection(vm: FinTrackViewModel) {
    val accounts = vm.scopedAccounts
    val totalBalance = accounts.sumOf { vm.balanceOf(it) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        SectionHeader(
            icon = Icons.Default.AccountBalance,
            title = "Bank Accounts",
            badgeText = inr(totalBalance),
            onAddClick = {
                vm.selectAddKind("BANK_ACCOUNT")
                vm.tab = Tab.ADD
            }
        )

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
                        val bal = vm.balanceOf(a)
                        val isNegative = bal < 0.0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.startEditAccount(a) }
                                .padding(horizontal = Space.s4, vertical = Space.s3),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                Text(
                                    a.name,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when {
                                    a.numberTail.isNotBlank() && a.person == "Joint" -> "••••${a.numberTail} · Joint"
                                    a.numberTail.isNotBlank() -> "••••${a.numberTail}"
                                    a.person == "Joint" -> "Joint"
                                    else -> "Bank Account"
                                }
                                Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                Text(
                                    inr(bal),
                                    color = if (isNegative) Color(0xFFEF4444) else Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("›", color = Pf.Muted, fontSize = 16.sp)
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
        SectionHeader(
            icon = Icons.Default.CreditCard,
            title = "Credit Cards",
            badgeText = inr(totalDues),
            onAddClick = {
                vm.selectAddKind("CREDIT_CARD")
                vm.tab = Tab.ADD
            }
        )

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
                        val cleanName = if (c.name.contains("••") && c.numberTail.isNotBlank()) {
                            c.name.substringBefore("••").trim().ifEmpty { c.name }
                        } else c.name

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.startEditCard(c) }
                                .padding(horizontal = Space.s4, vertical = Space.s3),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                Text(
                                    cleanName,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val duePart = if (c.dueText.isNotBlank()) "Due: ${c.dueText}" else null
                                val tailPart = if (c.numberTail.isNotBlank()) "••••${c.numberTail}" else null
                                val ownerPart = if (c.owner == "Joint") "Joint" else null
                                val subtitle = listOfNotNull(duePart, tailPart, ownerPart).joinToString(" · ").ifEmpty { "Credit Card" }
                                Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
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
                                            .border(1.dp, Pf.Hairline, Radius.Pill)
                                            .clickable { vm.startSettleCard(c.id) }
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text("Settle", color = Pf.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text("›", color = Pf.Muted, fontSize = 16.sp)
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
        SectionHeader(
            icon = Icons.Default.Payments,
            title = "Loans & EMIs",
            badgeText = inr(totalEmis),
            onAddClick = {
                vm.selectAddKind("EMI_LOAN")
                vm.tab = Tab.ADD
            }
        )

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
                                val payMonthOptions = vm.startPayMonthOptions
                                val selectedStartMonthKey = vm.loanDraft.startMonth.ifEmpty { today().take(7) }
                                val selectedStartMonthLabel = payMonthOptions.firstOrNull { it.key == selectedStartMonthKey }?.label
                                    ?: payMonthOptions.firstOrNull()?.label.orEmpty()
                                PfSelect(
                                    label = "Start Pay Month",
                                    value = selectedStartMonthLabel,
                                    options = payMonthOptions.map { it.label },
                                    onSelect = { label ->
                                        val opt = payMonthOptions.firstOrNull { it.label == label }
                                        if (opt != null) {
                                            vm.loanDraft = vm.loanDraft.copy(startMonth = opt.key)
                                        }
                                    }
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
                            Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                Text(
                                    l.name,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val personPart = if (l.person == "Joint") "Joint" else null
                                val tenurePart = "${l.remainingMonths} mo left · EMI ${inr(l.monthlyEmi)}"
                                val subtitle = listOfNotNull(tenurePart, personPart).joinToString(" · ")
                                Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                val isPaid = vm.isLoanConfirmed(l.id)
                                Box(
                                    Modifier
                                        .background(Pf.Surface2, Radius.Pill)
                                        .border(1.dp, Pf.Hairline, Radius.Pill)
                                        .clickable { vm.startConfirmLoan(l) }
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        if (isPaid) "Paid" else "Pay EMI",
                                        color = if (isPaid) Pf.Muted else Pf.Text,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text("›", color = Pf.Muted, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageRecurringSection(vm: FinTrackViewModel) {
    val items = vm.commitments
    val totalMonthly = items.sumOf { it.monthly }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        SectionHeader(
            icon = Icons.Default.Receipt,
            title = "Recurring Bills",
            badgeText = "${inr(totalMonthly)}/mo",
            onAddClick = {
                vm.selectAddKind("RECURRING")
                vm.tab = Tab.ADD
            }
        )

        if (items.isEmpty()) {
            PfCard(padding = PaddingValues(Space.s4)) {
                Text("No recurring commitments or bills found.", color = Pf.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(Space.s2))
                SecondaryButton("+ Add Recurring Bill", {
                    vm.selectAddKind("RECURRING")
                    vm.tab = Tab.ADD
                })
            }
        } else {
            PfCard(padding = PaddingValues(0.dp)) {
                items.forEachIndexed { idx, e ->
                    if (idx > 0) Hairline()
                    val done = vm.isConfirmed(e.id)
                    val kind = vm.commitmentKind(e)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openEditEntry(e) }
                            .padding(horizontal = Space.s4, vertical = Space.s3),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = Space.s2)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s2)
                            ) {
                                Text(
                                    e.note.ifEmpty { e.category },
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                when (kind) {
                                    "Investment" -> Tag(kind, Pf.Accent100, Pf.Accent800)
                                    "Savings" -> Tag(kind, Pf.Accent2_100, Pf.Accent2_800)
                                    else -> Tag(kind, Pf.Neutral100, Pf.Neutral800)
                                }
                            }
                            val when_ = if (e.nextDue.isEmpty()) "" else " · due in ${Ledger.untilText(today(), e.nextDue)}"
                            val subtitle = "${e.person} · ${inr(e.monthly)}/mo$when_"
                            Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            Box(
                                Modifier
                                    .background(if (done) Pf.Surface2 else Pf.Accent, Radius.Pill)
                                    .border(1.dp, if (done) Pf.Hairline else Pf.Accent, Radius.Pill)
                                    .clickable { vm.requestConfirm(e) }
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (done) "Paid" else "Pay",
                                    color = if (done) Pf.Muted else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(15.dp), tint = Pf.Accent400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageSetAsidesSection(vm: FinTrackViewModel) {
    val items = vm.annualSetAsides
    val totalDone = vm.annualSetAsideDone
    val totalNeeded = vm.annualSetAsideMonthly

    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        SectionHeader(
            icon = Icons.Default.TrendingUp,
            title = "Set Aside",
            badgeText = "${inr(totalNeeded)}/mo",
            onAddClick = {
                vm.selectAddKind("SET_ASIDE")
                vm.tab = Tab.ADD
            }
        )

        if (items.isEmpty()) {
            PfCard(padding = PaddingValues(Space.s4)) {
                Text(
                    "Nothing set aside yet. Add one to put aside funds monthly for upcoming yearly/periodic bills or goals.",
                    color = Pf.Muted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(Space.s2))
                SecondaryButton("+ Add Set Aside", {
                    vm.selectAddKind("SET_ASIDE")
                    vm.tab = Tab.ADD
                })
            }
        } else {
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
                            inr(totalNeeded),
                            color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Muted(
                        "${inr(totalDone)} done · ${inr((totalNeeded - totalDone).coerceAtLeast(0.0))} left",
                        size = 12
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    items.forEach { e ->
                        val put = vm.setAsideDone(e)
                        val left = vm.setAsideLeft(e)
                        val pot = vm.setAsidePot(e)
                        val fraction = safeFraction(pot, e.amount)
                        val pct = (fraction * 100).toInt()

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Pf.Surface2.copy(alpha = 0.6f), Radius.Md)
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
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(Space.s2))
                                    Text(
                                        "$pct%",
                                        color = if (pct >= 100) Color(0xFF00BFA5) else Pf.Text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
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
                                    Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                        Muted(
                                            "${inr(e.monthly(vm.salaryResetDayFor(e.person)))}/mo · " +
                                                if (put > 0) "${inr(put)} put by, ${inr(left)} left"
                                                else "none put by yet",
                                            size = 11
                                        )
                                        Muted(
                                            if (e.nextDue.isNotEmpty()) {
                                                val n = Ledger.instalmentsUntil(today(), e.nextDue, vm.salaryResetDayFor(e.person))
                                                "${inr(pot)} of ${inr(e.amount)} saved · due ${prettyDate(e.nextDue)}, $n mo to go"
                                            } else {
                                                "${inr(pot)} of ${inr(e.amount)} saved · every ${e.everyMonths} mo"
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
                                        IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, "Delete", Modifier.size(15.dp), tint = Pf.Accent400)
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
}

@Composable
private fun ManagePastPaymentsSection(vm: FinTrackViewModel) {
    val confirmedLoans = vm.scopedLoans.filter { vm.isLoanConfirmed(it.id) }
    val confirmedRecurring = vm.commitments.filter { vm.isConfirmed(it.id) }
    val settledCards = vm.scopedCards.filter { it.paid || it.balance == 0.0 }
    val pastLoans = vm.pastClosedLoans
    val pastSetAsides = vm.pastClosedSetAsides
    val pastRecurring = vm.pastClosedRecurring
    val recentPaymentTxns = vm.txns.filter {
        it.loanId.isNotEmpty() || it.entryId.isNotEmpty() || it.cardId.isNotEmpty() || it.category in listOf("EMI", "Credit Card Bill")
    }.sortedByDescending { it.date }.take(10)

    val totalPast = confirmedLoans.size + confirmedRecurring.size + settledCards.size +
            pastLoans.size + pastSetAsides.size + pastRecurring.size + recentPaymentTxns.size

    val isFilterPast = vm.accountsFilter == "Past"
    var isExpanded by remember(isFilterPast) { mutableStateOf(isFilterPast || totalPast > 0) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Space.s1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Pf.Surface2, Radius.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    "Past / Completed Payments",
                    color = Pf.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Tag(
                    "$totalPast completed",
                    Color(0xFF10B981).copy(alpha = 0.15f),
                    Color(0xFF10B981)
                )
            }
            if (totalPast > 0) {
                GhostButton(if (isExpanded) "Hide" else "Show", { isExpanded = !isExpanded })
            }
        }

        if (totalPast == 0) {
            PfCard(padding = PaddingValues(Space.s4)) {
                Text(
                    "No completed payments yet. When you pay off a loan, confirm a bill or EMI, or settle a card, it will appear here.",
                    color = Pf.Muted,
                    fontSize = 13.sp
                )
            }
        } else if (isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                // 1. Confirmed Loans this cycle
                if (confirmedLoans.isNotEmpty()) {
                    Text(
                        "LOANS PAID THIS CYCLE",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    confirmedLoans.forEach { l ->
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        l.name,
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val tenurePart = "${l.totalMonths - l.remainingMonths} of ${l.totalMonths} months paid"
                                    val subtitle = listOfNotNull(tenurePart, inr(l.monthlyEmi), if (l.person == "Joint") "Joint" else null).joinToString(" · ")
                                    Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                                }
                                Tag("✓ Paid this cycle", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                            }
                        }
                    }
                }

                // 2. Confirmed Recurring bills this cycle
                if (confirmedRecurring.isNotEmpty()) {
                    Text(
                        "RECURRING BILLS PAID THIS CYCLE",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    confirmedRecurring.forEach { e ->
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        e.note.ifEmpty { e.category },
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("${inr(e.amount)} · ${e.person}", color = Pf.Muted, fontSize = 12.sp)
                                }
                                Tag("✓ Paid this cycle", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                            }
                        }
                    }
                }

                // 3. Settled Credit Cards
                if (settledCards.isNotEmpty()) {
                    Text(
                        "SETTLED CREDIT CARDS",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    settledCards.forEach { c ->
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        c.name,
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val tailPart = if (c.numberTail.isNotBlank()) "••••${c.numberTail}" else null
                                    val subtitle = listOfNotNull(tailPart, if (c.owner == "Joint") "Joint" else null).joinToString(" · ")
                                    Text(subtitle.ifEmpty { "Card" }, color = Pf.Muted, fontSize = 12.sp)
                                }
                                Tag("✓ Settled / ₹0 Due", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                            }
                        }
                    }
                }

                // 4. Recent Payment Transactions / History
                if (recentPaymentTxns.isNotEmpty()) {
                    Text(
                        "RECENT PAYMENT RECORDS",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    recentPaymentTxns.forEach { t ->
                        val accName = vm.accounts.firstOrNull { it.id == t.fromAccountId }?.name.orEmpty()
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        t.note.ifEmpty { t.category },
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val subtitle = listOfNotNull(
                                        prettyDate(t.date),
                                        accName.ifEmpty { null }
                                    ).joinToString(" · ")
                                    Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                                }
                                Text(
                                    "-${inr(t.amount)}",
                                    color = Pf.Text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 5. Paid off Loans (Tenure complete)
                if (pastLoans.isNotEmpty()) {
                    Text(
                        "PAID OFF LOANS (ARCHIVED)",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    pastLoans.forEach { l ->
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        l.name,
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val personPart = if (l.person == "Joint") "Joint" else null
                                    val tenurePart = "${l.totalMonths} of ${l.totalMonths} months paid (${inr(l.monthlyEmi)}/mo)"
                                    val subtitle = listOfNotNull(tenurePart, personPart).joinToString(" · ")
                                    Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.s1)
                                ) {
                                    Tag("Paid off", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                                    IconButton(onClick = { vm.deleteLoan(l.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, "Delete", Modifier.size(15.dp), tint = Pf.Accent400)
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Completed Set Asides
                if (pastSetAsides.isNotEmpty()) {
                    Text(
                        "COMPLETED SET ASIDES",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    pastSetAsides.forEach { e ->
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        e.note.ifEmpty { e.category },
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val duePart = if (e.dueDate.isNotEmpty()) "Target: ${prettyDate(e.dueDate)}" else null
                                    val subtitle = listOfNotNull("Total: ${inr(e.amount)}", duePart, e.person).joinToString(" · ")
                                    Text(subtitle, color = Pf.Muted, fontSize = 12.sp)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.s1)
                                ) {
                                    Tag("Completed", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                                    SecondaryButton("Reopen", { vm.closeEntry(e.id, false) })
                                    IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, "Delete", Modifier.size(15.dp), tint = Pf.Accent400)
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. Inactive / Closed Recurring
                if (pastRecurring.isNotEmpty()) {
                    Text(
                        "CLOSED RECURRING BILLS",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = Space.s2)
                    )
                    pastRecurring.forEach { e ->
                        PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                    Text(
                                        e.note.ifEmpty { e.category },
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("${inr(e.amount)}/mo · ${e.person}", color = Pf.Muted, fontSize = 12.sp)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.s1)
                                ) {
                                    Tag("Inactive", Pf.Muted.copy(alpha = 0.2f), Pf.Muted)
                                    SecondaryButton("Reactivate", { vm.closeEntry(e.id, false) })
                                    IconButton(onClick = { vm.deleteEntry(e.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, "Delete", Modifier.size(15.dp), tint = Pf.Accent400)
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
private fun IncompleteSetupSection(vm: FinTrackViewModel) {
    val missingItems = vm.missingSetupItems

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.s2),
        verticalArrangement = Arrangement.spacedBy(Space.s3)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            if (missingItems.isNotEmpty()) Color(0xFFFFA726).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                            Radius.Sm
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (missingItems.isNotEmpty()) Icons.Default.Tune else Icons.Default.CheckCircle,
                        null,
                        Modifier.size(15.dp),
                        tint = if (missingItems.isNotEmpty()) Color(0xFFFFA726) else Color(0xFF10B981)
                    )
                }
                Text(
                    "Setup Health & Missing Details",
                    color = Pf.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (missingItems.isNotEmpty()) {
                Tag("${missingItems.size} Need Attention", Color(0xFFFFA726).copy(alpha = 0.2f), Color(0xFFFFA726))
            }
        }

        if (missingItems.isEmpty()) {
            PfCard(
                modifier = Modifier.fillMaxWidth(),
                padding = PaddingValues(Space.s4),
                shape = Radius.Md
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s3)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Column {
                        Text("All Setup Complete! ✓", color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Muted("All bank digits, card limits, due dates, and loans are fully configured for auto-tracking.", size = 11)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                missingItems.forEach { item ->
                    PfCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.openSetupFix(item) },
                        padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                        shape = Radius.Md
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = Space.s2)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlineTag(item.entityType)
                                    Text(
                                        item.name,
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                item.missingFields.forEach { msg ->
                                    Text(
                                        "• $msg",
                                        color = Color(0xFFFFA726),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.clickable { vm.openSetupFix(item) }
                            ) {
                                Text(
                                    "Fix →",
                                    color = Pf.Accent400,
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

@Composable
fun SetupFixDialog(vm: FinTrackViewModel) {
    val item = vm.setupFixItem ?: return

    Dialog(onDismissRequest = vm::closeSetupFix) {
        PfCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.s2),
            padding = PaddingValues(Space.s4),
            shape = Radius.Lg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(0xFFFFA726).copy(alpha = 0.15f), Radius.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, null, Modifier.size(16.dp), tint = Color(0xFFFFA726))
                        }
                        Column {
                            Text(
                                "Configure ${item.entityType}",
                                color = Pf.Text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                item.name,
                                color = Pf.Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = vm::closeSetupFix, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Pf.Muted)
                    }
                }

                // Missing fields indicators
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item.missingFields.forEach { msg ->
                        Text("• $msg", color = Color(0xFFFFA726), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Hairline()

                // Form fields based on entityType
                when (item.entityType) {
                    "Account" -> {
                        PfField(
                            label = "Account Name",
                            value = vm.accountDraft.name,
                            onValueChange = { vm.accountDraft = vm.accountDraft.copy(name = it) },
                            placeholder = "e.g. HDFC Salary Account"
                        )
                        PfField(
                            label = "Current Balance (₹)",
                            value = vm.accountDraft.balanceText,
                            onValueChange = { vm.accountDraft = vm.accountDraft.copy(balanceText = it) },
                            numeric = true
                        )
                        PfField(
                            label = "Last 3-4 Digits (for SMS auto-sync)",
                            value = vm.accountDraft.numberTail,
                            onValueChange = { vm.accountDraft = vm.accountDraft.copy(numberTail = it) },
                            placeholder = "e.g. 1234",
                            numeric = true
                        )
                    }
                    "Card" -> {
                        PfField(
                            label = "Card Name",
                            value = vm.cardDraft.name,
                            onValueChange = { vm.cardDraft = vm.cardDraft.copy(name = it) }
                        )
                        PfField(
                            label = "Last 4 Digits (for SMS auto-sync)",
                            value = vm.cardDraft.numberTail,
                            onValueChange = { vm.cardDraft = vm.cardDraft.copy(numberTail = it) },
                            placeholder = "e.g. 5678",
                            numeric = true
                        )
                        PfField(
                            label = "Credit Limit (₹)",
                            value = vm.cardDraft.limitText,
                            onValueChange = { vm.cardDraft = vm.cardDraft.copy(limitText = it) },
                            numeric = true
                        )
                        PfField(
                            label = "Current Balance / Due (₹)",
                            value = vm.cardDraft.balanceText,
                            onValueChange = { vm.cardDraft = vm.cardDraft.copy(balanceText = it) },
                            numeric = true
                        )
                        PfField(
                            label = "Payment Due Day of Month (1-31)",
                            value = vm.cardDraft.dueText,
                            onValueChange = { vm.cardDraft = vm.cardDraft.copy(dueText = it) },
                            placeholder = "e.g. 18",
                            numeric = true
                        )
                        PfField(
                            label = "Statement Day of Month (1-31)",
                            value = vm.cardDraft.statementDayText,
                            onValueChange = { vm.cardDraft = vm.cardDraft.copy(statementDayText = it) },
                            placeholder = "e.g. 1",
                            numeric = true
                        )
                    }
                    "Loan" -> {
                        PfField(
                            label = "Loan / EMI Name",
                            value = vm.loanDraft.name,
                            onValueChange = { vm.loanDraft = vm.loanDraft.copy(name = it) }
                        )
                        PfField(
                            label = "Monthly EMI Amount (₹)",
                            value = vm.loanDraft.emiText,
                            onValueChange = { vm.loanDraft = vm.loanDraft.copy(emiText = it) },
                            numeric = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            Box(Modifier.weight(1f)) {
                                PfField(
                                    label = "Total Months",
                                    value = vm.loanDraft.totalMonthsText,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(totalMonthsText = it) },
                                    numeric = true
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                PfField(
                                    label = "Remaining",
                                    value = vm.loanDraft.remainingMonthsText,
                                    onValueChange = { vm.loanDraft = vm.loanDraft.copy(remainingMonthsText = it) },
                                    numeric = true
                                )
                            }
                        }
                        Column {
                            Muted("Payment Account", size = 12)
                            Spacer(Modifier.height(4.dp))
                            PfSelect(
                                value = vm.accounts.firstOrNull { it.id == vm.loanDraft.accountId }?.name.orEmpty().ifEmpty { "Select Account" },
                                options = vm.accounts.map { it.name },
                                onSelect = { selName ->
                                    val selAcc = vm.accounts.firstOrNull { it.name == selName }
                                    vm.loanDraft = vm.loanDraft.copy(accountId = selAcc?.id.orEmpty(), cardId = "")
                                }
                            )
                        }
                    }
                    else -> {
                        PfField(
                            label = "Category / Note",
                            value = vm.draft.category,
                            onValueChange = { vm.draft = vm.draft.copy(category = it) }
                        )
                        PfField(
                            label = "Amount (₹)",
                            value = vm.draft.amountText,
                            onValueChange = { vm.draft = vm.draft.copy(amountText = it) },
                            numeric = true
                        )
                        PfField(
                            label = "Target Date (DD/MM/YYYY)",
                            value = vm.draft.dueText,
                            onValueChange = { vm.draft = vm.draft.copy(dueText = it) },
                            placeholder = "e.g. 15/10/2026"
                        )
                        Column {
                            Muted("Payment Account", size = 12)
                            Spacer(Modifier.height(4.dp))
                            PfSelect(
                                value = vm.accounts.firstOrNull { it.id == vm.draft.accountId }?.name.orEmpty().ifEmpty { "Select Account" },
                                options = vm.accounts.map { it.name },
                                onSelect = { selName ->
                                    val selAcc = vm.accounts.firstOrNull { it.name == selName }
                                    vm.draft = vm.draft.copy(accountId = selAcc?.id.orEmpty())
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Space.s2))

                // Actions
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    SecondaryButton(text = "Cancel", onClick = vm::closeSetupFix, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        text = "Save Details",
                        onClick = {
                            when (item.entityType) {
                                "Account" -> {
                                    vm.saveAccount()
                                    vm.closeSetupFix()
                                }
                                "Card" -> {
                                    vm.saveCard()
                                    vm.closeSetupFix()
                                }
                                "Loan" -> {
                                    vm.saveLoan()
                                    vm.closeSetupFix()
                                }
                                else -> {
                                    vm.saveSetupEntry(item.entityId)
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}


