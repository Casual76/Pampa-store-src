package com.pampa.store.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pampastore.shared.generated.resources.Res
import pampastore.shared.generated.resources.dm_serif_display_regular
import pampastore.shared.generated.resources.space_grotesk_bold
import pampastore.shared.generated.resources.space_grotesk_medium
import pampastore.shared.generated.resources.space_grotesk_regular

@Composable
internal actual fun rememberBrandFontFamilies(): BrandFontFamilies {
    return BrandFontFamilies(
        display = FontFamily(
            Font(Res.font.dm_serif_display_regular, FontWeight.Normal)
        ),
        sans = FontFamily(
            Font(Res.font.space_grotesk_regular, FontWeight.Normal),
            Font(Res.font.space_grotesk_medium, FontWeight.Medium),
            Font(Res.font.space_grotesk_bold, FontWeight.Bold)
        )
    )
}
