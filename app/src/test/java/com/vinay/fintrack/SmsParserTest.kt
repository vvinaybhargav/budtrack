package com.vinay.fintrack

import com.vinay.fintrack.data.looksLikeBankMessage
import com.vinay.fintrack.data.looksLikeBankSender
import com.vinay.fintrack.data.parseBankSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun testKotakReceivedMessageExactMatch() {
        val message = "Received Rs.1.00 in your Kotak Bank AC 2502 from VADLAMANI VINAY BHAR on 12-09-26.UPI Ref:769725550537"
        val sender = "JX-KOTAKB-S"

        assertTrue(looksLikeBankSender(sender))
        assertTrue(looksLikeBankMessage(message))

        val parsed = parseBankSms(message, sender)
        assertNotNull("Expected parsed transaction to not be null", parsed)
        assertEquals(1.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isCredit)
        assertEquals("2502", parsed.accountTail)
        assertEquals("769725550537", parsed.ref)
        assertEquals("VADLAMANI VINAY BHAR", parsed.party)
        assertEquals("2026-09-12", parsed.date)
        assertTrue(parsed.isUsable)
    }

    @Test
    fun testKotakSentMessage() {
        val message = "Sent Rs.500.00 from Kotak Bank AC 2502 to SWIGGY on 12-09-26.UPI Ref:769725550538"
        val parsed = parseBankSms(message, "JX-KOTAKB-S")
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("2502", parsed.accountTail)
        assertEquals("SWIGGY", parsed.party)
    }

    @Test
    fun testHdfcDebitMessage() {
        val message = "Rs.250.00 debited from HDFC Bank A/C **1234 on 10-09-26 to ZOMATO UPI Ref 123456789012"
        val parsed = parseBankSms(message, "AD-HDFCBK")
        assertNotNull(parsed)
        assertEquals(250.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("1234", parsed.accountTail)
        assertEquals("123456789012", parsed.ref)
    }

    @Test
    fun testIciciDebitMessage() {
        val message = "Acct XX391 debited for Rs 914.00 on 12-Aug-26; Eastern Power D credited."
        val parsed = parseBankSms(message, "VM-ICICIB")
        assertNotNull(parsed)
        assertEquals(914.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("391", parsed.accountTail)
        assertEquals("Eastern Power D", parsed.party)
    }

    @Test
    fun testCardSpendWithBalance() {
        val message = "Spent Rs 1,499.00 on Card ending 4321 at FLIPKART on 09-08-26. Avl Lmt Rs 50,000"
        val parsed = parseBankSms(message, "AXISBK")
        assertNotNull(parsed)
        assertEquals(1499.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("4321", parsed.accountTail)
        assertEquals("FLIPKART", parsed.party)
    }

    @Test
    fun testOtpIgnored() {
        val message = "Your OTP for Kotak Bank net banking is 482910 for Rs.1000. Do not share with anyone."
        val parsed = parseBankSms(message, "JX-KOTAKB")
        assertEquals(null, parsed)
        assertFalse(looksLikeBankMessage(message))
    }

    @Test
    fun testUtrExtraction() {
        val message = "Sent Rs.1.00 from Kotak Bank AC 2502 to Ramesh on 12-09-26. UTR: 769725550537"
        val parsed = parseBankSms(message, "JX-KOTAKB-S")
        assertNotNull(parsed)
        assertEquals("769725550537", parsed!!.ref)
        assertEquals(1.0, parsed.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("2502", parsed.accountTail)
        assertEquals("Ramesh", parsed.party)
    }

    @Test
    fun testUpiSlashFormat() {
        val message = "Rs 100.00 debited from A/C XX1234 on 12-09-26. Info: UPI/DR/425678901234/Merchant"
        val parsed = parseBankSms(message, "AD-HDFCBK")
        assertNotNull(parsed)
        assertEquals("425678901234", parsed!!.ref)
        assertEquals(100.0, parsed.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("1234", parsed.accountTail)
    }

    @Test
    fun testConsecutiveSameAmountTransactionsWithDifferentRef() {
        val t1 = parseBankSms("Sent Rs.1.00 from Kotak Bank AC 2502 to Ramesh on 12-09-26. UTR: 769725550537")
        val t2 = parseBankSms("Sent Rs.1.00 from Kotak Bank AC 2502 to Ramesh on 12-09-26. UTR: 769725550538")
        assertNotNull(t1)
        assertNotNull(t2)
        // Ensure 2nd transaction has a distinct dedupe key and won't be dropped as duplicate
        assertTrue("Consecutive transactions with different UTR must have different dedupe keys", t1!!.dedupeKey != t2!!.dedupeKey)
    }

    @Test
    fun testConsecutiveTransactionsWithoutRefDifferentTimestamps() {
        val now = 1726146000000L
        val later = now + 45_000L // 45 seconds later
        val t1 = parseBankSms("Paid ₹10.00 to Chai Wala", "Google Pay", now)
        val t2 = parseBankSms("Paid ₹10.00 to Chai Wala", "Google Pay", later)
        assertNotNull(t1)
        assertNotNull(t2)
        assertTrue("Consecutive separate transactions done moments apart must have different dedupe keys", t1!!.dedupeKey != t2!!.dedupeKey)
    }

    @Test
    fun testImmediateSameNotificationHasSameDedupeKey() {
        val now = 1726146000000L
        val t1 = parseBankSms("Paid ₹10.00 to Chai Wala", "Google Pay", now)
        val t2 = parseBankSms("Paid ₹10.00 to Chai Wala", "Google Pay", now + 2_000L) // 2 seconds later (duplicate notification)
        assertNotNull(t1)
        assertNotNull(t2)
        assertEquals("Immediate notification duplicate must share dedupe key", t1!!.dedupeKey, t2!!.dedupeKey)
    }

    @Test
    fun testPhoneRecipientParty() {
        val message = "Sent Rs. 500 to +919876543210 on 12-09-26"
        val parsed = parseBankSms(message, "AD-HDFCBK")
        assertNotNull(parsed)
        assertEquals("+919876543210", parsed!!.party)
    }

    @Test
    fun testPushNotificationWithoutRefOrPartyIsUsable() {
        val message = "Paid ₹50.00 successfully"
        val parsed = parseBankSms(message, "Google Pay")
        assertNotNull(parsed)
        assertEquals(50.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isUsable)
    }

    @Test
    fun test120RupeesNotificationFormat() {
        val message = "Paid 120 rupees to Chai Point"
        assertTrue(looksLikeBankMessage(message))
        val parsed = parseBankSms(message, "Google Pay")
        assertNotNull("Expected 120 rupees transaction to be parsed", parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("Chai Point", parsed.party)
    }

    @Test
    fun testSbiUpiDebitedByFormat() {
        val message = "Dear SBI UPI User, A/c ..1234 debited by 120.00 on 12Sep26 transfer to Ramesh Refno 425678901234"
        assertTrue(looksLikeBankMessage(message))
        val parsed = parseBankSms(message, "SBIUPI")
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("1234", parsed.accountTail)
        assertEquals("425678901234", parsed.ref)
    }

    @Test
    fun test120RsSuffixFormat() {
        val message = "Sent 120 Rs to Ramesh on 12-09-26"
        assertTrue(looksLikeBankMessage(message))
        val parsed = parseBankSms(message, "PhonePe")
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("Ramesh", parsed.party)
    }

    @Test
    fun test120SlashFormat() {
        val message = "120/- debited from A/C XX2502 at Grocery"
        assertTrue(looksLikeBankMessage(message))
        val parsed = parseBankSms(message, "JX-KOTAKB-S")
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertEquals("2502", parsed.accountTail)
    }

    @Test
    fun testDrAbbreviationDebit() {
        val message = "A/c XX1234 Dr. 120.00 on 12-09-26 to Merchant"
        assertTrue(looksLikeBankMessage(message))
        val parsed = parseBankSms(message, "VM-ICICIB")
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
    }

    @Test
    fun test40RupeesFormat() {
        val message = "Paid ₹40 to Ramesh"
        assertTrue(looksLikeBankMessage(message))
        val parsed = parseBankSms(message, "Google Pay")
        assertNotNull(parsed)
        assertEquals(40.0, parsed!!.amount, 0.001)
        assertFalse(parsed.isCredit)
        assertEquals("Ramesh", parsed.party)
    }
}
