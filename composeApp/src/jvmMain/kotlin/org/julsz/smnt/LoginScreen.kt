package org.julsz.smnt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(client: HttpClient, onLogin: (UserDto) -> Unit) {
    var users        by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var selected     by remember { mutableStateOf<UserDto?>(null) }
    var expanded     by remember { mutableStateOf(false) }
    var loadingUsers by remember { mutableStateOf(true) }
    var error        by remember { mutableStateOf<String?>(null) }

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
    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background),
        contentAlignment = Alignment.Center
    ) {
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
