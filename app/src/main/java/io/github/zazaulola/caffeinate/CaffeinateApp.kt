package io.github.zazaulola.caffeinate

import android.app.Application

class CaffeinateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Процесс только что стартовал, значит ничего не активно. Если раньше процесс
        // был убит, сообщаем SystemUI, чтобы он не продолжал подавлять блокировку.
        Bridge.publish(this)
    }
}
