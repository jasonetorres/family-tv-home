package app.familytv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** adb shell am broadcast -a app.familytv.SET_TOPIC --es topic YOUR_TOPIC */
public class ConfigReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"app.familytv.SET_TOPIC".equals(intent.getAction())) return;
        String topic = intent.getStringExtra("topic");
        if (topic == null || topic.trim().isEmpty()) return;
        context.getSharedPreferences("luma", Context.MODE_PRIVATE).edit().putString("dad_topic", topic.trim()).apply();
    }
}
