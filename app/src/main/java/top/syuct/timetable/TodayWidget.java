package top.syuct.timetable;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.RemoteViews;
import java.time.format.DateTimeFormatter;

public class TodayWidget extends AppWidgetProvider {
    public static final String OPEN="top.syuct.timetable.OPEN_OVERVIEW", REFRESH="top.syuct.timetable.WIDGET_REFRESH";
    private static PendingIntent alarmIntent(Context c){return PendingIntent.getBroadcast(c,50,new Intent(c,TodayWidget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void refreshAll(Context context){
        Context c=context.getApplicationContext();AppWidgetManager manager=AppWidgetManager.getInstance(c);
        int[] ids=manager.getAppWidgetIds(new ComponentName(c,TodayWidget.class));
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(ids.length==0){alarm.cancel(alarmIntent(c));return;}
        WidgetState state=WidgetState.load(c);
        for(int id:ids){
            RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_today);
            String day=new String[]{"一","二","三","四","五","六","日"}[state.now.getDayOfWeek().getValue()-1];
            v.setTextViewText(R.id.widget_title,"今日课表 · 周"+day);
            v.setTextViewText(R.id.widget_date,state.now.format(DateTimeFormatter.ofPattern("M月d日"))+(state.heading==null?"":" · "+state.heading));
            v.setTextViewText(R.id.widget_empty,state.empty);
            Intent adapter=new Intent(c,TodayWidgetService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
            adapter.setData(Uri.parse("syuct-widget://today/"+id));v.setRemoteAdapter(R.id.widget_list,adapter);v.setEmptyView(R.id.widget_list,R.id.widget_empty);
            Intent open=new Intent(c,MainActivity.class).setAction(OPEN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent fixed=PendingIntent.getActivity(c,70,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_header,fixed);v.setOnClickPendingIntent(R.id.widget_empty,fixed);
            // Collection rows need a mutable template, always restricted to our explicit Activity.
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            v.setPendingIntentTemplate(R.id.widget_list,PendingIntent.getActivity(c,71,open,flags));
            manager.updateAppWidget(id,v);
        }
        manager.notifyAppWidgetViewDataChanged(ids,R.id.widget_list);
        // Non-wakeup: refresh at section boundaries when the screen is awake.
        if(state.times!=null){
            long next=LessonClock.nextBoundary(state.times,state.now);
            try{if(CourseReminder.precise(c)){alarm.setExact(AlarmManager.RTC,next,alarmIntent(c));return;}}catch(SecurityException ignored){}
            alarm.set(AlarmManager.RTC,next,alarmIntent(c));
        }
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){refreshAll(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){refreshAll(c);}
    @Override public void onDisabled(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarmIntent(c));}
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);String action=intent.getAction();
        if(REFRESH.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))refreshAll(c);
    }
}
