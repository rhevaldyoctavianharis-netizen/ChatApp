package com.chatku.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class User(
    val id: String,
    val phone: String = "",
    @SerialName("display_name") val displayName: String = "",
    val about: String? = null,
    @SerialName("avatar_media_id") val avatarMediaId: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val role: String? = null,
    val online: Boolean = false,
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class LastMessage(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    val type: String = "text",
    val body: String? = null,
    val deleted: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Chat(
    val id: String,
    val type: String,
    val name: String? = null,
    val description: String? = null,
    val role: String? = null,
    val muted: Boolean = false,
    val unread: Int = 0,
    val peer: User? = null,
    @SerialName("last_message") val lastMessage: LastMessage? = null,
    @SerialName("last_activity") val lastActivity: String? = null,
    val members: List<User>? = null,
)

@Serializable
data class Reaction(@SerialName("user_id") val userId: String, val emoji: String)

@Serializable
data class Reply(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    val type: String = "text",
    val body: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val type: String = "text",
    val body: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_filename") val mediaFilename: String? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    val deleted: Boolean = false,
    val reply: Reply? = null,
    val status: String? = null,
    val reactions: List<Reaction> = emptyList(),
    @SerialName("client_id") val clientId: String? = null,
)

@Serializable
data class MessagesPage(
    val messages: List<Message>,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class MediaInfo(
    val id: String,
    val mime: String = "",
    val filename: String? = null,
    val size: Long = 0,
    val url: String = "",
)

@Serializable
data class Receipt(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
data class OkResponse(val ok: Boolean = true, val muted: Boolean? = null, val marked: Int? = null)

@Serializable
data class Channel(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    val handle: String,
    @SerialName("avatar_media_id") val avatarMediaId: String? = null,
    val following: Boolean = false,
    val followers: Int = 0,
)

@Serializable
data class ChannelPost(
    val id: String,
    @SerialName("channel_id") val channelId: String,
    val body: String,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CreateChannelBody(val name: String, val handle: String, val description: String = "")

@Serializable
data class CreateChannelPostBody(val body: String, @SerialName("media_id") val mediaId: String? = null)

// ---------- Request bodies ----------
@Serializable
data class RegisterBody(
    val phone: String,
    @SerialName("display_name") val displayName: String,
    val password: String,
)

@Serializable
data class LoginBody(val phone: String, val password: String)

@Serializable
data class UpdateMeBody(
    @SerialName("display_name") val displayName: String? = null,
    val about: String? = null,
    @SerialName("avatar_media_id") val avatarMediaId: String? = null,
)

@Serializable
data class PhonesBody(val phones: List<String>)

@Serializable
data class DirectBody(@SerialName("user_id") val userId: String)

@Serializable
data class GroupBody(
    val name: String,
    val description: String? = null,
    @SerialName("member_ids") val memberIds: List<String>,
)

@Serializable
data class UpdateChatBody(val name: String? = null, val description: String? = null)

@Serializable
data class MuteBody(val muted: Boolean)

@Serializable
data class AddMembersBody(@SerialName("user_ids") val userIds: List<String>)

@Serializable
data class SendBody(
    val body: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
    @SerialName("client_id") val clientId: String? = null,
)

@Serializable
data class EditBody(val body: String)

@Serializable
data class EmojiBody(val emoji: String)

// ---------- Helpers ----------
fun Message.millis(): Long = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)
fun Message.isLocal(): Boolean = id.startsWith("local-")
fun Message.toReply() = Reply(id, senderId, type, body, deleted)
fun Message.toLast() = LastMessage(id, senderId, type, body, deleted, createdAt)
fun Chat.title(): String = if (type == "group") (name ?: "Grup") else (peer?.displayName ?: "Pengguna")
fun Chat.avatarId(): String? = if (type == "group") null else peer?.avatarMediaId
fun mediaTypeOf(mime: String): String = when {
    mime.startsWith("image/") -> "image"
    mime.startsWith("video/") -> "video"
    mime.startsWith("audio/") -> "audio"
    else -> "file"
}
