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

private val ADD_KINDS = listOf(
    "EXPENSE" to "Expense",
    "BILL" to "Bill",
    "EMI_LOAN" to "EMI / Loan",
    "INVESTMENT" to "Investment",
    "ONE_TIME" to "One-time",
    "BANK_ACCOUNT" to "Bank Account",
    "CREDIT_CARD" to "Credit Card"
)

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
        "BILL" -> "e.g. Car insurance, Wi-Fi renewal…"
        "INVESTMENT" -> "e.g. Monthly SIP, PPF contribution…"
        "ONE_TIME" -> "e.g. Diwali gift, appliance purchase…"
        else -> "e.g. Groceries, electricity bill…"
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        PfSelect("Person", vm.draft.person, vm.draftPersonOptions, { person ->
            vm.draft = vm.draft.copy(person = person, bucket = if (person == "Joint") "JOINT" else vm.draft.bucket)
        })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            if (isEditing) {
                PfSelect("Type", vm.draft.type, listOf("EXPENSE", "INCOME", "SAVINGS"), { vm.draft = vm.draft.copy(type = it) }, Modifier.weight(1f))
            }
            PfSelect(
                "Bucket", vm.draft.bucket, listOf("JOINT", "PERSONAL"),
                { vm.draft = vm.draft.copy(bucket = it) },
                Modifier.weight(1f),
                enabled = vm.draft.person != "Joint"
            )
        }
        PfSelect("Category", vm.draft.category, categoryOptions, { vm.draft = vm.draft.copy(category = it) })
        PfField("Amount (₹)", vm.draft.amountText, { vm.draft = vm.draft.copy(amountText = it) }, placeholder = "e.g. 5000", numeric = true)
        if (isEditing || vm.addKind != "ONE_TIME") {
            PfSelect("Frequency", vm.draft.frequency, listOf("MONTHLY", "ANNUAL"), { vm.draft = vm.draft.copy(frequency = it) })
        }
        PfField("Note (optional)", vm.draft.note, { vm.draft = vm.draft.copy(note = it) }, placeholder = notePlaceholder)
        PrimaryButton(
            if (isEditing) "Save changes" else "Save entry",
            vm::saveDraft,
            Modifier.fillMaxWidth(),
            enabled = (vm.draft.amountText.toDoubleOrNull() ?: 0.0) > 0
        )
    }
}
