package org.julsz.smnt

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(client: HttpClient, onLogin: (UserDto) -> Unit) {
    val scope = rememberCoroutineScope()

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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.width(360.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Reserveo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Select your account to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // User picker
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
                            value = selected?.let { "${it.name} (${it.appRole})" } ?: "No users found",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("User") },
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text(it, color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { selected?.let(onLogin) },
                    enabled = selected != null && !loadingUsers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enter")
                }
            }
        }
    }
}
