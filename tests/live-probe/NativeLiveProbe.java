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
        if(getIntent().getBooleanExtra("visual",false)){
            long now=System.currentTimeMillis();
            Notification.Builder visual=new Notification.Builder(this,CourseReminder.CHANNEL).setContentTitle("课前预览")
                .setContentText(CourseNoticeStyle.time(now+180000)+" 开课 · 瑞师楼222")
                .setStyle(new Notification.BigTextStyle().setBigContentTitle("课前提醒 · 预览")
                    .bigText(CourseNoticeStyle.details(now+180000,"瑞师楼222")+"\n仅作效果预览，3分钟后结束。"));
            LiveCourseNotice.enable(this,true);manager().notify("visual",153,LiveCourseNotice.build(this,visual,"visual",153,now+180000,now));
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
        check(!ongoing(LiveCourseNotice.build(this,base(),"off",151,start,now)),"off retains normal notification");
        check(LiveCourseNotice.preview(this).contains("请先开启"),"preview requires opt in");
        LiveCourseNotice.enable(this,true);check(LiveCourseNotice.enabled(this),"opt in saved");
        Notification live=LiveCourseNotice.build(this,base(),"qa",151,start,now);
        if(Build.VERSION.SDK_INT>=36&&LiveCourseNotice.available(this)){
            check(ongoing(live),"ongoing requested");
            check(live.hasPromotableCharacteristics(),"eligible notification characteristics");
            check(live.when==start,"system countdown target");
            check(CourseNoticeStyle.time(start).equals(live.extras.getString("android.shortCriticalText")),"compact start time");
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
            manager().notify("qa",151,LiveCourseNotice.build(this,base(),"qa",151,System.currentTimeMillis()+20000,System.currentTimeMillis()));
            LiveCourseNotice.reconcile(this,Collections.emptyList(),System.currentTimeMillis());
            check(manager().getActiveNotifications().length==0,"deleted course cancels notice");
            check(LiveCourseNotice.preview(this).startsWith("已发送"),"preview delivered");
            check(!CourseReminder.enabled(this),"preview does not enable scheduled reminders");
            check(CourseReminder.prefs(this).getStringSet("sent",Collections.emptySet()).isEmpty(),"preview does not mark courses sent");
            boolean wasLive=LiveCourseNotice.available(this);
            LiveCourseNotice.enable(this,false);
            if(wasLive)check(manager().getActiveNotifications().length==0,"disable cancels live preview");
            manager().cancelAll();LiveCourseNotice.enable(this,true);
            long sent=System.currentTimeMillis();manager().notify("expiry",151,LiveCourseNotice.build(this,base(),"expiry",151,sent+20000,sent));
            report.setText("等待20秒自动结束…");
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                check(manager().getActiveNotifications().length==0,"system timeout removes at start");
                report.setText("PASS "+checks+" API "+Build.VERSION.SDK_INT);
                android.util.Log.i("NativeLiveProbe","PASS "+checks+" API "+Build.VERSION.SDK_INT);
            },23000);
        },1000);
    }
}
