@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chatku.app.ServiceLocator
import com.chatku.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit, onNewGroup: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ServiceLocator.repo
    val scope = rememberCoroutineScope()
    val contacts by repo.contacts.collectAsState()
    val snack = remember { SnackbarHostState() }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var syncing by remember { mutableStateOf(false) }

    fun sync() {
        scope.launch {
            syncing = true
            repo.syncContacts().onFailure { snack.showSnackbar(it.message ?: "Gagal sinkronisasi kontak") }
            syncing = false
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sync() else scope.launch { snack.showSnackbar("Izin kontak ditolak") }
    }

    LaunchedEffect(q) {
        val s = q.trim()
        if (s.length >= 3) {
            delay(350)
            repo.searchUsers(s).onSuccess { results = it }.onFailure { snack.showSnackbar(it.message ?: "Gagal mencari") }
        } else results = emptyList()
    }

    fun open(u: User) {
        scope.launch {
            repo.openDirect(u.id).onSuccess { onOpenChat(it.id) }.onFailure { snack.showSnackbar(it.message ?: "Gagal membuka chat") }
        }
    }

    val searching = q.trim().length >= 3
    val list = if (searching) results else contacts.filter { q.isBlank() || it.displayName.contains(q, ignoreCase = true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = waTopBarColors(),
                title = { Text("Chat baru") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    label = { Text("Cari nama atau nomor (min. 3 huruf)") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            item { ActionRow(Icons.Filled.Group, "Grup baru", onNewGroup) }
            item {
                ActionRow(Icons.Filled.Contacts, if (syncing) "Menyinkronkan kontak…" else "Cari teman dari kontak telepon", {
                    if (!syncing) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) sync()
                        else perm.launch(Manifest.permission.READ_CONTACTS)
                    }
                })
            }
            if (syncing) item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (list.isEmpty() && !syncing) {
                item {
                    Text(
                        if (searching) "Tidak ada pengguna ditemukan" else "Belum ada kontak. Cari berdasarkan nama/nomor atau sinkronkan kontak.",
                        Modifier.padding(16.dp), color = LocalWa.current.secondary,
                    )
                }
            }
            items(list, key = { it.id }) { u -> UserRow(u, onClick = { open(u) }) }
        }
    }
}
