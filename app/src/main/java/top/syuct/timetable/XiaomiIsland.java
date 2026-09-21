package top.syuct.timetable;

import android.app.*;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.util.Locale;

/** HyperOS 3 official image/text-left + text-right template. No media impersonation. */
final class XiaomiIsland {
    static final String PARAM="miui.focus.param", BADGE="miui.focus.pic_syuct_badge", END="miui.focus.action_end";
    static final int UNKNOWN=-1, DENIED=0, GRANTED=1;
    private static final java.util.concurrent.atomic.AtomicBoolean querying=new java.util.concurrent.atomic.AtomicBoolean();
    static boolean device(String brand,String maker){
        String b=brand==null?"":brand.toLowerCase(Locale.ROOT),m=maker==null?"":maker.toLowerCase(Locale.ROOT);
        return b.equals("xiaomi")||b.equals("redmi")||b.equals("poco")||m.equals("xiaomi");
    }
    static boolean device(){return device(Build.BRAND,Build.MANUFACTURER);}
    static int protocol(Context c){
        try{return Settings.System.getInt(c.getContentResolver(),"notification_focus_protocol",0);}
        catch(RuntimeException e){return 0;}
    }
    static int permission(Bundle result){
        if(result==null||!(result.get("canShowFocus") instanceof Boolean))return UNKNOWN;
        return result.getBoolean("canShowFocus")?GRANTED:DENIED;
    }
    static int permission(Context c){
        android.content.SharedPreferences p=CourseReminder.prefs(c);
        long age=System.currentTimeMillis()-p.getLong("xiaomiFocusCheckedAt",0);
        if(age<0||age>86400000L||!Build.FINGERPRINT.equals(p.getString("xiaomiFocusBuild","")))return UNKNOWN;
        return p.getInt("xiaomiFocusPermission",UNKNOWN);
    }
    // Xiaomi documents this provider call as slow. Never call it from notification
    // delivery/the UI thread, and never delay a class reminder waiting for it.
    static void refreshPermission(Context c,Runnable done){
        if(!device()||!querying.compareAndSet(false,true))return;
        Context app=c.getApplicationContext();
        new Thread(()->{
            int value=UNKNOWN;
            try{
                Bundle args=new Bundle();args.putString("package",app.getPackageName());
                value=permission(app.getContentResolver().call(Uri.parse("content://miui.statusbar.notification.public"),"canShowFocus",null,args));
            }catch(RuntimeException ignored){}
            finally{
                CourseReminder.prefs(app).edit().putInt("xiaomiFocusPermission",value)
                    .putLong("xiaomiFocusCheckedAt",System.currentTimeMillis()).putString("xiaomiFocusBuild",Build.FINGERPRINT).apply();
                querying.set(false);
                if(done!=null)new Handler(Looper.getMainLooper()).post(done);
            }
        },"xiaomi-focus-check").start();
    }
    static boolean nativeAllowed(int protocol,int permission){return protocol>=3&&permission==GRANTED;}
    static String status(int protocol,int permission){
        if(protocol<3)return "未检测到小米超级岛专用接口。使用通用通知，课程简称与开课时间在同一短文本中，可能被系统截短。";
        if(permission==DENIED)return "系统返回：未允许本应用的焦点通知。当前使用通用通知，不能保证左右分区和彩色校徽；可能需要系统授权或小米接入资格。";
        if(permission==UNKNOWN)return "暂未取得焦点通知权限结果，先使用通用通知；不会把发送成功当作左右模板已生效。";
        return "系统已允许焦点通知，将使用左侧校徽与课程、右侧时间的专用模板。实际呈现仍由系统决定。";
    }
    static boolean attempt(Context c){
        if(Build.VERSION.SDK_INT<36||!device()||!LiveCourseNotice.enabled(c))return false;
        return nativeAllowed(protocol(c),permission(c));
    }
    static JSONObject params(String name,String title,String detail,long start,long now) throws JSONException {
        long remaining=Math.max(1,start-now);
        JSONObject picture=new JSONObject().put("type",1).put("pic",BADGE);
        JSONObject left=new JSONObject().put("type",1).put("picInfo",picture)
            .put("textInfo",new JSONObject().put("title",CourseNoticeStyle.compactTitle(name)).put("showHighlightColor",false));
        JSONObject right=new JSONObject().put("title",CourseNoticeStyle.time(start))
            .put("narrowFont",true).put("showHighlightColor",false);
        JSONObject island=new JSONObject().put("islandProperty",1).put("islandOrder",false)
            .put("islandTimeout",Math.min(Integer.MAX_VALUE,(remaining+999)/1000))
            .put("bigIslandArea",new JSONObject().put("imageTextInfoLeft",left).put("textInfo",right))
            .put("smallIslandArea",new JSONObject().put("picInfo",picture));
        JSONObject base=new JSONObject().put("type",2).put("title",title).put("content",detail)
            .put("colorTitle","#3463C9").put("colorTitleDark","#99BDFF");
        JSONObject v2=new JSONObject().put("protocol",1).put("business","syuct_class_reminder")
            .put("islandFirstFloat",false).put("enableFloat",false).put("updatable",true).put("reopen","close")
            .put("filterWhenNoPermission",false).put("timeout",Math.min(Integer.MAX_VALUE,(remaining+59999)/60000))
            .put("param_island",island).put("baseInfo",base)
            .put("picInfo",new JSONObject().put("type",1).put("pic",BADGE).put("picDark",BADGE))
            .put("actions",new JSONArray().put(new JSONObject().put("action",END)));
        return new JSONObject().put("param_v2",v2);
    }
    static Bundle extras(Context c,String name,Notification ordinary,long start,long now,Notification.Action end) throws JSONException {
        CharSequence title=ordinary.extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
        if(title==null)title=ordinary.extras.getCharSequence(Notification.EXTRA_TITLE,name);
        CharSequence detail=ordinary.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(detail==null)detail=ordinary.extras.getCharSequence(Notification.EXTRA_TEXT,CourseNoticeStyle.time(start)+" 开课");
        Bundle out=new Bundle(),pics=new Bundle(),actions=new Bundle();
        pics.putParcelable(BADGE,CourseNoticeStyle.badgeIcon(c));actions.putParcelable(END,end);
        out.putBundle("miui.focus.pics",pics);out.putBundle("miui.focus.actions",actions);
        out.putString(PARAM,params(name,title==null?"即将上课":title.toString(),detail==null?"":detail.toString(),start,now).toString());
        return out;
    }
    static boolean hasPayload(Notification n){return n.extras.containsKey(PARAM);}
}
