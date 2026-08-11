package com.vinay.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser turns free text into financial records, so a mistake here writes a
 * wrong number into someone's balances silently. These cover the shapes real
 * Indian bank alerts come in, and — more importantly — the messages that must
 * NOT become transactions.
 */
class SmsParserTest {

    @Test
    fun `reads a UPI debit`() {
        val p = parseBankSms(
            "Rs.450.00 debited from a/c XX1234 on 09-08-26 to VPA swiggy@ybl. UPI Ref 423456789012",
            "AD-HDFCBK"
        )
        assertNotNull(p)
        assertEquals(450.0, p!!.amount, 0.001)
        assertFalse(p.isCredit)
        assertEquals("1234", p.accountTail)
        assertEquals("423456789012", p.ref)
        assertEquals("2026-08-09", p.date)
    }

    @Test
    fun `reads a credit`() {
        val p = parseBankSms(
            "INR 25,000.00 credited to A/c XX9876 on 01-08-26 from ACME PAYROLL. Ref No 998877665544",
            "VM-ICICIB"
        )
        assertNotNull(p)
        assertEquals(25000.0, p!!.amount, 0.001)
        assertTrue(p.isCredit)
        assertEquals("9876", p.accountTail)
    }

    /** The classic silent corruption: taking the balance as the amount. */
    @Test
    fun `takes the transacted amount not the available balance`() {
        val p = parseBankSms(
            "Rs.1,200.00 debited from A/c XX4321 on 05-08-26. Avl Bal Rs.53,120.55. UPI Ref 112233445566",
            "AD-SBIUPI"
        )
        assertNotNull(p)
        assertEquals(1200.0, p!!.amount, 0.001)
    }

    @Test
    fun `handles amount written before the balance without a rupee prefix on one`() {
        val p = parseBankSms(
            "Your A/c XX1111 is debited by Rs 99.50 on 11-08-26. Available balance is Rs 4,000.00. RRN 445566778899",
            "AX-AXISBK"
        )
        assertEquals(99.50, p!!.amount, 0.001)
    }

    @Test
    fun `ignores an OTP`() {
        assertNull(
            parseBankSms(
                "123456 is your OTP to transfer Rs.5000 from A/c XX1234. Do not share.",
                "AD-HDFCBK"
            )
        )
    }

    @Test
    fun `ignores a future or scheduled debit`() {
        assertNull(
            parseBankSms(
                "Your EMI of Rs.22,000 will be debited from A/c XX1234 on 05-09-26.",
                "AD-HDFCBK"
            )
        )
    }

    @Test
    fun `ignores a failed payment`() {
        assertNull(
            parseBankSms(
                "Your payment of Rs.750 to VPA test@ybl has failed. Ref 121212121212",
                "AD-HDFCBK"
            )
        )
    }

    @Test
    fun `ignores marketing that mentions an amount`() {
        assertNull(
            parseBankSms(
                "You are pre-approved for a personal loan of Rs.5,00,000. Apply now!",
                "VM-HDFCBK"
            )
        )
    }

    /** Without a reference or an account, it is not identifiable enough to trust. */
    @Test
    fun `rejects a debit with neither reference nor account`() {
        assertNull(parseBankSms("Rs.500 debited successfully", "AD-HDFCBK"))
    }

    @Test
    fun `named-month dates parse`() {
        val p = parseBankSms(
            "ICICI Bank Acct XX123 debited for Rs 450.00 on 09-Aug-26; SWIGGY credited. UPI:423456789012",
            "VM-ICICIB"
        )
        assertEquals("2026-08-09", p!!.date)
    }

    @Test
    fun `dedupe key falls back to details when there is no reference`() {
        val a = ParsedSms(450.0, false, "Swiggy", "", "1234", "2026-08-09", "x")
        val b = ParsedSms(450.0, false, "Swiggy", "", "1234", "2026-08-09", "y")
        assertEquals(a.dedupeKey, b.dedupeKey)
    }

    @Test
    fun `reference wins as the dedupe key`() {
        val p = ParsedSms(450.0, false, "Swiggy", "42345", "1234", "2026-08-09", "x")
        assertEquals("42345", p.dedupeKey)
    }

    /** The stored value used to be 300 characters of the message, which then
     *  synced to a household document readable by anyone with the project id. */
    @Test
    fun `only the amount is kept, never the message`() {
        val body = "Rs.450.00 debited from a/c XX1234 on 09-08-26 to VPA swiggy@ybl. " +
            "Avl Bal Rs.53,120.55. UPI Ref 423456789012"
        val p = parseBankSms(body, "AD-HDFCBK")!!
        assertEquals("450.00", p.amountText)
        assertFalse(p.amountText.contains("53,120"))
        assertFalse(p.amountText.contains("XX1234"))
    }

    /** The skip log is persisted, and OTP texts are exactly what gets skipped. */
    @Test
    fun `skip reasons never quote the message`() {
        val otp = "123456 is your OTP to transfer Rs.5000 from A/c XX1234. Do not share."
        val reason = skipReason(otp)
        assertFalse(reason.contains("123456"))
        assertFalse(reason.contains("XX1234"))
        assertTrue(reason.contains("otp"))
    }

    @Test
    fun `bank senders are recognised and mobile numbers are not`() {
        assertTrue(looksLikeBankSender("AD-HDFCBK"))
        assertTrue(looksLikeBankSender("VM-ICICIB"))
        assertFalse(looksLikeBankSender("9876543210"))
    }

    @Test
    fun `withinDays bounds the confirm-versus-SMS match`() {
        assertTrue(withinDays("2026-08-09", "2026-08-11", 4))
        assertTrue(withinDays("2026-08-11", "2026-08-09", 4))
        assertFalse(withinDays("2026-08-01", "2026-08-09", 4))
        assertTrue(withinDays("2026-07-31", "2026-08-02", 4))   // across a month end
    }
}

/**
 * Routing a bank alert to the right account by its trailing digits. Personal
 * and joint accounts are the pair that must never be confused: the account
 * decides which bucket a transaction lands in, so a wrong match files someone's
 * private spending as shared.
 */
class AccountTailTest {

    private fun account(id: String, person: String, tail: String) =
        Account(id, "acct-$id", person, person, 0.0, tail)

    private val accounts = listOf(
        account("joint", "Joint", "1234"),
        account("me", "Me", "5678"),
        account("wife", "Wife", "4321")
    )

    @Test
    fun `exact digits match`() {
        assertEquals(
            AccountMatch.One("me"),
            matchAccountByTail(accounts, "5678")
        )
    }

    /** Recording only the last three still has to find a four-digit message. */
    @Test
    fun `three stored digits match a four digit message`() {
        val threeDigit = listOf(account("me", "Me", "678"), account("joint", "Joint", "234"))
        assertEquals(AccountMatch.One("me"), matchAccountByTail(threeDigit, "5678"))
    }

    @Test
    fun `three digits in the message match a four digit account`() {
        assertEquals(AccountMatch.One("me"), matchAccountByTail(accounts, "678"))
    }

    /** Guessing here would file a personal payment as joint. */
    @Test
    fun `shared endings are reported rather than guessed`() {
        val clashing = listOf(account("joint", "Joint", "1123"), account("me", "Me", "2123"))
        val result = matchAccountByTail(clashing, "123")
        assertTrue(result is AccountMatch.Ambiguous)
        assertEquals(2, (result as AccountMatch.Ambiguous).count)
    }

    @Test
    fun `an exact match wins over a longer partial one`() {
        val mixed = listOf(account("me", "Me", "123"), account("joint", "Joint", "4123"))
        assertEquals(AccountMatch.One("me"), matchAccountByTail(mixed, "123"))
    }

    @Test
    fun `no digits recorded means no match`() {
        val blank = listOf(account("me", "Me", ""), account("joint", "Joint", ""))
        assertEquals(AccountMatch.None, matchAccountByTail(blank, "5678"))
        assertEquals(AccountMatch.None, matchAccountByTail(accounts, ""))
    }

    @Test
    fun `unknown digits fall through instead of matching something`() {
        assertEquals(AccountMatch.None, matchAccountByTail(accounts, "9999"))
    }
}

/** A card spend is added to the card, not taken from a bank account. */
class CardTailTest {

    private val cards = listOf(
        Card("cc1", "HDFC Regalia", "Me", 300000.0, 0.0, 0.0, "18 Sep", false, "4321"),
        Card("cc2", "ICICI Amazon", "Wife", 150000.0, 0.0, 0.0, "22 Sep", false, "8765")
    )

    @Test
    fun `a card is found by its last digits`() {
        assertEquals(AccountMatch.One("cc1"), matchCardByTail(cards, "4321"))
        assertEquals(AccountMatch.One("cc2"), matchCardByTail(cards, "765"))
    }

    @Test
    fun `digits belonging to no card fall through to the accounts`() {
        assertEquals(AccountMatch.None, matchCardByTail(cards, "1111"))
    }

    @Test
    fun `a card message parses like any other`() {
        val p = parseBankSms(
            "Rs.2,450.00 spent on HDFC Bank Card XX4321 at SWIGGY on 09-08-26. Txn 553311224488",
            "AD-HDFCBK"
        )
        assertEquals(2450.0, p!!.amount, 0.001)
        assertEquals("4321", p.accountTail)
        assertEquals(AccountMatch.One("cc1"), matchCardByTail(cards, p.accountTail))
    }
}
