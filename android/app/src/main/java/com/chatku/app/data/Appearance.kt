package com.chatku.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object Appearance {
    val dark = mutableStateOf(false)
    val accent = mutableStateOf(0xFF25D366.toInt())
    val glass = mutableStateOf(true)
    private lateinit var prefs: android.content.SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        dark.value = prefs.getBoolean("dark", false)
        accent.value = prefs.getInt("accent", 0xFF25D366.toInt())
        glass.value = prefs.getBoolean("glass", true)
    }

    fun setDark(value: Boolean) { dark.value = value; prefs.edit().putBoolean("dark", value).apply() }
    fun setGlass(value: Boolean) { glass.value = value; prefs.edit().putBoolean("glass", value).apply() }
    fun setAccent(value: Int) { accent.value = value; prefs.edit().putInt("accent", value).apply() }
}
