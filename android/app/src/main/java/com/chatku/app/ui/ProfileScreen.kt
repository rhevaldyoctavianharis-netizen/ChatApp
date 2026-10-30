@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatku.app.ServiceLocator
import com.chatku.app.data.Net
import com.chatku.app.data.Session
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.repo
    val scope = rememberCoroutineScope()
    val me by Session.me.collectAsState()
    val snack = remember { SnackbarHostState() }
    var name by remember(me?.displayName) { mutableStateOf(me?.displayName.orEmpty()) }
    var about by remember(me?.about) { mutableStateOf(me?.about.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            repo.uploadAvatar(uri)
                .onSuccess { snack.showSnackbar("Foto profil diperbarui") }
                .onFailure { snack.showSnackbar(it.message ?: "Gagal mengunggah foto") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = waTopBarColors(),
                title = { Text("Profil") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(me?.avatarMediaId, me?.displayName ?: "?", 110.dp)
                Text("Ketuk untuk ganti foto", Modifier.padding(top = 8.dp), color = LocalWa.current.secondary)
            }
            Spacer(Modifier.height(16.dp))
            Text(me?.phone.orEmpty(), color = LocalWa.current.secondary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(50) }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(about, { about = it.take(140) }, label = { Text("Info") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !saving && name.isNotBlank(),
                onClick = {
                    scope.launch {
                        saving = true
                        repo.updateProfile(name.trim(), about.trim())
                            .onSuccess { snack.showSnackbar("Profil disimpan") }
                            .onFailure { snack.showSnackbar(it.message ?: "Gagal menyimpan") }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Menyimpan…" else "Simpan") }
            Spacer(Modifier.height(24.dp))
            Text("Server: ${Net.baseUrl}", color = LocalWa.current.secondary)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { Session.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text("Keluar", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
