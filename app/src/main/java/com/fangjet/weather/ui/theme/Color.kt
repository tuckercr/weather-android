package com.fangjet.weather.ui.theme

import androidx.compose.ui.graphics.Color

// Light scheme. The brand blue (#0288D1, still used for the splash and launcher background) is
// only 3.7:1 on the light surface, short of WCAG AA for text, so the light theme's primary is the
// darker SkyBlueDark: 7.1:1 on the surface, 5.1:1 on SkyBlueSurface cards, 7.6:1 under white.
val SkyBlueDark = Color(0xFF01579B)
val SkyBlueContainer = Color(0xFF81C7EC)
val SkyBlueSurface = Color(0xFFB3DDF2)

// Accent drawn from the mascot's golden dog, replacing the previous off-brand teal.
val BrandYellow = Color(0xFFFBC02D)
val OnBrandYellow = Color(0xFF3A2E00)
val SurfaceLight = Color(0xFFF6FBFF)

// Dark scheme
val SkyBlueNight = Color(0xFF4FC3F7)
val BrandYellowNight = Color(0xFFFFD54F)
val SurfaceDark = Color(0xFF0D1B2A)
val SurfaceContainerDark = Color(0xFF1A2C3D)

// Radar precipitation legend, light (low intensity) to heavy (high intensity).
val RadarLegendColors = listOf(
    Color(0xFF8CD9FF),
    Color(0xFF2E9BE6),
    Color(0xFF39C24A),
    Color(0xFFF4E04D),
    Color(0xFFF39B2E),
    Color(0xFFE24B4B),
)

// Pet scene: hero card backgrounds per weather mood. The hero keeps its own light palette in
// both themes, so text on it always uses PetInk.
val PetSkySunny = Color(0xFFFAC775)
val PetSkyCloudy = Color(0xFFB3DDF2)
val PetSkyRain = Color(0xFF81C7EC)
val PetSkyStorm = Color(0xFF9FB3C8)
val PetSkySnow = Color(0xFFE6F1FB)
val PetSkyHot = Color(0xFFF5C4B3)
val PetSkyCold = Color(0xFFCFE3F5)
val PetSkyNight = Color(0xFF3C4A78)
val PetInk = Color(0xFF0C447C)
val PetInkOnNight = Color(0xFFF1EFE8)

// Pet scene: placeholder illustration colours (replaced by real art later).
val PetCatFur = Color(0xFF5F5E5A)
val PetCatBelly = Color(0xFFF1EFE8)
val PetDogFur = Color(0xFFE9B158)
val PetDogFace = Color(0xFFFAC775)
val PetDogEar = Color(0xFFBA7517)
val PetOutline = Color(0xFF10324A)
val PetUmbrella = Color(0xFF185FA5)
val PetSun = Color(0xFFEF9F27)
val PetSunHot = Color(0xFFD85A30)
val PetScarf = Color(0xFFD85A30)
val PetScarfAlt = Color(0xFF993556)
val PetTongue = Color(0xFFD4537E)
val PetCloud = Color(0xFFFFFFFF)
val PetStormCloud = Color(0xFF5F6B7A)
val PetBolt = Color(0xFFFAC775)
val PetMoon = Color(0xFFF1EFE8)

// The moon's unlit part on the night sky: just darker than PetSkyNight, so the disc shows faintly.
val PetMoonUnlit = Color(0xFF34416E)
val PetGround = Color(0x33000000)
val PetRouter = Color(0xFF4A5A6A)
val PetRouterLight = Color(0xFF97C459)
val PetCatNose = Color(0xFFE8849A)
val PetCollar = Color(0xFF185FA5)
val PetShine = Color(0xFFFFFFFF)

// Seasonal decorations (SeasonalTheme).
val PetBat = Color(0xFF412402)
val PetBatNight = Color(0xFF0B0B0B)
val PetBatPurple = Color(0xFF7F77DD)
val PetStar = Color(0xFFCECBF6)
val PetWitchHat = Color(0xFF26215C)
val PetHatBand = Color(0xFFEF9F27)
val PetBandana = Color(0xFFD85A30)
val PetPumpkin = Color(0xFFD85A30)
val PetPumpkinLit = Color(0xFFEF9F27)
val PetPumpkinFace = Color(0xFF412402)
val PetPumpkinGlow = Color(0xFFFAEEDA)
val PetPumpkinStem = Color(0xFF3B6D11)

// "Showing weather from ..." banner over cached data (background, text: 8.9:1). Fixed in both
// themes, like the hero card it sits above.
val StaleBannerBg = Color(0xFFFAEEDA)
val StaleBannerText = Color(0xFF633806)

// Walk-rating chips (background, text).
val WalkGreatBg = Color(0xFFC0DD97)
val WalkGreatText = Color(0xFF27500A)
val WalkOkayBg = Color(0xFFFAC775)
val WalkOkayText = Color(0xFF633806)
val WalkPoorBg = Color(0xFFD3D1C7)
val WalkPoorText = Color(0xFF444441)
val WalkHotBg = Color(0xFFF7C1C1)
val WalkHotText = Color(0xFF791F1F)
val WalkColdBg = Color(0xFFB5D4F4)
val WalkColdText = Color(0xFF0C447C)
