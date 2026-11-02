package io.github.zazaulola.caffeinate

import android.content.Context
import android.content.Intent

/**
 * Протокол обмена между приложением и хуком в процессе SystemUI.
 *
 * Приложение → SystemUI: [ACTION_STATE] с флагом «подавлять блокировку» и сроком.
 * SystemUI-приёмник зарегистрирован с [PERMISSION] (signature), поэтому
 * прислать состояние может только это приложение.
 *
 * SystemUI → приложение: [ACTION_QUERY_STATE] при старте SystemUI, чтобы получить
 * актуальное состояние после перезапуска системного интерфейса.
 */
object Bridge {
    const val APP_ID = "io.github.zazaulola.caffeinate"
    const val SYSTEMUI = "com.android.systemui"

    const val PERMISSION = "$APP_ID.permission.CONTROL"
    const val ACTION_STATE = "$APP_ID.action.KEYGUARD_STATE"
    const val ACTION_QUERY_STATE = "$APP_ID.action.QUERY_STATE"

    /** Boolean: подавлять ли блокировку. */
    const val EXTRA_PREVENT_LOCK = "prevent_lock"

    /** Long: срок по elapsedRealtime, 0 — без ограничения. */
    const val EXTRA_UNTIL_ELAPSED = "until_elapsed"

    /** Отправить текущее состояние хуку SystemUI. */
    fun publish(context: Context) {
        val st = CaffeinateState
        val prevent = st.active && st.mode.preventLock
        val intent = Intent(ACTION_STATE)
            .setPackage(SYSTEMUI)
            .putExtra(EXTRA_PREVENT_LOCK, prevent)
            .putExtra(EXTRA_UNTIL_ELAPSED, if (prevent) st.endElapsed else 0L)
        context.sendBroadcast(intent)
    }
}
