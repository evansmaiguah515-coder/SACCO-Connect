package com.sacco.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.sacco.app.R
import com.sacco.app.data.GoogleOutcome
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sacco.app.ui.components.DropdownField
import com.sacco.app.ui.components.FormField
import com.sacco.app.util.Validators
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sacco.app.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(vm: AuthViewModel, onRoute: (String) -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onRoute(when (vm.sessionRole()) { "ADMIN" -> "admin"; "MEMBER" -> "member"; else -> "login" })
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.AccountBalance, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(16.dp))
            Text("SACCO Connect", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text("Save. Borrow. Grow.", color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onSuccess: (String) -> Unit, onRegister: () -> Unit, onForgot: () -> Unit, onNeedsProfile: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var idErr by remember { mutableStateOf<String?>(null) }
    var pwErr by remember { mutableStateOf<String?>(null) }
    var formErr by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.AccountBalance, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Sign in to your SACCO account (secured by Firebase)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FormField(id, { id = it; idErr = null }, "Email, phone or member number", idErr)
        FormField(pw, { pw = it; pwErr = null }, "Password", pwErr, password = true)
        formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            idErr = Validators.required(id, "Email, phone or member number")
            pwErr = Validators.required(pw, "Password")
            if (idErr == null && pwErr == null) {
                loading = true; formErr = null
                vm.login(id, pw) { r -> loading = false; r.onSuccess { onSuccess(it.role) }.onFailure { formErr = it.message } }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Login")
        }
        OutlinedButton(onClick = {
            val activity = context as? Activity ?: return@OutlinedButton
            loading = true; formErr = null
            scope.launch {
                requestGoogleIdToken(activity, activity.getString(R.string.default_web_client_id))
                    .onSuccess { token ->
                        vm.googleSignIn(token) { r ->
                            loading = false
                            r.onSuccess { out -> when (out) { is GoogleOutcome.Existing -> onSuccess(out.user.role); is GoogleOutcome.NeedsProfile -> onNeedsProfile() } }
                                .onFailure { formErr = it.message }
                        }
                    }
                    .onFailure { loading = false; formErr = it.message }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Continue with Google") }
        TextButton(onForgot, Modifier.align(Alignment.CenterHorizontally)) { Text("Forgot password?") }
        TextButton(onRegister, Modifier.align(Alignment.CenterHorizontally)) { Text("New member? Create account") }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(14.dp)) {
                Text("DEMO DATA (simulated, no real money)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text("Member: jane@sacco.demo / Member@123", style = MaterialTheme.typography.bodySmall)
                Text("Admin: admin@sacco.demo / Admin@123", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val v = remember { mutableStateMapOf("memberNumber" to "", "fullName" to "", "nationalId" to "", "phone" to "", "email" to "",
        "gender" to "", "dob" to "", "address" to "", "password" to "", "confirm" to "") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formErr by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    fun field(key: String, label: String, kb: KeyboardType = KeyboardType.Text, pw: Boolean = false): @Composable () -> Unit = {
        FormField(v[key].orEmpty(), { v[key] = it; errors = errors - key }, label, errors[key], kb, pw)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Create account") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            field("memberNumber", "Member number (e.g. SAC-0100)")()
            field("fullName", "Full name")()
            field("nationalId", "National ID / passport number")()
            field("phone", "Phone number (0712345678)", KeyboardType.Phone)()
            field("email", "Email address", KeyboardType.Email)()
            DropdownField("Gender", listOf("Female", "Male", "Other"), v["gender"].orEmpty(), { v["gender"] = it; errors = errors - "gender" }, errors["gender"])
            field("dob", "Date of birth (yyyy-MM-dd)")()
            field("address", "Address")()
            field("password", "Password", pw = true)()
            field("confirm", "Confirm password", pw = true)()
            formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                errors = Validators.validateRegistration(v["memberNumber"]!!, v["fullName"]!!, v["nationalId"]!!, v["phone"]!!, v["email"]!!,
                    v["gender"]!!, v["dob"]!!, v["address"]!!, v["password"]!!, v["confirm"]!!)
                if (errors.isEmpty()) {
                    loading = true; formErr = null
                    vm.register(v["memberNumber"]!!, v["fullName"]!!, v["nationalId"]!!, v["phone"]!!, v["email"]!!, v["gender"]!!,
                        v["dob"]!!, v["address"]!!, v["password"]!!, v["confirm"]!!, cb = { r ->
                            loading = false; r.onSuccess { done = true }.onFailure { formErr = it.message }
                        })
                }
            }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Register")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (done) AlertDialog(onDismissRequest = {}, title = { Text("Registration successful") },
        text = { Text("Your account was created. The administrator will approve your membership. You can log in now.") },
        confirmButton = { Button(onBack) { Text("Go to login") } })
}

@Composable
fun ForgotPasswordScreen(vm: AuthViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var formErr by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text("Reset password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Enter your email. Firebase will send you a link to choose a new password.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FormField(email, { email = it; emailErr = null }, "Email address", emailErr, KeyboardType.Email)
        formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            emailErr = Validators.email(email)
            if (emailErr == null) {
                loading = true; formErr = null
                vm.resetPassword(email) { r -> loading = false; r.onSuccess { done = true }.onFailure { formErr = it.message } }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (loading) "Sending..." else "Send reset link") }
    }
    if (done) AlertDialog(onDismissRequest = {}, title = { Text("Check your email") },
        text = { Text("If that address is registered, a password reset link has been sent. Open it, set a new password, then log in.") },
        confirmButton = { Button(onBack) { Text("Back to login") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(vm: AuthViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val pending by vm.pendingGoogle.collectAsStateWithLifecycle()
    val g = pending
    if (g == null) { LaunchedEffect(Unit) { onBack() }; return }
    var memberNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf(g.name) }
    var nationalId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formErr by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Complete your profile") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Signed in with Google as ${g.email}. Add your member details to finish joining the SACCO.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormField(memberNumber, { memberNumber = it; errors = errors - "memberNumber" }, "Member number (e.g. SAC-0100)", errors["memberNumber"])
            FormField(fullName, { fullName = it; errors = errors - "fullName" }, "Full name", errors["fullName"])
            FormField(nationalId, { nationalId = it; errors = errors - "nationalId" }, "National ID / passport number", errors["nationalId"])
            FormField(phone, { phone = it; errors = errors - "phone" }, "Phone number (0712345678)", errors["phone"], KeyboardType.Phone)
            DropdownField("Gender", listOf("Female", "Male", "Other"), gender, { gender = it; errors = errors - "gender" }, errors["gender"])
            FormField(dob, { dob = it; errors = errors - "dob" }, "Date of birth (yyyy-MM-dd)", errors["dob"])
            FormField(address, { address = it; errors = errors - "address" }, "Address", errors["address"])
            formErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                errors = Validators.validateProfileFields(memberNumber, fullName, nationalId, phone, g.email, gender, dob, address)
                if (errors.isEmpty()) {
                    loading = true; formErr = null
                    vm.completeGoogleProfile(memberNumber, fullName, nationalId, phone, gender, dob, address) { r ->
                        loading = false; r.onSuccess { onDone() }.onFailure { formErr = it.message }
                    }
                }
            }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Finish registration") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
