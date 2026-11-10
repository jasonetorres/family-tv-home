package app.familytv.launcher;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Countdown pill beside the greeting: a little tree whose star and baubles twinkle, then "N days until Christmas".
 *  On Christmas Day the badge uses the family name from family.json, with soft sparkles. About 15 fps, only while on screen. */
final class HolidayBadge extends View {
    final boolean light;
    final float u;final String text;final boolean merry,animate;
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),tp=new Paint(Paint.ANTI_ALIAS_FLAG);
    final long t0=SystemClock.uptimeMillis();
    HolidayBadge(Context c,float unit,int until,String text,boolean animate,boolean light){super(c);this.light=light;u=unit;this.text=text;merry=until==0;this.animate=animate;
        tp.setColor(light?0xF21D1D1F:0xF2FFFFFF);tp.setTextSize(13.5f*u);tp.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));setContentDescription(text);}
    @Override protected void onDraw(Canvas c){
        float t=(SystemClock.uptimeMillis()-t0)/1000f,w=getWidth(),h=getHeight();
        p.setColor(light?0xE6F7F8FA:0x9E10141C);c.drawRoundRect(0,0,w,h,h/2,h/2,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,.8f*u));p.setColor(light?0x80D99A1E:0x40FFD36B);c.drawRoundRect(.5f,.5f,w-.5f,h-.5f,h/2,h/2,p);p.setStyle(Paint.Style.FILL);
        float cx=h*.62f,top=h*.16f,bot=h*.84f;
        // tree: three tiers and a trunk
        p.setColor(0xFF2FA35A);for(int i=0;i<3;i++){float y0=top+(bot-top)*(.12f+.22f*i),y1=y0+(bot-top)*.38f,hw=(bot-top)*(.2f+.1f*i);
            Path tri=new Path();tri.moveTo(cx,y0);tri.lineTo(cx+hw,y1);tri.lineTo(cx-hw,y1);tri.close();c.drawPath(tri,p);}
        p.setColor(0xFF8A5A3C);c.drawRect(cx-1.4f*u,bot-2.2f*u,cx+1.4f*u,bot+.6f*u,p);
        int[] col={0xFFFF5A5A,0xFF5AA8FF,0xFFFFD36B,0xFFFF9F43,0xFFFFFFFF};float[][] at={{-.18f,.42f},{.16f,.55f},{-.08f,.7f},{.24f,.78f},{-.26f,.84f}};
        for(int i=0;i<at.length;i++){float a=.45f+.55f*(.5f+.5f*(float)Math.sin(t*2.2f+i*1.9f));p.setColor(col[i]);p.setAlpha((int)(a*255));c.drawCircle(cx+at[i][0]*(bot-top),top+at[i][1]*(bot-top),1.25f*u,p);}
        float s=.75f+.25f*(float)Math.sin(t*3f);p.setColor(0xFFFFD36B);p.setAlpha(255);star(c,cx,top+.4f*u,2.6f*u*s);
        float tx=h*1.15f;c.drawText(text,tx,h/2-(tp.descent()+tp.ascent())/2,tp);
        if(merry){for(int i=0;i<5;i++){float ph=(t*.6f+i*.2f)%1f;float x=tx+(i*.23f+.05f)*(w-tx-h*.4f),y=h*(i%2==0?.22f:.8f);p.setColor(Color.WHITE);p.setAlpha((int)(220*Math.sin(ph*Math.PI)));star(c,x,y,1.8f*u*(float)Math.sin(ph*Math.PI));}}
        if(animate&&isShown())postInvalidateDelayed(66);
    }
    private void star(Canvas c,float x,float y,float r){if(r<=0)return;Path s=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float rr=i%2==0?r:r*.42f;float px=x+(float)Math.cos(a)*rr,py=y+(float)Math.sin(a)*rr;if(i==0)s.moveTo(px,py);else s.lineTo(px,py);}s.close();c.drawPath(s,p);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();invalidate();}
}
