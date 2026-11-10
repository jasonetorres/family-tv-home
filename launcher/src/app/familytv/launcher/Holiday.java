package app.familytv.launcher;

import android.content.SharedPreferences;
import java.util.Calendar;

/** Christmas season (Settings › Holiday mode, Auto/Off). Nov 25–Dec 25: countdown badge (Dec 25: Merry Christmas).
 *  Dec 1–26: holiday snow art and string lights over the other weather. QA: am start … --es luma.date 2026-12-20. */
final class Holiday {
    static String fakeDate; // yyyy-MM-dd, QA only, not saved
    static Calendar today(){
        Calendar c=Calendar.getInstance();
        if(fakeDate!=null)try{String[] f=fakeDate.split("-");c.set(Integer.parseInt(f[0]),Integer.parseInt(f[1])-1,Integer.parseInt(f[2]));}catch(RuntimeException ignored){}
        return c;
    }
    static boolean on(SharedPreferences p){return p.getBoolean("holiday_auto",FamilyConfig.seasonalDefault());}
    private static int md(Calendar c){return (c.get(Calendar.MONTH)+1)*100+c.get(Calendar.DAY_OF_MONTH);}
    /** Holiday snow art and string lights. */
    static boolean lights(SharedPreferences p){int d=md(today());return on(p)&&d>=1201&&d<=1226;}
    /** Days until Dec 25 while the badge shows, 0 on Christmas Day, -1 otherwise. */
    static int countdown(SharedPreferences p){
        if(!on(p))return -1;Calendar c=today();int d=md(c);if(d<1125||d>1225)return -1;
        int day=c.get(Calendar.DAY_OF_MONTH);return d<1201?(30-day)+25:25-day;
    }
}
