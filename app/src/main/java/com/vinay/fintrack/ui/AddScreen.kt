package com.vinay.fintrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel
import com.vinay.fintrack.data.INVEST_PICKABLE
import com.vinay.fintrack.data.inr

// Recurring and Set aside are separate kinds: one is paid every month, the
// other every few months and put by in between. They behave differently enough
// on Home that choosing between them belongs here, not in a period dropdown.
private val ADD_KINDS = listOf(
    "ONE_TIME" to "One-time",
    "RECURRING" to "Recurring",
    "SET_ASIDE" to "Set aside",
    "EMI_LOAN" to "EMI / Loan",
    "INVESTMENT" to "Investment",
    "BANK_ACCOUNT" to "Bank Account",
    "CREDIT_CARD" to "Credit Card"
)

/** A set-aside is paid every 2 to 12 months; every month would just be a
 *  recurring commitment, which is its own kind. */
private val PERIOD_OPTIONS = (2..12).map { "Every $it months" }

private fun periodLabel(months: Int) = "Every ${months.coerceIn(2, 12)} months"

private fun periodFromLabel(label: String) =
    label.filter { it.isDigit() }.toIntOrNull()?.coerceIn(2, 12) ?: 12

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
            item {
                Column {
                    Muted("What are you adding?", Modifier.padding(bottom = Space.s2))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADD_KINDS.forEach { (key, label) ->
                            Chip(label, vm.addKind == key, { vm.selectAddKind(key) }, Modifier.padding(bottom = 6.dp))
                        }
                    }
                    // One line, because the distinction is the whole point.
                    Muted(
                        when (vm.addKind) {
                            "ONE_TIME" -> "Already paid. Goes to Transactions now."
                            "SET_ASIDE" -> "Paid every few months. Put by a share each month."
                            "RECURRING" -> "Paid every month. Confirm it on Home."
                            else -> ""
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

// Smart Add lived here: a second, weaker chat beside the real one. The Chat tab
// reads and writes everything, so this screen is just the forms now.

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
        // Accounts and cards together: a card EMI is a purchase split into
        // instalments, which is the same arrangement paid to a different place.
        PfSelect("Paid from", vm.newLoanSourceName, vm.emiSourceOptions, vm::setLoanSource)
        PfField(
            "First EMI due on",
            vm.newLoanDraft.dueText,
            { vm.newLoanDraft = vm.newLoanDraft.copy(dueText = it) },
            placeholder = "dd-mm-yyyy, e.g. 05-09-2026"
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
            PfField("Bill due on", vm.newCardDraft.dueText, { vm.newCardDraft = vm.newCardDraft.copy(dueText = it) }, Modifier.weight(1f), "e.g. 18-09-2026")
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
        "RECURRING" -> "e.g. Groceries, Wi-Fi, music class…"
        "SET_ASIDE" -> "e.g. Car insurance, school fees…"
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
            // A due date suits both: a set-aside needs it to work out the
            // monthly share, and a recurring bill uses it to say when it is
            // next payable rather than sitting there confirmable all month.
            if (isEditing || vm.addKind == "SET_ASIDE" || vm.addKind == "RECURRING") {
                PfField(
                    "Due on",
                    vm.draft.dueText,
                    { vm.draft = vm.draft.copy(dueText = it) },
                    placeholder = "dd-mm-yyyy, e.g. 29-01-2027"
                )
                val amount = vm.draft.amountText.toDoubleOrNull() ?: 0.0
                val due = vm.draftDueIso
                val setAside = isEditing || vm.addKind == "SET_ASIDE"
                when {
                    due.isNotEmpty() && !setAside ->
                        Muted("Due in ${vm.draftDueIn}, then the same day each month.")
                    due.isNotEmpty() && amount > 0 -> {
                        val months = vm.draftInstalments
                        Muted(
                            "Due in ${vm.draftDueIn} — put by " +
                                "${inr(amount / months.coerceAtLeast(1))} a month over " +
                                "$months month${if (months == 1) "" else "s"}. Confirming that " +
                                "on Home moves it to savings rather than spending it."
                        )
                    }
                    due.isNotEmpty() -> Muted("Due in ${vm.draftDueIn}. Add the amount.")
                    vm.draft.dueText.isNotBlank() ->
                        Muted("That isn't a date yet — write it as 29-01-2027.")
                    setAside ->
                        Muted("Give the date it is due and the full amount; the monthly " +
                            "share is worked out from the months left.")
                    else -> Muted("The day it comes out each month, if you know it.")
                }
                // Only a set-aside splits an amount over months, and only when no
                // date is known — an older one, or a bill you would rather not pin
                // down. A recurring bill is monthly by definition.
                if (due.isEmpty() && setAside) {
                    PfSelect(
                        "Or split evenly over",
                        periodLabel(vm.draft.periodMonths),
                        PERIOD_OPTIONS,
                        { vm.draft = vm.draft.copy(periodMonths = periodFromLabel(it)) }
                    )
                }
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
