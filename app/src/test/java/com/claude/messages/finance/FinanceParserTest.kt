package com.claude.messages.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceParserTest {

    private fun parse(body: String): Transaction? =
        FinanceParser.parse(1L, 2L, "BANK", 1_700_000_000_000L, body)

    @Test
    fun `reads an english card purchase with a balance`() {
        val txn = parse(
            "Your account XXXX1234 has been debited AED 250.75 at CARREFOUR MALL " +
                "on 12/05. Avail Bal AED 3,400.20"
        )
        assertNotNull(txn)
        assertEquals(TxnKind.DEBIT, txn!!.kind)
        assertEquals(250.75, txn.amount, 0.001)
        assertEquals("AED", txn.currency)
        assertEquals(3400.20, txn.balance!!, 0.001)
        assertEquals("CARREFOUR MALL", txn.counterparty)
    }

    @Test
    fun `reads an arabic salary deposit`() {
        val txn = parse(
            "تم إيداع راتب بمبلغ 1,250.500 ر.ع في حسابك 1234. الرصيد المتاح 2,000.000 ر.ع"
        )
        assertNotNull(txn)
        assertEquals(TxnKind.SALARY, txn!!.kind)
        assertEquals(1250.5, txn.amount, 0.001)
        assertEquals("OMR", txn.currency)
        assertEquals(2000.0, txn.balance!!, 0.001)
    }

    /** "Salary" in a deduction must not turn the message into income. */
    @Test
    fun `a salary loan instalment is a debit`() {
        val txn = parse("تم خصم 100.000 ر.ع قسط قرض الراتب من حسابك")
        assertEquals(TxnKind.DEBIT, txn!!.kind)
        assertEquals(100.0, txn.amount, 0.001)
    }

    @Test
    fun `an incoming transfer is a credit and names the payer`() {
        val txn = parse("حوالة واردة بمبلغ 500.00 SAR من محمد العامري")
        assertEquals(TxnKind.CREDIT, txn!!.kind)
        assertEquals(500.0, txn.amount, 0.001)
        assertEquals("SAR", txn.currency)
        assertEquals("محمد العامري", txn.counterparty)
    }

    @Test
    fun `the card number is never mistaken for the amount`() {
        val txn = parse("Card ending 4567: purchase of SAR 99.00 at NOON COM")
        assertEquals(99.0, txn!!.amount, 0.001)
        assertEquals("SAR", txn.currency)
    }

    @Test
    fun `masked account digits are skipped`() {
        val txn = parse("A/C ****8899 debited OMR 12.300 POS ")
        assertEquals(12.3, txn!!.amount, 0.001)
    }

    @Test
    fun `arabic-indic digits are read`() {
        val txn = parse("تم خصم ٢٥.٥٠٠ ر.ع من حسابك")
        assertEquals(25.5, txn!!.amount, 0.001)
    }

    @Test
    fun `a verification code is not a transaction`() {
        assertNull(parse("Your verification code is 847211. Do not share it."))
    }

    @Test
    fun `a balance-only message is not a transaction`() {
        assertNull(parse("رصيدك الحالي 1,500.000 ر.ع"))
    }

    @Test
    fun `an ordinary message is not a transaction`() {
        assertNull(parse("وصلت البيت؟"))
        assertNull(parse(""))
    }

    @Test
    fun `income covers salary and credits but not debits`() {
        assertTrue(parse("تم إيداع راتب 100.000 ر.ع")!!.isIncome)
        assertTrue(parse("credited AED 20.00")!!.isIncome)
        assertTrue(!parse("debited AED 20.00")!!.isIncome)
    }

    /** Real-world templates, kept as one test so a format change is obvious. */
    @Test
    fun `reads the templates gulf banks actually send`() {
        val purchase = parse(
            "Purchase of OMR 7.500 with card ****1234 at LULU HYPERMARKET " +
                "on 03/11/2025. Available balance OMR 812.340"
        )!!
        assertEquals(TxnKind.DEBIT, purchase.kind)
        assertEquals(7.5, purchase.amount, 0.001)
        assertEquals("LULU HYPERMARKET", purchase.counterparty)
        assertEquals(812.34, purchase.balance!!, 0.001)

        val arabicPurchase = parse(
            "تم خصم مبلغ 23.750 ر.ع من حسابك ****4521 لدى شل الخوير " +
                "بتاريخ 03/11/2025. الرصيد المتاح 812.340 ر.ع"
        )!!
        assertEquals(TxnKind.DEBIT, arabicPurchase.kind)
        assertEquals(23.75, arabicPurchase.amount, 0.001)
        assertEquals("شل الخوير", arabicPurchase.counterparty)

        val salary = parse("ايداع راتب شهر 11 بمبلغ 1,450.000 ر.ع في حسابك رقم 0123456789")!!
        assertEquals(TxnKind.SALARY, salary.kind)
        assertEquals(1450.0, salary.amount, 0.001)

        // The account number must not be reported as the employer.
        val englishSalary = parse("Salary credited AED 12,500.00 to A/C XX8821. Avail Bal AED 41,230.55")!!
        assertEquals(TxnKind.SALARY, englishSalary.kind)
        assertEquals(12500.0, englishSalary.amount, 0.001)
        assertNull(englishSalary.counterparty)
        assertEquals(41230.55, englishSalary.balance!!, 0.001)

        val atm = parse("ATM withdrawal SAR 500.00 from ACC 1234. Bal SAR 2,100.00")!!
        assertEquals(TxnKind.DEBIT, atm.kind)
        assertEquals(500.0, atm.amount, 0.001)
        assertEquals(2100.0, atm.balance!!, 0.001)
        assertNull(atm.counterparty)

        val subscription = parse("Payment of QAR 349.99 to NETFLIX.COM completed. Balance QAR 1,004.20")!!
        assertEquals("NETFLIX", subscription.counterparty)
        assertEquals(349.99, subscription.amount, 0.001)

        val fee = parse("رسوم خدمة 1.500 ر.ع خصمت من حسابك")!!
        assertEquals(TxnKind.DEBIT, fee.kind)
        assertEquals(1.5, fee.amount, 0.001)
        // "from your account" is not a merchant name.
        assertNull(fee.counterparty)

        assertNull(parse("عميلنا العزيز، تم تجديد بطاقتك. يرجى زيارة الفرع."))
    }

    @Test
    fun `normalization keeps every index in place`() {
        val body = "تم إيداع ٢٥٠ ر.ع"
        assertEquals(body.length, FinanceParser.normalize(body).length)
    }
}
