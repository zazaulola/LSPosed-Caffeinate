package io.github.zazaulola.caffeinate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** SystemUI перезапустился и спрашивает состояние. */
class StateQueryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Bridge.ACTION_QUERY_STATE) Bridge.publish(context)
    }
}
