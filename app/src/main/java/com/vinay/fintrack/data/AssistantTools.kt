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
            "Balances, this month's figures, budgets used, and which side is shown."
        ))
        add(tool(
            "list_accounts",
            "Accounts and cards with balances, owners and matching digits."
        ))
        add(tool(
            "list_commitments",
            "Commitments, set-asides and loans, with periods and months left."
        ))
        add(tool(
            "summarise_spending",
            "Totals per category and month, worked out by the app. Use for any " +
                "spending question; never add up rows yourself."
        ) {
            put("months", int("How many months back, 1 to 12. Default 3."))
            put("category", str("Restrict to one category. Omit for all."))
        })
        add(tool(
            "due_soon",
            "What falls due soon — bills, EMIs, card statements, set-asides — and " +
                "what is saved towards each."
        ) {
            put("days", int("How far ahead to look, 1 to 60. Default 14."))
        })
        add(tool(
            "list_transactions",
            "Individual transactions grouped by date, newest first. In chat, users can reference them by date and day serial number starting at 1 per day (e.g. 'yesterday 1', 'yesterday first 5 transactions', 'today 2')."
        ) {
            put("month", str("Restrict to a month as yyyy-MM. Omit for all."))
            put("category", str("Restrict to one category."))
            put("search", str("Match payee, note or reference."))
            put("from", str("Earliest date, YYYY-MM-DD."))
            put("to", str("Latest date, YYYY-MM-DD."))
            put("limit", int("How many to return. Default 50."))
        })

        // ── transactions ───────────────────────────────────────────────
        add(tool(
            "add_transaction",
            "Record money that has ALREADY moved (spent, received, or transferred) on or before today. STRICTLY for past/completed payments. NEVER use for future dates, upcoming purchases, or planned savings (use add_commitment instead)."
        ) {
            put("amount", num("Rupees. Required."))
            put("category", str("One of the existing categories."))
            put("direction", enum("Which way the money went.", listOf("out", "in")))
            put("account", str("Account name."))
            put("note", str("Payee or description."))
            put("date", str("dd-MM-yyyy. Default today. MUST NOT be a future date."))
            required("amount")
        })
        add(tool("edit_transaction", "Change a recorded transaction.") {
            put("id", str("Transaction id. Required."))
            put("amount", num("New amount in rupees."))
            put("category", str("New category."))
            put("account", str("New account name."))
            put("note", str("New payee or description."))
            put("date", str("New date as dd-MM-yyyy."))
            required("id")
        })
        add(tool(
            "delete_transaction",
            "Remove a transaction. The user confirms it."
        ) {
            put("id", str("Transaction id. Required."))
            required("id")
        })

        // ── commitments ────────────────────────────────────────────────
        add(tool(
            "add_commitment",
            "Add a plan or goal to Set Aside or Recurring bills. Use this for ANY upcoming purchase, future planned expense, goal, periodic bill, or monthly commitment (e.g. 'i need to buy spects for 2500 on 15th sep', 'plan 50000 for insurance on 10 Oct', 'rent 20000/mo'). Never record future expenses as transactions."
        ) {
            put("amount", num(
                "The total amount of the planned purchase, goal, or periodic bill. Required."
            ))
            put("category", str("One of the existing categories."))
            put("every_months", int(
                "How many months between one payment and the next (1 to 12). Default 1 for single goal/purchase with due date or monthly bills; 12 for yearly bills."
            ))
            put("start_date", str(
                "When the set-aside or plan starts, as YYYY-MM-DD or dd-MM-yyyy. Default today."
            ))
            put("due_date", str(
                "When the planned purchase or bill is due, as YYYY-MM-DD (e.g. '2026-09-15') or dd-MM-yyyy."
            ))
            put("kind", enum("What sort of commitment.", listOf("expense", "savings", "income")))
            put("joint", bool(
                "True ONLY if the user says this is joint, shared, household or both of you. Default false."
            ))
            put("note", str("Description of the item or purchase (e.g. 'Spectacles')."))
            required("amount")
        })
        add(tool("edit_commitment", "Change a recurring entry or set aside.") {
            put("id", str("Entry id. Required."))
            put("amount", num("New amount."))
            put("category", str("New category."))
            put("every_months", int("New period in months, 1 to 12."))
            put("start_date", str("New start date as YYYY-MM-DD or dd-MM-yyyy."))
            put("due_date", str("New due date as YYYY-MM-DD or dd-MM-yyyy."))
            put("note", str("New description."))
            required("id")
        })
        add(tool("delete_commitment", "Remove a recurring entry. The user confirms it.") {
            put("id", str("Entry id. Required."))
            required("id")
        })
        add(tool(
            "pay_set_aside",
            "Pay the bill a set-aside saved for, out of the pot. The due date then " +
                "moves on, or the entry closes."
        ) {
            put("id", str("Entry id. Required."))
            put("account", str("Account to pay from. Default: where it was saved."))
            required("id")
        })
        add(tool(
            "close_commitment",
            "Mark a commitment finished. It keeps its history but leaves the plan."
        ) {
            put("id", str("Entry id. Required."))
            put("closed", bool("False to bring it back. Default true."))
            required("id")
        })
        add(tool(
            "confirm_commitment",
            "Mark this cycle's payment as made, which moves the money."
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
        add(tool(
            "add_loan",
            "A bank loan, or a purchase split into instalments on a card."
        ) {
            put("name", str("Loan name. Required."))
            put("emi", num("Monthly EMI. Required."))
            put("total_months", int("Tenure in months. Required."))
            put("remaining_months", int("Months still to pay. Defaults to the tenure."))
            put("card", str(
                "Card name, when the EMI is charged to a credit card rather than " +
                    "debited from a bank account. Leave out for a normal loan."
            ))
            put("account", str("Bank account the EMI is debited from, if not a card EMI."))
            put("due_date", str("The day the EMI comes out, as YYYY-MM-DD."))
            required("name", "emi", "total_months")
        })
        add(tool("update_card", "Change a credit card's limit, balance, bill date or digits.") {
            put("name", str("Current card name. Required."))
            put("new_name", str("New name."))
            put("limit", num("New credit limit."))
            put("balance", num("New outstanding balance."))
            put("min_due", num("New minimum due."))
            put("due_date", str("Bill date as YYYY-MM-DD."))
            put("last_digits", str("Last 3-4 digits, for matching card spends."))
            put("paid", bool("True once the bill has been settled."))
            required("name")
        })
        add(tool("update_loan", "Change a loan's EMI, months left, due date or where it is paid from.") {
            put("name", str("Current loan name. Required."))
            put("emi", num("New monthly EMI."))
            put("remaining_months", int("Months still to pay."))
            put("due_date", str("EMI date as YYYY-MM-DD."))
            put("account", str("Bank account it is debited from."))
            put("card", str("Card it is billed to instead, for a card EMI."))
            required("name")
        })
        add(tool(
            "delete_account",
            "Remove an account. Its transactions move elsewhere. The user confirms."
        ) {
            put("name", str("Account name. Required."))
            required("name")
        })
        add(tool("delete_card", "Remove a credit card. The user confirms it.") {
            put("name", str("Card name. Required."))
            required("name")
        })
        add(tool("delete_loan", "Remove a loan. The user confirms it.") {
            put("name", str("Loan name. Required."))
            required("name")
        })
        add(tool("set_budget_rollover", "Carry each budget's leftover into the next month.") {
            put("on", bool("True to carry it over. Required."))
            required("on")
        })
        add(tool(
            "set_payee_category",
            "Remember which category a payee belongs to, so every future payment " +
                "from them is filed there and the unsorted ones already recorded " +
                "are moved. Use when the user says something like 'Eastern Power " +
                "is electricity'."
        ) {
            put("payee", str("The payee's name as it appears on transactions. Required."))
            put("category", str("The category to file it under. Required."))
            required("payee", "category")
        })
        add(tool(
            "set_salary_date",
            "The day a profile is paid. Confirmations reset on it."
        ) {
            put("day", int("Day of the month, 1 to 28. Required."))
            put("profile", str("Whose salary date. Defaults to the current profile."))
            required("day")
        })
        add(tool(
            "set_salary_override",
            "Set a salary override amount and/or reset day for a specific profile and month."
        ) {
            put("profile", str("Profile name. Required."))
            put("year_month", str("Month as YYYY-MM (e.g. '2026-09'). Required."))
            put("amount", num("Override salary amount. Omit or set negative to clear override."))
            put("reset_day", int("Override salary reset day (1-28)."))
            required("profile", "year_month")
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
     * The instructions, then the figures.
     *
     * Order matters for cost: OpenAI discounts a repeated prompt prefix, but
     * only where it is identical byte for byte. Everything fixed is therefore
     * first, and the profile, the date and the snapshot — which change every
     * message — go last, so the long unchanging part stays cacheable instead of
     * being invalidated by the first balance that moves.
     */
    fun systemPrompt(profile: String, side: String, today: String, snapshot: String): String =
        STATIC_PROMPT + """

        NOW
        You are talking to $profile. The screen shows the $side side. Today is $today.

        CURRENT DATA — accurate as of this moment. Answer from it directly when it
        holds what was asked; calling a tool for something already here only makes
        the reply slower. Never state a figure that is neither here nor returned by
        a tool.
        $snapshot
        """.trimIndent()

    private val STATIC_PROMPT = """
        You are the AI assistant inside FinTrack, a household finance app for two people.
        You can read and modify financial data accurately through your tools.

        CRITICAL RULES FOR INSERT, UPDATE, AND DELETE:
        1. FUTURE EXPENSES, GOALS & EXPECTED INFLOWS vs COMPLETED TRANSACTIONS:
           - A planned expense, goal, upcoming purchase, or EXPECTED MONEY FROM OTHERS (e.g. "i should be getting 25k from ajay", "ajay owes me 25k", "expecting 50000 on 10 Oct", "i need to buy spects 2500 on 15th sep") is ALWAYS added to Set Aside (`add_commitment` with `kind = "income"` for incoming receivables, or `kind = "expense"` for goals, with `note` and `due_date` if mentioned).
           - NEVER call `add_transaction` for future dates, upcoming purchases, plans, or expected future money. `add_transaction` is STRICTLY for money that was ALREADY spent, received, or transferred in the past or on today's date.
        2. CLEAR INFORMATION ON WHERE ACTIONS ARE TAKEN:
           - Whenever you insert, update, or delete anything, clearly tell the user EXACTLY WHERE it was added, updated, or deleted (e.g. "Added to Set Aside (Vinay): Expected from Ajay ₹25,000", "Added to Set Aside: Spectacles ₹2,500 due 15 Sep 2026", "Recorded in Transactions under HDFC: Received ₹25,000 from Ajay", "Recorded in Transactions under ICICI: Spent ₹250 on Food", etc.).
           - Explicitly state the destination section (Set Aside, Recurring, Transactions, Accounts, Credit Cards, Loans, Budgets), the profile (Vinay / Joint), the date, and the amount.
        3. ASKING BEFORE ACTION WHEN AMBIGUOUS:
           - If a user's instruction is ambiguous — for example, if it is unclear whether they already received/paid the money or are expecting/planning to receive/pay it later, or if crucial details are missing — ask the user for clarification before executing a modifying tool.
        4. DELETIONS ALWAYS REQUIRE USER CONFIRMATION:
           - When deleting a transaction, commitment, account, card, or loan, the tool registers a proposal on screen. Always clearly tell the user what is being removed and inform them that a confirmation dialog has appeared on screen for them to tap "Delete".

        APP CONCEPTS & SECTIONS:
        - Set Aside (`add_commitment` with `due_date` or `every_months > 1`): One-time future goals, expected future receivables from others, or periodic large bills to save up for (e.g. Spectacles on 15 Sep, Expected from Ajay, Insurance in Nov).
        - Recurring (`add_commitment` with `every_months = 1` and no future due date): Fixed monthly bills paid every single month (e.g. Rent, Wi-Fi, Maid).
        - Transactions (`add_transaction`): Money that has already moved in/out of an account on or before today. Affects live account balances.
        - Profiles & Scope: Personal is the user's own (default); Joint is shared. Only mark something joint when the user explicitly says "joint", "shared", "household", or "both of us".
        - Payee vs Category: Payee is the merchant/person (e.g. "Eastern Power"); Category is the classification (e.g. "Utilities"). When something is Uncategorised, suggest a fitting category and use set_payee_category.

        CHOOSING TOOLS:
        - Totals, trends, averages, comparisons: summarise_spending. Never manually add up rows.
        - Upcoming bills and due dates: due_soon.
        - Individual transaction lookup: list_transactions. Users may refer to items by date and serial number (e.g. "yesterday 1", "today first 3").

        Keep replies concise, clear, and direct. Always format amounts in rupees (e.g. ₹2,500).
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
