package com.vinay.fintrack.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What the assistant is allowed to do. Everything the screens can do, described
 * so the model calls a function rather than inventing an answer about your
 * money.
 *
 * Reads are free. Writes go through the same ViewModel paths the screens use,
 * so a change made here syncs, moves balances and respects profiles exactly as
 * if it had been tapped.
 */
object AssistantTools {

    fun schema(): JsonArray = buildJsonArray {
        // ── reading ────────────────────────────────────────────────────
        add(tool(
            "get_overview",
            "Balances, this month's income, expenses, savings and investments, " +
                "the budgets and how much of each is used, and which profile and " +
                "side (personal or joint) is being viewed. Call this first for any " +
                "question about totals or 'how am I doing'."
        ))
        add(tool(
            "list_accounts",
            "Every account and card the current profile can see, with balances, " +
                "owners and the last digits used to match bank messages."
        ))
        add(tool(
            "list_commitments",
            "Recurring entries: monthly commitments, set-asides with their period, " +
                "loans with EMI and months remaining."
        ))
        add(tool(
            "list_transactions",
            "Recorded transactions, newest first. Use for anything about actual " +
                "spending, history, or finding a payment to edit or delete."
        ) {
            put("month", str("Restrict to a month as yyyy-MM. Omit for all."))
            put("category", str("Restrict to one category."))
            put("search", str("Match payee, note or reference."))
            put("limit", int("How many to return. Default 30."))
        })

        // ── transactions ───────────────────────────────────────────────
        add(tool(
            "add_transaction",
            "Record money that has already moved. Use for 'I paid 500 for groceries'."
        ) {
            put("amount", num("Rupees. Required."))
            put("category", str("One of the existing categories."))
            put("direction", enum("Which way the money went.", listOf("out", "in")))
            put("account", str("Account name. Defaults to the side being viewed."))
            put("note", str("Payee or description."))
            put("date", str("dd-MM-yyyy. Defaults to today."))
            required("amount")
        })
        add(tool("edit_transaction", "Change a recorded transaction.") {
            put("id", str("Transaction id from list_transactions. Required."))
            put("amount", num("New amount in rupees."))
            put("category", str("New category."))
            put("account", str("New account name."))
            put("note", str("New payee or description."))
            put("date", str("New date as dd-MM-yyyy."))
            required("id")
        })
        add(tool(
            "delete_transaction",
            "Remove a recorded transaction. Confirm with the user first — say what " +
                "it is and wait for a clear yes."
        ) {
            put("id", str("Transaction id from list_transactions. Required."))
            required("id")
        })

        // ── commitments ────────────────────────────────────────────────
        add(tool(
            "add_commitment",
            "Add a recurring entry — a monthly cost, or a set-aside paid every few " +
                "months. This is a plan, not a payment; it is confirmed each month."
        ) {
            put("amount", num("The full amount charged each time. Required."))
            put("category", str("One of the existing categories."))
            put("every_months", int("Months between payments, 1 to 12. Default 1."))
            put("kind", enum("What sort of commitment.", listOf("expense", "savings", "income")))
            put("joint", bool("True for shared, false for the current profile's own."))
            put("note", str("Description."))
            required("amount")
        })
        add(tool("edit_commitment", "Change a recurring entry.") {
            put("id", str("Entry id from list_commitments. Required."))
            put("amount", num("New amount."))
            put("category", str("New category."))
            put("every_months", int("New period in months, 1 to 12."))
            put("note", str("New description."))
            required("id")
        })
        add(tool("delete_commitment", "Remove a recurring entry. Confirm first.") {
            put("id", str("Entry id from list_commitments. Required."))
            required("id")
        })
        add(tool(
            "confirm_commitment",
            "Mark this month's payment of a commitment or loan as made, which moves " +
                "the money. A set-aside transfers rather than spends."
        ) {
            put("id", str("Entry or loan id. Required."))
            put("account", str("Account it came from. Defaults to the entry's own."))
            put("to_account", str("Where a set-aside transfer lands."))
            required("id")
        })

        // ── accounts, cards, loans ─────────────────────────────────────
        add(tool("add_account", "Add a bank account.") {
            put("name", str("Account name. Required."))
            put("balance", num("Current balance in rupees."))
            put("last_digits", str("Last 3-4 digits, for matching bank messages."))
            put("joint", bool("True for shared, false for the current profile's own."))
            required("name")
        })
        add(tool("add_card", "Add a credit card.") {
            put("name", str("Card name. Required."))
            put("limit", num("Credit limit. Required."))
            put("balance", num("Current outstanding."))
            put("min_due", num("Minimum due."))
            put("due", str("Due date, e.g. '18 Sep'."))
            put("last_digits", str("Last 3-4 digits, for matching card spends."))
            required("name", "limit")
        })
        add(tool("add_loan", "Add a loan with an EMI.") {
            put("name", str("Loan name. Required."))
            put("emi", num("Monthly EMI. Required."))
            put("total_months", int("Tenure in months. Required."))
            put("remaining_months", int("Months still to pay. Defaults to the tenure."))
            required("name", "emi", "total_months")
        })
        add(tool("update_account", "Change an account's name, balance or digits.") {
            put("name", str("Current account name. Required."))
            put("new_name", str("New name."))
            put("balance", num("New opening balance."))
            put("last_digits", str("New matching digits."))
            required("name")
        })

        // ── settings ───────────────────────────────────────────────────
        add(tool("set_budget", "Set or change a category's monthly budget.") {
            put("category", str("Category name. Required."))
            put("amount", num("Monthly limit in rupees. Zero removes it. Required."))
            required("category", "amount")
        })
        add(tool("add_category", "Add a spending category.") {
            put("name", str("Category name. Required."))
            required("name")
        })
        add(tool("set_default_account", "Change the default account.") {
            put("name", str("Account name. Required."))
            required("name")
        })
        add(tool(
            "switch_side",
            "Switch what is being viewed and what new entries default to."
        ) {
            put("joint", bool("True for the shared side, false for personal. Required."))
            required("joint")
        })
    }

    /**
     * Told to the model once. Deliberately firm about two things: never guess a
     * number, and confirm before destroying anything.
     */
    fun systemPrompt(profile: String, side: String, today: String): String = """
        You are the assistant inside FinTrack, a household finance app used by
        $profile. The screen is currently showing the $side side. Today is $today.

        You can read and change everything in the app through the tools provided.
        Use them — never state a balance, total or transaction from memory or
        assumption, and never invent an amount. If you need a figure, call a tool.

        How the app is arranged, so your answers match what the user sees:
        - A commitment is a plan that repeats. It only moves money when confirmed.
        - A transaction is money that actually moved. Balances come from these.
        - A set-aside is paid every few months; each month you put by a share of
          it, and confirming transfers that to a savings account rather than
          spending it.
        - Personal is this profile's own; Joint is shared. A transaction takes its
          side from the account it moved through.

        Before deleting anything, say exactly what will go and wait for a clear
        yes. For edits, say what you changed. Keep replies short and plain —
        this is a phone screen. Amounts in rupees, like ₹4,500.
    """.trimIndent()

    // ── schema helpers ─────────────────────────────────────────────────

    private class Params {
        val props = mutableMapOf<String, JsonObject>()
        val required = mutableListOf<String>()
        fun put(name: String, spec: JsonObject) { props[name] = spec }
        fun required(vararg names: String) { required += names }
    }

    private fun tool(
        name: String,
        description: String,
        params: (Params.() -> Unit)? = null
    ): JsonObject {
        val p = Params().apply { params?.invoke(this) }
        return buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", name)
                put("description", description)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        p.props.forEach { (key, spec) -> put(key, spec) }
                    }
                    putJsonArray("required") { p.required.forEach { add(it) } }
                }
            }
        }
    }

    private fun str(description: String) = buildJsonObject {
        put("type", "string"); put("description", description)
    }

    private fun num(description: String) = buildJsonObject {
        put("type", "number"); put("description", description)
    }

    private fun int(description: String) = buildJsonObject {
        put("type", "integer"); put("description", description)
    }

    private fun bool(description: String) = buildJsonObject {
        put("type", "boolean"); put("description", description)
    }

    private fun enum(description: String, values: List<String>) = buildJsonObject {
        put("type", "string")
        put("description", description)
        putJsonArray("enum") { values.forEach { add(it) } }
    }
}
