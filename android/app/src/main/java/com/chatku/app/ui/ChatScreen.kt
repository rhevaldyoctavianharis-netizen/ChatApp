@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.chatku.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.chatku.app.ServiceLocator
import com.chatku.app.data.Message
import com.chatku.app.data.Net
import com.chatku.app.data.Receipt
import com.chatku.app.data.Session
import com.chatku.app.data.avatarId
import com.chatku.app.data.isLocal
import com.chatku.app.data.millis
import com.chatku.app.data.title
import com.chatku.app.util.VoiceRecorder
import com.chatku.app.util.dayLabel
import com.chatku.app.util.formatTime
import com.chatku.app.util.humanSize
import com.chatku.app.util.isSameDay
import com.chatku.app.util.lastSeenText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit, onGroupInfo: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ServiceLocator.repo
    val scope = rememberCoroutineScope()
    val wa = LocalWa.current
    val clipboard = LocalClipboardManager.current

    val me by Session.me.collectAsState()
    val chats by repo.chats.collectAsState()
    val allMsgs by repo.messages.collectAsState()
    val typingMap by repo.typing.collectAsState()
    val onlineSet by repo.online.collectAsState()
    val lastSeenMap by repo.lastSeen.collectAsState()
    val membersMap by repo.members.collectAsState()
    val snack = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val chat = chats.firstOrNull { it.id == chatId }
    val msgs = allMsgs[chatId].orEmpty()
    val isGroup = chat?.type == "group"
    val peerId = chat?.peer?.id
    val myId = me?.id

    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var selected by remember { mutableStateOf<Message?>(null) }
    var pendingMedia by remember { mutableStateOf<Uri?>(null) }
    var viewImage by remember { mutableStateOf<String?>(null) }
    var infoFor by remember { mutableStateOf<Message?>(null) }
    var confirmDelete by remember { mutableStateOf<Message?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableIntStateOf(0) }
    var attachMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val recorder = remember { VoiceRecorder(ctx) }

    fun nameOf(id: String): String =
        if (id == myId) "Anda" else (membersMap[chatId]?.firstOrNull { it.id == id }?.displayName
            ?: chat?.peer?.takeIf { it.id == id }?.displayName ?: "Anggota")

    fun openUrl(mediaId: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Net.mediaUrl(mediaId))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            scope.launch { snack.showSnackbar("Tidak ada aplikasi untuk membuka file ini") }
        }
    }

    DisposableEffect(chatId) {
        repo.openChat(chatId)
        onDispose { repo.closeChat(chatId) }
    }
    LaunchedEffect(Unit) { repo.errors.collect { snack.showSnackbar(it) } }
    LaunchedEffect(peerId) { peerId?.let { repo.refreshPresence(it) } }
    LaunchedEffect(text) {
        if (editing == null && text.isNotBlank()) {
            repo.setTyping(chatId, true)
            delay(2500)
            repo.setTyping(chatId, false)
        }
    }
    LaunchedEffect(recording) {
        recSeconds = 0
        while (recording) {
            delay(1000)
            recSeconds++
        }
    }
    val newestId = msgs.firstOrNull()?.id
    LaunchedEffect(newestId) {
        val first = msgs.firstOrNull()
        if (first != null && (first.senderId == myId || listState.firstVisibleItemIndex <= 2)) listState.animateScrollToItem(0)
    }
    LaunchedEffect(listState, chatId) {
        snapshotFlow {
            Pair(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, listState.layoutInfo.totalItemsCount)
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 4) repo.loadOlder(chatId)
        }
    }

    // ---- launchers
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingMedia = uri
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingMedia = uri
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingMedia = cameraUri
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        cameraUri = uri
        takePicture.launch(uri)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else scope.launch { snack.showSnackbar("Izin kamera ditolak") }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snack.showSnackbar("Izin mikrofon ditolak") }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else camPerm.launch(Manifest.permission.CAMERA)
    }

    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        val ed = editing
        if (ed != null) {
            if (t != ed.body) repo.editMessage(ed, t)
            editing = null
        } else {
            repo.sendText(chatId, t, replyTo)
            replyTo = null
        }
        text = ""
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        recording = recorder.start()
    }

    fun stopRecording(cancel: Boolean) {
        if (!recording) return
        recording = false
        val f = recorder.stop(cancel)
        if (f != null) {
            repo.sendMedia(chatId, Uri.fromFile(f), "", replyTo)
            replyTo = null
        }
    }

    // ---- subtitle header
    val typingNames = typingMap[chatId].orEmpty().map { nameOf(it) }
    val subtitle = when {
        typingNames.isNotEmpty() -> if (isGroup) "${typingNames.joinToString()} sedang mengetik…" else "sedang mengetik…"
        isGroup -> (membersMap[chatId] ?: chat?.members).orEmpty().joinToString { if (it.id == myId) "Anda" else it.displayName }
        peerId != null && peerId in onlineSet -> "online"
        peerId != null -> lastSeenText(lastSeenMap[peerId] ?: chat?.peer?.lastSeen)
        else -> ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snack, Modifier.imePadding()) },
        topBar = {
            val sel = selected
            if (sel == null) {
                TopAppBar(
                    colors = waTopBarColors(),
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                    title = {
                        Row(Modifier.clickable(enabled = isGroup) { onGroupInfo() }, verticalAlignment = Alignment.CenterVertically) {
                            Avatar(chat?.avatarId(), chat?.title() ?: "?", 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(chat?.title() ?: "Chat", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                if (subtitle.isNotEmpty()) {
                                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = wa.onTopBar.copy(alpha = 0.8f))
                                }
                            }
                        }
                    },
                    actions = {
                        if (isGroup) IconButton(onClick = onGroupInfo) { Icon(Icons.Filled.Info, "Info grup") }
                    },
                )
            } else {
                Column {
                    TopAppBar(
                        colors = waTopBarColors(),
                        navigationIcon = { IconButton(onClick = { selected = null }) { Icon(Icons.Filled.Close, "Tutup") } },
                        title = {},
                        actions = {
                            val mine = sel.senderId == myId
                            if (!sel.deleted && !sel.isLocal()) {
                                IconButton(onClick = { replyTo = sel; editing = null; selected = null }) { Icon(Icons.AutoMirrored.Filled.Reply, "Balas") }
                            }
                            if (!sel.deleted && !sel.body.isNullOrBlank()) {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(sel.body.orEmpty())); selected = null }) {
                                    Icon(Icons.Filled.ContentCopy, "Salin")
                                }
                            }
                            if (mine && sel.type == "text" && !sel.deleted && !sel.isLocal()) {
                                IconButton(onClick = { editing = sel; replyTo = null; text = sel.body.orEmpty(); selected = null }) {
                                    Icon(Icons.Filled.Edit, "Edit")
                                }
                            }
                            if (mine && !sel.deleted && !sel.isLocal()) {
                                IconButton(onClick = { infoFor = sel; selected = null }) { Icon(Icons.Filled.Info, "Info") }
                            }
                            if (mine && (!sel.deleted || sel.isLocal())) {
                                IconButton(onClick = {
                                    if (sel.isLocal()) { repo.discardLocal(sel); selected = null } else { confirmDelete = sel; selected = null }
                                }) { Icon(Icons.Filled.Delete, "Hapus") }
                            }
                        },
                    )
                    if (!sel.deleted && !sel.isLocal()) {
                        Row(
                            Modifier.fillMaxWidth().background(wa.inputBar).padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            quickEmojis.forEach { e ->
                                Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { repo.react(sel, e); selected = null }.padding(6.dp))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(wa.wallpaper).navigationBarsPadding().imePadding()) {
                replyTo?.let { r ->
                    QuoteBar(title = "Membalas ${nameOf(r.senderId)}", preview = previewOf(r), onClose = { replyTo = null })
                }
                editing?.let { QuoteBar(title = "Mengedit pesan", preview = it.body.orEmpty(), onClose = { editing = null; text = "" }) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Surface(shape = RoundedCornerShape(26.dp), color = wa.inputBar, shadowElevation = 1.dp, modifier = Modifier.weight(1f)) {
                        if (recording) {
                            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                                Spacer(Modifier.width(10.dp))
                                Text("%d:%02d".format(recSeconds / 60, recSeconds % 60), fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Text("Lepaskan untuk mengirim", fontSize = 12.sp, color = wa.secondary)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                TextField(
                                    value = text, onValueChange = { text = it }, maxLines = 5,
                                    placeholder = { Text("Ketik pesan") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Box {
                                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Filled.AttachFile, "Lampirkan", tint = wa.secondary) }
                                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                                        DropdownMenuItem(text = { Text("Galeri") }, onClick = {
                                            attachMenu = false
                                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        })
                                        DropdownMenuItem(text = { Text("Dokumen") }, onClick = {
                                            attachMenu = false
                                            pickDoc.launch(arrayOf("*/*"))
                                        })
                                        DropdownMenuItem(text = { Text("Kamera") }, onClick = { attachMenu = false; openCamera() })
                                    }
                                }
                                if (text.isBlank()) {
                                    IconButton(onClick = { openCamera() }) { Icon(Icons.Filled.CameraAlt, "Kamera", tint = wa.secondary) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    val hasText = text.isNotBlank()
                    val startRec = rememberUpdatedState { startRecording() }
                    val stopRec = rememberUpdatedState { c: Boolean -> stopRecording(c) }
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).background(wa.accent).then(
                            if (hasText) Modifier.clickable { send() }
                            else Modifier.pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    startRec.value()
                                    val released = tryAwaitRelease()
                                    stopRec.value(!released)
                                })
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, if (hasText) "Kirim" else "Tahan untuk merekam", tint = Color.White)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(wa.wallpaper)) {
            if (msgs.isEmpty()) {
                Text("Belum ada pesan. Sapa dulu 👋", Modifier.align(Alignment.Center), color = wa.secondary)
            }
            LazyColumn(
                state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                itemsIndexed(msgs, key = { _, m -> m.id }) { index, m ->
                    val older = msgs.getOrNull(index + 1)
                    val showDate = older == null || !isSameDay(older.millis(), m.millis())
                    Column {
                        if (showDate) DateChip(dayLabel(m.millis()))
                        MessageRow(
                            m = m, mine = m.senderId == myId, isGroup = isGroup,
                            senderName = if (isGroup && m.senderId != myId) nameOf(m.senderId) else null,
                            nameOf = ::nameOf, isSelected = selected?.id == m.id,
                            onSwipeReply = { replyTo = m; editing = null },
                            onLongPress = { selected = m },
                            onClick = {
                                val sel = selected
                                when {
                                    sel != null -> selected = if (sel.id == m.id) null else m
                                    m.status == "failed" -> repo.retry(m)
                                    m.deleted -> {}
                                    (m.type == "image") && m.mediaId != null -> viewImage = m.mediaId
                                    (m.type == "video" || m.type == "file") && m.mediaId != null -> openUrl(m.mediaId)
                                }
                            },
                            onReplyClick = { id ->
                                val idx = msgs.indexOfFirst { it.id == id }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- dialogs
    viewImage?.let { ImageViewer(it) { viewImage = null } }
    infoFor?.let { MessageInfoDialog(it) { infoFor = null } }
    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus pesan?") },
            text = { Text("Pesan akan dihapus untuk semua orang di chat ini.") },
            confirmButton = { TextButton(onClick = { repo.deleteMessage(m); confirmDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } },
        )
    }
    pendingMedia?.let { uri ->
        var caption by remember(uri) { mutableStateOf("") }
        val mime = remember(uri) { repo.mimeOf(uri) }
        AlertDialog(
            onDismissRequest = { pendingMedia = null },
            title = { Text("Kirim ${if (mime.startsWith("image/")) "foto" else if (mime.startsWith("video/")) "video" else "file"}") },
            text = {
                Column {
                    if (mime.startsWith("image/")) {
                        AsyncImage(
                            model = uri, contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Text(repo.queryName(uri) ?: "File")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Keterangan (opsional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { repo.sendMedia(chatId, uri, caption, replyTo); replyTo = null; pendingMedia = null }) { Text("Kirim") }
            },
            dismissButton = { TextButton(onClick = { pendingMedia = null }) { Text("Batal") } },
        )
    }
}

private fun previewOf(m: Message): String = when {
    m.deleted -> "Pesan dihapus"
    m.type == "image" -> "📷 Foto"
    m.type == "video" -> "🎥 Video"
    m.type == "audio" -> "🎤 Pesan suara"
    m.type == "file" -> "📎 ${m.mediaFilename ?: "Dokumen"}"
    else -> m.body.orEmpty()
}

@Composable
private fun QuoteBar(title: String, preview: String, onClose: () -> Unit) {
    val wa = LocalWa.current
    Surface(color = wa.inputBar, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(wa.accent))
            Column(Modifier.weight(1f).padding(8.dp)) {
                Text(title, color = wa.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = wa.secondary)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup", tint = wa.secondary) }
        }
    }
}

@Composable
private fun DateChip(label: String) {
    val wa = LocalWa.current
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = wa.inputBar.copy(alpha = 0.9f), shadowElevation = 1.dp) {
            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, color = wa.secondary)
        }
    }
}

@Composable
private fun MessageRow(
    m: Message,
    mine: Boolean,
    isGroup: Boolean,
    senderName: String?,
    nameOf: (String) -> String,
    isSelected: Boolean,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onReplyClick: (String) -> Unit,
) {
    val wa = LocalWa.current
    val swipe = rememberUpdatedState(onSwipeReply)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.StartToEnd) swipe.value()
        false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = !m.deleted && !m.isLocal(),
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, tint = wa.secondary)
                }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().background(if (isSelected) wa.accent.copy(alpha = 0.25f) else Color.Transparent).padding(vertical = 2.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            Bubble(m, mine, senderName, nameOf, onClick, onLongPress, onReplyClick)
            if (m.reactions.isNotEmpty() && !m.deleted) {
                val grouped = m.reactions.groupBy { it.emoji }
                Surface(shape = RoundedCornerShape(12.dp), color = wa.inputBar, shadowElevation = 1.dp, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        grouped.entries.joinToString("  ") { (e, l) -> if (l.size > 1) "$e ${l.size}" else e },
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bubble(
    m: Message,
    mine: Boolean,
    senderName: String?,
    nameOf: (String) -> String,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onReplyClick: (String) -> Unit,
) {
    val wa = LocalWa.current
    val shape = RoundedCornerShape(
        topStart = if (mine) 12.dp else 2.dp, topEnd = if (mine) 2.dp else 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp,
    )
    Surface(
        color = if (mine) wa.bubbleOut else wa.bubbleIn,
        shape = shape,
        shadowElevation = 1.dp,
        modifier = Modifier.padding(start = if (mine) 40.dp else 0.dp, end = if (mine) 0.dp else 40.dp)
            .widthIn(min = 72.dp, max = 320.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (senderName != null) {
                Text(senderName, color = colorFor(m.senderId), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            m.reply?.let { r ->
                Row(
                    Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.08f))
                        .clickable { onReplyClick(r.id) }.height(IntrinsicSize.Min),
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(colorFor(r.senderId)))
                    Column(Modifier.padding(6.dp)) {
                        Text(nameOf(r.senderId), color = colorFor(r.senderId), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (r.deleted) "Pesan dihapus" else when (r.type) {
                                "image" -> "📷 Foto"; "video" -> "🎥 Video"; "audio" -> "🎤 Pesan suara"; "file" -> "📎 Dokumen"
                                else -> r.body.orEmpty()
                            },
                            maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        )
                    }
                }
            }
            if (m.deleted) {
                Text("🚫 Pesan ini telah dihapus", fontStyle = FontStyle.Italic, color = wa.secondary, fontSize = 15.sp)
            } else {
                MediaBlock(m)
                if (!m.body.isNullOrBlank()) {
                    Text(m.body, fontSize = 16.sp, modifier = Modifier.padding(top = if (m.type == "text") 0.dp else 4.dp))
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.status == "failed") {
                    Text("Gagal. Ketuk untuk kirim ulang  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                if (m.editedAt != null && !m.deleted) Text("diedit  ", fontSize = 11.sp, color = wa.secondary)
                Text(formatTime(m.millis()), fontSize = 11.sp, color = wa.secondary)
                if (mine) {
                    Spacer(Modifier.width(3.dp))
                    Ticks(m.status)
                }
            }
        }
    }
}

@Composable
private fun PendingBox(width: Int, height: Int, failed: Boolean) {
    Box(
        Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        else CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun MediaBlock(m: Message) {
    val wa = LocalWa.current
    val failed = m.status == "failed"
    when (m.type) {
        "image" -> if (m.mediaId != null) {
            AsyncImage(
                model = Net.mediaUrl(m.mediaId), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else PendingBox(240, 240, failed)
        "video" -> Box(
            Modifier.size(240.dp, 150.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (m.mediaId != null) Icon(Icons.Filled.PlayArrow, "Putar", tint = Color.White, modifier = Modifier.size(52.dp))
            else if (failed) Icon(Icons.Filled.ErrorOutline, null, tint = Color.White)
            else CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Color.White)
        }
        "audio" -> if (m.mediaId != null) AudioPlayerRow(Net.mediaUrl(m.mediaId)) else PendingBox(200, 40, failed)
        "file" -> Row(Modifier.width(240.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.InsertDriveFile, null, tint = wa.secondary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(m.mediaFilename ?: "Dokumen", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(humanSize(m.mediaSize), fontSize = 12.sp, color = wa.secondary)
            }
        }
        else -> {}
    }
}

@Composable
private fun AudioPlayerRow(url: String) {
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player.value?.release()
            player.value = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player.value
            if (p != null && p.duration > 0) progress = p.currentPosition.toFloat() / p.duration
            delay(200)
        }
    }
    Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            val p = player.value
            if (p == null) {
                try {
                    val mp = MediaPlayer()
                    player.value = mp
                    mp.setDataSource(url)
                    mp.setOnPreparedListener { it.start(); playing = true }
                    mp.setOnCompletionListener {
                        playing = false
                        progress = 0f
                        it.release()
                        player.value = null
                    }
                    mp.setOnErrorListener { _, _, _ ->
                        playing = false
                        player.value = null
                        true
                    }
                    mp.prepareAsync()
                } catch (_: Exception) {
                    player.value = null
                }
            } else if (p.isPlaying) {
                p.pause(); playing = false
            } else {
                p.start(); playing = true
            }
        }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Putar") }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Mic, null, Modifier.size(18.dp), tint = LocalWa.current.secondary)
    }
}

@Composable
private fun ImageViewer(mediaId: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = Net.mediaUrl(mediaId), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.Close, "Tutup", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageInfoDialog(m: Message, onDismiss: () -> Unit) {
    val repo = ServiceLocator.repo
    var list by remember { mutableStateOf<List<Receipt>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(m.id) {
        repo.receipts(m.id).onSuccess { list = it }.onFailure { error = it.message }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info pesan") },
        text = {
            Column {
                when {
                    error != null -> Text(error.orEmpty())
                    list == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    else -> list.orEmpty().forEach { r ->
                        val line = when {
                            r.readAt != null -> "Dibaca ${formatTime(com.chatku.app.util.parseMillis(r.readAt))}"
                            r.deliveredAt != null -> "Terkirim ${formatTime(com.chatku.app.util.parseMillis(r.deliveredAt))}"
                            else -> "Belum terkirim"
                        }
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(r.displayName.ifBlank { "Penerima" }, fontWeight = FontWeight.Medium)
                            Text(line, fontSize = 13.sp, color = LocalWa.current.secondary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
