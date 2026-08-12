package com.vinay.fintrack

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vinay.fintrack.data.AssistantTools
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.OpenAi
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.Txn
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.isoFromDayFirst
import com.vinay.fintrack.data.today
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch

/**
 * The chat assistant: a conversation with tools that read and change the real
 * app data, rather than a model guessing about it.
 *
 * The network call runs on a worker thread; every tool runs on the main thread,
 * because it touches the same Compose state the screens do. The loop is
 * bounded — a model that keeps calling tools stops rather than spending your
 * credit indefinitely.
 */
class Assistant(private val vm: FinTrackViewModel) {

    private val json = Json { ignoreUnknownKeys = true }
    private val main = Handler(Looper.getMainLooper())

    /** Runs the whole exchange. Call from a worker thread. */
    fun run(history: JsonArray, apiKey: String): Result {
        val client = OpenAi(apiKey)
        var messages = history
        var changed = false

        repeat(MAX_ROUNDS) {
            val reply = client.chat(messages, AssistantTools.schema())
            val calls = reply["tool_calls"] as? JsonArray

            if (calls.isNullOrEmpty()) {
                val text = reply["content"]?.jsonPrimitive?.contentOrNullSafe()
                return Result(text.orEmpty().ifBlank { "Done." }, messages + reply, changed)
            }

            // The assistant's request has to be in the history before its results.
            var next = messages + reply
            for (call in calls) {
                val obj = call as? JsonObject ?: continue
                val fn = obj["function"] as? JsonObject
                val name = fn?.get("name")?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                val rawArgs = fn?.get("arguments")?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                val args = runCatching { json.parseToJsonElement(rawArgs) as JsonObject }
                    .getOrDefault(JsonObject(emptyMap()))

                if (name in WRITERS) changed = true
                val result = onMain { dispatch(name, args) }

                next = next + buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", obj["id"]?.jsonPrimitive?.contentOrNullSafe().orEmpty())
                    put("content", result)
                }
            }
            messages = next
        }
        return Result(
            "That needed more steps than I'm allowed in one go. Ask me again, " +
                "more specifically.",
            messages,
            changed
        )
    }

    class Result(val reply: String, val history: JsonArray, val changed: Boolean)

    // ── running the tools ──────────────────────────────────────────────

    private fun dispatch(name: String, a: JsonObject): String = runCatching {
        when (name) {
            "get_overview" -> overview()
            "list_accounts" -> accountsText()
            "list_commitments" -> commitmentsText()
            "list_transactions" -> transactionsText(a)

            "add_transaction" -> addTransaction(a)
            "edit_transaction" -> editTransaction(a)
            "delete_transaction" -> {
                val t = vm.txnById(a.str("id").orEmpty())
                    ?: return@runCatching "No transaction with that id."
                vm.deleteTxn(t.id)
                "Deleted ${inr(t.amount)} · ${t.note.ifEmpty { t.category }}."
            }

            "add_commitment" -> addCommitment(a)
            "edit_commitment" -> {
                val e = vm.updateCommitment(
                    a.str("id").orEmpty(), a.num("amount"),
                    a.str("category")?.let { vm.categoryNamed(it) },
                    a.int("every_months"), a.str("note"), a.str("due_date")
                ) ?: return@runCatching "No commitment with that id."
                "Updated ${e.category}: ${inr(e.amount)} every ${e.everyMonths} month(s)" +
                    (if (e.nextDue.isNotEmpty()) ", due ${prettyDate(e.nextDue)}" else "") +
                    " — ${inr(e.monthly)} a month."
            }
            "delete_commitment" -> {
                val e = vm.entryById(a.str("id").orEmpty())
                    ?: return@runCatching "No commitment with that id."
                vm.deleteEntry(e.id)
                "Deleted the ${e.category} commitment."
            }
            "confirm_commitment" -> vm.confirmDirect(
                a.str("id").orEmpty(),
                a.str("account")?.let { vm.accountNamed(it)?.id },
                a.str("to_account")?.let { vm.accountNamed(it)?.id }
            )

            "add_account" -> {
                val acc = vm.addAccountDirect(
                    a.str("name").orEmpty(), a.num("balance") ?: 0.0,
                    a.str("last_digits").orEmpty(), a.bool("joint") ?: false
                )
                "Added ${acc.name}, opening ${inr(acc.openingBalance)}."
            }
            "add_card" -> {
                val c = vm.addCardDirect(
                    a.str("name").orEmpty(), a.num("limit") ?: 0.0, a.num("balance") ?: 0.0,
                    a.num("min_due") ?: 0.0, a.str("due").orEmpty(), a.str("last_digits").orEmpty()
                )
                "Added the card ${c.name}, limit ${inr(c.limit)}."
            }
            "add_loan" -> {
                val total = a.int("total_months") ?: 0
                val l = vm.addLoanDirect(
                    a.str("name").orEmpty(), a.num("emi") ?: 0.0, total,
                    a.int("remaining_months") ?: total,
                    a.str("card").orEmpty(), a.str("account").orEmpty(),
                    a.str("due_date").orEmpty()
                )
                "Added ${l.name}, ${inr(l.monthlyEmi)} a month — ${vm.emiSourceLabel(l)}" +
                    (if (l.nextDue.isNotEmpty()) ", due ${prettyDate(l.nextDue)}" else "") + "."
            }
            "update_account" -> {
                val acc = vm.accountNamed(a.str("name").orEmpty())
                    ?: return@runCatching "No account by that name."
                val updated = vm.updateAccountDirect(
                    acc.id, a.str("new_name"), a.num("balance"), a.str("last_digits")
                )
                "Updated ${updated?.name}."
            }

            "set_budget" -> {
                val cat = vm.categoryNamed(a.str("category").orEmpty())
                val amount = a.num("amount") ?: 0.0
                if (amount <= 0) { vm.removeBudget(cat); "Removed the $cat budget." }
                else { vm.setBudget(cat, amount); "$cat budget set to ${inr(amount)} a month." }
            }
            "add_category" -> {
                val n = a.str("name").orEmpty()
                vm.addCategoryNamed(n); "Added the category $n."
            }
            "set_default_account" -> {
                val acc = vm.accountNamed(a.str("name").orEmpty())
                    ?: return@runCatching "No account by that name."
                vm.setDefaultAccount(acc.name); "Default account is now ${acc.name}."
            }
            "switch_side" -> {
                val joint = a.bool("joint") ?: false
                vm.setScope(joint)
                "Showing the ${if (joint) "joint" else "personal"} side."
            }

            else -> "Unknown tool: $name"
        }
    }.getOrElse {
        Log.w(TAG, "tool $name failed", it)
        "That didn't work: ${it.message ?: "unexpected error"}"
    }

    // ── reads ──────────────────────────────────────────────────────────

    private fun overview(): String = buildString {
        appendLine("Profile: ${vm.activeProfile.orEmpty()}, viewing the ${vm.bucketLabel} side.")
        appendLine("Total balance: ${inr(vm.totalBalance)}")
        appendLine("Recorded this month — received ${inr(vm.actualIncome)}, " +
            "spent ${inr(vm.actualSpent)}, set aside ${inr(vm.actualSaved)}, " +
            "invested ${inr(vm.actualInvested)}")
        appendLine("Planned each month — ${inr(vm.plannedIncome)} in, " +
            "${inr(vm.plannedExpense)} out")
        appendLine("Set aside needed this month: ${inr(vm.annualSetAsideMonthly)}, " +
            "done ${inr(vm.annualSetAsideDone)}")
        if (vm.budgets.isEmpty()) appendLine("No budgets set.")
        else {
            appendLine("Budgets (spent of limit):")
            vm.budgets.forEach { (cat, limit) ->
                appendLine("  $cat: ${inr(vm.spendFor(cat))} of ${inr(limit)}")
            }
        }
        appendLine("Categories: ${vm.categories.joinToString(", ")}")
    }

    private fun accountsText(): String = buildString {
        appendLine("Accounts:")
        vm.visibleAccounts.forEach {
            appendLine("  ${it.name} — ${inr(vm.balanceOf(it))}, owner ${it.person}" +
                if (it.numberTail.isNotBlank()) ", ends ${it.numberTail}" else "")
        }
        if (vm.visibleCards.isEmpty()) appendLine("No cards.")
        else {
            appendLine("Cards:")
            vm.visibleCards.forEach {
                appendLine("  ${it.name} — owes ${inr(it.balance)} of ${inr(it.limit)}, " +
                    "due ${it.due}${if (it.paid) ", paid" else ""}")
            }
        }
    }

    private fun commitmentsText(): String = buildString {
        appendLine("Monthly commitments:")
        vm.commitments.forEach {
            appendLine("  [${it.id}] ${it.category} ${inr(it.amount)} — ${it.person}" +
                (if (it.note.isNotBlank()) " (${it.note})" else "") +
                if (vm.isConfirmed(it.id)) " — done this month" else "")
        }
        appendLine("Set-asides:")
        vm.annualSetAsides.forEach {
            appendLine("  [${it.id}] ${it.category} ${inr(it.amount)} every ${it.everyMonths} " +
                "month(s) = ${inr(it.monthly)}/mo" +
                (if (it.nextDue.isNotEmpty()) ", due ${prettyDate(it.nextDue)}" else "") +
                " — ${it.person}" +
                if (vm.isConfirmed(it.id)) " — done this month" else "")
        }
        appendLine("Loans:")
        vm.visibleLoans.forEach {
            appendLine("  [${it.id}] ${it.name} ${inr(it.monthlyEmi)}/mo, " +
                "${it.remainingMonths} of ${it.totalMonths} months left, ${vm.emiSourceLabel(it)}" +
                (if (it.nextDue.isNotEmpty()) ", due ${prettyDate(it.nextDue)}" else "") +
                if (vm.isLoanConfirmed(it.id)) " — paid this month" else "")
        }
    }

    private fun transactionsText(a: JsonObject): String {
        val month = a.str("month")
        val category = a.str("category")
        val search = a.str("search")
        val limit = (a.int("limit") ?: 30).coerceIn(1, 100)

        val rows = vm.txns
            .filter { month == null || it.month == month }
            .filter { category == null || it.category.equals(category, true) }
            .filter {
                search == null || it.note.contains(search, true) ||
                    it.category.contains(search, true) || it.ref.contains(search, true)
            }
            .sortedByDescending { it.sortKey }
            .take(limit)

        if (rows.isEmpty()) return "No transactions match."
        return buildString {
            appendLine("${rows.size} transaction(s):")
            rows.forEach { appendLine("  ${describe(it)}") }
        }
    }

    private fun describe(t: Txn): String {
        val account = vm.txnAccountLabel(t)
        val sign = when (t.kind) {
            "INCOME" -> "+"
            "TRANSFER" -> "moved "
            else -> "-"
        }
        return "[${t.id}] ${t.whenText} $sign${inr(t.amount)} ${t.category}" +
            (if (t.note.isNotBlank()) " · ${t.note}" else "") +
            (if (account.isNotBlank()) " · $account" else "") +
            (if (t.source.isNotBlank()) " · ${t.source}" else "")
    }

    // ── writes ─────────────────────────────────────────────────────────

    private fun addTransaction(a: JsonObject): String {
        val amount = a.num("amount") ?: return "I need an amount."
        val credit = a.str("direction") == "in"
        val category = vm.categoryNamed(a.str("category").orEmpty())
        val account = a.str("account")?.let { vm.accountNamed(it) }
            ?: vm.accountNamed(vm.oneOffAccountName)
        val accountId = account?.id ?: vm.resolvedOneOffAccount
        val date = a.str("date")?.let { isoFromDayFirst(it) } ?: today()
        val t = vm.addTransactionDirect(
            amount, category, credit, accountId, a.str("note").orEmpty().ifEmpty { category }, date
        )
        return "Recorded ${describe(t)}."
    }

    private fun editTransaction(a: JsonObject): String {
        val id = a.str("id") ?: return "I need the transaction id."
        val t = vm.updateTransaction(
            id = id,
            amount = a.num("amount"),
            category = a.str("category")?.let { vm.categoryNamed(it) },
            accountId = a.str("account")?.let { vm.accountNamed(it)?.id },
            note = a.str("note"),
            dateIso = a.str("date")?.let { isoFromDayFirst(it) }
        ) ?: return "No transaction with that id."
        return "Updated ${describe(t)}."
    }

    private fun addCommitment(a: JsonObject): String {
        val amount = a.num("amount") ?: return "I need an amount."
        val type = when (a.str("kind")) {
            "savings" -> "SAVINGS"
            "income" -> "INCOME"
            else -> "EXPENSE"
        }
        val e = vm.addCommitmentDirect(
            amount = amount,
            category = vm.categoryNamed(a.str("category").orEmpty()),
            everyMonths = a.int("every_months") ?: 1,   // schema requires it; 1 only if omitted anyway
            type = type,
            // Personal unless the user actually said shared. Following whichever
            // side happened to be on screen put private commitments on the joint
            // side because of where the user was standing at the time.
            joint = a.bool("joint") ?: false,
            note = a.str("note").orEmpty(),
            dueDate = a.str("due_date").orEmpty()
        )
        val side = if (e.bucket == "JOINT") "joint" else "personal"
        // Always states the figures it worked out, so a period or a date read
        // wrongly shows up as a number the user can see is wrong, rather than
        // as a plausible sentence.
        return when {
            e.nextDue.isNotEmpty() ->
                "Added ${e.category} ${inr(e.amount)} due ${prettyDate(e.nextDue)} on the " +
                    "$side side — ${inr(e.monthly)} a month over the " +
                    "${Ledger.instalmentsUntil(today(), e.nextDue)} months left."
            e.everyMonths > 1 ->
                "Added ${e.category} ${inr(e.amount)} every ${e.everyMonths} months on the " +
                    "$side side — ${inr(e.monthly)} to set aside each month."
            else ->
                "Added ${e.category} on the $side side as ${inr(e.amount)} charged every " +
                    "single month, ${inr(e.amount * 12)} a year. Tell the user this plainly, " +
                    "and check it is what they meant if they described one due date."
        }
    }

    // ── plumbing ───────────────────────────────────────────────────────

    /** Tools touch Compose state, so they run where the screens do. */
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        var error: Throwable? = null
        val latch = CountDownLatch(1)
        main.post {
            try { result = block() } catch (t: Throwable) { error = t } finally { latch.countDown() }
        }
        latch.await()
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.num(key: String): Double? = str(key)?.toDoubleOrNull()
    private fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
    private fun JsonObject.bool(key: String): Boolean? = str(key)?.lowercase()?.let {
        when (it) { "true" -> true; "false" -> false; else -> null }
    }

    private companion object {
        const val TAG = "Assistant"
        const val MAX_ROUNDS = 6

        /** Calls that change data, so the chat can offer to undo. */
        val WRITERS = setOf(
            "add_transaction", "edit_transaction", "delete_transaction",
            "add_commitment", "edit_commitment", "delete_commitment", "confirm_commitment",
            "add_account", "add_card", "add_loan", "update_account",
            "set_budget", "add_category", "set_default_account"
        )
    }
}

/** `content` is null on a tool-call reply, and `.content` would throw. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private operator fun JsonArray.plus(item: JsonObject): JsonArray =
    buildJsonArray { this@plus.forEach { add(it) }; add(item) }
