package theme

import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color

internal val LightColorPalette = lightColors(
    primary = Color(0xFFB3CDE0),       // Soft pastel blue
    primaryVariant = Color(0xFF9BB5D6), // Slightly darker blue
    secondary = Color(0xFFF4A6A6),      // Pastel coral
    secondaryVariant = Color(0xFFC97D7D), // Deeper coral
    background = Color(0xFFF8F8F8),     // Light neutral gray
    surface = Color(0xFFFFFFFF),        // White for surfaces
    onPrimary = Color.White,            // White text/icons on primary color
    onSecondary = Color.White,          // White text/icons on secondary color
    onBackground = Color(0xFF4A4A4A),   // Dark gray text on light background
    onSurface = Color(0xFF4A4A4A),      // Dark gray text/icons on surfaces
    error = Color(0xFFF6C1C1),          // Soft red for errors
)

internal val DarkColorPalette = darkColors(
    primary = Color(0xFF2E3A45),       // Muted dark blue
    primaryVariant = Color(0xFF1E2A35), // Even darker blue
    secondary = Color(0xFFF4A6A6),      // Pastel coral for consistency
    secondaryVariant = Color(0xFFC97D7D), // Slightly deeper coral
    background = Color(0xFF121212),     // Very dark gray
    surface = Color(0xFF1E1E1E),        // Slightly lighter dark gray
    onPrimary = Color(0xFFE0E0E0),      // Light gray text/icons on dark primary
    onSecondary = Color(0xFFE0E0E0),    // Light gray text/icons on dark secondary
    onBackground = Color(0xFFE0E0E0),   // Light gray text on dark background
    onSurface = Color(0xFFE0E0E0),      // Light gray text/icons on dark surfaces
    error = Color(0xFFF6C1C1),          // Soft red for errors
)