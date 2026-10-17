package com.sacco.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val BAL = "COALESCE(SUM(CASE WHEN type='DEPOSIT' THEN amount ELSE -amount END),0.0)"

@Dao
interface SaccoDao {
    // ---- users ----
    @Insert suspend fun insertUser(u: User): Long
    @Update suspend fun updateUser(u: User)
    @Query("SELECT * FROM users WHERE id=:id") suspend fun getUser(id: Long): User?
    @Query("SELECT * FROM users WHERE id=:id") fun observeUser(id: Long): Flow<User?>
    @Query("SELECT * FROM users WHERE LOWER(email)=LOWER(:idf) OR phone=:idf OR LOWER(memberNumber)=LOWER(:idf) LIMIT 1")
    suspend fun findForLogin(idf: String): User?
    @Query("SELECT * FROM users WHERE LOWER(email)=LOWER(:email) LIMIT 1") suspend fun findByEmail(email: String): User?
    @Query("SELECT COUNT(*) FROM users WHERE LOWER(memberNumber)=LOWER(:no)") suspend fun countMemberNumber(no: String): Int
    @Query("SELECT COUNT(*) FROM users") suspend fun countUsers(): Int
    @Query("SELECT id FROM users WHERE role='MEMBER'") suspend fun memberIds(): List<Long>
    @Query("SELECT * FROM users WHERE role='MEMBER' AND (fullName LIKE '%'||:q||'%' OR memberNumber LIKE '%'||:q||'%' OR phone LIKE '%'||:q||'%') ORDER BY createdAt DESC")
    fun observeMembers(q: String): Flow<List<User>>

    // ---- savings ----
    @Insert suspend fun insertSaving(s: Saving): Long
    @Query("SELECT * FROM savings WHERE userId=:u ORDER BY createdAt DESC") fun observeSavings(u: Long): Flow<List<Saving>>
    @Query("SELECT $BAL FROM savings WHERE userId=:u") fun observeSavingsBalance(u: Long): Flow<Double>
    @Query("SELECT $BAL FROM savings WHERE userId=:u") suspend fun savingsBalance(u: Long): Double
    @Query("SELECT $BAL FROM savings") fun observeTotalSavings(): Flow<Double>
    @Query("SELECT * FROM savings ORDER BY createdAt DESC") fun observeAllSavings(): Flow<List<Saving>>

    // ---- products ----
    @Insert suspend fun insertProduct(p: SaccoProduct): Long
    @Update suspend fun updateProduct(p: SaccoProduct)
    @Query("SELECT * FROM products ORDER BY name") fun observeProducts(): Flow<List<SaccoProduct>>
    @Query("SELECT * FROM products WHERE active=1 ORDER BY name") fun observeActiveProducts(): Flow<List<SaccoProduct>>

    // ---- loans ----
    @Insert suspend fun insertLoan(l: Loan): Long
    @Update suspend fun updateLoan(l: Loan)
    @Query("SELECT * FROM loans WHERE id=:id") suspend fun getLoan(id: Long): Loan?
    @Query("SELECT * FROM loans WHERE id=:id") fun observeLoan(id: Long): Flow<Loan?>
    @Query("SELECT * FROM loans WHERE userId=:u ORDER BY appliedAt DESC") fun observeLoans(u: Long): Flow<List<Loan>>
    @Query("SELECT * FROM loans WHERE userId=:u") suspend fun loansOfNow(u: Long): List<Loan>
    @Query("SELECT * FROM loans ORDER BY appliedAt DESC") fun observeAllLoans(): Flow<List<Loan>>
    @Query("SELECT * FROM loans WHERE status='ACTIVE'") suspend fun activeLoansNow(): List<Loan>

    // ---- repayments ----
    @Insert suspend fun insertRepayment(r: LoanRepayment): Long
    @Query("SELECT * FROM loan_repayments WHERE loanId=:loanId ORDER BY paidAt DESC") fun observeRepayments(loanId: Long): Flow<List<LoanRepayment>>
    @Query("SELECT * FROM loan_repayments ORDER BY paidAt DESC") fun observeAllRepayments(): Flow<List<LoanRepayment>>

    // ---- transactions ----
    @Insert suspend fun insertTransaction(t: SaccoTransaction): Long
    @Query("SELECT * FROM transactions WHERE userId=:u ORDER BY createdAt DESC") fun observeTransactions(u: Long): Flow<List<SaccoTransaction>>
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC") fun observeAllTransactions(): Flow<List<SaccoTransaction>>

    // ---- shares ----
    @Insert suspend fun insertShare(s: ShareTransaction): Long
    @Query("SELECT * FROM shares WHERE userId=:u ORDER BY createdAt DESC") fun observeShares(u: Long): Flow<List<ShareTransaction>>
    @Query("SELECT COALESCE(SUM(shares),0) FROM shares WHERE userId=:u") fun observeShareCount(u: Long): Flow<Int>

    // ---- notifications ----
    @Insert suspend fun insertNotification(n: AppNotification): Long
    @Insert suspend fun insertNotifications(n: List<AppNotification>)
    @Query("SELECT * FROM notifications WHERE userId=:u ORDER BY createdAt DESC") fun observeNotifications(u: Long): Flow<List<AppNotification>>
    @Query("SELECT COUNT(*) FROM notifications WHERE userId=:u AND isRead=0") fun observeUnread(u: Long): Flow<Int>
    @Query("UPDATE notifications SET isRead=1 WHERE userId=:u") suspend fun markAllRead(u: Long)

    // ---- M-Pesa payments ----
    @Insert suspend fun insertPayment(p: MpesaPayment): Long
    @Update suspend fun updatePayment(p: MpesaPayment)
    @Query("SELECT * FROM mpesa_payments WHERE id=:id") suspend fun getPayment(id: Long): MpesaPayment?
    @Query("SELECT * FROM mpesa_payments WHERE id=:id") fun observePayment(id: Long): Flow<MpesaPayment?>
    @Query("SELECT * FROM mpesa_payments ORDER BY createdAt DESC") fun observeAllPayments(): Flow<List<MpesaPayment>>
}
