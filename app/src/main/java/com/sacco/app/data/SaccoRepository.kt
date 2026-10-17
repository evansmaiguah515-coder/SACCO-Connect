package com.sacco.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sacco.app.BuildConfig
import com.sacco.app.util.LoanCalculator
import com.sacco.app.util.Notifier
import com.sacco.app.util.PasswordHasher
import com.sacco.app.util.SessionManager
import com.sacco.app.util.Validators
import com.sacco.app.util.fmtDate
import com.sacco.app.util.money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Result of Google sign-in: either a known member, or a new Google user who must complete a member profile. */
sealed interface GoogleOutcome {
    data class Existing(val user: User) : GoogleOutcome
    data class NeedsProfile(val uid: String, val email: String, val name: String) : GoogleOutcome
}

/**
 * Owns every business rule. Screens read through [dao] flows; all writes go through here so that
 * validation, transactions, ledger entries and notifications always stay consistent.
 */
class SaccoRepository(
    private val db: SaccoDatabase,
    private val context: Context,
    private val session: SessionManager,
    private val auth: FirebaseAuthService,
    private val mpesa: MpesaClient
) {
    val dao: SaccoDao = db.dao()
    private val notifier = Notifier(context)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)   // keeps payment polling alive if the user leaves the screen

    val firebaseSignedIn: Boolean get() = auth.currentUid != null
    fun signOutFirebase() = auth.signOut()

    companion object {
        const val SHARE_VALUE = 100.0
        const val MIN_SHARES = 50
        const val LIMIT_MULTIPLIER = 3.0   // loan limit = 3 x savings
        const val FIREBASE_MARK = "FIREBASE"   // passwordHash value for accounts whose password lives in Firebase
    }

    private suspend fun <T> tx(block: suspend () -> T): T = db.withTransaction { block() }
    private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)

    private suspend fun pushNotification(userId: Long, title: String, message: String, type: String) {
        dao.insertNotification(AppNotification(userId = userId, title = title, message = message, type = type))
        if (session.userId.value == userId) notifier.show(title, message)
    }

    private suspend fun record(userId: Long, type: String, amount: Double, description: String, at: Long = System.currentTimeMillis()) {
        dao.insertTransaction(
            SaccoTransaction(reference = "TXN-" + UUID.randomUUID().toString().take(8).uppercase(),
                userId = userId, type = type, amount = amount, description = description, createdAt = at)
        )
    }

    // ------------------------------------------------------------------ authentication
    private val invalidLogin = "Invalid email, phone, member number or password"

    /** Creates the Firebase account first, then the local member profile (status PENDING). No password is stored locally. */
    suspend fun register(
        memberNumber: String, fullName: String, nationalId: String, phone: String, email: String,
        gender: String, dob: String, address: String, password: String, confirm: String
    ): Result<Unit> = runCatching {
        Validators.validateRegistration(memberNumber, fullName, nationalId, phone, email, gender, dob, address, password, confirm)
            .values.firstOrNull()?.let { fail(it) }
        if (dao.countMemberNumber(memberNumber.trim()) > 0) fail("This member number is already registered")
        if (dao.findByEmail(email.trim()) != null) fail("This email is already registered")
        val uid = auth.createAccount(email.trim().lowercase(), password).getOrThrow()
        val id = dao.insertUser(
            User(memberNumber = memberNumber.trim().uppercase(), fullName = fullName.trim(), nationalId = nationalId.trim(),
                phone = phone.trim(), email = email.trim().lowercase(), gender = gender, dateOfBirth = dob.trim(),
                address = address.trim(), passwordHash = FIREBASE_MARK, salt = "", firebaseUid = uid)
        )
        pushNotification(id, "Registration successful", "Welcome to SACCO Connect. Your membership is awaiting approval by the administrator.", "ACCOUNT")
        auth.signOut()   // the member signs in explicitly on the login screen
    }

    /**
     * Signs in with Firebase. Demo/legacy accounts that only have a local password are migrated to Firebase
     * the first time they sign in with the correct password.
     */
    suspend fun login(identifier: String, password: String): Result<User> = runCatching {
        val local = dao.findForLogin(identifier.trim()) ?: fail(invalidLogin)
        if (local.status == "REJECTED") fail("Your membership application was rejected. Please contact the SACCO office.")
        val first = auth.signIn(local.email, password)
        var uid = first.getOrNull()
        if (uid == null) {
            val err = first.exceptionOrNull() as? AuthException
            val canMigrate = local.passwordHash != FIREBASE_MARK && err?.badCredentials == true &&
                PasswordHasher.verify(password, local.salt, local.passwordHash)
            if (!canMigrate) throw if (err != null && !err.badCredentials) err else IllegalArgumentException(invalidLogin)
            uid = auth.createAccount(local.email, password).getOrElse { fail(invalidLogin) }
        }
        val linked = local.copy(firebaseUid = uid, passwordHash = FIREBASE_MARK, salt = "")
        if (linked != local) dao.updateUser(linked)
        linked
    }

    /** Google sign-in using an ID token from Credential Manager. */
    suspend fun googleSignIn(idToken: String): Result<GoogleOutcome> = runCatching {
        val acc = auth.signInWithGoogle(idToken).getOrThrow()
        val local = dao.findByEmail(acc.email)
        if (local == null) GoogleOutcome.NeedsProfile(acc.uid, acc.email, acc.name)
        else {
            if (local.status == "REJECTED") { auth.signOut(); fail("Your membership application was rejected. Please contact the SACCO office.") }
            val linked = local.copy(firebaseUid = acc.uid)
            if (linked != local) dao.updateUser(linked)
            GoogleOutcome.Existing(linked)
        }
    }

    /** Creates the member profile for a Google user who has no profile yet. */
    suspend fun completeGoogleProfile(
        uid: String, email: String, memberNumber: String, fullName: String, nationalId: String,
        phone: String, gender: String, dob: String, address: String
    ): Result<User> = runCatching {
        Validators.validateProfileFields(memberNumber, fullName, nationalId, phone, email, gender, dob, address).values.firstOrNull()?.let { fail(it) }
        if (dao.countMemberNumber(memberNumber.trim()) > 0) fail("This member number is already registered")
        if (dao.findByEmail(email) != null) fail("This email is already registered")
        val user = User(memberNumber = memberNumber.trim().uppercase(), fullName = fullName.trim(), nationalId = nationalId.trim(), phone = phone.trim(),
            email = email.trim().lowercase(), gender = gender, dateOfBirth = dob.trim(), address = address.trim(), passwordHash = FIREBASE_MARK, salt = "", firebaseUid = uid)
        val id = dao.insertUser(user)
        pushNotification(id, "Registration successful", "Welcome to SACCO Connect. Your membership is awaiting approval by the administrator.", "ACCOUNT")
        user.copy(id = id)
    }

    /** Firebase emails a reset link. We never reveal whether the address is registered. */
    suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        Validators.email(email)?.let { fail(it) }
        auth.sendPasswordReset(email.trim().lowercase()).onFailure { e ->
            if ((e as? AuthException)?.badCredentials != true) throw e   // unknown email is silently accepted
        }
        Unit
    }

    suspend fun changePassword(userId: Long, old: String, new: String, confirm: String): Result<Unit> = runCatching {
        Validators.required(old, "Current password")?.let { fail(it) }
        Validators.password(new)?.let { fail(it) }
        Validators.confirm(new, confirm)?.let { fail(it) }
        auth.changePassword(old, new).onFailure { e ->
            throw if ((e as? AuthException)?.badCredentials == true) IllegalArgumentException("Current password is incorrect") else e
        }
        pushNotification(userId, "Password changed", "Your password was changed successfully.", "ACCOUNT")
    }

    suspend fun updateProfile(userId: Long, fullName: String, phone: String, email: String, address: String): Result<Unit> = runCatching {
        Validators.fullName(fullName)?.let { fail(it) }
        Validators.phone(phone)?.let { fail(it) }
        Validators.email(email)?.let { fail(it) }
        Validators.required(address, "Address")?.let { fail(it) }
        val user = dao.getUser(userId) ?: fail("Account not found")
        val other = dao.findByEmail(email.trim())
        if (other != null && other.id != userId) fail("That email belongs to another member")
        dao.updateUser(user.copy(fullName = fullName.trim(), phone = phone.trim(), email = email.trim().lowercase(), address = address.trim()))
    }

    suspend fun savePhoto(userId: Long, uri: Uri): Result<Unit> = runCatching {
        val user = dao.getUser(userId) ?: fail("Account not found")
        val file = File(context.filesDir, "profile_${userId}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: fail("Could not read the selected image")
        user.photoPath?.let { File(it).delete() }
        dao.updateUser(user.copy(photoPath = file.absolutePath))
    }

    // ------------------------------------------------------------------ savings and shares
    suspend fun deposit(userId: Long, amount: Double, receipt: String? = null): Result<Unit> = runCatching {
        Validators.amount(amount, 10.0, 1_000_000.0)?.let { fail(it) }
        tx {
            val label = if (receipt != null) "M-Pesa deposit ($receipt)" else "Savings deposit"
            dao.insertSaving(Saving(userId = userId, type = "DEPOSIT", amount = amount, note = label))
            record(userId, "DEPOSIT", amount, label)
            pushNotification(userId, "Deposit successful", "Your deposit of ${money(amount)} was received." + (receipt?.let { " M-Pesa receipt: $it" } ?: ""), "TRANSACTION")
        }
    }

    suspend fun withdraw(userId: Long, amount: Double): Result<Unit> = runCatching {
        val user = dao.getUser(userId) ?: fail("Account not found")
        if (user.status != "ACTIVE") fail("Withdrawals are available after your membership is approved")
        val balance = dao.savingsBalance(userId)
        Validators.amount(amount, 10.0, balance.coerceAtLeast(10.0))?.let { fail(it) }
        if (amount > balance) fail("Insufficient savings balance (${money(balance)})")
        tx {
            dao.insertSaving(Saving(userId = userId, type = "WITHDRAWAL", amount = amount, note = "Savings withdrawal"))
            record(userId, "WITHDRAWAL", amount, "Savings withdrawal")
            pushNotification(userId, "Withdrawal successful", "You withdrew ${money(amount)} from your savings.", "TRANSACTION")
        }
    }

    suspend fun buyShares(userId: Long, count: Int): Result<Unit> = runCatching {
        if (count !in 1..1000) fail("Enter between 1 and 1,000 shares")
        val amount = count * SHARE_VALUE
        tx {
            dao.insertShare(ShareTransaction(userId = userId, shares = count, unitValue = SHARE_VALUE, amount = amount))
            record(userId, "SHARE_CONTRIBUTION", amount, "Purchase of $count shares")
            pushNotification(userId, "Shares purchased", "You bought $count shares worth ${money(amount)}.", "TRANSACTION")
        }
    }

    // ------------------------------------------------------------------ loans
    suspend fun availableLimit(userId: Long): Double {
        val outstanding = dao.loansOfNow(userId).filter { it.status == "PENDING" || it.status == "ACTIVE" }.sumOf { it.outstanding }
        return (dao.savingsBalance(userId) * LIMIT_MULTIPLIER - outstanding).coerceAtLeast(0.0)
    }

    suspend fun applyLoan(userId: Long, product: SaccoProduct, amount: Double, months: Int, frequency: String, purpose: String): Result<Long> = runCatching {
        val user = dao.getUser(userId) ?: fail("Account not found")
        if (user.status != "ACTIVE") fail("Your membership must be approved before you can apply for a loan")
        Validators.amount(amount, product.minAmount, product.maxAmount)?.let { fail(it) }
        if (months !in 1..product.maxMonths) fail("Duration must be between 1 and ${product.maxMonths} months")
        Validators.required(purpose, "Loan purpose")?.let { fail(it) }
        val limit = availableLimit(userId)
        if (amount > limit) fail("Amount exceeds your available loan limit of ${money(limit)}")
        val q = LoanCalculator.quote(amount, product.interestRate, months, frequency)
        tx {
            val id = dao.insertLoan(
                Loan(loanNumber = "TEMP", userId = userId, productId = product.id, loanType = product.name, principal = q.principal,
                    interestRate = product.interestRate, interest = q.interest, totalRepayable = q.total, installment = q.installment,
                    installments = q.installments, frequency = frequency, durationMonths = months, purpose = purpose.trim(), status = "PENDING")
            )
            dao.updateLoan(dao.getLoan(id)!!.let { it.copy(loanNumber = "LN-%05d".format(id)) })
            pushNotification(userId, "Loan application submitted", "Your ${product.name} application for ${money(amount)} is awaiting review.", "LOAN")
            id
        }
    }

    suspend fun approveLoan(loanId: Long): Result<Unit> = runCatching {
        tx {
            val loan = dao.getLoan(loanId) ?: fail("Loan not found")
            if (loan.status != "PENDING") fail("This application has already been processed")
            dao.updateLoan(loan.copy(status = "ACTIVE", issuedAt = System.currentTimeMillis(), adminNote = "Approved"))
            record(loan.userId, "LOAN_DISBURSEMENT", loan.principal, "Loan ${loan.loanNumber} disbursed")
            pushNotification(loan.userId, "Loan approved", "Your loan ${loan.loanNumber} of ${money(loan.principal)} was approved and disbursed.", "LOAN")
        }
    }

    suspend fun rejectLoan(loanId: Long, reason: String): Result<Unit> = runCatching {
        Validators.required(reason, "Reason")?.let { fail(it) }
        tx {
            val loan = dao.getLoan(loanId) ?: fail("Loan not found")
            if (loan.status != "PENDING") fail("This application has already been processed")
            dao.updateLoan(loan.copy(status = "REJECTED", adminNote = reason.trim()))
            pushNotification(loan.userId, "Loan rejected", "Your loan ${loan.loanNumber} was rejected: ${reason.trim()}", "LOAN")
        }
    }

    suspend fun repayLoan(loanId: Long, amount: Double, receipt: String? = null): Result<Unit> = runCatching {
        tx {
            val loan = dao.getLoan(loanId) ?: fail("Loan not found")
            if (loan.status != "ACTIVE") fail("This loan is not active")
            Validators.amount(amount, 1.0, loan.outstanding)?.let { fail(it) }
            val paid = LoanCalculator.round2(loan.amountRepaid + amount)
            val done = loan.totalRepayable - paid <= 0.005
            dao.updateLoan(loan.copy(amountRepaid = paid, status = if (done) "FULLY_PAID" else "ACTIVE"))
            dao.insertRepayment(LoanRepayment(loanId = loanId, userId = loan.userId, amount = amount,
                reference = receipt ?: ("RPY-" + UUID.randomUUID().toString().take(8).uppercase())))
            record(loan.userId, "LOAN_REPAYMENT", amount, "Repayment for ${loan.loanNumber}" + (receipt?.let { " (M-Pesa $it)" } ?: ""))
            pushNotification(loan.userId, "Loan repayment received",
                "We received ${money(amount)} for ${loan.loanNumber}." + (receipt?.let { " M-Pesa receipt: $it." } ?: "") + if (done) " Your loan is now fully paid." else "", "LOAN")
        }
    }

    // ------------------------------------------------------------------ M-Pesa payments
    private fun toMsisdn(phone: String): String {
        val p = phone.trim().removePrefix("+")
        return if (p.startsWith("0")) "254" + p.drop(1) else p
    }

    /** Validates, stores a PENDING payment and asks the backend to send the STK Push (or simulates it). Returns the payment id. */
    suspend fun startMpesaPayment(userId: Long, purpose: String, loanId: Long?, phone: String, amount: Double): Result<Long> = runCatching {
        Validators.phone(phone)?.let { fail(it) }
        if (amount != Math.floor(amount)) fail("M-Pesa amounts must be whole shillings")
        if (purpose == "DEPOSIT") Validators.amount(amount, 10.0, 150_000.0)?.let { fail(it) }
        else {
            val loan = dao.getLoan(loanId ?: -1) ?: fail("Loan not found")
            if (loan.userId != userId) fail("That loan does not belong to you")
            if (loan.status != "ACTIVE") fail("This loan is not active")
            Validators.amount(amount, 1.0, loan.outstanding.coerceAtLeast(1.0))?.let { fail(it) }
        }
        val simulated = BuildConfig.MPESA_SIMULATED
        val payment = MpesaPayment(userId = userId, purpose = purpose, loanId = loanId, phone = toMsisdn(phone), amount = amount, simulated = simulated)
        val id = dao.insertPayment(payment)
        val saved = payment.copy(id = id)
        if (simulated) {
            dao.updatePayment(saved.copy(checkoutRequestId = "SIM-$id", message = "Simulated STK Push sent"))
        } else {
            val token = auth.idToken().getOrElse { failPayment(id, "FAILED", it.message ?: "Not signed in"); throw it }
            val r = mpesa.stkPush(token, saved.phone, amount.toInt(), "SACCO-$id")
                .getOrElse { failPayment(id, "FAILED", it.message ?: "Payment request failed"); throw it }
            dao.updatePayment(saved.copy(checkoutRequestId = r.checkoutRequestId, message = r.message))
        }
        id
    }

    /** Waits for the outcome in the background (keeps running even if the screen is closed). */
    fun trackPayment(paymentId: Long) { appScope.launch { awaitMpesaResult(paymentId) } }

    private suspend fun awaitMpesaResult(paymentId: Long) {
        val payment = dao.getPayment(paymentId) ?: return
        if (payment.simulated) {
            delay(4000)
            // Demo rule: a phone number ending in 0000 simulates the customer cancelling the prompt.
            if (payment.phone.endsWith("0000")) failPayment(paymentId, "CANCELLED", "Request cancelled by the customer (simulated)")
            else completePayment(paymentId, "SIM" + UUID.randomUUID().toString().take(7).uppercase())
            return
        }
        val checkoutId = payment.checkoutRequestId ?: return
        repeat(24) {   // about 72 seconds
            delay(3000)
            val token = auth.idToken().getOrNull() ?: return@repeat
            val s = mpesa.status(token, checkoutId).getOrNull() ?: return@repeat   // ignore short network glitches
            when (s.status) {
                "SUCCESS" -> { completePayment(paymentId, s.receipt ?: "CONFIRMED"); return }
                "FAILED", "CANCELLED" -> { failPayment(paymentId, s.status, s.message.ifBlank { "Payment was not completed" }); return }
            }
        }
        failPayment(paymentId, "FAILED", "No confirmation received in time. If money left your M-Pesa, contact the SACCO office.")
    }

    private suspend fun completePayment(paymentId: Long, receipt: String) {
        try {
            tx {
                val p = dao.getPayment(paymentId) ?: return@tx
                if (p.status != "PENDING") return@tx
                dao.updatePayment(p.copy(status = "SUCCESS", receipt = receipt, message = "Payment confirmed"))
                if (p.purpose == "DEPOSIT") deposit(p.userId, p.amount, receipt).getOrThrow()
                else repayLoan(p.loanId!!, p.amount, receipt).getOrThrow()
            }
        } catch (e: Exception) {
            failPayment(paymentId, "FAILED", "Payment received (receipt $receipt) but could not be applied: ${e.message}. Contact the SACCO office.")
        }
    }

    private suspend fun failPayment(paymentId: Long, status: String, message: String) {
        val p = dao.getPayment(paymentId) ?: return
        if (p.status != "PENDING") return
        dao.updatePayment(p.copy(status = status, message = message))
        pushNotification(p.userId, "M-Pesa payment ${status.lowercase()}", "${money(p.amount)}: $message", "TRANSACTION")
    }

    // ------------------------------------------------------------------ administrator actions
    suspend fun setMemberStatus(userId: Long, status: String): Result<Unit> = runCatching {
        val user = dao.getUser(userId) ?: fail("Member not found")
        dao.updateUser(user.copy(status = status))
        if (status == "ACTIVE") pushNotification(userId, "Membership approved", "Your membership is now active. You can apply for loans.", "ACCOUNT")
        else pushNotification(userId, "Membership rejected", "Your membership application was rejected. Contact the SACCO office.", "ACCOUNT")
    }

    suspend fun postAnnouncement(title: String, message: String): Result<Int> = runCatching {
        Validators.required(title, "Title")?.let { fail(it) }
        Validators.required(message, "Message")?.let { fail(it) }
        val ids = dao.memberIds()
        dao.insertNotifications(ids.map { AppNotification(userId = it, title = title.trim(), message = message.trim(), type = "ANNOUNCEMENT") })
        ids.forEach { if (session.userId.value == it) notifier.show(title.trim(), message.trim()) }
        ids.size
    }

    suspend fun sendRepaymentReminders(): Result<Int> = runCatching {
        val loans = dao.activeLoansNow()
        loans.forEach { loan ->
            val next = LoanCalculator.nextDue(loan)
            if (next != null) pushNotification(loan.userId, "Upcoming loan repayment",
                "Installment ${next.number} of ${money(next.amount)} for ${loan.loanNumber} is due on ${next.dueDate}.", "REMINDER")
        }
        loans.size
    }

    suspend fun addProduct(name: String, description: String, rate: Double, min: Double, max: Double, months: Int): Result<Unit> = runCatching {
        Validators.required(name, "Product name")?.let { fail(it) }
        if (rate <= 0 || rate > 60) fail("Interest rate must be between 0 and 60%")
        if (min <= 0 || max < min) fail("Maximum amount must be at least the minimum amount")
        if (months < 1 || months > 120) fail("Maximum duration must be 1-120 months")
        dao.insertProduct(SaccoProduct(name = name.trim(), description = description.trim(), interestRate = rate, minAmount = min, maxAmount = max, maxMonths = months))
    }

    suspend fun toggleProduct(p: SaccoProduct) = dao.updateProduct(p.copy(active = !p.active))

    // ------------------------------------------------------------------ demo data
    /** Inserts simulated data on first launch. No real people, IDs, accounts or money are used. */
    suspend fun seedIfEmpty() {
        if (dao.countUsers() > 0) return
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        fun user(no: String, name: String, id: String, phone: String, email: String, gender: String, dob: String, role: String, status: String, pw: String): User {
            val salt = PasswordHasher.newSalt()
            return User(memberNumber = no, fullName = name, nationalId = id, phone = phone, email = email, gender = gender, dateOfBirth = dob,
                address = "Nairobi (demo)", passwordHash = PasswordHasher.hash(pw, salt), salt = salt, role = role, status = status, createdAt = now - 200 * day)
        }
        dao.insertUser(user("ADMIN-001", "SACCO Administrator", "DEMO0001", "0700000001", "admin@sacco.demo", "Other", "1988-01-01", "ADMIN", "ACTIVE", "Admin@123"))
        val jane = dao.insertUser(user("SAC-0001", "Jane Wanjiru", "DEMO1001", "0712345678", "jane@sacco.demo", "Female", "1995-05-14", "MEMBER", "ACTIVE", "Member@123"))
        dao.insertUser(user("SAC-0002", "John Otieno", "DEMO1002", "0722345678", "john@sacco.demo", "Male", "1992-09-03", "MEMBER", "PENDING", "Member@123"))
        val emergency = dao.insertProduct(SaccoProduct(name = "Emergency Loan", description = "Quick short-term support", interestRate = 10.0, minAmount = 1000.0, maxAmount = 50000.0, maxMonths = 6))
        dao.insertProduct(SaccoProduct(name = "Development Loan", description = "Business and home improvement", interestRate = 12.0, minAmount = 5000.0, maxAmount = 500000.0, maxMonths = 36))
        dao.insertProduct(SaccoProduct(name = "School Fees Loan", description = "Education financing", interestRate = 9.0, minAmount = 5000.0, maxAmount = 200000.0, maxMonths = 12))

        listOf(5000.0, 5000.0, 6000.0, 6000.0, 7000.0, 7000.0).forEachIndexed { i, amt ->
            val at = now - (5 - i) * 30 * day
            dao.insertSaving(Saving(userId = jane, type = "DEPOSIT", amount = amt, note = "Monthly contribution", createdAt = at))
            record(jane, "DEPOSIT", amt, "Monthly contribution", at)
        }
        dao.insertSaving(Saving(userId = jane, type = "WITHDRAWAL", amount = 1000.0, note = "Savings withdrawal", createdAt = now - 70 * day))
        record(jane, "WITHDRAWAL", 1000.0, "Savings withdrawal", now - 70 * day)
        listOf(30, 30).forEachIndexed { i, n ->
            val at = now - (100 - i * 40) * day
            dao.insertShare(ShareTransaction(userId = jane, shares = n, unitValue = SHARE_VALUE, amount = n * SHARE_VALUE, createdAt = at))
            record(jane, "SHARE_CONTRIBUTION", n * SHARE_VALUE, "Purchase of $n shares", at)
        }
        val q = LoanCalculator.quote(20000.0, 10.0, 6, "Monthly")
        val loan = dao.insertLoan(Loan(loanNumber = "LN-00001", userId = jane, productId = emergency, loanType = "Emergency Loan", principal = 20000.0,
            interestRate = 10.0, interest = q.interest, totalRepayable = q.total, installment = q.installment, installments = q.installments,
            frequency = "Monthly", durationMonths = 6, purpose = "Medical bills", status = "ACTIVE", amountRepaid = q.installment * 2,
            appliedAt = now - 62 * day, issuedAt = now - 60 * day, adminNote = "Approved"))
        record(jane, "LOAN_DISBURSEMENT", 20000.0, "Loan LN-00001 disbursed", now - 60 * day)
        listOf(30, 0).forEach { ago ->
            dao.insertRepayment(LoanRepayment(loanId = loan, userId = jane, amount = q.installment, reference = "RPY-DEMO$ago", paidAt = now - ago * day))
            record(jane, "LOAN_REPAYMENT", q.installment, "Repayment for LN-00001", now - ago * day)
        }
        dao.insertNotifications(listOf(
            AppNotification(userId = jane, title = "Registration successful", message = "Welcome to SACCO Connect.", type = "ACCOUNT", createdAt = now - 200 * day, isRead = true),
            AppNotification(userId = jane, title = "Loan approved", message = "Your loan LN-00001 of ${money(20000.0)} was approved on ${fmtDate(now - 60 * day)}.", type = "LOAN", createdAt = now - 60 * day),
            AppNotification(userId = jane, title = "SACCO announcement", message = "Annual General Meeting is on the last Saturday of the month.", type = "ANNOUNCEMENT", createdAt = now - 2 * day)
        ))
    }
}
