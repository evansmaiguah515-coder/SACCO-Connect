package com.sacco.app.ui.screens

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Shows the Google account chooser and returns a Google ID token.
 * [webClientId] is the generated resource R.string.default_web_client_id (comes from google-services.json).
 */
suspend fun requestGoogleIdToken(activity: Activity, webClientId: String): Result<String> = try {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activity).getCredential(activity, request).credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    else Result.failure(IllegalStateException("Unexpected sign-in response"))
} catch (e: GetCredentialCancellationException) {
    Result.failure(IllegalStateException("Google sign-in was cancelled"))
} catch (e: NoCredentialException) {
    Result.failure(IllegalStateException("No Google account found on this phone. Add one in Settings and try again."))
} catch (e: GetCredentialException) {
    Result.failure(IllegalStateException(e.message ?: "Google sign-in failed"))
}
