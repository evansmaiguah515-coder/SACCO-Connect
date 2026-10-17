package com.sacco.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.max

/** Login account + member profile. Members and administrators are distinguished by [role]. */
@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true), Index(value = ["memberNumber"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberNumber: String,
    val fullName: String,
    val nationalId: String,
    val phone: String,
    val email: String,
    val gender: String,
    val dateOfBirth: String,
    val address: String,
    val passwordHash: String,
    val salt: String,
    val role: String = "MEMBER",      // MEMBER | ADMIN
    val status: String = "PENDING",   // PENDING | ACTIVE | REJECTED
    val photoPath: String? = null,
    val firebaseUid: String? = null,   // links this profile to its Firebase Authentication account
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "savings",
    foreignKeys = [ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class Saving(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val type: String,                 // DEPOSIT | WITHDRAWAL
    val amount: Double,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class SaccoProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val interestRate: Double,         // annual %, flat
    val minAmount: Double,
    val maxAmount: Double,
    val maxMonths: Int,
    val active: Boolean = true
)

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SaccoProduct::class, ["id"], ["productId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("userId"), Index("productId")]
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanNumber: String,
    val userId: Long,
    val productId: Long?,
    val loanType: String,
    val principal: Double,
    val interestRate: Double,
    val interest: Double,
    val totalRepayable: Double,
    val installment: Double,
    val installments: Int,
    val frequency: String,            // Weekly | Monthly
    val durationMonths: Int,
    val purpose: String,
    val status: String,               // PENDING | ACTIVE | REJECTED | FULLY_PAID
    val amountRepaid: Double = 0.0,
    val adminNote: String = "",
    val appliedAt: Long = System.currentTimeMillis(),
    val issuedAt: Long? = null
) {
    val outstanding: Double get() = max(0.0, totalRepayable - amountRepaid)
}

@Entity(
    tableName = "loan_repayments",
    foreignKeys = [
        ForeignKey(Loan::class, ["id"], ["loanId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("loanId"), Index("userId")]
)
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val userId: Long,
    val amount: Double,
    val reference: String,
    val paidAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class SaccoTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reference: String,
    val userId: Long,
    val type: String,   // DEPOSIT | WITHDRAWAL | LOAN_DISBURSEMENT | LOAN_REPAYMENT | SHARE_CONTRIBUTION | TRANSFER
    val amount: Double,
    val description: String,
    val status: String = "SUCCESS",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "shares",
    foreignKeys = [ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class ShareTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val shares: Int,
    val unitValue: Double,
    val amount: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notifications",
    foreignKeys = [ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val message: String,
    val type: String,   // ACCOUNT | TRANSACTION | LOAN | ANNOUNCEMENT | REMINDER
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** One M-Pesa STK Push request (deposit or loan repayment) and its outcome. */
@Entity(
    tableName = "mpesa_payments",
    foreignKeys = [ForeignKey(User::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class MpesaPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val purpose: String,              // DEPOSIT | LOAN_REPAYMENT
    val loanId: Long?,
    val phone: String,                // 2547XXXXXXXX
    val amount: Double,
    val checkoutRequestId: String? = null,
    val status: String = "PENDING",   // PENDING | SUCCESS | FAILED | CANCELLED
    val receipt: String? = null,
    val message: String = "",
    val simulated: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
