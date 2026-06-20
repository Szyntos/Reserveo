package org.julsz.smnt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    client: HttpClient,
    onLogin: (UserDto) -> Unit,
    serverMode: String = "localhost",
    onServerModeChange: (String) -> Unit = {},
    customServerUrl: String = "",
    onCustomServerUrlChange: (String) -> Unit = {}
) {
    var users           by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var selected        by remember { mutableStateOf<UserDto?>(null) }
    var expanded        by remember { mutableStateOf(false) }
    var loadingUsers    by remember { mutableStateOf(true) }
    var error           by remember { mutableStateOf<String?>(null) }
    var showSettings    by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            users = client.get("$BASE_URL/api/users").body()
            selected = users.firstOrNull()
        } catch (e: Exception) {
            error = "Could not reach server: ${e.message}"
        } finally {
            loadingUsers = false
        }
    }

    val s  = LocalStrings.current
    val cs = MaterialTheme.colorScheme

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(s.settingsServer, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val serverOptions = listOf(
                        "localhost"  to s.settingsServerLocalhost,
                        "deployment" to s.settingsServerDeployment,
                        "custom"     to s.settingsServerCustom
                    )
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(cs.surfaceVariant)) {
                        serverOptions.forEach { (mode, label) ->
                            val selected2 = serverMode == mode
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected2) cs.primary else Color.Transparent)
                                    .clickable(enabled = !selected2) { onServerModeChange(mode) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected2) cs.onPrimary else cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (serverMode == "custom") {
                        OutlinedTextField(
                            value = customServerUrl,
                            onValueChange = onCustomServerUrlChange,
                            label = { Text(s.settingsServerCustomUrl) },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("OK") }
            }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopEnd) {
            TextButton(
                onClick = { showSettings = true },
                modifier = Modifier.padding(12.dp)
            ) {
                Text("⚙", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            }
        }
        Card(
            modifier  = Modifier.width(400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border    = BorderStroke(1.dp, cs.outlineVariant),
            colors    = CardDefaults.cardColors(containerColor = cs.surface),
            shape     = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Branding
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        s.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                    Text(
                        s.loginSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = cs.outlineVariant)

                // Form
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (loadingUsers) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { if (users.isNotEmpty()) expanded = it }
                        ) {
                            OutlinedTextField(
                                value = selected?.let { "${it.name} (${it.appRole})" } ?: s.loginNoUsers,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(s.loginUserLabel) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                users.forEach { user ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(user.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    user.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = cs.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(user.appRole, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        },
                                        onClick = { selected = user; expanded = false }
                                    )
                                }
                            }
                        }
                    }

                    error?.let {
                        Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { selected?.let(onLogin) },
                        enabled = selected != null && !loadingUsers,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(s.loginEnter, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
