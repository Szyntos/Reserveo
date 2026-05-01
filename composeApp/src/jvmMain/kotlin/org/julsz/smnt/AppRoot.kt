package org.julsz.smnt

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as PlatformFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    var isDark        by remember { mutableStateOf(true) }
    var fontScale     by remember { mutableStateOf(1.0f) }
    var centerDays    by remember { mutableStateOf(30) }
    var language      by remember { mutableStateOf(AppLanguage.English) }

    fun logout() { currentUser = null; selectedHotel = null }

    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme, typography = rememberSansSerifTypography()) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalStrings provides language.strings,
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight       = 16.dp,
                thickness           = 8.dp,
                shape               = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f),
                hoverColor   = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.40f)
            ),
            LocalDensity provides Density(baseDensity.density, fontScale)
        ) {
            Surface(Modifier.fillMaxSize()) {
                when {
                    currentUser == null ->
                        LoginScreen(client, onLogin = { currentUser = it })
                    currentUser!!.appRole == "admin" ->
                        DbViewerApp(client, onLogout = ::logout)
                    selectedHotel == null ->
                        HotelPickerScreen(
                            client          = client,
                            currentUser     = currentUser!!,
                            onHotelSelected = { selectedHotel = it },
                            onLogout        = ::logout
                        )
                    else ->
                        MainApp(
                            client             = client,
                            currentUser        = currentUser!!,
                            selectedHotel      = selectedHotel!!,
                            onSwitchHotel      = { selectedHotel = null },
                            onLogout           = ::logout,
                            isDark             = isDark,
                            onThemeToggle      = { isDark = !isDark },
                            fontScale          = fontScale,
                            onFontScaleChange  = { fontScale = it },
                            centerDays         = centerDays,
                            onCenterDaysChange = { centerDays = it },
                            language           = language,
                            onLanguageChange   = { language = it }
                        )
                }
            }
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
