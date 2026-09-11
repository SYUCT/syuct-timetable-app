package top.syuct.timetable;

import android.content.*;
import android.view.View;
import android.widget.*;

public class TodayWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent){return new Factory(getApplicationContext());}
    static final class Factory implements RemoteViewsFactory {
        final Context context;WidgetState state;
        Factory(Context c){context=c;}
        @Override public void onCreate(){state=WidgetState.load(context);}
        @Override public void onDataSetChanged(){state=WidgetState.load(context);}
        @Override public void onDestroy(){state=null;}
        @Override public int getCount(){return state==null?0:state.today.size();}
        @Override public RemoteViews getViewAt(int position){
            if(state==null||position<0||position>=state.today.size())return null;
            WidgetState.Course c=state.today.get(position);RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.widget_course);
            v.setTextViewText(R.id.widget_course_name,c.name);v.setTextViewText(R.id.widget_course_room,c.room.isEmpty()?"地点待定":c.room);
            String start=c.start>=1&&c.start<=state.times.length?state.times[c.start-1][0]:"",end=c.end>=1&&c.end<=state.times.length?state.times[c.end-1][1]:"";
            v.setTextViewText(R.id.widget_course_time,c.start+"–"+c.end+"节"+(start.isEmpty()||end.isEmpty()?"":"\n"+start+"\n"+end));
            v.setViewVisibility(R.id.widget_course_current,c.active?View.VISIBLE:View.GONE);
            v.setInt(R.id.widget_course_row,"setBackgroundResource",c.active?R.drawable.widget_course_active:R.drawable.widget_course_background);
            v.setOnClickFillInIntent(R.id.widget_course_row,new Intent());return v;
        }
        @Override public RemoteViews getLoadingView(){return null;}
        @Override public int getViewTypeCount(){return 1;}
        @Override public long getItemId(int position){return position;}
        @Override public boolean hasStableIds(){return false;}
    }
}
