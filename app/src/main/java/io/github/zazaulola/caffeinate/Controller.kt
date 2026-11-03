package io.github.zazaulola.caffeinate

import android.content.Context
import android.content.Intent

/** Общие действия для плитки, уведомления и экрана настроек. */
object Controller {

    /** Запустить с пресетом [index] из списка пользователя. */
    fun startPreset(context: Context, index: Int) {
        val presets = Prefs.presets(context)
        val minutes = presets.getOrNull(index) ?: return
        start(context, minutes * 60_000L, index)
    }

    fun start(context: Context, durationMs: Long, presetIndex: Int) {
        val intent = Intent(context, CaffeinateService::class.java)
            .setAction(CaffeinateService.ACTION_START)
            .putExtra(CaffeinateService.EXTRA_DURATION_MS, durationMs)
            .putExtra(CaffeinateService.EXTRA_PRESET_INDEX, presetIndex)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, CaffeinateService::class.java))
    }

    /** Нажатие на плитку: выкл → пресет 1 → пресет 2 → … → выкл. */
    fun cycle(context: Context) {
        val presets = Prefs.presets(context)
        val st = CaffeinateState
        val next = if (st.active) st.presetIndex + 1 else 0
        if (next >= presets.size) stop(context) else startPreset(context, next)
    }

    /** Режим изменён в настройках: применить к уже работающему таймеру. */
    fun applyMode(context: Context) {
        if (!CaffeinateState.active) return
        val intent = Intent(context, CaffeinateService::class.java)
            .setAction(CaffeinateService.ACTION_APPLY_MODE)
        context.startService(intent)
    }
}
