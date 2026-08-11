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
