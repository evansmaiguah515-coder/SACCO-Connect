package com.sacco.app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** [badCredentials] is true when Firebase says the email/password pair (or the user) is wrong. */
class AuthException(val badCredentials: Boolean, message: String) : Exception(message)

data class GoogleAccount(val uid: String, val email: String, val name: String)

/** Thin wrapper over Firebase Authentication that turns Firebase errors into friendly messages. */
class FirebaseAuthService {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signIn(email: String, password: String): Result<String> =
        call { auth.signInWithEmailAndPassword(email, password).await().user!!.uid }

    suspend fun createAccount(email: String, password: String): Result<String> = call {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user!!
        runCatching { user.sendEmailVerification().await() }   // best effort; login is not blocked
        user.uid
    }

    suspend fun signInWithGoogle(idToken: String): Result<GoogleAccount> = call {
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user!!
        GoogleAccount(user.uid, user.email.orEmpty().lowercase(), user.displayName.orEmpty())
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = call { auth.sendPasswordResetEmail(email).await(); Unit }

    suspend fun changePassword(old: String, new: String): Result<Unit> = call {
        val user = auth.currentUser ?: throw AuthException(false, "You are not signed in")
        val email = user.email ?: throw AuthException(false, "This account has no email address")
        if (user.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID })
            throw AuthException(false, "Your account signs in with Google, so it has no password to change")
        user.reauthenticate(EmailAuthProvider.getCredential(email, old)).await()
        user.updatePassword(new).await()
        Unit
    }

    /** Short-lived Firebase ID token. The payment backend verifies it to know who is calling. */
    suspend fun idToken(): Result<String> = call {
        auth.currentUser?.getIdToken(false)?.await()?.token ?: throw AuthException(false, "You are not signed in")
    }

    fun signOut() = auth.signOut()

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        try { Result.success(block()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(friendly(e)) }

    private fun friendly(e: Exception): Exception = when (e) {
        is AuthException -> e
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> AuthException(true, "Invalid email or password")
        is FirebaseAuthUserCollisionException -> AuthException(false, "That email is already registered")
        is FirebaseAuthWeakPasswordException -> AuthException(false, "That password is too weak")
        is FirebaseNetworkException -> AuthException(false, "No internet connection")
        is FirebaseTooManyRequestsException -> AuthException(false, "Too many attempts. Try again later")
        else -> AuthException(false, e.message ?: "Authentication failed")
    }
}
