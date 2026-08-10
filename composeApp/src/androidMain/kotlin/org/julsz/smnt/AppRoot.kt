package org.julsz.smnt

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch

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
    val serverMode: String = "deployment",
    val customServerUrl: String = "",
    val savedPasswords: Map<String, String> = emptyMap(),
    val lastEmail: String = ""
)

private fun decodeSavedPasswords(encoded: String): Map<String, String> =
    encoded.split(",").filter { it.isNotEmpty() }
        .mapNotNull { entry ->
            val decoded = String(android.util.Base64.decode(entry, android.util.Base64.NO_WRAP))
            val parts = decoded.split(":", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

private fun encodeSavedPasswords(passwords: Map<String, String>): String =
    passwords.entries.joinToString(",") { (email, password) ->
        android.util.Base64.encodeToString("$email:$password".toByteArray(), android.util.Base64.NO_WRAP)
    }

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("reserveo_settings", Context.MODE_PRIVATE)

private fun loadSettings(context: Context): AppSettings {
    val p = prefs(context)
    return AppSettings(
        isDark                = p.getBoolean("isDark", true),
        fontScale             = p.getFloat("fontScale", 1.0f),
        centerDays            = p.getInt("centerDays", 30),
        noShowAfterDays       = p.getInt("noShowAfterDays", 14),
        autoCheckOutAfterDays = p.getInt("autoCheckOutAfterDays", 3),
        language              = p.getString("language", "English") ?: "English",
        timelineDayWidth      = p.getFloat("timelineDayWidth", 40f),
        timelineRowHeight     = p.getFloat("timelineRowHeight", 34f),
        timelineLabelWidth    = p.getFloat("timelineLabelWidth", 96f),
        timelineShowRoomType  = p.getBoolean("timelineShowRoomType", true),
        serverMode            = p.getString("serverMode", "deployment") ?: "deployment",
        customServerUrl       = p.getString("customServerUrl", "") ?: "",
        savedPasswords        = decodeSavedPasswords(p.getString("savedPasswords", "") ?: ""),
        lastEmail             = p.getString("lastEmail", "") ?: ""
    )
}

private fun saveSettings(context: Context, s: AppSettings) {
    prefs(context).edit()
        .putBoolean("isDark", s.isDark)
        .putFloat("fontScale", s.fontScale)
        .putInt("centerDays", s.centerDays)
        .putInt("noShowAfterDays", s.noShowAfterDays)
        .putInt("autoCheckOutAfterDays", s.autoCheckOutAfterDays)
        .putString("language", s.language)
        .putFloat("timelineDayWidth", s.timelineDayWidth)
        .putFloat("timelineRowHeight", s.timelineRowHeight)
        .putFloat("timelineLabelWidth", s.timelineLabelWidth)
        .putBoolean("timelineShowRoomType", s.timelineShowRoomType)
        .putString("serverMode", s.serverMode)
        .putString("customServerUrl", s.customServerUrl)
        .putString("savedPasswords", encodeSavedPasswords(s.savedPasswords))
        .putString("lastEmail", s.lastEmail)
        .apply()
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
    val context = LocalContext.current
    val client = remember {
        HttpClient(CIO) {
            install(ContentNegotiation) { json() }
            install(DefaultRequest) {
                AuthSession.basicAuthHeader?.let { header(HttpHeaders.Authorization, it) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { client.close() } }

    val githubClient = remember { createGithubHttpClient() }
    DisposableEffect(Unit) { onDispose { githubClient.close() } }
    val coroutineScope = rememberCoroutineScope()

    var currentUser   by remember { mutableStateOf<UserDto?>(null) }
    var selectedHotel by remember { mutableStateOf<UserHotelRoleDto?>(null) }

    var updateChecking       by remember { mutableStateOf(false) }
    var updateInfo           by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateError          by remember { mutableStateOf<String?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<Float?>(null) }

    fun checkForUpdateNow() {
        updateChecking = true
        updateError = null
        coroutineScope.launch {
            when (val result = checkForUpdate(githubClient, BuildConfig.VERSION_CODE)) {
                is UpdateCheckResult.UpdateAvailable -> updateInfo = result.info
                is UpdateCheckResult.UpToDate -> updateInfo = null
                is UpdateCheckResult.Error -> updateError = result.message
            }
            updateChecking = false
        }
    }

    LaunchedEffect(Unit) { checkForUpdateNow() }

    fun downloadAndInstallUpdate() {
        val info = updateInfo ?: return
        updateDownloadProgress = 0f
        coroutineScope.launch {
            try {
                val apkFile = downloadUpdate(githubClient, context, info) { progress ->
                    updateDownloadProgress = progress
                }
                updateDownloadProgress = null
                installUpdate(context, apkFile)
            } catch (e: Exception) {
                updateDownloadProgress = null
                updateError = e.message ?: "Download failed"
            }
        }
    }

    val initial = remember {
        val s = loadSettings(context)
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
    var savedPasswords        by remember { mutableStateOf(initial.savedPasswords) }
    var lastEmail             by remember { mutableStateOf(initial.lastEmail) }

    LaunchedEffect(isDark, fontScale, centerDays, noShowAfterDays, autoCheckOutAfterDays, language,
                   timelineDayWidth, timelineRowHeight, timelineLabelWidth, timelineShowRoomType,
                   serverMode, customServerUrl, savedPasswords, lastEmail) {
        BASE_URL = resolveServerUrl(serverMode, customServerUrl)
        saveSettings(context, AppSettings(isDark, fontScale, centerDays, noShowAfterDays, autoCheckOutAfterDays,
                                          language.name, timelineDayWidth, timelineRowHeight, timelineLabelWidth,
                                          timelineShowRoomType, serverMode, customServerUrl, savedPasswords, lastEmail))
    }

    fun logout() { AuthSession.clear(); currentUser = null; selectedHotel = null }

    val colorScheme = if (isDark) ReserveoDarkColors else ReserveoLightColors

    MaterialTheme(colorScheme = colorScheme, typography = Typography()) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalStrings provides language.strings,
            LocalFontScale provides fontScale,
            LocalDensity provides Density(baseDensity.density, fontScale)
        ) {
            Surface(Modifier.fillMaxSize().navigationBarsPadding()) {
                when {
                    currentUser == null ->
                        LoginScreen(
                            client                  = client,
                            onLogin                 = { currentUser = it },
                            serverMode              = serverMode,
                            onServerModeChange      = { serverMode = it },
                            customServerUrl         = customServerUrl,
                            onCustomServerUrlChange = { customServerUrl = it },
                            savedPasswords          = savedPasswords,
                            onPasswordSaved         = { email, password -> savedPasswords = savedPasswords + (email to password); lastEmail = email },
                            lastEmail               = lastEmail
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
                            onLanguageChange              = { language = it },
                            timelineDayWidth              = timelineDayWidth,
                            onTimelineDayWidthChange      = { timelineDayWidth = it },
                            timelineRowHeight             = timelineRowHeight,
                            onTimelineRowHeightChange     = { timelineRowHeight = it },
                            timelineLabelWidth            = timelineLabelWidth,
                            onTimelineLabelWidthChange    = { timelineLabelWidth = it },
                            timelineShowRoomType          = timelineShowRoomType,
                            onTimelineShowRoomTypeChange  = { timelineShowRoomType = it },
                            serverMode                    = serverMode,
                            onServerModeChange            = { serverMode = it },
                            customServerUrl               = customServerUrl,
                            onCustomServerUrlChange       = { customServerUrl = it },
                            appVersionName                = BuildConfig.VERSION_NAME,
                            appVersionCode                = BuildConfig.VERSION_CODE,
                            updateInfo                    = updateInfo,
                            updateChecking                = updateChecking,
                            updateError                   = updateError,
                            updateDownloadProgress         = updateDownloadProgress,
                            onCheckForUpdate               = ::checkForUpdateNow,
                            onDownloadAndInstallUpdate     = ::downloadAndInstallUpdate
                        )
                }
            }
        }
    }
}
