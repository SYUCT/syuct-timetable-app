package top.syuct.timetable;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import java.util.*;

/** The launcher owns placement. A successful request alone never means a widget was added. */
public final class WidgetPinRequest extends BroadcastReceiver {
    static final String ADDED="top.syuct.timetable.WIDGET_PIN_ADDED";
    static final String HELP="请到有空位的桌面页，长按空白处 → 小组件 → 化大课表，预留约4×3格。是否新建下一页由手机桌面决定。";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("widget_pin",Context.MODE_PRIVATE);}
    private static Set<String> ids(Context c){
        Set<String> result=new HashSet<>();
        for(int id:AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,TodayWidget.class)))result.add(String.valueOf(id));
        return result;
    }
    static void request(Activity activity){
        SharedPreferences p=prefs(activity);long now=System.currentTimeMillis();
        if("pending".equals(p.getString("state",""))&&now-p.getLong("at",0)>=0&&now-p.getLong("at",0)<10000)return;
        try{
            AppWidgetManager manager=AppWidgetManager.getInstance(activity);
            if(!manager.isRequestPinAppWidgetSupported()){p.edit().putString("state","unsupported").apply();return;}
            String token=UUID.randomUUID().toString();
            p.edit().putString("state","pending").putString("token",token).putLong("at",now).putStringSet("before",ids(activity)).commit();
            Intent intent=new Intent(activity,WidgetPinRequest.class).setAction(ADDED).setData(Uri.parse("syuct-widget-pin://result/"+token)).putExtra("token",token);
            PendingIntent callback=PendingIntent.getBroadcast(activity,190,intent,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE);
            if(!manager.requestPinAppWidget(new ComponentName(activity,TodayWidget.class),null,callback)){
                callback.cancel();p.edit().putString("state","unsupported").apply();
            }
        }catch(RuntimeException e){p.edit().putString("state","failed").apply();}
    }
    static String status(Context c){
        SharedPreferences p=prefs(c);String state=p.getString("state","");
        if(state.isEmpty())return "";
        if("pending".equals(state)){
            // Some launchers omit the success callback: confirm by new widget IDs instead.
            Set<String> current=ids(c);current.removeAll(p.getStringSet("before",Collections.emptySet()));
            if(!current.isEmpty()){p.edit().putString("state","added").apply();state="added";}
        }
        if("added".equals(state))return "已添加，请返回桌面查看。";
        if("pending".equals(state)){
            long elapsed=System.currentTimeMillis()-p.getLong("at",0);
            return elapsed>=0&&elapsed<10000?"已向桌面发送请求，请在系统提示中确认；未出现提示时可手动添加。": "尚未确认添加成功。"+HELP;
        }
        return ("unsupported".equals(state)?"当前桌面未允许直接添加。":"添加请求未完成。")+HELP;
    }
    @Override public void onReceive(Context c,Intent intent){
        if(!ADDED.equals(intent.getAction()))return;
        SharedPreferences p=prefs(c);String token=intent.getStringExtra("token");
        if(token==null||!token.equals(p.getString("token","")))return;
        p.edit().putString("state","added").apply();
        Toast.makeText(c,"今日课表小组件已添加",Toast.LENGTH_SHORT).show();
    }
}
