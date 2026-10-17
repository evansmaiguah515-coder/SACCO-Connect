package com.sacco.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sacco.app.SaccoApp
import com.sacco.app.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

typealias Callback<T> = (Result<T>) -> Unit

abstract class BaseViewModel(app: Application) : AndroidViewModel(app) {
    private val sacco = app as SaccoApp
    protected val repo: SaccoRepository get() = sacco.repo
    protected val session get() = sacco.session
    protected val uid: Long get() = session.userId.value ?: -1L

    /** Runs a repository call and delivers the Result on the main thread. */
    protected fun <T> launchResult(cb: Callback<T>, block: suspend () -> Result<T>) {
        viewModelScope.launch { cb(try { block() } catch (e: Exception) { Result.failure(e) }) }
    }

    /** Hot state that starts as null so screens can show a loading indicator. */
    protected fun <T> Flow<T>.asLoading(): StateFlow<T?> =
        map<T, T?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    protected fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)
}

class AuthViewModel(app: Application) : BaseViewModel(app) {
    val userId = session.userId

    /** Set when a Google user has no member profile yet; read by the "complete your profile" screen. */
    val pendingGoogle = MutableStateFlow<GoogleOutcome.NeedsProfile?>(null)

    /** Returns the saved role, or null when there is no valid session (also requires a live Firebase sign-in). */
    suspend fun sessionRole(): String? {
        val id = session.userId.value ?: return null
        if (!repo.firebaseSignedIn) { session.logout(); return null }
        return repo.dao.getUser(id)?.role
    }

    fun login(identifier: String, password: String, cb: Callback<User>) = launchResult(cb) {
        repo.login(identifier, password).onSuccess { session.login(it.id) }
    }

    fun googleSignIn(idToken: String, cb: Callback<GoogleOutcome>) = launchResult(cb) {
        repo.googleSignIn(idToken).onSuccess {
            when (it) {
                is GoogleOutcome.Existing -> session.login(it.user.id)
                is GoogleOutcome.NeedsProfile -> pendingGoogle.value = it
            }
        }
    }

    fun completeGoogleProfile(memberNumber: String, fullName: String, nationalId: String, phone: String, gender: String,
                              dob: String, address: String, cb: Callback<User>) = launchResult(cb) {
        val g = pendingGoogle.value ?: return@launchResult Result.failure(IllegalStateException("Google sign-in expired. Please try again."))
        repo.completeGoogleProfile(g.uid, g.email, memberNumber, fullName, nationalId, phone, gender, dob, address)
            .onSuccess { session.login(it.id) }
    }

    fun register(vararg f: String, cb: Callback<Unit>) = launchResult(cb) {
        repo.register(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9])
    }

    fun resetPassword(email: String, cb: Callback<Unit>) = launchResult(cb) { repo.resetPassword(email) }

    fun logout() { repo.signOutFirebase(); pendingGoogle.value = null; session.logout() }
    fun firebaseSignOut() = repo.signOutFirebase()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MemberViewModel(app: Application) : BaseViewModel(app) {
    private val id = session.userId.filterNotNull()
    private val dao get() = repo.dao

    val user = id.flatMapLatest { dao.observeUser(it) }.asLoading()
    val savings = id.flatMapLatest { dao.observeSavings(it) }.asLoading()
    val savingsBalance = id.flatMapLatest { dao.observeSavingsBalance(it) }.asLoading()
    val loans = id.flatMapLatest { dao.observeLoans(it) }.asLoading()
    val transactions = id.flatMapLatest { dao.observeTransactions(it) }.asLoading()
    val shares = id.flatMapLatest { dao.observeShares(it) }.asLoading()
    val shareCount = id.flatMapLatest { dao.observeShareCount(it) }.asLoading()
    val notifications = id.flatMapLatest { dao.observeNotifications(it) }.asLoading()
    val unread = id.flatMapLatest { dao.observeUnread(it) }.asState(0)
    val products = dao.observeActiveProducts().asLoading()

    /** Available limit = 3 x savings minus loans that are pending or active. */
    val availableLimit = combine(savingsBalance, loans) { bal, l ->
        if (bal == null || l == null) null
        else (bal * SaccoRepository.LIMIT_MULTIPLIER - l.filter { it.status == "PENDING" || it.status == "ACTIVE" }.sumOf { it.outstanding })
            .coerceAtLeast(0.0)
    }.asLoading()

    fun loan(loanId: Long) = dao.observeLoan(loanId)
    fun repayments(loanId: Long) = dao.observeRepayments(loanId)

    fun deposit(a: Double, cb: Callback<Unit>) = launchResult(cb) { repo.deposit(uid, a) }
    fun withdraw(a: Double, cb: Callback<Unit>) = launchResult(cb) { repo.withdraw(uid, a) }
    fun buyShares(n: Int, cb: Callback<Unit>) = launchResult(cb) { repo.buyShares(uid, n) }
    fun repay(loanId: Long, a: Double, cb: Callback<Unit>) = launchResult(cb) { repo.repayLoan(loanId, a) }
    fun applyLoan(p: SaccoProduct, amount: Double, months: Int, freq: String, purpose: String, cb: Callback<Long>) =
        launchResult(cb) { repo.applyLoan(uid, p, amount, months, freq, purpose) }
    fun payment(id: Long) = dao.observePayment(id)
    /** Starts an M-Pesa STK Push for a deposit or a loan repayment and tracks the result in the background. */
    fun startMpesa(purpose: String, loanId: Long?, phone: String, amount: Double, cb: Callback<Long>) = launchResult(cb) {
        repo.startMpesaPayment(uid, purpose, loanId, phone, amount).onSuccess { repo.trackPayment(it) }
    }
    fun markAllRead() { viewModelScope.launch { dao.markAllRead(uid) } }
    fun updateProfile(name: String, phone: String, email: String, address: String, cb: Callback<Unit>) =
        launchResult(cb) { repo.updateProfile(uid, name, phone, email, address) }
    fun changePassword(old: String, new: String, confirm: String, cb: Callback<Unit>) =
        launchResult(cb) { repo.changePassword(uid, old, new, confirm) }
    fun savePhoto(uri: Uri, cb: Callback<Unit>) = launchResult(cb) { repo.savePhoto(uid, uri) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(app: Application) : BaseViewModel(app) {
    private val dao get() = repo.dao
    val query = MutableStateFlow("")

    val members = query.flatMapLatest { dao.observeMembers(it) }.asLoading()
    private val allMembers = dao.observeMembers("").asState(emptyList())
    val usersById = allMembers.map { list -> list.associateBy { it.id } }.asState(emptyMap())
    val loans = dao.observeAllLoans().asLoading()
    val repayments = dao.observeAllRepayments().asLoading()
    val transactions = dao.observeAllTransactions().asLoading()
    val allSavings = dao.observeAllSavings().asLoading()
    val totalSavings = dao.observeTotalSavings().asState(0.0)
    val products = dao.observeProducts().asLoading()
    val mpesaPayments = dao.observeAllPayments().asLoading()
    val allMembersList: StateFlow<List<User>> = allMembers

    fun member(id: Long) = dao.observeUser(id)
    fun memberSavings(id: Long) = dao.observeSavingsBalance(id)
    fun memberLoans(id: Long) = dao.observeLoans(id)
    fun memberShares(id: Long) = dao.observeShareCount(id)

    fun setMemberStatus(id: Long, status: String, cb: Callback<Unit>) = launchResult(cb) { repo.setMemberStatus(id, status) }
    fun approveLoan(id: Long, cb: Callback<Unit>) = launchResult(cb) { repo.approveLoan(id) }
    fun rejectLoan(id: Long, reason: String, cb: Callback<Unit>) = launchResult(cb) { repo.rejectLoan(id, reason) }
    fun announce(title: String, msg: String, cb: Callback<Int>) = launchResult(cb) { repo.postAnnouncement(title, msg) }
    fun reminders(cb: Callback<Int>) = launchResult(cb) { repo.sendRepaymentReminders() }
    fun addProduct(n: String, d: String, r: Double, min: Double, max: Double, m: Int, cb: Callback<Unit>) =
        launchResult(cb) { repo.addProduct(n, d, r, min, max, m) }
    fun toggleProduct(p: SaccoProduct) { viewModelScope.launch { repo.toggleProduct(p) } }
}
