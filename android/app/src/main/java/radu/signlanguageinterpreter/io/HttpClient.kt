package radu.signlanguageinterpreter.io

import com.auth0.jwt.JWT
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.BuildConfig
import radu.signlanguageinterpreter.globals.REFRESH_TOKEN
import radu.signlanguageinterpreter.globals.TOKEN
import radu.signlanguageinterpreter.globals.TOKENS_PREFS_NAME
import java.util.Date

object HttpClient {
    private val JsonMediaType = "application/json".toMediaType()
    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val gson = GsonBuilder().create()
    private val preferences = Application.getEncryptedSharedPreferences(TOKENS_PREFS_NAME)

    suspend fun <T> get(
        url: String, typeToken: TypeToken<T>
    ): Result<T> {
        return doRequest(url, HttpMethod.GET, typeToken)
    }

    suspend fun post(
        url: String,
        payload: Any? = null,
    ): Result<Unit> {
        return doRequest(url, HttpMethod.POST, object : TypeToken<Unit>() {}, payload)
    }

    suspend fun <T> post(
        url: String,
        typeToken: TypeToken<T>,
        payload: Any? = null,
    ): Result<T> {
        return doRequest(url, HttpMethod.POST, typeToken, payload)
    }

    suspend fun delete(
        url: String
    ): Result<Unit> {
        return doRequest(url, HttpMethod.DELETE, object : TypeToken<Unit>() {})
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> doRequest(
        url: String, httpMethod: HttpMethod, typeToken: TypeToken<T>, payload: Any? = null
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            var token = preferences.getString(TOKEN, null) ?: return@withContext Result.failure(
                HttpClientException("Not logged in.", 401)
            )

            if (isJwtExpired(token)) {
                val result = refreshToken(token)
                if (result.isSuccess) {
                    token = result.getOrThrow()
                } else {
                    return@withContext Result.failure(result.exceptionOrNull()!!)
                }
            }

            val body = if (payload != null) {
                val json = gson.toJson(payload)
                json.toRequestBody(JsonMediaType)
            } else {
                "".toRequestBody()
            }

            var requestBuilder = Request.Builder().url(BuildConfig.API_URI + url)
                .addHeader("Authorization", "Bearer $token")
            requestBuilder = when (httpMethod) {
                HttpMethod.GET -> requestBuilder.get()
                HttpMethod.POST -> requestBuilder.post(body)
                HttpMethod.DELETE -> requestBuilder.delete()
            }
            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    HttpClientException(
                        response.message, response.code
                    )
                )
            }

            val responseJson = response.body?.string()

            if (typeToken.type == String::class.java) {
                return@withContext Result.success(responseJson as T)
            }

            val responseData = gson.fromJson<T>(responseJson, typeToken.type)

            return@withContext Result.success(responseData)
        } catch (e: Exception) {
            return@withContext Result.failure(
                HttpClientException(
                    e.message ?: "Unknown error.", 400
                )
            )
        }
    }

    fun isJwtExpired(token: String): Boolean {
        return try {
            val decodedJWT = JWT.decode(token)
            val expiration = decodedJWT.expiresAt ?: return true
            expiration.before(Date())
        } catch (e: Exception) {
            true
        }
    }

    suspend fun login(userName: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val requestPayload = mapOf("userName" to userName, "password" to password)
                val body = gson.toJson(requestPayload).toRequestBody(JsonMediaType)

                val request =
                    Request.Builder().url(BuildConfig.API_URI + "authenticate/login").post(body)
                        .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        HttpClientException(
                            response.message, response.code
                        )
                    )
                }

                val responseJson = response.body?.string()
                val responseData =
                    gson.fromJson(responseJson, object : TypeToken<Map<String, String>>() {})

                with(preferences.edit()) {
                    putString(TOKEN, responseData[TOKEN]!!)
                    putString(REFRESH_TOKEN, responseData[REFRESH_TOKEN]!!)
                    apply()
                }

                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    HttpClientException(
                        e.message ?: "Unknown error.", 400
                    )
                )
            }
        }

    suspend fun register(userName: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val requestPayload = mapOf("userName" to userName, "password" to password)
                val body = gson.toJson(requestPayload).toRequestBody(JsonMediaType)

                val request =
                    Request.Builder().url(BuildConfig.API_URI + "authenticate/register").post(body)
                        .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        HttpClientException(
                            response.message, response.code
                        )
                    )
                }

                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    HttpClientException(
                        e.message ?: "Unknown error.", 400
                    )
                )
            }
        }

    suspend fun refreshToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = preferences.getString(REFRESH_TOKEN, null)

            val requestPayload = mapOf(TOKEN to token, REFRESH_TOKEN to refreshToken)
            val body = gson.toJson(requestPayload).toRequestBody(JsonMediaType)

            val request =
                Request.Builder().url(BuildConfig.API_URI + "authenticate/refreshToken").post(body)
                    .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                with(preferences.edit()) {
                    remove(TOKEN)
                    remove(REFRESH_TOKEN)
                    apply()
                }
                return@withContext Result.failure(
                    HttpClientException(
                        response.message, response.code
                    )
                )
            }

            val responseJson = response.body?.string()
            val responseData =
                gson.fromJson(responseJson, object : TypeToken<Map<String, String>>() {})

            val newToken = responseData[TOKEN]!!

            with(preferences.edit()) {
                putString(TOKEN, newToken)
                putString(REFRESH_TOKEN, responseData[REFRESH_TOKEN]!!)
                apply()
            }

            return@withContext Result.success(newToken)
        } catch (e: Exception) {
            with(preferences.edit()) {
                remove(TOKEN)
                remove(REFRESH_TOKEN)
                apply()
            }
            return@withContext Result.failure(
                HttpClientException(
                    e.message ?: "Unknown error.", 400
                )
            )
        }
    }

    suspend fun logout(): Result<Unit> {
        val result = delete("authenticate/logout")
        if (result.isSuccess) {
            with(preferences.edit()) {
                remove(TOKEN)
                remove(REFRESH_TOKEN)
                apply()
            }
        }
        return result
    }

    class HttpClientException(override val message: String, val code: Int) : Exception(message)

    private enum class HttpMethod {
        GET, POST, DELETE
    }
}