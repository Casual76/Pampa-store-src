package com.pampa.store.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
internal actual fun rememberBrandFontFamilies(): BrandFontFamilies {
    return BrandFontFamilies(
        display = FontFamily.Serif,
        sans = FontFamily.SansSerif
    )
}
