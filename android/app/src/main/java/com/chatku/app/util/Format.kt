package com.chatku.app.util

import com.chatku.app.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone get() = ZoneId.systemDefault()
private val idLocale = Locale("id", "ID")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", idLocale)
private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", idLocale)
private val shortDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", idLocale)

fun parseMillis(iso: String?): Long =
    if (iso == null) 0L else runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

fun formatTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)

private fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

fun isSameDay(a: Long, b: Long): Boolean = dayOf(a) == dayOf(b)

fun dayLabel(ms: Long): String {
    val d = dayOf(ms)
    val today = LocalDate.now(zone)
    return when (d) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> d.format(dateFmt)
    }
}

/** Waktu di daftar chat: jam (hari ini), "Kemarin", atau tanggal. */
fun listTime(iso: String?): String {
    val ms = parseMillis(iso)
    if (ms == 0L) return ""
    val d = dayOf(ms)
    val today = LocalDate.now(zone)
    return when (d) {
        today -> formatTime(ms)
        today.minusDays(1) -> "Kemarin"
        else -> d.format(shortDateFmt)
    }
}

fun lastSeenText(iso: String?): String {
    val ms = parseMillis(iso)
    if (ms == 0L) return ""
    val d = dayOf(ms)
    val today = LocalDate.now(zone)
    return when (d) {
        today -> "terakhir dilihat hari ini ${formatTime(ms)}"
        today.minusDays(1) -> "terakhir dilihat kemarin ${formatTime(ms)}"
        else -> "terakhir dilihat ${d.format(shortDateFmt)}"
    }
}

fun humanSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}

/**
 * Ubah nomor telepon apa pun (0812..., 62812..., +62 812-...) menjadi format E.164 (+62812...).
 * Mengembalikan null bila tidak valid.
 */
fun normalizePhone(raw: String?, countryCode: String = BuildConfig.DEFAULT_COUNTRY_CODE): String? {
    if (raw == null) return null
    val trimmed = raw.trim()
    val hasPlus = trimmed.startsWith("+")
    var digits = trimmed.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    val full = when {
        hasPlus -> digits
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> countryCode + digits.drop(1)
        digits.startsWith(countryCode) -> digits
        else -> countryCode + digits
    }
    digits = full
    val e164 = "+$digits"
    return if (Regex("^\\+[1-9]\\d{7,14}$").matches(e164)) e164 else null
}

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
