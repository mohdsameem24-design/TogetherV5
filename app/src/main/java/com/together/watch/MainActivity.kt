package com.together.watch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val sender: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) =
        super.onCreate(savedInstanceState).also {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    TogetherV5App()
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherV5App() {
    var room by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var joined by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Offline") }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var applyingRemote by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(false) }

    var serverUrl by remember {
        mutableStateOf("wss://together-sync-server.onrender.com/ws")
    }

    var onlineUrl by remember { mutableStateOf("") }
    var partnerUrl by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                selected = it
                player.setMediaItem(MediaItem.fromUri(it))
                player.prepare()
            }
        }

    DisposableEffect(Unit) {
        onDispose {
            socket?.close(1000, "bye")
            player.release()
        }
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return if (
            t.startsWith("http://") ||
            t.startsWith("https://")
        ) t else "https://$t"
    }

    fun openBrowser(url: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(normalizeUrl(url))
                )
            )
        }
    }

    fun send(type: String, text: String? = null) {
        if (!joined || applyingRemote) return

        val o = JSONObject()
            .put("type", type)
            .put("position", player.currentPosition)
            .put("sender", name.ifBlank { "Guest" })

        if (text != null) o.put("text", text)

        socket?.send(o.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Together V5") })
        }
    ) { pad ->

        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (!joined) {

                Text(
                    "Private watch room for two people in different places.",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    name,
                    { name = it.take(20) },
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    room,
                    {
                        room = it.filter(Char::isLetterOrDigit)
                            .uppercase()
                            .take(6)
                    },
                    label = { Text("Private room code") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    serverUrl,
                    { serverUrl = it.trim() },
                    label = { Text("Sync server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = {
                            room = (1..6)
                                .map {
                                    "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                                        .random()
                                }
                                .joinToString("")
                        }
                    ) {
                        Text("Create Room")
                    }

                    Button(
                        enabled = room.length == 6,
                        onClick = {

                            val client =
                                OkHttpClient.Builder()
                                    .pingInterval(
                                        20,
                                        TimeUnit.SECONDS
                                    )
                                    .build()

                            val request =
                                Request.Builder()
                                    .url(
                                        "${serverUrl.trimEnd('/')}?room=$room"
                                    )
                                    .build()

                            socket =
                                client.newWebSocket(
                                    request,
                                    object :
                                        WebSocketListener() {

                                        override fun onOpen(
                                            ws: WebSocket,
                                            r: Response
                                        ) {
                                            status = "Connected"
                                        }

                                        override fun onMessage(
                                            ws: WebSocket,
                                            text: String
                                        ) {
                                            val o =
                                                JSONObject(text)

                                            when (
                                                o.optString("type")
                                            ) {

                                                "chat" ->
                                                    messages =
                                                        messages +
                                                            ChatMessage(
                                                                o.optString(
                                                                    "sender",
                                                                    "Partner"
                                                                ),
                                                                o.optString(
                                                                    "text"
                                                                )
                                                            )

                                                "browser_url" ->
                                                    partnerUrl =
                                                        o.optString(
                                                            "text"
                                                        )

                                                "countdown" ->
                                                    messages =
                                                        messages +
                                                            ChatMessage(
                                                                "Together",
                                                                "Partner says: 3… 2… 1… PLAY ▶"
                                                            )

                                                "play",
                                                "pause",
                                                "seek" -> {
                                                    applyingRemote =
                                                        true

                                                    player.seekTo(
                                                        o.optLong(
                                                            "position",
                                                            player.currentPosition
                                                        )
                                                    )

                                                    if (
                                                        o.optString(
                                                            "type"
                                                        ) == "play"
                                                    ) {
                                                        player.play()
                                                    } else if (
                                                        o.optString(
                                                            "type"
                                                        ) == "pause"
                                                    ) {
                                                        player.pause()
                                                    }

                                                    applyingRemote =
                                                        false
                                                }
                                            }
                                        }

                                        override fun onFailure(
                                            ws: WebSocket,
                                            t: Throwable,
                                            r: Response?
                                        ) {
                                            status =
                                                "Connection failed"
                                        }
                                    }
                                )

                            joined = true
                            status = "Connecting…"
                        }
                    ) {
                        Text("Join")
                    }
                }

                Text("Status: $status")

            } else {

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Text(
                        "Room $room • $status",
                        style =
                            MaterialTheme.typography
                                .titleMedium
                    )

                    FilledTonalButton(
                        onClick = {
                            voiceEnabled =
                                !voiceEnabled
                        }
                    ) {
                        Icon(
                            Icons.Default.Call,
                            null
                        )
                        Spacer(
                            Modifier.width(6.dp)
                        )
                        Text("Voice")
                    }
                }

                TabRow(selectedTabIndex = tab) {
                    Tab(
                        tab == 0,
                        { tab = 0 },
                        text = {
                            Text("Local Movie")
                        }
                    )

                    Tab(
                        tab == 1,
                        { tab = 1 },
                        text = {
                            Text("Online / Browser")
                        }
                    )
                }

                if (tab == 0) {

                    Button(
                        onClick = {
                            picker.launch(
                                arrayOf("video/*")
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selected == null)
                                "Choose Movie"
                            else
                                "Change Movie"
                        )
                    }

                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                player.play()
                                send("play")
                            }
                        ) {
                            Text("▶ Together")
                        }

                        Button(
                            onClick = {
                                player.pause()
                                send("pause")
                            }
                        ) {
                            Text("⏸ Pause")
                        }

                        FilledTonalButton(
                            onClick = {
                                send("seek")
                            }
                        ) {
                            Text("Sync")
                        }
                    }

                } else {

                    Text(
                        "Open the same legal streaming/movie page in Chrome, Samsung Internet, Firefox, Edge, or your default browser."
                    )

                    OutlinedTextField(
                        onlineUrl,
                        { onlineUrl = it },
                        label = {
                            Text(
                                "Movie / streaming page URL"
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        enabled =
                            onlineUrl.isNotBlank(),
                        onClick = {
                            val u =
                                normalizeUrl(
                                    onlineUrl
                                )

                            send(
                                "browser_url",
                                u
                            )

                            openBrowser(u)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            null
                        )

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        Text(
                            "Send link + Open Browser"
                        )
                    }

                    partnerUrl?.let { u ->

                        Card(
                            Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(
                                    12.dp
                                )
                            ) {
                                Text(
                                    "Partner shared a watch link"
                                )

                                Text(u)

                                Button(
                                    onClick = {
                                        openBrowser(u)
                                    }
                                ) {
                                    Text(
                                        "Open in my browser"
                                    )
                                }
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            send("countdown")

                            messages =
                                messages +
                                    ChatMessage(
                                        "Together",
                                        "3… 2… 1… PLAY ▶"
                                    )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "3-2-1 Start Together"
                        )
                    }

                    Spacer(
                        Modifier.weight(1f)
                    )

                    Text(
                        "Browser mode coordinates the link and start time. Playback inside arbitrary browsers or DRM-protected streaming services cannot be remotely controlled by Together.",
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }

                if (voiceEnabled) {
                    Text(
                        "Voice calling is not enabled in this build yet.",
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }

                HorizontalDivider()

                Text(
                    "Chat",
                    style =
                        MaterialTheme.typography
                            .titleMedium
                )

                LazyColumn(
                    Modifier
                        .heightIn(max = 110.dp)
                        .fillMaxWidth()
                ) {
                    items(messages) {
                        Text(
                            "${it.sender}: ${it.text}",
                            modifier =
                                Modifier.padding(
                                    vertical = 2.dp
                                )
                        )
                    }
                }

                Row {

                    OutlinedTextField(
                        message,
                        { message = it },
                        placeholder = {
                            Text(
                                "Message your partner"
                            )
                        },
                        modifier =
                            Modifier.weight(1f),
                        singleLine = true
                    )

                    IconButton(
                        enabled =
                            message.isNotBlank(),
                        onClick = {
                            val m =
                                message.trim()

                            messages =
                                messages +
                                    ChatMessage(
                                        name.ifBlank {
                                            "Me"
                                        },
                                        m
                                    )

                            send(
                                "chat",
                                m
                            )

                            message = ""
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            "Send"
                        )
                    }
                }
            }
        }
    }
}
