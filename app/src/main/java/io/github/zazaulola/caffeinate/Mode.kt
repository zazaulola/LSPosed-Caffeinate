package io.github.zazaulola.caffeinate

/** Что именно удерживать, пока работает таймер. */
enum class Mode(
    val keepScreenOn: Boolean,
    val preventLock: Boolean,
    val titleRes: Int,
    val shortRes: Int,
    val hintRes: Int,
) {
    /** Не выключать дисплей. */
    SCREEN_ON(true, false, R.string.mode_screen_on, R.string.mode_screen_on_short, R.string.mode_screen_on_hint),

    /** Не блокировать дисплей (экран может погаснуть). */
    NO_LOCK(false, true, R.string.mode_no_lock, R.string.mode_no_lock_short, R.string.mode_no_lock_hint),

    /** Не выключать и не блокировать. */
    BOTH(true, true, R.string.mode_both, R.string.mode_both_short, R.string.mode_both_hint);

    companion object {
        fun fromName(name: String?): Mode = entries.firstOrNull { it.name == name } ?: SCREEN_ON
    }
}
