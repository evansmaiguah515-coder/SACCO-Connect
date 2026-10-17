package com.sacco.app.data

import com.sacco.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class StkResult(val checkoutRequestId: String, val message: String)
data class StatusResult(val status: String, val receipt: String?, val message: String)

/**
 * Talks to OUR payment backend (never to Safaricom directly). The backend holds the Daraja keys.
 * Every call carries the user's Firebase ID token so the backend knows who is paying.
 */
class MpesaClient {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    suspend fun stkPush(idToken: String, phone: String, amount: Int, reference: String): Result<StkResult> = withContext(Dispatchers.IO) {
        call {
            val body = JSONObject().put("phone", phone).put("amount", amount).put("reference", reference).toString().toRequestBody(json)
            val j = execute(request("mpesa/stkpush", idToken).post(body).build())
            StkResult(j.getString("checkout_request_id"), j.optString("message"))
        }
    }

    suspend fun status(idToken: String, checkoutRequestId: String): Result<StatusResult> = withContext(Dispatchers.IO) {
        call {
            val j = execute(request("mpesa/status/$checkoutRequestId", idToken).get().build())
            StatusResult(j.optString("status", "PENDING"), j.optString("receipt").takeIf { it.isNotBlank() && it != "null" }, j.optString("message"))
        }
    }

    private fun request(path: String, token: String): Request.Builder {
        check(BuildConfig.BACKEND_URL.isNotBlank()) { "BACKEND_URL is empty. Set it in app/build.gradle.kts, or set MPESA_SIMULATED to true." }
        return builder(path, token)
    }

    private fun builder(path: String, token: String) = Request.Builder()
        .url(BuildConfig.BACKEND_URL + path)
        .header("Authorization", "Bearer $token")
        .header("ngrok-skip-browser-warning", "true")   // lets free ngrok URLs answer API calls

    private fun execute(req: Request): JSONObject = http.newCall(req).execute().use { r ->
        val text = r.body?.string().orEmpty()
        val j = try { JSONObject(text.ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }
        if (!r.isSuccessful) throw IllegalStateException(j.optString("detail").ifBlank { "Payment server error (${r.code})" })
        j
    }

    private inline fun <T> call(block: () -> T): Result<T> = try { Result.success(block()) }
    catch (e: IOException) { Result.failure(IllegalStateException("Cannot reach the payment server. Check BACKEND_URL and that the server and ngrok are running.")) }
    catch (e: Exception) { Result.failure(e) }
}
