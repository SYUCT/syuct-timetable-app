package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.TextView;
import java.util.Collections;

/** Synthetic notifications in a separate QA package; never included in release. */
public final class NativeLiveProbe extends Activity {
    int checks;
    void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
    void await(String message,java.util.function.BooleanSupplier condition,Runnable next){
        long deadline=SystemClock.uptimeMillis()+5000;
        Handler handler=new Handler(Looper.getMainLooper());
        handler.post(new Runnable(){public void run(){
            if(condition.getAsBoolean()){check(true,message);next.run();return;}
            if(SystemClock.uptimeMillis()>=deadline)throw new AssertionError(message+" (system callback timeout)");
            handler.postDelayed(this,50);
        }});
    }
    NotificationManager manager(){return getSystemService(NotificationManager.class);}
    Notification.Builder base(){return new Notification.Builder(this,CourseReminder.CHANNEL).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("课前倒计时 · 验证").setContentText("测试课程 · 测试教室").setStyle(new Notification.BigTextStyle().bigText("测试课程 · 测试教室"))
        .setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(20000);}
    boolean ongoing(Notification n){return (n.flags&Notification.FLAG_ONGOING_EVENT)!=0;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!getPackageName().endsWith(".liveprobe"))throw new SecurityException("Isolated package only");
        TextView report=new TextView(this);report.setTextSize(20);report.setPadding(24,80,24,24);setContentView(report);
        manager().cancelAll();CourseReminder.prefs(this).edit().clear().commit();
        CourseReminder.channel(this);
        Notification branded=CourseNoticeStyle.apply(this,base()).build();
        check(branded.color==CourseNoticeStyle.BLUE,"brand blue");
        check(!branded.extras.getBoolean(Notification.EXTRA_COLORIZED),"not colorized for promotion");
        check(branded.getLargeIcon()!=null,"full color crest available");
        android.graphics.drawable.Drawable icon=branded.getSmallIcon().loadDrawable(this);
        android.graphics.Bitmap mask=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888);
        icon.setBounds(0,0,96,96);icon.draw(new android.graphics.Canvas(mask));
        int transparent=0,opaque=0;
        for(int y=0;y<96;y++)for(int x=0;x<96;x++){int a=android.graphics.Color.alpha(mask.getPixel(x,y));if(a==0)transparent++;if(a>200)opaque++;}
        check(transparent>100&&opaque>100,"small crest retains transparent and visible detail");
        check(CourseNoticeStyle.title("  测试课程  ").equals("测试课程"),"trim title");
        check(CourseNoticeStyle.title("").equals("即将上课"),"empty title");
        check(CourseNoticeStyle.title("新时代中国特色社会主义理论与实践").codePointCount(0,8)==8,"compact long title");
        check(CourseNoticeStyle.time(0).equals("08:00"),"Beijing time");
        check(CourseNoticeStyle.details(0,"").equals("开课时间：08:00\n课程地点：待定"),"separate details without invented room");
        check(CourseNoticeStyle.compactTitle("数值分析").equals("数值分"),"chip first three course characters");
        check(CourseNoticeStyle.compactTitle("  高 等\n数学").equals("高等数"),"chip strips whitespace");
        check(CourseNoticeStyle.compactTitle(null).equals("待上课"),"empty chip fallback");
        check(CourseNoticeStyle.compactTitle("化学").equals("化学"),"short course unchanged");
        check(CourseNoticeStyle.compactTitle("🧪实验课程").equals("🧪实验"),"chip does not split surrogate pair");
        check(CourseNoticeStyle.fallbackChip("金属腐蚀理论及应用",0).equals("金属腐 08:00"),"generic Xiaomi fallback includes course AND time");
        check(CourseNoticeStyle.fallbackChip(null,0).equals("待上课 08:00"),"fallback never time only");
        Notification.Builder miIcon=base();CourseNoticeStyle.applyXiaomiIcon(this,miIcon);
        Notification miNotice=miIcon.build();
        check(miNotice.getSmallIcon().getType()==android.graphics.drawable.Icon.TYPE_RESOURCE&&miNotice.getSmallIcon().getResId()==R.drawable.campus_badge,"Xiaomi small icon is original colour resource, not generated alpha mask");
        check(miNotice.extras.containsKey("miui.isGrayscaleIcon")&&!miNotice.extras.getBoolean("miui.isGrayscaleIcon"),"MIUI colour compatibility hint (rendering not proven)");
        check(XiaomiIsland.permission((Bundle)null)==XiaomiIsland.UNKNOWN,"null provider reply unknown, not denied");
        check(XiaomiIsland.permission(new Bundle())==XiaomiIsland.UNKNOWN,"missing permission unknown");
        Bundle focus=new Bundle();focus.putString("canShowFocus","true");
        check(XiaomiIsland.permission(focus)==XiaomiIsland.UNKNOWN,"wrong permission type unknown");
        focus.putBoolean("canShowFocus",false);check(XiaomiIsland.permission(focus)==XiaomiIsland.DENIED,"explicit denial");
        focus.putBoolean("canShowFocus",true);check(XiaomiIsland.permission(focus)==XiaomiIsland.GRANTED,"explicit permission");
        check(!XiaomiIsland.nativeAllowed(3,XiaomiIsland.UNKNOWN),"protocol alone cannot authorize native renderer");
        check(!XiaomiIsland.nativeAllowed(3,XiaomiIsland.DENIED),"denied uses fallback");
        check(!XiaomiIsland.nativeAllowed(2,XiaomiIsland.GRANTED),"old protocol uses fallback");
        check(XiaomiIsland.nativeAllowed(3,XiaomiIsland.GRANTED),"supported and granted native renderer");
        check(XiaomiIsland.status(3,XiaomiIsland.DENIED).contains("未允许"),"denied status does not claim native success");
        check(XiaomiIsland.status(3,XiaomiIsland.UNKNOWN).contains("暂未取得"),"unknown status honest");
        check(VivoIsland.device("iQOO","vivo")&&!VivoIsland.device("google","Google"),"OEM gate");
        Bundle vivo=VivoIsland.extras(this,"数值分析",0,null);
        check(vivo.getInt("notification.superx.operation",-1)==0,"vivo create");
        check(vivo.getBoolean("notification.superx.showNotify"),"vivo ordinary fallback");
        check(vivo.getString("notification.superx.scene").equals("METTING"),"calendar scene, not train spoof");
        Bundle island=vivo.getBundle("notification.superx.island");
        check(island.getBundle("island.superx.leftInfo").getString("island.superx.leftInfo.content").equals("数值分"),"vivo left name");
        check(island.getBundle("island.superx.rightInfo").getString("island.superx.rightInfo.content").equals("08:00"),"vivo right time");
        android.graphics.drawable.Icon colour=vivo.getBundle("notification.superx.baseInfos").getParcelable("notification.superx.baseInfos.icon");
        check(colour.getType()==android.graphics.drawable.Icon.TYPE_RESOURCE&&colour.getResId()==R.drawable.campus_badge,"OEM gets original full colour badge");
        android.graphics.Bitmap original=android.graphics.BitmapFactory.decodeResource(getResources(),R.drawable.campus_badge);
        android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(original,96,96,true);
        int light=0,lightAlpha=0,dark=0,darkAlpha=0;
        for(int y=0;y<96;y++)for(int x=0;x<96;x++){
            int p=scaled.getPixel(x,y);if(android.graphics.Color.alpha(p)<240)continue;
            int l=(android.graphics.Color.red(p)*54+android.graphics.Color.green(p)*183+android.graphics.Color.blue(p)*19)/256;
            if(l>220){light++;lightAlpha+=android.graphics.Color.alpha(mask.getPixel(x,y));}
            if(l<100){dark++;darkAlpha+=android.graphics.Color.alpha(mask.getPixel(x,y));}
        }
        check(light>10&&dark>10&&lightAlpha/light>darkAlpha/dark,"white areas stay white, not inverted");
        check(XiaomiIsland.device("Redmi","Xiaomi")&&XiaomiIsland.device("POCO","Xiaomi"),"Xiaomi family");
        check(!XiaomiIsland.device("vivo","vivo")&&!XiaomiIsland.device(null,null),"no vendor cross contamination");
        try{
            org.json.JSONObject root=XiaomiIsland.params("数值分析","数值分析","开课时间：08:00\n教室222",0,-180000);
            org.json.JSONObject v2=root.getJSONObject("param_v2"),mi=v2.getJSONObject("param_island");
            org.json.JSONObject big=mi.getJSONObject("bigIslandArea"),left=big.getJSONObject("imageTextInfoLeft");
            check(left.getJSONObject("textInfo").getString("title").equals("数值分"),"Xiaomi left is course");
            check(big.getJSONObject("textInfo").getString("title").equals("08:00"),"Xiaomi right retains time simultaneously");
            check(big.getJSONObject("textInfo").getBoolean("narrowFont"),"time uses narrow font");
            check(left.getJSONObject("picInfo").getString("pic").equals(XiaomiIsland.BADGE),"left colour image reference");
            check(mi.getJSONObject("smallIslandArea").getJSONObject("picInfo").getString("pic").equals(XiaomiIsland.BADGE),"small island image");
            check(v2.getJSONObject("picInfo").getString("picDark").equals(XiaomiIsland.BADGE),"expanded dark uses original badge too");
            check(mi.getInt("islandTimeout")==180&&v2.getInt("timeout")==3,"OEM timeouts units seconds and minutes");
            check(!v2.getBoolean("filterWhenNoPermission")&&v2.getString("reopen").equals("close"),"permission fallback and no resurrection");
            check(v2.getJSONObject("baseInfo").getString("title").equals("数值分析"),"expanded full name");
            check(XiaomiIsland.params("引号\"换行\n课","课","\"",0,1).getJSONObject("param_v2").getInt("timeout")==1,"escaped data and minimum timeout");
            PendingIntent stop=PendingIntent.getActivity(this,199,new Intent(this,NativeLiveProbe.class),PendingIntent.FLAG_IMMUTABLE);
            Notification.Action action=new Notification.Action.Builder(null,"结束提醒",stop).build();
            Bundle payload=XiaomiIsland.extras(this,"数值分析",base().build(),0,-180000,action);
            check(payload.getBundle("miui.focus.pics").getParcelable(XiaomiIsland.BADGE)!=null,"image bundle resolves picture");
            check(payload.getBundle("miui.focus.actions").getParcelable(XiaomiIsland.END)!=null,"end button resolves action");
            Parcel parcel=Parcel.obtain();parcel.writeBundle(payload);parcel.setDataPosition(0);
            Bundle decoded=parcel.readBundle(getClassLoader());parcel.recycle();
            check(decoded.getString(XiaomiIsland.PARAM).contains("数值分"),"payload survives notification IPC");
            if(Build.VERSION.SDK_INT>=36){
                Notification restored=LiveCourseNotice.liveState(base().addExtras(payload),"数值分 08:00",action,stop,180000,0,true).build();
                check(restored.extras.getBoolean("android.requestPromotedOngoing"),"regression: preserve generic promotion request");
                check("数值分 08:00".equals(restored.extras.getString("android.shortCriticalText")),"regression: preserve name AND time");
                check(!XiaomiIsland.hasPayload(restored),"unverified OEM template cannot hijack standard route");
                check(restored.deleteIntent!=null&&restored.actions.length==1,"restored route retains end action");
                check(restored.getTimeoutAfter()==180000,"restored route expires at start");
                check(ongoing(restored),"regression: ongoing NOT cleared on granted focus permission");
                check(!ongoing(LiveCourseNotice.liveState(base(),"数值分",action,stop,180000,0,false).build()),"generic permission denial still respected");
            }
        }catch(org.json.JSONException e){throw new AssertionError(e);}
        if(getIntent().getBooleanExtra("visual",false)){
            long now=System.currentTimeMillis();
            Notification.Builder visual=new Notification.Builder(this,CourseReminder.CHANNEL).setContentTitle("课前预览")
                .setContentText(CourseNoticeStyle.time(now+180000)+" 开课 · 瑞师楼222")
                .setStyle(new Notification.BigTextStyle().setBigContentTitle("课前提醒 · 预览")
                    .bigText(CourseNoticeStyle.details(now+180000,"瑞师楼222")+"\n仅作效果预览，3分钟后结束。"));
            LiveCourseNotice.enable(this,true);manager().notify("visual",153,LiveCourseNotice.build(this,visual,"数值分析","visual",153,now+180000,now));
            report.setText("PASS "+checks+" style visual");android.util.Log.i("NativeLiveProbe","PASS "+checks+" style visual");return;
        }
        boolean denied=getIntent().getBooleanExtra("denied",false);
        if(denied){
            check(!CourseReminder.notifications(this),"notifications denied");
            LiveCourseNotice.enable(this,true);
            check(LiveCourseNotice.preview(this).contains("请先允许"),"preview does not bypass permission");
            check(manager().getActiveNotifications().length==0,"no notification when denied");
            report.setText("PASS "+checks+" denied");android.util.Log.i("NativeLiveProbe","PASS "+checks+" denied");return;
        }
        check(CourseReminder.notifications(this),"notifications granted");
        check(!LiveCourseNotice.enabled(this),"default off");
        long now=System.currentTimeMillis(),start=now+20000;
        check(!ongoing(LiveCourseNotice.build(this,base(),"数值分析","off",151,start,now)),"off retains normal notification");
        check(LiveCourseNotice.preview(this).contains("请先开启"),"preview requires opt in");
        LiveCourseNotice.enable(this,true);check(LiveCourseNotice.enabled(this),"opt in saved");
        Notification live=LiveCourseNotice.build(this,base(),"数值分析","qa",151,start,now);
        if(!VivoIsland.device())check(!VivoIsland.hasPayload(live),"generic phone has no vendor extras");
        check(!XiaomiIsland.hasPayload(live),"no phone automatically selects unverified Xiaomi template");
        if(Build.VERSION.SDK_INT>=36&&LiveCourseNotice.available(this)){
            check(ongoing(live),"ongoing requested");
            check(live.hasPromotableCharacteristics(),"eligible notification characteristics");
            check(live.when==start,"system countdown target");
            check((XiaomiIsland.device()?CourseNoticeStyle.fallbackChip("数值分析",start):"数值分").equals(live.extras.getString("android.shortCriticalText")),"OEM fallback chip includes course and time");
            check(live.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"system chronometer");
            check(live.deleteIntent!=null,"dismissal callback");
            check(live.actions.length==1,"explicit end action");
            manager().notify("qa",151,live);
            check(manager().getActiveNotifications().length==1,"posted");
            android.util.Log.i("NativeLiveProbe","promoted="+((manager().getActiveNotifications()[0].getNotification().flags&Notification.FLAG_PROMOTED_ONGOING)!=0));
            try{live.actions[0].actionIntent.send();}catch(PendingIntent.CanceledException e){throw new RuntimeException(e);}
        }else{
            check(!ongoing(live),"unsupported/disallowed keeps ordinary notification");
            check(live.deleteIntent==null,"no live actions on fallback");
        }
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            check(manager().getActiveNotifications().length==0,"end action clears notification");
            manager().notify("qa",151,LiveCourseNotice.build(this,base(),"数值分析","qa",151,System.currentTimeMillis()+20000,System.currentTimeMillis()));
            // notify()/cancel() are asynchronous in Android 16. Observe the post
            // before reconciling; don't test the race between two IPC calls.
            await("posted notice observable",()->manager().getActiveNotifications().length==1,()->{
            LiveCourseNotice.reconcile(this,Collections.emptyList(),System.currentTimeMillis());
            await("deleted course cancels notice",()->manager().getActiveNotifications().length==0,()->{
            check(LiveCourseNotice.preview(this).startsWith("已发送"),"preview delivered");
            check(!CourseReminder.enabled(this),"preview does not enable scheduled reminders");
            check(CourseReminder.prefs(this).getStringSet("sent",Collections.emptySet()).isEmpty(),"preview does not mark courses sent");
            boolean wasLive=LiveCourseNotice.available(this);
            LiveCourseNotice.enable(this,false);
            if(wasLive)check(manager().getActiveNotifications().length==0,"disable cancels live preview");
            manager().cancelAll();LiveCourseNotice.enable(this,true);
            long sent=System.currentTimeMillis();manager().notify("expiry",151,LiveCourseNotice.build(this,base(),"数值分析","expiry",151,sent+20000,sent));
            report.setText("等待20秒自动结束…");
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                check(manager().getActiveNotifications().length==0,"system timeout removes at start");
                report.setText("PASS "+checks+" API "+Build.VERSION.SDK_INT);
                android.util.Log.i("NativeLiveProbe","PASS "+checks+" API "+Build.VERSION.SDK_INT);
            },23000);
            });
            });
        },1000);
    }
}
