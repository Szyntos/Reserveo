package org.julsz.smnt

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as PlatformFont
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.io.File

internal const val BASE_URL = "http://localhost:8080"

@Composable
fun AppRoot() {
    val client = remember { HttpClient(CIO) { install(ContentNegotiation) { json() } } }
    DisposableEffect(Unit) { onDispose { client.close() } }

    var currentUser   by remember { mutableStateOf<UserDto?>(null) }
    var selectedHotel by remember { mutableStateOf<UserHotelRoleDto?>(null) }

    fun logout() { currentUser = null; selectedHotel = null }

    MaterialTheme(typography = rememberSansSerifTypography()) {
        when {
            currentUser == null ->
                LoginScreen(client, onLogin = { currentUser = it })
            currentUser!!.appRole == "admin" ->
                DbViewerApp(client, onLogout = ::logout)
            selectedHotel == null ->
                HotelPickerScreen(
                    client       = client,
                    currentUser  = currentUser!!,
                    onHotelSelected = { selectedHotel = it },
                    onLogout     = ::logout
                )
            else ->
                MainApp(
                    client        = client,
                    currentUser   = currentUser!!,
                    selectedHotel = selectedHotel!!,
                    onSwitchHotel = { selectedHotel = null },
                    onLogout      = ::logout
                )
        }
    }
}

@Composable
internal fun rememberSansSerifTypography(): Typography {
    val ff = remember { loadUnicodeFontFamily() }
    return remember(ff) {
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

/**
 * Loads Segoe UI directly from the Windows system fonts directory so that
 * Skia/Skiko renders Latin Extended characters (ą ę ó ś ź ż ć ń) correctly.
 * Falls back to FontFamily.SansSerif on non-Windows systems.
 */
private fun loadUnicodeFontFamily(): FontFamily {
    val winFonts = File("C:/Windows/Fonts")
    if (!winFonts.exists()) return FontFamily.SansSerif

    data class Entry(val file: String, val weight: FontWeight, val style: FontStyle)
    val entries = listOf(
        Entry("segoeui.ttf",  FontWeight.Normal,   FontStyle.Normal),
        Entry("segoeuib.ttf", FontWeight.Bold,     FontStyle.Normal),
        Entry("segoeuii.ttf", FontWeight.Normal,   FontStyle.Italic),
        Entry("segoeuiz.ttf", FontWeight.Bold,     FontStyle.Italic),
        Entry("seguisb.ttf",  FontWeight.SemiBold, FontStyle.Normal),
    )

    val fonts = entries.mapNotNull { (file, weight, style) ->
        val f = winFonts.resolve(file)
        if (f.exists()) PlatformFont("segoeui-$file", f.readBytes(), weight, style) else null
    }

    return if (fonts.isNotEmpty()) FontFamily(fonts) else FontFamily.SansSerif
}
