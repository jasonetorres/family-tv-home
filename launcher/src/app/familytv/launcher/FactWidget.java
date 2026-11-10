package app.familytv.launcher;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fact of the day from a swappable pack (res/raw/facts_NAME.txt). Fully offline. */
final class FactWidget extends Widget {
    static final float PAD=20;
    static List<String> facts;
    static String loadedPack;
    final TextView body,meta,title;
    FactWidget(Context context,WidgetBoard.Host host){
        super(context,host,WidgetBoard.width("fact"));
        Glyph star=new Glyph(context).kind("star").color(ink(0xB3));addView(star,pos(14,14,PAD,15));
        title=text("Fact of the day",14,ink(0xCC),"sans-serif-medium");addView(title,pos(220,18,PAD+22,13));
        meta=text("",12,ink(0x99),"sans-serif");meta.setGravity(Gravity.END);meta.setSingleLine(true);addView(meta,pos(110,16,w-PAD-110,15));
        body=text("",16,ink(0xF2),"sans-serif");body.setSingleLine(false);body.setMaxLines(2);body.setEllipsize(android.text.TextUtils.TruncateAt.END);body.setLineSpacing(px(2),1);addView(body,pos(w-PAD*2,46,PAD,36));
        setFocusable(true);setFocusableInTouchMode(true);setSoundEffectsEnabled(false);
        setOnFocusChangeListener((v,f)->{if(f){host.hero("fact","Fact of the day",body.getText().toString());host.step(0);}});
        refresh();
    }
    @Override void refresh(){
        String pack=FamilyConfig.factPack();
        List<String> all=load(getContext(),pack);if(all.isEmpty()){body.setText("Add a fact pack in res/raw.");return;}
        Calendar c=Calendar.getInstance();long day=(c.getTimeInMillis()+c.get(Calendar.ZONE_OFFSET)+c.get(Calendar.DST_OFFSET))/86400000L;
        int i=(int)(day%all.size());body.setText(all.get(i));meta.setText(pack);
    }
    static synchronized List<String> load(Context context,String pack){
        if(facts!=null&&pack.equals(loadedPack))return facts;
        ArrayList<String> out=read(context,"facts_"+pack);
        if(out.isEmpty()&&!pack.equals("general"))out=read(context,"facts_general");
        facts=out;loadedPack=pack;return out;
    }
    private static ArrayList<String> read(Context context,String name){
        ArrayList<String> out=new ArrayList<>();
        int res=context.getResources().getIdentifier(name,"raw",context.getPackageName());
        if(res!=0)try(BufferedReader r=new BufferedReader(new InputStreamReader(context.getResources().openRawResource(res),StandardCharsets.UTF_8))){
            String line;while((line=r.readLine())!=null){line=line.trim();if(!line.isEmpty()&&!line.startsWith("#"))out.add(line);}
        }catch(IOException ignored){}
        return out;
    }
}
