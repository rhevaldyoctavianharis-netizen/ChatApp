@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatku.app.ServiceLocator
import com.chatku.app.data.Chat
import com.chatku.app.data.Session
import com.chatku.app.data.avatarId
import com.chatku.app.data.title
import com.chatku.app.util.listTime

@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onProfile: () -> Unit,
    onChannels: () -> Unit,
    onStatuses: () -> Unit,
) {
    val repo = ServiceLocator.repo
    val chats by repo.chats.collectAsState()
    val typing by repo.typing.collectAsState()
    val connected by repo.socket.connected.collectAsState()
    val refreshing by repo.refreshing.collectAsState()
    val me by Session.me.collectAsState()
    val wa = LocalWa.current
    val snack = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { repo.errors.collect { snack.showSnackbar(it) } }

    val shown = if (query.isBlank()) chats else chats.filter { it.title().contains(query, ignoreCase = true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = waTopBarColors(),
                navigationIcon = {
                    if (searching) IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Cari…", color = Color.White.copy(alpha = 0.7f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                            ),
                        )
                    } else {
                        Column {
                            Text("ChatKu", fontWeight = FontWeight.Bold)
                            if (!connected) Text("Menyambungkan…", fontSize = 12.sp, color = wa.onTopBar.copy(alpha = 0.8f))
                        }
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Cari") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Grup baru") }, onClick = { menu = false; onNewGroup() })
                                DropdownMenuItem(text = { Text("Channels") }, onClick = { menu = false; onChannels() })
                                DropdownMenuItem(text = { Text("Status") }, onClick = { menu = false; onStatuses() })
                                DropdownMenuItem(
                                    text = { Text("Profil (${me?.displayName.orEmpty()})") },
                                    onClick = { menu = false; onProfile() },
                                )
                                DropdownMenuItem(text = { Text("Keluar") }, onClick = { menu = false; Session.logout() })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat, containerColor = wa.accent, contentColor = Color.White) {
                Icon(Icons.Filled.Chat, "Chat baru")
            }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { repo.refresh() },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Belum ada percakapan.\nKetuk tombol hijau untuk memulai chat."
                        else "Tidak ada hasil",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { chat ->
                        ChatRow(chat, typing[chat.id].orEmpty().isNotEmpty(), me?.id) { onOpenChat(chat.id) }
                        HorizontalDivider(Modifier.padding(start = 80.dp), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, isTyping: Boolean, myId: String?, onClick: () -> Unit) {
    val wa = LocalWa.current
    val last = chat.lastMessage
    val preview = when {
        isTyping -> "mengetik…"
        last == null -> chat.description.orEmpty()
        last.deleted -> "🚫 Pesan ini telah dihapus"
        else -> {
            val core = when (last.type) {
                "image" -> "📷 Foto" + (last.body?.let { " $it" } ?: "")
                "video" -> "🎥 Video"
                "audio" -> "🎤 Pesan suara"
                "file" -> "📎 Dokumen"
                else -> last.body.orEmpty()
            }
            if (last.senderId == myId) "Anda: $core" else core
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.avatarId(), chat.title(), 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 17.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    listTime(chat.lastActivity ?: last?.createdAt), fontSize = 12.sp,
                    color = if (chat.unread > 0) wa.accent else wa.secondary,
                )
            }
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    preview, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    color = if (isTyping) wa.accent else wa.secondary,
                )
                if (chat.muted) Icon(Icons.Filled.VolumeOff, null, Modifier.size(16.dp), tint = wa.secondary)
                if (chat.unread > 0) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(wa.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (chat.unread > 99) "99+" else chat.unread.toString(),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
