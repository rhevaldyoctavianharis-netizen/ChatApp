@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatku.app.ServiceLocator
import com.chatku.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewGroupScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val repo = ServiceLocator.repo
    val scope = rememberCoroutineScope()
    val contacts by repo.contacts.collectAsState()
    val snack = remember { SnackbarHostState() }
    val selected = remember { mutableStateMapOf<String, User>() }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf("") }

    LaunchedEffect(q) {
        val s = q.trim()
        if (s.length >= 3) {
            delay(350)
            repo.searchUsers(s).onSuccess { results = it }
        } else results = emptyList()
    }

    val candidates = (selected.values.toList() + results + contacts).distinctBy { it.id }
        .filter { q.trim().length < 3 || results.any { r -> r.id == it.id } || selected.containsKey(it.id) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = waTopBarColors(),
                title = { Text("Grup baru") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
        bottomBar = {
            Button(
                enabled = !creating && name.isNotBlank() && (isPublic || selected.isNotEmpty()),
                onClick = {
                    scope.launch {
                        creating = true
                        repo.createGroup(name.trim(), desc.trim().ifBlank { null }, selected.keys.toList(), isPublic, handle.trim().lowercase())
                            .onSuccess { onCreated(it.id) }
                            .onFailure { snack.showSnackbar(it.message ?: "Gagal membuat grup") }
                        creating = false
                    }
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            ) { Text(if (creating) "Membuat…" else "Buat grup (${selected.size} anggota)") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Nama grup") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it.take(500) }, label = { Text("Deskripsi (opsional)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Publikasikan di direktori grup")
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
            }
            if (isPublic) {
                OutlinedTextField(
                    value = handle, onValueChange = { handle = it.filter { c -> c.isLowerCase() || c.isDigit() || c == '_' }.take(40) },
                    singleLine = true, label = { Text("Handle publik (contoh: komunitas_kotlin)") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, label = { Text("Cari anggota (min. 3 huruf)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (candidates.isEmpty()) {
                Text(
                    "Cari pengguna atau sinkronkan kontak lewat menu Chat baru.",
                    Modifier.padding(16.dp), color = LocalWa.current.secondary,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(candidates, key = { it.id }) { u ->
                    val checked = selected.containsKey(u.id)
                    val toggle = { if (checked) selected.remove(u.id) else selected[u.id] = u; Unit }
                    UserRow(u, onClick = toggle, trailing = { Checkbox(checked = checked, onCheckedChange = { toggle() }) })
                }
            }
        }
    }
}
