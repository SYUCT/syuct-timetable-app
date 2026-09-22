package top.syuct.timetable;

import android.app.*;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import java.util.Locale;

/** Only icon detection and cancellation of notices posted by older versions. */
final class NoticeCompat {
    static boolean xiaomi(String brand,String maker){
        String b=brand==null?"":brand.toLowerCase(Locale.ROOT),m=maker==null?"":maker.toLowerCase(Locale.ROOT);
        return b.equals("xiaomi")||b.equals("redmi")||b.equals("poco")||m.equals("xiaomi");
    }
    static boolean xiaomi(){return xiaomi(Build.BRAND,Build.MANUFACTURER);}
    static boolean legacy(Notification n){
        return n.extras.containsKey("miui.focus.param")||n.extras.containsKey("notification.superx.operation");
    }
    @android.annotation.SuppressLint("MissingPermission")
    static void cancel(Context c,String tag,int id){
        // A cosmetic timer cancellation error must not prevent normal dismissal.
        try{CountdownDisplay.cancel(c,tag,id);}catch(RuntimeException ignored){}
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        try{
            for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications()){
                if(n.getId()!=id||!java.util.Objects.equals(tag,n.getTag())||!n.getNotification().extras.containsKey("notification.superx.operation"))continue;
                Bundle end=new Bundle();end.putInt("notification.superx.operation",2);
                manager.notify(tag,id,Notification.Builder.recoverBuilder(c,n.getNotification())
                    .addExtras(end).setOnlyAlertOnce(true).setOngoing(false).setTimeoutAfter(1).build());
            }
        }catch(RuntimeException ignored){}finally{manager.cancel(tag,id);}
    }
}
