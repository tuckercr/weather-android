package com.fangjet.weather.ui.pets

import android.graphics.Bitmap
import androidx.annotation.ArrayRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.fangjet.weather.R
import com.fangjet.weather.domain.SceneDecoration
import com.fangjet.weather.ui.LocalSouthernHemisphere
import com.fangjet.weather.ui.MoonPhase
import com.fangjet.weather.ui.moonPhaseAt
import com.fangjet.weather.ui.theme.PetBandana
import com.fangjet.weather.ui.theme.PetBat
import com.fangjet.weather.ui.theme.PetBatNight
import com.fangjet.weather.ui.theme.PetBatPurple
import com.fangjet.weather.ui.theme.PetBolt
import com.fangjet.weather.ui.theme.PetCatBelly
import com.fangjet.weather.ui.theme.PetCatFur
import com.fangjet.weather.ui.theme.PetCatInnerEar
import com.fangjet.weather.ui.theme.PetCatNose
import com.fangjet.weather.ui.theme.PetCloud
import com.fangjet.weather.ui.theme.PetCollar
import com.fangjet.weather.ui.theme.PetDogEar
import com.fangjet.weather.ui.theme.PetDogFace
import com.fangjet.weather.ui.theme.PetDogFur
import com.fangjet.weather.ui.theme.PetGround
import com.fangjet.weather.ui.theme.PetHatBand
import com.fangjet.weather.ui.theme.PetInk
import com.fangjet.weather.ui.theme.PetInkOnNight
import com.fangjet.weather.ui.theme.PetMoon
import com.fangjet.weather.ui.theme.PetMoonUnlit
import com.fangjet.weather.ui.theme.PetMouth
import com.fangjet.weather.ui.theme.PetOutline
import com.fangjet.weather.ui.theme.PetPumpkin
import com.fangjet.weather.ui.theme.PetPumpkinFace
import com.fangjet.weather.ui.theme.PetPumpkinGlow
import com.fangjet.weather.ui.theme.PetPumpkinLit
import com.fangjet.weather.ui.theme.PetPumpkinStem
import com.fangjet.weather.ui.theme.PetRouter
import com.fangjet.weather.ui.theme.PetRouterLight
import com.fangjet.weather.ui.theme.PetScarf
import com.fangjet.weather.ui.theme.PetScarfAlt
import com.fangjet.weather.ui.theme.PetShine
import com.fangjet.weather.ui.theme.PetSkyCloudy
import com.fangjet.weather.ui.theme.PetSkyCold
import com.fangjet.weather.ui.theme.PetSkyHot
import com.fangjet.weather.ui.theme.PetSkyNight
import com.fangjet.weather.ui.theme.PetSkyRain
import com.fangjet.weather.ui.theme.PetSkySnow
import com.fangjet.weather.ui.theme.PetSkyStorm
import com.fangjet.weather.ui.theme.PetSkySunny
import com.fangjet.weather.ui.theme.PetStar
import com.fangjet.weather.ui.theme.PetStormCloud
import com.fangjet.weather.ui.theme.PetSun
import com.fangjet.weather.ui.theme.PetSunHot
import com.fangjet.weather.ui.theme.PetTongue
import com.fangjet.weather.ui.theme.PetTongueSoft
import com.fangjet.weather.ui.theme.PetUmbrella
import com.fangjet.weather.ui.theme.PetWitchHat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.cos

// The scene is authored on a 260 x 150 grid and scaled to fit.
private const val SCENE_W = 260f
private const val SCENE_H = 150f

val PetMood.skyColor: Color
    get() = when (this) {
        PetMood.SUNNY -> PetSkySunny
        PetMood.CLOUDY -> PetSkyCloudy
        PetMood.RAIN -> PetSkyRain
        PetMood.STORM -> PetSkyStorm
        PetMood.SNOW -> PetSkySnow
        PetMood.HOT -> PetSkyHot
        PetMood.COLD -> PetSkyCold
        PetMood.NIGHT -> PetSkyNight
    }

val PetMood.inkColor: Color
    get() = if (this == PetMood.NIGHT) PetInkOnNight else PetInk

/**
 * The part of the night it is, by the city's local hour. The night scene runs from sunset to
 * sunrise, so any night hour before noon is before dawn and any after noon is evening.
 */
enum class NightPart { EVENING, LATE_NIGHT, BEFORE_DAWN }

fun nightPartAt(localHour: Int): NightPart =
    when (localHour) {
        23, in 0 until 5 -> NightPart.LATE_NIGHT
        in 5 until 12 -> NightPart.BEFORE_DAWN
        else -> NightPart.EVENING
    }

@ArrayRes
private fun PetMood.captionsRes(nightPart: NightPart): Int =
    when (this) {
        PetMood.SUNNY -> R.array.pet_caption_sunny
        PetMood.CLOUDY -> R.array.pet_caption_cloudy
        PetMood.RAIN -> R.array.pet_caption_rain
        PetMood.STORM -> R.array.pet_caption_storm
        PetMood.SNOW -> R.array.pet_caption_snow
        PetMood.HOT -> R.array.pet_caption_hot
        PetMood.COLD -> R.array.pet_caption_cold
        PetMood.NIGHT -> when (nightPart) {
            NightPart.EVENING -> R.array.pet_caption_night_evening
            NightPart.LATE_NIGHT -> R.array.pet_caption_night_late
            NightPart.BEFORE_DAWN -> R.array.pet_caption_night_before_dawn
        }
    }

/**
 * One caption per mood per day, so it varies without flickering between recompositions. The day
 * and (at night) the hour are the city's, from its [utcOffsetSeconds], not the device's.
 */
@Composable
fun petCaption(
    mood: PetMood,
    utcOffsetSeconds: Int = 0,
): String {
    val local = OffsetDateTime.now(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
    val options = stringArrayResource(mood.captionsRes(nightPartAt(local.hour)))
    return options[local.dayOfYear % options.size]
}

/**
 * Sunny (the dog) and Misty (the cat) acting out [mood], drawn with simple shapes after the app
 * icon: a golden retriever with a blue collar and a grey-and-white tuxedo cat. [decorations] are
 * today's seasonal theme's extras (costumes, a jack-o'-lantern, bats). Same shapes and colours as
 * iOS's PetScene.
 */
@Composable
fun PetScene(
    mood: PetMood,
    modifier: Modifier = Modifier,
    moonPhase: MoonPhase = moonPhaseAt(
        java.time.Instant
            .now()
            .epochSecond,
    ),
    // The offline error: the Wi-Fi router lies on its side next to Misty.
    knockedOverRouter: Boolean = false,
    decorations: Set<SceneDecoration> = emptySet(),
) {
    val description = stringResource(R.string.cd_pet_scene)
    val southernHemisphere = LocalSouthernHemisphere.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(SCENE_W / SCENE_H)
            .semantics { contentDescription = description },
    ) {
        drawPetScene(mood, size.width / SCENE_W, moonPhase, southernHemisphere, knockedOverRouter, decorations)
    }
}

/**
 * The scene, drawn at [s] pixels per scene unit with its top-left at the current origin. Shared by
 * [PetScene] and [petSceneIcon]. The sky's background colour is left to the caller.
 */
private fun DrawScope.drawPetScene(
    mood: PetMood,
    s: Float,
    moonPhase: MoonPhase,
    southernHemisphere: Boolean,
    knockedOverRouter: Boolean = false,
    decorations: Set<SceneDecoration> = emptySet(),
) {
    fun p(
        x: Float,
        y: Float,
    ) = Offset(x * s, y * s)

    drawSky(mood, s, ::p, moonPhase, southernHemisphere)
    if (SceneDecoration.BATS in decorations) drawBats(mood == PetMood.NIGHT, s)
    drawOval(PetGround, topLeft = p(20f, 132f), size = Size(220f * s, 16f * s))
    // The pets are authored cat-left, dog-right; mirrored so Sunny (the dog) stands on the left,
    // matching the name. The sky stays put, and the ground shadow is symmetrical.
    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(SCENE_W / 2 * s, 0f)) {
        drawCat(mood, s, ::p)
        drawDog(mood, s, ::p)
        // A costume replaces the weather's own props (sunglasses, scarves); the umbrella stays.
        val dressedUp = mood != PetMood.NIGHT &&
            (SceneDecoration.WITCH_HAT in decorations || SceneDecoration.BANDANA in decorations)
        if (!dressedUp || mood == PetMood.RAIN) drawProps(mood, s, ::p)
        drawDecorations(decorations, mood, s, ::p)
    }
    if (knockedOverRouter) drawKnockedOverRouter(s, ::p)
}

/**
 * The pets in [mood] on their sky colour, as a square bitmap: a notification's large icon. A square
 * cut from the middle of the scene, around the pets, so they stay recognisable at 64 dp.
 */
fun petSceneIcon(
    mood: PetMood,
    sizePx: Int,
    southernHemisphere: Boolean,
    moonPhase: MoonPhase = moonPhaseAt(
        java.time.Instant
            .now()
            .epochSecond,
    ),
): Bitmap {
    val image = ImageBitmap(sizePx, sizePx)
    val s = sizePx / ICON_SIDE
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(sizePx.toFloat(), sizePx.toFloat())) {
        drawRect(mood.skyColor)
        translate(left = -ICON_LEFT * s, top = -ICON_TOP * s) {
            drawPetScene(mood, s, moonPhase, southernHemisphere)
        }
    }
    return image.asAndroidBitmap()
}

// The icon's square, in scene units: tight round the pets (x 66..194, ears to ground y 40..148), so
// they fill the 64 dp icon. The sun and moon fall outside it; the pets' props still show the mood.
private const val ICON_SIDE = 134f
private const val ICON_LEFT = 63f
private const val ICON_TOP = 24f

private fun DrawScope.drawKnockedOverRouter(
    s: Float,
    p: (Float, Float) -> Offset,
) {
    // Tipped back on the ground to Misty's right, antennas splayed.
    rotate(degrees = -18f, pivot = p(216f, 128f)) {
        drawLine(PetOutline, p(202f, 122f), p(196f, 104f), strokeWidth = 2.5f * s)
        drawLine(PetOutline, p(230f, 122f), p(236f, 106f), strokeWidth = 2.5f * s)
        drawRoundRect(PetRouter, p(196f, 120f), Size(40f * s, 14f * s), CornerRadius(3f * s))
        listOf(206f, 214f, 222f).forEach { x -> drawCircle(PetRouterLight, 1.8f * s, p(x, 127f)) }
    }
}

private fun DrawScope.oval(
    color: Color,
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    s: Float,
) = drawOval(color, topLeft = Offset((cx - rx) * s, (cy - ry) * s), size = Size(rx * 2 * s, ry * 2 * s))

private fun DrawScope.poly(
    color: Color,
    s: Float,
    vararg pts: Float,
) {
    val path = Path().apply {
        moveTo(pts[0] * s, pts[1] * s)
        for (i in 2 until pts.size step 2) lineTo(pts[i] * s, pts[i + 1] * s)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSky(
    mood: PetMood,
    s: Float,
    p: (Float, Float) -> Offset,
    moonPhase: MoonPhase,
    southernHemisphere: Boolean,
) {
    when (mood) {
        PetMood.SUNNY -> drawCircle(PetSun, 18f * s, p(220f, 32f))
        PetMood.HOT -> drawCircle(PetSunHot, 24f * s, p(220f, 32f))
        PetMood.NIGHT -> drawMoon(moonPhase, southernHemisphere, p(215f, 30f), 16f * s)
        PetMood.CLOUDY -> cloud(PetCloud, 210f, 30f, s)
        PetMood.RAIN -> listOf(30f to 14f, 60f to 36f, 200f to 58f, 232f to 20f, 45f to 70f, 245f to 44f)
            .forEach { (x, y) -> drawRoundRect(PetUmbrella, p(x, y), Size(3f * s, 11f * s)) }
        PetMood.STORM -> {
            cloud(PetStormCloud, 200f, 28f, s)
            poly(PetBolt, s, 205f, 40f, 196f, 58f, 204f, 58f, 198f, 74f, 214f, 52f, 206f, 52f, 212f, 40f)
        }
        PetMood.SNOW, PetMood.COLD -> listOf(30f to 18f, 70f to 44f, 200f to 22f, 240f to 50f, 110f to 12f, 180f to 40f)
            .forEach { (x, y) -> drawCircle(PetCloud, 4f * s, p(x, y)) }
    }
}

/**
 * The moon at [phase]: the unlit disc, the lit half, then the terminator, an ellipse that adds to
 * the lit half (gibbous) or cuts into it (crescent). Waxing is lit on the right in the north and
 * on the left in the south.
 */
private fun DrawScope.drawMoon(
    phase: MoonPhase,
    southernHemisphere: Boolean,
    centre: Offset,
    radius: Float,
) {
    drawCircle(PetMoonUnlit, radius, centre)
    when (phase) {
        MoonPhase.NEW -> return
        MoonPhase.FULL -> {
            drawCircle(PetMoon, radius, centre)
            return
        }
        else -> Unit
    }
    val litOnRight = phase.isWaxing != southernHemisphere
    clipRect(
        left = if (litOnRight) centre.x else centre.x - radius,
        top = centre.y - radius,
        right = if (litOnRight) centre.x + radius else centre.x,
        bottom = centre.y + radius,
    ) { drawCircle(PetMoon, radius, centre) }
    val rx = abs(cos(Math.toRadians(phase.drawAngleDegrees.toDouble()))).toFloat() * radius
    drawOval(
        if (phase.isGibbous) PetMoon else PetMoonUnlit,
        topLeft = Offset(centre.x - rx, centre.y - radius),
        size = Size(rx * 2, radius * 2),
    )
}

private fun DrawScope.cloud(
    color: Color,
    cx: Float,
    cy: Float,
    s: Float,
) {
    oval(color, cx, cy + 6f, 28f, 12f, s)
    drawCircle(color, 13f * s, Offset((cx - 10f) * s, cy * s))
    drawCircle(color, 16f * s, Offset((cx + 8f) * s, (cy - 4f) * s))
}

private fun DrawScope.drawCat(
    mood: PetMood,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    // A storm sends the cat low (hiding); otherwise it sits upright.
    val dy = if (mood == PetMood.STORM) 14f else 0f
    oval(PetCatFur, 95f, 112f + dy / 2, 24f, 26f - dy / 2, s)
    drawCircle(PetCatFur, 20f * s, p(95f, 80f + dy))
    poly(PetCatFur, s, 78f, 68f + dy, 82f, 50f + dy, 92f, 64f + dy)
    poly(PetCatFur, s, 98f, 64f + dy, 108f, 50f + dy, 112f, 68f + dy)
    // A tuxedo cat, as on the app icon: pink inner ears, a white blaze into white cheeks, a small
    // pink nose, a "w" mouth and whiskers.
    poly(PetCatInnerEar, s, 81f, 64f + dy, 83.5f, 55f + dy, 89f, 62f + dy)
    poly(PetCatInnerEar, s, 101f, 62f + dy, 106.5f, 55f + dy, 109f, 64f + dy)
    poly(PetCatBelly, s, 93f, 66f + dy, 97f, 66f + dy, 101f, 86f + dy, 89f, 86f + dy)
    oval(PetCatBelly, 90f, 90f + dy, 7.5f, 6.5f, s)
    oval(PetCatBelly, 100f, 90f + dy, 7.5f, 6.5f, s)
    oval(PetCatBelly, 95f, 118f + dy / 2, 12f, 14f - dy / 3, s)
    poly(PetCatNose, s, 92.6f, 85.5f + dy, 97.4f, 85.5f + dy, 95f, 88.6f + dy)
    val mouth = Path().apply {
        moveTo(95f * s, (88.6f + dy) * s)
        lineTo(95f * s, (90.3f + dy) * s)
        moveTo(91.6f * s, (90.3f + dy) * s)
        quadraticTo(93.3f * s, (92.6f + dy) * s, 95f * s, (90.3f + dy) * s)
        quadraticTo(96.7f * s, (92.6f + dy) * s, 98.4f * s, (90.3f + dy) * s)
    }
    drawPath(mouth, PetOutline, style = Stroke(width = 1.1f * s, cap = StrokeCap.Round))
    // Dark whiskers vanish into the night sky, so they're pale after dark.
    val whisker = if (mood == PetMood.NIGHT) PetCatBelly.copy(alpha = 0.6f) else PetOutline.copy(alpha = 0.75f)
    listOf(85f to 89f to (71f to 86.5f), 85f to 91.5f to (71f to 92.5f), 105f to 89f to (119f to 86.5f), 105f to 91.5f to (119f to 92.5f))
        .forEach { (from, to) ->
            drawLine(whisker, p(from.first, from.second + dy), p(to.first, to.second + dy), 0.8f * s, StrokeCap.Round)
        }
    eyes(mood, 88f, 102f, 78f + dy, s)
}

private fun DrawScope.drawDog(
    mood: PetMood,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    oval(PetDogFur, 160f, 110f, 28f, 28f, s)
    // A rim of body fur round the face, so it doesn't vanish into the sunny sky (the same colour).
    drawCircle(PetDogFur, 25.5f * s, p(160f, 76f))
    // A golden retriever, as on the app icon. The blue collar dips under the chin, behind the
    // face, with its ends under the floppy ears and its sun tag hanging at the front.
    val collar = Path().apply {
        moveTo(140f * s, 94f * s)
        quadraticTo(160f * s, 109f * s, 180f * s, 94f * s)
        lineTo(180f * s, 99f * s)
        quadraticTo(160f * s, 115f * s, 140f * s, 99f * s)
        close()
    }
    drawPath(collar, PetCollar)
    drawCircle(PetDogFace, 23f * s, p(160f, 76f))
    rotate(degrees = 12f, pivot = p(139f, 70f)) { oval(PetDogEar, 139f, 80f, 8f, 15f, s) }
    rotate(degrees = -12f, pivot = p(181f, 70f)) { oval(PetDogEar, 181f, 80f, 8f, 15f, s) }
    drawCircle(PetDogEar, 4.2f * s, p(160f, 109.5f))
    drawCircle(PetDogFace, 3.3f * s, p(160f, 109.5f))
    drawCircle(PetShine, 1.4f * s, p(160f, 109.5f))
    val brows = Path().apply {
        listOf(152f, 168f).forEach { x ->
            moveTo((x - 3.5f) * s, 66f * s)
            quadraticTo(x * s, 63.5f * s, (x + 3.5f) * s, 66f * s)
        }
    }
    drawPath(brows, PetDogEar, style = Stroke(width = 1.6f * s, cap = StrokeCap.Round))
    eyes(mood, 152f, 168f, 72f, s)
    oval(PetOutline, 160f, 81f, 5.6f, 4f, s)
    if (mood == PetMood.NIGHT) {
        // Asleep: the mouth is closed.
        val smile = Path().apply {
            moveTo(154f * s, 88f * s)
            quadraticTo(160f * s, 93f * s, 166f * s, 88f * s)
        }
        drawPath(smile, PetOutline, style = Stroke(width = 1.8f * s, cap = StrokeCap.Round))
    } else {
        oval(PetShine.copy(alpha = 0.6f), 158.4f, 79.8f, 1.6f, 0.9f, s)
        val mouth = Path().apply {
            moveTo(153f * s, 86.5f * s)
            quadraticTo(160f * s, 97f * s, 167f * s, 86.5f * s)
            close()
        }
        drawPath(mouth, PetMouth)
        // Panting in the heat: the tongue hangs out.
        if (mood == PetMood.HOT) oval(PetTongue, 160f, 95f, 3.8f, 6f, s) else oval(PetTongueSoft, 160f, 92.2f, 3.4f, 2.6f, s)
    }
}

/** Open eyes with a catch-light, or closed, smiling curves when the pets are asleep at night. */
private fun DrawScope.eyes(
    mood: PetMood,
    lx: Float,
    rx: Float,
    y: Float,
    s: Float,
) {
    listOf(lx, rx).forEach { x ->
        if (mood == PetMood.NIGHT) {
            val closed = Path().apply {
                moveTo((x - 3f) * s, y * s)
                quadraticTo(x * s, (y + 2f) * s, (x + 3f) * s, y * s)
            }
            drawPath(closed, PetOutline, style = Stroke(width = 1.8f * s, cap = StrokeCap.Round))
        } else {
            drawCircle(PetOutline, 2.95f * s, Offset(x * s, y * s))
            drawCircle(PetShine, 0.95f * s, Offset((x + 0.9f) * s, (y - 0.9f) * s))
        }
    }
}

/** A bat: two wings meeting at the body, [width] scene units across. */
private fun DrawScope.bat(
    color: Color,
    x: Float,
    y: Float,
    width: Float,
    s: Float,
) {
    val w = width / 2
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticTo((x + w / 2) * s, (y - w) * s, (x + w) * s, y * s)
        quadraticTo((x + w * 1.5f) * s, (y - w) * s, (x + width) * s, y * s)
        quadraticTo((x + w * 1.5f) * s, (y + w * 0.2f) * s, (x + w) * s, (y + w * 0.7f) * s)
        quadraticTo((x + w / 2) * s, (y + w * 0.2f) * s, x * s, y * s)
        close()
    }
    drawPath(path, color)
}

/** Bats by day; by night bats and a few stars round the moon. */
private fun DrawScope.drawBats(
    night: Boolean,
    s: Float,
) {
    if (night) {
        listOf(30f to 16f, 78f to 30f, 140f to 12f).forEach { (x, y) -> drawCircle(PetStar, 1.4f * s, Offset(x * s, y * s)) }
        bat(PetBatNight, 168f, 24f, 22f, s)
        bat(PetBatPurple, 30f, 54f, 18f, s)
    } else {
        bat(PetBat, 24f, 34f, 22f, s)
        bat(PetBat, 188f, 62f, 16f, s)
    }
}

/**
 * The costumes and props in [decorations]. Asleep at night the pets have taken their costumes off;
 * the jack-o'-lantern on Misty's side is lit then. Drawn in the pets' mirrored space, so Misty is
 * at x 95 and the pumpkin at x 38.
 */
private fun DrawScope.drawDecorations(
    decorations: Set<SceneDecoration>,
    mood: PetMood,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    val night = mood == PetMood.NIGHT
    val dy = if (mood == PetMood.STORM) 14f else 0f
    if (SceneDecoration.WITCH_HAT in decorations && !night) {
        poly(PetWitchHat, s, 80f, 62f + dy, 99f, 22f + dy, 111f, 62f + dy)
        oval(PetWitchHat, 95f, 63f + dy, 27f, 4.5f, s)
        drawRect(PetHatBand, p(81f, 56f + dy), Size(29f * s, 5f * s))
    }
    if (SceneDecoration.BANDANA in decorations && !night) {
        poly(PetBandana, s, 142f, 97f, 178f, 97f, 160f, 111f)
        drawCircle(PetPumpkinFace, 1.6f * s, p(153f, 101f))
        drawCircle(PetPumpkinFace, 1.6f * s, p(167f, 101f))
    }
    if (SceneDecoration.JACK_O_LANTERN in decorations) {
        val face = if (night) PetPumpkinGlow else PetPumpkinFace
        oval(if (night) PetPumpkinLit else PetPumpkin, 38f, 126f, 21f, 16f, s)
        drawRect(PetPumpkinStem, p(35.5f, 104f), Size(5f * s, 8f * s))
        poly(face, s, 26f, 123f, 31f, 116f, 36f, 123f)
        poly(face, s, 40f, 123f, 45f, 116f, 50f, 123f)
        poly(face, s, 25f, 130f, 51f, 130f, 47f, 136f, 43f, 133f, 38f, 137f, 33f, 133f, 29f, 136f)
    }
}

private fun DrawScope.drawProps(
    mood: PetMood,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    when (mood) {
        PetMood.RAIN -> {
            val canopy = Path().apply {
                moveTo(70f * s, 56f * s)
                quadraticTo(128f * s, 0f, 190f * s, 56f * s)
                close()
            }
            drawPath(canopy, PetUmbrella)
            drawRect(PetOutline, p(128f, 54f), Size(3f * s, 42f * s))
        }
        PetMood.SUNNY -> {
            drawRoundRect(PetOutline, p(146f, 68f), Size(12f * s, 7f * s))
            drawRoundRect(PetOutline, p(162f, 68f), Size(12f * s, 7f * s))
            drawRect(PetOutline, p(157f, 70f), Size(6f * s, 2f * s))
        }
        PetMood.SNOW, PetMood.COLD -> {
            drawRoundRect(PetScarf, p(136f, 94f), Size(48f * s, 9f * s))
            drawRoundRect(PetScarfAlt, p(72f, 96f), Size(46f * s, 8f * s))
        }
        else -> Unit
    }
}
