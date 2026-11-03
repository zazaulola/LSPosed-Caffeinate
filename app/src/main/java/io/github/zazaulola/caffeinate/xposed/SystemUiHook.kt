package io.github.zazaulola.caffeinate.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.zazaulola.caffeinate.Bridge

/**
 * Хук в процессе SystemUI для режима «не блокировать».
 *
 * KeyguardViewMediator.doKeyguardLocked() — единая точка, через которую SystemUI
 * показывает экран блокировки (после выключения экрана, по таймауту блокировки,
 * при «заблокировать сейчас»). Пока приложение просит подавлять блокировку,
 * вызов пропускается. Когда режим заканчивается при погашенном экране,
 * блокировка включается принудительно через doKeyguardTimeout().
 */
object SystemUiHook {
    private const val MEDIATOR = "com.android.systemui.keyguard.KeyguardViewMediator"

    @Volatile private var preventLock = false
    @Volatile private var untilElapsed = 0L
    @Volatile private var mediator: Any? = null
    @Volatile private var registered = false

    fun install(classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists(MEDIATOR, classLoader)
        if (cls == null) {
            log("$MEDIATOR not found, keyguard hook disabled")
            return
        }

        XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                attach(param.thisObject)
            }
        })

        val hooked = XposedBridge.hookAllMethods(cls, "doKeyguardLocked", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                attach(param.thisObject)
                if (isPreventing()) {
                    param.result = null
                }
            }
        })
        log("installed, doKeyguardLocked hooks: ${hooked.size}")
    }

    private fun isPreventing(): Boolean {
        if (!preventLock) return false
        val until = untilElapsed
        if (until != 0L && SystemClock.elapsedRealtime() >= until) {
            preventLock = false
            return false
        }
        return true
    }

    private fun attach(instance: Any) {
        if (mediator == null) mediator = instance
        if (registered) return
        val context = try {
            XposedHelpers.getObjectField(instance, "mContext") as? Context
        } catch (t: Throwable) {
            null
        } ?: return
        synchronized(this) {
            if (registered) return
            registered = true
        }
        register(context)
    }

    private fun register(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != Bridge.ACTION_STATE) return
                val wasPreventing = isPreventing()
                preventLock = intent.getBooleanExtra(Bridge.EXTRA_PREVENT_LOCK, false)
                untilElapsed = intent.getLongExtra(Bridge.EXTRA_UNTIL_ELAPSED, 0L)
                if (wasPreventing && !preventLock) relockIfScreenOff(ctx)
            }
        }
        val filter = IntentFilter(Bridge.ACTION_STATE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Bridge.PERMISSION, null, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter, Bridge.PERMISSION, null)
            }
            // Узнать текущее состояние: SystemUI мог перезапуститься, пока таймер работает.
            context.sendBroadcast(Intent(Bridge.ACTION_QUERY_STATE).setPackage(Bridge.APP_ID))
            log("receiver registered")
        } catch (t: Throwable) {
            registered = false
            log("register failed: $t")
        }
    }

    /** Режим закончился, а экран уже погашен: заблокировать, как сделала бы система. */
    private fun relockIfScreenOff(context: Context) {
        try {
            val pm = context.getSystemService(PowerManager::class.java)
            if (pm.isInteractive) return
            val m = mediator ?: return
            XposedHelpers.callMethod(m, "doKeyguardTimeout", null as Bundle?)
        } catch (t: Throwable) {
            log("relock failed: $t")
        }
    }

    private fun log(msg: String) = XposedBridge.log("Caffeinate: $msg")
}
