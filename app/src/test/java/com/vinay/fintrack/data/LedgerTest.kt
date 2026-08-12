package com.vinay.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide what a number says. Every one of these covers a bug
 * that reached a real build: money vanishing from a balance, a payment filed
 * to the wrong side, a budget bar that ignored half of what was spent.
 */
class LedgerTest {

    private val joint = Account("a-joint", "ICICI Joint", "Joint", "Joint", 100_000.0, "1111")
    private val mine = Account("a-me", "SBI", "Me · personal", "Me", 50_000.0, "2222")
    private val hers = Account("a-wife", "HDFC", "Wife · personal", "Wife", 30_000.0, "3333")
    private val accounts = listOf(joint, mine, hers)

    private val myCard = Card("c-me", "Regalia", "Me", 300_000.0, 0.0, 0.0, "18 Sep", false, "4444")
    private val cards = listOf(myCard)

    private fun txn(
        id: String = "t1",
        kind: String = "EXPENSE",
        amount: Double = 100.0,
        from: String = "",
        to: String = "",
        card: String = "",
        category: String = "Groceries",
        source: String = "",
        period: String = "2026-08"
    ) = Txn(
        id = id, date = "$period-09", kind = kind, amount = amount, category = category,
        fromAccountId = from, toAccountId = to, cardId = card, period = period, source = source
    )

    // ── balances ───────────────────────────────────────────────────────

    @Test
    fun `an expense leaves the account it came from`() {
        val b = Ledger.balances(accounts, listOf(txn(amount = 1_000.0, from = mine.id)))
        assertEquals(49_000.0, b[mine.id]!!, 0.001)
        assertEquals(100_000.0, b[joint.id]!!, 0.001)
    }

    @Test
    fun `a transfer moves between two accounts and creates no money`() {
        val b = Ledger.balances(
            accounts,
            listOf(txn(kind = "TRANSFER", amount = 5_000.0, from = joint.id, to = mine.id))
        )
        assertEquals(95_000.0, b[joint.id]!!, 0.001)
        assertEquals(55_000.0, b[mine.id]!!, 0.001)
        assertEquals(180_000.0, b.values.sum(), 0.001)
    }

    /** A card spend is owed on the card; no bank balance moves until the bill. */
    @Test
    fun `a card spend touches no account`() {
        val b = Ledger.balances(accounts, listOf(txn(amount = 2_450.0, card = myCard.id)))
        assertEquals(100_000.0, b[joint.id]!!, 0.001)
        assertEquals(50_000.0, b[mine.id]!!, 0.001)
    }

    /** Deleting an account must not make the money it held reappear elsewhere. */
    @Test
    fun `a transaction on an unknown account changes nothing`() {
        val b = Ledger.balances(accounts, listOf(txn(amount = 999.0, from = "a-deleted")))
        assertEquals(180_000.0, b.values.sum(), 0.001)
    }

    // ── which side it belongs to ───────────────────────────────────────

    @Test
    fun `a transaction takes its side from its account`() {
        assertEquals("Me", Ledger.personOf(txn(from = mine.id), accounts, cards))
        assertEquals("Joint", Ledger.personOf(txn(from = joint.id), accounts, cards))
        assertEquals("Wife", Ledger.personOf(txn(from = hers.id), accounts, cards))
    }

    @Test
    fun `income takes the side of the account it landed in`() {
        assertEquals("Me", Ledger.personOf(txn(kind = "INCOME", to = mine.id), accounts, cards))
    }

    @Test
    fun `a card spend takes the card owner`() {
        assertEquals("Me", Ledger.personOf(txn(card = myCard.id), accounts, cards))
    }

    @Test
    fun `mine shows under personal and not under joint`() {
        assertTrue(Ledger.inBucket("Me", "Me", personalView = true))
        assertFalse(Ledger.inBucket("Me", "Me", personalView = false))
    }

    @Test
    fun `shared shows under joint and not under personal`() {
        assertTrue(Ledger.inBucket("Joint", "Me", personalView = false))
        assertFalse(Ledger.inBucket("Joint", "Me", personalView = true))
    }

    /** Her personal spending is hers; it appears on neither of my tabs. */
    @Test
    fun `the other profile's own spending shows on neither side`() {
        assertFalse(Ledger.inBucket("Wife", "Me", personalView = true))
        assertFalse(Ledger.inBucket("Wife", "Me", personalView = false))
    }

    /** Orphans were invisible in both tabs, which read as a lost payment. */
    @Test
    fun `an orphan stays reachable under joint`() {
        assertTrue(Ledger.inBucket("", "Me", personalView = false))
    }

    // ── budgets ────────────────────────────────────────────────────────

    private val myIds = setOf(mine.id, joint.id)
    private val myCardIds = setOf(myCard.id)

    @Test
    fun `spending is summed per category for the month`() {
        val spend = Ledger.spendByCategory(
            listOf(
                txn(id = "1", amount = 400.0, from = mine.id, category = "Groceries"),
                txn(id = "2", amount = 600.0, from = joint.id, category = "Groceries"),
                txn(id = "3", amount = 900.0, from = mine.id, category = "Eating Out")
            ),
            "2026-08", myIds, myCardIds
        )
        assertEquals(1_000.0, spend["Groceries"]!!, 0.001)
        assertEquals(900.0, spend["Eating Out"]!!, 0.001)
    }

    /** Setting money aside for car insurance is not spending on car insurance. */
    @Test
    fun `transfers are not spending`() {
        val spend = Ledger.spendByCategory(
            listOf(txn(kind = "TRANSFER", amount = 3_333.0, from = joint.id, category = "Car Insurance")),
            "2026-08", myIds, myCardIds
        )
        assertEquals(null, spend["Car Insurance"])
    }

    /** Card spends have no account, and were silently missing from budgets. */
    @Test
    fun `card spends count towards the budget`() {
        val spend = Ledger.spendByCategory(
            listOf(txn(amount = 2_450.0, card = myCard.id, category = "Eating Out")),
            "2026-08", myIds, myCardIds
        )
        assertEquals(2_450.0, spend["Eating Out"]!!, 0.001)
    }

    /** Otherwise the purchase and settling the bill are both charged. */
    @Test
    fun `paying the card bill is not itself spending`() {
        val spend = Ledger.spendByCategory(
            listOf(
                txn(id = "1", amount = 2_450.0, card = myCard.id, category = "Eating Out"),
                txn(
                    id = "2", amount = 2_450.0, from = mine.id, card = myCard.id,
                    category = "Credit Card", source = Ledger.CARD_PAYMENT
                )
            ),
            "2026-08", myIds, myCardIds
        )
        assertEquals(2_450.0, spend["Eating Out"]!!, 0.001)
        assertEquals(null, spend["Credit Card"])
    }

    @Test
    fun `another month does not count`() {
        val spend = Ledger.spendByCategory(
            listOf(txn(amount = 500.0, from = mine.id, period = "2026-07")),
            "2026-08", myIds, myCardIds
        )
        assertEquals(null, spend["Groceries"])
    }

    @Test
    fun `the other profile's spending is not in my budget`() {
        val spend = Ledger.spendByCategory(
            listOf(txn(amount = 500.0, from = hers.id)),
            "2026-08", myIds, myCardIds
        )
        assertEquals(null, spend["Groceries"])
    }

    // ── set-aside arithmetic ───────────────────────────────────────────

    @Test
    fun `a monthly commitment is its own amount`() {
        assertEquals(4_500.0, Ledger.monthlyShare(4_500.0, 1), 0.001)
    }

    @Test
    fun `an annual bill divides by twelve`() {
        assertEquals(1_000.0, Ledger.monthlyShare(12_000.0, 12), 0.001)
    }

    /** ₹40,000 a year is ₹3,333.33 a month, and twelve of those must be
     *  ₹40,000 again — not ₹39,999.96 with the remainder quietly dropped. */
    @Test
    fun `the shares add back up to the whole`() {
        val total = (0 until 12).sumOf { Ledger.monthlyShare(40_000.0, 12, it) }
        assertEquals(40_000.0, total, 0.001)
    }

    @Test
    fun `a quarterly bill divides by three and still adds up`() {
        val total = (0 until 3).sumOf { Ledger.monthlyShare(1_000.0, 3, it) }
        assertEquals(1_000.0, total, 0.001)
    }

    // ── matching a bank alert to a confirmation ────────────────────────

    @Test
    fun `the same payment confirmed by hand is recognised`() {
        val confirmed = txn(amount = 22_000.0, from = joint.id, category = "EMI")
        assertTrue(Ledger.isSamePayment(confirmed, 22_000.0, "2026-08-11", isCredit = false))
    }

    @Test
    fun `a different amount is a different payment`() {
        val confirmed = txn(amount = 22_000.0, from = joint.id)
        assertFalse(Ledger.isSamePayment(confirmed, 15_300.0, "2026-08-09", isCredit = false))
    }

    @Test
    fun `a payment weeks away is a different payment`() {
        val confirmed = txn(amount = 22_000.0, from = joint.id)
        assertFalse(Ledger.isSamePayment(confirmed, 22_000.0, "2026-08-25", isCredit = false))
    }

    /** An already-imported one carries a reference, and must not absorb another. */
    @Test
    fun `an imported transaction is not matched again`() {
        val imported = txn(amount = 22_000.0, from = joint.id, source = "sms")
            .copy(ref = "423456789012")
        assertFalse(Ledger.isSamePayment(imported, 22_000.0, "2026-08-09", isCredit = false))
    }

    @Test
    fun `a credit does not absorb a debit`() {
        val confirmed = txn(kind = "EXPENSE", amount = 500.0, from = mine.id)
        assertFalse(Ledger.isSamePayment(confirmed, 500.0, "2026-08-09", isCredit = true))
    }
}

/** Timestamps: recorded automatically, and used for ordering. */
class TxnTimeTest {

    private fun at(millis: Long, date: String = "2026-08-11") =
        Txn(id = "t", date = date, kind = "EXPENSE", amount = 1.0, at = millis)

    @Test
    fun `a stamped transaction sorts by its own time`() {
        val morning = at(1_754_900_000_000)
        val evening = at(1_754_940_000_000)
        assertTrue(evening.sortKey > morning.sortKey)
    }

    /** Records written before the field existed must still order sensibly. */
    @Test
    fun `an untimed transaction falls back to its date`() {
        val older = Txn(id = "a", date = "2026-08-01", kind = "EXPENSE", amount = 1.0)
        val newer = Txn(id = "b", date = "2026-08-11", kind = "EXPENSE", amount = 1.0)
        assertTrue(newer.sortKey > older.sortKey)
        assertTrue(older.sortKey > 0L)
    }

    @Test
    fun `the time is shown only when one was recorded`() {
        assertTrue(at(1_754_940_000_000).whenText.contains(","))
        assertFalse(Txn(id = "a", date = "2026-08-11", kind = "EXPENSE", amount = 1.0).whenText.contains(","))
    }
}

/**
 * The month's figures come from what was recorded, not from what was planned.
 * They used to be summed from entries, so deleting every transaction left them
 * unchanged — which reads as the app being broken.
 */
class MonthTotalsTest {

    private val mine = Account("a-me", "SBI", "Me", "Me", 0.0)
    private val ids = setOf(mine.id)
    private val cardIds = setOf("c1")
    private val invest = listOf("LIC", "PPF")

    private fun t(
        kind: String, amount: Double, from: String = "", to: String = "",
        card: String = "", category: String = "Groceries", source: String = "",
        entry: String = ""
    ) = Txn(
        id = "t${amount.toInt()}$kind$category", date = "2026-08-09", kind = kind,
        amount = amount, category = category, fromAccountId = from, toAccountId = to,
        cardId = card, entryId = entry, period = "2026-08", source = source
    )

    @Test
    fun `nothing recorded means nothing to show`() {
        val m = Ledger.monthTotals(emptyList(), "2026-08", ids, cardIds, invest)
        assertEquals(0.0, m.income, 0.001)
        assertEquals(0.0, m.spent, 0.001)
        assertEquals(0.0, m.saved, 0.001)
        assertEquals(0.0, m.invested, 0.001)
    }

    @Test
    fun `income and spending are counted separately`() {
        val m = Ledger.monthTotals(
            listOf(
                t("INCOME", 120_000.0, to = mine.id, category = "Salary"),
                t("EXPENSE", 2_000.0, from = mine.id)
            ),
            "2026-08", ids, cardIds, invest
        )
        assertEquals(120_000.0, m.income, 0.001)
        assertEquals(2_000.0, m.spent, 0.001)
    }

    /** A set-aside you confirmed is money kept, not spent. */
    @Test
    fun `a confirmed set-aside is put by rather than spent`() {
        val m = Ledger.monthTotals(
            listOf(t("TRANSFER", 3_333.0, from = mine.id, category = "Car Insurance", entry = "e1")),
            "2026-08", ids, cardIds, invest
        )
        assertEquals(3_333.0, m.saved, 0.001)
        assertEquals(0.0, m.spent, 0.001)
    }

    /**
     * The two halves of a bank-to-bank move. Neither is spending nor income,
     * and neither is a set-aside: nothing was put by until it is confirmed
     * against one.
     */
    @Test
    fun `moving money between your own banks counts as nothing`() {
        val m = Ledger.monthTotals(
            listOf(
                t("TRANSFER", 20_000.0, from = mine.id, category = "Sent"),
                t("TRANSFER", 20_000.0, to = mine.id, category = "Arrived")
            ),
            "2026-08", ids, cardIds, invest
        )
        assertEquals(0.0, m.spent, 0.001)
        assertEquals(0.0, m.income, 0.001)
        assertEquals(0.0, m.saved, 0.001)
        assertEquals(0.0, m.invested, 0.001)
    }

    @Test
    fun `a transfer to an investment category counts as invested`() {
        val m = Ledger.monthTotals(
            listOf(t("TRANSFER", 5_000.0, from = mine.id, category = "PPF", entry = "e2")),
            "2026-08", ids, cardIds, invest
        )
        assertEquals(5_000.0, m.invested, 0.001)
        assertEquals(0.0, m.saved, 0.001)
    }

    /**
     * The bill leaves a bank account and settles a card at once, so it carries
     * both. Testing the two separately let it through on the account side and
     * charged the same rupees to the month twice.
     */
    @Test
    fun `settling a card is not spending, even from a tracked account`() {
        val m = Ledger.monthTotals(
            listOf(
                t("EXPENSE", 2_450.0, card = "c1", category = "Eating Out"),
                t("EXPENSE", 2_450.0, from = mine.id, card = "c1",
                    category = "Credit Card", source = Ledger.CARD_PAYMENT)
            ),
            "2026-08", ids, cardIds, invest
        )
        assertEquals(2_450.0, m.spent, 0.001)
    }

    @Test
    fun `last month is not this month`() {
        val old = t("EXPENSE", 900.0, from = mine.id).copy(period = "2026-07")
        val m = Ledger.monthTotals(listOf(old), "2026-08", ids, cardIds, invest)
        assertEquals(0.0, m.spent, 0.001)
    }
}

/** Transfers concern both ends, categories are never a catch-all, and the
 *  month can start on a day other than the 1st. */
class TransferAndCycleTest {

    private val joint = Account("a-j", "ICICI Joint", "Joint", "Joint", 0.0)
    private val mine = Account("a-m", "SBI", "Me", "Me", 0.0)
    private val hers = Account("a-w", "HDFC", "Wife", "Wife", 0.0)
    private val accounts = listOf(joint, mine, hers)

    private fun transfer(from: String, to: String) = Txn(
        id = "t", date = "2026-08-09", kind = "TRANSFER", amount = 5_000.0,
        fromAccountId = from, toAccountId = to, period = "2026-08"
    )

    @Test
    fun `a transfer to the joint account concerns both sides`() {
        val people = Ledger.personsOf(transfer(mine.id, joint.id), accounts, emptyList())
        assertEquals(setOf("Me", "Joint"), people)
        assertTrue(Ledger.inBucket(people, "Me", personalView = true))
        assertTrue(Ledger.inBucket(people, "Me", personalView = false))
    }

    /** It is on my phone under Personal, and on hers under hers. */
    @Test
    fun `a transfer to the other profile shows for each of them`() {
        val people = Ledger.personsOf(transfer(mine.id, hers.id), accounts, emptyList())
        assertTrue(Ledger.inBucket(people, "Me", personalView = true))
        assertTrue(Ledger.inBucket(people, "Wife", personalView = true))
        assertFalse(Ledger.inBucket(people, "Me", personalView = false))
    }

    @Test
    fun `an ordinary expense still concerns only its own side`() {
        val spend = Txn(
            id = "t", date = "2026-08-09", kind = "EXPENSE", amount = 100.0,
            fromAccountId = mine.id, period = "2026-08"
        )
        assertEquals(setOf("Me"), Ledger.personsOf(spend, accounts, emptyList()))
    }

    // ── cycle ──────────────────────────────────────────────────────────

    @Test
    fun `a first-of-the-month cycle is just the calendar month`() {
        assertEquals("2026-08", Ledger.cycleOf("2026-08-01", 1))
        assertEquals("2026-08", Ledger.cycleOf("2026-08-31", 1))
    }

    /** Paid on the 5th: the 3rd still belongs to the month before. */
    @Test
    fun `before the reset day the cycle is the previous month`() {
        assertEquals("2026-07", Ledger.cycleOf("2026-08-03", 5))
        assertEquals("2026-08", Ledger.cycleOf("2026-08-05", 5))
        assertEquals("2026-08", Ledger.cycleOf("2026-08-28", 5))
    }

    @Test
    fun `january rolls back to december of the year before`() {
        assertEquals("2025-12", Ledger.cycleOf("2026-01-02", 5))
    }

    // ── set-aside progress ─────────────────────────────────────────────

    @Test
    fun `parts put by in the same cycle add up`() {
        val txns = listOf(
            Txn(id = "1", date = "2026-08-05", kind = "TRANSFER", amount = 1_000.0,
                entryId = "e1", period = "2026-08"),
            Txn(id = "2", date = "2026-08-19", kind = "TRANSFER", amount = 2_000.0,
                entryId = "e1", period = "2026-08")
        )
        assertEquals(3_000.0, Ledger.setAsideDone(txns, "e1", "2026-08"), 0.001)
    }

    @Test
    fun `last cycle does not count towards this one`() {
        val txns = listOf(
            Txn(id = "1", date = "2026-07-05", kind = "TRANSFER", amount = 5_000.0,
                entryId = "e1", period = "2026-07")
        )
        assertEquals(0.0, Ledger.setAsideDone(txns, "e1", "2026-08"), 0.001)
    }

    // ── categories ─────────────────────────────────────────────────────

    private val cats = listOf("Groceries", "Eating Out", "EMI")

    @Test
    fun `an existing category is used when it fits`() {
        assertEquals("Eating Out", categoryForParty("SWIGGY", cats))
        assertEquals("Groceries", categoryForParty("Blinkit", cats))
    }

    @Test
    fun `a category named in the payee wins`() {
        assertEquals("EMI", categoryForParty("HDFC EMI Aug", cats))
    }

    /**
     * Neither a catch-all nor a new category named after the shop.
     *
     * It used to become "Vijaya Stores", which put billers in the category list
     * instead of kinds of spending. Left unsorted for one correction to settle.
     */
    @Test
    fun `an unrecognised payee is left unsorted`() {
        val made = categoryForParty("VIJAYA STORES", cats)
        assertEquals(UNCATEGORISED, made)
        assertFalse(made == "Other")
    }

    /** And filing it once settles it. */
    @Test
    fun `filing it once settles that payee`() {
        val learned = mapOf(payeeKey("VIJAYA STORES") to "Groceries")
        assertEquals("Groceries", categoryForParty("vijaya stores", cats + "Groceries", learned))
    }
}

/**
 * Real messages that were read wrongly. The ICICI one below produced a payee
 * of "AD-ICICIT-S" — the bank's shortcode — which then became the category
 * "Ad-icicit-s" and the transaction's name.
 */
class RealMessageTest {

    private val icici =
        "ICICI Bank Acct XX391 debited for Rs 914.00 on 12-Aug-26; Eastern Power D " +
            "credited. UPI:881425363233. Call 18002662 for dispute. SMS BLOCK 391 to 9215676766."

    @Test
    fun `the payee is the name before credited, not the sender`() {
        val p = parseBankSms(icici, "AD-ICICIT-S")!!
        assertEquals("Eastern Power D", p.party)
        assertFalse(p.party.contains("ICICIT"))
    }

    @Test
    fun `it reads as a debit of the stated amount from the stated account`() {
        val p = parseBankSms(icici, "AD-ICICIT-S")!!
        assertEquals(914.0, p.amount, 0.001)
        assertFalse(p.isCredit)
        assertEquals("391", p.accountTail)
        assertEquals("2026-08-12", p.date)
    }

    /** The payee is read from the message, and an electricity biller is
     *  recognised as Utilities without being taught. */
    @Test
    fun `its category comes from the payee, not the shortcode`() {
        val p = parseBankSms(icici, "AD-ICICIT-S")!!
        val category = categoryForParty(p.party, listOf("Groceries", "Utilities"))
        assertEquals("Utilities", category)
    }

    /** Better plainly unsorted than filed under something meaningless. */
    @Test
    fun `a sender id never becomes a category`() {
        assertEquals(UNCATEGORISED, categoryForParty("AD-ICICIT-S", listOf("Groceries")))
        assertEquals(UNCATEGORISED, categoryForParty("VM-HDFCBK", listOf("Groceries")))
        assertEquals(UNCATEGORISED, categoryForParty("", listOf("Groceries")))
    }

    @Test
    fun `a card spend takes the merchant after at`() {
        val p = parseBankSms(
            "Rs.2,450.00 spent on HDFC Bank Card XX4321 at SWIGGY on 09-08-26. Txn 553311224488",
            "AD-HDFCBK"
        )!!
        assertEquals("SWIGGY", p.party)
        assertEquals("Eating Out", categoryForParty(p.party, listOf("Eating Out")))
    }

    @Test
    fun `a UPI payee still wins`() {
        val p = parseBankSms(
            "Rs.450.00 debited from a/c XX1234 on 09-08-26 to VPA swiggy@ybl. UPI Ref 423456789012",
            "AD-HDFCBK"
        )!!
        assertEquals("swiggy@ybl", p.party)
    }
}

/** Recognising the bank by name, so a new account works before its digits are
 *  recorded and messages stop piling onto the shared account. */
class BankNameMatchTest {

    private val iciciPersonal = Account("a1", "ICICI Vinay", "Me", "Me", 0.0)
    private val sbiJoint = Account("a2", "SBI Joint", "Joint", "Joint", 0.0)
    private val accounts = listOf(iciciPersonal, sbiJoint)

    private val iciciMessage =
        "ICICI Bank Acct XX391 debited for Rs 914.00 on 12-Aug-26; Eastern Power D credited."
    private val sbiMessage =
        "Rs.2,000.00 debited from SBI A/c XX8305 on 12-08-26. UPI Ref 998877665544"

    @Test
    fun `each message finds its own bank`() {
        assertEquals(AccountMatch.One("a1"), matchAccountByBank(accounts, iciciMessage))
        assertEquals(AccountMatch.One("a2"), matchAccountByBank(accounts, sbiMessage))
    }

    /** "Savings" and "Joint" name no bank, so they must not match on their own. */
    @Test
    fun `generic words in an account name match nothing`() {
        val vague = listOf(
            Account("a1", "Savings Account", "Me", "Me", 0.0),
            Account("a2", "Joint Account", "Joint", "Joint", 0.0)
        )
        assertEquals(
            AccountMatch.None,
            matchAccountByBank(vague, "Rs.100 debited from your savings account")
        )
    }

    /** Two at the same bank is the pair worth not guessing between. */
    @Test
    fun `two accounts at one bank are ambiguous, not a guess`() {
        val both = listOf(
            Account("a1", "ICICI Vinay", "Me", "Me", 0.0),
            Account("a2", "ICICI Joint", "Joint", "Joint", 0.0)
        )
        assertTrue(matchAccountByBank(both, iciciMessage) is AccountMatch.Ambiguous)
    }

    @Test
    fun `a bank nobody banks with matches nothing`() {
        assertEquals(
            AccountMatch.None,
            matchAccountByBank(accounts, "Rs.500 debited from Kotak A/c XX7777")
        )
    }
}

/**
 * Spreading a bill over the months that are actually left, rather than over its
 * full period. Dividing ₹55,000 by twelve in August leaves you ₹27,500 short on
 * a January due date, which is the whole thing failing quietly.
 */
class DueDateTest {

    private val today = "2026-08-12"
    private val jan = "2027-01-29"

    @Test
    fun `august to january is five instalments, not twelve`() {
        assertEquals(5, Ledger.instalmentsUntil(today, jan))
        assertEquals(11_000.0, Ledger.shareUntilDue(55_000.0, today, jan), 0.001)
    }

    /** Saving in the due month itself would leave the money arriving the same
     *  week the bill does, so the due month is not counted. */
    @Test
    fun `the due month is not one of the instalments`() {
        assertEquals(1, Ledger.instalmentsUntil("2027-01-02", jan))
    }

    /** Never zero, or the share would divide by nothing. */
    @Test
    fun `a date already gone still gives one instalment`() {
        assertEquals(1, Ledger.instalmentsUntil("2027-06-01", jan))
    }

    @Test
    fun `a due date in the past rolls forward a year`() {
        assertEquals("2027-01-29", Ledger.nextDue("2026-01-29", 12, today))
    }

    @Test
    fun `a monthly bill rolls forward to next month`() {
        assertEquals("2026-09-05", Ledger.nextDue("2026-03-05", 1, today))
    }

    /** The 31st has no equivalent in February; it must not overflow into March. */
    @Test
    fun `a month end clamps rather than overflowing`() {
        assertEquals("2026-02-28", Ledger.addMonths("2026-01-31", 1))
        assertEquals("2028-02-29", Ledger.addMonths("2028-01-31", 1))
    }

    @Test
    fun `how long is left reads the way you would say it`() {
        assertEquals("5 months, 17 days", Ledger.untilText(today, jan))
        assertEquals("1 month", Ledger.untilText("2026-08-12", "2026-09-12"))
        assertEquals("3 days", Ledger.untilText("2026-08-12", "2026-08-15"))
    }

    @Test
    fun `days between dates cross months and leap years`() {
        assertEquals(31, Ledger.daysBetween("2026-08-12", "2026-09-12"))
        assertEquals(366, Ledger.daysBetween("2028-01-01", "2029-01-01"))
    }

    /** No due date means the old behaviour: split over the stated period. */
    @Test
    fun `without a due date nothing changes`() {
        assertEquals("", Ledger.nextDue("", 12, today))
        assertEquals(1, Ledger.instalmentsUntil(today, ""))
    }
}

/** An EMI charged to a credit card rather than debited from a bank account. */
class CardEmiTest {

    private val mine = Account("a-me", "SBI", "Me", "Me", 50_000.0)
    private val card = Card("c1", "Regalia", "Me", 300_000.0, 0.0, 0.0, "18 Sep")

    private fun emi(from: String = "", onCard: String = "") = Txn(
        id = "t1", date = "2026-08-09", kind = "EXPENSE", amount = 8_000.0,
        category = "EMI", fromAccountId = from, cardId = onCard,
        loanId = "l1", period = "2026-08"
    )

    /** The instalment is owed on the card; the bank balance moves only when the
     *  card bill is settled. Debiting both would take the money twice. */
    @Test
    fun `a card EMI leaves the bank balance alone`() {
        val b = Ledger.balances(listOf(mine), listOf(emi(onCard = card.id)))
        assertEquals(50_000.0, b[mine.id]!!, 0.001)
    }

    @Test
    fun `a bank EMI does leave the account`() {
        val b = Ledger.balances(listOf(mine), listOf(emi(from = mine.id)))
        assertEquals(42_000.0, b[mine.id]!!, 0.001)
    }

    /** It is still spending — it just reaches the month through the card. */
    @Test
    fun `a card EMI counts as spending for the month`() {
        val m = Ledger.monthTotals(
            listOf(emi(onCard = card.id)),
            "2026-08", setOf(mine.id), setOf(card.id), emptyList()
        )
        assertEquals(8_000.0, m.spent, 0.001)
    }

    @Test
    fun `a card EMI takes the card owner's side`() {
        assertEquals(
            "Me",
            Ledger.personOf(emi(onCard = card.id), listOf(mine), listOf(card))
        )
    }
}

/**
 * Two salaries land on different days, so "is this month's bill paid?" is a
 * different question for each person.
 */
class PerProfileCycleTest {

    /** Paid on the 24th: the 12th of August still belongs to July's pay month,
     *  so a bill confirmed on the 25th of July is not owed again yet. */
    @Test
    fun `a late salary day keeps the earlier pay month`() {
        assertEquals("2026-07", Ledger.cycleOf("2026-08-12", 24))
        assertEquals("2026-08", Ledger.cycleOf("2026-08-24", 24))
        assertEquals("2026-08", Ledger.cycleOf("2026-08-25", 24))
    }

    /** The same day falls in different pay months for two people. */
    @Test
    fun `two salary days put one date in two different months`() {
        assertEquals("2026-08", Ledger.cycleOf("2026-08-20", 5))
        assertEquals("2026-07", Ledger.cycleOf("2026-08-20", 24))
    }

    /** January must roll back to the previous December, not month zero. */
    @Test
    fun `january rolls back into the previous year`() {
        assertEquals("2025-12", Ledger.cycleOf("2026-01-03", 24))
    }

    @Test
    fun `the first of the month means the plain calendar month`() {
        assertEquals("2026-08", Ledger.cycleOf("2026-08-01", 1))
    }
}

/** A returned purchase is money back, not money made. */
class RefundTest {

    private val mine = Account("a-me", "SBI", "Me", "Me", 50_000.0)
    private val ids = setOf(mine.id)

    private fun t(kind: String, amount: Double, category: String = "Shopping") = Txn(
        id = "t$kind$amount", date = "2026-08-09", kind = kind, amount = amount,
        category = category,
        fromAccountId = if (kind == "EXPENSE") mine.id else "",
        toAccountId = if (kind != "EXPENSE") mine.id else "",
        period = "2026-08"
    )

    /** Buy for ₹2,000, return it, and the month spent nothing — rather than
     *  spending ₹2,000 and mysteriously earning ₹2,000. */
    @Test
    fun `a refund cancels the spending instead of adding income`() {
        val m = Ledger.monthTotals(
            listOf(t("EXPENSE", 2_000.0), t("REFUND", 2_000.0)),
            "2026-08", ids, emptySet(), emptyList()
        )
        assertEquals(0.0, m.spent, 0.001)
        assertEquals(0.0, m.income, 0.001)
    }

    /** A salary is still income; only a refund is netted off. */
    @Test
    fun `income is untouched by the refund rule`() {
        val m = Ledger.monthTotals(
            listOf(t("INCOME", 120_000.0, "Salary")),
            "2026-08", ids, emptySet(), emptyList()
        )
        assertEquals(120_000.0, m.income, 0.001)
    }

    @Test
    fun `a refund gives the category budget its money back`() {
        val spend = Ledger.spendByCategory(
            listOf(t("EXPENSE", 3_000.0), t("REFUND", 1_200.0)),
            "2026-08", ids, emptySet()
        )
        assertEquals(1_800.0, spend["Shopping"]!!, 0.001)
    }

    /** It still credits the account it landed in. */
    @Test
    fun `a refund puts the money back in the account`() {
        val b = Ledger.balances(listOf(mine), listOf(t("REFUND", 2_000.0)))
        assertEquals(52_000.0, b[mine.id]!!, 0.001)
    }
}

/** Saving up for a bill, then paying it out of what was saved. */
class SetAsidePotTest {

    private fun put(amount: Double, month: String) = Txn(
        id = "t$month", date = "$month-05", kind = "TRANSFER", amount = amount,
        fromAccountId = "a1", toAccountId = "a2", entryId = "e1", period = month
    )

    @Test
    fun `the pot is everything put by across all months`() {
        val pot = Ledger.setAsidePot(
            listOf(put(11_000.0, "2026-08"), put(11_000.0, "2026-09")),
            "e1"
        )
        assertEquals(22_000.0, pot, 0.001)
    }

    /** Paying the bill is an expense against the same entry, so the pot empties
     *  by the same arithmetic that filled it — no separate bookkeeping. */
    @Test
    fun `paying the bill empties the pot`() {
        val paid = Txn(
            id = "pay", date = "2027-01-29", kind = "EXPENSE", amount = 55_000.0,
            fromAccountId = "a2", entryId = "e1", period = "2027-01"
        )
        val saved = (1..5).map { put(11_000.0, "2026-%02d".format(it + 7)) }
        assertEquals(0.0, Ledger.setAsidePot(saved + paid, "e1"), 0.001)
    }

    /** Another commitment's saving is not this one's pot. */
    @Test
    fun `each set-aside keeps its own pot`() {
        val other = put(5_000.0, "2026-08").copy(id = "x", entryId = "e2")
        assertEquals(11_000.0, Ledger.setAsidePot(listOf(put(11_000.0, "2026-08"), other), "e1"), 0.001)
    }
}

/** Carrying a budget's leftover, and reading it against recent months. */
class BudgetRolloverTest {

    private val mine = Account("a-me", "SBI", "Me", "Me", 0.0)
    private val ids = setOf(mine.id)

    private fun spend(amount: Double, period: String, category: String = "Groceries") = Txn(
        id = "t$period$amount", date = "$period-09", kind = "EXPENSE", amount = amount,
        category = category, fromAccountId = mine.id, period = period
    )

    /** Underspending ₹2,000 buys ₹2,000 of room. */
    @Test
    fun `an underspend adds to this month`() {
        assertEquals(12_000.0, Ledger.allowance(10_000.0, 8_000.0), 0.001)
    }

    /** And overspending costs it — carrying only the good half would make the
     *  number flattering rather than useful. */
    @Test
    fun `an overspend takes from this month`() {
        assertEquals(8_000.0, Ledger.allowance(10_000.0, 12_000.0), 0.001)
    }

    @Test
    fun `spending exactly the budget changes nothing`() {
        assertEquals(10_000.0, Ledger.allowance(10_000.0, 10_000.0), 0.001)
    }

    @Test
    fun `walking back a cycle crosses the year end`() {
        assertEquals("2026-07", Ledger.cycleBefore("2026-08", 1))
        assertEquals("2025-12", Ledger.cycleBefore("2026-01", 1))
        assertEquals("2025-08", Ledger.cycleBefore("2026-08", 12))
    }

    @Test
    fun `the trend reads oldest first and fills gaps with zero`() {
        val trend = Ledger.spendTrend(
            listOf(spend(4_000.0, "2026-05"), spend(6_000.0, "2026-07")),
            "2026-08", "Groceries", ids, emptySet()
        )
        assertEquals(listOf(4_000.0, 0.0, 6_000.0), trend)
    }
}

/** Rounding at the aggregates, so error has nowhere to accumulate. */
class PaiseTest {

    private val mine = Account("a-me", "SBI", "Me", "Me", 0.0)

    /** A tenth of a rupee is not representable, so a hundred of them do not
     *  add to ten without help. */
    @Test
    fun `a hundred ten-paise amounts make exactly ten rupees`() {
        val txns = (1..100).map {
            Txn(id = "t$it", date = "2026-08-09", kind = "INCOME", amount = 0.1,
                toAccountId = mine.id, period = "2026-08")
        }
        assertEquals(10.0, Ledger.balances(listOf(mine), txns)[mine.id]!!, 0.0)
    }

    @Test
    fun `rounding is to the nearest paisa, not down`() {
        assertEquals(0.13, Ledger.paise(0.125), 0.0)
        assertEquals(1.0, Ledger.paise(0.999), 0.0)
        assertEquals(-2.5, Ledger.paise(-2.5), 0.0)
    }

    @Test
    fun `a plain amount is untouched`() {
        assertEquals(55_000.0, Ledger.paise(55_000.0), 0.0)
    }
}

/**
 * Filing a payee. A payee is not a category — "Eastern Power" is who you paid,
 * Utilities is what it was for — and correcting one should settle it for good.
 */
class PayeeCategoryTest {

    private val categories = listOf("Groceries", "Utilities", "Eating Out", "Shopping")

    /**
     * The whole complaint: it used to invent the payee as a category, so the
     * list filled with billers instead of kinds of spending.
     *
     * A payee nothing recognises — Eastern Power is matched as Utilities by the
     * keyword table, which is the right answer for it and no test of this rule.
     */
    @Test
    fun `an unknown payee is left unsorted, not made into a category`() {
        assertEquals(UNCATEGORISED, categoryForParty("Sri Balaji Traders", categories))
    }

    /** Once you have filed it, every later payment follows. */
    @Test
    fun `what you filed it under last time wins`() {
        val learned = mapOf(payeeKey("Eastern Power D") to "Utilities")
        assertEquals("Utilities", categoryForParty("Eastern Power D", categories, learned))
    }

    /** The same biller writes its name differently between messages. */
    @Test
    fun `case and punctuation do not make it a different payee`() {
        assertEquals(payeeKey("EASTERN POWER D."), payeeKey("eastern power d"))
        val learned = mapOf(payeeKey("eastern power d") to "Utilities")
        assertEquals("Utilities", categoryForParty("EASTERN POWER D.", categories, learned))
    }

    /** A category since deleted must not come back from the learned map. */
    @Test
    fun `a remembered category that no longer exists is ignored`() {
        val learned = mapOf(payeeKey("Sri Balaji Traders") to "Old Category")
        assertEquals(UNCATEGORISED, categoryForParty("Sri Balaji Traders", categories, learned))
    }

    /** Electricity boards are recognised without being taught. */
    @Test
    fun `power companies are utilities out of the box`() {
        assertEquals("Utilities", categoryForParty("TSSPDCL", categories))
        assertEquals("Utilities", categoryForParty("Tata Power", categories))
    }

    @Test
    fun `known payees still map to their category`() {
        assertEquals("Eating Out", categoryForParty("swiggy@ybl", categories))
        assertEquals("Groceries", categoryForParty("BigBasket", categories))
    }

    /** A bank shortcode is nobody, and never a category. */
    @Test
    fun `a sender id is left unsorted`() {
        assertEquals(UNCATEGORISED, categoryForParty("", categories))
    }
}

/**
 * Categories are kinds of spending you could set a budget against — groceries,
 * home bills, petrol. Never the name of a shop.
 */
class StandardCategoryTest {

    /** Every category the payee rules can produce has to be a real one, or the
     *  rules quietly invent categories through the back door. */
    @Test
    fun `every rule lands on a standard category`() {
        val landed = listOf(
            "swiggy" to "Eating Out",
            "bigbasket" to "Groceries",
            "tsspdcl" to "Utilities",
            "indianoil petrol" to "Fuel",
            "uber" to "Travel",
            "flipkart" to "Shopping",
            "apollo pharmacy" to "Health"
        )
        landed.forEach { (payee, expected) ->
            assertEquals(expected, categoryForParty(payee, emptyList()))
            assertTrue("$expected is not a standard category", expected in STANDARD_CATEGORIES)
        }
    }

    @Test
    fun `the seeded list contains every standard category`() {
        STANDARD_CATEGORIES.forEach {
            assertTrue("$it missing from the seed", it in Seed.categoriesMedium)
        }
    }

    /** A shop is not a kind of spending. */
    @Test
    fun `no shop name is a category`() {
        listOf("Sri Balaji Traders", "VIJAYA STORES", "Eastern Power D").forEach {
            assertFalse(it in STANDARD_CATEGORIES)
            assertFalse(it in Seed.categoriesMedium)
        }
    }
}
