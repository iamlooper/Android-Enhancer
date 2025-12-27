package io.github.iamlooper.androidenhancer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 Expressive Shapes - Contrasting variety for energy and visual hierarchy
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),      // XS - Small badges, chips
    small = RoundedCornerShape(8.dp),           // S - Compact buttons, indicators
    medium = RoundedCornerShape(16.dp),         // M - Default cards, dialogs
    large = RoundedCornerShape(24.dp),          // L - Prominent cards, large surfaces
    extraLarge = RoundedCornerShape(32.dp)      // XL - Hero surfaces, FABs
)
