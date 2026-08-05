package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// SEMANTIC / FUNCTIONAL COLORS (theme-independent — do NOT tie
// these to a selected theme: they carry meaning across the app:
// medical warnings, cheque status, gender avatars, tooth-chart
// anatomy, etc. Screens reference these constants directly.)
// ============================================================
val DebtColor = Color(0xFFFF453A)
val ClearColor = Color(0xFF30D158)

// ============================================================
// NEON THEME ENGINE
// Each palette drives the full Material3 color scheme AND a set
// of extra "glow" values used for neon borders/shadows/gradients
// on hero banners, selected cards, and accents.
// ============================================================

/**
 * A complete, self-contained visual theme. Every value here is
 * hand-tuned so the theme reads as one coherent neon family
 * rather than a single recolored accent on top of old chrome.
 */
data class NeonPalette(
    val id: String,
    val labelFa: String,
    val labelEn: String,
    // Core brand hues
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Foreground on brand colors
    val onPrimary: Color,
    // Neon-specific extras
    val glow: Color,           // bright glow color for shadows/borders
    val gradientStart: Color,  // hero banner / accent gradient
    val gradientEnd: Color,
    val swatchDots: List<Color> // preview dots shown in the theme picker
)

val ThemeAmberClassic = NeonPalette(
    id = "AMBER_CLASSIC",
    labelFa = "کهربایی کلاسیک",
    labelEn = "Amber Classic",
    primary = Color(0xFFFF9F0A),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFFFF7043),
    background = Color(0xFF0C0C0F),
    surface = Color(0xFF16161E),
    surfaceVariant = Color(0xFF22222E),
    primaryContainer = Color(0xFF3A2A0F),
    onPrimaryContainer = Color(0xFFFFE0B2),
    onPrimary = Color.Black,
    glow = Color(0xFFFFD60A),
    gradientStart = Color(0xFF3A2A0F),
    gradientEnd = Color(0xFF1A1408),
    swatchDots = listOf(Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFFFF7043))
)

val ThemeNeonPurple = NeonPalette(
    id = "NEON_PURPLE",
    labelFa = "بنفش نئونی",
    labelEn = "Neon Purple",
    primary = Color(0xFFB026FF),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFFE0AAFF),
    background = Color(0xFF0A0612),
    surface = Color(0xFF160B24),
    surfaceVariant = Color(0xFF221232),
    primaryContainer = Color(0xFF2E1250),
    onPrimaryContainer = Color(0xFFEBD6FF),
    onPrimary = Color.White,
    glow = Color(0xFFD65DFF),
    gradientStart = Color(0xFF3A0F6E),
    gradientEnd = Color(0xFF130826),
    swatchDots = listOf(Color(0xFFB026FF), Color(0xFFD65DFF), Color(0xFF8B5CF6))
)

val ThemeNeonGold = NeonPalette(
    id = "NEON_GOLD",
    labelFa = "طلایی نئونی",
    labelEn = "Neon Gold",
    primary = Color(0xFFFFD700),
    secondary = Color(0xFFFFC400),
    tertiary = Color(0xFFFFF176),
    background = Color(0xFF0F0C04),
    surface = Color(0xFF1E1808),
    surfaceVariant = Color(0xFF2B2210),
    primaryContainer = Color(0xFF4A3B08),
    onPrimaryContainer = Color(0xFFFFF3C4),
    onPrimary = Color.Black,
    glow = Color(0xFFFFEA00),
    gradientStart = Color(0xFF4A3B08),
    gradientEnd = Color(0xFF1A1504),
    swatchDots = listOf(Color(0xFFFFD700), Color(0xFFFFEA00), Color(0xFFFFC400))
)

val ThemeNeonCopper = NeonPalette(
    id = "NEON_COPPER",
    labelFa = "مسی نئونی",
    labelEn = "Neon Copper",
    primary = Color(0xFFFF6B35),
    secondary = Color(0xFFE8873A),
    tertiary = Color(0xFFFFA773),
    background = Color(0xFF130904),
    surface = Color(0xFF231208),
    surfaceVariant = Color(0xFF32190D),
    primaryContainer = Color(0xFF522210),
    onPrimaryContainer = Color(0xFFFFDCC4),
    onPrimary = Color.Black,
    glow = Color(0xFFFF8C42),
    gradientStart = Color(0xFF5C2408),
    gradientEnd = Color(0xFF1D0F05),
    swatchDots = listOf(Color(0xFFFF6B35), Color(0xFFFF8C42), Color(0xFFE8873A))
)

val ThemeNeonBlue = NeonPalette(
    id = "NEON_BLUE",
    labelFa = "آبی نئونی",
    labelEn = "Neon Blue",
    primary = Color(0xFF00B8FF),
    secondary = Color(0xFF2979FF),
    tertiary = Color(0xFF18FFFF),
    background = Color(0xFF03070F),
    surface = Color(0xFF08131F),
    surfaceVariant = Color(0xFF0E1E30),
    primaryContainer = Color(0xFF0A2C4A),
    onPrimaryContainer = Color(0xFFC9EFFF),
    onPrimary = Color.Black,
    glow = Color(0xFF00E5FF),
    gradientStart = Color(0xFF0A3A5C),
    gradientEnd = Color(0xFF061420),
    swatchDots = listOf(Color(0xFF00B8FF), Color(0xFF18FFFF), Color(0xFF2979FF))
)

val AllNeonPalettes = listOf(
    ThemeAmberClassic,
    ThemeNeonPurple,
    ThemeNeonGold,
    ThemeNeonCopper,
    ThemeNeonBlue
)

fun neonPaletteById(id: String?): NeonPalette =
    AllNeonPalettes.firstOrNull { it.id == id } ?: ThemeAmberClassic

// Light-mode fallback (app is designed dark-first; kept for completeness)
val LightBackground = Color(0xFFF8F9FC)
val LightSurface = Color(0xFFFFFFFF)
