package com.sacco.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stores only the signed-in user id and last-activity time. Sessions expire after 15 minutes idle. */
class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("sacco_session", Context.MODE_PRIVATE)
    private val _userId = MutableStateFlow(prefs.getLong("uid", -1L).takeIf { it > 0 })
    val userId: StateFlow<Long?> = _userId

    fun login(id: Long) {
        prefs.edit().putLong("uid", id).putLong("last", System.currentTimeMillis()).apply()
        _userId.value = id
    }

    fun logout() {
        prefs.edit().clear().apply()
        _userId.value = null
    }

    fun touch() {
        if (_userId.value != null) prefs.edit().putLong("last", System.currentTimeMillis()).apply()
    }

    fun expireIfIdle() {
        if (_userId.value == null) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0L) > TIMEOUT_MS) logout()
    }

    companion object { const val TIMEOUT_MS = 15 * 60 * 1000L }
}
