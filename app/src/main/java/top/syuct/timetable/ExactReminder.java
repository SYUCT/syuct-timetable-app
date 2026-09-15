package top.syuct.timetable;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

/** User-selected precision, not an alarm-clock entry or a persistent service. */
final class ExactReminder {
    static boolean requested(Context c){return CourseReminder.prefs(c).getBoolean("preciseReminder",false);}
    static boolean permitted(Context c){
        if(Build.VERSION.SDK_INT<31)return true;
        try{return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
        catch(RuntimeException e){return false;}
    }
    static boolean active(Context c){return requested(c)&&permitted(c);}
    static void enable(Context c,boolean value){
        CourseReminder.prefs(c).edit().putBoolean("preciseReminder",value).apply();
        CourseReminder.schedule(c,System.currentTimeMillis(),true);
    }
    static String status(Context c){
        if(!requested(c))return "普通提醒 · 可能受系统省电影响而延迟";
        if(!permitted(c))return "待授权 · 当前仍为普通提醒，可能延迟";
        return "准时提醒已就绪 · 使用系统精确定时";
    }
    // Permission can disappear between checking and reserving. Keep a usable fallback.
    static boolean reserve(AlarmManager manager,long at,PendingIntent intent,boolean exact){
        if(exact){
            try{manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent);return true;}
            catch(SecurityException ignored){}
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent);return false;
    }
}
