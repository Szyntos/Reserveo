package org.julsz.smnt

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

internal const val BASE_URL = "http://localhost:8080"

@Composable
fun AppRoot() {
    val client = remember { HttpClient(CIO) { install(ContentNegotiation) { json() } } }
    DisposableEffect(Unit) { onDispose { client.close() } }

    var currentUser by remember { mutableStateOf<UserDto?>(null) }

    MaterialTheme(typography = rememberSansSerifTypography()) {
        when {
            currentUser == null ->
                LoginScreen(client, onLogin = { currentUser = it })
            currentUser!!.appRole == "admin" ->
                DbViewerApp(client, onLogout = { currentUser = null })
            else ->
                MainApp(client, currentUser!!, onLogout = { currentUser = null })
        }
    }
}

@Composable
internal fun rememberSansSerifTypography(): Typography {
    val ff = FontFamily.SansSerif
    return remember {
        val base = Typography()
        Typography(
            displayLarge   = base.displayLarge.copy(fontFamily = ff),
            displayMedium  = base.displayMedium.copy(fontFamily = ff),
            displaySmall   = base.displaySmall.copy(fontFamily = ff),
            headlineLarge  = base.headlineLarge.copy(fontFamily = ff),
            headlineMedium = base.headlineMedium.copy(fontFamily = ff),
            headlineSmall  = base.headlineSmall.copy(fontFamily = ff),
            titleLarge     = base.titleLarge.copy(fontFamily = ff),
            titleMedium    = base.titleMedium.copy(fontFamily = ff),
            titleSmall     = base.titleSmall.copy(fontFamily = ff),
            bodyLarge      = base.bodyLarge.copy(fontFamily = ff),
            bodyMedium     = base.bodyMedium.copy(fontFamily = ff),
            bodySmall      = base.bodySmall.copy(fontFamily = ff),
            labelLarge     = base.labelLarge.copy(fontFamily = ff),
            labelMedium    = base.labelMedium.copy(fontFamily = ff),
            labelSmall     = base.labelSmall.copy(fontFamily = ff),
        )
    }
}
