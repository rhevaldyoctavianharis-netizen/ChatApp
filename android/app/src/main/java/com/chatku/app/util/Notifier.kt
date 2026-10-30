package com.chatku.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chatku.app.MainActivity
import com.chatku.app.data.Chat
import com.chatku.app.data.Message
import com.chatku.app.data.title

object Notifier {
    private const val CHANNEL = "messages"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Pesan", NotificationManager.IMPORTANCE_HIGH)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun preview(m: Message): String = when {
        m.deleted -> "🚫 Pesan ini telah dihapus"
        m.type == "image" -> "📷 Foto"
        m.type == "video" -> "🎥 Video"
        m.type == "audio" -> "🎤 Pesan suara"
        m.type == "file" -> "📎 Dokumen"
        else -> m.body.orEmpty()
    }

    fun show(ctx: Context, chat: Chat, m: Message, senderName: String?) {
        if (chat.muted) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chat.id)
        }
        val pi = PendingIntent.getActivity(
            ctx, chat.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (chat.type == "group" && senderName != null) "$senderName: ${preview(m)}" else preview(m)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(chat.title())
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(chat.id.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(ctx: Context, chatId: String) {
        NotificationManagerCompat.from(ctx).cancel(chatId.hashCode())
    }
}
