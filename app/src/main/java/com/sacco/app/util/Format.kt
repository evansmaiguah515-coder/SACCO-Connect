package com.sacco.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun money(v: Double): String = "KES " + String.format(Locale.US, "%,.2f", v)
fun fmtDate(ms: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
fun fmtTime(ms: Long): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ms))
fun monthKey(ms: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ms))
fun monthLabel(key: String): String {
    val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: return key
    return SimpleDateFormat("MMM", Locale.getDefault()).format(d)
}
fun typeLabel(t: String) = t.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
