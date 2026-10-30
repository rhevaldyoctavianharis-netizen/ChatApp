@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatku.app.ServiceLocator
import com.chatku.app.data.Session
import com.chatku.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupInfoScreen(chatId: String, onBack: () -> Unit, onLeft: () -> Unit) {
    val repo = ServiceLocator.repo
    val scope = rememberCoroutineScope()
    val chats by repo.chats.collectAsState()
    val membersMap by repo.members.collectAsState()
    val me by Session.me.collectAsState()
    val snack = remember { SnackbarHostState() }
    val wa = LocalWa.current

    val chat = chats.firstOrNull { it.id == chatId }
    val members = membersMap[chatId] ?: chat?.members.orEmpty()
    val isAdmin = chat?.role == "admin"

    var editDialog by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(chatId) { repo.refreshChat(chatId) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = waTopBarColors(),
                title = { Text("Info grup") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = {
                    if (isAdmin) IconButton(onClick = { editDialog = true }) { Icon(Icons.Filled.Edit, "Ubah") }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(null, chat?.name ?: "Grup", 96.dp)
                    Text(chat?.name ?: "Grup", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text("${members.size} anggota", color = wa.secondary)
                    if (!chat?.description.isNullOrBlank()) Text(chat?.description.orEmpty(), Modifier.padding(top = 8.dp))
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Bisukan notifikasi")
                    Switch(checked = chat?.muted == true, onCheckedChange = { repo.setMuted(chatId, it) })
                }
                HorizontalDivider()
                if (isAdmin) ActionRow(Icons.Filled.PersonAdd, "Tambah anggota", { addDialog = true })
            }
            items(members, key = { it.id }) { u ->
                UserRow(
                    user = u,
                    subtitle = if (u.role == "admin") "Admin grup" else u.about,
                    trailing = {
                        if (isAdmin && u.id != me?.id) {
                            IconButton(onClick = { removeTarget = u }) { Icon(Icons.Filled.Close, "Keluarkan", tint = wa.secondary) }
                        }
                    },
                )
            }
            item {
                HorizontalDivider()
                Text(
                    "Keluar dari grup", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().clickable { confirmLeave = true }.padding(16.dp),
                )
            }
        }
    }

    if (editDialog) {
        var name by remember { mutableStateOf(chat?.name.orEmpty()) }
        var desc by remember { mutableStateOf(chat?.description.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editDialog = false },
            title = { Text("Ubah info grup") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nama grup") }, singleLine = true)
                    OutlinedTextField(desc, { desc = it.take(500) }, label = { Text("Deskripsi") }, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.updateGroup(chatId, name.trim(), desc.trim()).onFailure { snack.showSnackbar(it.message ?: "Gagal") }
                        editDialog = false
                    }
                }, enabled = name.isNotBlank()) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editDialog = false }) { Text("Batal") } },
        )
    }

    if (addDialog) {
        var q by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<User>>(emptyList()) }
        LaunchedEffect(q) {
            val s = q.trim()
            if (s.length >= 3) {
                delay(350)
                repo.searchUsers(s).onSuccess { r -> results = r.filter { u -> members.none { it.id == u.id } } }
            } else results = emptyList()
        }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Tambah anggota") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, label = { Text("Cari nama/nomor") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 260.dp).height(260.dp)) {
                        items(results, key = { it.id }) { u ->
                            UserRow(u, trailing = {
                                TextButton(onClick = {
                                    scope.launch {
                                        repo.addMembers(chatId, listOf(u.id))
                                            .onSuccess { results = results.filter { it.id != u.id } }
                                            .onFailure { snack.showSnackbar(it.message ?: "Gagal menambah") }
                                    }
                                }) { Text("Tambah") }
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addDialog = false }) { Text("Selesai") } },
        )
    }

    removeTarget?.let { u ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Keluarkan ${u.displayName}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.removeMember(chatId, u.id).onFailure { snack.showSnackbar(it.message ?: "Gagal") }
                        removeTarget = null
                    }
                }) { Text("Keluarkan") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Batal") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Keluar dari grup?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val id = me?.id ?: return@launch
                        repo.removeMember(chatId, id)
                            .onSuccess { confirmLeave = false; onLeft() }
                            .onFailure { snack.showSnackbar(it.message ?: "Gagal"); confirmLeave = false }
                    }
                }) { Text("Keluar") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Batal") } },
        )
    }
}
