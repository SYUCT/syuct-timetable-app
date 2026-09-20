package top.syuct.timetable;

import android.app.*;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import java.util.Locale;

/** Best-effort OriginOS payload; ignored where unsupported. No hidden API/scene registration. */
final class VivoIsland {
    private static final String PREFIX="notification.superx.";
    static boolean device(String brand,String manufacturer){
        String b=brand==null?"":brand.toLowerCase(Locale.ROOT);
        String m=manufacturer==null?"":manufacturer.toLowerCase(Locale.ROOT);
        return b.equals("vivo")||b.equals("iqoo")||m.equals("vivo");
    }
    static boolean device(){return device(Build.BRAND,Build.MANUFACTURER);}
    static boolean attempt(Context c){return Build.VERSION.SDK_INT>=36&&device()&&LiveCourseNotice.enabled(c);}
    static Bundle extras(Context c,String name,long start,PendingIntent open){
        Icon icon=CourseNoticeStyle.badgeIcon(c);
        String shortName=CourseNoticeStyle.compactTitle(name),time=CourseNoticeStyle.time(start);
        Bundle out=new Bundle();
        out.putInt(PREFIX+"operation",0);out.putBoolean(PREFIX+"showNotify",true);
        out.putInt(PREFIX+"template",1);
        // The Chinese integration guide spells its calendar/meeting scene METTING.
        // Never impersonate a train/navigation/health event to force promotion.
        out.putString(PREFIX+"scene","METTING");
        if(open!=null)out.putParcelable(PREFIX+"clickResp",open);
        Bundle base=new Bundle();
        base.putParcelable(PREFIX+"baseInfos.icon",icon);
        base.putCharSequence(PREFIX+"baseInfos.title",name==null?"即将上课":name);
        base.putCharSequence(PREFIX+"baseInfos.content",time+" 开课");
        out.putBundle(PREFIX+"baseInfos",base);
        Bundle capsule=new Bundle();capsule.putInt(PREFIX+"capsule.state",1);
        capsule.putCharSequence(PREFIX+"capsule.content",shortName);
        capsule.putParcelable(PREFIX+"capsule.icon",icon);out.putBundle(PREFIX+"capsule",capsule);
        Bundle info=new Bundle();info.putString(PREFIX+"infos.describe",name);
        info.putString(PREFIX+"infos.coreInfo",time+" 开课");info.putParcelable(PREFIX+"infos.image",icon);
        out.putBundle(PREFIX+"infos",info);
        Bundle small=new Bundle();small.putString(PREFIX+"shortInfos.describeShort",name);
        small.putString(PREFIX+"shortInfos.coreInfoShort",time+" 开课");small.putParcelable(PREFIX+"shortInfos.image",icon);
        out.putBundle(PREFIX+"shortInfos",small);
        Bundle island=new Bundle(),left=new Bundle(),right=new Bundle();
        island.putInt("island.superx.leftTemplate",1);island.putInt("island.superx.rightTemplate",4);
        left.putString("island.superx.leftInfo.content",shortName);left.putParcelable("island.superx.leftInfo.icon",icon);
        right.putString("island.superx.rightInfo.content",time);right.putParcelable("island.superx.rightInfo.icon",icon);
        island.putBundle("island.superx.leftInfo",left);island.putBundle("island.superx.rightInfo",right);
        out.putBundle(PREFIX+"island",island);
        return out;
    }
    static boolean hasPayload(Notification n){return n.extras.containsKey(PREFIX+"operation");}
    @android.annotation.SuppressLint("MissingPermission")
    static void cancel(Context c,String tag,int id){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        // Send the end hint only for a live, existing payload; do not resurrect a
        // dismissed notification and do not let an OEM error prevent normal cancel.
        try{
            if(device())for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications()){
                if(n.getId()!=id||!java.util.Objects.equals(tag,n.getTag())||!hasPayload(n.getNotification()))continue;
                Bundle end=new Bundle();end.putInt(PREFIX+"operation",2);
                manager.notify(tag,id,Notification.Builder.recoverBuilder(c,n.getNotification())
                    .addExtras(end).setOnlyAlertOnce(true).setOngoing(false).setTimeoutAfter(1).build());
            }
        }catch(RuntimeException ignored){}finally{manager.cancel(tag,id);}
    }
}
