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
            "Individual transactions, newest first."
        ) {
            put("month", str("Restrict to a month as yyyy-MM. Omit for all."))
            put("category", str("Restrict to one category."))
            put("search", str("Match payee, note or reference."))
            put("from", str("Earliest date, YYYY-MM-DD."))
            put("to", str("Latest date, YYYY-MM-DD."))
            put("limit", int("How many to return. Default 30."))
        })

        // ── transactions ───────────────────────────────────────────────
        add(tool(
            "add_transaction",
            "Record money that has already moved."
        ) {
            put("amount", num("Rupees. Required."))
            put("category", str("One of the existing categories."))
            put("direction", enum("Which way the money went.", listOf("out", "in")))
            put("account", str("Account name."))
            put("note", str("Payee or description."))
            put("date", str("dd-MM-yyyy. Default today."))
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
            "A monthly cost, or a set-aside paid every few months. A plan, not a " +
                "payment: it is confirmed each month."
        ) {
            put("amount", num(
                "The whole amount charged each time the bill comes, NOT the monthly " +
                    "share. A ₹55,000 yearly premium is 55000. Required."
            ))
            put("category", str("One of the existing categories."))
            put("every_months", int(
                "How many months between one payment and the next, 1 to 12. A yearly " +
                    "premium or annual bill is 12; half-yearly is 6; quarterly is 3. " +
                    "Use 1 only when the full amount really is charged every single " +
                    "month, like rent. If the user names one due date for a large " +
                    "bill, it is not monthly. Required — never guess 1 by default."
            ))
            put("due_date", str(
                "When the bill is actually due, as YYYY-MM-DD, if the user says. " +
                    "The share each month is then worked out from the months left " +
                    "before that date, not from every_months."
            ))
            put("kind", enum("What sort of commitment.", listOf("expense", "savings", "income")))
            put("joint", bool(
                "True ONLY if the user says this is joint, shared, household or " +
                    "both of you. Leave it out otherwise — anything unsaid is the " +
                    "user's own personal side."
            ))
            put("note", str("Description."))
            // Required, because defaulting it to 1 turned a ₹55,000 yearly premium
            // into a ₹55,000 monthly commitment — a twelvefold error that read as
            // a plausible sentence.
            required("amount", "every_months")
        })
        add(tool("edit_commitment", "Change a recurring entry.") {
            put("id", str("Entry id. Required."))
            put("amount", num("New amount."))
            put("category", str("New category."))
            put("every_months", int("New period in months, 1 to 12."))
            put("due_date", str("New due date as YYYY-MM-DD."))
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
        You are the assistant inside FinTrack, a household finance app for two
        people. You can read and change everything in it through your tools.

        How the app is arranged, so your answers match what the user sees:
        - A commitment is a plan that repeats. It only moves money when confirmed.
        - A transaction is money that actually moved. Balances come from these.
        - A set-aside is one large bill you save up for: each month you put by a
          share, and confirming transfers that to savings rather than spending it.
          When it falls due, pay_set_aside pays it out of what was saved.
        - Recurring and set-aside are not the same. Rent is recurring: every_months
          1, and the full amount leaves the account monthly. One large bill with a
          due date — insurance, school fees, road tax — is a set-aside: give the
          whole bill as the amount, and every_months for how often it comes round.
        - With a due date, the monthly share comes from the months left until then,
          not from every_months: ₹55,000 due 29 January, decided in August, is
          ₹11,000 a month over five months, not a twelfth. Say both figures back,
          so a period read wrongly shows up as a number rather than a fluent
          sentence.
        - Personal is the user's own; Joint is shared. Personal is the default —
          only mark something joint when they say it is shared, household, ours or
          both of you. Never infer it from whichever side is on screen.
        - A payee is not a category. "Eastern Power" is a payee whose category is
          Utilities. When something is Uncategorised, suggest a fitting category
          and use set_payee_category, which settles that payee for good.

        Choosing a tool:
        - Totals, trends, averages, comparisons: summarise_spending. It returns
          figures the app worked out. Never add up list_transactions yourself.
        - What is coming up, what is owed this week: due_soon.
        - Individual payments and history: list_transactions.

        Deleting is confirmed by the user, not by you: the tool describes what
        would go and they tap a button. Say what you changed after an edit. Keep
        replies short and plain — this is a phone screen. Amounts in rupees, like
        ₹4,500.
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
