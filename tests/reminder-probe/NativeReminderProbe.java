package top.syuct.timetable;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Isolated QA package only; never ship in the main/debug source sets. */
public final class NativeReminderProbe extends Activity {
    int checks;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    NotificationManager manager(){return getSystemService(NotificationManager.class);}
    // NotificationManager IPC enqueues work; wait for the system, not a same-tick snapshot.
    void awaitCount(int expected){
        long until=SystemClock.elapsedRealtime()+3000;
        while(manager().getActiveNotifications().length!=expected&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(20);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!getPackageName().endsWith(".reminderprobe"))throw new SecurityException("Isolated QA package required");
        try{
            boolean blocked=getIntent().getBooleanExtra("blocked",false);
            CourseReminder.enable(this,false);manager().cancelAll();awaitCount(0);CourseReminder.prefs(this).edit().clear().commit();
            LocalDate first=LocalDate.now(LessonClock.ZONE).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
            long start=first.atTime(13,30).atZone(LessonClock.ZONE).toInstant().toEpochMilli(),due=start-900000;
            JSONObject settings=new JSONObject().put("firstWeekDate",first.toString()).put("totalWeeks",20);
            JSONObject course=new JSONObject().put("name","提醒测试课程").put("teacher","测试教师").put("room","测试教室").put("weekType","all").put("weekday",1).put("startSection",5).put("startWeek",1).put("endWeek",20);
            JSONObject sample=new JSONObject().put("settings",settings).put("courses",new JSONArray().put(course));
            getSharedPreferences("timetable",MODE_PRIVATE).edit().putString("state",sample.toString()).commit();
            check(CourseReminder.events(this).get(0).course.teacher.equals("测试教师"),"stored teacher reaches planner");
            NoticePreview preview=NoticePreview.sample(this);
            check(preview.teacher.equals("测试教师")&&preview.borrowed,"preview borrows real teacher");
            Notification sampleNotice=preview.builder(this,start,true).build();
            check(sampleNotice.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("授课教师：测试教师"),"preview expanded teacher");
            check(sampleNotice.getTimeoutAfter()==900000,"preview lasts fifteen minutes");
            CourseReminder.prefs(this).edit().putBoolean("enabled",true).commit();
            if(blocked){
                check(!CourseReminder.notifications(this),"notification permission denied");
                CourseReminder.schedule(this,due+300000,false);
                awaitCount(1);
                check(manager().getActiveNotifications().length==0,"blocked cannot send");
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==0,"blocked cannot schedule");
                check(CourseReminder.testNotification(this).contains("未允许"),"blocked test gives guidance");
            }else{
                check(CourseReminder.notifications(this),"notification permission granted");
                CourseReminder.schedule(this,due-1000,true);
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due,"first future appointment");
                check(manager().getActiveNotifications().length==0,"no premature send");
                check(CourseReminder.status(this).contains("下次计划提醒"),"next reminder displayed");
                CourseReminder.schedule(this,due+300000,false);
                check(manager().getActiveNotifications().length==1,"reopening sends unsent reminder");
                Notification actual=manager().getActiveNotifications()[0].getNotification();
                check(actual.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString().equals("提醒测试课程"),"full course title");
                check(actual.extras.getCharSequence(Notification.EXTRA_TEXT).toString().equals("13:30 开课 · 测试教室"),"actual dispatched summary shows classroom");
                check(actual.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().equals("13:30 开课\n授课教师：测试教师\n上课教室：测试教室"),"actual dispatched reminder has all four fields");
                check(actual.getTimeoutAfter()==600000,"late delivery expires at actual start");
                long posted=manager().getActiveNotifications()[0].getPostTime();
                String key=manager().getActiveNotifications()[0].getTag();
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due+7*86400000L,"next week reserved");
                CourseReminder.schedule(this,due+310000,false);
                check(manager().getActiveNotifications()[0].getPostTime()==posted,"reopen does not repeat");
                manager().cancel(key,151);awaitCount(0);CourseReminder.schedule(this,due+320000,true);
                check(manager().getActiveNotifications().length==0,"dismissed reminder stays dismissed even on forced rearm");
                CourseReminder.prefs(this).edit().remove("sent").commit();CourseReminder.schedule(this,start,true);
                check(manager().getActiveNotifications().length==0,"class already started is not sent");
                CourseReminder.enable(this,false);
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==0,"switch off clears appointment");
                check(CourseReminder.testNotification(this).contains("已发送"),"explicit test works without enabling reminders");
                awaitCount(1);
                check(manager().getActiveNotifications().length==1,"test is a real notification");
                check(!CourseReminder.enabled(this),"test does not enable reminders");
                check(CourseReminder.prefs(this).getStringSet("sent",Set.of()).isEmpty(),"test does not mark courses sent");
                manager().cancelAll();
                course.remove("teacher");getSharedPreferences("timetable",MODE_PRIVATE).edit().putString("state",sample.toString()).commit();
                check(CourseReminder.events(this).get(0).course.teacher.isEmpty(),"legacy course without teacher still schedules");
                check(NoticePreview.sample(this).builder(this,start,true).build().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("授课教师：未提供"),"legacy preview never invents teacher");
                WidgetPinRequest.prefs(this).edit().clear().commit();check(WidgetPinRequest.status(this).isEmpty(),"no phantom pin result");
                WidgetPinRequest.prefs(this).edit().putString("state","pending").putString("token","expected").putLong("at",System.currentTimeMillis()).commit();
                check(!WidgetPinRequest.status(this).startsWith("已添加"),"request is not success");
                WidgetPinRequest.prefs(this).edit().putLong("at",System.currentTimeMillis()-11000).commit();
                check(WidgetPinRequest.status(this).startsWith("尚未确认"),"no callback remains unconfirmed, not failed");
                new WidgetPinRequest().onReceive(this,new Intent().setAction(WidgetPinRequest.ADDED).putExtra("token","stale"));
                check(WidgetPinRequest.status(this).startsWith("尚未确认"),"stale callback ignored");
                new WidgetPinRequest().onReceive(this,new Intent().setAction(WidgetPinRequest.ADDED).putExtra("token","expected"));
                check(WidgetPinRequest.status(this).startsWith("已添加"),"valid callback confirms");
                WidgetPinRequest.prefs(this).edit().putString("state","unsupported").commit();
                check(WidgetPinRequest.status(this).contains("4×3"),"unsupported guidance");
                WidgetPinRequest.prefs(this).edit().putString("state","failed").commit();
                check(WidgetPinRequest.status(this).startsWith("添加请求未完成"),"exception guidance");
            }
            CourseReminder.enable(this,false);WidgetPinRequest.prefs(this).edit().clear().commit();
            android.util.Log.i("NativeReminderProbe","PASS "+checks+" native checks; blocked="+blocked);
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(32,96,32,32);
            TextView report=new TextView(this);report.setText("PASS "+checks+"\n"+(blocked?"通知权限关闭":"提醒补发与防重复"));report.setTextSize(18);content.addView(report);
            Button pin=new Button(this);pin.setText("测试添加小组件");pin.setOnClickListener(v->{WidgetPinRequest.request(this);report.setText(WidgetPinRequest.status(this));});content.addView(pin);
            Button result=new Button(this);result.setText("查看添加结果");result.setOnClickListener(v->report.setText(WidgetPinRequest.status(this)));content.addView(result);
            setContentView(content);
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
