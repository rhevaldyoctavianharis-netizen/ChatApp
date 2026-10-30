package com.chatku.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatku.app.data.LoginBody
import com.chatku.app.data.Net
import com.chatku.app.data.RegisterBody
import com.chatku.app.data.Session
import com.chatku.app.data.safe
import com.chatku.app.util.normalizePhone
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(Session.baseUrl) }
    var showServer by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wa = LocalWa.current

    fun submit() {
        scope.launch {
            error = null
            val p = normalizePhone(phone)
            if (p == null) { error = "Nomor telepon tidak valid"; return@launch }
            if (register && name.isBlank()) { error = "Nama wajib diisi"; return@launch }
            if (password.length < 6) { error = "Password minimal 6 karakter"; return@launch }
            loading = true
            val url = Net.normalizeBaseUrl(server)
            if (url.isNotEmpty() && url != Net.baseUrl) {
                Session.baseUrl = url
                Net.configure(url)
            }
            val r = safe {
                if (register) Net.api.register(RegisterBody(p, name.trim(), password))
                else Net.api.login(LoginBody(p, password))
            }
            r.onSuccess { Session.save(it.token, it.user) }.onFailure { error = it.message }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ChatKu", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = wa.accent)
        Text(
            if (register) "Buat akun baru" else "Masuk untuk mulai mengobrol",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = phone, onValueChange = { phone = it }, label = { Text("Nomor telepon") },
            placeholder = { Text("+62812xxxxxxx") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (register) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Nama") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { submit() }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(if (register) "Daftar" else "Masuk")
        }
        TextButton(onClick = { register = !register; error = null }) {
            Text(if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar")
        }
        TextButton(onClick = { showServer = !showServer }) { Text("Pengaturan server") }
        if (showServer) {
            OutlinedTextField(
                value = server, onValueChange = { server = it }, label = { Text("URL server (Cloudflare)") },
                placeholder = { Text("https://chat.domainanda.com") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
