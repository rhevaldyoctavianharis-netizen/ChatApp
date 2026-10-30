package com.chatku.app.data

import android.app.Application
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.chatku.app.util.Notifier
import com.chatku.app.util.UriRequestBody
import com.chatku.app.util.normalizePhone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.io.File
import java.time.Instant
import java.util.UUID

/** Sumber kebenaran state aplikasi: daftar chat, pesan, presence, dsb. Memproses event Socket.IO. */
class Repo(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val socket = SocketManager()

    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val refreshing = MutableStateFlow(false)
    val messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap()) // terbaru -> lama
    val typing = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val online = MutableStateFlow<Set<String>>(emptySet())
    val lastSeen = MutableStateFlow<Map<String, String?>>(emptyMap())
    val members = MutableStateFlow<Map<String, List<User>>>(emptyMap())
    val contacts = MutableStateFlow<List<User>>(emptyList())
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val pendingOpenChat = MutableStateFlow<String?>(null)

    @Volatile var openChatId: String? = null
    @Volatile var inForeground = true

    private var started = false
    private var collectJob: Job? = null
    private val hasMore = mutableMapOf<String, Boolean>()
    private val loading = mutableSetOf<String>()
    private val typingJobs = mutableMapOf<String, Job>()
    private val retryActions = mutableMapOf<String, () -> Unit>()

    // ------------------------------------------------------------ lifecycle
    fun start() {
        val token = Session.token.value ?: return
        if (started) return
        started = true
        socket.connect(Net.baseUrl, token)
        collectJob = scope.launch { socket.events.collect { handle(it) } }
        scope.launch {
            refreshChats()
            safe { Net.api.me() }.onSuccess { Session.updateMe(it) }
        }
    }

    fun stop() {
        started = false
        collectJob?.cancel()
        socket.disconnect()
        chats.value = emptyList()
        messages.value = emptyMap()
        typing.value = emptyMap()
        online.value = emptySet()
        lastSeen.value = emptyMap()
        members.value = emptyMap()
        contacts.value = emptyList()
        hasMore.clear()
        loading.clear()
        retryActions.clear()
        openChatId = null
    }

    // ------------------------------------------------------------ helpers
    private fun sortChats(l: List<Chat>) = l.sortedByDescending { it.lastActivity ?: "" }
    private fun sortMsgs(l: List<Message>) = l.sortedByDescending { it.millis() }
    private val myId get() = Session.me.value?.id

    // ------------------------------------------------------------ socket events
    private fun handle(e: SocketEvent) {
        when (e) {
            is SocketEvent.Connected -> scope.launch {
                refreshChats()
                openChatId?.let { loadMessages(it) }
            }
            is SocketEvent.NewMessage -> onNewMessage(e.message)
            is SocketEvent.Status -> updateMessage(e.chatId, e.messageId) { it.copy(status = e.status) }
            is SocketEvent.Edited -> updateMessage(e.message.chatId, e.message.id) { e.message }
            is SocketEvent.Deleted -> {
                updateMessage(e.chatId, e.messageId) {
                    it.copy(deleted = true, body = null, mediaId = null, reactions = emptyList())
                }
                chats.update { l ->
                    l.map { c ->
                        if (c.id == e.chatId && c.lastMessage?.id == e.messageId)
                            c.copy(lastMessage = c.lastMessage?.copy(deleted = true, body = null)) else c
                    }
                }
            }
            is SocketEvent.ReactionChanged -> updateMessage(e.chatId, e.messageId) { m ->
                val others = m.reactions.filter { it.userId != e.userId }
                m.copy(reactions = if (e.emoji != null) others + Reaction(e.userId, e.emoji) else others)
            }
            is SocketEvent.Typing -> onTyping(e)
            is SocketEvent.Presence -> {
                online.update { if (e.online) it + e.userId else it - e.userId }
                lastSeen.update { it + (e.userId to e.lastSeen) }
            }
            is SocketEvent.ChatNew -> chats.update { l -> sortChats(listOf(e.chat) + l.filter { it.id != e.chat.id }) }
            is SocketEvent.ChatUpdated -> scope.launch { refreshChat(e.chatId) }
            is SocketEvent.MemberRemoved -> {
                if (e.userId == myId) chats.update { l -> l.filter { it.id != e.chatId } }
                else scope.launch { refreshChat(e.chatId) }
            }
            is SocketEvent.ChatRead -> chats.update { l -> l.map { if (it.id == e.chatId) it.copy(unread = 0) else it } }
        }
    }

    private fun onTyping(e: SocketEvent.Typing) {
        if (e.userId == myId) return
        val key = "${e.chatId}:${e.userId}"
        typingJobs.remove(key)?.cancel()
        setTypingUser(e.chatId, e.userId, e.isTyping)
        if (e.isTyping) {
            typingJobs[key] = scope.launch {
                delay(6000)
                setTypingUser(e.chatId, e.userId, false)
            }
        }
    }

    private fun setTypingUser(chatId: String, userId: String, isTyping: Boolean) {
        typing.update { m ->
            val s = m[chatId].orEmpty()
            m + (chatId to if (isTyping) s + userId else s - userId)
        }
    }

    private fun onNewMessage(m: Message) {
        upsertMessage(m.chatId, m)
        setTypingUser(m.chatId, m.senderId, false)
        val chat = chats.value.firstOrNull { it.id == m.chatId }
        if (chat == null) {
            scope.launch { refreshChats() }
            return
        }
        val fromMe = m.senderId == myId
        val isOpen = openChatId == m.chatId && inForeground
        chats.update { list ->
            sortChats(
                list.map { c ->
                    if (c.id == m.chatId)
                        c.copy(
                            lastMessage = m.toLast(),
                            lastActivity = m.createdAt,
                            unread = if (fromMe || isOpen) c.unread else c.unread + 1,
                        )
                    else c
                },
            )
        }
        if (!fromMe) {
            if (isOpen) markRead(m.chatId)
            else {
                val sender = members.value[m.chatId]?.firstOrNull { it.id == m.senderId }?.displayName
                Notifier.show(app, chat, m, sender)
            }
        }
    }

    // ------------------------------------------------------------ message state
    private fun upsertMessage(chatId: String, m: Message) {
        messages.update { map ->
            val cur = map[chatId].orEmpty().filterNot {
                it.id == m.id || (m.clientId != null && it.clientId == m.clientId)
            }
            map + (chatId to sortMsgs(cur + m))
        }
    }

    private fun updateMessage(chatId: String, messageId: String, f: (Message) -> Message) {
        messages.update { map ->
            val cur = map[chatId] ?: return@update map
            map + (chatId to cur.map { if (it.id == messageId) f(it) else it })
        }
    }

    private fun setLocalStatus(local: Message, status: String) = updateMessage(local.chatId, local.id) { it.copy(status = status) }

    private fun touchChat(m: Message) {
        chats.update { list ->
            sortChats(
                list.map { c ->
                    if (c.id == m.chatId && (c.lastActivity ?: "") <= m.createdAt)
                        c.copy(lastMessage = m.toLast(), lastActivity = m.createdAt) else c
                },
            )
        }
    }

    // ------------------------------------------------------------ loading
    suspend fun refreshChats() {
        refreshing.value = true
        safe { Net.api.chats() }
            .onSuccess { chats.value = sortChats(it) }
            .onFailure { errors.tryEmit(it.message ?: "Gagal memuat chat") }
        refreshing.value = false
    }

    fun refresh() {
        scope.launch { refreshChats() }
    }

    suspend fun refreshChat(chatId: String) {
        safe { Net.api.chat(chatId) }.onSuccess { c ->
            c.members?.let { m -> members.update { it + (chatId to m) } }
            chats.update { list ->
                sortChats(if (list.any { it.id == c.id }) list.map { if (it.id == c.id) c else it } else list + c)
            }
        }
    }

    suspend fun loadMessages(chatId: String, older: Boolean = false) {
        if (!loading.add(chatId)) return
        try {
            val before = if (older) messages.value[chatId].orEmpty().lastOrNull { !it.isLocal() }?.id else null
            if (older && (before == null || hasMore[chatId] == false)) return
            safe { Net.api.messages(chatId, before, 50) }
                .onSuccess { page ->
                    if (older || !hasMore.containsKey(chatId)) hasMore[chatId] = page.hasMore
                    messages.update { map ->
                        map + (chatId to sortMsgs((page.messages + map[chatId].orEmpty()).distinctBy { it.id }))
                    }
                }
                .onFailure { errors.tryEmit(it.message ?: "Gagal memuat pesan") }
        } finally {
            loading.remove(chatId)
        }
    }

    fun loadOlder(chatId: String) {
        scope.launch { loadMessages(chatId, older = true) }
    }

    fun openChat(chatId: String) {
        openChatId = chatId
        scope.launch {
            val c = chats.value.firstOrNull { it.id == chatId }
            if (c == null || c.type == "group") refreshChat(chatId)
            loadMessages(chatId)
            markRead(chatId)
        }
    }

    fun closeChat(chatId: String) {
        if (openChatId == chatId) openChatId = null
    }

    fun markRead(chatId: String) {
        Notifier.cancel(app, chatId)
        chats.update { l -> l.map { if (it.id == chatId) it.copy(unread = 0) else it } }
        scope.launch { safe { Net.api.markRead(chatId) } }
    }

    fun setTyping(chatId: String, isTyping: Boolean) = socket.emitTyping(chatId, isTyping)

    fun refreshPresence(userId: String) {
        scope.launch {
            safe { Net.api.user(userId) }.onSuccess { u ->
                online.update { if (u.online) it + u.id else it - u.id }
                lastSeen.update { it + (u.id to u.lastSeen) }
            }
        }
    }

    // ------------------------------------------------------------ sending
    fun sendText(chatId: String, text: String, reply: Message?) {
        val me = Session.me.value ?: return
        val clientId = UUID.randomUUID().toString()
        val local = Message(
            id = "local-$clientId", chatId = chatId, senderId = me.id, type = "text", body = text,
            createdAt = Instant.now().toString(), reply = reply?.toReply(), status = "pending", clientId = clientId,
        )
        upsertMessage(chatId, local)
        val action = {
            setLocalStatus(local, "pending")
            scope.launch { doSend(local, SendBody(body = text, replyTo = reply?.id, clientId = clientId)) }
            Unit
        }
        retryActions[local.id] = action
        action()
    }

    fun sendMedia(chatId: String, uri: Uri, caption: String, reply: Message?) {
        val me = Session.me.value ?: return
        val mime = mimeOf(uri)
        val name = queryName(uri) ?: "file"
        val size = querySize(uri)
        val clientId = UUID.randomUUID().toString()
        val local = Message(
            id = "local-$clientId", chatId = chatId, senderId = me.id, type = mediaTypeOf(mime),
            body = caption.ifBlank { null }, mediaMime = mime, mediaFilename = name,
            mediaSize = size.takeIf { it >= 0 }, createdAt = Instant.now().toString(),
            reply = reply?.toReply(), status = "pending", clientId = clientId,
        )
        upsertMessage(chatId, local)
        val action = {
            setLocalStatus(local, "pending")
            scope.launch {
                val part = MultipartBody.Part.createFormData(
                    "file", name, UriRequestBody(app.contentResolver, uri, mime, size),
                )
                safe { Net.api.upload(part) }
                    .onSuccess { info ->
                        doSend(local, SendBody(body = local.body, mediaId = info.id, replyTo = reply?.id, clientId = clientId))
                    }
                    .onFailure {
                        setLocalStatus(local, "failed")
                        errors.tryEmit(it.message ?: "Gagal mengunggah file")
                    }
            }
            Unit
        }
        retryActions[local.id] = action
        action()
    }

    private suspend fun doSend(local: Message, body: SendBody) {
        safe { Net.api.send(local.chatId, body) }
            .onSuccess { sent ->
                retryActions.remove(local.id)
                reconcile(local, sent)
            }
            .onFailure {
                setLocalStatus(local, "failed")
                errors.tryEmit(it.message ?: "Gagal mengirim pesan")
            }
    }

    private fun reconcile(local: Message, sent: Message) {
        messages.update { map ->
            val cur = map[local.chatId].orEmpty().filterNot { it.id == local.id }
            map + (local.chatId to if (cur.any { it.id == sent.id }) cur else sortMsgs(cur + sent))
        }
        touchChat(sent)
    }

    fun retry(m: Message) {
        retryActions[m.id]?.invoke()
    }

    fun discardLocal(m: Message) {
        retryActions.remove(m.id)
        messages.update { map -> map + (m.chatId to map[m.chatId].orEmpty().filterNot { it.id == m.id }) }
    }

    fun editMessage(m: Message, newText: String) {
        scope.launch {
            safe { Net.api.edit(m.id, EditBody(newText)) }
                .onSuccess { updated -> updateMessage(m.chatId, m.id) { updated } }
                .onFailure { errors.tryEmit(it.message ?: "Gagal mengedit") }
        }
    }

    fun deleteMessage(m: Message) {
        scope.launch {
            safe { Net.api.delete(m.id) }
                .onSuccess {
                    updateMessage(m.chatId, m.id) { it.copy(deleted = true, body = null, mediaId = null, reactions = emptyList()) }
                }
                .onFailure { errors.tryEmit(it.message ?: "Gagal menghapus") }
        }
    }

    fun react(m: Message, emoji: String) {
        val mine = m.reactions.firstOrNull { it.userId == myId }
        scope.launch {
            val r = if (mine?.emoji == emoji) safe { Net.api.unreact(m.id) } else safe { Net.api.react(m.id, EmojiBody(emoji)) }
            r.onFailure { errors.tryEmit(it.message ?: "Gagal memberi reaksi") }
        }
    }

    suspend fun receipts(messageId: String): Result<List<Receipt>> = safe { Net.api.receipts(messageId) }

    // ------------------------------------------------------------ chats & users
    suspend fun searchUsers(q: String): Result<List<User>> = safe { Net.api.search(q) }

    suspend fun openDirect(userId: String): Result<Chat> {
        val r = safe { Net.api.direct(DirectBody(userId)) }
        r.onSuccess { c -> chats.update { l -> sortChats(listOf(c) + l.filter { it.id != c.id }) } }
        return r
    }

    suspend fun createGroup(
        name: String,
        description: String?,
        memberIds: List<String>,
        isPublic: Boolean = false,
        publicHandle: String? = null,
        communityName: String? = null,
    ): Result<Chat> {
        val r = safe { Net.api.group(GroupBody(name, description, memberIds, isPublic, publicHandle, communityName)) }
        r.onSuccess { c ->
            c.members?.let { m -> members.update { it + (c.id to m) } }
            chats.update { l -> sortChats(listOf(c) + l.filter { it.id != c.id }) }
        }
        return r
    }

    suspend fun updateGroup(chatId: String, name: String, description: String): Result<Chat> {
        val r = safe { Net.api.updateChat(chatId, UpdateChatBody(name, description)) }
        r.onSuccess { refreshChat(chatId) }
        return r
    }

    suspend fun addMembers(chatId: String, ids: List<String>): Result<Chat> {
        val r = safe { Net.api.addMembers(chatId, AddMembersBody(ids)) }
        r.onSuccess { refreshChat(chatId) }
        return r
    }

    suspend fun removeMember(chatId: String, userId: String): Result<OkResponse> {
        val r = safe { Net.api.removeMember(chatId, userId) }
        r.onSuccess {
            if (userId == myId) chats.update { l -> l.filter { it.id != chatId } } else refreshChat(chatId)
        }
        return r
    }

    fun setMuted(chatId: String, muted: Boolean) {
        chats.update { l -> l.map { if (it.id == chatId) it.copy(muted = muted) else it } }
        scope.launch { safe { Net.api.mute(chatId, MuteBody(muted)) } }
    }

    // ------------------------------------------------------------ profile
    suspend fun updateProfile(name: String, about: String): Result<User> {
        val r = safe { Net.api.updateMe(UpdateMeBody(displayName = name, about = about)) }
        r.onSuccess { Session.updateMe(it) }
        return r
    }

    suspend fun uploadAvatar(uri: Uri): Result<User> {
        val mime = mimeOf(uri)
        val part = MultipartBody.Part.createFormData(
            "file", queryName(uri) ?: "avatar.jpg", UriRequestBody(app.contentResolver, uri, mime, querySize(uri)),
        )
        val up = safe { Net.api.upload(part) }
        val info = up.getOrElse { return Result.failure(it) }
        val r = safe { Net.api.updateMe(UpdateMeBody(avatarMediaId = info.id)) }
        r.onSuccess { Session.updateMe(it) }
        return r
    }

    // ------------------------------------------------------------ contacts
    private fun readContactPhones(): List<String> {
        val out = LinkedHashSet<String>()
        val col = ContactsContract.CommonDataKinds.Phone.NUMBER
        app.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(col), null, null, null,
        )?.use { c ->
            val idx = c.getColumnIndex(col)
            while (c.moveToNext()) {
                normalizePhone(c.getString(idx))?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    suspend fun syncContacts(): Result<List<User>> = withContext(Dispatchers.IO) {
        val phones = readContactPhones()
        val found = mutableListOf<User>()
        for (chunk in phones.chunked(500)) {
            val r = safe { Net.api.match(PhonesBody(chunk)) }
            if (r.isFailure) return@withContext Result.failure<List<User>>(r.exceptionOrNull() ?: Exception("Gagal"))
            found += r.getOrThrow()
        }
        val sorted = found.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
        contacts.value = sorted
        Result.success(sorted)
    }

    // ------------------------------------------------------------ uri helpers
    fun mimeOf(uri: Uri): String =
        app.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()))
            ?: "application/octet-stream"

    fun queryName(uri: Uri): String? =
        if (uri.scheme == "content") {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } else uri.lastPathSegment

    fun querySize(uri: Uri): Long = when (uri.scheme) {
        "content" -> app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        "file" -> File(uri.path ?: "").length()
        else -> -1L
    }
}
