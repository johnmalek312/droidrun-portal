package com.droidrun.portal.taskprompt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.droidrun.portal.R
import com.droidrun.portal.config.ConfigManager

class TaskPromptNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TaskPromptNotificationManager.ACTION_CANCEL_TASK) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val configManager = ConfigManager.getInstance(appContext)
        val activeTask = configManager.activePortalTask
        val taskId = intent.getStringExtra(TaskPromptNotificationManager.EXTRA_TASK_ID)
            ?.takeIf { it.isNotBlank() }
            ?: activeTask?.taskId

        if (activeTask == null || taskId.isNullOrBlank()) {
            TaskPromptNotificationManager.cancel(appContext)
            TaskPromptNotificationManager.broadcastTaskStateChanged(appContext)
            pendingResult.finish()
            return
        }

        val authToken = configManager.reverseConnectionToken.trim()
        val restBaseUrl = PortalCloudClient.deriveRestBaseUrl(configManager.reverseConnectionUrlOrDefault)
        if (authToken.isBlank() || restBaseUrl == null) {
            pendingResult.finish()
            return
        }

        val previousRecord = activeTask
        val cancellingRecord = activeTask.copy(lastStatus = PortalTaskTracking.STATUS_CANCELLING)
        configManager.saveActivePortalTask(cancellingRecord)
        TaskPromptNotificationManager.showActiveTask(appContext, cancellingRecord)
        TaskPromptNotificationManager.broadcastTaskStateChanged(appContext)

        val client = PortalCloudClient()
        client.cancelTask(restBaseUrl, authToken, taskId) { result ->
            when (result) {
                PortalTaskCancelResult.Success -> {
                    TaskPromptNotificationManager.broadcastTaskStateChanged(appContext)
                    pendingResult.finish()
                }

                PortalTaskCancelResult.AlreadyFinished -> {
                    client.getTaskStatus(restBaseUrl, authToken, taskId) { statusResult ->
                        when (statusResult) {
                            is PortalTaskStatusResult.Success -> {
                                val updatedRecord = cancellingRecord.copy(
                                    lastStatus = statusResult.value.status,
                                    terminalToastShown = false,
                                )
                                configManager.saveActivePortalTask(updatedRecord)
                                if (PortalTaskTracking.isTerminalStatus(updatedRecord.lastStatus)) {
                                    TaskPromptNotificationManager.showTerminalTask(
                                        appContext,
                                        updatedRecord,
                                        details = null,
                                        fallbackMessage = appContext.getString(R.string.task_prompt_status_unexpected_response),
                                    )
                                }
                            }

                            is PortalTaskStatusResult.Error -> {
                                configManager.saveActivePortalTask(previousRecord)
                                TaskPromptNotificationManager.showActiveTask(appContext, previousRecord)
                            }
                        }
                        TaskPromptNotificationManager.broadcastTaskStateChanged(appContext)
                        pendingResult.finish()
                    }
                }

                is PortalTaskCancelResult.Error -> {
                    configManager.saveActivePortalTask(previousRecord)
                    TaskPromptNotificationManager.showActiveTask(appContext, previousRecord)
                    TaskPromptNotificationManager.broadcastTaskStateChanged(appContext)
                    pendingResult.finish()
                }
            }
        }
    }
}
