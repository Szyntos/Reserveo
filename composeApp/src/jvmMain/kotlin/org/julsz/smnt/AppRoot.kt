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

// ─── Settings persistence ─────────────────────────────────────────────────────

private data class AppSettings(
    val isDark: Boolean = true,
    val fontScale: Float = 1.0f,
    val centerDays: Int = 30,
    val noShowAfterDays: Int = 14,
    val autoCheckOutAfterDays: Int = 3,
    val language: String = "English",
    val timelineDayWidth: Float = 40f,
    val timelineRowHeight: Float = 34f,
    val timelineLabelWidth: Float = 96f,
    val timelineShowRoomType: Boolean = true,
    val serverMode: String = "localhost",
    val customServerUrl: String = ""
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
        language              = props.getProperty("language", "English"),
        timelineDayWidth      = props.getProperty("timelineDayWidth", "40").toFloat(),
        timelineRowHeight     = props.getProperty("timelineRowHeight", "34").toFloat(),
        timelineLabelWidth    = props.getProperty("timelineLabelWidth", "96").toFloat(),
        timelineShowRoomType  = props.getProperty("timelineShowRoomType", "true").toBoolean(),
        serverMode            = props.getProperty("serverMode", "localhost"),
        customServerUrl       = props.getProperty("customServerUrl", "")
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
        props["timelineDayWidth"]      = s.timelineDayWidth.toString()
        props["timelineRowHeight"]     = s.timelineRowHeight.toString()
        props["timelineLabelWidth"]    = s.timelineLabelWidth.toString()
        props["timelineShowRoomType"]  = s.timelineShowRoomType.toString()
        props["serverMode"]            = s.serverMode
        props["customServerUrl"]       = s.customServerUrl
        settingsFile.outputStream().use { props.store(it, null) }
    } catch (_: Exception) {}
}

private val ReserveoDarkColors = darkColorScheme(
    primary              = Color(0xFF7B9EF0),
    onPrimary            = Color(0xFF0A1B60),
    primaryContainer     = Color(0xFF1A2D6E),
    onPrimaryContainer   = Color(0xFFB8C8FF),
    secondary            = Color(0xFF8DA3C2),
    onSecondary          = Color(0xFF0D2040),
    secondaryContainer   = Color(0xFF1A3058),
    onSecondaryContainer = Color(0xFFCCDEFF),
    tertiary             = Color(0xFF5EC8E8),
    onTertiary           = Color(0xFF00374D),
    tertiaryContainer    = Color(0xFF0A4A65),
    onTertiaryContainer  = Color(0xFFAFE8FF),
    error                = Color(0xFFFF8070),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF5C1010),
    onErrorContainer     = Color(0xFFFFDAD6),
    background           = Color(0xFF0D1117),
    onBackground         = Color(0xFFCDD5E0),
    surface              = Color(0xFF151C28),
    onSurface            = Color(0xFFCDD5E0),
    surfaceVariant       = Color(0xFF1B2438),
    onSurfaceVariant     = Color(0xFF7A8BA8),
    outline              = Color(0xFF2C3A52),
    outlineVariant       = Color(0xFF1E2A40),
    inverseSurface       = Color(0xFFCDD5E0),
    inverseOnSurface     = Color(0xFF0D1117),
    inversePrimary       = Color(0xFF2A4DC4),
)

private val ReserveoLightColors = lightColorScheme(
    primary              = Color(0xFF2563EB),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFDBEAFE),
    onPrimaryContainer   = Color(0xFF1E3A8A),
    secondary            = Color(0xFF3B5280),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFDCE8FF),
    onSecondaryContainer = Color(0xFF0B1E4A),
    tertiary             = Color(0xFF0284C7),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFE0F2FE),
    onTertiaryContainer  = Color(0xFF00334F),
    error                = Color(0xFFDC2626),
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFFFEE2E2),
    onErrorContainer     = Color(0xFF7F1D1D),
    background           = Color(0xFFF2F5FB),
    onBackground         = Color(0xFF1A2036),
    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF1A2036),
    surfaceVariant       = Color(0xFFEBF0FA),
    onSurfaceVariant     = Color(0xFF4B5A7A),
    outline              = Color(0xFFC0CDE8),
    outlineVariant       = Color(0xFFDDE5F5),
    inverseSurface       = Color(0xFF1A2036),
    inverseOnSurface     = Color(0xFFF2F5FB),
    inversePrimary       = Color(0xFF7B9EF0),
)

@Composable
fun AppRoot() {
    val client = remember { HttpClient(CIO) { install(ContentNegotiation) { json() } } }
    DisposableEffect(Unit) { onDispose { client.close() } }

    var currentUser   by remember { mutableStateOf<UserDto?>(null) }
    var selectedHotel by remember { mutableStateOf<UserHotelRoleDto?>(null) }

    val initial = remember {
        val s = loadSettings()
        BASE_URL = resolveServerUrl(s.serverMode, s.customServerUrl)
        s
    }
    var isDark                by remember { mutableStateOf(initial.isDark) }
    var fontScale             by remember { mutableStateOf(initial.fontScale) }
    var centerDays            by remember { mutableStateOf(initial.centerDays) }
    var noShowAfterDays       by remember { mutableStateOf(initial.noShowAfterDays) }
    var autoCheckOutAfterDays by remember { mutableStateOf(initial.autoCheckOutAfterDays) }
    var language              by remember { mutableStateOf(
        AppLanguage.entries.firstOrNull { it.name == initial.language } ?: AppLanguage.English
    ) }
    var timelineDayWidth      by remember { mutableStateOf(initial.timelineDayWidth) }
    var timelineRowHeight     by remember { mutableStateOf(initial.timelineRowHeight) }
    var timelineLabelWidth    by remember { mutableStateOf(initial.timelineLabelWidth) }
    var timelineShowRoomType  by remember { mutableStateOf(initial.timelineShowRoomType) }
    var serverMode            by remember { mutableStateOf(initial.serverMode) }
    var customServerUrl       by remember { mutableStateOf(initial.customServerUrl) }

    LaunchedEffect(isDark, fontScale, centerDays, noShowAfterDays, autoCheckOutAfterDays, language,
                   timelineDayWidth, timelineRowHeight, timelineLabelWidth, timelineShowRoomType,
                   serverMode, customServerUrl) {
        BASE_URL = resolveServerUrl(serverMode, customServerUrl)
        saveSettings(AppSettings(isDark, fontScale, centerDays, noShowAfterDays, autoCheckOutAfterDays, language.name,
                                 timelineDayWidth, timelineRowHeight, timelineLabelWidth, timelineShowRoomType,
                                 serverMode, customServerUrl))
    }

    fun logout() { currentUser = null; selectedHotel = null }

    val colorScheme = if (isDark) ReserveoDarkColors else ReserveoLightColors

    MaterialTheme(colorScheme = colorScheme, typography = rememberSansSerifTypography()) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalStrings provides language.strings,
            LocalFontScale provides fontScale,
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight       = 16.dp,
                thickness           = 8.dp,
                shape               = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = if (isDark) Color(0xFF7B9EF0).copy(alpha = 0.25f) else Color(0xFF2563EB).copy(alpha = 0.20f),
                hoverColor   = if (isDark) Color(0xFF7B9EF0).copy(alpha = 0.55f) else Color(0xFF2563EB).copy(alpha = 0.45f)
            ),
            LocalDensity provides Density(baseDensity.density, fontScale)
        ) {
            Surface(Modifier.fillMaxSize()) {
                when {
                    currentUser == null ->
                        LoginScreen(
                            client                 = client,
                            onLogin                = { currentUser = it },
                            serverMode             = serverMode,
                            onServerModeChange     = { serverMode = it },
                            customServerUrl        = customServerUrl,
                            onCustomServerUrlChange = { customServerUrl = it }
                        )
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
                            onLanguageChange             = { language = it },
                            timelineDayWidth             = timelineDayWidth,
                            onTimelineDayWidthChange     = { timelineDayWidth = it },
                            timelineRowHeight            = timelineRowHeight,
                            onTimelineRowHeightChange    = { timelineRowHeight = it },
                            timelineLabelWidth           = timelineLabelWidth,
                            onTimelineLabelWidthChange   = { timelineLabelWidth = it },
                            timelineShowRoomType         = timelineShowRoomType,
                            onTimelineShowRoomTypeChange = { timelineShowRoomType = it },
                            serverMode                   = serverMode,
                            onServerModeChange           = { serverMode = it },
                            customServerUrl              = customServerUrl,
                            onCustomServerUrlChange      = { customServerUrl = it }
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
