package com.fangjet.weather.util

import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * Text from strings.xml for code that writes sentences away from Compose, such as the
 * notifications. The app passes [Resources] ([asStrings]); JVM tests read strings.xml itself, so
 * the wording is checked without an Android runtime.
 */
interface Strings {
    /** The string, formatted with [args] when there are any, as [Resources.getString] does. */
    fun get(
        @StringRes id: Int,
        vararg args: Any,
    ): String
}

fun Resources.asStrings(): Strings =
    object : Strings {
        override fun get(
            id: Int,
            vararg args: Any,
        ): String = if (args.isEmpty()) getString(id) else getString(id, *args)
    }
