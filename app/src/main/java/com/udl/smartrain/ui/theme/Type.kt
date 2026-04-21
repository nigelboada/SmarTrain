package com.udl.smartrain.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.udl.smartrain.R
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val fontBebas = GoogleFont("Bebas Neue")
val fontInter = GoogleFont("Inter")

val FontFamilyTitles = FontFamily(Font(googleFont = fontBebas, fontProvider = provider))
val FontFamilyBody = FontFamily(Font(googleFont = fontInter, fontProvider = provider))

val Typography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamilyTitles, fontSize = 32.sp),
    titleMedium = TextStyle(fontFamily = FontFamilyTitles, fontSize = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamilyBody, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = FontFamilyBody, fontSize = 12.sp)
)