package io.github.zazaulola.caffeinate

import android.util.Log

/** LSPosed подменяет [isActive] на `true`, когда модуль включён (см. HookEntry). */
object ModuleStatus {
    @JvmStatic
    fun isActive(): Boolean {
        // Тело не должно быть константой, чтобы вызов не встроился компилятором.
        Log.v("Caffeinate", "module not active")
        return false
    }
}
