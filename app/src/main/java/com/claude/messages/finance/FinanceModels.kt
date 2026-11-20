package com.claude.messages.finance

/** What a bank message says happened to the money. */
enum class TxnKind {
    /** Pay landing in the account. Kept apart from an ordinary credit. */
    SALARY,

    /** Money in: a transfer, a refund, a deposit. */
    CREDIT,

    /** Money out: a card purchase, a withdrawal, a fee, a transfer sent. */
    DEBIT,
}

/** One money movement, read out of a single bank SMS. */
data class Transaction(
    val messageId: Long,
    val threadId: Long,
    /** The sender of the SMS — usually the bank's short code. */
    val source: String,
    val date: Long,
    val kind: TxnKind,
    val amount: Double,
    /** ISO code when recognised ("OMR"), the literal token otherwise, "" if absent. */
    val currency: String,
    /** Merchant or payer, when the message names one. */
    val counterparty: String?,
    /** Account balance quoted in the same message, if any. */
    val balance: Double?,
    val body: String,
) {
    val isIncome: Boolean get() = kind != TxnKind.DEBIT
}
