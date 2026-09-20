package top.syuct.timetable;

import android.app.*;
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.util.Locale;

/** HyperOS 3 official image/text-left + text-right template. No media impersonation. */
final class XiaomiIsland {
    static final String PARAM="miui.focus.param", BADGE="miui.focus.pic_syuct_badge", END="miui.focus.action_end";
    static boolean device(String brand,String maker){
        String b=brand==null?"":brand.toLowerCase(Locale.ROOT),m=maker==null?"":maker.toLowerCase(Locale.ROOT);
        return b.equals("xiaomi")||b.equals("redmi")||b.equals("poco")||m.equals("xiaomi");
    }
    static boolean device(){return device(Build.BRAND,Build.MANUFACTURER);}
    static boolean attempt(Context c){
        if(Build.VERSION.SDK_INT<36||!device()||!LiveCourseNotice.enabled(c))return false;
        try{return Settings.System.getInt(c.getContentResolver(),"notification_focus_protocol",0)>=3;}
        catch(RuntimeException e){return false;}
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
