package com.sacco.app.util

import com.sacco.app.data.Loan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round

data class LoanQuote(
    val principal: Double, val interest: Double, val total: Double,
    val installments: Int, val installment: Double, val periodLabel: String
)

data class ScheduleItem(val number: Int, val dueDate: LocalDate, val amount: Double, val status: String)

/** Flat-rate loan maths: interest = principal x annual rate x months / 12. */
object LoanCalculator {
    fun round2(v: Double) = round(v * 100.0) / 100.0

    fun installmentCount(months: Int, frequency: String) = if (frequency == "Weekly") months * 4 else months

    fun quote(principal: Double, annualRate: Double, months: Int, frequency: String): LoanQuote {
        val interest = round2(principal * annualRate / 100.0 * months / 12.0)
        val total = round2(principal + interest)
        val n = installmentCount(months, frequency).coerceAtLeast(1)
        return LoanQuote(principal, interest, total, n, round2(total / n), "$months months ($n $frequency installments)")
    }

    /** Builds the repayment schedule and marks each installment PAID / PARTIAL / DUE / OVERDUE. */
    fun schedule(loan: Loan, today: LocalDate = LocalDate.now()): List<ScheduleItem> {
        val start = Instant.ofEpochMilli(loan.issuedAt ?: loan.appliedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        var cumulative = 0.0
        return (1..loan.installments).map { i ->
            val amount = if (i == loan.installments) round2(loan.totalRepayable - loan.installment * (i - 1)) else loan.installment
            val due = if (loan.frequency == "Weekly") start.plusWeeks(i.toLong()) else start.plusMonths(i.toLong())
            val previous = cumulative
            cumulative += amount
            val status = when {
                loan.amountRepaid >= cumulative - 0.005 -> "PAID"
                loan.amountRepaid > previous + 0.005 -> "PARTIAL"
                due.isBefore(today) -> "OVERDUE"
                else -> "DUE"
            }
            ScheduleItem(i, due, amount, status)
        }
    }

    /** Next installment that is not fully paid, or null when nothing is left. */
    fun nextDue(loan: Loan): ScheduleItem? = schedule(loan).firstOrNull { it.status != "PAID" }
}
