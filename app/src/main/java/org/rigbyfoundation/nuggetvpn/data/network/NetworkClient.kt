package org.rigbyfoundation.nuggetvpn.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.rigbyfoundation.nuggetvpn.data.models.AppSettings
import org.rigbyfoundation.nuggetvpn.data.models.IpInfo
import org.rigbyfoundation.nuggetvpn.data.models.Profile
import java.util.concurrent.TimeUnit

@Serializable
data class AuthRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val token: String)

@Serializable
data class ProfilesPayload(val profiles: List<Profile>)

object NetworkClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun login(server: String, username: String, password: String): Result<String> {
        return runCatching {
            val body = json.encodeToString(AuthRequest.serializer(), AuthRequest(username, password))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$server/login")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Login failed: ${response.code}")
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<AuthResponse>(respBody).token
        }
    }

    suspend fun register(server: String, username: String, password: String): Result<String> {
        return runCatching {
            val body = json.encodeToString(AuthRequest.serializer(), AuthRequest(username, password))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$server/register")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Registration failed: ${response.code}")
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<AuthResponse>(respBody).token
        }
    }

    suspend fun pushProfiles(settings: AppSettings, profiles: List<Profile>): Result<Unit> {
        return runCatching {
            val token = settings.authToken ?: throw Exception("Not authenticated")
            val server = settings.authServer ?: throw Exception("No server configured")
            val body = json.encodeToString(ProfilesPayload.serializer(), ProfilesPayload(profiles))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$server/profiles")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Push failed: ${response.code}")
        }
    }

    suspend fun pullProfiles(settings: AppSettings): Result<List<Profile>> {
        return runCatching {
            val token = settings.authToken ?: throw Exception("Not authenticated")
            val server = settings.authServer ?: throw Exception("No server configured")
            val request = Request.Builder()
                .url("$server/profiles")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Pull failed: ${response.code}")
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            json.decodeFromString<ProfilesPayload>(respBody).profiles
        }
    }

    suspend fun importSubscription(url: String): Result<String> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NuggetVPN/1.0")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Import failed: ${response.code}")
            response.body?.string() ?: throw Exception("Empty response")
        }
    }

    suspend fun checkIp(): Result<IpInfo> {
        return runCatching {
            val request = Request.Builder()
                .url("https://ipinfo.io/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val jsonObj = json.parseToJsonElement(body)
            IpInfo(
                ip = jsonObj.jsonObject["ip"]?.jsonPrimitive?.content ?: "Unknown",
                region = jsonObj.jsonObject["region"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as kotlinx.serialization.json.JsonObject
    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = this as kotlinx.serialization.json.JsonPrimitive
}
