package com.materialy.music.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.materialy.music.R

val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val RobotoFlexFont = GoogleFont("Roboto Flex")
val OpenSansFont = GoogleFont("Open Sans")

val AppFontFamily = FontFamily(
    Font(googleFont = RobotoFlexFont, fontProvider = googleFontProvider, weight = FontWeight.W300),
    Font(googleFont = RobotoFlexFont, fontProvider = googleFontProvider, weight = FontWeight.W400),
    Font(googleFont = RobotoFlexFont, fontProvider = googleFontProvider, weight = FontWeight.W500),
    Font(googleFont = RobotoFlexFont, fontProvider = googleFontProvider, weight = FontWeight.W600),
    Font(googleFont = RobotoFlexFont, fontProvider = googleFontProvider, weight = FontWeight.W700)
)

val AccentFontFamily = FontFamily(
    Font(googleFont = OpenSansFont, fontProvider = googleFontProvider, weight = FontWeight.W500),
    Font(googleFont = OpenSansFont, fontProvider = googleFontProvider, weight = FontWeight.W600),
    Font(googleFont = OpenSansFont, fontProvider = googleFontProvider, weight = FontWeight.W700)
)
