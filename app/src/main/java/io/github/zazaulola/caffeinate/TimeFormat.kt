package io.github.zazaulola.caffeinate

import java.util.Locale

object TimeFormat {
    const val INFINITY = "∞"

    /** Оставшееся время: «4:59» или «1:04:59». */
    fun remaining(ms: Long): String {
        val total = ((ms + 999) / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Подпись пресета: «15 мин», «1 ч», «1 ч 30 мин», «∞». */
    fun preset(minutes: Int): String {
        if (minutes == 0) return INFINITY
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m мин"
            m == 0 -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    /** Разбор ввода пользователя: «15», «1:30», «0», «∞». null — если не получилось. */
    fun parsePreset(input: String): Int? {
        val s = input.trim()
        if (s == INFINITY) return 0
        if (s.contains(':')) {
            val parts = s.split(':')
            if (parts.size != 2) return null
            val h = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().toIntOrNull() ?: return null
            if (h < 0 || m !in 0..59) return null
            return h * 60 + m
        }
        val m = s.toIntOrNull() ?: return null
        return if (m in 0..(24 * 60 * 7)) m else null
    }
}
