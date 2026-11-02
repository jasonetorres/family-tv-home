package app.familytv.where

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The extra is the base topic. This app subscribes to that topic plus "-where". */
class TopicReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val topic = i.getStringExtra("topic")?.trim().orEmpty()
        if (topic.isEmpty()) return
        val full = if (topic.endsWith("-where")) topic else "$topic-where"
        Prefs.setTopic(c, full)
        c.startForegroundService(Intent(c, WhereService::class.java).putExtra("reconnect", true))
    }
}
