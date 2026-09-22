package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.widget.TextView;
import java.util.Collections;

/** Real notification IPC in a separate QA package, never shipped in release. */
public final class NativeLiveProbe extends Activity {
    int checks;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    NotificationManager manager(){return getSystemService(NotificationManager.class);}
    Notification.Builder base(){return CourseNoticeStyle.apply(this,new NoticePreview("自然辩证法概论","瑞师楼222","示例教师",false).builder(this,System.currentTimeMillis()+900000,true));}
    Notification active(String key,int id){
        for(StatusBarNotification n:manager().getActiveNotifications())if(n.getId()==id&&java.util.Objects.equals(key,n.getTag()))return n.getNotification();
        return null;
    }
    void await(String label,java.util.function.BooleanSupplier condition) throws InterruptedException {
        for(int i=0;i<300;i++){if(condition.getAsBoolean()){check(true,label);return;}Thread.sleep(50);}
        throw new AssertionError(label);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!getPackageName().endsWith(".liveprobe"))throw new SecurityException("Isolated package only");
        TextView report=new TextView(this);report.setTextSize(20);report.setPadding(24,80,24,24);setContentView(report);
        new Thread(()->{
            try{runChecks();String text="PASS "+checks+" API "+Build.VERSION.SDK_INT;android.util.Log.i("NativeLiveProbe",text);runOnUiThread(()->report.setText(text));}
            catch(Exception|AssertionError e){android.util.Log.e("NativeLiveProbe","FAIL",e);runOnUiThread(()->report.setText("FAIL "+e));}
        },"native-notice-qa").start();
    }
    void runChecks() throws Exception {
        manager().cancelAll();CourseReminder.prefs(this).edit().clear().commit();CourseReminder.channel(this);
        Notification branded=CourseNoticeStyle.apply(this,base()).build();
        check(branded.color==CourseNoticeStyle.BLUE,"brand blue");
        check(!branded.extras.getBoolean(Notification.EXTRA_COLORIZED),"non-colorized standard notification");
        check(branded.getLargeIcon()!=null&&branded.getSmallIcon()!=null,"both icons provided");
        check(CourseNoticeStyle.chipName("  机械结构有限元分析  ").equals("机械结构"),"four course characters");
        check(CourseNoticeStyle.chipName("大学 外语\nIII").equals("大学外语"),"strip whitespace");
        check(CourseNoticeStyle.chipName("电工学").equals("电工学"),"short names unchanged");
        check(CourseNoticeStyle.chipName("🧪实验课程").equals("🧪实验课"),"no split surrogate pairs");
        check(CourseNoticeStyle.chipName(null).equals("课程提醒"),"empty fallback");
        check(CourseNoticeStyle.time(0).equals("08:00"),"Beijing time");
        check(CourseNoticeStyle.details(0," 张老师 "," 瑞师楼222 ").equals("08:00 开课 · 点“查看详情”\n授课教师：张老师\n上课教室：瑞师楼222"),"separate fields for standard expansion");
        check(CourseNoticeStyle.details(0,null,"").contains("授课教师：未提供\n上课教室：未提供"),"missing fields explicit");
        String summary=branded.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        String expanded=branded.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        check(summary.contains("查看详情")&&summary.length()<24,"short OEM summary points to full details");
        check(expanded.contains("示例教师")&&expanded.contains("瑞师楼222")&&expanded.split("\n").length==3,"standard expansion has three rows");
        check(branded.actions.length==1&&branded.actions[0].title.equals("查看详情"),"ordinary notice keeps details action");
        check(NoticeCompat.xiaomi("Redmi","Xiaomi")&&NoticeCompat.xiaomi("POCO","Xiaomi"),"Xiaomi family");
        check(!NoticeCompat.xiaomi(null,null)&&!NoticeCompat.xiaomi("vivo","vivo"),"scoped to Xiaomi");
        Notification.Builder mi=base();CourseNoticeStyle.applyXiaomiIcon(this,mi);
        check(mi.build().getSmallIcon().getResId()==R.drawable.campus_badge,"original Xiaomi badge");
        check(!mi.build().extras.getBoolean("miui.isGrayscaleIcon",true),"colour hint retained");
        PendingIntent stop=PendingIntent.getBroadcast(this,153,new Intent(this,LiveCourseNotice.class)
            .setAction(LiveCourseNotice.END).putExtra("key",LiveCourseNotice.PREVIEW).putExtra("id",153),PendingIntent.FLAG_IMMUTABLE);
        Notification.Action action=new Notification.Action.Builder(null,"结束提醒",stop).build();
        long now=System.currentTimeMillis(),start=now+900000;
        Bundle stale=new Bundle();stale.putString("miui.focus.param","unused");
        Notification full=LiveCourseNotice.liveState(base().addExtras(stale),action,stop,start,now,true).build();
        check(!NoticeCompat.legacy(full),"unused payload stripped");
        check(full.when==start&&full.getTimeoutAfter()==900000,"fifteen-minute target");
        check(full.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)&&full.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),"ticking countdown preserved");
        check(full.extras.getString("android.shortCriticalText")==null,"non-Xiaomi native timer retained");
        Notification late=LiveCourseNotice.liveState(base(),action,stop,start,now+300000,true).build();
        check(late.when==start&&late.getTimeoutAfter()==600000,"late delivery never restarts fifteen minutes");
        Notification compact=CourseNoticeStyle.chip(Notification.Builder.recoverBuilder(this,full),"自然辩证法概论").build();
        check(compact.extras.getCharSequence(Notification.EXTRA_TITLE).toString().equals("自然辩证"),"title fallback four characters");
        check(compact.extras.getString("android.shortCriticalText").equals("自然辩证"),"explicit four-character chip");
        check(compact.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString().equals("自然辩证法概论"),"expanded title not shortened or prefixed");
        check(compact.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().equals(expanded),"full details preserved");
        check(compact.when==start&&compact.getTimeoutAfter()==900000,"countdown target preserved");
        check(compact.hasPromotableCharacteristics()==full.hasPromotableCharacteristics(),"promotion eligibility unchanged");
        if(getIntent().getBooleanExtra("denied",false)){
            check(!CourseReminder.notifications(this),"notifications denied");LiveCourseNotice.enable(this,true);
            check(LiveCourseNotice.preview(this).contains("请先允许"),"permission respected");
            check(manager().getActiveNotifications().length==0,"nothing posted");return;
        }
        check(CourseReminder.notifications(this),"notifications granted");
        check(!LiveCourseNotice.enabled(this),"default disabled");
        check((LiveCourseNotice.build(this,base(),"off",151,start,now).flags&Notification.FLAG_ONGOING_EVENT)==0,"off retains ordinary notice");
        check(LiveCourseNotice.preview(this).contains("请先开启"),"opt in required");
        LiveCourseNotice.enable(this,true);
        // Force the production compatibility builder on AOSP to test IPC and
        // lifecycle, NOT to claim an OEM island was displayed.
        LiveCourseNotice.post(this,LiveCourseNotice.PREVIEW,153,compact);
        await("posted",()->active(LiveCourseNotice.PREVIEW,153)!=null);
        if(getIntent().getBooleanExtra("visual",false))return;
        Intent obsolete=new Intent(this,LiveCourseNotice.class).setAction(NoticeCompat.OLD_REFRESH).putExtra("key",LiveCourseNotice.PREVIEW).putExtra("id",153);
        new LiveCourseNotice().onReceive(this,obsolete);
        check(active(LiveCourseNotice.PREVIEW,153).extras.getString("android.shortCriticalText").equals("自然辩证"),"old minute callback cannot overwrite course");
        stop.send();await("end cancels",()->active(LiveCourseNotice.PREVIEW,153)==null);
        new LiveCourseNotice().onReceive(this,obsolete);
        check(active(LiveCourseNotice.PREVIEW,153)==null,"dismissed not resurrected");
        check(LiveCourseNotice.preview(this).startsWith("已发送"),"real preview submission");
        await("real preview accepted",()->active(LiveCourseNotice.PREVIEW,153)!=null);
        check(!CourseReminder.enabled(this),"preview does not enable formal reminders");
        check(CourseReminder.prefs(this).getStringSet("sent",Collections.emptySet()).isEmpty(),"preview does not mark sent");
        LiveCourseNotice.enable(this,false);await("disable cancels",()->active(LiveCourseNotice.PREVIEW,153)==null);
        new LiveCourseNotice().onReceive(this,obsolete);check(active(LiveCourseNotice.PREVIEW,153)==null,"disabled refresh ignored");
        LiveCourseNotice.enable(this,true);
        long sent=System.currentTimeMillis();manager().notify("expiry",151,LiveCourseNotice.build(this,base().setTimeoutAfter(2000),"expiry",151,sent+2000,sent));
        await("expiry fixture posted",()->active("expiry",151)!=null);await("system timeout at start",()->active("expiry",151)==null);
        manager().notify("deleted",151,base().build());await("deletion fixture",()->active("deleted",151)!=null);
        LiveCourseNotice.reconcile(this,Collections.emptyList(),System.currentTimeMillis());await("course deletion cleans notice",()->active("deleted",151)==null);
    }
}
