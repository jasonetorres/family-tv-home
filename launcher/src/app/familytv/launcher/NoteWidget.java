package app.familytv.launcher;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/** Latest household note from the same ntfy topic as the notes app. Read-only: this card polls and never posts.
 *  Commands that start with / are skipped. Offline, it shows a bundled note for the day. */
final class NoteWidget extends Widget {
    static final float PAD=20;
    static final String[] BUNDLED={
        "Proud of every one of you. Be kind to each other today.",
        "Drink some water, finish homework, then go have fun.",
        "Whatever happens today, you've got a team at home.",
        "Missing you. Save me a seat at dinner.",
        "Try something new today, even if it's small.",
        "It's okay to make mistakes. That's how we learn.",
        "Movie night is soon. You pick the film.",
        "Be brave, be curious, and be home for dinner.",
        "Thank you for being awesome today."};
    final TextView body,meta;
    final Runnable tick=new Runnable(){public void run(){if(!active)return;load();postDelayed(this,15*60000L);}};

    NoteWidget(Context context,WidgetBoard.Host host){
        super(context,host,WidgetBoard.width("note"));
        Glyph heart=new Glyph(context).kind("heart").color(ink(0xB3));addView(heart,pos(14,14,PAD,15));
        addView(text("Note from "+FamilyConfig.parentName(),14,ink(0xCC),"sans-serif-medium"),pos(200,18,PAD+22,13));
        meta=text("",12,ink(0x99),"sans-serif");meta.setGravity(Gravity.END);meta.setSingleLine(true);addView(meta,pos(w-PAD*2-190,16,PAD+190,15));
        body=text("",16,ink(0xF2),"sans-serif");body.setSingleLine(false);body.setMaxLines(2);body.setEllipsize(android.text.TextUtils.TruncateAt.END);body.setLineSpacing(px(2),1);addView(body,pos(w-PAD*2,46,PAD,36));
        setFocusable(true);setFocusableInTouchMode(true);setSoundEffectsEnabled(false);
        setOnFocusChangeListener((v,f)->{if(f){host.hero("note",body.getText().toString(),meta.getText().toString());host.step(0);}});
        setOnClickListener(v->{host.open();press(this,1.03f);openApp(FamilyConfig.notesPackage());});
        show(host.prefs().getString("dad_note",""),host.prefs().getLong("dad_note_at",0));
    }
    static String topic(android.content.SharedPreferences p){return FamilyConfig.topic(p);}
    @Override void setActive(boolean value){boolean was=active;super.setActive(value);removeCallbacks(tick);if(value&&!was)post(tick);}
    private void show(String text,long at){
        if(text==null||text.isEmpty()){
            Calendar c=Calendar.getInstance();body.setText(BUNDLED[(c.get(Calendar.YEAR)*400+c.get(Calendar.DAY_OF_YEAR))%BUNDLED.length]);
            meta.setText("");return;}
        body.setText(text);
        meta.setText(at>0?new SimpleDateFormat(FamilyConfig.clock12(host.prefs())?"EEE h:mm a":"EEE HH:mm",Locale.US).format(new Date(at*1000)):"From "+FamilyConfig.parentName());
    }
    private void load(){
        final String topic=topic(host.prefs());
        WidgetBoard.IO.execute(()->{
            String best=null;long at=0;
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(FamilyConfig.server()+"/"+topic+"/json?poll=1&since=168h").openConnection();
                c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","FamilyHome");
                try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while((line=r.readLine())!=null){
                        try{JSONObject o=new JSONObject(line);if(!"message".equals(o.optString("event")))continue;
                            String m=o.optString("message","").trim();if(m.isEmpty()||m.startsWith("/"))continue;
                            long t=o.optLong("time");if(t>=at){at=t;best=m;}}catch(JSONException ignored){}
                    }
                }
            }catch(Exception ignored){}
            final String note=best;final long time=at;
            if(note!=null)post(()->{host.prefs().edit().putString("dad_note",note).putLong("dad_note_at",time).apply();show(note,time);});
        });
    }
}
