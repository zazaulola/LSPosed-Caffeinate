package io.github.zazaulola.caffeinate.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.zazaulola.caffeinate.Bridge
import io.github.zazaulola.caffeinate.ModuleStatus

/** Точка входа LSPosed (см. assets/xposed_init). */
class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            Bridge.APP_ID -> hookSelf(lpparam.classLoader)
            Bridge.SYSTEMUI -> if (lpparam.isFirstApplication) SystemUiHook.install(lpparam.classLoader)
        }
    }

    private fun hookSelf(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                ModuleStatus::class.java.name, classLoader, "isActive",
                XC_MethodReplacement.returnConstant(true),
            )
        } catch (t: Throwable) {
            XposedBridge.log("Caffeinate: self hook failed: $t")
        }
    }
}
