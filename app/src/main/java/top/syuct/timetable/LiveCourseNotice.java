package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import java.util.List;

/** Optional system-owned live countdown. No foreground service or refresh loop. */
public final class LiveCourseNotice extends BroadcastReceiver {
    static final String END="top.syuct.timetable.END_LIVE_COURSE", PREVIEW="live_course_preview";
    static boolean enabled(Context c){return CourseReminder.prefs(c).getBoolean("liveCountdown",false);}
    static boolean supported(){return Build.VERSION.SDK_INT>=36;}
    static boolean available(Context c){
        if(Build.VERSION.SDK_INT<36||!enabled(c))return false;
        try{return c.getSystemService(NotificationManager.class).canPostPromotedNotifications();}
        catch(RuntimeException e){return false;}
    }
    static void enable(Context c,boolean value){
        CourseReminder.prefs(c).edit().putBoolean("liveCountdown",value).apply();
        if(!value)cancelLive(c);
    }
    static String status(Context c){
        if(!supported())return "当前系统不支持 Android 16 实时通知，仍使用普通提醒。";
        if(!enabled(c))return "开启后，课前15分钟尝试显示系统倒计时；上课或关闭后结束。";
        if(XiaomiIsland.device())return XiaomiIsland.status(XiaomiIsland.protocol(c),XiaomiIsland.permission(c));
        if(VivoIsland.device())return "已开启 vivo 原子岛实验兼容；是否显示取决于系统版本和接入权限，不支持时保留普通通知。";
        return available(c)?"已开启。是否上岛及展示样式由手机系统决定。":"已开启，但系统未允许提升显示，仍使用普通通知。";
    }
    static Notification build(Context c,Notification.Builder builder,String name,String key,int id,long start,long now){
        CourseNoticeStyle.apply(c,builder);
        boolean vivo=VivoIsland.attempt(c);
        boolean xiaomi=XiaomiIsland.attempt(c);
        if(vivo)builder.addExtras(VivoIsland.extras(c,name,start,builder.build().contentIntent));
        // Unsupported/disallowed systems must retain a dismissible, non-ongoing notice.
        if(Build.VERSION.SDK_INT<36||(!available(c)&&!vivo&&!xiaomi))return builder.build();
        PendingIntent end=PendingIntent.getBroadcast(c,id,new Intent(c,LiveCourseNotice.class)
            .setAction(END).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).setData(Uri.parse("syuct-live://end/"+Uri.encode(key)))
            .putExtra("key",key).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Action endAction=new Notification.Action.Builder(null,"结束提醒",end).build();
        if(xiaomi){
            try{
                builder.addExtras(XiaomiIsland.extras(c,name,builder.build(),start,now,endAction));
                return nativeNotice(builder,endAction,end,start,now);
            }
            catch(org.json.JSONException|RuntimeException e){
                xiaomi=false;
                builder.getExtras().remove(XiaomiIsland.PARAM);
                builder.getExtras().remove("miui.focus.pics");
                builder.getExtras().remove("miui.focus.actions");
            }
        }
        // Official extras contract also works with compileSdk 36. The native setter
        // was only exposed in SDK 36.1; do not invoke it on base Android 16.
        android.os.Bundle extras=new android.os.Bundle();extras.putBoolean("android.requestPromotedOngoing",true);
        // Fallback is one short text slot, NOT two OEM regions. Retain both pieces
        // of information; the system may truncate this on narrow devices.
        builder.addExtras(extras).setOngoing(available(c)).setShortCriticalText(
                XiaomiIsland.device()?CourseNoticeStyle.fallbackChip(name,start):CourseNoticeStyle.compactTitle(name))
            .setWhen(start).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(Math.max(1,start-now)).setDeleteIntent(end)
            .addAction(endAction);
        Notification notice=builder.build();
        if(!notice.hasPromotableCharacteristics()&&!vivo&&!xiaomi){
            extras.putBoolean("android.requestPromotedOngoing",false);
            return builder.addExtras(extras).setOngoing(false).setUsesChronometer(false).setShortCriticalText(null)
                .setDeleteIntent(null).setActions(new Notification.Action[0]).build();
        }
        return notice;
    }
    static Notification nativeNotice(Notification.Builder builder,Notification.Action action,PendingIntent end,long start,long now){
        android.os.Bundle extras=new android.os.Bundle();extras.putBoolean("android.requestPromotedOngoing",false);
        // One renderer at a time: don't ask Android's generic chip to override
        // the permitted Xiaomi focus template. OEM updatable controls its life.
        builder.getExtras().remove("android.shortCriticalText");
        return builder.addExtras(extras).setOngoing(false)
            .setWhen(start).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(Math.max(1,start-now)).setDeleteIntent(end).addAction(action).build();
    }
    static void cancelLive(Context c){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        for(StatusBarNotification n:manager.getActiveNotifications())
            if((n.getId()==151||PREVIEW.equals(n.getTag()))&&((n.getNotification().flags&Notification.FLAG_ONGOING_EVENT)!=0||VivoIsland.hasPayload(n.getNotification())||XiaomiIsland.hasPayload(n.getNotification())))
                VivoIsland.cancel(c,n.getTag(),n.getId());
    }
    static void reconcile(Context c,List<ReminderPlanner.Event> events,long now){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        for(StatusBarNotification n:manager.getActiveNotifications()){
            if(n.getId()!=151)continue;
            boolean current=false;
            for(ReminderPlanner.Event e:events)if(e.key.equals(n.getTag())&&e.start>now){current=true;break;}
            if(!current)VivoIsland.cancel(c,n.getTag(),n.getId());
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    static String preview(Context c){
        if(!CourseReminder.notifications(c))return "请先允许通知，再预览倒计时。";
        if(!enabled(c))return "请先开启课前实时倒计时。";
        long now=System.currentTimeMillis(),start=now+180000;
        NoticePreview sample=NoticePreview.sample(c);
        Notification.Builder builder=sample.builder(c,start,true);
        try{
            c.getSystemService(NotificationManager.class).notify(PREVIEW,153,build(c,builder,sample.name,PREVIEW,153,start,now));
            if(XiaomiIsland.device())return "已发送3分钟预览。"+XiaomiIsland.status(XiaomiIsland.protocol(c),XiaomiIsland.permission(c));
            if(VivoIsland.attempt(c))return "已发送3分钟原子岛兼容预览；是否上岛由 vivo 系统决定。";
            return available(c)?"已发送3分钟预览，请查看状态栏或锁屏；系统决定是否上岛。":"已发送普通通知预览，当前系统未允许实时显示。";
        }catch(RuntimeException e){return "预览未能发送，请检查系统通知设置。";}
    }
    @Override public void onReceive(Context c,Intent intent){
        if(!END.equals(intent.getAction()))return;
        String key=intent.getStringExtra("key");int id=intent.getIntExtra("id",0);
        if(key==null||key.length()>2000||!(id==151||id==153&&PREVIEW.equals(key)))return;
        // CourseReminder's durable sent journal already prevents re-posting after dismissal.
        VivoIsland.cancel(c,key,id);
    }
}
