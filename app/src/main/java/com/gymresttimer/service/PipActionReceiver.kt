package com.gymresttimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Forwards PIP RemoteAction broadcasts into [TimerService]. We use a broadcast
 * rather than a service PendingIntent because broadcast receivers are not
 * affected by background start restrictions during PIP transitions.
 */
class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        TimerService.send(context, action)
    }

    companion object {
        const val ACTION_FORWARD = "com.gymresttimer.action.PIP_FORWARD"
        const val EXTRA_ACTION = "extra_action"
    }
}
