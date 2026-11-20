package com.claude.messages.finance

/**
 * Reads a bank SMS and reports the money movement it describes.
 *
 * Deliberately free of Android types so the whole thing is unit-testable: the
 * rules below are guesses about other people's message formats, and guesses
 * need tests. Nothing here is configured per bank — a message qualifies by
 * carrying an amount next to a currency and a word that says which direction
 * the money went, which is what every bank's template has in common.
 */
object FinanceParser {

    // Order matters: the earliest keyword in the text decides the direction,
    // so "salary loan deducted" reads as a debit rather than as pay arriving.
    private val salaryWords = listOf(
        "salary", "payroll", "wage", "راتب", "رواتب", "الراتب", "مستحقات",
    )
    private val creditWords = listOf(
        "credited", "credit of", "deposit", "deposited", "received", "transfer in",
        "incoming", "refund", "cashback", "ايداع", "إيداع", "اضافة", "إضافة",
        "اضيف", "أضيف", "اضافه", "استلام", "وارد", "واردة", "ارجاع", "إرجاع",
        "استرداد", "دائن",
    )
    private val debitWords = listOf(
        "debited", "debit of", "withdrawn", "withdrawal", "purchase", "paid",
        "payment of", "spent", "pos ", "atm ", "charged", "fee", "transfer out",
        "خصم", "خُصم", "سحب", "شراء", "دفع", "مدفوع", "سداد", "مدين", "تحويل من حسابك",
        "صرف", "عمولة", "رسوم", "فاتورة",
    )
    private val balanceWords = listOf(
        "balance", "avail bal", "avl bal", "bal:", " bal ", "bal ",
        "رصيد", "الرصيد", "رصيدك", "المتاح", "المتوفر",
    )

    /** Words that mean the number after them identifies something, not an amount. */
    private val identifierWords = listOf(
        "account", "acct", "a/c", "card", "ending", "ref", "reference", "no.", "no:",
        "iban", "otp", "code", "حساب", "الحساب", "بطاقة", "البطاقة", "رقم", "مرجع",
        "ايبان", "رمز", "كود", "جوال", "هاتف",
    )

    /** Currency spellings mapped to the code the UI shows. */
    private val currencies = listOf(
        "omr" to "OMR", "ر.ع" to "OMR", "ريال عماني" to "OMR", "ro " to "OMR",
        "sar" to "SAR", "ر.س" to "SAR", "sr " to "SAR", "ريال سعودي" to "SAR",
        "aed" to "AED", "د.ا" to "AED", "درهم" to "AED", "dhs" to "AED",
        "kwd" to "KWD", "د.ك" to "KWD", "bhd" to "BHD", "د.ب" to "BHD",
        "qar" to "QAR", "ر.ق" to "QAR", "jod" to "JOD", "egp" to "EGP",
        "ج.م" to "EGP", "جنيه" to "EGP", "usd" to "USD", "$" to "USD",
        "eur" to "EUR", "gbp" to "GBP", "inr" to "INR", "pkr" to "PKR",
        // Generic words come last: a specific spelling above wins.
        "ريال" to "SAR", "دينار" to "KWD", "دولار" to "USD",
    )

    private val amountPattern =
        Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,3})?|\d+(?:\.\d{1,3})?""")

    private data class Candidate(val value: Double, val start: Int, val end: Int)

    fun parse(
        messageId: Long,
        threadId: Long,
        source: String,
        date: Long,
        body: String,
    ): Transaction? {
        if (body.isBlank()) return null
        val text = normalize(body)

        val kindHit = firstKindHit(text) ?: return null
        val kind = when {
            // A credit that also says "salary" is pay; a debit that does is a
            // deduction taken out of it, so the direction keyword stays.
            kindHit.kind == TxnKind.CREDIT && salaryWords.any { text.contains(it) } ->
                TxnKind.SALARY

            else -> kindHit.kind
        }

        val candidates = amountPattern.findAll(text)
            .map { Candidate(it.value.replace(",", "").toDoubleOrNull() ?: 0.0, it.range.first, it.range.last + 1) }
            .filter { it.value > 0 }
            .filterNot { isIdentifier(text, it) }
            .toList()
        if (candidates.isEmpty()) return null

        // A bank writes the currency next to the money and nowhere else, so when
        // any candidate has one, the ones without are reference numbers.
        val withCurrency = candidates.mapNotNull { c ->
            currencyNear(text, c)?.let { c to it }
        }
        val priced = if (withCurrency.isNotEmpty()) withCurrency else candidates.map { it to "" }

        val balanceEntry = priced.lastOrNull { (c, _) -> isBalance(text, c) }
        val amountEntry = priced.filter { it !== balanceEntry }
            .minByOrNull { (c, _) -> distance(c, kindHit.start, kindHit.end) }
            ?: return null

        return Transaction(
            messageId = messageId,
            threadId = threadId,
            source = source,
            date = date,
            kind = kind,
            amount = amountEntry.first.value,
            currency = amountEntry.second,
            counterparty = counterparty(body),
            balance = balanceEntry?.first?.value,
            body = body,
        )
    }

    private data class KindHit(val kind: TxnKind, val start: Int, val end: Int)

    /**
     * The direction keyword nearest the start of the message. Bank templates
     * lead with the verb ("AED 50 debited from..."), and a later keyword is
     * usually part of a trailing clause.
     */
    private fun firstKindHit(text: String): KindHit? {
        val hits = mutableListOf<KindHit>()
        fun collect(words: List<String>, kind: TxnKind) {
            words.forEach { word ->
                val at = text.indexOf(word)
                if (at >= 0) hits += KindHit(kind, at, at + word.length)
            }
        }
        collect(debitWords, TxnKind.DEBIT)
        collect(creditWords, TxnKind.CREDIT)
        collect(salaryWords, TxnKind.SALARY)
        return hits.minByOrNull { it.start }
    }

    private fun isBalance(text: String, c: Candidate): Boolean = balanceWords.any { word ->
        val at = text.lastIndexOf(word, c.start)
        at >= 0 && c.start - (at + word.length) in 0..24
    }

    /** True when the number is an account, card or reference number. */
    private fun isIdentifier(text: String, c: Candidate): Boolean {
        // Only the characters touching the number: masked digits are written
        // "****8899", and the stars of one account number must not disqualify
        // every amount later in the message.
        val touching = text.substring(maxOf(0, c.start - 2), c.start)
        if (touching.contains('*') || touching.endsWith("x")) return true
        // A currency next to the number outranks a nearby "account": in
        // "account 123 debited AED 50" the 50 is still the amount.
        if (currencyNear(text, c) != null) return false
        val before = text.substring(maxOf(0, c.start - 18), c.start)
        return identifierWords.any { before.contains(it) }
    }

    /** The currency token touching this number, if any. */
    private fun currencyNear(text: String, c: Candidate): String? {
        val before = text.substring(maxOf(0, c.start - 16), c.start)
        val after = text.substring(c.end, minOf(text.length, c.end + 16))
        return currencies.firstOrNull { (token, _) ->
            before.contains(token) || after.contains(token)
        }?.second
    }

    private fun distance(c: Candidate, start: Int, end: Int): Int =
        if (c.start > end) c.start - end else start - c.end

    /** Phrases that introduce the other party. */
    private val counterpartyMarkers = listOf(
        " at ", " to ", " from ", " by ", " لدى ", " من ", " الى ", " إلى ", " في ",
    )

    /** Where a name ends: what follows belongs to another clause. */
    private val counterpartyStops = listOf(
        " on ", " at ", " dated", " avail", " bal", " ref", " card",
        " بتاريخ", " رصيد", " الرصيد", " المتاح", " في ", " الى ", " إلى ", " حسابك",
    )

    /**
     * A "name" made of these is really an account or a card. The Latin ones are
     * matched as whole words — "ref" inside "Carrefour" is a shop, not a
     * reference number — while Arabic words take prefixes and suffixes and so
     * are matched anywhere ("حسابك" is "حساب").
     */
    private val accountWords = listOf("a/c", "acc", "account", "card", "bal", "ref", "iban")
    private val accountWordsArabic = listOf("حساب", "بطاقة", "رصيد", "ايبان", "مرجع")

    /**
     * Merchant or payer, taken from the phrase that names it. Everything here
     * is a cut-off rule: bank templates run several clauses into one sentence,
     * and the account number is never the merchant.
     */
    private fun counterparty(body: String): String? {
        val lower = body.lowercase()
        for (marker in counterpartyMarkers) {
            val at = lower.indexOf(marker)
            if (at < 0) continue
            val from = at + marker.length
            var name = body.substring(from)
                .takeWhile { it != '\n' && it != ',' && it != ';' && it != '|' && it != '.' }
            counterpartyStops.forEach { stop ->
                val cut = name.lowercase().indexOf(stop)
                if (cut >= 0) name = name.substring(0, cut)
            }
            name = name.trim().trim('-', ':', '*', '/')
            val folded = name.lowercase()
            val words = folded.split(Regex("""[^a-z0-9/]+""")).filter { it.isNotEmpty() }
            val looksLikeAnAccount = words.any { it in accountWords || it.startsWith("xx") } ||
                accountWordsArabic.any { folded.contains(it) }
            val plausible = name.length in 3..34 &&
                name.any { it.isLetter() } &&
                name.count { it.isDigit() } * 2 < name.length &&
                !looksLikeAnAccount
            if (plausible) return name
        }
        return null
    }

    /**
     * Lowercases, maps Arabic-Indic digits to ASCII and folds the Arabic letter
     * variants banks mix freely. Every replacement is one character for one
     * character so the indices still point into the same positions.
     */
    internal fun normalize(body: String): String = buildString(body.length) {
        body.forEach { ch ->
            append(
                when (ch) {
                    in '٠'..'٩' -> ('0' + (ch - '٠'))   // ٠-٩
                    in '۰'..'۹' -> ('0' + (ch - '۰'))   // ۰-۹
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ﻻ' -> 'ل'
                    'ـ' -> ' '                                     // tatweel
                    in 'ً'..'ْ' -> ' '                        // diacritics
                    ' ' -> ' '
                    else -> ch.lowercaseChar()
                }
            )
        }
    }
}
