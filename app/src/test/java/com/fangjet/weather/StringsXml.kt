package com.fangjet.weather

import com.fangjet.weather.util.Strings
import java.io.File
import java.util.Locale

/**
 * [Strings] for JVM tests: the English text straight from res/values/strings.xml, found by the
 * resource's name and formatted as [android.content.res.Resources.getString] would, so tests
 * check the real wording without an Android runtime.
 */
object StringsXml : Strings {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    /** The string named [name], unescaped but not formatted. */
    fun raw(name: String): String {
        val text = Regex("<string name=\"$name\"[^>]*>([^<]*)</string>").find(xml)?.groupValues?.get(1)
            ?: error("No string \"$name\" in strings.xml")
        return text
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    override fun get(
        id: Int,
        vararg args: Any,
    ): String {
        val text = raw(names[id] ?: error("No string resource $id"))
        return if (args.isEmpty()) text else String.format(Locale.US, text, *args)
    }
}
