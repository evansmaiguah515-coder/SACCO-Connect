package com.sacco.app

import com.sacco.app.data.Loan
import com.sacco.app.util.LoanCalculator
import com.sacco.app.util.PasswordHasher
import com.sacco.app.util.Validators
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ValidatorsTest {
    @Test fun emailRules() { assertNull(Validators.email("jane@sacco.demo")); assertNotNull(Validators.email("jane@")); assertNotNull(Validators.email("")) }
    @Test fun phoneRules() { assertNull(Validators.phone("0712345678")); assertNull(Validators.phone("+254712345678")); assertNotNull(Validators.phone("12345")) }
    @Test fun passwordStrength() { assertNull(Validators.password("Member@123")); assertNotNull(Validators.password("short1A")); assertNotNull(Validators.password("alllowercase1")) }
    @Test fun confirmPassword() { assertNull(Validators.confirm("a", "a")); assertNotNull(Validators.confirm("a", "b")) }
    @Test fun dateOfBirth() {
        val today = LocalDate.of(2026, 10, 10)
        assertNull(Validators.dateOfBirth("1995-05-14", today)); assertNotNull(Validators.dateOfBirth("2015-01-01", today))
        assertNotNull(Validators.dateOfBirth("14/05/1995", today)); assertNotNull(Validators.dateOfBirth("2030-01-01", today))
    }
    @Test fun amountRange() { assertNull(Validators.amount(5000.0, 1000.0, 50000.0)); assertNotNull(Validators.amount(500.0, 1000.0, 50000.0)); assertNotNull(Validators.amount(60000.0, 1000.0, 50000.0)); assertNotNull(Validators.amount(-1.0, 1.0, 10.0)) }
    @Test fun registrationReportsEveryBadField() {
        val e = Validators.validateRegistration("", "", "", "", "", "", "", "", "", "")
        assertEquals(9, e.size)
        assertTrue(Validators.validateRegistration("SAC-0100", "Mary Achieng", "ID123456", "0711111111", "m@x.com", "Female", "1990-01-01", "Nairobi", "Passw0rdX", "Passw0rdX").isEmpty())
    }
}

class LoanCalculatorTest {
    @Test fun quoteMonthly() {
        val q = LoanCalculator.quote(20000.0, 10.0, 6, "Monthly")
        assertEquals(1000.0, q.interest, 0.001); assertEquals(21000.0, q.total, 0.001); assertEquals(6, q.installments); assertEquals(3500.0, q.installment, 0.001)
    }
    @Test fun quoteWeekly() { assertEquals(24, LoanCalculator.quote(12000.0, 12.0, 6, "Weekly").installments) }
    private fun loan(repaid: Double) = Loan(id = 1, loanNumber = "LN-1", userId = 1, productId = 1, loanType = "T", principal = 20000.0, interestRate = 10.0, interest = 1000.0,
        totalRepayable = 21000.0, installment = 3500.0, installments = 6, frequency = "Monthly", durationMonths = 6, purpose = "x", status = "ACTIVE",
        amountRepaid = repaid, appliedAt = 1_700_000_000_000L, issuedAt = 1_700_000_000_000L)
    @Test fun scheduleMarksPaidAndNextDue() {
        val s = LoanCalculator.schedule(loan(7000.0), LocalDate.of(2023, 1, 1))
        assertEquals(6, s.size); assertEquals("PAID", s[0].status); assertEquals("PAID", s[1].status); assertEquals("DUE", s[2].status)
        assertEquals(21000.0, s.sumOf { it.amount }, 0.001)
    }
    @Test fun partialAndOutstanding() {
        assertEquals("PARTIAL", LoanCalculator.schedule(loan(5000.0), LocalDate.of(2023, 1, 1))[1].status)
        assertEquals(14000.0, loan(7000.0).outstanding, 0.001)
    }
}

class PasswordHasherTest {
    @Test fun hashVerifiesAndIsSalted() {
        val salt = PasswordHasher.newSalt(); val h = PasswordHasher.hash("Member@123", salt)
        assertTrue(PasswordHasher.verify("Member@123", salt, h)); assertFalse(PasswordHasher.verify("wrong", salt, h))
        assertNotEquals("Member@123", h); assertNotEquals(h, PasswordHasher.hash("Member@123", PasswordHasher.newSalt()))
    }
}
