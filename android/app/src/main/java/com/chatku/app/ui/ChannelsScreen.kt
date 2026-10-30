@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatku.app.data.Channel
import com.chatku.app.data.ChannelPost
import com.chatku.app.data.CreateChannelBody
import com.chatku.app.data.CreateChannelPostBody
import com.chatku.app.data.Net
import com.chatku.app.data.safe
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ChannelsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch { safe { Net.api.channels() }.onSuccess { channels = it } }
    }
    LaunchedEffect(Unit) { reload() }

    if (selected != null) {
        ChannelDetailScreen(selected!!, onBack = { selected = null }) {
            reload()
            selected = channels.firstOrNull { it.id == selected?.id } ?: selected
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }, containerColor = LocalWa.current.accent) {
                Icon(Icons.Filled.Add, "Buat channel")
            }
        },
    ) { padding ->
        if (channels.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Campaign, null, Modifier.size(56.dp), tint = LocalWa.current.secondary)
                Text("Belum ada channel", modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(channels, key = { it.id }) { channel ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = channel }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(channel.avatarMediaId, channel.name, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(channel.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("@${channel.handle} · ${channel.followers} pengikut", color = LocalWa.current.secondary, fontSize = 13.sp)
                            if (channel.description.isNotBlank()) Text(channel.description, maxLines = 1, color = LocalWa.current.secondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateChannelDialog(
            onDismiss = { showCreate = false },
            onCreated = { channel ->
                showCreate = false
                channels = listOf(channel) + channels
            },
        )
    }
}

@Composable
private fun CreateChannelDialog(onDismiss: () -> Unit, onCreated: (Channel) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(handle, { handle = it.lowercase().replace(" ", "_") }, label = { Text("Handle") }, prefix = { Text("@") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Deskripsi") }, maxLines = 3)
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    safe { Net.api.createChannel(CreateChannelBody(name.trim(), handle.trim(), description.trim())) }
                        .onSuccess(onCreated)
                        .onFailure { error = it.message ?: "Gagal membuat channel" }
                }
            }) { Text("Buat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ChannelDetailScreen(channel: Channel, onBack: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<ChannelPost>>(emptyList()) }
    var compose by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(channel.following) }
    var body by remember { mutableStateOf("") }
    LaunchedEffect(channel.id) { safe { Net.api.channelPosts(channel.id) }.onSuccess { posts = it } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channel.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            if (following) safe { Net.api.unfollowChannel(channel.id) } else safe { Net.api.followChannel(channel.id) }
                            following = !following
                            onChanged()
                        }
                    }) { Text(if (following) "Mengikuti" else "Ikuti") }
                },
            )
        },
        floatingActionButton = {
            if (channel.ownerId == com.chatku.app.data.Session.me.value?.id) {
                FloatingActionButton(onClick = { compose = true }, containerColor = LocalWa.current.accent) {
                    Icon(Icons.Filled.Send, "Posting")
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Avatar(channel.avatarMediaId, channel.name, 72.dp)
                    Text("@${channel.handle}", color = LocalWa.current.secondary, modifier = Modifier.padding(top = 8.dp))
                    if (channel.description.isNotBlank()) Text(channel.description, modifier = Modifier.padding(top = 4.dp))
                }
            }
            items(posts, key = { it.id }) { post ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(post.body, fontSize = 16.sp)
                    Text(post.createdAt.replace("T", " ").take(16), color = LocalWa.current.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    if (compose) {
        AlertDialog(
            onDismissRequest = { compose = false },
            title = { Text("Posting baru") },
            text = { OutlinedTextField(body, { body = it }, label = { Text("Pesan") }, minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        safe { Net.api.createChannelPost(channel.id, CreateChannelPostBody(body)) }
                            .onSuccess { posts = listOf(it) + posts; body = ""; compose = false }
                    }
                }) { Text("Kirim") }
            },
            dismissButton = { TextButton(onClick = { compose = false }) { Text("Batal") } },
        )
    }
}
