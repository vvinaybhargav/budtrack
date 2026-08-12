package com.vinay.fintrack.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.vinay.fintrack.sms.Notifier

/**
 * Turns bank SMS into transactions. Runs from the broadcast receiver as
 * messages arrive, and over the inbox for a one-off backfill, so it has to work
 * without the app being on screen.
 *
 * Message text is parsed and discarded — only the parsed fields are stored, and
 * nothing raw is ever uploaded.
 */
class SmsImporter(private val context: Context) {

    private val store = Store(context)

    /** Reads one freshly-arrived message. Returns true if it became a transaction. */
    fun importOne(body: String, sender: String, receivedAt: Long): Boolean {
        val state = store.load()
        if (!state.smsImportOn) return false

        val parsed = parseBankSms(body, sender)
        if (parsed == null) {
            // Recorded rather than dropped silently: without this there is no way
            // to tell a wrongly-worded alert from one that never arrived.
            store.save(state.copy(smsLog = note(state, "skipped $sender — ${skipReason(body)}")))
            return false
        }
        if (parsed.dedupeKey in state.importedRefs) {
            store.save(state.copy(smsLog = note(state, "already had ${inr(parsed.amount)} ${parsed.party}")))
            return false
        }
        val after = apply(state, listOf(parsed.copy(receivedAt = receivedAt)), maxOf(state.lastSmsScan, receivedAt))
        store.save(after)

        // Announce what the message became. The point is catching a wrong
        // account or category while the payment is still fresh in your mind.
        val known = state.txns.map { it.id }.toSet()
        after.txns.filterNot { it.id in known }
            .forEach { Notifier.notifyRecorded(context, it) }
        return true
    }

    private fun note(state: PersistedState, line: String): List<String> =
        (listOf(line) + state.smsLog).take(MAX_LOG)

    /**
     * Reads the SMS inbox for the last [days] days. Used once when the feature
     * is switched on, so history isn't lost.
     */
    fun backfill(days: Int = 60): Int {
        val state = store.load()
        val since = maxOf(state.lastSmsScan, daysAgoMillis(days))
        val found = mutableListOf<ParsedSms>()
        var newest = state.lastSmsScan

        val projection = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE
        )
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val sender = c.getString(addr).orEmpty()
                    val body = c.getString(bodyCol).orEmpty()
                    val at = c.getLong(dateCol)
                    newest = maxOf(newest, at)
                    if (!looksLikeBankSender(sender)) continue
                    parseBankSms(body, sender)?.let { found += it.copy(receivedAt = at) }
                }
            }
        }.onFailure { Log.w(TAG, "inbox read failed", it) }

        if (found.isEmpty()) {
            store.save(state.copy(lastSmsScan = maxOf(newest, state.lastSmsScan)))
            return 0
        }
        val fresh = found.distinctBy { it.dedupeKey }
            .filterNot { it.dedupeKey in state.importedRefs }
        store.save(apply(state, fresh, newest))
        return fresh.size
    }

    /**
     * Writes the parsed messages into state as transactions.
     *
     * A payment you already confirmed by hand — an EMI, say — will also be
     * texted about by the bank. Rather than recording it twice, the existing
     * transaction is annotated with the bank's reference.
     */
    private fun apply(state: PersistedState, items: List<ParsedSms>, newest: Long): PersistedState {
        val txns = state.txns.toMutableList()
        var matched = 0
        var added = 0
        val log = mutableListOf<String>()
        val cardSpend = mutableMapOf<String, Double>()
        val bodies = mutableMapOf<String, String>()
        val smsSuggestions = state.smsSuggestions.toMutableMap()

        for (p in items) {
            val existing = matchingConfirmed(txns, p)
            if (existing != null) {
                txns[txns.indexOf(existing)] = existing.copy(
                    ref = p.ref.ifEmpty { existing.ref },
                    source = "sms+confirm"
                )
                matched++
                log += "matched ${inr(p.amount)} ${p.party} to an existing confirmation"
                continue
            }
            // A card spend adds to what the card owes; it doesn't leave any
            // bank account until the bill is paid.
            val card = ((matchCardByTail(state.cards, p.accountTail) as? AccountMatch.One)
                ?: (matchCardByBank(state.cards, p.body) as? AccountMatch.One))?.accountId
            val accountId =
                if (card != null) "" else accountFor(state, p.accountTail, p.body, log)

            // Money moved between your own banks arrives as two messages, and
            // stays two transactions — each bank sent its own, and each shows
            // against its own account.
            //
            // Both are marked as transfers rather than a spend and a receipt.
            // The money never left the household, so counting the debit as
            // spending and the credit as income would inflate both sides of the
            // month for a payment that only changed hands.
            val otherLeg = if (card != null) null else otherLegOf(txns, p, accountId)
            if (otherLeg != null) {
                txns[txns.indexOf(otherLeg)] = otherLeg.copy(kind = "TRANSFER")
                matched++
                log += "paired ${inr(p.amount)} as a transfer between your accounts"
            }

            val newTxn = Txn(
                id = newId("t"),
                date = p.date,
                kind = when {
                    otherLeg != null -> "TRANSFER"
                    p.isRefund -> "REFUND"
                    p.isCredit -> "INCOME"
                    else -> "EXPENSE"
                },
                amount = p.amount,
                category = categoryFor(state, p.party),
                fromAccountId = if (p.isCredit || card != null) "" else accountId,
                toAccountId = if (p.isCredit && card == null) accountId else "",
                cardId = card.orEmpty(),
                period = Ledger.cycleOf(p.date, state.cycleResetDay),
                at = if (p.receivedAt > 0L) p.receivedAt else millisOfDate(p.date),
                note = p.party.ifEmpty {
                    if (p.isRefund) "Refund" else if (p.isCredit) "Credit" else "Payment"
                },
                ref = p.ref,
                source = "sms",
                rawAmountText = p.amountText,
                // Only when unmatched: otherwise it is noise on a row that is
                // already filed correctly.
                accountTail = if (accountId.isEmpty() && card == null) p.accountTail else ""
            )
            txns += newTxn
            bodies[newTxn.id] = p.body

            // Sinking Fund Match Suggestion logic
            if (newTxn.kind == "EXPENSE" && newTxn.cardId.isEmpty()) {
                val matchingSetAside = state.entries.firstOrNull { e ->
                    e.isSetAside && !e.closed && (
                        e.category.equals(newTxn.category, true) ||
                        e.category.equals(p.party, true) ||
                        (e.note.isNotEmpty() && p.party.lowercase().contains(e.note.lowercase()))
                    )
                }
                if (matchingSetAside != null) {
                    val pot = Ledger.setAsidePot(txns, matchingSetAside.id)
                    if (pot < matchingSetAside.amount) {
                        smsSuggestions[newTxn.id] = matchingSetAside.id
                    }
                }
            }

            // A refund to a card reduces what the card owes, the mirror of a
            // spend on it. Ignoring it left the returned money owed forever.
            if (card != null && (!p.isCredit || p.isRefund)) {
                val delta = if (p.isRefund) -p.amount else p.amount
                cardSpend[card] = (cardSpend[card] ?: 0.0) + delta
            }
            added++
            val who = p.party.ifEmpty { "unnamed payment" }
            log += if (card != null) "added ${inr(p.amount)} $who to the card"
            else "added ${inr(p.amount)} $who"
        }

        // A category invented from a payee has to join the list, or budgets and
        // the pickers never see it.
        val fresh = txns.map { it.category }
            .filter { it.isNotBlank() && state.categories.none { c -> c.equals(it, true) } }
            .distinct()

        Log.i(TAG, "SMS import: $added added, $matched matched to existing confirmations")
        return state.copy(
            txns = txns,
            categories = state.categories + fresh,
            cards = state.cards.map { c ->
                cardSpend[c.id]?.let {
                    c.copy(balance = (c.balance + it).coerceAtLeast(0.0), paid = false)
                } ?: c
            },
            importedRefs = capRefs(state.importedRefs + items.map { it.dedupeKey }),
            smsBodies = capBodies(state.smsBodies + bodies),
            smsLog = (log.asReversed() + state.smsLog).take(MAX_LOG),
            lastSmsScan = maxOf(newest, state.lastSmsScan),
            smsSuggestions = smsSuggestions
        )
    }

    /**
     * An existing hand-confirmed transaction for the same payment: same
     * direction, same amount, within a few days, and carrying no bank
     * reference of its own yet.
     */
    private fun matchingConfirmed(txns: List<Txn>, p: ParsedSms): Txn? =
        // The far half of something already confirmed by hand. Confirming a
        // set-aside makes one transfer, but both banks text about it: the debit
        // message stamped the confirmation with this reference, so the credit
        // message is that same money arriving. Recorded on its own it became
        // income, and the month showed a salary that never happened.
        txns.firstOrNull { p.ref.isNotEmpty() && it.ref == p.ref && it.source == "sms+confirm" }
            ?: txns.firstOrNull { Ledger.isSamePayment(it, p.amount, p.date, p.isCredit) }

    /**
     * The already-imported half of the same transfer: the bank's own reference,
     * the same amount, the opposite direction, and a different account.
     *
     * The reference is required. Without it this would join any two unrelated
     * payments that happened to be for the same amount on the same day, and a
     * wrong transfer is worse than two honest rows.
     */
    private fun otherLegOf(txns: List<Txn>, p: ParsedSms, accountId: String): Txn? {
        if (p.ref.isEmpty() || accountId.isEmpty()) return null
        return txns.firstOrNull { t ->
            t.ref == p.ref &&
                t.source == "sms" &&
                t.cardId.isEmpty() &&
                kotlin.math.abs(t.amount - p.amount) < 0.5 &&
                t.kind == (if (p.isCredit) "EXPENSE" else "INCOME") &&
                // The far end must be a different account, or this is one
                // message the bank sent twice rather than two halves.
                t.fromAccountId.ifEmpty { t.toAccountId } != accountId
        }
    }

    /** Unbounded growth in SharedPreferences helps nobody; recent keys are
     *  enough to stop a re-read duplicating. */
    private fun capRefs(refs: Set<String>): Set<String> =
        if (refs.size <= MAX_REFS) refs else refs.toList().takeLast(MAX_REFS).toSet()

    /** Message bodies are the bulkiest thing stored; keep only recent ones. */
    private fun capBodies(bodies: Map<String, String>): Map<String, String> =
        if (bodies.size <= MAX_BODIES) bodies
        else bodies.entries.toList().takeLast(MAX_BODIES).associate { it.key to it.value }

    /**
     * The account the message names, or nothing when it cannot be told.
     *
     * Never a guess. An account decides which side of the household a payment
     * lands on and whose balance moves, so guessing wrong is worse than leaving
     * it plainly unset for you to finish.
     */
    private fun accountFor(
        state: PersistedState,
        tail: String,
        body: String,
        log: MutableList<String>
    ): String {
        // No account rather than a guessed one.
        //
        // Guessing put payments on accounts they never touched, and a balance
        // built from guesses is worth nothing. An empty account moves no
        // balance and shows as "Account not set", which is a job to finish
        // rather than a wrong number to find later.
        val fallback = ""

        // Digits are the reliable answer, so they go first.
        when (val byTail = matchAccountByTail(state.accounts, tail)) {
            is AccountMatch.One -> return byTail.accountId
            is AccountMatch.Ambiguous -> {
                log += "…$tail matches ${byTail.count} accounts — add a digit to tell them apart"
                return fallback
            }
            AccountMatch.None -> Unit
        }

        // Then the bank's own name, so an account works before its digits are
        // recorded rather than everything piling onto the shared one.
        return when (val byBank = matchAccountByBank(state.accounts, body)) {
            is AccountMatch.One -> byBank.accountId
            is AccountMatch.Ambiguous -> {
                log += "two accounts at that bank — set their last digits"
                fallback
            }
            AccountMatch.None -> {
                log += if (tail.isNotEmpty()) "…$tail matches no account — left for you to set"
                else "no account named — left for you to set"
                fallback
            }
        }
    }

    private fun categoryFor(state: PersistedState, party: String): String {
        val lowerParty = party.lowercase()
        for ((pattern, category) in state.smsRules) {
            if (lowerParty.contains(pattern.lowercase())) {
                return category
            }
        }
        return categoryForParty(party, state.categories, state.payeeCategories)
    }

    private companion object {
        const val TAG = "SmsImporter"
        const val MAX_REFS = 500
        const val MAX_LOG = 25
        const val MAX_BODIES = 300
    }
}
