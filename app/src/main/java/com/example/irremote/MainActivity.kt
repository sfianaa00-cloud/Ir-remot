package com.example.irremote

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keys copied from the ILK remote, top to bottom. Codes are NOT known; assign them in Setup. */
private val KEYS = listOf(
    "Power", "Home", "Mouse",
    "App", "Hotstar", "Prime",
    "Setting", "YouTube", "Browser",
    "Freeze", "Netflix", "Miracast",
    "1", "2", "3",
    "4", "5", "6",
    "7", "8", "9",
    "-/--", "0", "Recall",
    "Ch+", "Source", "Ch-",
    "Smart S", "Smart P", "Menu",
    "Up", "Down", "Left",
    "Right", "OK", "Exit",
    "Vol+", "Rec", "Vol-",
    "Prev", "Play/Pause", "Next",
    "Rew", "Stop", "Fwd",
    "Red", "Green", "Yellow",
    "Info", "Audio", "PlayStore",
    "Sleep", "Aspect", "Fav"
)

private class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("ir", Context.MODE_PRIVATE)
    var address: Int
        get() = p.getInt("address", 0)
        set(v) { p.edit().putInt("address", v).apply() }
    var protocol: Protocol
        get() = runCatching { Protocol.valueOf(p.getString("protocol", "NEC") ?: "NEC") }.getOrDefault(Protocol.NEC)
        set(v) { p.edit().putString("protocol", v.name).apply() }

    fun command(key: String): Int? = if (p.contains("cmd_$key")) p.getInt("cmd_$key", 0) else null
    fun setCommand(key: String, v: Int) { p.edit().putInt("cmd_$key", v).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sender = IrSender(this)
        val store = Store(this)
        setContent { MaterialTheme { Root(sender, store) } }
    }
}

@Composable
private fun Root(sender: IrSender, store: Store) {
    var tab by remember { mutableIntStateOf(0) }
    var address by remember { mutableIntStateOf(store.address) }
    var protocol by remember { mutableStateOf(store.protocol) }
    var version by remember { mutableIntStateOf(0) } // bump to refresh after saving a code

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!sender.available) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "This phone has no infrared emitter, so it cannot control a TV. " +
                            "Use a phone with an IR blaster or a USB/audio-jack IR dongle.",
                        Modifier.padding(12.dp)
                    )
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Remote") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Setup") })
            }
            if (tab == 0) {
                RemoteScreen(sender, store, address, protocol, version)
            } else {
                SetupScreen(
                    sender, store, address, protocol,
                    onAddress = { address = it; store.address = it },
                    onProtocol = { protocol = it; store.protocol = it },
                    onSaved = { version++ }
                )
            }
        }
    }
}

@Composable
private fun RemoteScreen(sender: IrSender, store: Store, address: Int, protocol: Protocol, version: Int) {
    @Suppress("UNUSED_VARIABLE") val v = version // read so this recomposes after a code is assigned
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val cmd = store.command(key)
                    Button(
                        onClick = { cmd?.let { sender.send(protocol, address, it) } },
                        enabled = cmd != null && sender.available,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                    ) { Text(key, maxLines = 1) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            "Greyed-out keys have no code yet. Open Setup to find them. Protocol: ${protocol.label}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SetupScreen(
    sender: IrSender, store: Store, address: Int, protocol: Protocol,
    onAddress: (Int) -> Unit, onProtocol: (Protocol) -> Unit, onSaved: () -> Unit
) {
    var addrText by remember { mutableStateOf("%02X".format(address)) }
    var cmd by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(KEYS.first()) }
    var keyMenu by remember { mutableStateOf(false) }
    var protoMenu by remember { mutableStateOf(false) }
    val count = protocol.maxCommand + 1

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("1. Protocol and address", style = MaterialTheme.typography.titleMedium)
        Text(
            "Try each protocol in turn. For every protocol, step through commands and watch the TV. " +
                "Address is hex. Good first tries: 00, then 01, 04, 10, 20, 40, 80, FF. " +
                "Sony and RC5 use small addresses (1 = TV for both).",
            style = MaterialTheme.typography.bodySmall
        )
        Box {
            OutlinedButton({ protoMenu = true }) { Text("Protocol: ${protocol.label}") }
            DropdownMenu(protoMenu, { protoMenu = false }) {
                Protocol.values().forEach { pr ->
                    DropdownMenuItem(
                        text = { Text(pr.label) },
                        onClick = {
                            onProtocol(pr)
                            cmd = cmd.coerceAtMost(pr.maxCommand)
                            val a = (addrText.toIntOrNull(16) ?: 0).coerceAtMost(pr.maxAddress)
                            addrText = "%02X".format(a)
                            onAddress(a)
                            protoMenu = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = addrText,
            onValueChange = { t ->
                addrText = t.uppercase().filter { it in "0123456789ABCDEF" }.take(4)
                addrText.toIntOrNull(16)?.let { onAddress(it.coerceAtMost(protocol.maxAddress)) }
            },
            label = { Text("Address (hex, max %X)".format(protocol.maxAddress)) },
            singleLine = true
        )

        HorizontalDivider()
        Text("2. Find a working command", style = MaterialTheme.typography.titleMedium)
        Text(
            "Point the phone's IR window at the TV from 1 to 2 m. Press Send and watch for a reaction " +
                "(standby LED, volume bar). Commands run 0 to ${protocol.maxCommand}.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Command: 0x%02X  (%d)".format(cmd, cmd), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ cmd = (cmd + count - 1) % count }) { Text("Prev") }
            Button({ sender.send(protocol, address, cmd) }, enabled = sender.available) { Text("Send") }
            OutlinedButton({ cmd = (cmd + 1) % count }) { Text("Next") }
        }
        OutlinedButton({
            sender.send(protocol, address, cmd)
            cmd = (cmd + 1) % count
        }, enabled = sender.available) { Text("Send + Next") }

        HorizontalDivider()
        Text("3. Assign it to a key", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton({ keyMenu = true }) { Text("Key: $target") }
            DropdownMenu(keyMenu, { keyMenu = false }) {
                KEYS.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(k + (store.command(k)?.let { "  (0x%02X)".format(it) } ?: "")) },
                        onClick = { target = k; keyMenu = false }
                    )
                }
            }
        }
        Button({ store.setCommand(target, cmd); onSaved() }) {
            Text("Save 0x%02X as %s".format(cmd, target))
        }
    }
}
