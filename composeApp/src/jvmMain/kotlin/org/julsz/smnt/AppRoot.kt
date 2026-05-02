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
import java.util.Properties

internal const val BASE_URL = "http://localhost:8080"

// ─── Settings persistence ─────────────────────────────────────────────────────

private data class AppSettings(
    val isDark: Boolean = true,
    val fontScale: Float = 1.0f,
    val centerDays: Int = 30,
    val noShowAfterDays: Int = 14,
    val autoCheckOutAfterDays: Int = 3,
    val language: String = "English"
)

private val settingsFile = File(System.getProperty("user.home"), ".reserveo_settings.properties")

private fun loadSettings(): AppSettings {
    val props = Properties()
    try { settingsFile.inputStream().use { props.load(it) } } catch (_: Exception) {}
    return AppSettings(
        isDark                = props.getProperty("isDark", "true").toBoolean(),
        fontScale             = props.getProperty("fontScale", "1.0").toFloat(),
        centerDays            = props.getProperty("centerDays", "30").toInt(),
        noShowAfterDays       = props.getProperty("noShowAfterDays", "14").toInt(),
        autoCheckOutAfterDays = props.getProperty("autoCheckOutAfterDays", "3").toInt(),
        language              = props.getProperty("language", "English")
    )
}

private fun saveSettings(s: AppSettings) {
    try {
        val props = Properties()
        props["isDark"]                = s.isDark.toString()
        props["fontScale"]             = s.fontScale.toString()
        props["centerDays"]            = s.centerDays.toString()
        props["noShowAfterDays"]       = s.noShowAfterDays.toString()
        props["autoCheckOutAfterDays"] = s.autoCheckOutAfterDays.toString()
        props["language"]              = s.language
        settingsFile.outputStream().use { props.store(it, null) }
    } catch (_: Exception) {}
}

@Composable
fun AppRoot() {
    val client = remember { HttpClient(CIO) { install(ContentNegotiation) { json() } } }
    DisposableEffect(Unit) { onDispose { client.close() } }

    var currentUser   by remember { mutableStateOf<UserDto?>(null) }
    var selectedHotel by remember { mutableStateOf<UserHotelRoleDto?>(null) }

    val initial = remember { loadSettings() }
    var isDark                by remember { mutableStateOf(initial.isDark) }
    var fontScale             by remember { mutableStateOf(initial.fontScale) }
    var centerDays            by remember { mutableStateOf(initial.centerDays) }
    var noShowAfterDays       by remember { mutableStateOf(initial.noShowAfterDays) }
    var autoCheckOutAfterDays by remember { mutableStateOf(initial.autoCheckOutAfterDays) }
    var language              by remember { mutableStateOf(
        AppLanguage.entries.firstOrNull { it.name == initial.language } ?: AppLanguage.English
    ) }

    LaunchedEffect(isDark, fontScale, centerDays, noShowAfterDays, autoCheckOutAfterDays, language) {
        saveSettings(AppSettings(isDark, fontScale, centerDays, noShowAfterDays, autoCheckOutAfterDays, language.name))
    }

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
                            client                       = client,
                            currentUser                  = currentUser!!,
                            selectedHotel                = selectedHotel!!,
                            onSwitchHotel                = { selectedHotel = null },
                            onLogout                     = ::logout,
                            isDark                       = isDark,
                            onThemeToggle                = { isDark = !isDark },
                            fontScale                    = fontScale,
                            onFontScaleChange            = { fontScale = it },
                            centerDays                   = centerDays,
                            onCenterDaysChange           = { centerDays = it },
                            noShowAfterDays              = noShowAfterDays,
                            onNoShowAfterDaysChange      = { noShowAfterDays = it },
                            autoCheckOutAfterDays        = autoCheckOutAfterDays,
                            onAutoCheckOutAfterDaysChange = { autoCheckOutAfterDays = it },
                            language                     = language,
                            onLanguageChange             = { language = it }
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
