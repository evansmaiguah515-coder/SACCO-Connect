package com.sacco.app

import android.app.Application
import com.sacco.app.data.FirebaseAuthService
import com.sacco.app.data.MpesaClient
import com.sacco.app.data.SaccoDatabase
import com.sacco.app.data.SaccoRepository
import com.sacco.app.util.Notifier
import com.sacco.app.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaccoApp : Application() {
    lateinit var session: SessionManager
    lateinit var repo: SaccoRepository

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
        repo = SaccoRepository(SaccoDatabase.get(this), this, session, FirebaseAuthService(), MpesaClient())
        Notifier.createChannel(this)
        CoroutineScope(Dispatchers.IO).launch { repo.seedIfEmpty() }
    }
}
