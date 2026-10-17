package com.sacco.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sacco.app.data.SaccoTransaction
import com.sacco.app.ui.components.*
import com.sacco.app.ui.navigation.Routes
import com.sacco.app.util.*
import com.sacco.app.viewmodel.MemberViewModel

private val INFLOW = setOf("DEPOSIT", "LOAN_DISBURSEMENT")

@Composable
fun TransactionRow(t: SaccoTransaction) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(typeLabel(t.type), fontWeight = FontWeight.SemiBold)
                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${t.reference} | ${fmtDate(t.createdAt)} ${fmtTime(t.createdAt)}", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (t.type in INFLOW) "+ " else "- ") + money(t.amount), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                StatusChip(t.status)
            }
        }
    }
}

@Composable
fun HomeScreen(vm: MemberViewModel, go: (String) -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val balance by vm.savingsBalance.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    val limit by vm.availableLimit.collectAsStateWithLifecycle()
    val savings by vm.savings.collectAsStateWithLifecycle()
    val notifs by vm.notifications.collectAsStateWithLifecycle()
    val u = user; val l = loans; val t = txns; val b = balance; val s = savings; val n = notifs
    if (u == null || l == null || t == null || b == null || s == null || n == null) { LoadingBox(); return }

    val active = l.filter { it.status == "ACTIVE" }
    val loanBalance = active.sumOf { it.outstanding }
    val contributions = s.filter { it.type == "DEPOSIT" }.sumOf { it.amount }
    val next = active.firstOrNull()?.let { loan -> LoanCalculator.nextDue(loan)?.let { loan to it } }
    val repayStatus = when {
        next == null -> "No active loan"
        next.second.status == "OVERDUE" -> "OVERDUE: ${next.second.dueDate} (${money(next.second.amount)})"
        else -> "Up to date. Next: ${next.second.dueDate} (${money(next.second.amount)})"
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text("Welcome, ${u.fullName.substringBefore(' ')}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Member No: ${u.memberNumber}")
                    Spacer(Modifier.height(6.dp)); StatusChip(u.status)
                }
            }
        }
        if (u.status == "PENDING") item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null); Spacer(Modifier.width(10.dp))
                    Text("Your membership is awaiting approval. Loan applications and withdrawals unlock once the administrator approves you.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Savings balance", money(b), Icons.Filled.Savings, Modifier.weight(1f))
                StatCard("Loan balance", money(loanBalance), Icons.Filled.AccountBalanceWallet, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Available loan limit", money(limit ?: 0.0), Icons.Filled.TrendingUp, Modifier.weight(1f))
                StatCard("Total contributions", money(contributions), Icons.Filled.Payments, Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("Loan repayment status", style = MaterialTheme.typography.labelMedium)
                Text(repayStatus, fontWeight = FontWeight.SemiBold)
            } }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(Triple("Deposit", Icons.Filled.Add, Routes.SAVINGS), Triple("Apply loan", Icons.Filled.RequestQuote, Routes.APPLY),
                    Triple("Repay", Icons.Filled.Payments, Routes.LOANS), Triple("Shares", Icons.Filled.PieChart, Routes.SHARES),
                    Triple("History", Icons.Filled.SwapHoriz, Routes.TXNS))) { (label, icon, route) ->
                    AssistChip({ go(route) }, { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
                }
            }
        }
        item { SectionTitle("Recent transactions") }
        if (t.isEmpty()) item { EmptyState(Icons.Filled.SwapHoriz, "No transactions yet", "Make a deposit to get started.") }
        items(t.take(3), key = { it.id }) { TransactionRow(it) }
        item { SectionTitle("Notifications") }
        if (n.isEmpty()) item { EmptyState(Icons.Filled.Notifications, "All caught up", "You have no notifications.") }
        items(n.take(2), key = { it.id }) {
            Card(Modifier.fillMaxWidth().clickable { go(Routes.NOTIFS) }) { Column(Modifier.padding(14.dp)) {
                Text(it.title, fontWeight = FontWeight.SemiBold); Text(it.message, style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

@Composable
fun SavingsScreen(vm: MemberViewModel) {
    val list by vm.savings.collectAsStateWithLifecycle()
    val bal by vm.savingsBalance.collectAsStateWithLifecycle()
    val me by vm.user.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("All") }
    var dialog by remember { mutableStateOf<String?>(null) }
    val all = list; val balance = bal
    if (all == null || balance == null) { LoadingBox(); return }

    // Running balance (oldest to newest) for the statement view.
    val running = HashMap<Long, Double>()
    var acc = 0.0
    all.sortedBy { it.createdAt }.forEach { acc += if (it.type == "DEPOSIT") it.amount else -it.amount; running[it.id] = acc }
    val byMonth = all.groupBy { monthKey(it.createdAt) }.toSortedMap()
    val months = byMonth.keys.toList().takeLast(6)
    var cumulative = byMonth.filterKeys { it < (months.firstOrNull() ?: "") }.values.flatten().sumOf { if (it.type == "DEPOSIT") it.amount else -it.amount }
    val growth = months.map { m -> cumulative += byMonth[m]!!.sumOf { if (it.type == "DEPOSIT") it.amount else -it.amount }; cumulative }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text("Savings balance"); Text(money(balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ dialog = "deposit" }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Deposit (M-Pesa)") }
                        OutlinedButton({ dialog = "withdraw" }) { Text("Withdraw") }
                    }
                }
            }
        }
        if (growth.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("Savings growth (last ${months.size} months)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); SimpleLineChart(growth, months.map { monthLabel(it) })
            } }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Deposits", "Withdrawals", "Monthly", "Statement")) { f ->
                    FilterChip(filter == f, { filter = f }, { Text(f) })
                }
            }
        }
        if (all.isEmpty()) item { EmptyState(Icons.Filled.Savings, "No savings yet", "Make your first deposit.") }
        when (filter) {
            "Monthly" -> items(byMonth.keys.toList().reversed()) { m ->
                val dep = byMonth[m]!!.filter { it.type == "DEPOSIT" }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    LabeledRow(m, money(dep.sumOf { it.amount })); Text("${dep.size} deposit(s)", style = MaterialTheme.typography.labelSmall)
                } }
            }
            else -> items(all.filter { filter == "All" || filter == "Statement" || (filter == "Deposits") == (it.type == "DEPOSIT") }, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    LabeledRow(s.note, (if (s.type == "DEPOSIT") "+ " else "- ") + money(s.amount))
                    Text("${fmtDate(s.createdAt)} ${fmtTime(s.createdAt)} | ${typeLabel(s.type)}", style = MaterialTheme.typography.labelSmall)
                    if (filter == "Statement") Text("Balance: ${money(running[s.id] ?: 0.0)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                } }
            }
        }
    }
    when (dialog) {
        "deposit" -> MpesaPayDialog(vm, "DEPOSIT", null, "Deposit via M-Pesa", "", me?.phone.orEmpty()) { dialog = null }
        "withdraw" -> AmountDialog("Withdraw (simulated)", "Amount (KES)", onDismiss = { dialog = null }) { a, cb -> vm.withdraw(a, cb) }
    }
}
