package com.murickens.bulkwa

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*

class Store(ctx: Context) {
    private val p = EncryptedSharedPreferences.create(
        ctx, "wa_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun get(k: String, d: String = "") = p.getString(k, d) ?: d
    fun put(k: String, v: String) = p.edit().putString(k, v).apply()
}

data class Contact(val phone: String, val name: String)

/** Lines of "phone,name". Header rows and duplicates are skipped. */
fun parseContacts(text: String, cc: String): List<Contact> =
    text.lines().mapNotNull { line ->
        val parts = line.split(",", ";", "\t").map { it.trim() }
        var digits = parts.getOrNull(0).orEmpty().filter { it.isDigit() }.trimStart('0')
        if (digits.length < 7) return@mapNotNull null
        if (digits.length == 10) digits = cc + digits
        Contact(digits, parts.getOrNull(1).orEmpty())
    }.distinctBy { it.phone }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = Store(this)
        setContent { MaterialTheme { Surface { App(store) } } }
    }
}

@Composable
fun App(store: Store) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneId by remember { mutableStateOf(store.get("phoneId")) }
    var token by remember { mutableStateOf(store.get("token")) }
    var template by remember { mutableStateOf(store.get("template")) }
    var lang by remember { mutableStateOf(store.get("lang", "en")) }
    var cc by remember { mutableStateOf(store.get("cc", "91")) }
    var delaySec by remember { mutableStateOf(store.get("delay", "2")) }
    var useName by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var sent by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val log = remember { mutableStateListOf<String>() }
    var job by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contacts = ctx.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }.orEmpty()
        }
    }

    fun start() {
        store.put("phoneId", phoneId); store.put("token", token); store.put("template", template)
        store.put("lang", lang); store.put("cc", cc); store.put("delay", delaySec)
        val rows = parseContacts(contacts, cc.trim())
        if (rows.isEmpty() || phoneId.isBlank() || token.isBlank() || template.isBlank()) {
            log.add(0, "Fill Phone Number ID, token, template and at least one valid number."); return
        }
        sent = 0; failed = 0; total = rows.size; log.clear(); running = true
        val api = WhatsAppApi(phoneId.trim(), token.trim())
        val delayMs = ((delaySec.toDoubleOrNull() ?: 2.0).coerceAtLeast(0.5) * 1000).toLong()
        job = scope.launch(Dispatchers.IO) {
            for (c in rows) {
                if (!isActive) break
                val params = if (useName) listOf(c.name.ifBlank { "Customer" }) else emptyList()
                val res = api.sendTemplate(c.phone, template.trim(), lang.trim(), params)
                withContext(Dispatchers.Main) {
                    if (res.ok) sent++ else failed++
                    log.add(0, "${if (res.ok) "OK  " else "FAIL"} ${c.phone}  ${if (res.ok) "" else res.detail}")
                }
                delay(delayMs)
            }
            withContext(Dispatchers.Main) { running = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bulk WhatsApp Sender", style = MaterialTheme.typography.headlineSmall)
        Text("WhatsApp Business Cloud API. Send only to people who opted in.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(phoneId, { phoneId = it }, label = { Text("Phone Number ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text("Permanent access token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(template, { template = it }, label = { Text("Approved template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lang, { lang = it }, label = { Text("Lang") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(cc, { cc = it }, label = { Text("Country code") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(delaySec, { delaySec = it }, label = { Text("Delay (s)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(useName, { useName = it }); Spacer(Modifier.width(8.dp))
            Text("Send contact name as {{1}}")
        }
        OutlinedTextField(contacts, { contacts = it }, label = { Text("Contacts: phone,name (one per line)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "*/*")) }, enabled = !running) { Text("Import CSV") }
            if (!running) Button(onClick = { start() }) { Text("Start sending") }
            else Button(onClick = { job?.cancel(); running = false }) { Text("Stop") }
        }
        if (total > 0) {
            LinearProgressIndicator(progress = { (sent + failed).toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text("Sent $sent  |  Failed $failed  |  Total $total")
        }
        log.take(200).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
