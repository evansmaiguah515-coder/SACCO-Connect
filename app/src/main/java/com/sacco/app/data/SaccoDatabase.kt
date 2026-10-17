package com.sacco.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, Saving::class, SaccoProduct::class, Loan::class, LoanRepayment::class,
        SaccoTransaction::class, ShareTransaction::class, AppNotification::class, MpesaPayment::class],
    version = 2,
    exportSchema = false
)
abstract class SaccoDatabase : RoomDatabase() {
    abstract fun dao(): SaccoDao

    companion object {
        @Volatile private var instance: SaccoDatabase? = null
        fun get(context: Context): SaccoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SaccoDatabase::class.java, "sacco.db")
                .fallbackToDestructiveMigration()   // demo app: schema change from v1 wipes and re-seeds the demo data
                .build().also { instance = it }
        }
    }
}
