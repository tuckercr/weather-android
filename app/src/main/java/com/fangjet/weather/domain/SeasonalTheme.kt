package com.fangjet.weather.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A look for a day or a stretch of days: the pets' props, the hero card's colours and caption, and
 * a warmer tint on the cards below. Themes are data, not code: the app ships them in
 * res/raw/seasonal_themes.json and Remote Config's `seasonal_themes` can replace the whole list, so a
 * new theme (Thanksgiving, say) needs no app update as long as it uses decorations the app can
 * draw. `seasonal_themes_enabled` switches them all off. Today's theme comes from the city's own
 * date, so it ends by itself when its day does. Same format and rules as iOS's SeasonalTheme.
 */
data class SeasonalTheme(
    // Stable, lowercase ("halloween").
    val id: String,
    val dates: DateRule,
    val colors: Colors,
    val captions: Captions,
    // What the pet scene adds. Names this version can't draw are dropped, so a newer theme still
    // shows its colours and captions here.
    val decorations: Set<SceneDecoration>,
) {
    /** 0xRRGGBB colours. */
    data class Colors(
        val heroDay: Int,
        val heroNight: Int,
        val inkDay: Int,
        val inkNight: Int,
        // The cards below the hero, in light and dark mode.
        val cardLight: Int,
        val cardDark: Int,
        // Section headers ("HOURLY", "TODAY").
        val accentLight: Int,
        val accentDark: Int,
        // Each tile's icon and label ("Feels like"); the accent when a theme leaves them out.
        val tileHeaderLight: Int,
        val tileHeaderDark: Int,
    )

    data class Captions(
        val day: List<String>,
        val night: List<String>,
    )

    /** One caption per day, picked by the city's date; null when the theme has none. */
    fun caption(
        night: Boolean,
        date: LocalDate,
    ): String? {
        val options = if (night) captions.night else captions.day
        return options.takeIf { it.isNotEmpty() }?.let { it[date.dayOfYear % it.size] }
    }
}

/**
 * When a theme applies, in the city's own calendar. JSON: `{"type": "fixed", "month": 10, "day": 31}`,
 * `{"type": "range", "from": [12, 24], "through": [1, 1]}` or `{"type": "nthWeekday", "month": 11,
 * "weekday": 5, "n": 4}` (Thanksgiving; weekday 1 is Sunday, as on iOS, and n -1 is the last).
 */
sealed interface DateRule {
    fun contains(date: LocalDate): Boolean

    data class Fixed(
        val month: Int,
        val day: Int,
    ) : DateRule {
        override fun contains(date: LocalDate) = date.monthValue == month && date.dayOfMonth == day
    }

    /** Both ends included; may cross New Year. */
    data class Range(
        val fromMonth: Int,
        val fromDay: Int,
        val throughMonth: Int,
        val throughDay: Int,
    ) : DateRule {
        override fun contains(date: LocalDate): Boolean {
            val today = date.monthValue * 100 + date.dayOfMonth
            val start = fromMonth * 100 + fromDay
            val end = throughMonth * 100 + throughDay
            return if (start <= end) today in start..end else today >= start || today <= end
        }
    }

    data class NthWeekday(
        val month: Int,
        val weekday: Int,
        val n: Int,
    ) : DateRule {
        override fun contains(date: LocalDate): Boolean {
            // java.time numbers Monday 1 to Sunday 7; the JSON, like iOS, Sunday 1 to Saturday 7.
            if (date.monthValue != month || date.dayOfWeek.value % 7 + 1 != weekday) return false
            return if (n > 0) (date.dayOfMonth - 1) / 7 + 1 == n else date.dayOfMonth + 7 > date.lengthOfMonth()
        }
    }
}

/**
 * The extras a theme can ask the pet scene for, by their JSON name. Adding one means drawing it in
 * PetScene (and iOS's); a theme naming one this version doesn't know just goes without it.
 */
enum class SceneDecoration(
    val jsonName: String,
) {
    // Bats by day; bats and stars after dark.
    BATS("bats"),

    // Misty's witch hat, by day.
    WITCH_HAT("witchHat"),

    // Sunny's bandana, by day.
    BANDANA("bandana"),

    // A jack-o'-lantern on Misty's side, lit after dark.
    JACK_O_LANTERN("jackOLantern"),
}

object SeasonalThemes {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The themes in [text], or null when it isn't a valid list, so a bad Remote Config value falls
     * back to the list shipped in the app rather than switching every theme off.
     */
    fun parse(text: String): List<SeasonalTheme>? =
        runCatching { json.decodeFromString<List<ThemeDto>>(text).map { it.toTheme() } }.getOrNull()

    /** Today's theme in the city's own calendar; null on an ordinary day. The earlier wins a tie. */
    fun themeOn(
        epochSeconds: Long,
        utcOffsetSeconds: Int,
        themes: List<SeasonalTheme>,
    ): SeasonalTheme? {
        val date = Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds)).toLocalDate()
        return themes.firstOrNull { it.dates.contains(date) }
    }

    @Serializable
    private data class ThemeDto(
        val id: String,
        val dates: DateRuleDto,
        val colors: ColorsDto,
        val captions: CaptionsDto = CaptionsDto(),
        val decorations: List<String> = emptyList(),
    ) {
        fun toTheme() =
            SeasonalTheme(
                id = id,
                dates = dates.toRule(),
                colors = colors.toColors(),
                captions = SeasonalTheme.Captions(captions.day, captions.night),
                decorations = decorations.mapNotNull { name -> SceneDecoration.entries.firstOrNull { it.jsonName == name } }.toSet(),
            )
    }

    @Serializable
    private data class DateRuleDto(
        val type: String,
        val month: Int? = null,
        val day: Int? = null,
        val from: List<Int>? = null,
        val through: List<Int>? = null,
        val weekday: Int? = null,
        val n: Int? = null,
    ) {
        fun toRule(): DateRule =
            when (type) {
                "fixed" -> DateRule.Fixed(requireNotNull(month), requireNotNull(day))
                "range" -> {
                    val (fromMonth, fromDay) = requireNotNull(from).also { require(it.size == 2) }
                    val (throughMonth, throughDay) = requireNotNull(through).also { require(it.size == 2) }
                    DateRule.Range(fromMonth, fromDay, throughMonth, throughDay)
                }
                "nthWeekday" -> DateRule.NthWeekday(requireNotNull(month), requireNotNull(weekday), requireNotNull(n))
                else -> throw IllegalArgumentException("Unknown date rule $type")
            }
    }

    @Serializable
    private data class ColorsDto(
        val heroDay: String,
        val heroNight: String,
        val inkDay: String,
        val inkNight: String,
        val cardLight: String,
        val cardDark: String,
        val accentLight: String,
        val accentDark: String,
        val tileHeaderLight: String? = null,
        val tileHeaderDark: String? = null,
    ) {
        fun toColors() =
            SeasonalTheme.Colors(
                heroDay = rgb(heroDay),
                heroNight = rgb(heroNight),
                inkDay = rgb(inkDay),
                inkNight = rgb(inkNight),
                cardLight = rgb(cardLight),
                cardDark = rgb(cardDark),
                accentLight = rgb(accentLight),
                accentDark = rgb(accentDark),
                tileHeaderLight = rgb(tileHeaderLight ?: accentLight),
                tileHeaderDark = rgb(tileHeaderDark ?: accentDark),
            )
    }

    @Serializable
    private data class CaptionsDto(
        val day: List<String> = emptyList(),
        val night: List<String> = emptyList(),
    )

    /** "#F5A94E" to 0xF5A94E. */
    private fun rgb(hex: String): Int {
        val digits = hex.removePrefix("#")
        require(digits.length == 6) { "Not #RRGGBB: $hex" }
        return digits.toInt(16)
    }
}
