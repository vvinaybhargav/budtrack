package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.HomeTab
import com.vinay.fintrack.Tab
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.friendlyCycle
import com.vinay.fintrack.data.monthsToDate
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.formatDueDisplay
import com.vinay.fintrack.data.formatInstalmentsLeft
import com.vinay.fintrack.data.ordinal
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.normalizeDateToIso
import com.vinay.fintrack.data.Entry
import com.vinay.fintrack.data.Card
import com.vinay.fintrack.data.Loan
import com.vinay.fintrack.data.Debt
import com.vinay.fintrack.data.DetectedAccountParser

private const val ALERT_PCT = 0.90f

@Composable
fun HomeScreen(vm: FinTrackViewModel) {
    val currentCalMonth = today().split("-").getOrNull(1)?.toIntOrNull() ?: 1
    val nextCalMonth = ((currentCalMonth % 12) + 1)
    val nextCalMonthStr = nextCalMonth.toString()
    val rollingMonthNums = (0..11).map { offset -> ((currentCalMonth - 1 + offset) % 12) + 1 }

    val totalBankBalances = vm.scopedAccounts.sumOf { vm.balanceOf(it) }
    val pendingCards = vm.scopedCards.filter { it.balance > 0.0 }
    val totalCardDues = pendingCards.sumOf { it.balance }

    val pendingLoans = vm.scopedLoans.filter { vm.isLoanActiveThisMonth(it) && !vm.isLoanConfirmed(it.id) }
    val currentPendingLoanEmis = pendingLoans.sumOf { it.monthlyEmi }

    val pendingRecurring = vm.commitments.filter { !vm.isConfirmed(it.id) }
    val currentPendingRecurring = pendingRecurring.sumOf { it.monthly }

    // All unclosed sinking funds
    val allSinkingFunds = vm.annualSetAsides.filter { !it.closed }
    val pendingSetAsidesThisMonth = allSinkingFunds.filter { vm.isSetAsideActiveThisMonth(it) && vm.setAsideLeft(it) > 0.0 }
    val currentPendingSetAside = pendingSetAsidesThisMonth.sumOf { vm.setAsideLeft(it) }

    // Helper: determine target calendar month (1..12) for any sinking fund entry
    fun getSetAsideMonthNum(e: Entry): Int {
        val rawStart = when {
            e.startDate.isNotBlank() -> e.startDate
            else -> vm.firstPaydayOf(e)
        }
        val startIso = normalizeDateToIso(rawStart) ?: rawStart
        val parts = startIso.split("-")
        if (parts.size >= 2) {
            val m = parts[1].toIntOrNull()
            if (m != null && m in 1..12) return m
        }
        val todayParts = today().split("-")
        return todayParts.getOrNull(1)?.toIntOrNull() ?: 1
    }

    // Helper: determine month for credit card
    fun getCardMonthNum(c: Card): Int {
        if (c.nextDue.isNotEmpty()) {
            val m = c.nextDue.split("-").getOrNull(1)?.toIntOrNull()
            if (m != null && m in 1..12) return m
        }
        if (c.dueDate.isNotEmpty()) {
            val iso = normalizeDateToIso(c.dueDate) ?: c.dueDate
            val m = iso.split("-").getOrNull(1)?.toIntOrNull()
            if (m != null && m in 1..12) return m
        }
        val dueTextLower = (c.dueText + " " + c.due).lowercase()
        val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        monthNames.forEachIndexed { index, name ->
            if (dueTextLower.contains(name)) return index + 1
        }
        val todayDay = today().split("-").getOrNull(2)?.toIntOrNull() ?: 1
        if (c.dueDay > 0) {
            return if (todayDay <= c.dueDay) currentCalMonth else ((currentCalMonth % 12) + 1)
        }
        return currentCalMonth
    }

    // Helper: is loan active in target calendar month
    fun isLoanInMonth(l: Loan, targetMonth: Int, offsetFromNow: Int = -1): Boolean {
        if (l.remainingMonths <= 0) return false
        val offset = if (offsetFromNow >= 0) offsetFromNow else ((targetMonth - currentCalMonth + 12) % 12)
        if (offset >= l.remainingMonths) return false
        val resetDay = vm.salaryResetDayFor(l.person)
        if (offset == 0) {
            return l.isStarted(today(), resetDay)
        }
        if (l.isStarted(today(), resetDay)) return true
        if (l.startMonth.isNotEmpty()) {
            val parts = l.startMonth.split("-")
            val sYear = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val sMonth = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val curYear = today().split("-").getOrNull(0)?.toIntOrNull() ?: 2026
            val targetYear = if (targetMonth < currentCalMonth) curYear + 1 else curYear
            return (targetYear > sYear) || (targetYear == sYear && targetMonth >= sMonth)
        }
        return true
    }

    // Debts & helper: determine month for debt (lent / borrow)
    val pendingDebts = vm.pendingDebts
    fun getDebtMonthNum(d: Debt): Int {
        if (d.dueDate.isNotBlank()) {
            val iso = normalizeDateToIso(d.dueDate) ?: d.dueDate
            val parts = iso.split("-")
            if (parts.size >= 2) {
                val m = parts[1].toIntOrNull()
                if (m != null && m in 1..12) return m
            }
            val textLower = d.dueDate.lowercase()
            val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            monthNames.forEachIndexed { index, name ->
                if (textLower.contains(name)) return index + 1
            }
        }
        if (d.createdAt > 0L) {
            val createdIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(d.createdAt))
            val m = createdIso.split("-").getOrNull(1)?.toIntOrNull()
            if (m != null && m in 1..12) return m
        }
        return nextCalMonth
    }

    // Top Card Calculations:
    // 1. Current Month Dues: card balances + current unconfirmed recurring bills + current unconfirmed loan EMIs + current active sinking fund shares
    val currentMonthDues = totalCardDues + currentPendingRecurring + currentPendingLoanEmis + currentPendingSetAside

    // 2. Next Month Expenses: loan EMIs for next month + recurring commitments for next month + monthly sinking funds
    val nextMonthLoanEmis = vm.scopedLoans.filter { isLoanInMonth(it, nextCalMonth, 1) }.sumOf { it.monthlyEmi }
    val nextMonthRecurring = vm.commitments.sumOf { it.monthly }
    val nextMonthSinkingFunds = allSinkingFunds.sumOf { it.monthly(vm.salaryResetDayFor(it.person)) }
    val nextMonthExpenses = nextMonthLoanEmis + nextMonthRecurring + nextMonthSinkingFunds

    // 3. Next Month Salary
    val nextMonthSalary = if (vm.bucketView == "JOINT") {
        vm.profileNames.sumOf { vm.upcomingSalaryFor(it, 1) }
    } else {
        vm.upcomingSalaryFor(vm.activeProfile.orEmpty(), 1)
    }

    // 4. Next Month Lent & Borrow
    val nextMonthLent = pendingDebts.filter { it.isLent && (getDebtMonthNum(it) == nextCalMonth || it.dueDate.isBlank()) }.sumOf { it.remainingAmount }
    val nextMonthBorrowed = pendingDebts.filter { it.isBorrowed && (getDebtMonthNum(it) == nextCalMonth || it.dueDate.isBlank()) }.sumOf { it.remainingAmount }
    val nextMonthNetDebt = nextMonthLent - nextMonthBorrowed

    // Net Balance = Bank Balances - Current Month Dues - Next Month Expenses + Next Month Salary + Next Month Net Debt
    val netBalance = totalBankBalances - currentMonthDues - nextMonthExpenses + nextMonthSalary + nextMonthNetDebt
    val totalHeroExpenses = currentMonthDues + nextMonthExpenses

    // Filter states (all defaulting to NEXT CALENDAR MONTH)
    var sinkingFundFilter by remember { mutableStateOf(nextCalMonthStr) }
    var cardFilter by remember { mutableStateOf(nextCalMonthStr) }
    var loanFilter by remember { mutableStateOf(nextCalMonthStr) }
    var debtFilter by remember { mutableStateOf(nextCalMonthStr) }

    var expandedBanks by remember { mutableStateOf(false) }
    var expandedPast by remember { mutableStateOf(false) }
    var expandedSinkingFunds by remember { mutableStateOf(false) }
    var expandedDebts by remember { mutableStateOf(false) }
    var expandedCards by remember { mutableStateOf(false) }
    var expandedRecurring by remember { mutableStateOf(false) }
    var expandedLoans by remember { mutableStateOf(false) }

    // Sinking Fund Pills
    val sinkingPills = remember(allSinkingFunds, currentCalMonth) {
        buildList {
            add(Triple("ALL", "All", allSinkingFunds.size))
            rollingMonthNums.forEach { m ->
                val count = allSinkingFunds.count { getSetAsideMonthNum(it) == m }
                if (m == nextCalMonth || count > 0) {
                    val monthName = Ledger.fullMonthName("2026-%02d-01".format(m)).take(3)
                    add(Triple(m.toString(), monthName, count))
                }
            }
        }
    }
    LaunchedEffect(sinkingPills) {
        if (sinkingPills.none { it.first == sinkingFundFilter }) {
            sinkingFundFilter = if (sinkingPills.any { it.first == nextCalMonthStr }) nextCalMonthStr else "ALL"
        }
    }

    // Card Pills
    val cardPills = remember(pendingCards, currentCalMonth) {
        buildList {
            add(Triple("ALL", "All", pendingCards.size))
            rollingMonthNums.forEach { m ->
                val count = pendingCards.count { getCardMonthNum(it) == m }
                if (m == nextCalMonth || count > 0) {
                    val monthName = Ledger.fullMonthName("2026-%02d-01".format(m)).take(3)
                    add(Triple(m.toString(), monthName, count))
                }
            }
        }
    }
    LaunchedEffect(cardPills) {
        if (cardPills.none { it.first == cardFilter }) {
            cardFilter = if (cardPills.any { it.first == nextCalMonthStr }) nextCalMonthStr else "ALL"
        }
    }

    // Loan Pills
    val loanPills = remember(pendingLoans, currentCalMonth) {
        buildList {
            add(Triple("ALL", "All", pendingLoans.size))
            rollingMonthNums.forEach { m ->
                val count = pendingLoans.count { isLoanInMonth(it, m) }
                if (m == nextCalMonth || count > 0) {
                    val monthName = Ledger.fullMonthName("2026-%02d-01".format(m)).take(3)
                    add(Triple(m.toString(), monthName, count))
                }
            }
        }
    }
    LaunchedEffect(loanPills) {
        if (loanPills.none { it.first == loanFilter }) {
            loanFilter = if (loanPills.any { it.first == nextCalMonthStr }) nextCalMonthStr else "ALL"
        }
    }

    // Debt Pills
    val debtPills = remember(pendingDebts, currentCalMonth) {
        buildList {
            add(Triple("ALL", "All", pendingDebts.size))
            rollingMonthNums.forEach { m ->
                val count = pendingDebts.count { getDebtMonthNum(it) == m || (it.dueDate.isBlank() && m == nextCalMonth) }
                if (m == nextCalMonth || count > 0) {
                    val monthName = Ledger.fullMonthName("2026-%02d-01".format(m)).take(3)
                    add(Triple(m.toString(), monthName, count))
                }
            }
        }
    }
    LaunchedEffect(debtPills) {
        if (debtPills.none { it.first == debtFilter }) {
            debtFilter = if (debtPills.any { it.first == nextCalMonthStr }) nextCalMonthStr else "ALL"
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 90.dp, top = Space.s2, start = Space.s4, end = Space.s4),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Scope Switch (Personal / Joint ledger toggle) + billing cycle
        item { ScopeSwitch(vm) }

        // Alert if SMS transactions need account link
        item { UnmatchedAccountAlert(vm) }

        // 2. HERO CARD: Net Balance (Bank Balances - Current Month Dues - Next Month Expenses + Next Month Salary + Next Month Net Debt)
        item {
            AfterAllExpensesCard(
                netBalance = netBalance,
                bankBalances = totalBankBalances,
                otherExpenses = totalHeroExpenses,
                upcomingSalary = nextMonthSalary,
                lentToReceive = nextMonthNetDebt,
                balanceHidden = vm.balanceHidden,
                onToggleVisibility = vm::toggleBalanceVisible,
                onBankBalancesClick = {
                    expandedBanks = !expandedBanks
                },
                onExpensesClick = {
                    expandedCards = true
                    expandedLoans = true
                    expandedRecurring = true
                }
            )
        }

        // 2b. SPENT TODAY BADGE
        item {
            SpentTodayBadge(vm)
        }

        // 2c. BANK ACCOUNTS CARD
        val bankAccounts = vm.scopedAccounts
        if (bankAccounts.isNotEmpty()) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expandedBanks = !expandedBanks },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f), Radius.Sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountBalance, null, Modifier.size(16.dp), tint = Color(0xFF3B82F6))
                            }
                            Text(
                                "BANK ACCOUNTS · ${inr(totalBankBalances)}",
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    vm.selectAddKind("BANK_ACCOUNT")
                                    vm.tab = Tab.ADD
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add Bank", tint = Pf.Muted, modifier = Modifier.size(18.dp))
                            }
                            Icon(
                                if (expandedBanks) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                "Expand",
                                tint = Pf.Muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    val visibleAccounts = if (expandedBanks) bankAccounts else bankAccounts.take(3)
                    visibleAccounts.forEachIndexed { idx, a ->
                        Hairline()
                        val bal = vm.balanceOf(a)
                        val isNegative = bal < 0.0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.editAccountById(a.id) }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .background(Pf.Surface2, CircleShape)
                                        .border(1.dp, Pf.Hairline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        a.name.take(1).uppercase(),
                                        color = Pf.Text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            a.name,
                                            color = Pf.Text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (a.person == "Joint") {
                                            Spacer(Modifier.width(6.dp))
                                            Tag("Joint", Pf.Surface2, Pf.Muted)
                                        }
                                    }
                                    val subtitle = if (a.numberTail.isNotBlank()) "••••${a.numberTail}" else "Bank Account"
                                    Text(subtitle, color = Pf.Muted, fontSize = 11.5.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    inr(bal),
                                    color = if (isNegative) Color(0xFFEF4444) else Pf.Text,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.Edit, "Edit", tint = Pf.Muted.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    if (bankAccounts.size > 3) {
                        ExpandCollapseButton(expandedBanks, bankAccounts.size - 3) {
                            expandedBanks = !expandedBanks
                        }
                    }
                }
            }
        }

        // 3a. CONSOLIDATED SINKING FUNDS CARD (Combines SET ASIDE + Future Months into 1 Card with Filter Pills)
        val hasSinkingFunds = allSinkingFunds.isNotEmpty()
        if (hasSinkingFunds) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    val filteredSinkingFunds = if (sinkingFundFilter == "ALL") {
                        allSinkingFunds.sortedBy { e ->
                            val m = getSetAsideMonthNum(e)
                            (m - currentCalMonth + 12) % 12
                        }
                    } else {
                        val filterM = sinkingFundFilter.toIntOrNull()
                        allSinkingFunds.filter { getSetAsideMonthNum(it) == filterM }
                    }

                    val totalItemCount = filteredSinkingFunds.size
                    val activePendingInSelection = filteredSinkingFunds.filter { vm.isSetAsideActiveThisMonth(it) }.sumOf { vm.setAsideLeft(it) }
                    val monthlySumInSelection = filteredSinkingFunds.sumOf { it.monthly(vm.salaryResetDayFor(it.person)) }
                    val headerAmount = if (activePendingInSelection > 0.0) "(${inr(activePendingInSelection)})" else "${inr(monthlySumInSelection)}/mo"

                    HomeListHeaderLabel(
                        label = "SINKING FUNDS · $headerAmount",
                        icon = Icons.Default.Bookmark,
                        iconTint = Color(0xFF14B8A6)
                    ) {
                        expandedSinkingFunds = !expandedSinkingFunds
                    }

                    // Horizontal Month Filter Pills
                    if (sinkingPills.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sinkingPills.forEach { (id, label, count) ->
                                val isSelected = sinkingFundFilter == id
                                Box(
                                    Modifier
                                        .clip(Radius.Pill)
                                        .background(if (isSelected) Color(0xFF14B8A6) else Pf.Surface2)
                                        .clickable {
                                            sinkingFundFilter = id
                                            expandedSinkingFunds = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        "$label ($count)",
                                        color = if (isSelected) Color.Black else Pf.Text,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    val maxDisplay = if (expandedSinkingFunds) totalItemCount else 3
                    var displayedCount = 0

                    filteredSinkingFunds.forEach { e ->
                        if (displayedCount < maxDisplay) {
                            if (displayedCount > 0) Hairline()
                            val resetDay = vm.salaryResetDayFor(e.person)
                            val isActiveThisMonth = vm.isSetAsideActiveThisMonth(e)
                            val pot = vm.setAsidePot(e)

                            if (isActiveThisMonth && pot > 0.0) {
                                val left = vm.setAsideLeft(e)
                                val fraction = safeFraction(pot, e.amount)
                                val pct = (fraction * 100).toInt()
                                val n = Ledger.instalmentsUntil(today(), e.nextDue, resetDay)
                                val subtitle = "${inr(pot)}/${inr(e.amount)} ($pct%) · Due ${ordinal(resetDay)} · ${formatInstalmentsLeft(n)}"

                                HomeCompactSetAsideRow(
                                    index = displayedCount + 1,
                                    title = (e.note.ifEmpty { e.category }) + if (e.formula.isNotEmpty()) " · ${e.formula}" else "",
                                    subtitle = subtitle,
                                    amount = "(${inr(left)})",
                                    fraction = fraction,
                                    pct = pct,
                                    accentColor = Color(0xFF14B8A6),
                                    onClick = { vm.requestConfirm(e) }
                                )
                            } else {
                                val rawStart = if (e.startDate.isNotEmpty()) e.startDate else vm.firstPaydayOf(e)
                                val start = normalizeDateToIso(rawStart) ?: rawStart
                                val n = Ledger.instalmentsBetween(start, e.nextDue, resetDay)
                                val monthlyAmt = e.monthly(resetDay)
                                val monthNum = getSetAsideMonthNum(e)
                                val monthTitle = Ledger.fullMonthName("2026-%02d-01".format(monthNum))
                                val subtitle = "Starts in $monthTitle · Due ${ordinal(resetDay)} · ${formatInstalmentsLeft(n)} · Total: ${inr(e.amount)}"

                                HomeCompactRow(
                                    title = "${displayedCount + 1}. ${e.note.ifEmpty { e.category }}",
                                    subtitle = subtitle,
                                    amount = inr(monthlyAmt),
                                    amountColor = Pf.Muted,
                                    icon = Icons.Default.Bookmark,
                                    iconTint = Color(0xFF14B8A6),
                                    onClick = { vm.openEditEntry(e) }
                                )
                            }
                            displayedCount++
                        }
                    }

                    // Progressive Disclosure Toggle
                    if (totalItemCount > 3) {
                        ExpandCollapseButton(expandedSinkingFunds, totalItemCount - 3) {
                            expandedSinkingFunds = !expandedSinkingFunds
                        }
                    }
                }
            }
        }


        // 3c. LENT & BORROW (DEBTS) ELEVATED CARD
        if (pendingDebts.isNotEmpty()) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    val filteredDebts = if (debtFilter == "ALL") {
                        pendingDebts
                    } else {
                        val filterM = debtFilter.toIntOrNull()
                        pendingDebts.filter { getDebtMonthNum(it) == filterM || (it.dueDate.isBlank() && filterM == nextCalMonth) }
                    }
                    val totalDebtCount = filteredDebts.size

                    val net = vm.netDebt
                    val netLabel = if (net >= 0) "Net +${inr(net)}" else "Net -${inr(-net)}"
                    HomeListHeaderLabel(
                        label = "LENT & BORROW · $netLabel",
                        icon = Icons.Default.SwapHoriz,
                        iconTint = Pf.Accent400
                    ) {
                        expandedDebts = !expandedDebts
                    }

                    // Horizontal Month Filter Pills
                    if (debtPills.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            debtPills.forEach { (id, label, count) ->
                                val isSelected = debtFilter == id
                                Box(
                                    Modifier
                                        .clip(Radius.Pill)
                                        .background(if (isSelected) Pf.Accent400 else Pf.Surface2)
                                        .clickable {
                                            debtFilter = id
                                            expandedDebts = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        "$label ($count)",
                                        color = if (isSelected) Color.Black else Pf.Text,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    val visibleDebts = if (expandedDebts) filteredDebts else filteredDebts.take(3)
                    visibleDebts.forEachIndexed { idx, d ->
                        if (idx > 0) Hairline()
                        val isLent = d.isLent
                        val tagColor = if (isLent) Color(0xFF00BFA5) else Color(0xFFFF5252)
                        val dueSubtitle = buildString {
                            append(if (isLent) "Lent to " else "Borrowed from ")
                            append(d.peerName)
                            if (d.dueDate.isNotEmpty()) {
                                append(" · ${formatDueDisplay(d.dueDate)}")
                            } else if (d.note.isNotEmpty()) {
                                append(" · ${d.note}")
                            }
                        }

                        HomeCompactDebtRow(
                            index = idx + 1,
                            title = d.peerName,
                            type = if (isLent) "LENT" else "BORROWED",
                            typeColor = tagColor,
                            subtitle = dueSubtitle,
                            amount = inr(d.remainingAmount),
                            onClick = { vm.openDebtAction(d) }
                        )
                    }

                    if (totalDebtCount > 3) {
                        ExpandCollapseButton(expandedDebts, totalDebtCount - 3) {
                            expandedDebts = !expandedDebts
                        }
                    }
                }
            }
        }

        // 3d. CREDIT CARDS ELEVATED CARD
        if (pendingCards.isNotEmpty()) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    val filteredCards = if (cardFilter == "ALL") {
                        pendingCards
                    } else {
                        val filterM = cardFilter.toIntOrNull()
                        pendingCards.filter { getCardMonthNum(it) == filterM }
                    }
                    val totalCardFilterDues = filteredCards.sumOf { it.balance }
                    val totalCardCount = filteredCards.size

                    HomeListHeaderLabel(
                        label = "CREDIT CARDS · ${inr(totalCardFilterDues)}",
                        icon = Icons.Default.CreditCard,
                        iconTint = Color(0xFFF59E0B)
                    ) {
                        expandedCards = !expandedCards
                    }

                    // Horizontal Month Filter Pills
                    if (cardPills.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            cardPills.forEach { (id, label, count) ->
                                val isSelected = cardFilter == id
                                Box(
                                    Modifier
                                        .clip(Radius.Pill)
                                        .background(if (isSelected) Color(0xFFF59E0B) else Pf.Surface2)
                                        .clickable {
                                            cardFilter = id
                                            expandedCards = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        "$label ($count)",
                                        color = if (isSelected) Color.Black else Pf.Text,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    val visibleCards = if (expandedCards) filteredCards else filteredCards.take(3)
                    visibleCards.forEachIndexed { idx, c ->
                        if (idx > 0) Hairline()
                        val cleanName = if (c.name.contains("••") && c.numberTail.isNotBlank()) {
                            c.name.substringBefore("••").trim().ifEmpty { c.name }
                        } else c.name
                        val duePart = if (c.dueText.isNotBlank()) formatDueDisplay(c.dueText) else null
                        val tailPart = if (c.numberTail.isNotBlank()) "••••${c.numberTail}" else null
                        val ownerPart = if (c.owner == "Joint") "Joint" else null
                        val limitPart = if (c.limit > 0.0) "Limit: ${inr(c.limit)}" else null
                        val subtitle = listOfNotNull(duePart, tailPart, limitPart, ownerPart).joinToString(" · ").ifEmpty { "Credit Card" }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1f).padding(end = Space.s2).clickable { vm.editCardById(c.id) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.14f), Radius.Sm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CreditCard, null, Modifier.size(15.dp), tint = Color(0xFFF59E0B))
                                }
                                Column {
                                    Text(
                                        "${idx + 1}. $cleanName",
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        subtitle,
                                        color = Pf.Muted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    inr(c.balance),
                                    color = Pf.Text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    Modifier
                                        .clip(Radius.Pill)
                                        .background(Pf.Surface2)
                                        .clickable { vm.startSettleCard(c.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Settle", color = Pf.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { vm.editCardById(c.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit Card", tint = Pf.Muted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    if (totalCardCount > 3) {
                        ExpandCollapseButton(expandedCards, totalCardCount - 3) {
                            expandedCards = !expandedCards
                        }
                    }
                }
            }
        }

        // 3e. RECURRING BILLS ELEVATED CARD
        if (pendingRecurring.isNotEmpty()) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    HomeListHeaderLabel(
                        label = "RECURRING · ${inr(totalRecurring)}/mo",
                        icon = Icons.Default.DateRange,
                        iconTint = Color(0xFF8B5CF6)
                    ) {
                        expandedRecurring = !expandedRecurring
                    }

                    val visibleRecurring = if (expandedRecurring) pendingRecurring else pendingRecurring.take(3)
                    visibleRecurring.forEachIndexed { idx, e ->
                        if (idx > 0) Hairline()
                        val duePart = if (e.nextDue.isNotEmpty()) formatDueDisplay(e.nextDue) else null
                        val subtitle = listOfNotNull(e.person, e.category, duePart).joinToString(" · ")
                        HomeCompactRow(
                            title = "${idx + 1}. ${e.note.ifEmpty { e.category }}",
                            subtitle = subtitle,
                            amount = inr(e.monthly),
                            amountColor = Pf.Text,
                            icon = Icons.Default.DateRange,
                            iconTint = Color(0xFF8B5CF6),
                            onClick = { vm.requestConfirm(e) }
                        )
                    }

                    if (pendingRecurring.size > 3) {
                        ExpandCollapseButton(expandedRecurring, pendingRecurring.size - 3) {
                            expandedRecurring = !expandedRecurring
                        }
                    }
                }
            }
        }

        // 3f. LOANS (EMIS) ELEVATED CARD
        if (pendingLoans.isNotEmpty()) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    val filteredLoans = if (loanFilter == "ALL") {
                        pendingLoans
                    } else {
                        val filterM = loanFilter.toIntOrNull() ?: nextCalMonth
                        pendingLoans.filter { isLoanInMonth(it, filterM) }
                    }
                    val totalLoanFilterEmis = filteredLoans.sumOf { it.monthlyEmi }
                    val totalLoanCount = filteredLoans.size

                    HomeListHeaderLabel(
                        label = "LOANS · ${inr(totalLoanFilterEmis)}/mo",
                        icon = Icons.Default.AccountBalance,
                        iconTint = Color(0xFF3B82F6)
                    ) {
                        expandedLoans = !expandedLoans
                    }

                    // Horizontal Month Filter Pills
                    if (loanPills.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            loanPills.forEach { (id, label, count) ->
                                val isSelected = loanFilter == id
                                Box(
                                    Modifier
                                        .clip(Radius.Pill)
                                        .background(if (isSelected) Color(0xFF3B82F6) else Pf.Surface2)
                                        .clickable {
                                            loanFilter = id
                                            expandedLoans = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        "$label ($count)",
                                        color = if (isSelected) Color.White else Pf.Text,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    val visibleLoans = if (expandedLoans) filteredLoans else filteredLoans.take(3)
                    visibleLoans.forEachIndexed { idx, l ->
                        if (idx > 0) Hairline()
                        val duePart = if (l.dueDay > 0) "Due ${ordinal(l.dueDay)}" else if (l.nextDue.isNotEmpty()) formatDueDisplay(l.nextDue) else ""
                        val subtitle = "${formatInstalmentsLeft(l.remainingMonths)} · EMI ${inr(l.monthlyEmi)}${if (duePart.isNotEmpty()) " · $duePart" else ""}"
                        HomeCompactRow(
                            title = "${idx + 1}. ${l.name}",
                            subtitle = subtitle,
                            amount = inr(l.monthlyEmi),
                            amountColor = Pf.Text,
                            icon = Icons.Default.AccountBalance,
                            iconTint = Color(0xFF3B82F6),
                            onClick = { vm.startConfirmLoan(l) }
                        )
                    }

                    if (totalLoanCount > 3) {
                        ExpandCollapseButton(expandedLoans, totalLoanCount - 3) {
                            expandedLoans = !expandedLoans
                        }
                    }
                }
            }
        }

        // 3g. ALL CLEAR EMPTY STATE
        val hasAnyCommitments = hasSinkingFunds ||
            pendingDebts.isNotEmpty() ||
            pendingCards.isNotEmpty() ||
            pendingRecurring.isNotEmpty() ||
            pendingLoans.isNotEmpty()

        if (!hasAnyCommitments) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(vertical = Space.s4, horizontal = Space.s4),
                    shape = Radius.Lg
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "All dues, bills, and set-asides for this cycle are clear! 🎉",
                            color = Pf.Muted,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3h. PAST & SETTLED ITEMS (Expandable)
        val clearedLoans = vm.scopedLoans.filter { vm.isLoanCleared(it) }
        val closedEntries = vm.closedEntries
        val pastDebts = vm.pastClosedDebts
        val totalPastCount = clearedLoans.size + closedEntries.size + pastDebts.size

        if (totalPastCount > 0) {
            item {
                PfCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                    shape = Radius.Lg
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expandedPast = !expandedPast },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f), Radius.Sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF10B981))
                            }
                            Text(
                                "PAST & SETTLED · $totalPastCount items",
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }

                        Icon(
                            if (expandedPast) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            "Expand",
                            tint = Pf.Muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (expandedPast) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = Space.s2),
                            verticalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            clearedLoans.forEach { l ->
                                Hairline()
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(l.name, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Cleared Loan · EMI ${inr(l.monthlyEmi)}", color = Pf.Muted, fontSize = 11.5.sp)
                                    }
                                    Tag("Cleared", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                                }
                            }

                            closedEntries.forEach { e ->
                                Hairline()
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.note.ifEmpty { e.category }, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        val sub = if (e.isSetAside) "Completed Set Aside · Total ${inr(e.amount)}" else "Closed Bill · ${inr(e.amount)}/mo"
                                        Text(sub, color = Pf.Muted, fontSize = 11.5.sp)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Tag("Closed", Pf.Muted.copy(alpha = 0.15f), Pf.Muted)
                                        SecondaryButton("Reopen", { vm.closeEntry(e.id, false) })
                                    }
                                }
                            }

                            pastDebts.forEach { d ->
                                Hairline()
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.peerName, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Settled · ${if (d.isLent) "Lent" else "Borrowed"} ${inr(d.amount)}", color = Pf.Muted, fontSize = 11.5.sp)
                                    }
                                    Tag("Settled", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. MORE HOME FEATURES (Category Spending Breakdown Till Payday)
        item {
            MoreHomeFeaturesSection(vm)
        }

        // 5. UPCOMING MONTHS 2 & 3 FORECAST (Cash Flow Projection)
        item {
            UpcomingMonths2And3Section(vm, netBalance)
        }
    }

    ConfirmSheet(vm)
    CardSettleSheet(vm)
    LoanConfirmSheet(vm)
    InitialProfileDialog(vm)
    AddDebtDialog(vm)
    DebtActionSheet(vm)
}

@Composable
private fun SpentTodayBadge(vm: FinTrackViewModel) {
    val spent = vm.todaySpent
    val count = vm.todayTxnCount

    Box(
        Modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(Pf.Surface)
            .border(1.dp, Pf.Hairline, Radius.Md)
            .clickable { vm.tab = Tab.ENTRIES }
            .padding(horizontal = Space.s4, vertical = Space.s3)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Pf.Accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Pf.Accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        "Today's Spends",
                        color = Pf.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (count > 0) "${inr(spent)} (${count} txn${if (count > 1) "s" else ""})" else "₹0 (0 txns)",
                        color = Pf.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "View",
                    color = Pf.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("→", color = Pf.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExpandCollapseButton(
    expanded: Boolean,
    remainingCount: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.Sm)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            null,
            Modifier.size(16.dp),
            tint = Pf.Accent400
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (expanded) "Show less" else "Show $remainingCount more",
            color = Pf.Accent400,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HomeListHeaderLabel(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = Pf.Accent400,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = iconTint)
            }
            Text(
                text = label,
                color = if (icon != null) iconTint else Pf.Accent400,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onClick != null) {
            Text(
                text = "View all →",
                color = Pf.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                softWrap = false,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun HomeSectionDivider() {
    Spacer(Modifier.height(Space.s2))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                if (Pf.isDark) Color(0xFF262833)
                else Color(0xFFCBD5E1)
            )
    )
    Spacer(Modifier.height(Space.s1))
}

@Composable
private fun HomeCompactRow(
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = Pf.Accent400,
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).padding(end = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(iconTint.copy(alpha = 0.14f), Radius.Sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            null,
                            Modifier.size(15.dp),
                            tint = iconTint
                        )
                    }
                }
                Text(
                    title,
                    color = Pf.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                amount,
                color = amountColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = Pf.Muted,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (icon != null) Modifier.padding(start = 36.dp) else Modifier
            )
        }
    }
}

@Composable
private fun HomeCompactSetAsideRow(
    index: Int,
    title: String,
    subtitle: String,
    amount: String,
    fraction: Float,
    pct: Int,
    accentColor: Color = Color(0xFF14B8A6),
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).padding(end = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(accentColor.copy(alpha = 0.14f), Radius.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        null,
                        Modifier.size(15.dp),
                        tint = accentColor
                    )
                }
                Text(
                    "$index. $title",
                    color = Pf.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    amount,
                    color = Pf.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "this mo",
                    color = Pf.Muted,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            color = Pf.Muted,
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 36.dp)
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(
            fraction = fraction,
            color = if (pct >= 100) Color(0xFF00BFA5) else accentColor,
            height = 4,
            modifier = Modifier.fillMaxWidth().padding(start = 36.dp)
        )
    }
}

@Composable
private fun HomeCompactDebtRow(
    index: Int,
    title: String,
    type: String,
    typeColor: Color,
    subtitle: String,
    amount: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).padding(end = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(typeColor.copy(alpha = 0.14f), Radius.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        null,
                        Modifier.size(15.dp),
                        tint = typeColor
                    )
                }
                Text(
                    "$index. $title",
                    color = Pf.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Tag(
                    type,
                    typeColor.copy(alpha = 0.15f),
                    typeColor
                )
            }
            Text(
                amount,
                color = typeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = Pf.Muted,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 36.dp)
            )
        }
    }
}

@Composable
private fun AfterAllExpensesCard(
    netBalance: Double,
    bankBalances: Double,
    otherExpenses: Double,
    upcomingSalary: Double = 0.0,
    lentToReceive: Double = 0.0,
    balanceHidden: Boolean,
    onToggleVisibility: () -> Unit,
    onBankBalancesClick: (() -> Unit)? = null,
    onExpensesClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (Pf.isDark) Brush.linearGradient(listOf(Color(0xFF1E1B2E), Color(0xFF13111C)))
                else Brush.linearGradient(listOf(Color(0xFF1F2937), Color(0xFF111827))),
                Radius.Lg
            )
            .border(1.dp, if (Pf.isDark) Color(0xFF262833) else Pf.Hairline, Radius.Lg)
            .padding(horizontal = Space.s4, vertical = Space.s4)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ESTIMATED NET (AFTER EXPENSES & SALARY)",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (balanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    "Toggle balance visibility",
                    Modifier.size(16.dp),
                    tint = Color(0xFFE5E7EB)
                )
            }
        }

        Text(
            if (balanceHidden) "••••••" else inr(netBalance),
            Modifier.padding(top = 4.dp, bottom = 10.dp),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Clean 2x2 Data Grid (Category icons, dedicated width, never truncates!)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), Radius.Md)
                .padding(horizontal = Space.s3, vertical = Space.s3),
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            // Row 1: Bank Balances & Current Expenses
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                // Bank Balances (Left)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(Radius.Sm)
                        .then(if (onBankBalancesClick != null) Modifier.clickable { onBankBalancesClick() } else Modifier)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("🏦", fontSize = 11.sp)
                        Text(
                            "Bank Balances ↗",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (balanceHidden) "••••••" else inr(bankBalances),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Expenses (Right)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(Radius.Sm)
                        .then(if (onExpensesClick != null) Modifier.clickable { onExpensesClick() } else Modifier)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("💳", fontSize = 11.sp)
                        Text(
                            "Dues & Next Exp ↗",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (balanceHidden) "••••••" else "(${inr(otherExpenses)})",
                        color = Color(0xFFF87171),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            // Row 2: Expected Salary & Lent Return
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                // Expected Salary (Left)
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("💰", fontSize = 11.sp)
                        Text(
                            "Next Salary",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (balanceHidden) "••••••" else if (upcomingSalary > 0) "+${inr(upcomingSalary)}" else "Not set",
                        color = if (upcomingSalary > 0) Color(0xFF10B981) else Color(0xFF9CA3AF),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Lent to Receive / Borrow Repayment (Right)
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("🤝", fontSize = 11.sp)
                        Text(
                            "Next Lent/Borrow",
                            color = if (lentToReceive >= 0) Pf.Amber else Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    val lentText = when {
                        lentToReceive > 0 -> "+${inr(lentToReceive)}"
                        lentToReceive < 0 -> "-${inr(-lentToReceive)}"
                        else -> "₹0.00"
                    }
                    Text(
                        if (balanceHidden) "••••••" else lentText,
                        color = when {
                            lentToReceive > 0 -> Pf.Amber
                            lentToReceive < 0 -> Color(0xFFF87171)
                            else -> Color(0xFF9CA3AF)
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingMonths2And3Section(vm: FinTrackViewModel, startingLeftover: Double = 0.0) {
    val outlookMonths = vm.outlook(2..3, startingLeftover)
    if (outlookMonths.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.s2),
        verticalArrangement = Arrangement.spacedBy(Space.s3)
    ) {
        Column {
            Text(
                "Upcoming Months 2 & 3 Outlook",
                color = Pf.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Muted("Projected cash flow carried forward into Month 2 and Month 3", size = 11)
        }

        outlookMonths.forEachIndexed { idx, month ->
            val monthNumber = idx + 2
            PfCard(
                modifier = Modifier.fillMaxWidth(),
                padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
                shape = Radius.Lg
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    // Header: Month label + Net Leftover
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .background(Pf.Accent.copy(alpha = 0.15f), Radius.Pill)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Month $monthNumber",
                                        color = Pf.Accent400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    month.label,
                                    color = Pf.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (month.loanEnding.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "🎉 ${month.loanEnding} ends this month!",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val isIncomeUnset = month.income <= 0.0
                            if (isIncomeUnset && month.left < 0) {
                                Box(
                                    Modifier
                                        .clip(Radius.Pill)
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.16f))
                                        .clickable { vm.tab = Tab.SETTINGS }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "Set salary to calculate ↗",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Muted("Salary unset", size = 10)
                            } else {
                                val isSurplus = month.left >= 0.0
                                Text(
                                    if (vm.balanceHidden) "••••••" else if (isSurplus) "+${inr(month.left)}" else "-${inr(Math.abs(month.left))}",
                                    color = if (isSurplus) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Muted("Net Projected Leftover", size = 10)
                            }
                        }
                    }

                    Hairline()

                    // 1. Carried over from preceding month
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(if (month.openingBalance >= 0) Pf.Accent else Color(0xFFEF4444), CircleShape))
                            Muted(if (idx == 0) "Carried from Month 1 (Net)" else "Carried from Month 2", size = 12)
                        }
                        Text(
                            if (vm.balanceHidden) "••••••" else if (month.openingBalance >= 0) "+${inr(month.openingBalance)}" else "-${inr(Math.abs(month.openingBalance))}",
                            color = if (month.openingBalance >= 0) Pf.Accent else Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 2. Expected Salary
                    val isIncomeUnset = month.income <= 0.0
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                            Muted("Expected Salary", size = 12)
                        }
                        if (isIncomeUnset) {
                            Text(
                                "+₹0.00 (Tap to set ↗)",
                                color = Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { vm.tab = Tab.SETTINGS }
                            )
                        } else {
                            Text(
                                if (vm.balanceHidden) "••••••" else "+${inr(month.income)}",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (isIncomeUnset && month.left < 0) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF59E0B).copy(alpha = 0.08f), Radius.Sm)
                                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f), Radius.Sm)
                                .padding(horizontal = Space.s3, vertical = Space.s2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("💡", fontSize = 12.sp)
                            Text(
                                "Expected salary for ${month.label} defaults to ₹0. Set anticipated salary in Settings to calculate your true projected surplus.",
                                color = Pf.Muted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    if (month.loans > 0.0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(Color(0xFFFFA726), CircleShape))
                                Muted("Active Loans / EMIs", size = 12)
                            }
                            Text(
                                if (vm.balanceHidden) "••••••" else "−${inr(month.loans)}",
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (month.setAside > 0.0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(Pf.Accent400, CircleShape))
                                Muted("Set Asides (Sinking Funds)", size = 12)
                            }
                            Text(
                                if (vm.balanceHidden) "••••••" else "−${inr(month.setAside)}",
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (month.recurring > 0.0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(Pf.Muted, CircleShape))
                                Muted("Recurring Bills", size = 12)
                            }
                            Text(
                                if (vm.balanceHidden) "••••••" else "−${inr(month.recurring)}",
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Total Outflows Footer
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Pf.Surface2, Radius.Sm)
                            .padding(horizontal = Space.s2, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Muted("Total Fixed Commitments", size = 11)
                        Text(
                            if (vm.balanceHidden) "••••••" else inr(month.out),
                            color = Pf.Text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreHomeFeaturesSection(vm: FinTrackViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val spends = vm.currentCycleCategorySpend
    val totalSpend = vm.currentCycleTotalSpend
    val resetDay = vm.salaryResetDayFor(vm.activeProfile.orEmpty())

    PfCard(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3),
        shape = Radius.Lg
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.Sm)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
                        Text(
                            "Categories Spent (Till Payday)",
                            color = Pf.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            Modifier
                                .background(Pf.Accent.copy(alpha = 0.12f), Radius.Pill)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Till ${resetDay}th",
                                color = Pf.Accent400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (totalSpend > 0.0) "Total: ${inr(totalSpend)} across ${spends.size} categories"
                        else "Tap to view spending breakdown till payday",
                        color = Pf.Muted,
                        fontSize = 12.sp
                    )
                }
                Text(
                    if (expanded) "▲" else "▼",
                    color = Pf.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = Space.s2)
                )
            }

            if (expanded) {
                HomeSectionDivider()
                if (spends.isEmpty()) {
                    Text(
                        "No expenses recorded for this cycle yet.",
                        color = Pf.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = Space.s2)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                        spends.forEachIndexed { idx, (cat, amt) ->
                            if (idx > 0) Hairline()
                            val pct = if (totalSpend > 0.0) ((amt / totalSpend) * 100).toInt() else 0
                            val fraction = if (totalSpend > 0.0) (amt / totalSpend).toFloat() else 0f
                            val catColor = categoryColor(cat)

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(Radius.Sm)
                                    .clickable {
                                        vm.entriesCategoryFilter = cat
                                        vm.tab = Tab.ENTRIES
                                    }
                                    .padding(vertical = 2.dp)
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
                                                .size(10.dp)
                                                .background(catColor, CircleShape)
                                        )
                                        Text(
                                            cat,
                                            color = Pf.Text,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "($pct%)",
                                            color = Pf.Muted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Text(
                                        inr(amt),
                                        color = Pf.Text,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Pf.Surface2, Radius.Pill)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                                            .height(6.dp)
                                            .background(catColor, Radius.Pill)
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
fun InitialProfileDialog(vm: FinTrackViewModel) {
    if (!vm.showInitialProfileDialog) return
    var nameDraft by remember { mutableStateOf(vm.activeProfile ?: "Vinay") }

    Dialog(onDismissRequest = { vm.showInitialProfileDialog = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            Text(
                "Welcome to FinTrack!",
                color = Pf.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Muted("Please enter your profile name to get started:")

            PfField(
                label = "Your Name",
                value = nameDraft,
                onValueChange = { nameDraft = it.take(20) },
                placeholder = "e.g. Vinay"
            )

            Row(
                Modifier.fillMaxWidth().padding(top = Space.s2),
                horizontalArrangement = Arrangement.End
            ) {
                PrimaryButton(
                    "Get Started",
                    { vm.completeInitialProfile(nameDraft) },
                    enabled = nameDraft.trim().isNotEmpty()
                )
            }
        }
    }
}

/**
 * Shown when a confirm needs an account. An expense asks where the money left
 * from, income where it landed, and a set-aside asks both — debit and credit —
 * because both sides are yours.
 */
@Composable
fun ConfirmSheet(vm: FinTrackViewModel) {

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
fun CardSettleSheet(vm: FinTrackViewModel) {
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

@Composable
fun LoanConfirmSheet(vm: FinTrackViewModel) {
    val loanId = vm.confirmingLoanId ?: return
    val loan = vm.loans.firstOrNull { it.id == loanId } ?: return
    val isPaid = vm.isLoanConfirmed(loan.id)

    Dialog(onDismissRequest = vm::cancelConfirmLoan) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            Text(
                if (isPaid) "Loan EMI Already Paid" else "Record Loan EMI Payment",
                color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
            )
            Text(
                "${loan.name} (${loan.person}) · ${loan.remainingMonths} mo remaining",
                color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )

            if (isPaid) {
                Muted("This loan EMI (₹${inr(loan.monthlyEmi)}) was already marked as paid for this month cycle.")
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    SecondaryButton("Close", vm::cancelConfirmLoan, Modifier.weight(1f))
                    PrimaryButton(
                        "Undo Payment",
                        vm::commitConfirmLoan,
                        Modifier.weight(1f)
                    )
                }
            } else {
                PfField(
                    label = "EMI Amount (₹)",
                    value = vm.loanConfirmAmountDraft,
                    onValueChange = { vm.loanConfirmAmountDraft = it },
                    numeric = true
                )

                if (loan.onCard) {
                    val card = vm.cards.firstOrNull { it.id == loan.cardId }
                    Muted("Billed to Card: ${card?.name ?: "Credit Card"}")
                } else {
                    Column {
                        Muted("Paid from Account")
                        PfSelect(
                            value = vm.loanConfirmAccountNameDraft,
                            options = vm.visibleAccounts.map { it.name },
                            onSelect = { vm.loanConfirmAccountNameDraft = it }
                        )
                    }
                }

                Muted("Recording EMI payment creates an expense transaction and decrements remaining months by 1.")

                Row(
                    Modifier.fillMaxWidth().padding(top = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2)
                ) {
                    SecondaryButton("Cancel", vm::cancelConfirmLoan, Modifier.weight(1f))
                    PrimaryButton(
                        "Confirm EMI",
                        vm::commitConfirmLoan,
                        Modifier.weight(1f),
                        enabled = (vm.loanConfirmAmountDraft.toDoubleOrNull() ?: 0.0) > 0.0 &&
                            (loan.onCard || vm.loanConfirmAccountNameDraft.isNotBlank())
                    )
                }
            }
        }
    }
}

@Composable
fun DetectedAccountDialog(vm: FinTrackViewModel) {
    val draft = vm.detectedAccountDraft ?: return
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = vm::cancelUnmatchedAccountPrompt) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
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
                        "New Entity Detected",
                        color = Pf.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Muted("Auto-extracted from SMS for tail ••${draft.tail}")
                }
                IconButton(onClick = vm::cancelUnmatchedAccountPrompt, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Pf.Muted, modifier = Modifier.size(18.dp))
                }
            }

            // Entity Type Selector (Bank Account / Credit Card / Loan)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface2, Radius.Pill)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val kinds = listOf(
                    "BANK_ACCOUNT" to "Bank A/c",
                    "CREDIT_CARD" to "Credit Card",
                    "EMI_LOAN" to "EMI / Loan"
                )
                kinds.forEach { (k, label) ->
                    val selected = draft.kind == k
                    Text(
                        label,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) Pf.Accent else Color.Transparent, Radius.Pill)
                            .clickable {
                                val updatedName = when (k) {
                                    "CREDIT_CARD" -> if (draft.bankName.isNotEmpty()) "${draft.bankName} Card ••${draft.tail}" else "Card ••${draft.tail}"
                                    "EMI_LOAN" -> if (draft.bankName.isNotEmpty()) "${draft.bankName} Loan ••${draft.tail}" else "Loan ••${draft.tail}"
                                    else -> if (draft.bankName.isNotEmpty()) "${draft.bankName} A/c ••${draft.tail}" else "Bank A/c ••${draft.tail}"
                                }
                                vm.updateDetectedAccountDraft(draft.copy(kind = k, suggestedName = updatedName))
                            }
                            .padding(vertical = 7.dp),
                        color = if (selected) (if (Pf.isDark) Color(0xFF111827) else Color.White) else Pf.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Editable Name
            PfField(
                label = when (draft.kind) {
                    "CREDIT_CARD" -> "Card Name"
                    "EMI_LOAN" -> "Loan Name"
                    else -> "Account Name"
                },
                value = draft.suggestedName,
                onValueChange = { vm.updateDetectedAccountDraft(draft.copy(suggestedName = it)) },
                placeholder = "Name"
            )

            // Belongs to
            PfSelect(
                label = "Belongs to",
                value = draft.owner,
                options = vm.ownerOptions,
                onSelect = { vm.updateDetectedAccountDraft(draft.copy(owner = it)) }
            )

            // Account / Card / Loan specific fields
            when (draft.kind) {
                "BANK_ACCOUNT" -> {
                    PfField(
                        label = "Current Balance (₹)",
                        value = draft.balanceText,
                        onValueChange = { vm.updateDetectedAccountDraft(draft.copy(balanceText = it)) },
                        placeholder = "e.g. 50000",
                        numeric = true
                    )
                    PfField(
                        label = "Last 3-4 Digits (for SMS Matching)",
                        value = draft.tail,
                        onValueChange = { vm.updateDetectedAccountDraft(draft.copy(tail = it)) },
                        placeholder = "Last digits",
                        numeric = true
                    )
                }
                "CREDIT_CARD" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        PfField(
                            label = "Credit Limit (₹)",
                            value = draft.limitText,
                            onValueChange = { vm.updateDetectedAccountDraft(draft.copy(limitText = it)) },
                            placeholder = "50000",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                        PfField(
                            label = "Current Due / Bal (₹)",
                            value = draft.balanceText,
                            onValueChange = { vm.updateDetectedAccountDraft(draft.copy(balanceText = it)) },
                            placeholder = "0",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        PfField(
                            label = "Min Due (₹)",
                            value = draft.minDueText,
                            onValueChange = { vm.updateDetectedAccountDraft(draft.copy(minDueText = it)) },
                            placeholder = "0",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                        PfField(
                            label = "Due Day (1-31)",
                            value = draft.dueDayText,
                            onValueChange = { vm.updateDetectedAccountDraft(draft.copy(dueDayText = it)) },
                            placeholder = "e.g. 15",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PfField(
                        label = "Card Last 3-4 Digits",
                        value = draft.tail,
                        onValueChange = { vm.updateDetectedAccountDraft(draft.copy(tail = it)) },
                        placeholder = "Last digits",
                        numeric = true
                    )
                }
                "EMI_LOAN" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        PfField(
                            label = "Monthly EMI (₹)",
                            value = draft.emiText,
                            onValueChange = { vm.updateDetectedAccountDraft(draft.copy(emiText = it)) },
                            placeholder = "e.g. 15000",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                        PfField(
                            label = "Tenure (Months)",
                            value = draft.tenureMonthsText,
                            onValueChange = { vm.updateDetectedAccountDraft(draft.copy(tenureMonthsText = it)) },
                            placeholder = "12",
                            numeric = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PfField(
                        label = "Due Day (1-31)",
                        value = draft.dueDayText,
                        onValueChange = { vm.updateDetectedAccountDraft(draft.copy(dueDayText = it)) },
                        placeholder = "e.g. 5",
                        numeric = true
                    )
                }
            }

            // Original SMS snippet
            if (draft.originalSms.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Surface2, Radius.Md)
                        .padding(Space.s3)
                ) {
                    Muted("Original SMS message:", size = 11)
                    Text(
                        draft.originalSms,
                        color = Pf.Text,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                SecondaryButton("Cancel", vm::cancelUnmatchedAccountPrompt, Modifier.weight(0.9f))
                PrimaryButton(
                    "Add & Link Transactions",
                    { vm.saveDetectedEntity(draft) },
                    Modifier.weight(1.4f)
                )
            }
        }
    }
}

@Composable
private fun RecurringSuggestionsSection(vm: FinTrackViewModel) {
    val suggestions = vm.recurringSuggestions
    if (suggestions.isEmpty()) return

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        SectionTitle("Recurring Bill Suggestions")
        suggestions.take(3).forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pf.Surface, Radius.Md)
                    .border(1.dp, Pf.Hairline, Radius.Md)
                    .padding(Space.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = Space.s2)) {
                    Text(s.party, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Muted("₹${inr(s.averageAmount)} / month around day ${s.suggestedDay} (${s.occurrences} payments)")
                }
                PrimaryButton(
                    "Track Bill",
                    onClick = { vm.addRecurringFromSuggestion(s) }
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
            .padding(horizontal = Space.s1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                if (vm.bucketView == "JOINT") "Joint Ledger" else (vm.activeProfile ?: "Personal"),
                color = Pf.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text("▾", color = Pf.Muted, fontSize = 11.sp)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .background(Pf.Surface2, Radius.Pill)
                .border(1.dp, Pf.Hairline, Radius.Pill)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = Pf.Accent400
            )
            Text(
                friendlyCycle(vm.cycle()),
                color = Pf.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeSubTabBar(vm: FinTrackViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Space.s1),
        horizontalArrangement = Arrangement.spacedBy(Space.s2)
    ) {
        for (tab in HomeTab.values()) {
            val isSelected = vm.homeTab == tab
            val bg = if (isSelected) Pf.Accent else Pf.Surface
            val textColor = if (isSelected) Color.White else Pf.Muted
            val icon = when (tab) {
                HomeTab.OVERVIEW -> Icons.Default.Dashboard
                HomeTab.ACCOUNTS -> Icons.Default.CreditCard
                HomeTab.BUDGETS -> Icons.Default.PieChart
                HomeTab.DEBTS_FUTURE -> Icons.Default.TrendingUp
            }

            Row(
                Modifier
                    .background(bg, Radius.Pill)
                    .clickable { vm.homeTab = tab }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    Modifier.size(14.dp),
                    tint = textColor
                )
                Text(
                    tab.label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OverviewSnapshotGrid(vm: FinTrackViewModel) {
    val income = vm.actualIncome
    val spent = vm.actualSpent
    val savingsRate = if (income > 0.0) {
        (((income - spent) / income) * 100.0).coerceAtLeast(0.0)
    } else 0.0
    val totalCardOwed = vm.scopedCards.sumOf { it.balance }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        SectionTitle("Monthly Overview · ${vm.bucketLabel}")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            OverviewMetricTile(
                title = "INCOME",
                value = inr(income),
                subtitle = "Earned this cycle",
                accent = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { vm.homeTab = HomeTab.BUDGETS }
            )
            OverviewMetricTile(
                title = "EXPENSES",
                value = inr(spent),
                subtitle = "Budget: ${inr(vm.plannedExpense)}",
                accent = Pf.Text,
                modifier = Modifier.weight(1f),
                onClick = { vm.homeTab = HomeTab.BUDGETS }
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            OverviewMetricTile(
                title = "SAVINGS RATE",
                value = "${"%.1f".format(savingsRate)}%",
                subtitle = if (savingsRate >= 20.0) "Healthy savings" else "Target: 20%+",
                accent = if (savingsRate >= 20.0) Color(0xFF10B981) else Pf.Text,
                modifier = Modifier.weight(1f),
                onClick = { vm.homeTab = HomeTab.BUDGETS }
            )
            OverviewMetricTile(
                title = "CARD DUES",
                value = inr(totalCardOwed),
                subtitle = if (totalCardOwed > 0.0) "Outstanding balance" else "All bills clear",
                accent = if (totalCardOwed > 0.0) Color(0xFFEF4444) else Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { vm.homeTab = HomeTab.ACCOUNTS }
            )
        }
    }
}

@Composable
private fun OverviewMetricTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .background(Pf.Surface, Radius.Lg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.s3)
    ) {
        Text(
            title,
            color = Pf.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Muted(subtitle, size = 11)
    }
}

@Composable
private fun OverviewUpcomingDues(vm: FinTrackViewModel) {
    val pendingLoans = vm.scopedLoans.filter { !vm.isLoanCleared(it) && !vm.isLoanConfirmed(it.id) }
    val pendingCards = vm.scopedCards.filter { !it.paid && it.balance > 0.0 }

    if (pendingLoans.isEmpty() && pendingCards.isEmpty()) {
        PfCard(padding = PaddingValues(Space.s4)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color(0xFF10B981).copy(alpha = 0.15f), Radius.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = Color(0xFF10B981))
                }
                Column(Modifier.weight(1f)) {
                    Text("All dues are clear", color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Muted("No pending loan EMIs or credit card bills due right now.", size = 11)
                }
            }
        }
        return
    }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Space.s2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Upcoming Dues & Bills")
            Text(
                "View All",
                color = Pf.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { vm.homeTab = if (pendingCards.isNotEmpty()) HomeTab.ACCOUNTS else HomeTab.DEBTS_FUTURE }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            pendingCards.take(2).forEach { c ->
                PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val dueInfo = if (c.statementAmount > 0.0) "Statement due: ${inr(c.statementAmount)}" else "Balance: ${inr(c.balance)}"
                            Muted("$dueInfo · due ${c.dueText}", size = 11)
                        }
                        SecondaryButton("Pay / Settle", {
                            vm.startSettleCard(c.id)
                        })
                    }
                }
            }

            pendingLoans.take(2).forEach { l ->
                PfCard(padding = PaddingValues(horizontal = Space.s4, vertical = Space.s3)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, color = Pf.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted("${inr(l.monthlyEmi)}/mo · ${l.remainingMonths} months left", size = 11)
                        }
                        PrimaryButton("Pay EMI", {
                            vm.homeTab = HomeTab.DEBTS_FUTURE
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun UnmatchedAccountAlert(vm: FinTrackViewModel) {
    val knownTails = (
        vm.accounts.map { it.numberTail } +
        vm.cards.map { it.numberTail } +
        vm.accounts.map { it.name } +
        vm.cards.map { it.name }
    ).filter { it.isNotBlank() }

    val txnsNeedingAccount = vm.txnsNeedingAccount
        .filter { it.accountTail.isNotBlank() }
        .filter { t ->
            val tail = t.accountTail.trim()
            val digits = tail.filter { it.isDigit() }
            knownTails.none { known ->
                DetectedAccountParser.tailsMatch(known, tail) ||
                (digits.length >= 3 && known.filter { it.isDigit() }.endsWith(digits)) ||
                known.contains(tail, ignoreCase = true)
            }
        }
    val uniqueTails = txnsNeedingAccount.map { it.accountTail.trim() }.distinct()

    if (uniqueTails.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Space.s2),
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            uniqueTails.forEach { tail ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Pf.Surface2, Radius.Md)
                        .border(1.dp, Pf.Hairline, Radius.Md)
                        .clickable { vm.navigateToCreateAccountFromTail(tail) }
                        .padding(Space.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "New Account/Card detected: ••$tail",
                            color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Muted(
                            "An SMS transaction arrived for ••$tail but it isn't in your account list. Tap to add it!",
                            size = 11
                        )
                    }
                    Tag("Action Required", Pf.Accent, if (Pf.isDark) Color.Black else Color.White)
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
                Brush.linearGradient(listOf(Color(0xFF23163D), Color(0xFF120C22))),
                Radius.Xl
            )
            .border(1.dp, Pf.Accent.copy(alpha = 0.35f), Radius.Xl)
            .padding(horizontal = Space.s4, vertical = Space.s4)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "TOTAL ACROSS ACCOUNTS",
                color = Pf.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
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
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Muted("Across ${vm.scopedAccounts.size} active accounts", size = 12)
            Tag(vm.bucketLabel, Pf.Accent100, Pf.Accent800)
        }
    }
}

@Composable
private fun AccountsSection(vm: FinTrackViewModel) {
    Column {
        SectionTitle("Accounts · ${vm.bucketLabel}", Modifier.padding(bottom = Space.s3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            vm.scopedAccounts.forEach { a ->
                PfCard {
                    if (vm.editingAccountId == a.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PfField(label = "Account name", value = vm.accountDraft.name, onValueChange = { vm.accountDraft = vm.accountDraft.copy(name = it) }, placeholder = "Account name")
                            PfSelect(
                                label = "Belongs to",
                                value = vm.accountDraft.owner,
                                options = vm.ownerOptions,
                                onSelect = { vm.accountDraft = vm.accountDraft.copy(owner = it) }
                            )
                            PfField(label = "Balance (₹)", value = vm.accountDraft.balanceText, onValueChange = { vm.accountDraft = vm.accountDraft.copy(balanceText = it) }, placeholder = "Balance", numeric = true)
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
                                Column(Modifier.weight(1f, fill = false)) {
                                    Text(
                                        a.name,
                                        color = Pf.Text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Muted(
                                        if (a.numberTail.isNotBlank()) "${a.owner} · ••${a.numberTail}"
                                        else "${a.owner} · no digits set",
                                        size = 12
                                    )
                                }
                            }
                            Spacer(Modifier.width(Space.s2))
                            Text(
                                inr(vm.balanceOf(a)),
                                color = Pf.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
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
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                            Spacer(Modifier.width(Space.s2))
                            Text(
                                inr(m.left),
                                color = if (m.left >= 0) Color(0xFF00BFA5) else Color(0xFFFF5252),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
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
    val salary = nextMonth?.income ?: vm.plannedIncome
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
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

private fun categoryColor(category: String): Color = when {
    category.contains("Food", ignoreCase = true) || category.contains("Dining", ignoreCase = true) || category.contains("Groceries", ignoreCase = true) || category.contains("Snack", ignoreCase = true) -> Color(0xFFFFA726)
    category.contains("Shopping", ignoreCase = true) || category.contains("Clothes", ignoreCase = true) || category.contains("Electronic", ignoreCase = true) -> Color(0xFF29B6F6)
    category.contains("Bill", ignoreCase = true) || category.contains("Electricity", ignoreCase = true) || category.contains("Recharge", ignoreCase = true) || category.contains("Wifi", ignoreCase = true) || category.contains("Utility", ignoreCase = true) -> Color(0xFFAB47BC)
    category.contains("Travel", ignoreCase = true) || category.contains("Fuel", ignoreCase = true) || category.contains("Cab", ignoreCase = true) || category.contains("Transport", ignoreCase = true) -> Color(0xFF5C6BC0)
    category.contains("Health", ignoreCase = true) || category.contains("Med", ignoreCase = true) || category.contains("Doctor", ignoreCase = true) -> Color(0xFF26A69A)
    category.contains("Invest", ignoreCase = true) || category.contains("SIP", ignoreCase = true) || category.contains("Mutual", ignoreCase = true) -> Color(0xFF66BB6A)
    category.contains("Entertainment", ignoreCase = true) || category.contains("Movie", ignoreCase = true) || category.contains("Netflix", ignoreCase = true) -> Color(0xFFEC407A)
    else -> Color(0xFF7E57C2)
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

                // Visual Category Spending Distribution Bar
                val cycle = vm.cycle()
                val currentTxns = vm.filteredTxns.filter { it.kind == "EXPENSE" && it.period == cycle }
                val totalExpense = currentTxns.sumOf { it.amount }

                if (totalExpense > 0.0) {
                    val catSpends = currentTxns
                        .groupBy { it.category }
                        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                        .toList()
                        .sortedByDescending { it.second }

                    Hairline()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.s4, vertical = Space.s3)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MONTHLY SPEND DISTRIBUTION",
                                color = Pf.Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                inr(totalExpense),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(Modifier.height(Space.s2))

                        // Multi-segment horizontal bar
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(Radius.Pill)
                                .background(Pf.Surface2)
                        ) {
                            catSpends.forEach { (cat, amt) ->
                                val weight = (amt / totalExpense).toFloat().coerceIn(0.001f, 1f)
                                Box(
                                    Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(categoryColor(cat))
                                )
                            }
                        }

                        Spacer(Modifier.height(Space.s3))

                        // Category chips with colored indicators
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Space.s2)
                        ) {
                            catSpends.forEach { (cat, amt) ->
                                val pct = ((amt / totalExpense) * 100).toInt()
                                Row(
                                    Modifier
                                        .background(Pf.Surface2, Radius.Pill)
                                        .border(1.dp, Pf.Hairline, Radius.Pill)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(categoryColor(cat), CircleShape)
                                    )
                                    Text(cat, color = Pf.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("$pct%", color = Pf.Muted, fontSize = 11.sp)
                                }
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
                        if (expanded) "Hide Detailed Analytics" else "View Savings Rate & Category Breakdown",
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

                        // 2. Category Breakdown List
                        Text(
                            "Category Details",
                            color = Pf.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = Space.s1)
                        )
                        
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .background(categoryColor(cat), CircleShape)
                                            )
                                            Text(cat, color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("${(pct * 100).toInt()}%", color = Pf.Muted, fontSize = 11.sp)
                                        }
                                        Text(inr(amt), color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    ProgressBar(pct.toFloat(), categoryColor(cat))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    cat,
                                    color = Pf.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(8.dp))
                                if (pct >= 1.0) {
                                    val over = spend - allowed
                                    Tag("Overspent by ${inr(over)}", Pf.Accent, Color.White)
                                } else if (pct >= 0.8) {
                                    Tag("80%+ Used", Color(0xFFFFA726), Color.Black)
                                }
                            }
                            Spacer(Modifier.width(Space.s2))
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
                                        color = Pf.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                        SecondaryButton("Paid", { vm.startConfirmLoan(l) })
                                    else -> PrimaryButton("Pay EMI", { vm.startConfirmLoan(l) })
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
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2A1747), Color(0xFF160D28))
                                    ),
                                    Radius.Lg
                                )
                                .border(1.dp, Pf.Accent.copy(alpha = 0.45f), Radius.Lg)
                                .padding(Space.s4)
                        ) {
                            // Top row: Chip Emblem + Card Name + Network Tag + Edit Icon
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp, 20.dp)
                                            .background(Color(0xFFD4AF37).copy(alpha = 0.25f), Radius.Sm)
                                            .border(1.dp, Color(0xFFD4AF37), Radius.Sm)
                                    )
                                    Text(
                                        c.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Tag("CREDIT", Pf.Accent.copy(alpha = 0.25f), Pf.Accent400)
                                    IconButton(onClick = { vm.startEditCard(c) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, "Edit card", Modifier.size(16.dp), tint = Pf.Accent400)
                                    }
                                }
                            }

                            // Card Number & Owner & Due Date
                            Spacer(Modifier.height(Space.s3))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        "•••• •••• •••• ${c.numberTail.ifBlank { "••••" }}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.5.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Muted(c.owner.uppercase(), size = 10)
                                }
                                if (c.dueText.isNotBlank()) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Muted("DUE DATE", size = 9)
                                        Text(c.dueText, color = Pf.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(Modifier.height(Space.s3))
                            Hairline()
                            Spacer(Modifier.height(Space.s3))

                            // Current Balance & Available Limit
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Muted("CURRENT BALANCE", size = 10)
                                    Text(
                                        inr(c.balance),
                                        color = if (c.balance > 0.0) Color.White else Color(0xFF10B981),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Muted("AVAILABLE / LIMIT", size = 10)
                                    Text(
                                        "${inr((c.limit - c.balance).coerceAtLeast(0.0))} / ${inr(c.limit)}",
                                        color = Pf.Muted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            ProgressBar(pct, if (pct >= ALERT_PCT) Color(0xFFEF4444) else Pf.Accent, height = 6)

                            // Statement amount info & action buttons
                            Spacer(Modifier.height(Space.s3))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    if (c.statementAmount > 0.0) {
                                        Text(
                                            "Actually Due: ${inr(c.statementAmount)}",
                                            color = Color(0xFFFFA726),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Muted("Min due ${inr(c.minDue)} · Cycle ${c.statementDay}th", size = 11)
                                }
                                Spacer(Modifier.width(Space.s2))
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
                        Text(
                            e.category,
                            color = Pf.Text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    Spacer(Modifier.width(Space.s2))
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
