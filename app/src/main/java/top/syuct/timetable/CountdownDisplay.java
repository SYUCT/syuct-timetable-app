package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

/** HyperOS ignores the generic chip chronometer on some builds. One non-wakeup
 * minute refresh per active notice; no service, loop, new permission or sent-state writes. */
final class CountdownDisplay {
    static final String REFRESH="top.syuct.timetable.REFRESH_COUNTDOWN",TARGET="syuct.countdown.target";
    static Notification.Builder apply(Notification.Builder b,long start,long now){
        Bundle data=new Bundle();data.putLong(TARGET,start);
        b.addExtras(data).setContentTitle(CountdownText.label(start,now));
        if(Build.VERSION.SDK_INT>=36)b.setShortCriticalText(CountdownText.label(start,now));
        return b;
    }
    private static PendingIntent pending(Context c,String key,int id){
        Intent intent=new Intent(c,LiveCourseNotice.class).setAction(REFRESH)
            .setData(Uri.parse("syuct-live://refresh/"+Uri.encode(key))).putExtra("key",key).putExtra("id",id);
        return PendingIntent.getBroadcast(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context c,String key,int id){c.getSystemService(AlarmManager.class).cancel(pending(c,key,id));}
    static void schedule(Context c,String key,int id,Notification n,long now){
        long start=n.extras.getLong(TARGET),at=CountdownText.next(start,now);
        if(!LiveCourseNotice.enabled(c)||at==0||start-now>ReminderPlanner.LEAD)return;
        AlarmManager manager=c.getSystemService(AlarmManager.class);PendingIntent intent=pending(c,key,id);
        // This display update must not wake a sleeping phone. Delivery of the actual
        // class reminder remains exclusively owned by CourseReminder/ExactReminder.
        if(ExactReminder.active(c))try{manager.setExact(AlarmManager.RTC,at,intent);return;}catch(SecurityException ignored){}
        manager.set(AlarmManager.RTC,at,intent);
    }
    @android.annotation.SuppressLint("MissingPermission")
    static void refresh(Context c,String key,int id){
        if(!LiveCourseNotice.enabled(c)||!CourseReminder.notifications(c))return;
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        long now=System.currentTimeMillis();
        for(StatusBarNotification item:manager.getActiveNotifications()){
            if(item.getId()!=id||!java.util.Objects.equals(key,item.getTag()))continue;
            Notification old=item.getNotification();long start=old.extras.getLong(TARGET);
            if(start==0)return;
            if(start<=now||start-now>ReminderPlanner.LEAD){NoticeCompat.cancel(c,key,id);return;}
            Notification updated=apply(Notification.Builder.recoverBuilder(c,old),start,now)
                .setOnlyAlertOnce(true).setTimeoutAfter(start-now).build();
            manager.notify(key,id,updated);schedule(c,key,id,updated,now);return;
        }
        // No active record: the user dismissed it. Never reconstruct or resurrect it.
    }
}
