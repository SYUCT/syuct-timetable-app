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
    void backgroundDelivery()throws Exception{
        CourseReminder.enable(this,false);CourseReminder.prefs(this).edit().clear().commit();
        ZonedDateTime start=ZonedDateTime.now(LessonClock.ZONE).plusMinutes(16).plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        if(start.toLocalTime().equals(LocalTime.of(23,59)))start=start.plusMinutes(2);
        JSONArray times=new JSONArray();
        for(int i=0;i<12;i++)times.put(new JSONArray().put(i==0?start.toLocalTime().toString():"").put(i==0?start.plusMinutes(1).toLocalTime().toString():""));
        JSONObject course=new JSONObject().put("name","后台恢复验证课程").put("teacher","测试教师").put("room","测试楼101").put("weekType","all").put("weekday",start.getDayOfWeek().getValue()).put("startSection",1).put("startWeek",1).put("endWeek",1);
        JSONObject settings=new JSONObject().put("firstWeekDate",start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()).put("totalWeeks",20).put("periodTimes",times);
        getSharedPreferences("timetable",0).edit().putString("state",new JSONObject().put("settings",settings).put("courses",new JSONArray().put(course)).toString()).commit();
        check(CourseReminder.notifications(this),"background notification allowed");
        CourseReminder.prefs(this).edit().putBoolean("enabled",true).putBoolean("preciseReminder",true).commit();
        check(ExactReminder.active(this),"background precision allowed");
        CourseReminder.schedule(this);
        long due=start.toInstant().toEpochMilli()-ReminderPlanner.LEAD;
        check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due,"real future appointment");
        PendingIntent token=PendingIntent.getBroadcast(this,150,new Intent(this,CourseReminder.class).setAction(CourseReminder.FIRE),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        getSystemService(AlarmManager.class).cancel(token);
        CourseReminder.schedule(this);
        check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due&&CourseReminder.prefs(this).getBoolean("scheduledExact",false),"real appointment restored");
        android.util.Log.i("NativeReminderProbe","BACKGROUND_RESERVED due="+due+" checks="+checks);
        TextView report=new TextView(this);report.setText("后台恢复隔离回归");setContentView(report);moveTaskToBack(true);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!getPackageName().endsWith(".reminderprobe"))throw new SecurityException("Isolated QA package required");
        try{
            if(getIntent().getBooleanExtra("background",false)){backgroundDelivery();return;}
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
            check(sampleNotice.extras.getCharSequence(Notification.EXTRA_TITLE).toString().equals("提醒测试课程"),"ordinary preview title is complete");
            check(sampleNotice.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("教师：测试教师"),"preview compact teacher");
            check(sampleNotice.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)[0].toString().equals("教室：测试教室"),"preview expanded classroom is first");
            check(sampleNotice.getTimeoutAfter()==900000,"preview lasts fifteen minutes");
            CourseReminder.prefs(this).edit().putBoolean("enabled",true).commit();
            if(blocked){
                check(!CourseReminder.notifications(this),"notification permission denied");
                CourseReminder.schedule(this,due+300000);
                awaitCount(1);
                check(manager().getActiveNotifications().length==0,"blocked cannot send");
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==0,"blocked cannot schedule");
                check(CourseReminder.testNotification(this).contains("未允许"),"blocked test gives guidance");
            }else{
                check(CourseReminder.notifications(this),"notification permission granted");
                CourseReminder.schedule(this,due-1000);
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due,"first future appointment");
                check(manager().getActiveNotifications().length==0,"no premature send");
                check(CourseReminder.status(this).contains("下次计划提醒"),"next reminder displayed");
                PendingIntent old=PendingIntent.getBroadcast(this,150,new Intent(this,CourseReminder.class).setAction(CourseReminder.FIRE),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                check(old!=null,"reservation token exists");
                getSystemService(AlarmManager.class).cancel(old);
                check(PendingIntent.getBroadcast(this,150,new Intent(this,CourseReminder.class).setAction(CourseReminder.FIRE),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE)!=null,"token survives removal of actual alarm");
                CourseReminder.schedule(this,due-500);
                check(CourseReminder.prefs(this).getLong("lastReservedAt",0)==due-500,"unchanged cached appointment is registered again");
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due,"restored appointment retains course time");
                int[] compatibilityFailures={0,0};
                Context noNotifications=new ContextWrapper(this){
                    @Override public Context getApplicationContext(){return this;}
                    @Override public Object getSystemService(String name){if(Context.NOTIFICATION_SERVICE.equals(name)){compatibilityFailures[0]++;throw new UnsupportedOperationException("probe notification inspection");}return super.getSystemService(name);}
                };
                LiveCourseNotice.reconcile(noNotifications,CourseReminder.events(this),due-500);
                LiveCourseNotice.cancelLive(noNotifications);
                check(compatibilityFailures[0]==2,"unsupported live cleanup never escapes into ordinary scheduling");
                new LiveCourseNotice().onReceive(noNotifications,new Intent(LiveCourseNotice.END).putExtra("key",LiveCourseNotice.PREVIEW).putExtra("id",153));
                check(compatibilityFailures[0]>2,"unsupported live cancellation remains optional");
                Context noWidgets=new ContextWrapper(this){
                    @Override public Context getApplicationContext(){return this;}
                    @Override public Object getSystemService(String name){if(Context.APPWIDGET_SERVICE.equals(name)){compatibilityFailures[1]++;throw new UnsupportedOperationException("probe widget service");}return super.getSystemService(name);}
                };
                CourseReminder.prefs(this).edit().remove("lastReservedAt").commit();
                CourseReminder.refresh(noWidgets);
                check(CourseReminder.prefs(this).getLong("lastReservedAt",0)>0,"widget failure does not prevent reminder restoration");
                check(compatibilityFailures[1]>0,"widget service fault actually exercised");
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due,"widget failure preserves next appointment");
                CourseReminder.schedule(this,due+300000);
                awaitCount(1);
                check(manager().getActiveNotifications().length==1,"reopening sends unsent reminder");
                Notification actual=manager().getActiveNotifications()[0].getNotification();
                check(actual.extras.getCharSequence(Notification.EXTRA_TITLE).toString().equals("提醒测试课程"),"ordinary reminder title is complete");
                check(actual.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString().equals("提醒测试课程"),"full course title");
                check(actual.extras.getCharSequence(Notification.EXTRA_TEXT).toString().equals("教室：测试教室\n开课时间：13:30\n授课教师：测试教师"),"actual summary puts classroom first");
                CharSequence[] detailRows=actual.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                check(detailRows.length==3&&detailRows[0].toString().equals("教室：测试教室")&&detailRows[1].toString().equals("开课时间：13:30")&&detailRows[2].toString().equals("授课教师：测试教师"),"actual expanded reminder shows classroom first");
                check(actual.getTimeoutAfter()==600000,"late delivery expires at actual start");
                check(CourseReminder.prefs(this).getLong("lastPostedAt",0)==due+300000,"ordinary submission recorded without claiming host display");
                long posted=manager().getActiveNotifications()[0].getPostTime();
                String key=manager().getActiveNotifications()[0].getTag();
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)==due+7*86400000L,"next week reserved");
                CourseReminder.schedule(this,due+310000);
                check(manager().getActiveNotifications()[0].getPostTime()==posted,"reopen does not repeat");
                manager().cancel(key,151);awaitCount(0);CourseReminder.schedule(this,due+320000);
                check(manager().getActiveNotifications().length==0,"dismissed reminder stays dismissed even on forced rearm");
                CourseReminder.prefs(this).edit().remove("sent").commit();CourseReminder.schedule(this,start);
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
                check(NoticePreview.sample(this).builder(this,start,true).build().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)[2].toString().equals("授课教师：未提供"),"legacy preview never invents teacher");
                check(CourseReminder.prefs(this).getLong("lastAlarmAt",0)==0,"foreground calls do not pretend background delivery");
                CourseReminder.prefs(this).edit().putBoolean("enabled",true).commit();
                new CourseReminder().onReceive(this,new Intent(CourseReminder.FIRE));
                check(CourseReminder.prefs(this).getLong("lastAlarmAt",0)>0,"alarm receiver records a background trigger");
                check(CourseReminder.status(this).contains("最近后台触发：")&&CourseReminder.status(this).contains("最近通知提交："),"diagnostic timestamps visible in reminder settings");
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
