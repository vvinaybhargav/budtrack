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
}
