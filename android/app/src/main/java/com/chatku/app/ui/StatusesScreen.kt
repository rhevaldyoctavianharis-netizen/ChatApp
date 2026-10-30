@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatku.app.data.CreateStatusBody
import com.chatku.app.data.Net
import com.chatku.app.data.Status
import com.chatku.app.data.safe
import kotlinx.coroutines.launch

@Composable
fun StatusesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<List<Status>>(emptyList()) }
    var composer by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Status?>(null) }

    fun reload() {
        scope.launch { safe { Net.api.statuses() }.onSuccess { statuses = it } }
    }
    LaunchedEffect(Unit) { reload() }

    selected?.let { status ->
        StatusViewer(status, onClose = { selected = null }) {
            scope.launch { safe { Net.api.viewStatus(status.id) } }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Status") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { composer = true },
                containerColor = LocalWa.current.accent,
                contentColor = Color.White,
            ) { Icon(Icons.Filled.Add, "Status baru") }
        },
    ) { padding ->
        if (statuses.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(56.dp), tint = LocalWa.current.accent)
                Text("Belum ada status", modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
                Text("Bagikan kabar singkat selama 24 jam", color = LocalWa.current.secondary)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(statuses, key = { it.id }) { status ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { selected = status },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        tonalElevation = 2.dp,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(54.dp).clip(CircleShape).background(
                                    Brush.linearGradient(listOf(LocalWa.current.accent, Color(0xFF8B5CF6))),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Avatar(status.avatarMediaId, status.displayName, 48.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(status.displayName, fontWeight = FontWeight.SemiBold)
                                Text(status.body, maxLines = 1, color = LocalWa.current.secondary)
                            }
                            Text(if (status.viewed) "Dilihat" else "Baru", color = LocalWa.current.accent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if (composer) {
        StatusComposer(
            onDismiss = { composer = false },
            onCreated = { status ->
                composer = false
                statuses = listOf(status) + statuses
            },
        )
    }
}

@Composable
private fun StatusComposer(onDismiss: () -> Unit, onCreated: (Status) -> Unit) {
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status baru") },
        text = {
            Column {
                Text("Tersedia selama 24 jam", color = LocalWa.current.secondary, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(body, { body = it.take(700) }, label = { Text("Tulis sesuatu") }, minLines = 4)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    safe { Net.api.createStatus(CreateStatusBody(body.trim())) }
                        .onSuccess(onCreated)
                        .onFailure { error = it.message ?: "Gagal membuat status" }
                }
            }, enabled = body.isNotBlank()) { Text("Bagikan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun StatusViewer(status: Status, onClose: () -> Unit, onViewed: () -> Unit) {
    LaunchedEffect(status.id) { onViewed() }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF18212B), Color(0xFF071014))),
        ).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(24.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(status.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                Text(status.body, color = Color.White, modifier = Modifier.padding(top = 18.dp))
                Text("Ketuk untuk menutup", color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 28.dp))
            }
        }
    }
}
