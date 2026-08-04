package app.brokoli5191.quote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Additional accents for customizable palettes
private val PrimaryBlue = androidx.compose.ui.graphics.Color(0xFFADC6FF)
private val OnPrimaryBlue = androidx.compose.ui.graphics.Color(0xFF002F66)
private val PrimaryContainerBlue = androidx.compose.ui.graphics.Color(0xFFD4E3FF)
private val OnPrimaryContainerBlue = androidx.compose.ui.graphics.Color(0xFF2B467F)

private val PrimaryRose = androidx.compose.ui.graphics.Color(0xFFFFB2C5)
private val OnPrimaryRose = androidx.compose.ui.graphics.Color(0xFF5B112B)
private val PrimaryContainerRose = androidx.compose.ui.graphics.Color(0xFFFFD9E2)
private val OnPrimaryContainerRose = androidx.compose.ui.graphics.Color(0xFF7A2A44)

private val PrimaryAmber = androidx.compose.ui.graphics.Color(0xFFFFDB9C)
private val OnPrimaryAmber = androidx.compose.ui.graphics.Color(0xFF412D00)
private val PrimaryContainerAmber = androidx.compose.ui.graphics.Color(0xFFFDB700)
private val OnPrimaryContainerAmber = androidx.compose.ui.graphics.Color(0xFF6A4B00)

private val PrimaryGreen = androidx.compose.ui.graphics.Color(0xFFBCEEC8)
private val OnPrimaryGreen = androidx.compose.ui.graphics.Color(0xFF06381F)
private val PrimaryContainerGreen = androidx.compose.ui.graphics.Color(0xFFA0D2AD)
private val OnPrimaryContainerGreen = androidx.compose.ui.graphics.Color(0xFF2E5B3F)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryViolet,
    onPrimary = OnPrimaryViolet,
    primaryContainer = PrimaryContainerViolet,
    onPrimaryContainer = OnPrimaryContainerViolet,
    secondary = SecondaryAmber,
    onSecondary = OnSecondaryAmber,
    secondaryContainer = SecondaryContainerAmber,
    onSecondaryContainer = OnSecondaryContainerAmber,
    tertiary = TertiaryGreen,
    onTertiary = OnTertiaryGreen,
    tertiaryContainer = TertiaryContainerGreen,
    onTertiaryContainer = OnTertiaryContainerGreen,
    background = SurfaceBg,
    onBackground = OnSurfaceText,
    surface = SurfaceContainer,
    onSurface = OnSurfaceText,
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = OnSurfaceVariantText,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFEADDFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF21005D),
    secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF1D192B),
    background = androidx.compose.ui.graphics.Color(0xFFFBF8FD),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    surface = androidx.compose.ui.graphics.Color(0xFFF3EDF7),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE6E0E9),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF49454F)
)

private data class QuadrupleColors(
    val primary: androidx.compose.ui.graphics.Color,
    val onPrimary: androidx.compose.ui.graphics.Color,
    val primaryContainer: androidx.compose.ui.graphics.Color,
    val onPrimaryContainer: androidx.compose.ui.graphics.Color
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "DARK", // LIGHT, DARK, DYNAMIC
    themeAccent: String = "Violet", // Violet, Amber, Green, Blue, Rose
    amoledBlack: Boolean = false, // true-black surfaces in any dark scheme (incl. DYNAMIC)
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDark = when (themeMode) {
        "LIGHT" -> false
        "DYNAMIC" -> isSystemInDarkTheme()
        else -> true
    }

    val colorScheme = when {
        themeMode == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dark = isSystemInDarkTheme()
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (amoledBlack && dark) {
                base.copy(
                    background = androidx.compose.ui.graphics.Color(0xFF000000),
                    surface = androidx.compose.ui.graphics.Color(0xFF0B0B0C),
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                )
            } else base
        }
        useDark -> {
            val isAmoled = amoledBlack
            val bg = if (isAmoled) androidx.compose.ui.graphics.Color(0xFF000000) else androidx.compose.ui.graphics.Color(0xFF141317)
            val surf = if (isAmoled) androidx.compose.ui.graphics.Color(0xFF0B0B0C) else androidx.compose.ui.graphics.Color(0xFF1C1B1F)
            val surfContainer = if (isAmoled) androidx.compose.ui.graphics.Color(0xFF111112) else androidx.compose.ui.graphics.Color(0xFF201F23)
            val surfHighest = if (isAmoled) androidx.compose.ui.graphics.Color(0xFF1C1C1E) else androidx.compose.ui.graphics.Color(0xFF353438)

            val colors = when (themeAccent) {
                "Amber" -> QuadrupleColors(PrimaryAmber, OnPrimaryAmber, PrimaryContainerAmber, OnPrimaryContainerAmber)
                "Green" -> QuadrupleColors(PrimaryGreen, OnPrimaryGreen, PrimaryContainerGreen, OnPrimaryContainerGreen)
                "Blue" -> QuadrupleColors(PrimaryBlue, OnPrimaryBlue, PrimaryContainerBlue, OnPrimaryContainerBlue)
                "Rose" -> QuadrupleColors(PrimaryRose, OnPrimaryRose, PrimaryContainerRose, OnPrimaryContainerRose)
                else -> QuadrupleColors(PrimaryViolet, OnPrimaryViolet, PrimaryContainerViolet, OnPrimaryContainerViolet) // Violet
            }

            darkColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                primaryContainer = colors.primaryContainer,
                onPrimaryContainer = colors.onPrimaryContainer,
                secondary = SecondaryAmber,
                onSecondary = OnSecondaryAmber,
                secondaryContainer = SecondaryContainerAmber,
                onSecondaryContainer = OnSecondaryContainerAmber,
                tertiary = TertiaryGreen,
                onTertiary = OnTertiaryGreen,
                background = bg,
                onBackground = OnSurfaceText,
                surface = surfContainer,
                onSurface = OnSurfaceText,
                surfaceVariant = surfHighest,
                onSurfaceVariant = OnSurfaceVariantText
            )
        }
        else -> { // LIGHT
            val bg = androidx.compose.ui.graphics.Color(0xFFFBF8FD)
            val surf = androidx.compose.ui.graphics.Color(0xFFF3EDF7)
            val surfContainer = androidx.compose.ui.graphics.Color(0xFFECE6F0)
            val surfHighest = androidx.compose.ui.graphics.Color(0xFFE6E0E9)
            val textLight = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
            val textVariantLight = androidx.compose.ui.graphics.Color(0xFF49454F)

            val colors = when (themeAccent) {
                "Amber" -> QuadrupleColors(androidx.compose.ui.graphics.Color(0xFF6E5000), androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFFFFFDBD), androidx.compose.ui.graphics.Color(0xFF3E2D00))
                "Green" -> QuadrupleColors(androidx.compose.ui.graphics.Color(0xFF006D3E), androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFF90F9B9), androidx.compose.ui.graphics.Color(0xFF003D20))
                "Blue" -> QuadrupleColors(androidx.compose.ui.graphics.Color(0xFF005FAF), androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFFD4E3FF), androidx.compose.ui.graphics.Color(0xFF001C3B))
                "Rose" -> QuadrupleColors(androidx.compose.ui.graphics.Color(0xFF9D004B), androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFFFFD9E2), androidx.compose.ui.graphics.Color(0xFF3E001A))
                else -> QuadrupleColors(androidx.compose.ui.graphics.Color(0xFF6750A4), androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFFEADDFF), androidx.compose.ui.graphics.Color(0xFF21005D)) // Violet
            }

            lightColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                primaryContainer = colors.primaryContainer,
                onPrimaryContainer = colors.onPrimaryContainer,
                secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
                onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
                onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF1D192B),
                tertiary = androidx.compose.ui.graphics.Color(0xFF7D5260),
                onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                background = bg,
                onBackground = textLight,
                surface = surf,
                onSurface = textLight,
                surfaceVariant = surfHighest,
                onSurfaceVariant = textVariantLight
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
