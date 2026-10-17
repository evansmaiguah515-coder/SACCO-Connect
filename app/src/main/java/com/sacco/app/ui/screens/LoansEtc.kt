package com.sacco.app.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sacco.app.data.Loan
import com.sacco.app.data.SaccoRepository
import com.sacco.app.data.SaccoTransaction
import com.sacco.app.ui.components.*
import com.sacco.app.util.*
import com.sacco.app.viewmodel.MemberViewModel

@Composable
fun LoanCard(l: Loan, onClick: (() -> Unit)? = null, owner: String? = null) {
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text(l.loanNumber, fontWeight = FontWeight.Bold); Text(l.loanType, style = MaterialTheme.typography.bodySmall) }
                StatusChip(l.status)
            }
            Spacer(Modifier.height(8.dp))
            owner?.let { LabeledRow("Member", it) }
            LabeledRow("Amount borrowed", money(l.principal))
            LabeledRow("Interest", money(l.interest))
            LabeledRow("Date issued", l.issuedAt?.let { fmtDate(it) } ?: "Not yet issued")
            LabeledRow("Repayment period", "${l.durationMonths} months")
            LabeledRow("Amount repaid", money(l.amountRepaid))
            LabeledRow("Outstanding balance", money(if (l.status == "ACTIVE" || l.status == "FULLY_PAID") l.outstanding else 0.0))
        }
    }
}

@Composable
fun LoansScreen(vm: MemberViewModel, onApply: () -> Unit, onOpen: (Long) -> Unit) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val list = loans
    if (list == null) { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onApply, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.RequestQuote, null); Spacer(Modifier.width(8.dp)); Text("Apply for a loan") } }
        if (list.isEmpty()) item { EmptyState(Icons.Filled.AccountBalanceWallet, "No loans yet", "Apply for a loan and track it here.") }
        items(list, key = { it.id }) { LoanCard(it, { onOpen(it.id) }) }
    }
}

@Composable
fun LoanApplyScreen(vm: MemberViewModel, onDone: () -> Unit) {
    val products by vm.products.collectAsStateWithLifecycle()
    val limit by vm.availableLimit.collectAsStateWithLifecycle()
    val list = products
    if (list == null) { LoadingBox(); return }
    var productName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var months by remember { mutableStateOf(6) }
    var frequency by remember { mutableStateOf("Monthly") }
    var purpose by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formErr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val product = list.firstOrNull { it.name == productName }
    val durations = listOf(3, 6, 12, 18, 24, 36).filter { product == null || it <= product.maxMonths }
    val amount = amountText.toDoubleOrNull()
    val quote = if (product != null && amount != null && amount > 0) LoanCalculator.quote(amount, product.interestRate, months.coerceAtMost(product.maxMonths), frequency) else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text("Available loan limit: ${money(limit ?: 0.0)}", Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
        }
        if (list.isEmpty()) EmptyState(Icons.Filled.Info, "No loan products", "The administrator has not published any loan products.")
        DropdownField("Loan type", list.map { it.name }, productName, { productName = it; errors = errors - "product"; if (months > (list.first { p -> p.name == it }.maxMonths)) months = 6 }, errors["product"])
        product?.let { Text("${it.description}. ${it.interestRate}% p.a., KES ${"%,.0f".format(it.minAmount)} to ${"%,.0f".format(it.maxAmount)}, up to ${it.maxMonths} months.", style = MaterialTheme.typography.bodySmall) }
        FormField(amountText, { amountText = it; errors = errors - "amount" }, "Requested amount (KES)", errors["amount"], KeyboardType.Decimal)
        DropdownField("Loan duration", durations.map { "$it months" }, "$months months", { months = it.substringBefore(' ').toInt() })
        DropdownField("Repayment frequency", listOf("Weekly", "Monthly"), frequency, { frequency = it })
        FormField(purpose, { purpose = it; errors = errors - "purpose" }, "Purpose of the loan", errors["purpose"], singleLine = false)
        quote?.let { q ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("Loan summary", fontWeight = FontWeight.Bold)
                LabeledRow("Principal amount", money(q.principal)); LabeledRow("Interest", money(q.interest))
                LabeledRow("Total repayment", money(q.total)); LabeledRow("Estimated installment", money(q.installment))
                LabeledRow("Repayment period", q.periodLabel)
            } }
        }
        formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), onClick = {
            val e = linkedMapOf<String, String>()
            if (product == null) e["product"] = "Choose a loan type"
            else if (amount == null) e["amount"] = "Enter a valid amount"
            else {
                Validators.amount(amount, product.minAmount, product.maxAmount)?.let { e["amount"] = it }
                if (!e.containsKey("amount") && amount > (limit ?: 0.0)) e["amount"] = "Exceeds your available limit of ${money(limit ?: 0.0)}"
            }
            Validators.required(purpose, "Purpose")?.let { e["purpose"] = it }
            errors = e
            if (e.isEmpty() && product != null && amount != null) {
                busy = true; formErr = null
                vm.applyLoan(product, amount, months, frequency, purpose) { r -> busy = false; r.onSuccess { submitted = true }.onFailure { formErr = it.message } }
            }
        }) { Text("Submit application") }
    }
    if (submitted) AlertDialog(onDismissRequest = {}, title = { Text("Application submitted") },
        text = { Text("Your loan application is pending review by the administrator. You will be notified of the decision.") },
        confirmButton = { Button(onDone) { Text("View my loans") } })
}

@Composable
fun LoanDetailScreen(vm: MemberViewModel, loanId: Long) {
    val loan by remember(loanId) { vm.loan(loanId) }.collectAsStateWithLifecycle(null)
    val repayments by remember(loanId) { vm.repayments(loanId) }.collectAsStateWithLifecycle(emptyList())
    var pay by remember { mutableStateOf(false) }
    val me by vm.user.collectAsStateWithLifecycle()
    val l = loan
    if (l == null) { LoadingBox(); return }
    val schedule = if (l.status == "ACTIVE" || l.status == "FULLY_PAID") LoanCalculator.schedule(l) else emptyList()
    val next = LoanCalculator.nextDue(l).takeIf { l.status == "ACTIVE" }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LoanCard(l) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("Repayment", fontWeight = FontWeight.Bold)
                LabeledRow("Installment amount", money(l.installment)); LabeledRow("Frequency", l.frequency)
                LabeledRow("Total repayable", money(l.totalRepayable))
                LabeledRow("Next repayment date", next?.dueDate?.toString() ?: "None")
                if (l.status == "REJECTED") Text("Reason: ${l.adminNote}", color = MaterialTheme.colorScheme.error)
                if (l.status == "PENDING") Text("Awaiting administrator review.")
                if (l.status == "ACTIVE") { Spacer(Modifier.height(8.dp)); Button({ pay = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Payments, null); Spacer(Modifier.width(8.dp)); Text("Pay with M-Pesa") } }
            } }
        }
        if (schedule.isNotEmpty()) {
            item { SectionTitle("Repayment schedule") }
            items(schedule) { s ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("#${s.number}  ${s.dueDate}", fontWeight = FontWeight.Medium); Text(money(s.amount), style = MaterialTheme.typography.bodySmall) }
                    StatusChip(s.status)
                } }
            }
        }
        item { SectionTitle("Repayment history") }
        if (repayments.isEmpty()) item { EmptyState(Icons.Filled.Payments, "No repayments yet", "Payments you make will appear here.") }
        items(repayments, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                LabeledRow(r.reference, money(r.amount)); Text("${fmtDate(r.paidAt)} ${fmtTime(r.paidAt)}", style = MaterialTheme.typography.labelSmall)
            } }
        }
    }
    if (pay) MpesaPayDialog(vm, "LOAN_REPAYMENT", loanId, "Repay via M-Pesa",
        Math.ceil(minOf(l.installment, l.outstanding)).toInt().toString(), me?.phone.orEmpty()) { pay = false }
}

private val TYPES = listOf("All" to "", "Deposit" to "DEPOSIT", "Withdrawal" to "WITHDRAWAL", "Loan disbursement" to "LOAN_DISBURSEMENT",
    "Loan repayment" to "LOAN_REPAYMENT", "Share contribution" to "SHARE_CONTRIBUTION", "Transfer" to "TRANSFER")

/** Transaction history with search + type filters. Also reused by the administrator (pass [nameOf] to show members). */
@Composable
fun TransactionsScreen(list: List<SaccoTransaction>?, nameOf: ((Long) -> String)? = null) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    if (list == null) { LoadingBox(); return }
    val shown = list.filter { (type.isEmpty() || it.type == type) &&
        (query.isBlank() || it.description.contains(query, true) || it.reference.contains(query, true) || typeLabel(it.type).contains(query, true) || (nameOf?.invoke(it.userId)?.contains(query, true) ?: false)) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search transactions") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(TYPES) { (label, code) -> FilterChip(type == code, { type = code }, { Text(label) }) } } }
        if (shown.isEmpty()) item { EmptyState(Icons.Filled.SwapHoriz, "No transactions found", "Try another search or filter.") }
        items(shown, key = { it.id }) { t ->
            Column {
                if (nameOf != null) Text(nameOf(t.userId), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                TransactionRow(t)
            }
        }
    }
}

@Composable
fun SharesScreen(vm: MemberViewModel) {
    val count by vm.shareCount.collectAsStateWithLifecycle()
    val history by vm.shares.collectAsStateWithLifecycle()
    var buy by remember { mutableStateOf(false) }
    val c = count; val h = history
    if (c == null || h == null) { LoadingBox(); return }
    val met = c >= SaccoRepository.MIN_SHARES
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text("Total shares"); Text("$c", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    LabeledRow("Share value", money(SaccoRepository.SHARE_VALUE)); LabeledRow("Total value", money(c * SaccoRepository.SHARE_VALUE))
                    LabeledRow("Minimum required shares", "${SaccoRepository.MIN_SHARES}")
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { (c.toFloat() / SaccoRepository.MIN_SHARES).coerceAtMost(1f) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (met) Icons.Filled.CheckCircle else Icons.Filled.Warning, null); Spacer(Modifier.width(6.dp))
                        Text(if (met) "Share status: minimum met" else "Share status: ${SaccoRepository.MIN_SHARES - c} more shares needed", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp)); Button({ buy = true }) { Text("Buy shares (simulated)") }
                }
            }
        }
        item { SectionTitle("Share contribution history") }
        if (h.isEmpty()) item { EmptyState(Icons.Filled.PieChart, "No shares yet", "Buy shares to build your stake.") }
        items(h, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                LabeledRow("${s.shares} shares", money(s.amount)); Text("${fmtDate(s.createdAt)} ${fmtTime(s.createdAt)}", style = MaterialTheme.typography.labelSmall)
            } }
        }
    }
    if (buy) AmountDialog("Buy shares (simulated)", "Number of shares", onDismiss = { buy = false }) { a, cb ->
        if (a != a.toInt().toDouble()) cb(Result.failure(IllegalArgumentException("Enter a whole number of shares"))) else vm.buyShares(a.toInt(), cb)
    }
}

@Composable
fun NotificationsScreen(vm: MemberViewModel) {
    val items by vm.notifications.collectAsStateWithLifecycle()
    val list = items
    if (list == null) { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedButton({ vm.markAllRead() }, enabled = list.any { !it.isRead }) { Icon(Icons.Filled.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("Mark all as read") } }
        if (list.isEmpty()) item { EmptyState(Icons.Filled.Notifications, "No notifications", "Alerts about deposits, loans and announcements appear here.") }
        items(list, key = { it.id }) { n ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(when (n.type) { "LOAN" -> Icons.Filled.AccountBalanceWallet; "TRANSACTION" -> Icons.Filled.SwapHoriz; "ANNOUNCEMENT" -> Icons.Filled.Campaign; "REMINDER" -> Icons.Filled.Schedule; else -> Icons.Filled.Info }, null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold)
                        if (!n.isRead) { Spacer(Modifier.width(6.dp)); Text("NEW", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                    }
                    Text(n.message, style = MaterialTheme.typography.bodySmall)
                    Text("${fmtDate(n.createdAt)} ${fmtTime(n.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            } }
        }
    }
}

@Composable
fun ProfileScreen(vm: MemberViewModel, onLogout: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val u = user
    var editing by remember { mutableStateOf(false) }
    var changingPw by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.savePhoto(it) { r -> message = r.exceptionOrNull()?.message ?: "Profile photo updated" } }
    }
    if (u == null) { LoadingBox(); return }
    val bitmap = remember(u.photoPath) { u.photoPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (bitmap != null) Image(bitmap, "Profile photo", Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        else Surface(Modifier.size(104.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Filled.Person, null, Modifier.padding(24.dp)) }
        Text(u.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        StatusChip(u.status)
        OutlinedButton({ picker.launch("image/*") }) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Change photo") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Personal information", fontWeight = FontWeight.Bold)
            LabeledRow("Phone", u.phone); LabeledRow("Email", u.email); LabeledRow("Gender", u.gender)
            LabeledRow("Date of birth", u.dateOfBirth); LabeledRow("Address", u.address)
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Membership details", fontWeight = FontWeight.Bold)
            LabeledRow("Member number", u.memberNumber); LabeledRow("National ID", u.nationalId.take(2) + "******")
            LabeledRow("Member since", fmtDate(u.createdAt)); LabeledRow("Account status", typeLabel(u.status))
        } }
        Button({ editing = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit profile") }
        OutlinedButton({ changingPw = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(6.dp)); Text("Change password") }
        OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(6.dp)); Text("Logout") }
    }

    if (editing) {
        var name by remember { mutableStateOf(u.fullName) }; var phone by remember { mutableStateOf(u.phone) }
        var email by remember { mutableStateOf(u.email) }; var address by remember { mutableStateOf(u.address) }
        var err by remember { mutableStateOf<Map<String, String>>(emptyMap()) }; var formErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Edit profile") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                FormField(name, { name = it }, "Full name", err["n"]); FormField(phone, { phone = it }, "Phone", err["p"], KeyboardType.Phone)
                FormField(email, { email = it }, "Email", err["e"], KeyboardType.Email); FormField(address, { address = it }, "Address", err["a"])
                formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button({
                err = listOfNotNull(Validators.fullName(name)?.let { "n" to it }, Validators.phone(phone)?.let { "p" to it },
                    Validators.email(email)?.let { "e" to it }, Validators.required(address, "Address")?.let { "a" to it }).toMap()
                if (err.isEmpty()) vm.updateProfile(name, phone, email, address) { r -> r.onSuccess { editing = false; message = "Profile updated" }.onFailure { formErr = it.message } }
            }) { Text("Save") } },
            dismissButton = { TextButton({ editing = false }) { Text("Cancel") } })
    }
    if (changingPw) {
        var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var conf by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<Map<String, String>>(emptyMap()) }; var formErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { changingPw = false }, title = { Text("Change password") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                FormField(old, { old = it }, "Current password", err["o"], password = true); FormField(new, { new = it }, "New password", err["n"], password = true)
                FormField(conf, { conf = it }, "Confirm new password", err["c"], password = true)
                formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button({
                err = listOfNotNull(Validators.required(old, "Current password")?.let { "o" to it }, Validators.password(new)?.let { "n" to it }, Validators.confirm(new, conf)?.let { "c" to it }).toMap()
                if (err.isEmpty()) vm.changePassword(old, new, conf) { r -> r.onSuccess { changingPw = false; message = "Password changed" }.onFailure { formErr = it.message } }
            }) { Text("Update") } },
            dismissButton = { TextButton({ changingPw = false }) { Text("Cancel") } })
    }
}
