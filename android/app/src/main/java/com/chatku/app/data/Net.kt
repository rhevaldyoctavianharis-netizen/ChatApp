package com.chatku.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    lateinit var api: Api
        private set

    var baseUrl: String = ""
        private set

    fun normalizeBaseUrl(url: String): String {
        var u = url.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }

    fun configure(url: String) {
        val normalized = normalizeBaseUrl(url)
        baseUrl = normalized
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = Session.token.value
                val req = chain.request().newBuilder().apply {
                    if (token != null) header("Authorization", "Bearer $token")
                }.build()
                val res = chain.proceed(req)
                if (res.code == 401 && !req.url.encodedPath.contains("/api/auth/")) Session.logout()
                res
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        api = Retrofit.Builder()
            .baseUrl("$normalized/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
    }

    fun mediaUrl(id: String): String = "$baseUrl/api/media/$id?token=${Session.token.value ?: ""}"
}

/** Memanggil API dan mengubah error jaringan/HTTP menjadi pesan Indonesia yang ramah. */
suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val msg = runCatching {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        Net.json.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    Result.failure(Exception(msg ?: "Kesalahan server (${e.code()})"))
} catch (e: IOException) {
    Result.failure(Exception("Tidak dapat terhubung ke server"))
} catch (e: Exception) {
    Result.failure(Exception(e.message ?: "Terjadi kesalahan"))
}
