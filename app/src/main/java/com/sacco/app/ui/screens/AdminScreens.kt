package com.sacco.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sacco.app.data.User
import com.sacco.app.ui.components.*
import com.sacco.app.ui.navigation.Routes
import com.sacco.app.util.*
import com.sacco.app.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShell(onLogout: () -> Unit, vm: AdminViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var confirmLogout by remember { mutableStateOf(false) }
    val tabs = listOf(Tab(Routes.A_DASH, "Dashboard", Icons.Filled.Dashboard), Tab(Routes.A_MEMBERS, "Members", Icons.Filled.People),
        Tab(Routes.A_LOANS, "Loans", Icons.Filled.AccountBalanceWallet), Tab(Routes.A_REPORTS, "Reports", Icons.Filled.Assessment))
    val titles = mapOf(Routes.A_DASH to "Admin dashboard", Routes.A_MEMBERS to "Members", Routes.A_MEMBER to "Member account", Routes.A_LOANS to "Loan applications",
        Routes.A_REPORTS to "Reports and analytics", Routes.A_TXNS to "Monitor transactions", Routes.A_REPAY to "Loan repayments",
        Routes.A_PRODUCTS to "SACCO products", Routes.A_MPESA to "M-Pesa payments", Routes.A_ANNOUNCE to "Post announcement")
    val users by vm.usersById.collectAsStateWithLifecycle()
    val nameOf: (Long) -> String = { users[it]?.fullName ?: "Member #$it" }
    fun go(r: String) {
        if (tabs.any { it.route == r }) nav.navigate(r) { popUpTo(Routes.A_DASH) { saveState = true }; launchSingleTop = true; restoreState = true }
        else nav.navigate(r) { launchSingleTop = true }
    }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(titles[route] ?: "Admin") },
            navigationIcon = { if (route != null && tabs.none { it.route == route }) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton({ confirmLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Logout") } }) },
        bottomBar = { if (tabs.any { it.route == route }) NavigationBar {
            tabs.forEach { t -> NavigationBarItem(route == t.route, { go(t.route) }, { Icon(t.icon, null) }, label = { Text(t.label) }) } } }
    ) { pad ->
        NavHost(nav, Routes.A_DASH, Modifier.padding(pad)) {
            composable(Routes.A_DASH) { AdminDashboard(vm, ::go) }
            composable(Routes.A_MEMBERS) { MembersScreen(vm) { nav.navigate("a_member/$it") } }
            composable(Routes.A_MEMBER, arguments = listOf(navArgument("id") { type = NavType.LongType })) { MemberAccountScreen(vm, it.arguments!!.getLong("id")) }
            composable(Routes.A_LOANS) { AdminLoansScreen(vm, nameOf) }
            composable(Routes.A_REPORTS) { ReportsScreen(vm) }
            composable(Routes.A_TXNS) { TransactionsScreen(vm.transactions.collectAsStateWithLifecycle().value, nameOf) }
            composable(Routes.A_REPAY) { AdminRepaymentsScreen(vm, nameOf) }
            composable(Routes.A_PRODUCTS) { ProductsScreen(vm) }
            composable(Routes.A_MPESA) { AdminMpesaScreen(vm, nameOf) }
            composable(Routes.A_ANNOUNCE) { AnnounceScreen(vm) }
        }
    }
    if (confirmLogout) AlertDialog({ confirmLogout = false }, title = { Text("Log out?") }, text = { Text("You will need to sign in again.") },
        confirmButton = { Button({ confirmLogout = false; onLogout() }) { Text("Logout") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } })
}

@Composable
fun AdminDashboard(vm: AdminViewModel, go: (String) -> Unit) {
    val members by vm.allMembersList.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val savings by vm.totalSavings.collectAsStateWithLifecycle()
    val l = loans
    if (l == null) { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Members", "${members.size}", Icons.Filled.People, Modifier.weight(1f))
            StatCard("Awaiting approval", "${members.count { it.status == "PENDING" }}", Icons.Filled.Schedule, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Pending loans", "${l.count { it.status == "PENDING" }}", Icons.Filled.RequestQuote, Modifier.weight(1f))
            StatCard("Active loans", "${l.count { it.status == "ACTIVE" }}", Icons.Filled.AccountBalanceWallet, Modifier.weight(1f)) } }
        item { StatCard("Total savings", money(savings), Icons.Filled.Savings, Modifier.fillMaxWidth()) }
        item { SectionTitle("Manage") }
        items(listOf(Triple("Monitor transactions", Icons.Filled.SwapHoriz, Routes.A_TXNS), Triple("Loan repayments", Icons.Filled.Payments, Routes.A_REPAY), Triple("M-Pesa payments", Icons.Filled.PhoneAndroid, Routes.A_MPESA),
            Triple("SACCO products", Icons.Filled.Inventory2, Routes.A_PRODUCTS), Triple("Post announcement", Icons.Filled.Campaign, Routes.A_ANNOUNCE))) { (label, icon, route) ->
            Card(Modifier.fillMaxWidth().clickable { go(route) }) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Text(label, fontWeight = FontWeight.Medium) } }
        }
    }
}

@Composable
fun MembersScreen(vm: AdminViewModel, onOpen: (Long) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    var msg by remember { mutableStateOf<String?>(null) }
    val list = members
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, { vm.query.value = it }, Modifier.fillMaxWidth(), label = { Text("Search by name, number or phone") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true) }
        msg?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (list == null) item { LoadingBox() }
        else if (list.isEmpty()) item { EmptyState(Icons.Filled.People, "No members found", "Try a different search.") }
        items(list ?: emptyList<User>(), key = { it.id }) { u ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(u.id) }) { Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(u.fullName, fontWeight = FontWeight.Bold); Text("${u.memberNumber} | ${u.phone}", style = MaterialTheme.typography.bodySmall) }
                    StatusChip(u.status)
                }
                if (u.status == "PENDING") Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button({ vm.setMemberStatus(u.id, "ACTIVE") { r -> msg = r.exceptionOrNull()?.message } }) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(4.dp)); Text("Approve") }
                    OutlinedButton({ vm.setMemberStatus(u.id, "REJECTED") { r -> msg = r.exceptionOrNull()?.message } }) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(4.dp)); Text("Reject") }
                }
            } }
        }
    }
}

@Composable
fun MemberAccountScreen(vm: AdminViewModel, id: Long) {
    val user by remember(id) { vm.member(id) }.collectAsStateWithLifecycle(null)
    val savings by remember(id) { vm.memberSavings(id) }.collectAsStateWithLifecycle(0.0)
    val shares by remember(id) { vm.memberShares(id) }.collectAsStateWithLifecycle(0)
    val loans by remember(id) { vm.memberLoans(id) }.collectAsStateWithLifecycle(emptyList())
    val u = user
    if (u == null) { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text(u.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); StatusChip(u.status)
            Spacer(Modifier.height(8.dp))
            LabeledRow("Member number", u.memberNumber); LabeledRow("Phone", u.phone); LabeledRow("Email", u.email)
            LabeledRow("Joined", fmtDate(u.createdAt)); LabeledRow("Savings balance", money(savings))
            LabeledRow("Shares", "$shares (${money(shares * 100.0)})"); LabeledRow("Loans", "${loans.size}")
        } } }
        item { SectionTitle("Loans") }
        if (loans.isEmpty()) item { EmptyState(Icons.Filled.AccountBalanceWallet, "No loans", "This member has not borrowed yet.") }
        items(loans, key = { it.id }) { LoanCard(it) }
    }
}

@Composable
fun AdminLoansScreen(vm: AdminViewModel, nameOf: (Long) -> String) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("ALL") }
    var rejecting by remember { mutableStateOf<Long?>(null) }
    var reason by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val all = loans
    if (all == null) { LoadingBox(); return }
    val shown = all.filter { filter == "ALL" || it.status == filter }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("ALL", "PENDING", "ACTIVE", "FULLY_PAID", "REJECTED")) { f -> FilterChip(filter == f, { filter = f }, { Text(if (f == "ALL") "All" else typeLabel(f)) }) } } }
        msg?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (shown.isEmpty()) item { EmptyState(Icons.Filled.AccountBalanceWallet, "No loan applications", "Nothing matches this filter.") }
        items(shown, key = { it.id }) { l ->
            Column {
                LoanCard(l, owner = nameOf(l.userId))
                Text("Purpose: ${l.purpose}", Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                if (l.status == "PENDING") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.approveLoan(l.id) { r -> msg = r.exceptionOrNull()?.message } }) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(4.dp)); Text("Approve") }
                    OutlinedButton({ rejecting = l.id; reason = "" }) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(4.dp)); Text("Reject") }
                }
            }
        }
    }
    rejecting?.let { id ->
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog({ rejecting = null }, title = { Text("Reject application") },
            text = { FormField(reason, { reason = it; err = null }, "Reason", err) },
            confirmButton = { Button({ vm.rejectLoan(id, reason) { r -> r.onSuccess { rejecting = null }.onFailure { err = it.message } } }) { Text("Reject") } },
            dismissButton = { TextButton({ rejecting = null }) { Text("Cancel") } })
    }
}

@Composable
fun AdminRepaymentsScreen(vm: AdminViewModel, nameOf: (Long) -> String) {
    val repayments by vm.repayments.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val r = repayments
    if (r == null) { LoadingBox(); return }
    val loanNo = (loans ?: emptyList()).associate { it.id to it.loanNumber }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (r.isEmpty()) item { EmptyState(Icons.Filled.Payments, "No repayments", "Repayments will be listed here.") }
        items(r, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                LabeledRow(nameOf(p.userId), money(p.amount)); LabeledRow("Loan", loanNo[p.loanId] ?: "-")
                Text("${p.reference} | ${fmtDate(p.paidAt)} ${fmtTime(p.paidAt)}", style = MaterialTheme.typography.labelSmall)
            } }
        }
    }
}

@Composable
fun ProductsScreen(vm: AdminViewModel) {
    val products by vm.products.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val list = products
    if (list == null) { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button({ adding = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add product") } }
        if (list.isEmpty()) item { EmptyState(Icons.Filled.Inventory2, "No products", "Add a loan product.") }
        items(list, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold); Text(p.description, style = MaterialTheme.typography.bodySmall)
                    Text("${p.interestRate}% p.a. | ${money(p.minAmount)} - ${money(p.maxAmount)} | max ${p.maxMonths} months", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Switch(p.active, { vm.toggleProduct(p) }); Text(if (p.active) "Active" else "Off", style = MaterialTheme.typography.labelSmall) }
            } }
        }
    }
    if (adding) {
        var f by remember { mutableStateOf(listOf("", "", "", "", "", "")) }
        var err by remember { mutableStateOf<String?>(null) }
        fun set(i: Int, v: String) { f = f.toMutableList().also { it[i] = v }; err = null }
        AlertDialog({ adding = false }, title = { Text("New product") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                FormField(f[0], { set(0, it) }, "Name"); FormField(f[1], { set(1, it) }, "Description")
                FormField(f[2], { set(2, it) }, "Interest rate % p.a.", keyboard = KeyboardType.Decimal)
                FormField(f[3], { set(3, it) }, "Minimum amount", keyboard = KeyboardType.Decimal)
                FormField(f[4], { set(4, it) }, "Maximum amount", keyboard = KeyboardType.Decimal)
                FormField(f[5], { set(5, it) }, "Maximum months", keyboard = KeyboardType.Number)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button({
                val rate = f[2].toDoubleOrNull(); val min = f[3].toDoubleOrNull(); val max = f[4].toDoubleOrNull(); val m = f[5].toIntOrNull()
                if (rate == null || min == null || max == null || m == null) err = "Enter valid numbers for rate, amounts and months"
                else vm.addProduct(f[0], f[1], rate, min, max, m) { r -> r.onSuccess { adding = false }.onFailure { err = it.message } }
            }) { Text("Save") } },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } })
    }
}

@Composable
fun AnnounceScreen(vm: AdminViewModel) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(title, { title = it }, "Title"); FormField(message, { message = it }, "Message", singleLine = false)
        result?.let { Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        Button({ vm.announce(title, message) { r ->
            isError = r.isFailure; result = r.fold({ "Sent to $it members" }, { it.message })
            if (r.isSuccess) { title = ""; message = "" } } }, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Filled.Campaign, null); Spacer(Modifier.width(6.dp)); Text("Post announcement") }
    }
}

@Composable
fun ReportsScreen(vm: AdminViewModel) {
    val members by vm.allMembersList.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    val savings by vm.allSavings.collectAsStateWithLifecycle()
    val repayments by vm.repayments.collectAsStateWithLifecycle()
    val totalSavings by vm.totalSavings.collectAsStateWithLifecycle()
    var msg by remember { mutableStateOf<String?>(null) }
    val l = loans; val t = txns; val s = savings; val r = repayments
    if (l == null || t == null || s == null || r == null) { LoadingBox(); return }

    val disbursed = l.filter { it.status == "ACTIVE" || it.status == "FULLY_PAID" }
    val outstanding = l.filter { it.status == "ACTIVE" }.sumOf { it.outstanding }
    val months = s.filter { it.type == "DEPOSIT" }.groupBy { monthKey(it.createdAt) }.toSortedMap().entries.toList().takeLast(6)
    val loanStatuses = listOf("PENDING", "ACTIVE", "FULLY_PAID", "REJECTED")
    val txTypes = t.groupingBy { it.type }.eachCount().entries.sortedByDescending { it.value }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total savings", money(totalSavings), Icons.Filled.Savings, Modifier.weight(1f))
            StatCard("Total loans issued", money(disbursed.sumOf { it.principal }), Icons.Filled.AccountBalanceWallet, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Outstanding loans", money(outstanding), Icons.Filled.TrendingUp, Modifier.weight(1f))
            StatCard("Repayments collected", money(r.sumOf { it.amount }), Icons.Filled.Payments, Modifier.weight(1f)) } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Monthly contributions", fontWeight = FontWeight.Bold)
            if (months.isEmpty()) Text("No data yet") else { Spacer(Modifier.height(8.dp)); SimpleBarChart(months.map { e -> e.value.sumOf { it.amount } }, months.map { monthLabel(it.key) }) }
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Loans by status", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
            SimpleBarChart(loanStatuses.map { st -> l.count { it.status == st }.toDouble() }, loanStatuses.map { typeLabel(it).take(8) }, color = MaterialTheme.colorScheme.tertiary)
            loanStatuses.forEach { st -> LabeledRow(typeLabel(st), "${l.count { it.status == st }}") }
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Member statistics", fontWeight = FontWeight.Bold)
            LabeledRow("Total members", "${members.size}"); LabeledRow("Active", "${members.count { it.status == "ACTIVE" }}")
            LabeledRow("Pending", "${members.count { it.status == "PENDING" }}"); LabeledRow("Rejected", "${members.count { it.status == "REJECTED" }}")
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Transaction statistics", fontWeight = FontWeight.Bold); LabeledRow("Total transactions", "${t.size}")
            txTypes.forEach { LabeledRow(typeLabel(it.key), "${it.value}") }
        } } }
        msg?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { OutlinedButton({ vm.reminders { res -> msg = res.fold({ "Reminders sent for $it active loans" }, { e -> e.message }) } }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Send repayment reminders") } }
    }
}

@Composable
fun AdminMpesaScreen(vm: AdminViewModel, nameOf: (Long) -> String) {
    val payments by vm.mpesaPayments.collectAsStateWithLifecycle()
    val list = payments
    if (list == null) { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("M-Pesa summary", fontWeight = FontWeight.Bold)
            LabeledRow("Requests", "${list.size}")
            LabeledRow("Successful", "${list.count { it.status == "SUCCESS" }}")
            LabeledRow("Failed or cancelled", "${list.count { it.status == "FAILED" || it.status == "CANCELLED" }}")
            LabeledRow("Collected", money(list.filter { it.status == "SUCCESS" }.sumOf { it.amount }))
        } } }
        if (list.isEmpty()) item { EmptyState(Icons.Filled.PhoneAndroid, "No M-Pesa payments", "Payments made with M-Pesa appear here.") }
        items(list, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(nameOf(p.userId), fontWeight = FontWeight.Bold); Text("${typeLabel(p.purpose)} | ${p.phone}", style = MaterialTheme.typography.bodySmall) }
                    StatusChip(p.status)
                }
                Spacer(Modifier.height(6.dp))
                LabeledRow("Amount", money(p.amount)); LabeledRow("Receipt", p.receipt ?: "-")
                LabeledRow("Mode", if (p.simulated) "Simulated" else "Daraja sandbox")
                if (p.message.isNotBlank()) Text(p.message, style = MaterialTheme.typography.bodySmall)
                Text("${fmtDate(p.createdAt)} ${fmtTime(p.createdAt)}", style = MaterialTheme.typography.labelSmall)
            } }
        }
    }
}
