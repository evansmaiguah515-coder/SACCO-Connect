package com.sacco.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sacco.app.BuildConfig
import com.sacco.app.ui.components.FormField
import com.sacco.app.util.Validators
import com.sacco.app.util.money
import com.sacco.app.viewmodel.MemberViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * M-Pesa STK Push dialog used for savings deposits and loan repayments.
 * Step 1: enter phone and amount. Step 2: wait for the customer to enter their PIN. Step 3: show the receipt or the failure.
 */
@Composable
fun MpesaPayDialog(
    vm: MemberViewModel, purpose: String, loanId: Long?, title: String,
    defaultAmount: String, defaultPhone: String, onDismiss: () -> Unit
) {
    var phone by remember { mutableStateOf(defaultPhone) }
    var amount by remember { mutableStateOf(defaultAmount) }
    var error by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var paymentId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val payment by remember(paymentId) { paymentId?.let { vm.payment(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)
    val status = payment?.status
    val finished = paymentId != null && status != null && status != "PENDING"

    AlertDialog(
        onDismissRequest = { if (paymentId == null || finished) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (BuildConfig.MPESA_SIMULATED) "SIMULATED M-Pesa: no server, no real money" else "M-Pesa sandbox (Safaricom Daraja STK Push)",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                )
                if (paymentId == null) {
                    FormField(phone, { phone = it; error = null }, "M-Pesa phone number", null, KeyboardType.Phone)
                    FormField(amount, { amount = it; amountError = null }, "Amount (KES, whole shillings)", amountError, KeyboardType.Number)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } else when (status) {
                    "SUCCESS" -> {
                        Text("Payment confirmed", fontWeight = FontWeight.Bold)
                        Text("Amount: ${money(payment?.amount ?: 0.0)}")
                        Text("M-Pesa receipt: ${payment?.receipt ?: "-"}")
                    }
                    "FAILED", "CANCELLED" -> {
                        Text(if (status == "CANCELLED") "Payment cancelled" else "Payment failed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(payment?.message.orEmpty())
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Request sent. Check your phone and enter your M-Pesa PIN. Waiting for confirmation...")
                    }
                }
            }
        },
        confirmButton = {
            if (paymentId == null) {
                Button(enabled = !busy, onClick = {
                    val a = amount.trim().toDoubleOrNull()
                    amountError = if (a == null) "Enter a valid amount" else null
                    error = Validators.phone(phone)
                    if (a != null && error == null) {
                        busy = true
                        vm.startMpesa(purpose, loanId, phone, a) { r -> busy = false; r.onSuccess { paymentId = it }.onFailure { error = it.message } }
                    }
                }) { Text(if (busy) "Sending..." else "Pay with M-Pesa") }
            } else if (finished) Button(onDismiss) { Text("Done") }
        },
        dismissButton = { if (paymentId == null) TextButton(onDismiss) { Text("Cancel") } }
    )
}
