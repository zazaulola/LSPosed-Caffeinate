package io.github.zazaulola.caffeinate

import android.content.Context
import android.content.SharedPreferences

/** Пользовательские настройки: режим и пресеты (в минутах, 0 = бесконечно). */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_MODE = "mode"
    private const val KEY_PRESETS = "presets"

    val DEFAULT_PRESETS = listOf(5, 15, 30, 60, 0)

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(context: Context): Mode = Mode.fromName(sp(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: Mode) {
        sp(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun presets(context: Context): List<Int> {
        val raw = sp(context).getString(KEY_PRESETS, null) ?: return DEFAULT_PRESETS
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }
    }

    fun setPresets(context: Context, presets: List<Int>) {
        sp(context).edit().putString(KEY_PRESETS, normalize(presets).joinToString(",")).apply()
    }

    fun resetPresets(context: Context) {
        sp(context).edit().remove(KEY_PRESETS).apply()
    }

    /** Без дублей, по возрастанию, «бесконечно» в конце. */
    fun normalize(presets: List<Int>): List<Int> =
        presets.distinct().sortedWith(compareBy({ it == 0 }, { it }))
}
