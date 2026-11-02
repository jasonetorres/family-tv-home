package app.familytv.launcher;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** Live wallpaper overlays drawn over the Live Weather picture: rain (and storm flashes), snow, stars, drifting
 *  cloud or fog banks, flickering window lights, and holiday string lights. Particle density follows the real
 *  precipitation intensity; slant, sideways drift and gusts follow the real wind. At most 30 fps, plain canvas
 *  primitives (no shaders per frame), and it stops completely whenever Home is not visible (setRunning(false)). */
final class WeatherFx extends View {
    static final int CLEAR=0,CLOUDY=1,RAIN=2,SNOW=3,FOG=4,STORM=5;
    int cond=-1,tod;boolean holiday;float intensity=.5f,windMph,windDir=270;
    float[] lightsX=new float[0],lightsY=new float[0];
    private boolean running;private long last,start=SystemClock.uptimeMillis();
    private final Random rnd=new Random(7);
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] px=new float[0],py=new float[0],pz=new float[0],ph=new float[0];
    private float[] sx=new float[60],sy=new float[60],sp=new float[60];
    private Bitmap blob,glow;
    private float flash,nextFlash=6,gust,gustT,cloudOff;
    private static final int[] BULBS={0xFFFF4D4D,0xFFFFD36B,0xFF4DD97A,0xFF5AA8FF,0xFFFF9F43};

    WeatherFx(Context c){super(c);setWillNotDraw(false);
        for(int i=0;i<sx.length;i++){sx[i]=rnd.nextFloat();sy[i]=rnd.nextFloat()*.42f;sp[i]=rnd.nextFloat()*6.28f;}
        blob=soft(160,80,0xFFFFFFFF);glow=soft(32,32,0xFFFFFFFF);}
    private static Bitmap soft(int w,int h,int color){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint q=new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new RadialGradient(w/2f,h/2f,Math.max(w,h)/2f,new int[]{color,color&0x80FFFFFF,0},new float[]{0,.45f,1},Shader.TileMode.CLAMP));c.save();c.scale(1,(float)h/w,w/2f,h/2f);c.drawCircle(w/2f,h/2f,w/2f,q);c.restore();return b;}

    /** Matrix condition (0–5), time of day (0 day, 1 dusk, 2 night), holiday lights. */
    void setScene(int cond,int tod,boolean holiday,float[] lx,float[] ly){
        boolean reseed=cond!=this.cond;this.cond=cond;this.tod=tod;this.holiday=holiday;lightsX=lx;lightsY=ly;
        if(reseed)seed();invalidate();}
    /** Intensity 0.3 light … 1 heavy; wind in mph and the direction it blows FROM (degrees). */
    void setWeather(float intensity,float mph,float dir){boolean re=Math.abs(intensity-this.intensity)>.05f;this.intensity=intensity;windMph=mph;windDir=dir;if(re)seed();}
    private int count(){if(cond==RAIN||cond==STORM)return (int)(40+200*intensity);if(cond==SNOW)return (int)(30+330*intensity);return 0;}
    private void seed(){int n=count();px=new float[n];py=new float[n];pz=new float[n];ph=new float[n];
        for(int i=0;i<n;i++){px[i]=rnd.nextFloat();py[i]=rnd.nextFloat();pz[i]=.45f+rnd.nextFloat()*.55f;ph[i]=rnd.nextFloat()*6.28f;}}
    void setRunning(boolean r){if(r==running)return;running=r;last=SystemClock.uptimeMillis();if(r)postInvalidateOnAnimation();}
    /** Wind toward screen right (+) or left (−), in mph, with gusts above 20 mph. */
    private float windX(){return (float)(-Math.sin(Math.toRadians(windDir)))*windMph*(1+gust);}

    @Override protected void onDraw(Canvas c){
        long now=SystemClock.uptimeMillis();float dt=Math.min(.1f,(now-last)/1000f);last=now;float t=(now-start)/1000f;
        int w=getWidth(),h=getHeight();float u=w/960f;
        if(windMph>20){gustT-=dt;if(gustT<=0)gustT=4+rnd.nextFloat()*5;gust=gustT<1.6f?.8f*(float)Math.sin((1.6f-gustT)/1.6f*Math.PI):0;}else gust=0;
        float wx=windX();
        // drifting cloud / fog banks
        if(cond==CLOUDY||cond==FOG||cond==STORM||cond==RAIN){
            cloudOff+=dt*u*(6+Math.abs(wx)*.9f)*(wx<0?-1:1);
            int banks=cond==FOG?5:3;int a=cond==FOG?(tod==2?46:70):(tod==2?26:40);
            for(int i=0;i<banks;i++){float bw=w*(.55f+.1f*i),bh=bw*.28f;float y=cond==FOG?h*(.45f+.09f*i):h*(.04f+.08f*i);
                float x=((cloudOff*(1+.35f*i)+i*w*.37f)%(w+bw)+(w+bw))%(w+bw)-bw;p.setAlpha(a);c.drawBitmap(blob,null,new RectF(x,y,x+bw,y+bh),p);}
            p.setAlpha(255);
        }
        // stars on clear (and snowy) nights
        if(tod==2&&(cond==CLEAR||cond==SNOW)){p.setColor(Color.WHITE);
            for(int i=0;i<sx.length;i++){float a=.35f+.65f*(.5f+.5f*(float)Math.sin(t*(.7f+i%5*.23f)+sp[i]));p.setAlpha((int)(a*200));c.drawCircle(sx[i]*w,sy[i]*h,(i%7==0?1.6f:1f)*u,p);}}
        // window lights
        if(tod>=1&&lightsX.length>0){for(int i=0;i<lightsX.length;i++){float f=.75f+.25f*(float)Math.sin(t*(1.3f+i%4*.4f)+i*1.7f)*((i%3==0)?1:.4f);
            p.setColorFilter(new PorterDuffColorFilter(0xFFFFC46B,PorterDuff.Mode.SRC_IN));p.setAlpha((int)(f*120));float r=7*u;
            c.drawBitmap(glow,null,new RectF(lightsX[i]*w-r,lightsY[i]*h-r,lightsX[i]*w+r,lightsY[i]*h+r),p);}p.setColorFilter(null);p.setAlpha(255);}
        // rain
        if(cond==RAIN||cond==STORM){float speed=(.9f+.6f*intensity)*h;float slant=wx*.025f;p.setStrokeWidth(Math.max(1,1.1f*u));p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<px.length;i++){py[i]+=dt*speed*pz[i]/h;px[i]+=dt*speed*pz[i]*slant/w;if(py[i]>1){py[i]-=1.05f;px[i]=rnd.nextFloat()*1.2f-.1f;}px[i]=(px[i]%1.2f+1.2f)%1.2f;
                float x=(px[i]-.1f)*w,y=py[i]*h,len=(10+14*intensity)*u*pz[i];p.setColor(0xFFDDE6F2);p.setAlpha((int)(60+70*pz[i]));c.drawLine(x,y,x-slant*len,y-len,p);}}
        // snow
        if(cond==SNOW){float speed=(.06f+.2f*intensity)*h;p.setColor(Color.WHITE);
            for(int i=0;i<px.length;i++){py[i]+=dt*speed*pz[i]/h;px[i]+=dt*(wx*.012f*u*pz[i]+ (float)Math.sin(t*.9f+ph[i])*6*u)/w;
                if(py[i]>1.02f){py[i]=-.02f;px[i]=rnd.nextFloat();}px[i]=(px[i]%1f+1f)%1f;
                p.setAlpha((int)(150+100*pz[i]));c.drawCircle(px[i]*w,py[i]*h,(1.2f+2.2f*pz[i])*u,p);}}
        // storm flash
        if(cond==STORM){nextFlash-=dt;if(nextFlash<=0){flash=1;nextFlash=7+rnd.nextFloat()*12;}
            if(flash>0){c.drawColor(Color.argb((int)(flash*70),235,240,255));flash=Math.max(0,flash-dt*2.6f);if(flash<.55f&&flash>.5f)flash=.85f;}}
        // holiday string lights along the top
        if(holiday&&cond!=SNOW){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f*u);p.setColor(0x99202A1E);Path wire=new Path();int sag=4;float seg=w/(float)sag;
            for(int s=0;s<sag;s++){wire.moveTo(s*seg,2*u);wire.quadTo(s*seg+seg/2,26*u,(s+1)*seg,2*u);}c.drawPath(wire,p);p.setStyle(Paint.Style.FILL);
            int n=0;for(int s=0;s<sag;s++)for(int k=1;k<8;k++){float q=k/8f;float x=s*seg+q*seg,y=2*u+4*q*(1-q)*12*u+3*u;int col=BULBS[n%BULBS.length];
                float tw=.55f+.45f*(float)Math.sin(t*(1.1f+(n%4)*.35f)+n*2.1f);p.setColorFilter(new PorterDuffColorFilter(col,PorterDuff.Mode.SRC_IN));p.setAlpha((int)(tw*150));float r=9*u;
                c.drawBitmap(glow,null,new RectF(x-r,y-r,x+r,y+r),p);p.setColorFilter(null);p.setColor(col);p.setAlpha((int)(180+75*tw));c.drawCircle(x,y,2.4f*u,p);n++;}
            p.setAlpha(255);}
        if(running&&cond>=0)postInvalidateDelayed(33); // ~30 fps
    }
}
