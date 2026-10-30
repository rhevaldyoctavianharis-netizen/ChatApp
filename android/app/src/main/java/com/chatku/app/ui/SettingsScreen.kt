package com.chatku.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatku.app.data.Appearance

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val dark by Appearance.dark
    val glass by Appearance.glass
    Scaffold(topBar = {
        TopAppBar(title = { Text("Pengaturan tampilan") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Personalisasi", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mode gelap")
                Switch(checked = dark, onCheckedChange = Appearance::setDark)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Efek glass/transparan")
                Switch(checked = glass, onCheckedChange = Appearance::setGlass)
            }
            Text("Warna aksen", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(0xFF25D366, 0xFF6750A4, 0xFF007AFF, 0xFFFF6B6B).forEach { color ->
                    Button(onClick = { Appearance.setAccent(color) }, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(color))) { Text(" ") }
                }
            }
        }
    }
}
