package io.github.zazaulola.caffeinate

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.util.Log

/**
 * Foreground-сервис, который держит блокировки, пока работает таймер.
 *
 * - Всегда: PARTIAL_WAKE_LOCK, чтобы процессор не засыпал и таймер срабатывал вовремя.
 * - «Не выключать дисплей»: SCREEN_BRIGHT_WAKE_LOCK (устаревший, но рабочий способ
 *   держать экран без собственного окна).
 * - «Не блокировать»: состояние уходит хуку SystemUI ([Bridge]); если модуль LSPosed
 *   не активен, используется KeyguardLock.disableKeyguard() (работает только без
 *   защищённой блокировки).
 */
class CaffeinateService : Service() {

    companion object {
        private const val TAG = "Caffeinate"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_NEXT = "next"
        const val ACTION_APPLY_MODE = "apply_mode"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_PRESET_INDEX = "preset_index"

        private const val CHANNEL_ID = "active"
        private const val NOTIFICATION_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { stopSelf() }

    private var cpuLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                val index = intent.getIntExtra(EXTRA_PRESET_INDEX, -1)
                CaffeinateState.start(Prefs.mode(this), duration, index)
                apply()
            }

            ACTION_NEXT -> {
                // Сервис уже на переднем плане; просто переключаем пресет.
                val presets = Prefs.presets(this)
                val next = CaffeinateState.presetIndex + 1
                if (!CaffeinateState.active || next >= presets.size) {
                    stopSelf()
                } else {
                    CaffeinateState.start(Prefs.mode(this), presets[next] * 60_000L, next)
                    apply()
                }
            }

            ACTION_APPLY_MODE -> {
                if (CaffeinateState.active) {
                    CaffeinateState.changeMode(Prefs.mode(this))
                    apply()
                } else {
                    stopSelf()
                }
            }

            ACTION_STOP -> stopSelf()

            else -> {
                // Перезапуск системой без интента: состояние потеряно, выходим.
                if (!CaffeinateState.active) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Привести блокировки, уведомление, таймер и плитку в соответствие с состоянием. */
    private fun apply() {
        val st = CaffeinateState
        startInForeground(buildNotification())

        val pm = getSystemService(PowerManager::class.java)
        val remaining = st.remainingMs()
        // Запас на случай, если сервис убьют, не дав снять блокировки.
        val safety = if (st.isInfinite) 0L else remaining + 5_000L

        if (cpuLock == null) {
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Caffeinate:cpu")
                .apply { setReferenceCounted(false) }
        }
        cpuLock!!.acquireFor(safety)

        if (st.mode.keepScreenOn) {
            if (screenLock == null) {
                @Suppress("DEPRECATION")
                screenLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                    "Caffeinate:screen",
                ).apply { setReferenceCounted(false) }
            }
            screenLock!!.acquireFor(safety)
        } else {
            screenLock?.releaseSafely()
        }

        if (st.mode.preventLock && !ModuleStatus.isActive()) {
            disableKeyguardFallback()
        } else {
            reenableKeyguardFallback()
        }

        handler.removeCallbacks(expire)
        if (!st.isInfinite) handler.postDelayed(expire, remaining)

        Bridge.publish(this)
        refreshTile()
    }

    override fun onDestroy() {
        handler.removeCallbacks(expire)
        screenLock?.releaseSafely()
        cpuLock?.releaseSafely()
        reenableKeyguardFallback()
        CaffeinateState.stop()
        Bridge.publish(this)
        refreshTile()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }

        val st = CaffeinateState
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CaffeinateService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = PendingIntent.getService(
            this, 2, Intent(this, CaffeinateService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(st.mode.titleRes))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), stop).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.action_next), next).build())

        if (st.isInfinite) {
            builder.setContentText(getString(R.string.notif_infinite)).setShowWhen(false)
        } else {
            val endWall = System.currentTimeMillis() + st.remainingMs()
            val clock = DateFormat.getTimeFormat(this).format(endWall)
            builder.setContentText(getString(R.string.notif_until, clock))
                .setWhen(endWall)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun disableKeyguardFallback() {
        if (keyguardLock != null) return
        try {
            val km = getSystemService(KeyguardManager::class.java)
            keyguardLock = km.newKeyguardLock("Caffeinate").also { it.disableKeyguard() }
        } catch (e: Exception) {
            Log.w(TAG, "disableKeyguard failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun reenableKeyguardFallback() {
        val lock = keyguardLock ?: return
        keyguardLock = null
        try {
            lock.reenableKeyguard()
        } catch (e: Exception) {
            Log.w(TAG, "reenableKeyguard failed", e)
        }
    }

    private fun refreshTile() {
        try {
            TileService.requestListeningState(this, ComponentName(this, CaffeinateTileService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "requestListeningState failed", e)
        }
    }

    private fun PowerManager.WakeLock.acquireFor(timeoutMs: Long) {
        // Не reference-counted: повторный acquire просто обновляет таймаут.
        if (timeoutMs > 0) acquire(timeoutMs) else acquire()
    }

    private fun PowerManager.WakeLock.releaseSafely() {
        try {
            if (isHeld) release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "release failed", e)
        }
    }
}
