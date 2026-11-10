package com.fangjet.weather.ui.pets

import android.graphics.Bitmap
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
import com.fangjet.weather.domain.PetAccessory
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.SceneDecoration
import com.fangjet.weather.domain.sceneAccessories
import com.fangjet.weather.model.AFTERNOON_ONLY
import com.fangjet.weather.model.HeroCaption
import com.fangjet.weather.model.WALK_SUGGESTIONS
import com.fangjet.weather.model.captionOptions
import com.fangjet.weather.ui.LocalSouthernHemisphere
import com.fangjet.weather.ui.MoonPhase
import com.fangjet.weather.ui.moonPhaseAt
import com.fangjet.weather.ui.screenNowEpochSeconds
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
import com.fangjet.weather.ui.theme.PetLeafGold
import com.fangjet.weather.ui.theme.PetLeafOrange
import com.fangjet.weather.ui.theme.PetLeafRust
import com.fangjet.weather.ui.theme.PetLeafVein
import com.fangjet.weather.ui.theme.PetMoon
import com.fangjet.weather.ui.theme.PetMoonUnlit
import com.fangjet.weather.ui.theme.PetOutline
import com.fangjet.weather.ui.theme.PetPieCrust
import com.fangjet.weather.ui.theme.PetPieTop
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
import com.fangjet.weather.ui.theme.PetSkyFair
import com.fangjet.weather.ui.theme.PetSkyHot
import com.fangjet.weather.ui.theme.PetSkyNight
import com.fangjet.weather.ui.theme.PetSkyRain
import com.fangjet.weather.ui.theme.PetSkySnow
import com.fangjet.weather.ui.theme.PetSkyStorm
import com.fangjet.weather.ui.theme.PetSkySunny
import com.fangjet.weather.ui.theme.PetStar
import com.fangjet.weather.ui.theme.PetSteam
import com.fangjet.weather.ui.theme.PetStormCloud
import com.fangjet.weather.ui.theme.PetSun
import com.fangjet.weather.ui.theme.PetSunHot
import com.fangjet.weather.ui.theme.PetTongue
import com.fangjet.weather.ui.theme.PetUmbrella
import com.fangjet.weather.ui.theme.PetWitchHat
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos

// The scene is authored on a 260 x 150 grid and scaled to fit.
private const val SCENE_W = 260f
private const val SCENE_H = 150f

val PetMood.skyColor: Color
    get() = when (this) {
        PetMood.FAIR -> PetSkyFair
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

/** The caption's words: the choice comes worked out in [HeroCaption]. */
@Composable
fun HeroCaption.resolve(): String =
    when (this) {
        is HeroCaption.Walk -> text.resolve()
        is HeroCaption.Theme -> text
        is HeroCaption.Weather -> {
            val suggestions = WALK_SUGGESTIONS.map { stringResource(it) }.toSet()
            val options = captionOptions(
                stringArrayResource(captions).toList(),
                stringArrayResource(noDogCaptions).toList(),
                suggestions,
                leaveOutWalkSuggestions,
                AFTERNOON_ONLY.map { stringResource(it) }.toSet(),
                leaveOutAfternoonOnly,
            )
            options[day % options.size]
        }
    }

/**
 * Sunny (the dog) and Misty (the cat) acting out [mood], drawn with simple shapes after the app
 * icon: a golden retriever with a blue collar and a grey-and-white tuxedo cat. [decorations] are
 * today's seasonal theme's extras (costumes, a jack-o'-lantern, bats, leaves, a pie), whose night
 * look follows [dark], whether it's dark at the city by the clock, rather than the mood: on a rainy
 * Halloween evening the pumpkin is lit and the costumes are off, under the umbrella. Same shapes and
 * colours as iOS's PetScene.
 */
@Composable
fun PetScene(
    mood: PetMood,
    modifier: Modifier = Modifier,
    // The phase at the screen's clock, or the device's outside the weather screen.
    moonPhase: MoonPhase = moonPhaseAt(screenNowEpochSeconds()),
    // The offline error: the Wi-Fi router lies on its side next to Misty.
    knockedOverRouter: Boolean = false,
    decorations: Set<SceneDecoration> = emptySet(),
    dark: Boolean = mood == PetMood.NIGHT,
) {
    val description = stringResource(R.string.cd_pet_scene)
    val southernHemisphere = LocalSouthernHemisphere.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(SCENE_W / SCENE_H)
            .semantics { contentDescription = description },
    ) {
        drawPetScene(mood, size.width / SCENE_W, moonPhase, southernHemisphere, knockedOverRouter, decorations, dark)
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
    dark: Boolean = mood == PetMood.NIGHT,
) {
    fun p(
        x: Float,
        y: Float,
    ) = Offset(x * s, y * s)

    drawSky(mood, s, ::p, moonPhase, southernHemisphere)
    if (SceneDecoration.BATS in decorations) drawBats(dark, s)
    if (SceneDecoration.AUTUMN_LEAVES in decorations) drawAutumnLeaves(dark, s)
    drawOval(PetGround, topLeft = p(20f, 132f), size = Size(220f * s, 16f * s))
    // The pets are authored cat-left, dog-right; mirrored so Sunny (the dog) stands on the left,
    // matching the name. The sky stays put, and the ground shadow is symmetrical.
    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(SCENE_W / 2 * s, 0f)) {
        drawCat(mood, s, ::p)
        drawDog(mood, s, ::p)
        drawGroundDecorations(decorations, dark, s, ::p)
        drawAccessories(sceneAccessories(mood, decorations, dark), mood, s, ::p)
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
        PetMood.FAIR, PetMood.SUNNY -> drawCircle(PetSun, 18f * s, p(220f, 32f))
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
    oval(PetOutline, 160f, 82f, 5f, 3.5f, s)
    if (mood == PetMood.HOT) {
        // Panting in the heat: the tongue hangs out.
        oval(PetTongue, 160f, 94f, 4f, 7f, s)
    } else {
        val smile = Path().apply {
            moveTo(154f * s, 88f * s)
            quadraticTo(160f * s, 94f * s, 166f * s, 88f * s)
        }
        drawPath(smile, PetOutline, style = Stroke(width = 2f * s))
    }
}

/** Open eyes, or closed lines when the pets are asleep at night. */
private fun DrawScope.eyes(
    mood: PetMood,
    lx: Float,
    rx: Float,
    y: Float,
    s: Float,
) {
    listOf(lx, rx).forEach { x ->
        if (mood == PetMood.NIGHT) {
            drawLine(PetOutline, Offset((x - 3f) * s, y * s), Offset((x + 3f) * s, y * s), strokeWidth = 2f * s)
        } else {
            drawCircle(PetOutline, 2.6f * s, Offset(x * s, y * s))
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
 * The theme's things on the ground on Misty's side: the jack-o'-lantern, lit after dark, and the
 * pie, steaming by day. Drawn in the pets' mirrored space, so Misty is at x 95 and they're at x 38.
 */
private fun DrawScope.drawGroundDecorations(
    decorations: Set<SceneDecoration>,
    dark: Boolean,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    if (SceneDecoration.JACK_O_LANTERN in decorations) {
        val face = if (dark) PetPumpkinGlow else PetPumpkinFace
        oval(if (dark) PetPumpkinLit else PetPumpkin, 38f, 126f, 21f, 16f, s)
        drawRect(PetPumpkinStem, p(35.5f, 104f), Size(5f * s, 8f * s))
        poly(face, s, 26f, 123f, 31f, 116f, 36f, 123f)
        poly(face, s, 40f, 123f, 45f, 116f, 50f, 123f)
        poly(face, s, 25f, 130f, 51f, 130f, 47f, 136f, 43f, 133f, 38f, 137f, 33f, 133f, 29f, 136f)
    }
    if (SceneDecoration.PIE in decorations) drawPie(dark, s, p)
}

/** A pie in its crust, seen from a little above, with a wisp of steam by day. */
private fun DrawScope.drawPie(
    dark: Boolean,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    // The dish's side, then the filling with the crimped rim round it and two vents.
    poly(PetPieCrust, s, 19f, 128f, 57f, 128f, 53f, 139f, 23f, 139f)
    oval(PetPieTop, 38f, 128f, 18f, 5f, s)
    drawOval(
        PetPieCrust,
        topLeft = p(20f, 123f),
        size = Size(36f * s, 10f * s),
        style = Stroke(width = 2.4f * s),
    )
    listOf(31f to 127f, 43f to 128.5f).forEach { (x, y) ->
        drawLine(PetPumpkinFace.copy(alpha = 0.5f), p(x, y), p(x + 4f, y - 1f), 1.2f * s, StrokeCap.Round)
    }
    if (!dark) {
        val steam = Path().apply {
            moveTo(33f * s, 120f * s)
            quadraticTo(29f * s, 115f * s, 33f * s, 110f * s)
            quadraticTo(37f * s, 105f * s, 33f * s, 100f * s)
            moveTo(42f * s, 119f * s)
            quadraticTo(38f * s, 114.5f * s, 42f * s, 110f * s)
            quadraticTo(46f * s, 105.5f * s, 42f * s, 101f * s)
        }
        drawPath(steam, PetSteam.copy(alpha = 0.7f), style = Stroke(width = 1.6f * s, cap = StrokeCap.Round))
    }
}

/** A leaf [length] scene units from tip to tip, turned [degrees], with a pale midrib. */
private fun DrawScope.leaf(
    color: Color,
    x: Float,
    y: Float,
    length: Float,
    degrees: Float,
    alpha: Float,
    s: Float,
) {
    val h = length / 2
    rotate(degrees = degrees, pivot = Offset(x * s, y * s)) {
        val path = Path().apply {
            moveTo((x - h) * s, y * s)
            quadraticTo(x * s, (y - h * 0.9f) * s, (x + h) * s, y * s)
            quadraticTo(x * s, (y + h * 0.9f) * s, (x - h) * s, y * s)
            close()
        }
        drawPath(path, color.copy(alpha = alpha))
        drawLine(
            PetLeafVein.copy(alpha = 0.55f * alpha),
            Offset((x - h - 1.5f) * s, y * s),
            Offset((x + h * 0.7f) * s, y * s),
            0.9f * s,
            StrokeCap.Round,
        )
    }
}

/** Autumn leaves drifting through the sky, clear of the sun, the moon and the pets; dimmer after dark. */
private fun DrawScope.drawAutumnLeaves(
    dark: Boolean,
    s: Float,
) {
    val alpha = if (dark) 0.6f else 1f
    leaf(PetLeafOrange, 24f, 28f, 12f, -30f, alpha, s)
    leaf(PetLeafGold, 62f, 56f, 10f, 25f, alpha, s)
    leaf(PetLeafRust, 124f, 16f, 11f, -15f, alpha, s)
    leaf(PetLeafGold, 170f, 36f, 9f, 40f, alpha, s)
    leaf(PetLeafOrange, 236f, 74f, 10f, -20f, alpha, s)
}

/**
 * What the pets wear or hold: the theme's costumes, then the mood's own props, with the umbrella
 * last so it's in front of everything under it. Misty's witch hat is a shorter one under the
 * umbrella, so its point doesn't poke through the canopy. Drawn in the pets' mirrored space.
 */
private fun DrawScope.drawAccessories(
    accessories: Set<PetAccessory>,
    mood: PetMood,
    s: Float,
    p: (Float, Float) -> Offset,
) {
    val dy = if (mood == PetMood.STORM) 14f else 0f
    if (PetAccessory.MISTY_WITCH_HAT in accessories) {
        val tip = if (PetAccessory.UMBRELLA in accessories) 40f else 22f
        poly(PetWitchHat, s, 80f, 62f + dy, 99f, tip + dy, 111f, 62f + dy)
        oval(PetWitchHat, 95f, 63f + dy, 27f, 4.5f, s)
        drawRect(PetHatBand, p(81f, 56f + dy), Size(29f * s, 5f * s))
    }
    if (PetAccessory.SUNNY_BANDANA in accessories) {
        poly(PetBandana, s, 142f, 97f, 178f, 97f, 160f, 111f)
        drawCircle(PetPumpkinFace, 1.6f * s, p(153f, 101f))
        drawCircle(PetPumpkinFace, 1.6f * s, p(167f, 101f))
    }
    if (PetAccessory.SUNNY_SUNGLASSES in accessories) {
        drawRoundRect(PetOutline, p(146f, 68f), Size(12f * s, 7f * s))
        drawRoundRect(PetOutline, p(162f, 68f), Size(12f * s, 7f * s))
        drawRect(PetOutline, p(157f, 70f), Size(6f * s, 2f * s))
    }
    if (PetAccessory.SUNNY_SCARF in accessories) drawRoundRect(PetScarf, p(136f, 94f), Size(48f * s, 9f * s))
    if (PetAccessory.MISTY_SCARF in accessories) drawRoundRect(PetScarfAlt, p(72f, 96f), Size(46f * s, 8f * s))
    if (PetAccessory.UMBRELLA in accessories) {
        val canopy = Path().apply {
            moveTo(70f * s, 56f * s)
            quadraticTo(128f * s, 0f, 190f * s, 56f * s)
            close()
        }
        drawPath(canopy, PetUmbrella)
        drawRect(PetOutline, p(128f, 54f), Size(3f * s, 42f * s))
    }
}
