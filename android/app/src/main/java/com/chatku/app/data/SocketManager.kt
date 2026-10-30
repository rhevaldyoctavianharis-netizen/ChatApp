package com.chatku.app.data

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import org.json.JSONObject

sealed interface SocketEvent {
    data object Connected : SocketEvent
    data class NewMessage(val message: Message) : SocketEvent
    data class Status(val chatId: String, val messageId: String, val status: String) : SocketEvent
    data class Edited(val message: Message) : SocketEvent
    data class Deleted(val chatId: String, val messageId: String) : SocketEvent
    data class ReactionChanged(val chatId: String, val messageId: String, val userId: String, val emoji: String?) : SocketEvent
    data class Typing(val chatId: String, val userId: String, val isTyping: Boolean) : SocketEvent
    data class Presence(val userId: String, val online: Boolean, val lastSeen: String?) : SocketEvent
    data class ChatNew(val chat: Chat) : SocketEvent
    data class ChatUpdated(val chatId: String) : SocketEvent
    data class MemberRemoved(val chatId: String, val userId: String) : SocketEvent
    data class ChatRead(val chatId: String) : SocketEvent
}

class SocketManager {
    private var socket: Socket? = null
    val events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 256)
    val connected = MutableStateFlow(false)

    fun connect(baseUrl: String, token: String) {
        disconnect()
        val opts = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1000
            reconnectionDelayMax = 8000
        }
        val s = IO.socket(baseUrl, opts)

        s.on(Socket.EVENT_CONNECT) {
            connected.value = true
            events.tryEmit(SocketEvent.Connected)
        }
        s.on(Socket.EVENT_DISCONNECT) { connected.value = false }
        s.on(Socket.EVENT_CONNECT_ERROR) { connected.value = false }

        listen(s, "message:new") { o -> SocketEvent.NewMessage(decode(Message.serializer(), o)) }
        listen(s, "message:edited") { o -> SocketEvent.Edited(decode(Message.serializer(), o)) }
        listen(s, "message:status") { o ->
            SocketEvent.Status(o.getString("chat_id"), o.getString("message_id"), o.getString("status"))
        }
        listen(s, "message:deleted") { o -> SocketEvent.Deleted(o.getString("chat_id"), o.getString("message_id")) }
        listen(s, "message:reaction") { o ->
            SocketEvent.ReactionChanged(
                o.getString("chat_id"), o.getString("message_id"), o.getString("user_id"),
                if (o.isNull("emoji")) null else o.getString("emoji"),
            )
        }
        listen(s, "typing") { o ->
            SocketEvent.Typing(o.getString("chat_id"), o.getString("user_id"), o.optBoolean("is_typing"))
        }
        listen(s, "presence") { o ->
            SocketEvent.Presence(
                o.getString("user_id"), o.optBoolean("online"),
                if (o.isNull("last_seen")) null else o.optString("last_seen"),
            )
        }
        listen(s, "chat:new") { o -> SocketEvent.ChatNew(decode(Chat.serializer(), o)) }
        listen(s, "chat:updated") { o -> SocketEvent.ChatUpdated(o.getString("chat_id")) }
        listen(s, "chat:member_removed") { o -> SocketEvent.MemberRemoved(o.getString("chat_id"), o.getString("user_id")) }
        listen(s, "chat:read") { o -> SocketEvent.ChatRead(o.getString("chat_id")) }

        s.connect()
        socket = s
    }

    private fun <T> decode(ser: KSerializer<T>, o: JSONObject): T = Net.json.decodeFromString(ser, o.toString())

    private fun listen(s: Socket, name: String, map: (JSONObject) -> SocketEvent?) {
        s.on(name) { args ->
            val o = args.firstOrNull() as? JSONObject
            if (o != null) {
                try {
                    map(o)?.let { events.tryEmit(it) }
                } catch (_: Exception) {
                    // abaikan payload yang tidak sesuai
                }
            }
        }
    }

    fun emitTyping(chatId: String, isTyping: Boolean) {
        val s = socket ?: return
        if (!s.connected()) return
        s.emit("typing", JSONObject().put("chat_id", chatId).put("is_typing", isTyping))
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null
        connected.value = false
    }
}
