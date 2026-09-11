package top.syuct.timetable;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;

final class WidgetState {
    static final class Course {
        String name,room;int start,end;boolean active;
    }
    final List<Course> today=new ArrayList<>();
    String empty="今天没有课程",heading;
    String[][] times;
    ZonedDateTime now=ZonedDateTime.now(LessonClock.ZONE);
    static String defaults(Context context) {
        try(InputStream in=context.getAssets().open("section-times.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");
        }catch(Exception e){return "[]";}
    }
    static String[][] readTimes(JSONArray a)throws JSONException{
        if(a==null||a.length()!=12)throw new JSONException("节次表无效");
        String[][] times=new String[12][2];
        for(int i=0;i<12;i++){JSONArray pair=a.getJSONArray(i);if(pair.length()!=2)throw new JSONException("节次表无效");times[i][0]=pair.getString(0);times[i][1]=pair.getString(1);}
        LessonClock.validateTimes(times);return times;
    }
    static WidgetState load(Context context) {
        WidgetState s=new WidgetState();
        try{
            s.times=readTimes(new JSONArray(defaults(context)));
            String raw=context.getSharedPreferences("timetable",Context.MODE_PRIVATE).getString("state","");
            if(raw.isEmpty()){s.empty="请先在 App 中导入课表";return s;}
            JSONObject state=new JSONObject(raw),settings=state.getJSONObject("settings");
            if(settings.has("periodTimes"))s.times=readTimes(settings.getJSONArray("periodTimes"));
            String first=settings.optString("firstWeekDate");int week=LessonClock.week(first,s.now);
            if(first.isEmpty()){s.empty="请先设置第一周的周一";return s;}
            if(week<1||week>settings.getInt("totalWeeks")){s.empty="当前日期不在本学期内";return s;}
            s.heading="第 "+week+" 周";
            JSONArray courses=state.getJSONArray("courses");
            for(int i=0;i<courses.length();i++){
                JSONObject c=courses.getJSONObject(i);
                if(c.getInt("weekday")!=s.now.getDayOfWeek().getValue()||!LessonClock.matches(week,c.getInt("startWeek"),c.getInt("endWeek"),c.getString("weekType")))continue;
                Course entry=new Course();entry.name=c.getString("name");entry.room=c.optString("room","");entry.start=c.getInt("startSection");entry.end=c.getInt("endSection");
                entry.active=LessonClock.active(entry.start,entry.end,s.times,s.now);s.today.add(entry);
            }
            s.today.sort(Comparator.comparingInt(c->c.start));
        }catch(Exception e){s.today.clear();s.empty="课表数据异常，请打开 App 核对";}
        return s;
    }
}
