package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.media.AudioAttributes;
import android.provider.Settings;
import org.json.*;
import java.util.*;
import java.time.*;

/** Only local schedule data is used. One next alarm, no polling service. */
public final class CourseReminder extends BroadcastReceiver {
    static final String CHANNEL="course_notices",FIRE="top.syuct.timetable.COURSE_REMINDER";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("course_reminders",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static void channel(Context c){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(manager.getNotificationChannel("course_reminders")!=null){manager.cancelAll();manager.deleteNotificationChannel("course_reminders");}
        NotificationChannel ch=new NotificationChannel(CHANNEL,"上课提醒",NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("每次上课前15分钟提醒，使用系统默认通知声音");
        ch.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        ch.enableVibration(true);ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
    static boolean notifications(Context c){
        channel(c);NotificationManager m=c.getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return false;
        return m.areNotificationsEnabled()&&m.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    private static PendingIntent alarm(Context c,long at){
        return PendingIntent.getBroadcast(c,150,new Intent(c,CourseReminder.class).setAction(FIRE).putExtra("at",at),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static List<ReminderPlanner.Event> events(Context c){
        try{
            JSONObject state=new JSONObject(c.getSharedPreferences("timetable",Context.MODE_PRIVATE).getString("state",""));
            JSONObject s=state.getJSONObject("settings");JSONArray raw=state.getJSONArray("courses");
            String[][] times=WidgetState.readTimes(s.has("periodTimes")?s.getJSONArray("periodTimes"):new JSONArray(WidgetState.defaults(c)));
            List<ReminderPlanner.Course> courses=new ArrayList<>();
            for(int i=0;i<raw.length();i++){JSONObject x=raw.getJSONObject(i);courses.add(new ReminderPlanner.Course(x.getString("name"),x.optString("room"),x.getString("weekType"),x.getInt("weekday"),x.getInt("startSection"),x.getInt("startWeek"),x.getInt("endWeek")));}
            return ReminderPlanner.events(s.optString("firstWeekDate"),s.getInt("totalWeeks"),times,courses);
        }catch(Exception e){return Collections.emptyList();}
    }
    static synchronized void schedule(Context c){
        schedule(c,System.currentTimeMillis(),false);
    }
    // Clock argument is also used by the isolated native regression harness.
    static synchronized void schedule(Context c,long now,boolean force){
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        PendingIntent previous=PendingIntent.getBroadcast(c,150,new Intent(c,CourseReminder.class).setAction(FIRE),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        SharedPreferences p=prefs(c);
        if(!enabled(c)||!notifications(c)){
            if(previous!=null)manager.cancel(previous);
            p.edit().remove("scheduledAt").remove("scheduledExact").remove("scheduleError").apply();return;
        }
        List<ReminderPlanner.Event> all=events(c);
        LiveCourseNotice.reconcile(c,all,now);
        Set<String> sent=sent(c,now);
        p.edit().remove("deliveryError").apply();
        // Opening/saving/rebooting during the reminder window must not drop an unsent class.
        firePending(c,all,now,sent);
        long next=ReminderPlanner.next(all,now,sent);
        boolean exact=ExactReminder.active(c);
        if(!force&&next>0&&previous!=null&&p.getLong("scheduledAt",0)==next&&p.getBoolean("scheduledExact",false)==exact)return;
        if(previous!=null)manager.cancel(previous);
        p.edit().remove("scheduledAt").remove("scheduledExact").remove("scheduleError").apply();
        if(next==0)return;
        try{
            boolean reservedExact=ExactReminder.reserve(manager,next,alarm(c,next),exact);
            p.edit().putLong("scheduledAt",next).putBoolean("scheduledExact",reservedExact).apply();
            if(exact&&!reservedExact)p.edit().putString("scheduleError","精确定时权限发生变化，已改用普通提醒；请重新检查授权。").apply();
        }catch(RuntimeException e){p.edit().putString("scheduleError","预约失败，请重新打开 App 或检查系统后台限制。").apply();}
    }
    private static Set<String> sent(Context c,long now){
        Set<String> result=new HashSet<>(prefs(c).getStringSet("sent",Collections.emptySet()));
        // Keep a rolling journal across midnight; midnight courses must not be re-sent.
        result.removeIf(key->{try{return Long.parseLong(key.substring(0,key.indexOf('|')))<now-86400000L;}catch(RuntimeException e){return true;}});
        return result;
    }
    static void enable(Context c,boolean value){prefs(c).edit().putBoolean("enabled",value).apply();if(!value)c.getSystemService(NotificationManager.class).cancelAll();schedule(c);}
    static String status(Context c){
        if(!enabled(c))return "未开启。开启后，每次上课前15分钟提醒。";
        if(!notifications(c))return "尚未允许通知，请在系统设置中开启。";
        List<ReminderPlanner.Event> all=events(c);
        if(all.isEmpty())return "已开启。请导入课表、设置第一周日期和上课时间。";
        long now=System.currentTimeMillis(),next=ReminderPlanner.next(all,now,sent(c,now));
        String plan=next==0?"本学期暂无后续提醒。":"下次计划提醒："+Instant.ofEpochMilli(next).atZone(LessonClock.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        for(ReminderPlanner.Event e:all)if(e.remind==next){plan+="\n"+e.course.name;break;}
        if(!ReminderPlanner.pending(all,now,sent(c,now)).isEmpty())plan="当前有待发送的课前提醒。\n"+plan;
        String error=prefs(c).getString("scheduleError",prefs(c).getString("deliveryError",""));
        return "已开启。"+plan+"\n"+ExactReminder.status(c)+"。上课后不补发。"+(error.isEmpty()?"":"\n"+error);
    }
    @Override public void onReceive(Context context,Intent intent){
        Context c=context.getApplicationContext();String action=intent.getAction();
        if(FIRE.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)){
            schedule(c,System.currentTimeMillis(),true);
            TodayWidget.refreshAll(c);
        }
    }
    @android.annotation.SuppressLint("MissingPermission") // notifications() checks runtime permission and channel.
    private static void firePending(Context c,List<ReminderPlanner.Event> all,long now,Set<String> sent){
        SharedPreferences p=prefs(c);
        for(ReminderPlanner.Event e:ReminderPlanner.pending(all,now,sent)){
            Intent open=new Intent(c,MainActivity.class).setAction(TodayWidget.OPEN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi=PendingIntent.getActivity(c,170,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String time=CourseNoticeStyle.time(e.start);
            String body=time+" 开课 · "+(e.course.room.isEmpty()?"地点待定":e.course.room);
            Notification publicNotice=CourseNoticeStyle.apply(c,new Notification.Builder(c,CHANNEL)).setContentTitle("上课提醒").setContentText("即将上课，点击查看课表").build();
            Notification.Builder builder=new Notification.Builder(c,CHANNEL)
                .setContentTitle(CourseNoticeStyle.title(e.course.name)).setContentText(body)
                .setStyle(new Notification.BigTextStyle().setBigContentTitle(e.course.name).bigText(CourseNoticeStyle.details(e.start,e.course.room)))
                .setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicNotice).setOnlyAlertOnce(true)
                .setTimeoutAfter(e.start-now);
            Notification notice=LiveCourseNotice.build(c,builder,e.course.name,e.key,151,e.start,now);
            try{
                c.getSystemService(NotificationManager.class).notify(e.key,151,notice);sent.add(e.key);
                // Persist each event before the next one; stable tags also prevent duplicate entries.
                p.edit().putStringSet("sent",new HashSet<>(sent)).commit();
            }catch(RuntimeException e1){p.edit().putString("deliveryError","通知未能发送，请检查系统通知权限后重新打开 App。").apply();}
        }
        p.edit().remove("sentDay").putStringSet("sent",new HashSet<>(sent)).commit();
    }
    @android.annotation.SuppressLint("MissingPermission")
    static String testNotification(Context c){
        if(!notifications(c))return "未允许通知，请点「权限与声音」开启。";
        try{
            Notification notice=CourseNoticeStyle.apply(c,NoticePreview.sample(c).builder(c,System.currentTimeMillis(),false))
                .setCategory(Notification.CATEGORY_REMINDER).build();
            c.getSystemService(NotificationManager.class).notify("reminder_test",152,notice);
            return "通知预览已发送，请下拉通知栏查看。这不代表后台提醒一定准时。";
        }catch(RuntimeException e){return "测试通知发送失败，请检查系统通知设置。";}
    }
}
