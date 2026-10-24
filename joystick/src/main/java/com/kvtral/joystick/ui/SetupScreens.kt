package com.kvtral.joystick.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kvtral.joystick.auth.DeviceCode
import com.kvtral.joystick.xhome.XboxConsole

@Composable
fun LoginScreen(state: Screen.Login, onLogin: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.SportsEsports, null,
                Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary,
            )
            Text("Mando Xbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Usa el móvil como mando de tu Xbox a través de Remote Play. " +
                    "Inicia sesión con la misma cuenta Microsoft que usas en la consola.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            when {
                state.code != null -> DeviceCodeBox(state.code, context)
                state.busy -> CircularProgressIndicator()
                else -> Button(onClick = onLogin) { Text("Iniciar sesión con Microsoft") }
            }
        }
    }
}

@Composable
private fun DeviceCodeBox(code: DeviceCode, context: Context) {
    Text("1. Abre ${code.verificationUri.removePrefix("https://")} en cualquier navegador")
    Text("2. Escribe este código:")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            code.userCode,
            fontSize = 34.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("código", code.userCode))
        }) { Icon(Icons.Default.ContentCopy, "Copiar código") }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("código", code.userCode))
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)))
    }) { Text("Copiar código y abrir navegador") }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Esperando a que confirmes…", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsolesScreen(
    state: Screen.Consoles,
    gamertag: String?,
    onRefresh: () -> Unit,
    onConnect: (XboxConsole) -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gamertag?.let { "Consolas de $it" } ?: "Tus consolas") },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar") }
                    IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.consoles.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay consolas con Remote Play activado en esta cuenta.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    RemotePlayHelp()
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRefresh) { Text("Volver a buscar") }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.consoles, key = { it.serverId }) { console ->
                    ConsoleCard(console, onConnect)
                }
                item { RemotePlayHelp() }
            }
        }
    }
}

@Composable
private fun ConsoleCard(console: XboxConsole, onConnect: (XboxConsole) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SportsEsports, null, Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(console.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(consoleTypeName(console.consoleType), powerStateName(console.powerState))
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = { onConnect(console) }) { Text("Conectar") }
        }
    }
}

@Composable
private fun RemotePlayHelp() {
    Text(
        "En la Xbox: Ajustes › Dispositivos y conexiones › Funciones remotas › activa \"Habilitar funciones remotas\" " +
            "y, en Modo de energía, elige \"Inicio inmediato\" o \"Suspensión\" para poder despertarla desde el móvil.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

private fun consoleTypeName(type: String) = when (type) {
    "XboxOne" -> "Xbox One"
    "XboxOneS" -> "Xbox One S"
    "XboxOneX" -> "Xbox One X"
    "XboxSeriesS" -> "Xbox Series S"
    "XboxSeriesX" -> "Xbox Series X"
    else -> type
}

private fun powerStateName(state: String) = when (state) {
    "On" -> "Encendida"
    "ConnectedStandby" -> "En suspensión"
    "Off" -> "Apagada"
    else -> state
}
