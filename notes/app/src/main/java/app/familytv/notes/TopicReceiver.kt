package app.familytv.notes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** adb shell am broadcast -a app.familytv.SET_TOPIC --es topic YOUR_TOPIC */
class TopicReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val topic = i.getStringExtra("topic")?.trim().orEmpty()
        if (topic.isEmpty()) return
        Store.setTopic(c, topic)
        c.startForegroundService(Intent(c, NotesService::class.java).putExtra("reconnect", true))
    }
}
