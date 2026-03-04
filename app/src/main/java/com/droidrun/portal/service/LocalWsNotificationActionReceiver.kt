package com.droidrun.portal.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.droidrun.portal.config.ConfigManager

class LocalWsNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DroidrunAccessibilityService.ACTION_DISABLE_LOCAL_WS_SERVER) {
            return
        }

        val appContext = context.applicationContext
        val configManager = ConfigManager.getInstance(appContext)
        configManager.setWebSocketEnabledWithNotification(false)
        DroidrunAccessibilityService.getInstance()?.hideLocalWebSocketConnectionNotification()
        Log.i(
            DroidrunAccessibilityService.TAG,
            "Disabled local WebSocket server from notification action",
        )
    }
}
