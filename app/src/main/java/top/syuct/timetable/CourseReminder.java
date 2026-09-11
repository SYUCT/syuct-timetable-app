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
    static final String CHANNEL="course_reminders",FIRE="top.syuct.timetable.COURSE_REMINDER";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("course_reminders",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static boolean precise(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static void channel(Context c){
        NotificationChannel ch=new NotificationChannel(CHANNEL,"上课提醒",NotificationManager.IMPORTANCE_HIGH);
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
        AlarmManager manager=c.getSystemService(AlarmManager.class);manager.cancel(alarm(c,0));
        if(!enabled(c)||!notifications(c))return;
        long next=ReminderPlanner.next(events(c),System.currentTimeMillis());if(next==0)return;
        PendingIntent pi=alarm(c,next);
        try{if(precise(c)){manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi);return;}}catch(SecurityException ignored){}
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi);
    }
    static void enable(Context c,boolean value){prefs(c).edit().putBoolean("enabled",value).apply();if(!value)c.getSystemService(NotificationManager.class).cancelAll();schedule(c);}
    static String status(Context c){
        if(!enabled(c))return "未开启。开启后，每次上课前15分钟提醒。";
        if(!notifications(c))return "尚未允许通知，请在系统设置中开启。";
        if(!precise(c))return "已开启；未允许准时提醒，通知可能延迟。";
        if(events(c).isEmpty())return "已开启。请导入课表、设置第一周日期和上课时间。";
        return "已开启：每次上课前15分钟提醒，使用系统默认提示音。";
    }
    @Override public void onReceive(Context context,Intent intent){
        Context c=context.getApplicationContext();
        if(FIRE.equals(intent.getAction()))fire(c,intent.getLongExtra("at",0));
        schedule(c);
    }
    @android.annotation.SuppressLint("MissingPermission") // notifications() checks runtime permission and channel.
    private static synchronized void fire(Context c,long at){
        if(at<=0||!enabled(c)||!notifications(c))return;
        SharedPreferences p=prefs(c);long now=System.currentTimeMillis();
        String day=Instant.ofEpochMilli(now).atZone(LessonClock.ZONE).toLocalDate().toString();
        Set<String> sent=new HashSet<>(day.equals(p.getString("sentDay",""))?p.getStringSet("sent",Collections.emptySet()):Collections.emptySet());
        for(ReminderPlanner.Event e:ReminderPlanner.due(events(c),at,now,sent)){
            Intent open=new Intent(c,MainActivity.class).setAction(TodayWidget.OPEN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi=PendingIntent.getActivity(c,170,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String time=Instant.ofEpochMilli(e.start).atZone(LessonClock.ZONE).toLocalTime().toString();
            String body=time+" 开课 · "+(e.course.room.isEmpty()?"地点待定":e.course.room);
            Notification publicNotice=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("上课提醒").setContentText("即将上课，点击查看课表").build();
            Notification notice=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(e.course.name).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicNotice).setOnlyAlertOnce(true)
                .setTimeoutAfter(e.start-now).build();
            try{c.getSystemService(NotificationManager.class).notify(e.key,151,notice);sent.add(e.key);}catch(SecurityException ignored){}
        }
        p.edit().putString("sentDay",day).putStringSet("sent",sent).commit();
        TodayWidget.refreshAll(c);
    }
}
