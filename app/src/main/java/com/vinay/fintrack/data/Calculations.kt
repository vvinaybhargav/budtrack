package com.vinay.fintrack.data

import java.util.Calendar
import java.util.Locale

data class SixMonthOutlook(
    val totalSalary: Double,
    val totalLoans: Double,
    val totalRecurring: Double,
    val totalSetAside: Double,
    val totalSurplus: Double,
    val monthlySalary: Double,
    val monthlyLoans: Double,
    val monthlyRecurring: Double,
    val monthlySetAside: Double,
    val monthlySurplus: Double
)

fun resolveNextDueDate(dueDay: Int, todayIso: String = today()): String {
    val day = dueDay.coerceIn(1, 31)
    val parts = todayIso.split("-")
    if (parts.size < 3) return todayIso
    val y = parts[0].toIntOrNull() ?: return todayIso
    val m = parts[1].toIntOrNull() ?: return todayIso
    
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.YEAR, y)
    calendar.set(Calendar.MONTH, m - 1) // 0-based
    val maxDayCurrentMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val targetDayInCurrentMonth = minOf(day, maxDayCurrentMonth)
    
    val targetDateCurrentMonth = "%04d-%02d-%02d".format(Locale.US, y, m, targetDayInCurrentMonth)
    
    return if (targetDateCurrentMonth >= todayIso) {
        targetDateCurrentMonth
    } else {
        val nextYear = if (m == 12) y + 1 else y
        val nextMonth = if (m == 12) 1 else m + 1
        calendar.set(Calendar.YEAR, nextYear)
        calendar.set(Calendar.MONTH, nextMonth - 1)
        val maxDayNextMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val targetDayInNextMonth = minOf(day, maxDayNextMonth)
        "%04d-%02d-%02d".format(Locale.US, nextYear, nextMonth, targetDayInNextMonth)
    }
}

fun calculateSixMonthOutlook(
    salaryAmount: Double,
    loans: List<Loan>,
    entries: List<Entry>,
    todayIso: String = today()
): SixMonthOutlook {
    val monthlySalary = salaryAmount
    val totalSalary = salaryAmount * 6.0

    var totalLoans = 0.0
    // Monthly is active loan EMIs + any EMI category commitments
    val monthlyLoans = loans.sumOf { it.monthlyEmi } + entries.filter {
        it.type == "EXPENSE" && (it.category == "EMI" || it.category.lowercase().contains("emi") || it.category.lowercase().contains("loan"))
    }.sumOf { it.monthly }

    for (m in 1..6) {
        val activeLoansEmi = loans.filter { it.remainingMonths >= m }.sumOf { it.monthlyEmi }
        val emiBills = entries.filter {
            it.type == "EXPENSE" && (it.category == "EMI" || it.category.lowercase().contains("emi") || it.category.lowercase().contains("loan"))
        }.sumOf { it.monthly }
        totalLoans += activeLoansEmi + emiBills
    }

    val monthlyRecurring = entries.filter {
        it.type == "EXPENSE" && !it.isSetAside &&
                it.category != "EMI" && !it.category.lowercase().contains("emi") && !it.category.lowercase().contains("loan")
    }.sumOf { it.monthly }
    val totalRecurring = monthlyRecurring * 6.0

    var totalSetAside = 0.0
    val monthlySetAside = entries.filter {
        it.type == "SAVINGS" || (it.type == "EXPENSE" && it.isSetAside)
    }.sumOf { e ->
        val due = Ledger.nextDue(e.dueDate, e.everyMonths, todayIso)
        if (due.isEmpty()) Ledger.monthlyShare(e.amount, e.everyMonths)
        else Ledger.shareUntilDue(e.amount, todayIso, due)
    }

    for (m in 1..6) {
        val on = Ledger.addMonths(todayIso, m)
        val setAsideInMonth = entries.filter {
            it.type == "SAVINGS" || (it.type == "EXPENSE" && it.isSetAside)
        }.sumOf { e ->
            val due = Ledger.nextDue(e.dueDate, e.everyMonths, on)
            if (due.isEmpty()) Ledger.monthlyShare(e.amount, e.everyMonths)
            else Ledger.shareUntilDue(e.amount, on, due)
        }
        totalSetAside += setAsideInMonth
    }

    val totalSurplus = totalSalary - totalLoans - totalRecurring - totalSetAside
    val monthlySurplus = monthlySalary - monthlyLoans - monthlyRecurring - monthlySetAside

    return SixMonthOutlook(
        totalSalary = Ledger.paise(totalSalary),
        totalLoans = Ledger.paise(totalLoans),
        totalRecurring = Ledger.paise(totalRecurring),
        totalSetAside = Ledger.paise(totalSetAside),
        totalSurplus = Ledger.paise(totalSurplus),
        monthlySalary = Ledger.paise(monthlySalary),
        monthlyLoans = Ledger.paise(monthlyLoans),
        monthlyRecurring = Ledger.paise(monthlyRecurring),
        monthlySetAside = Ledger.paise(monthlySetAside),
        monthlySurplus = Ledger.paise(monthlySurplus)
    )
}
