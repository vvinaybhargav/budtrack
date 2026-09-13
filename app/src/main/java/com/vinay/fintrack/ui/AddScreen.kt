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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.INVEST_PICKABLE
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.addDays
import com.vinay.fintrack.data.dayFirstOf
import com.vinay.fintrack.data.todayDayFirst
import com.vinay.fintrack.data.isoFromDayFirst
import com.vinay.fintrack.data.categoryForParty
import com.vinay.fintrack.data.UNCATEGORISED
import com.vinay.fintrack.data.MathEvaluator
import java.util.Calendar

private data class AddKindItem(val key: String, val label: String, val icon: ImageVector)

// Recurring and Set aside are separate kinds: one is paid every month, the
// other every few months and put by in between. They behave differently enough
// on Home that choosing between them belongs here, not in a period dropdown.
private val ADD_KINDS = listOf(
    AddKindItem("ONE_TIME", "One-time", Icons.Default.Receipt),
    AddKindItem("RECURRING", "Recurring", Icons.Default.Repeat),
    AddKindItem("SET_ASIDE", "Set aside", Icons.Default.Bookmark),
    AddKindItem("EMI_LOAN", "EMI / Loan", Icons.Default.AccountBalance),
    AddKindItem("INVESTMENT", "Investment", Icons.Default.TrendingUp),
    AddKindItem("BANK_ACCOUNT", "Account", Icons.Default.AccountBalanceWallet),
    AddKindItem("CREDIT_CARD", "Credit Card", Icons.Default.CreditCard)
)

/** A set-aside is paid every 2 to 12 months; every month would just be a
 *  recurring commitment, which is its own kind. */
private val PERIOD_OPTIONS = (2..12).map { "Every $it months" }

private fun periodLabel(months: Int) = "Every ${months.coerceIn(2, 12)} months"

private fun periodFromLabel(label: String) =
    label.filter { it.isDigit() }.toIntOrNull()?.coerceIn(2, 12) ?: 12

@Composable
fun AddScreen(vm: FinTrackViewModel) {
    val isEditing = vm.editingEntryId != null

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s4)
    ) {
        if (isEditing) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editing entry", color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    GhostButton("Cancel", vm::cancelEdit)
                }
            }
        } else {
            item {
                Column {
                    Muted("WHAT ARE YOU ADDING?", Modifier.padding(bottom = Space.s2), size = 11)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
                        ADD_KINDS.forEach { item ->
                            val isSelected = vm.addKind == item.key
                            Row(
                                Modifier
                                    .background(
                                        if (isSelected) Pf.Accent else Pf.Surface,
                                        Radius.Pill
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Pf.Accent400 else Pf.Hairline,
                                        Radius.Pill
                                    )
                                    .clickable { vm.selectAddKind(item.key) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    Modifier.size(16.dp),
                                    tint = if (isSelected) Pf.OnAccent else Pf.Muted
                                )
                                Text(
                                    item.label,
                                    color = if (isSelected) Pf.OnAccent else Pf.Text,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    val hint = when (vm.addKind) {
                        "ONE_TIME" -> "Already paid or received. Goes straight into Transactions."
                        "SET_ASIDE" -> "Paid periodically or lent to others. Set aside a share each month."
                        "RECURRING" -> "Monthly fixed commitment. Confirm it each month on Home."
                        "EMI_LOAN" -> "Track loan tenure, EMI schedule, and interest payments."
                        "INVESTMENT" -> "Track mutual funds, SIPs, gold, or recurring market assets."
                        "BANK_ACCOUNT" -> "Add a bank account to track balances and auto-sync SMS."
                        "CREDIT_CARD" -> "Add a credit card with cycle, limit, and statement tracking."
                        else -> ""
                    }
                    if (hint.isNotEmpty()) {
                        Muted(hint, Modifier.padding(top = Space.s2))
                    }
                }
            }
        }

        val showLoan = !isEditing && vm.addKind == "EMI_LOAN"
        val showAccount = !isEditing && vm.addKind == "BANK_ACCOUNT"
        val showCard = !isEditing && vm.addKind == "CREDIT_CARD"
        val showOneTime = !isEditing && vm.addKind == "ONE_TIME"
        val showGeneric = isEditing || (vm.addKind !in listOf("EMI_LOAN", "BANK_ACCOUNT", "CREDIT_CARD", "ONE_TIME"))

        if (showLoan) item { LoanForm(vm) }
        if (showAccount) item { AccountForm(vm) }
        if (showCard) item { CardForm(vm) }
        if (showOneTime) item { OneTimePaymentForm(vm) }
        if (showGeneric) item { GenericForm(vm, isEditing) }
    }
}

@Composable
private fun HeroAmountInput(
    amountText: String,
    onAmountChange: (String) -> Unit,
    onQuickAdd: (Long) -> Unit
) {
    var calculationFormula by remember(amountText.isEmpty()) { androidx.compose.runtime.mutableStateOf("") }
    val hasMath = MathEvaluator.hasMathOperation(amountText)

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF23163D), Color(0xFF130C23))),
                Radius.Lg
            )
            .border(1.dp, Pf.Accent.copy(alpha = 0.45f), Radius.Lg)
            .padding(Space.s4)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AMOUNT",
                    color = Pf.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (amountText.isNotEmpty()) {
                    GhostButton("Clear", {
                        calculationFormula = ""
                        onAmountChange("")
                    })
                }
            }
            Spacer(Modifier.height(Space.s2))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "₹",
                    color = Pf.Accent400,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { onAmountChange(it.filter { c -> c.isDigit() || c in ".,+-*/() " }) },
                    placeholder = { Text("0", color = Pf.Muted, fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Pf.Text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = Radius.Sm,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Pf.Accent400,
                        focusedTextColor = Pf.Text,
                        unfocusedTextColor = Pf.Text
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Calculate button
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .background(
                            if (hasMath) Pf.Accent else Pf.Surface2,
                            Radius.Md
                        )
                        .border(
                            1.dp,
                            if (hasMath) Pf.Accent400 else Pf.Hairline,
                            Radius.Md
                        )
                        .clickable {
                            val res = MathEvaluator.evaluate(amountText)
                            if (res != null) {
                                val formatted = MathEvaluator.formatResult(res)
                                calculationFormula = "$amountText = $formatted"
                                onAmountChange(formatted)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "=",
                        color = if (hasMath) Color.White else Pf.Muted,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (calculationFormula.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    calculationFormula,
                    color = Pf.Accent400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(Space.s3))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(100L, 500L, 1000L, 2000L, 5000L).forEach { delta ->
                    Box(
                        Modifier
                            .background(Pf.Surface2, Radius.Pill)
                            .border(1.dp, Pf.Hairline, Radius.Pill)
                            .clickable { onQuickAdd(delta) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "+₹${inr(delta.toDouble())}",
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

// Smart Add lived here: a second, weaker chat beside the real one. The Chat tab
// reads and writes everything, so this screen is just the forms now.

@Composable
private fun LoanForm(vm: FinTrackViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        PfField("Loan name", vm.newLoanDraft.name, { vm.newLoanDraft = vm.newLoanDraft.copy(name = it) }, placeholder = "e.g. Car loan — Me")
        PfSelect("Person", vm.newLoanDraft.person, vm.draftPersonOptions, { vm.newLoanDraft = vm.newLoanDraft.copy(person = it) })
        PfField("Monthly EMI (₹)", vm.newLoanDraft.emiText, { vm.newLoanDraft = vm.newLoanDraft.copy(emiText = it) }, placeholder = "e.g. 22000", isAmount = true)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Tenure (months)", vm.newLoanDraft.totalMonthsText, { vm.newLoanDraft = vm.newLoanDraft.copy(totalMonthsText = it) }, Modifier.weight(1f), "e.g. 84", numeric = true)
            PfField("Months remaining", vm.newLoanDraft.remainingMonthsText, { vm.newLoanDraft = vm.newLoanDraft.copy(remainingMonthsText = it) }, Modifier.weight(1f), "e.g. 42", numeric = true)
        }
        // Accounts and cards together: a card EMI is a purchase split into
        // instalments, which is the same arrangement paid to a different place.
        PfSelect("Paid from", vm.newLoanSourceName, vm.emiSourceOptions, vm::setLoanSource)
        val payMonthOptions = vm.startPayMonthOptions
        val selectedStartMonthKey = vm.newLoanDraft.startMonth.ifEmpty { today().take(7) }
        val selectedStartMonthLabel = payMonthOptions.firstOrNull { it.key == selectedStartMonthKey }?.label
            ?: payMonthOptions.firstOrNull()?.label.orEmpty()
        PfSelect(
            label = "Start Pay Month",
            value = selectedStartMonthLabel,
            options = payMonthOptions.map { it.label },
            onSelect = { label ->
                val opt = payMonthOptions.firstOrNull { it.label == label }
                if (opt != null) {
                    vm.newLoanDraft = vm.newLoanDraft.copy(startMonth = opt.key)
                }
            }
        )
        PfField(
            "Due day of month (1-31)",
            vm.newLoanDraft.dueText,
            { vm.newLoanDraft = vm.newLoanDraft.copy(dueText = it) },
            placeholder = "e.g. 15",
            numeric = true
        )
        Muted(
            if (vm.newLoanDraft.cardId.isNotEmpty())
                "On a card the instalment adds to what the card owes. Nothing leaves " +
                    "your bank until you settle the card bill."
            else "Debited from this account each month when you confirm it."
        )
        PrimaryButton(
            "Add loan",
            vm::addNewLoan,
            Modifier.fillMaxWidth(),
            enabled = vm.newLoanDraft.name.isNotBlank() &&
                (vm.newLoanDraft.emiText.toDoubleOrNull() ?: 0.0) > 0 &&
                (vm.newLoanDraft.totalMonthsText.toIntOrNull() ?: 0) > 0
        )
    }
}

@Composable
private fun DebtForm(vm: FinTrackViewModel) {
    val draft = vm.newDebtDraft
    val isLent = draft.type == "LENT"
    val accounts = vm.scopedAccounts
    val accountOptions = listOf("None (Cash / Offline)") + accounts.map { it.name }
    val selectedAccountName = accounts.firstOrNull { it.id == draft.accountId }?.name ?: "None (Cash / Offline)"

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        // Toggle
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface2, Radius.Pill)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .background(if (isLent) Color(0xFF00BFA5) else Color.Transparent, Radius.Pill)
                    .clickable { vm.newDebtDraft = draft.copy(type = "LENT") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("I Lent Money", color = if (isLent) Color.White else Pf.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .weight(1f)
                    .background(if (!isLent) Color(0xFFFF5252) else Color.Transparent, Radius.Pill)
                    .clickable { vm.newDebtDraft = draft.copy(type = "BORROWED") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("I Borrowed", color = if (!isLent) Color.White else Pf.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        PfField(
            if (isLent) "Lent to (Person Name)" else "Borrowed from (Person Name)",
            draft.peerName,
            { vm.newDebtDraft = draft.copy(peerName = it) },
            placeholder = "e.g. Rahul, Priya, Ajay"
        )

        PfField(
            "Amount (₹)",
            draft.amountText,
            { vm.newDebtDraft = draft.copy(amountText = it) },
            placeholder = "e.g. 5000",
            numeric = true
        )

        PfField(
            "Expected Return Date (Optional)",
            draft.dueDateText,
            { vm.newDebtDraft = draft.copy(dueDateText = it) },
            placeholder = "e.g. 15-10-2026 or 15th"
        )

        Column {
            Muted("Bank Account (Optional)", size = 12)
            Spacer(Modifier.height(4.dp))
            PfSelect(
                "Bank Account",
                selectedAccountName,
                accountOptions,
                { chosen ->
                    val accId = accounts.firstOrNull { it.name == chosen }?.id.orEmpty()
                    vm.newDebtDraft = draft.copy(accountId = accId, recordTxn = accId.isNotEmpty())
                }
            )
        }

        if (draft.accountId.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.newDebtDraft = draft.copy(recordTxn = !draft.recordTxn) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(1.5.dp, if (draft.recordTxn) Pf.Accent else Pf.Muted, Radius.Sm)
                        .background(if (draft.recordTxn) Pf.Accent else Color.Transparent, Radius.Sm),
                    contentAlignment = Alignment.Center
                ) {
                    if (draft.recordTxn) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = Color.White)
                    }
                }
                Text(
                    if (isLent) "Debit $selectedAccountName now" else "Credit $selectedAccountName now",
                    color = Pf.Text,
                    fontSize = 13.sp
                )
            }
        }

        PfField(
            "Note / Purpose (Optional)",
            draft.note,
            { vm.newDebtDraft = draft.copy(note = it) },
            placeholder = "e.g. Dinner split, Emergency loan"
        )

        val isValid = draft.peerName.isNotBlank() && (draft.amountText.toDoubleOrNull() ?: 0.0) > 0.0
        PrimaryButton(
            "Save Record",
            { vm.saveNewDebt(navigateHome = true) },
            Modifier.fillMaxWidth(),
            enabled = isValid
        )
    }
}

@Composable
private fun AccountForm(vm: FinTrackViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        PfField("Account name", vm.newAccountDraft.name, { vm.newAccountDraft = vm.newAccountDraft.copy(name = it) }, placeholder = "e.g. HDFC Savings")
        PfSelect("Belongs to", vm.newAccountDraft.owner, vm.ownerOptions, { vm.newAccountDraft = vm.newAccountDraft.copy(owner = it) })
        PfField("Current balance (₹)", vm.newAccountDraft.balanceText, { vm.newAccountDraft = vm.newAccountDraft.copy(balanceText = it) }, placeholder = "e.g. 120000", numeric = true)
        PfField(
            "Last digits of the account number",
            vm.newAccountDraft.numberTail,
            { vm.newAccountDraft = vm.newAccountDraft.copy(numberTail = it) },
            placeholder = "e.g. 234 — the digits your bank's SMS shows",
            numeric = true
        )
        Muted("Three is enough, as long as no two accounts end the same.")
        PrimaryButton("Add account", vm::addNewAccount, Modifier.fillMaxWidth(), enabled = vm.newAccountDraft.name.isNotBlank())
    }
}

@Composable
private fun CardForm(vm: FinTrackViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        PfField("Card name", vm.newCardDraft.name, { vm.newCardDraft = vm.newCardDraft.copy(name = it) }, placeholder = "e.g. HDFC Regalia")
        PfSelect("Belongs to", vm.newCardDraft.owner, vm.ownerOptions, { vm.newCardDraft = vm.newCardDraft.copy(owner = it) })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Credit limit (₹)", vm.newCardDraft.limitText, { vm.newCardDraft = vm.newCardDraft.copy(limitText = it) }, Modifier.weight(1f), "e.g. 300000", numeric = true)
            PfField("Current balance (₹)", vm.newCardDraft.balanceText, { vm.newCardDraft = vm.newCardDraft.copy(balanceText = it) }, Modifier.weight(1f), "e.g. 42500", numeric = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Minimum due (₹)", vm.newCardDraft.minDueText, { vm.newCardDraft = vm.newCardDraft.copy(minDueText = it) }, Modifier.weight(1f), "e.g. 2200", numeric = true)
            PfField("Due day of month (1-31)", vm.newCardDraft.dueText, { vm.newCardDraft = vm.newCardDraft.copy(dueText = it) }, Modifier.weight(1f), "e.g. 18", numeric = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Statement day of month (1-31)", vm.newCardDraft.statementDayText, { vm.newCardDraft = vm.newCardDraft.copy(statementDayText = it) }, Modifier.weight(1f), "e.g. 20", numeric = true)
            PfField("Statement amount / Actually Due (₹)", vm.newCardDraft.statementAmountText, { vm.newCardDraft = vm.newCardDraft.copy(statementAmountText = it) }, Modifier.weight(1f), "e.g. 12000", numeric = true)
        }
        PfField(
            "Last digits of the card",
            vm.newCardDraft.numberTail,
            { vm.newCardDraft = vm.newCardDraft.copy(numberTail = it) },
            placeholder = "e.g. 321 — the digits your bank's SMS shows",
            numeric = true
        )
        Muted("A spend on this card is added to the card, not taken from an account.")
        PrimaryButton(
            "Add card",
            vm::addNewCard,
            Modifier.fillMaxWidth(),
            enabled = vm.newCardDraft.name.isNotBlank()
        )
    }
}

@Composable
private fun OneTimePaymentForm(vm: FinTrackViewModel) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                vm.oneOffDateText = "%02d-%02d-%04d".format(dayOfMonth, month + 1, year)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val isToday = vm.oneOffDateText == todayDayFirst()
    val isYesterday = vm.oneOffDateText == dayFirstOf(addDays(today(), -1))
    val evaluatedAmount = MathEvaluator.evaluate(vm.draft.amountText) ?: vm.draft.amountText.toDoubleOrNull() ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        // 1. Amount Input
        HeroAmountInput(
            amountText = vm.draft.amountText,
            onAmountChange = { vm.draft = vm.draft.copy(amountText = it) },
            onQuickAdd = { delta ->
                val cur = MathEvaluator.evaluate(vm.draft.amountText) ?: vm.draft.amountText.toDoubleOrNull() ?: 0.0
                vm.draft = vm.draft.copy(amountText = MathEvaluator.formatResult(cur + delta))
            }
        )

        // 2. Transaction Type / Kind Selector (Expense / Income / Transfer)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            listOf(
                Triple("EXPENSE", "💸 Expense", Pf.Accent400),
                Triple("INCOME", "💰 Income", Color(0xFF00BFA5)),
                Triple("TRANSFER", "🔄 Transfer", Color(0xFF64B5F6))
            ).forEach { (k, label, tint) ->
                val isSel = vm.oneOffKind == k
                Box(
                    Modifier
                        .weight(1f)
                        .clip(Radius.Pill)
                        .background(if (isSel) tint.copy(alpha = 0.18f) else Pf.Surface2)
                        .border(1.5.dp, if (isSel) tint else Pf.Hairline, Radius.Pill)
                        .clickable { vm.oneOffKind = k }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSel) tint else Pf.Text,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Description / Note with real-time Auto-Categorization
        PfField(
            label = if (vm.oneOffKind == "TRANSFER") "Transfer description (optional)" else "Description / Note",
            value = vm.draft.note,
            onValueChange = { noteInput ->
                val autoCat = categoryForParty(noteInput, vm.categories, vm.smsRules)
                val newCat = if (autoCat.isNotBlank() && autoCat != UNCATEGORISED) autoCat else vm.draft.category
                vm.draft = vm.draft.copy(note = noteInput, category = newCat)
            },
            placeholder = when (vm.oneOffKind) {
                "EXPENSE" -> "e.g. Swiggy, Uber, D-Mart groceries, Fuel, Netflix…"
                "INCOME" -> "e.g. Freelance project, Salary bonus, Cash gift…"
                "TRANSFER" -> "e.g. Moved to Savings, Emergency fund…"
                else -> "Note"
            }
        )

        // 4. Category Selector (for Expense and Income)
        if (vm.oneOffKind != "TRANSFER") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Muted("CATEGORY", size = 11)
                    Text(
                        vm.draft.category.ifEmpty { "Uncategorised" },
                        color = Pf.Accent400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val topCategories = listOf("Eating Out", "Groceries", "Shopping", "Fuel", "Travel", "Utilities", "Health", "Investments")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topCategories.forEach { cat ->
                        val isSel = vm.draft.category == cat
                        Box(
                            Modifier
                                .clip(Radius.Pill)
                                .background(if (isSel) Pf.Accent else Pf.Surface2)
                                .border(1.dp, if (isSel) Pf.Accent else Pf.Hairline, Radius.Pill)
                                .clickable { vm.draft = vm.draft.copy(category = cat) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                cat,
                                color = if (isSel) Color.White else Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                PfSelect(
                    label = "All Categories",
                    value = vm.draft.category,
                    options = vm.categories,
                    onSelect = { vm.draft = vm.draft.copy(category = it) }
                )
            }
        }

        // 5. Payment Method / Source Picker
        if (vm.oneOffKind == "TRANSFER") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                Column(Modifier.weight(1f)) {
                    PfSelect(
                        label = "From Account",
                        value = vm.oneOffAccountName,
                        options = vm.oneOffAccountOptions.map { it.name },
                        onSelect = { name ->
                            vm.setOneOffAccount(vm.oneOffAccountOptions.firstOrNull { it.name == name }?.id.orEmpty())
                        }
                    )
                }
                Column(Modifier.weight(1f)) {
                    PfSelect(
                        label = "To Account",
                        value = vm.oneOffToAccountName,
                        options = vm.visibleAccounts.map { it.name },
                        onSelect = { name ->
                            vm.setOneOffToAccount(vm.visibleAccounts.firstOrNull { it.name == name }?.id.orEmpty())
                        }
                    )
                }
            }
        } else if (vm.oneOffKind == "EXPENSE") {
            val sourceOptions = vm.oneOffPaymentSources.map {
                if (it.isCard) "${it.name} (Credit Card)" else "${it.name} (Bank)"
            }
            PfSelect(
                label = "Paid With (Bank / Credit Card)",
                value = vm.selectedOneOffSourceName,
                options = sourceOptions,
                onSelect = vm::selectOneOffPaymentSource
            )
        } else {
            PfSelect(
                label = "Deposit Into (Bank Account)",
                value = vm.oneOffAccountName,
                options = vm.oneOffAccountOptions.map { it.name },
                onSelect = { name ->
                    vm.setOneOffAccount(vm.oneOffAccountOptions.firstOrNull { it.name == name }?.id.orEmpty())
                }
            )
        }

        // 6. Date Fast Selector
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Muted("TRANSACTION DATE", size = 11)
                val parsedIso = isoFromDayFirst(vm.oneOffDateText)
                if (parsedIso != null) {
                    Tag(prettyDate(parsedIso), Pf.Accent100, Pf.Accent800)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(Radius.Pill)
                        .background(if (isToday) Pf.Accent else Pf.Surface2)
                        .border(1.dp, if (isToday) Pf.Accent else Pf.Hairline, Radius.Pill)
                        .clickable { vm.oneOffDateText = todayDayFirst() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Today",
                        color = if (isToday) Color.White else Pf.Text,
                        fontSize = 12.5.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .clip(Radius.Pill)
                        .background(if (isYesterday) Pf.Accent else Pf.Surface2)
                        .border(1.dp, if (isYesterday) Pf.Accent else Pf.Hairline, Radius.Pill)
                        .clickable { vm.oneOffDateText = dayFirstOf(addDays(today(), -1)) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Yesterday",
                        color = if (isYesterday) Color.White else Pf.Text,
                        fontSize = 12.5.sp,
                        fontWeight = if (isYesterday) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Box(
                    Modifier
                        .weight(1.2f)
                        .clip(Radius.Pill)
                        .background(if (!isToday && !isYesterday) Pf.Accent else Pf.Surface2)
                        .border(1.dp, if (!isToday && !isYesterday) Pf.Accent else Pf.Hairline, Radius.Pill)
                        .clickable { datePickerDialog.show() }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (!isToday && !isYesterday) Color.White else Pf.Accent400
                        )
                        Text(
                            if (!isToday && !isYesterday) vm.oneOffDateText else "Pick Date",
                            color = if (!isToday && !isYesterday) Color.White else Pf.Text,
                            fontSize = 12.sp,
                            fontWeight = if (!isToday && !isYesterday) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 7. For / Ledger
        PfSelect("For / Ledger", vm.draft.person, vm.forOptions, vm::setDraftFor)

        // 8. Action Button
        val buttonText = when {
            evaluatedAmount <= 0.0 -> "Enter amount"
            vm.oneOffKind == "TRANSFER" -> "Transfer ${inr(evaluatedAmount)}"
            vm.oneOffKind == "INCOME" -> "Record Income · ${inr(evaluatedAmount)}"
            else -> "Record Expense · ${inr(evaluatedAmount)}"
        }

        PrimaryButton(
            buttonText,
            vm::saveDraft,
            Modifier.fillMaxWidth(),
            enabled = evaluatedAmount > 0.0 && vm.oneOffDateValid
        )
    }
}

@Composable
private fun GenericForm(vm: FinTrackViewModel, isEditing: Boolean) {
    val categoryOptions = if (!isEditing && vm.addKind == "INVESTMENT") {
        vm.categories.filter { it in INVEST_PICKABLE }.ifEmpty { vm.categories }
    } else {
        vm.categories
    }
    val notePlaceholder = if (isEditing) "Optional note" else when (vm.addKind) {
        "RECURRING" -> "e.g. Groceries, Wi-Fi, music class…"
        "SET_ASIDE" -> "e.g. Car insurance, school fees…"
        "INVESTMENT" -> "e.g. Monthly SIP, PPF contribution…"
        else -> "e.g. Groceries, electricity bill…"
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        HeroAmountInput(
            amountText = vm.draft.amountText,
            onAmountChange = { vm.draft = vm.draft.copy(amountText = it) },
            onQuickAdd = { delta ->
                val cur = MathEvaluator.evaluate(vm.draft.amountText) ?: vm.draft.amountText.toDoubleOrNull() ?: 0.0
                vm.draft = vm.draft.copy(amountText = MathEvaluator.formatResult(cur + delta))
            }
        )

        PfSelect("For", vm.draft.person, vm.forOptions, vm::setDraftFor)
        if (isEditing) {
            PfSelect(
                "Type", vm.draft.type, listOf("EXPENSE", "INCOME", "SAVINGS"),
                { vm.draft = vm.draft.copy(type = it) }
            )
        }
        PfSelect("Category", vm.draft.category, categoryOptions, { vm.draft = vm.draft.copy(category = it) })

        val context = LocalContext.current
        val calendar = Calendar.getInstance()
        val startDatePickerDialog = remember {
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    vm.draft = vm.draft.copy(startDateText = "%02d-%02d-%04d".format(dayOfMonth, month + 1, year))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
        val dueDatePickerDialog = remember {
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    vm.draft = vm.draft.copy(dueText = "%02d-%02d-%04d".format(dayOfMonth, month + 1, year))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }

        val isSetAsideKind = vm.addKind == "SET_ASIDE" || (isEditing && (vm.draft.isLent || vm.draft.type == "SAVINGS" || vm.draft.dueText.isNotEmpty() || vm.draft.startDateText.isNotEmpty() || vm.draft.startMonth.isNotEmpty() || vm.draft.periodMonths > 1 || vm.draft.frequency == "ANNUAL"))

        if (isSetAsideKind) {
            // Option to choose between Normal Set Aside and Lent Money
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                listOf(
                    false to "📌 Set Aside",
                    true to "🤝 Lent Money"
                ).forEach { (lent, label) ->
                    val isSel = vm.draft.isLent == lent
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(Radius.Pill)
                            .background(if (isSel) (if (lent) Pf.Amber.copy(alpha = 0.2f) else Pf.Accent.copy(alpha = 0.2f)) else Pf.Surface2)
                            .border(1.5.dp, if (isSel) (if (lent) Pf.Amber else Pf.Accent) else Pf.Hairline, Radius.Pill)
                            .clickable {
                                vm.draft = vm.draft.copy(
                                    isLent = lent,
                                    category = if (lent && vm.draft.category.isEmpty()) "Lent" else vm.draft.category
                                )
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (isSel) (if (lent) Pf.Amber else Pf.Accent400) else Pf.Text,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            val payMonthOptions = vm.startPayMonthOptions
            val selectedStartMonthKey = vm.draft.startMonth.ifEmpty { today().take(7) }
            val selectedStartMonthLabel = payMonthOptions.firstOrNull { it.key == selectedStartMonthKey }?.label
                ?: payMonthOptions.firstOrNull()?.label.orEmpty()

            PfSelect(
                label = "Start Pay Month",
                value = selectedStartMonthLabel,
                options = payMonthOptions.map { it.label },
                onSelect = { label ->
                    val opt = payMonthOptions.firstOrNull { it.label == label }
                    if (opt != null) {
                        vm.draft = vm.draft.copy(startMonth = opt.key)
                    }
                }
            )

            PfField(
                label = "Target / End Date",
                value = vm.draft.dueText,
                onValueChange = { vm.draft = vm.draft.copy(dueText = it) },
                placeholder = "dd-mm-yyyy",
                numeric = false,
                trailingIcon = {
                    IconButton(onClick = { dueDatePickerDialog.show() }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "End Date",
                            tint = Pf.Accent400
                        )
                    }
                }
            )

            val resetDay = vm.salaryResetDayFor(vm.draft.person)
            val paydaysList = vm.draftPaydaysList
            val instalments = vm.draftInstalments
            val evaluatedDraftAmount = MathEvaluator.evaluate(vm.draft.amountText) ?: vm.draft.amountText.toDoubleOrNull() ?: 0.0

            if (vm.draftDueIso.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.Md)
                        .background(Pf.Surface2)
                        .border(1.dp, Pf.Hairline, Radius.Md)
                        .padding(Space.s3)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Payday / Reset: ${resetDay}th of month",
                                color = Pf.Accent400,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Tag(
                                "$instalments payday${if (instalments > 1) "s" else ""}",
                                Pf.Accent100,
                                Pf.Accent800
                            )
                        }
                        if (paydaysList.isNotEmpty()) {
                            Text(
                                "Paydays: ${paydaysList.joinToString(", ")}",
                                color = Pf.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (evaluatedDraftAmount > 0.0) {
                            val monthlyShare = evaluatedDraftAmount / instalments.coerceAtLeast(1)
                            Text(
                                "Split equally: ${inr(monthlyShare)} / month",
                                color = Pf.Accent,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            } else {
                Muted("Select start pay month and target end date. The amount splits equally across the paydays (${resetDay}th of each month) between them.")
            }
        } else {
            PfField(
                "Due day of month (1-31)",
                vm.draft.dueText,
                { vm.draft = vm.draft.copy(dueText = it) },
                placeholder = "e.g. 29",
                numeric = true
            )
            val due = vm.draftDueIso
            if (due.isNotEmpty()) {
                Muted("Due in ${vm.draftDueIn}, then the same day each month.")
            }
        }

        PfSelect(
            "Bank account",
            vm.draftAccountName,
            vm.visibleAccounts.map { it.name },
            { name ->
                vm.draft = vm.draft.copy(
                    accountId = vm.visibleAccounts.firstOrNull { it.name == name }?.id.orEmpty()
                )
            }
        )

        PfField(
            "Note (optional)",
            vm.draft.note,
            { noteInput ->
                val autoCat = categoryForParty(noteInput, vm.categories, vm.smsRules)
                val newCat = if (autoCat.isNotBlank() && autoCat != UNCATEGORISED) autoCat else vm.draft.category
                vm.draft = vm.draft.copy(note = noteInput, category = newCat)
            },
            placeholder = notePlaceholder
        )
        val finalEvaluatedAmount = MathEvaluator.evaluate(vm.draft.amountText) ?: vm.draft.amountText.toDoubleOrNull() ?: 0.0
        PrimaryButton(
            if (isEditing) "Save changes" else "Save entry",
            vm::saveDraft,
            Modifier.fillMaxWidth(),
            enabled = finalEvaluatedAmount > 0
        )
    }
}
