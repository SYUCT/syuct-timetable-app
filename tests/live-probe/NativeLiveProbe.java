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
        check(CourseNoticeStyle.title("  测试课程  ").equals("测试课程"),"trim title");
        check(CourseNoticeStyle.title("").equals("即将上课"),"empty title");
        check(CourseNoticeStyle.time(0).equals("08:00"),"Beijing time");
        check(CourseNoticeStyle.details(0," 张老师 "," 瑞师楼222 ").equals("开课时间：08:00；授课教师：张老师；上课教室：瑞师楼222"),"one paragraph with all fields");
        check(CourseNoticeStyle.details(0,null,"").equals("开课时间：08:00；授课教师：未提供；上课教室：未提供"),"missing fields explicit");
        String summary=branded.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        String expanded=branded.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        check(summary.contains("示例教师")&&summary.contains("瑞师楼222"),"ordinary summary has teacher and room");
        check(expanded.contains("示例教师")&&expanded.contains("瑞师楼222")&&!expanded.contains("\n"),"first paragraph has all details");
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
        Notification compact=CountdownDisplay.apply(Notification.Builder.recoverBuilder(this,full),start,now).build();
        check(compact.extras.getCharSequence(Notification.EXTRA_TITLE).toString().equals("剩余15分钟"),"title fallback is countdown");
        check(compact.extras.getString("android.shortCriticalText").equals("剩余15分钟"),"explicit chip text");
        check(compact.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString().equals("效果预览｜自然辩证法概论"),"full course preserved");
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
        if(ExactReminder.permitted(this)){
            CourseReminder.prefs(this).edit().putBoolean("preciseReminder",true).commit();
            long tickNow=System.currentTimeMillis(),tickTarget=tickNow+65000;
            Notification ticking=CountdownDisplay.apply(Notification.Builder.recoverBuilder(this,compact),tickTarget,tickNow).setWhen(tickTarget).build();
            LiveCourseNotice.post(this,LiveCourseNotice.PREVIEW,153,ticking);
            await("real non-wakeup alarm refresh",()->"剩余1分钟".equals(active(LiveCourseNotice.PREVIEW,153).extras.getString("android.shortCriticalText")));
            CourseReminder.prefs(this).edit().putBoolean("preciseReminder",false).commit();
        }
        long nearer=System.currentTimeMillis()+59000;
        Notification near=CountdownDisplay.apply(Notification.Builder.recoverBuilder(this,compact),nearer,nearer-900000).setWhen(nearer).build();
        manager().notify(LiveCourseNotice.PREVIEW,153,near);
        await("replacement target",()->active(LiveCourseNotice.PREVIEW,153).extras.getLong(CountdownDisplay.TARGET)==nearer);
        CountdownDisplay.refresh(this,LiveCourseNotice.PREVIEW,153);
        await("recomputed actual remainder",()->"剩余1分钟".equals(active(LiveCourseNotice.PREVIEW,153).extras.getString("android.shortCriticalText")));
        Notification updated=active(LiveCourseNotice.PREVIEW,153);
        check((updated.flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0,"refresh silent");
        check(updated.getTimeoutAfter()<=59000&&updated.getTimeoutAfter()>0,"remaining lifetime");
        check(updated.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().equals(expanded),"refresh preserves details");
        stop.send();await("end cancels",()->active(LiveCourseNotice.PREVIEW,153)==null);
        CountdownDisplay.refresh(this,LiveCourseNotice.PREVIEW,153);
        check(active(LiveCourseNotice.PREVIEW,153)==null,"dismissed not resurrected");
        Notification expired=CountdownDisplay.apply(base(),System.currentTimeMillis()-1000,System.currentTimeMillis()).setTimeoutAfter(60000).build();
        manager().notify(LiveCourseNotice.PREVIEW,153,expired);await("expired fixture",()->active(LiveCourseNotice.PREVIEW,153)!=null);
        CountdownDisplay.refresh(this,LiveCourseNotice.PREVIEW,153);await("expiry cancels",()->active(LiveCourseNotice.PREVIEW,153)==null);
        check(LiveCourseNotice.preview(this).startsWith("已发送"),"real preview submission");
        await("real preview accepted",()->active(LiveCourseNotice.PREVIEW,153)!=null);
        check(!CourseReminder.enabled(this),"preview does not enable formal reminders");
        check(CourseReminder.prefs(this).getStringSet("sent",Collections.emptySet()).isEmpty(),"preview does not mark sent");
        LiveCourseNotice.enable(this,false);await("disable cancels",()->active(LiveCourseNotice.PREVIEW,153)==null);
        CountdownDisplay.refresh(this,LiveCourseNotice.PREVIEW,153);check(active(LiveCourseNotice.PREVIEW,153)==null,"disabled refresh ignored");
        LiveCourseNotice.enable(this,true);
        long sent=System.currentTimeMillis();manager().notify("expiry",151,LiveCourseNotice.build(this,base().setTimeoutAfter(2000),"expiry",151,sent+2000,sent));
        await("expiry fixture posted",()->active("expiry",151)!=null);await("system timeout at start",()->active("expiry",151)==null);
        manager().notify("deleted",151,base().build());await("deletion fixture",()->active("deleted",151)!=null);
        LiveCourseNotice.reconcile(this,Collections.emptyList(),System.currentTimeMillis());await("course deletion cleans notice",()->active("deleted",151)==null);
    }
}
