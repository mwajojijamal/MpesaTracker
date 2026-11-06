package com.mpesatracker

import com.mpesatracker.data.entities.TransactionType
import com.mpesatracker.sms.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MpesaParserTest {

    @Test
    fun `parse illustrative SEND message from specification`() {
        val sms = "ABC123XYZ Confirmed. Ksh500.00 sent to JOHN DOE on 31/10/2026 at 10:30 AM. Transaction cost Ksh12.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("ABC123XYZ", parsed!!.transactionCode)
        assertEquals(50000L, parsed.amountCents)
        assertEquals(1200L, parsed.chargesCents)
        assertEquals("JOHN DOE", parsed.recipient)
        assertEquals(TransactionType.SEND, parsed.transactionType)
    }

    @Test
    fun `parse SEND message with phone number and 2-digit year`() {
        val sms = "SLK45ABC12 Confirmed. Ksh1,500.00 sent to JANE WANJIKU 0712345678 on 31/10/26 at 11:15 AM. New M-PESA balance is Ksh2,450.00. Transaction cost, Ksh23.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC12", parsed!!.transactionCode)
        assertEquals(150000L, parsed.amountCents)
        assertEquals(2300L, parsed.chargesCents)
        assertEquals("JANE WANJIKU", parsed.recipient)
        assertEquals(TransactionType.SEND, parsed.transactionType)
    }

    @Test
    fun `parse BUY_GOODS till payment message`() {
        val sms = "SLK45ABC14 Confirmed. Ksh1,250.00 paid to NAIVAS SUPERMARKET. on 31/10/26 at 1:20 PM. New M-PESA balance is Ksh1,050.00. Transaction cost, Ksh0.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC14", parsed!!.transactionCode)
        assertEquals(125000L, parsed.amountCents)
        assertEquals(0L, parsed.chargesCents)
        assertEquals("NAIVAS SUPERMARKET", parsed.recipient)
        assertEquals(TransactionType.BUY_GOODS, parsed.transactionType)
    }

    @Test
    fun `parse PAYBILL payment with account reference`() {
        val sms = "SLK45ABC15 Confirmed. Ksh2,500.00 sent to KPLC PREPAID for account 14253647 on 31/10/26 at 2:05 PM. New M-PESA balance is Ksh850.00. Transaction cost, Ksh23.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC15", parsed!!.transactionCode)
        assertEquals(250000L, parsed.amountCents)
        assertEquals(2300L, parsed.chargesCents)
        assertEquals("KPLC PREPAID", parsed.recipient)
        assertEquals("14253647", parsed.accountReference)
        assertEquals(TransactionType.PAYBILL, parsed.transactionType)
    }

    @Test
    fun `parse RECEIVED money message`() {
        val sms = "SLK45ABC16 Confirmed. You have received Ksh3,000.00 from PETER KAMAU 0722000111 on 31/10/26 at 3:40 PM. New M-PESA balance is Ksh3,850.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC16", parsed!!.transactionCode)
        assertEquals(300000L, parsed.amountCents)
        assertEquals(0L, parsed.chargesCents)
        assertEquals("PETER KAMAU", parsed.sender)
        assertEquals(TransactionType.RECEIVED, parsed.transactionType)
    }

    @Test
    fun `parse WITHDRAWAL from agent message`() {
        val sms = "SLK45ABC17 Confirmed. on 31/10/26 at 4:10 PM Withdraw Ksh1,000.00 from 123456 - SIMBA ENTERPRISES AGENT New M-PESA balance is Ksh2,821.00. Transaction cost, Ksh29.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC17", parsed!!.transactionCode)
        assertEquals(100000L, parsed.amountCents)
        assertEquals(2900L, parsed.chargesCents)
        assertEquals("123456 - SIMBA ENTERPRISES AGENT", parsed.recipient)
        assertEquals(TransactionType.WITHDRAWAL, parsed.transactionType)
    }

    @Test
    fun `parse AIRTIME purchase message`() {
        val sms = "SLK45ABC18 Confirmed. You bought Ksh100.00 of airtime on 31/10/26 at 5:00 PM. New M-PESA balance is Ksh2,721.00. Transaction cost, Ksh0.00."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC18", parsed!!.transactionCode)
        assertEquals(10000L, parsed.amountCents)
        assertEquals(TransactionType.AIRTIME, parsed.transactionType)
    }

    @Test
    fun `parse REVERSAL message and extract original transaction code`() {
        val sms = "SLK45ABC20 Confirmed. Reversal of transaction ABC123XYZ has been successfully reversed on 31/10/26 at 6:00 PM and Ksh500.00 is credited to your M-PESA account."
        val parsed = MpesaParser.parse(sms)

        assertNotNull(parsed)
        assertEquals("SLK45ABC20", parsed!!.transactionCode)
        assertEquals("ABC123XYZ", parsed.reversedOriginalCode)
        assertEquals(50000L, parsed.amountCents)
        assertEquals(TransactionType.REVERSAL, parsed.transactionType)
    }

    @Test
    fun `ignore non-transaction promotional SMS`() {
        val promoSms = "Dear Customer, enjoy double data bundles today by dialing *544#."
        assertNull(MpesaParser.parse(promoSms))
    }
}