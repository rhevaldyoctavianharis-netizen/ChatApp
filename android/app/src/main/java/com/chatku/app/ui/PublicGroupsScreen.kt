@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatku.app.data.Net
import com.chatku.app.data.PublicGroup
import com.chatku.app.data.safe
import kotlinx.coroutines.launch

@Composable
fun PublicGroupsScreen(onBack: () -> Unit, onJoined: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<PublicGroup>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun load() { scope.launch { safe { Net.api.publicGroups(query) }.onSuccess { groups = it }.onFailure { error = it.message } } }
    LaunchedEffect(Unit) { load() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Temukan grup & komunitas") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Cari grup publik") },
                trailingIcon = { IconButton(onClick = { load() }) { Icon(Icons.Filled.Search, "Cari") } },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(groups, key = { it.id }) { group ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Text("@${group.publicHandle ?: "publik"} · ${group.memberCount} anggota", style = MaterialTheme.typography.labelMedium)
                            if (!group.description.isNullOrBlank()) Text(group.description.orEmpty(), modifier = Modifier.padding(top = 4.dp))
                            Button(onClick = {
                                scope.launch { safe { Net.api.joinPublicGroup(group.id) }.onSuccess { onJoined(it.id) }.onFailure { error = it.message } }
                            }, modifier = Modifier.padding(top = 8.dp)) { Text("Gabung") }
                        }
                    }
                }
            }
        }
    }
}
