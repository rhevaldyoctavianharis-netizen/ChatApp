package com.chatku.app.data

import android.content.Context
import android.content.SharedPreferences
import com.chatku.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow

object Session {
    private lateinit var prefs: SharedPreferences

    val token = MutableStateFlow<String?>(null)
    val me = MutableStateFlow<User?>(null)

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
        token.value = prefs.getString("token", null)
        me.value = prefs.getString("me", null)?.let {
            runCatching { Net.json.decodeFromString(User.serializer(), it) }.getOrNull()
        }
    }

    var baseUrl: String
        get() = prefs.getString("base_url", null) ?: BuildConfig.BASE_URL
        set(value) {
            prefs.edit().putString("base_url", value).apply()
        }

    fun save(newToken: String, user: User) {
        prefs.edit()
            .putString("token", newToken)
            .putString("me", Net.json.encodeToString(User.serializer(), user))
            .apply()
        me.value = user
        token.value = newToken
    }

    fun updateMe(user: User) {
        prefs.edit().putString("me", Net.json.encodeToString(User.serializer(), user)).apply()
        me.value = user
    }

    fun logout() {
        prefs.edit().remove("token").remove("me").apply()
        me.value = null
        token.value = null
    }
}
