package io.github.zazaulola.caffeinate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Плитка в шторке быстрых настроек.
 *
 * Нажатие переключает пресеты по кругу, рядом с иконкой показывается оставшееся
 * время, которое обновляется каждую секунду.
 *
 * Тикер работает всё время, пока SystemUI держит сервис привязанным (onCreate…onDestroy),
 * а не только между onStartListening/onStopListening: на некоторых прошивках SystemUI
 * зовёт onStopListening через несколько секунд при открытой шторке и отвязывает
 * сервис лишь спустя ~30 с, из-за чего время на плитке «замирало».
 */
class CaffeinateTileService : TileService() {

    companion object {
        private const val TAG = "CaffeinateTile"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var alive = false

    private val tick = object : Runnable {
        override fun run() {
            update()
            if (alive && CaffeinateState.active && !CaffeinateState.isInfinite) {
                // Следующее обновление — на границе секунды оставшегося времени.
                val delay = (CaffeinateState.remainingMs() % 1000).let { if (it < 50) 1000 else it }
                handler.postDelayed(this, delay)
            }
        }
    }

    private val stateListener: () -> Unit = { restartTicker() }

    override fun onCreate() {
        super.onCreate()
        alive = true
        Log.d(TAG, "onCreate")
        CaffeinateState.addListener(stateListener)
    }

    override fun onDestroy() {
        alive = false
        Log.d(TAG, "onDestroy")
        CaffeinateState.removeListener(stateListener)
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        restartTicker()
    }

    override fun onStopListening() {
        // Тикер не останавливаем: см. комментарий к классу.
        Log.d(TAG, "onStopListening")
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        try {
            Controller.cycle(applicationContext)
        } catch (e: IllegalStateException) {
            // Например, ForegroundServiceStartNotAllowedException: запускаем через активити.
            Log.w(TAG, "cannot start service from tile", e)
            openApp(cycle = true)
        }
        update()
    }

    private fun restartTicker() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun update() {
        val tile = try {
            qsTile
        } catch (e: RuntimeException) {
            null
        } ?: return
        val st = CaffeinateState
        val label: String
        val subtitle: String
        if (st.active) {
            tile.state = Tile.STATE_ACTIVE
            label = if (st.isInfinite) TimeFormat.INFINITY else TimeFormat.remaining(st.remainingMs())
            subtitle = getString(st.mode.shortRes)
        } else {
            tile.state = Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            subtitle = getString(R.string.tile_off)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = label
            tile.subtitle = subtitle
        } else {
            tile.label = if (st.active) "$label · $subtitle" else label
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = if (st.active) "$label, $subtitle" else subtitle
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(cycle: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_CYCLE, cycle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
