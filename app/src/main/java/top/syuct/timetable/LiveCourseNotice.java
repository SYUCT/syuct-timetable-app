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
        return available(c)?"已开启。课前15分钟显示剩余倒计时；点开查看课程、时间、教师和教室。是否上岛及秒数样式由手机系统决定。":"已开启，但系统未允许提升显示，仍使用普通通知。";
    }
    static Notification build(Context c,Notification.Builder builder,String name,String key,int id,long start,long now){
        CourseNoticeStyle.apply(c,builder);
        // Unsupported/disallowed systems must retain a dismissible, non-ongoing notice.
        if(Build.VERSION.SDK_INT<36||!available(c))return builder.build();
        PendingIntent end=PendingIntent.getBroadcast(c,id,new Intent(c,LiveCourseNotice.class)
            .setAction(END).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).setData(Uri.parse("syuct-live://end/"+Uri.encode(key)))
            .putExtra("key",key).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Action endAction=new Notification.Action.Builder(null,"结束提醒",end).build();
        // No fixed short text: SystemUI owns the ticking countdown from `when`.
        // Never restart at 15 minutes if delivery was late; target actual class start.
        liveState(builder,endAction,end,start,now,available(c));
        Notification notice=builder.build();
        if(!notice.hasPromotableCharacteristics()){
            android.os.Bundle extras=new android.os.Bundle();
            extras.putBoolean("android.requestPromotedOngoing",false);
            return builder.addExtras(extras).setOngoing(false).setUsesChronometer(false).setShortCriticalText(null)
                .setDeleteIntent(null).setActions(new Notification.Action[0]).build();
        }
        return notice;
    }
    static Notification.Builder liveState(Notification.Builder builder,Notification.Action action,PendingIntent end,long start,long now,boolean promoted){
        if(Build.VERSION.SDK_INT<36)return builder;
        // No unverified Xiaomi template can suppress the standard live state.
        builder.getExtras().remove(XiaomiIsland.PARAM);
        builder.getExtras().remove("miui.focus.pics");
        builder.getExtras().remove("miui.focus.actions");
        // compileSdk 36 extras contract; the native setter is only in SDK 36.1.
        android.os.Bundle extras=new android.os.Bundle();extras.putBoolean("android.requestPromotedOngoing",promoted);
        return builder.addExtras(extras).setOngoing(promoted).setShortCriticalText(null)
            .setWhen(start).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(Math.max(1,start-now)).setDeleteIntent(end).addAction(action);
    }
    static void cancelLive(Context c){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        for(StatusBarNotification n:manager.getActiveNotifications())
            if(PREVIEW.equals(n.getTag())||n.getId()==151&&((n.getNotification().flags&Notification.FLAG_ONGOING_EVENT)!=0||VivoIsland.hasPayload(n.getNotification())||XiaomiIsland.hasPayload(n.getNotification())))
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
    static String preview(Context c){
        return submitPreview(c).message;
    }
    static final class PreviewResult {
        final long target;final String message;
        PreviewResult(long target,String message){this.target=target;this.message=message;}
    }
    @android.annotation.SuppressLint("MissingPermission")
    static PreviewResult submitPreview(Context c){
        try{
            if(!CourseReminder.notifications(c))return new PreviewResult(0,"请先允许通知，再预览倒计时。");
            if(!enabled(c))return new PreviewResult(0,"请先开启课前实时倒计时。");
            long now=System.currentTimeMillis(),start=now+ReminderPlanner.LEAD;
            NoticePreview sample=NoticePreview.sample(c);
            Notification.Builder builder=sample.builder(c,start,true).setOnlyAlertOnce(false);
            android.os.Bundle token=new android.os.Bundle();token.putLong("syuct.preview.target",start);builder.addExtras(token);
            c.getSystemService(NotificationManager.class).notify(PREVIEW,153,build(c,builder,sample.name,PREVIEW,153,start,now));
            return new PreviewResult(start,"已发送预览请求，正在确认系统是否接收…");
        }catch(RuntimeException e){return new PreviewResult(0,"预览发送失败，请检查通知权限与声音设置。");}
    }
    // Bounded check on a worker thread, not a polling service. An active record
    // proves acceptance only, never that an OEM island actually appeared.
    static String confirmPreview(Context c,PreviewResult result){
        if(result.target==0)return result.message;
        try{
            for(int attempt=0;attempt<8;attempt++){
                if(!enabled(c))return "实时倒计时已关闭，未继续预览。";
                for(StatusBarNotification item:c.getSystemService(NotificationManager.class).getActiveNotifications()){
                    Notification n=item.getNotification();
                    if(item.getId()==153&&PREVIEW.equals(item.getTag())&&n.extras.getLong("syuct.preview.target")==result.target)
                        return "系统已接收15分钟预览，请下拉通知栏查看。"+
                            ((n.flags&Notification.FLAG_ONGOING_EVENT)!=0?"已请求实时显示，是否上岛由手机系统决定。":"当前为普通通知，系统未允许实时提升显示。");
                }
                if(attempt<7)Thread.sleep(150);
            }
            return "暂未确认系统接收到本次预览，请下拉通知栏核对；若没有通知，请检查通知权限。";
        }catch(InterruptedException e){Thread.currentThread().interrupt();return "预览确认已中断，可重新尝试。";}
        catch(RuntimeException e){return "预览请求已发送，但无法读取系统接收结果，请下拉通知栏核对。";}
    }
    @Override public void onReceive(Context c,Intent intent){
        if(!END.equals(intent.getAction()))return;
        String key=intent.getStringExtra("key");int id=intent.getIntExtra("id",0);
        if(key==null||key.length()>2000||!(id==151||id==153&&PREVIEW.equals(key)))return;
        // CourseReminder's durable sent journal already prevents re-posting after dismissal.
        VivoIsland.cancel(c,key,id);
    }
}
