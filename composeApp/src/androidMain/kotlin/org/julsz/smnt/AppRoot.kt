package org.julsz.smnt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

// ─── Settings (in-memory on Android) ─────────────────────────────────────────

private data class AppSettings(
    val isDark: Boolean = true,
    val fontScale: Float = 1.0f,
    val centerDays: Int = 30,
    val noShowAfterDays: Int = 14,
    val autoCheckOutAfterDays: Int = 3,
    val language: String = "English"
)

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

    var isDark                by remember { mutableStateOf(true) }
    var fontScale             by remember { mutableStateOf(1.0f) }
    var centerDays            by remember { mutableStateOf(30) }
    var noShowAfterDays       by remember { mutableStateOf(14) }
    var autoCheckOutAfterDays by remember { mutableStateOf(3) }
    var language              by remember { mutableStateOf(AppLanguage.English) }

    fun logout() { currentUser = null; selectedHotel = null }

    val colorScheme = if (isDark) ReserveoDarkColors else ReserveoLightColors

    MaterialTheme(colorScheme = colorScheme, typography = Typography()) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalStrings provides language.strings,
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
                            client                        = client,
                            currentUser                   = currentUser!!,
                            selectedHotel                 = selectedHotel!!,
                            onSwitchHotel                 = { selectedHotel = null },
                            onLogout                      = ::logout,
                            isDark                        = isDark,
                            onThemeToggle                 = { isDark = !isDark },
                            fontScale                     = fontScale,
                            onFontScaleChange             = { fontScale = it },
                            centerDays                    = centerDays,
                            onCenterDaysChange            = { centerDays = it },
                            noShowAfterDays               = noShowAfterDays,
                            onNoShowAfterDaysChange       = { noShowAfterDays = it },
                            autoCheckOutAfterDays         = autoCheckOutAfterDays,
                            onAutoCheckOutAfterDaysChange = { autoCheckOutAfterDays = it },
                            language                      = language,
                            onLanguageChange              = { language = it }
                        )
                }
            }
        }
    }
}
