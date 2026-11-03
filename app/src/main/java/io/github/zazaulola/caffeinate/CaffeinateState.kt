package io.github.zazaulola.caffeinate

import android.os.SystemClock

/**
 * Текущее состояние (живёт в процессе приложения, хозяин — [CaffeinateService]).
 * Все обращения — из главного потока.
 */
object CaffeinateState {
    var active: Boolean = false
        private set
    var mode: Mode = Mode.SCREEN_ON
        private set

    /** Момент окончания по [SystemClock.elapsedRealtime]; 0 — без ограничения. */
    var endElapsed: Long = 0L
        private set

    /** Индекс текущего пресета в списке пользователя; -1 — запущено не из пресета. */
    var presetIndex: Int = -1
        private set

    private val listeners = LinkedHashSet<() -> Unit>()

    val isInfinite: Boolean get() = active && endElapsed == 0L

    fun remainingMs(): Long =
        if (!active || endElapsed == 0L) 0L
        else (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun start(mode: Mode, durationMs: Long, presetIndex: Int) {
        this.active = true
        this.mode = mode
        this.endElapsed = if (durationMs <= 0L) 0L else SystemClock.elapsedRealtime() + durationMs
        this.presetIndex = presetIndex
        notifyListeners()
    }

    fun changeMode(mode: Mode) {
        this.mode = mode
        notifyListeners()
    }

    fun stop() {
        active = false
        endElapsed = 0L
        presetIndex = -1
        notifyListeners()
    }

    fun addListener(l: () -> Unit) {
        listeners += l
    }

    fun removeListener(l: () -> Unit) {
        listeners -= l
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }
}
