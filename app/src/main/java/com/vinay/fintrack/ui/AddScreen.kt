package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.INVEST_PICKABLE
import com.vinay.fintrack.data.inr

// Monthly expense and Monthly bill are gone — both read as "money I just spent"
// and produced a plan that never reached Transactions. One "Recurring / Set
// aside" replaces them, since without it there is no way to create a set-aside
// at all.
private val ADD_KINDS = listOf(
    "ONE_TIME" to "One-time",
    "RECURRING" to "Recurring / Set aside",
    "EMI_LOAN" to "EMI / Loan",
    "INVESTMENT" to "Investment",
    "BANK_ACCOUNT" to "Bank Account",
    "CREDIT_CARD" to "Credit Card"
)

/** 1–12 months between payments. 1 is an ordinary monthly commitment, 12 the
 *  old "annual"; anything above 1 is set aside a month at a time. */
private val PERIOD_OPTIONS = (1..12).map { if (it == 1) "Every month" else "Every $it months" }

private fun periodLabel(months: Int) = PERIOD_OPTIONS[(months.coerceIn(1, 12)) - 1]

private fun periodFromLabel(label: String) = PERIOD_OPTIONS.indexOf(label) + 1

@OptIn(ExperimentalLayoutApi::class)
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
            item { SmartAdd(vm) }
            item { Hairline() }
            item {
                Column {
                    Muted("What are you adding?", Modifier.padding(bottom = Space.s2))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADD_KINDS.forEach { (key, label) ->
                            Chip(label, vm.addKind == key, { vm.selectAddKind(key) }, Modifier.padding(bottom = 6.dp))
                        }
                    }
                    // The difference that keeps catching people out.
                    Muted(
                        if (vm.addKind == "ONE_TIME") {
                            "Money that has already gone. Recorded in Transactions straight away."
                        } else {
                            "A commitment that repeats. It waits on Home and only reaches " +
                                "Transactions when you confirm it each month."
                        },
                        Modifier.padding(top = Space.s2)
                    )
                }
            }
        }

        val showLoan = !isEditing && vm.addKind == "EMI_LOAN"
        val showAccount = !isEditing && vm.addKind == "BANK_ACCOUNT"
        val showCard = !isEditing && vm.addKind == "CREDIT_CARD"
        val showGeneric = isEditing || vm.addKind !in listOf("EMI_LOAN", "BANK_ACCOUNT", "CREDIT_CARD")

        if (showLoan) item { LoanForm(vm) }
        if (showAccount) item { AccountForm(vm) }
        if (showCard) item { CardForm(vm) }
        if (showGeneric) item { GenericForm(vm, isEditing) }
    }
}

@Composable
private fun SmartAdd(vm: FinTrackViewModel) {
    Column {
        Text("Smart Add", Modifier.padding(bottom = 6.dp), color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Column(
            Modifier
                .heightIn(max = 170.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.s2),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            vm.chatMessages.forEach { m ->
                val user = m.role == "user"
                Box(
                    Modifier
                        .fillMaxWidth(),
                    contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Text(
                        m.text,
                        Modifier
                            .fillMaxWidth(0.85f)
                            .background(if (user) Pf.Accent else Pf.Surface2, Radius.Sm)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (user) Color.White else Pf.Text,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            verticalAlignment = Alignment.Bottom
        ) {
            PfField(
                value = vm.smartText,
                onValueChange = { vm.smartText = it },
                placeholder = "e.g. 22k EMI, 4500 wife music class",
                modifier = Modifier.weight(1f)
            )
            PrimaryButton("Send", vm::parseSmart, enabled = vm.smartText.isNotBlank())
        }
    }
}

@Composable
private fun LoanForm(vm: FinTrackViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        PfField("Loan name", vm.newLoanDraft.name, { vm.newLoanDraft = vm.newLoanDraft.copy(name = it) }, placeholder = "e.g. Car loan — Me")
        PfSelect("Person", vm.newLoanDraft.person, vm.draftPersonOptions, { vm.newLoanDraft = vm.newLoanDraft.copy(person = it) })
        PfField("Monthly EMI (₹)", vm.newLoanDraft.emiText, { vm.newLoanDraft = vm.newLoanDraft.copy(emiText = it) }, placeholder = "e.g. 22000", numeric = true)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Tenure (months)", vm.newLoanDraft.totalMonthsText, { vm.newLoanDraft = vm.newLoanDraft.copy(totalMonthsText = it) }, Modifier.weight(1f), "e.g. 84", numeric = true)
            PfField("Months remaining", vm.newLoanDraft.remainingMonthsText, { vm.newLoanDraft = vm.newLoanDraft.copy(remainingMonthsText = it) }, Modifier.weight(1f), "e.g. 42", numeric = true)
        }
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
private fun AccountForm(vm: FinTrackViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        PfField("Account name", vm.newAccountDraft.name, { vm.newAccountDraft = vm.newAccountDraft.copy(name = it) }, placeholder = "e.g. HDFC Savings")
        PfSelect("Owner", vm.newAccountDraft.owner, vm.draftPersonOptions, { vm.newAccountDraft = vm.newAccountDraft.copy(owner = it) })
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
        PfSelect("Owner", vm.newCardDraft.owner, vm.draftPersonOptions, { vm.newCardDraft = vm.newCardDraft.copy(owner = it) })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Credit limit (₹)", vm.newCardDraft.limitText, { vm.newCardDraft = vm.newCardDraft.copy(limitText = it) }, Modifier.weight(1f), "e.g. 300000", numeric = true)
            PfField("Current balance (₹)", vm.newCardDraft.balanceText, { vm.newCardDraft = vm.newCardDraft.copy(balanceText = it) }, Modifier.weight(1f), "e.g. 42500", numeric = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PfField("Minimum due (₹)", vm.newCardDraft.minDueText, { vm.newCardDraft = vm.newCardDraft.copy(minDueText = it) }, Modifier.weight(1f), "e.g. 2200", numeric = true)
            PfField("Due date", vm.newCardDraft.due, { vm.newCardDraft = vm.newCardDraft.copy(due = it) }, Modifier.weight(1f), "e.g. 18 Sep")
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
            enabled = vm.newCardDraft.name.isNotBlank() && (vm.newCardDraft.limitText.toDoubleOrNull() ?: 0.0) > 0
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
        "RECURRING" -> "e.g. Car insurance, school fees…"
        "INVESTMENT" -> "e.g. Monthly SIP, PPF contribution…"
        "ONE_TIME" -> "e.g. Diwali gift, appliance purchase…"
        else -> "e.g. Groceries, electricity bill…"
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        // One choice, not two: "Joint" and your own name said everything the
        // separate Person and Bucket selects said between them, and the pair
        // could be set to combinations that meant nothing.
        PfSelect("For", vm.draft.person, vm.forOptions, vm::setDraftFor)
        if (isEditing) {
            PfSelect(
                "Type", vm.draft.type, listOf("EXPENSE", "INCOME", "SAVINGS"),
                { vm.draft = vm.draft.copy(type = it) }
            )
        }
        PfSelect("Category", vm.draft.category, categoryOptions, { vm.draft = vm.draft.copy(category = it) })
        PfField("Amount (₹)", vm.draft.amountText, { vm.draft = vm.draft.copy(amountText = it) }, placeholder = "e.g. 5000", numeric = true)
        val oneOff = !isEditing && vm.addKind == "ONE_TIME"
        if (!oneOff) {
            PfSelect(
                "Repeats",
                periodLabel(vm.draft.periodMonths),
                PERIOD_OPTIONS,
                { vm.draft = vm.draft.copy(periodMonths = periodFromLabel(it)) }
            )
            if (vm.draft.periodMonths > 1) {
                val amount = vm.draft.amountText.toDoubleOrNull() ?: 0.0
                Muted(
                    "Set aside ${inr(amount / vm.draft.periodMonths)} a month " +
                        "towards it, on the Home screen."
                )
            }
            // Which account this is paid from, asked once here so confirming it
            // later starts from the right account instead of the joint default.
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
        } else {
            // A one-off is money that already moved, so it needs an account and
            // a direction — it becomes a transaction, not something to confirm
            // again every month.
            // Only accounts on the chosen side, so the account can't contradict
            // the For choice — a transaction takes its side from its account.
            PfSelect(
                "Account",
                vm.oneOffAccountName,
                vm.oneOffAccountOptions.map { it.name },
                { name ->
                    vm.setOneOffAccount(
                        vm.oneOffAccountOptions.firstOrNull { it.name == name }?.id.orEmpty()
                    )
                }
            )
            PfSelect(
                "Direction",
                if (vm.oneOffIsCredit) "Money in" else "Money out",
                listOf("Money out", "Money in"),
                { vm.oneOffIsCredit = it == "Money in" }
            )
            PfField(
                "Date",
                vm.oneOffDateText,
                { vm.oneOffDateText = it },
                placeholder = "dd-mm-yyyy"
            )
            if (!vm.oneOffDateValid) {
                Text("Use dd-mm-yyyy, e.g. ${vm.todayDayFirstText}", color = Pf.Accent400, fontSize = 12.sp)
            }
        }
        PfField("Note (optional)", vm.draft.note, { vm.draft = vm.draft.copy(note = it) }, placeholder = notePlaceholder)
        PrimaryButton(
            when {
                isEditing -> "Save changes"
                oneOff -> "Record payment"
                else -> "Save entry"
            },
            vm::saveDraft,
            Modifier.fillMaxWidth(),
            enabled = (vm.draft.amountText.toDoubleOrNull() ?: 0.0) > 0 &&
                (!oneOff || vm.oneOffDateValid)
        )
        if (oneOff) {
            Muted("Goes straight to Transactions and moves the account balance.")
        }
    }
}
