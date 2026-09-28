package top.syuct.timetable;

import org.json.*;
import java.time.*;
import java.util.*;

/** Date-specific timetable overrides. The recurring course is never mutated. */
public final class ScheduleDay {
    public static final class Adjustment {
        public final LocalDate source,target;
        public final boolean suspendSource;
        public Adjustment(LocalDate source,LocalDate target,boolean suspendSource){
            this.source=source;this.target=target;this.suspendSource=suspendSource;
        }
    }
    public static final class Resolved {
        public final LocalDate source;
        public final boolean suspended,adjusted;
        Resolved(LocalDate source,boolean suspended,boolean adjusted){
            this.source=source;this.suspended=suspended;this.adjusted=adjusted;
        }
    }
    public static List<Adjustment> read(JSONObject state,LocalDate first,int total)throws JSONException{
        JSONArray raw=state.optJSONArray("adjustments");
        if(raw==null){if(state.has("adjustments"))throw new JSONException("调课记录无效");return Collections.emptyList();}
        if(raw.length()>50)throw new JSONException("调课记录过多");
        if(raw.length()>0&&(first==null||first.getDayOfWeek()!=DayOfWeek.MONDAY))throw new JSONException("调课缺少第一周周一");
        LocalDate end=first==null?null:first.plusWeeks(total);
        Set<LocalDate> used=new HashSet<>();List<Adjustment> out=new ArrayList<>();
        for(int i=0;i<raw.length();i++){
            JSONObject item=raw.getJSONObject(i);
            try{
                LocalDate source=LocalDate.parse(item.getString("sourceDate")),target=LocalDate.parse(item.getString("targetDate"));
                if(source.equals(target)||source.isBefore(first)||!source.isBefore(end)||target.isBefore(first)||!target.isBefore(end))throw new JSONException("调课日期超出本学期");
                if(!used.add(source)||!used.add(target))throw new JSONException("调课日期重复");
                if(!item.has("suspendSource")||!(item.get("suspendSource") instanceof Boolean))throw new JSONException("原日期停课设置无效");
                out.add(new Adjustment(source,target,item.getBoolean("suspendSource")));
            }catch(DateTimeException e){throw new JSONException("调课日期无效");}
        }
        return out;
    }
    public static Resolved resolve(List<Adjustment> adjustments,LocalDate day){
        for(Adjustment a:adjustments)if(a.target.equals(day))return new Resolved(a.source,false,true);
        for(Adjustment a:adjustments)if(a.source.equals(day)&&a.suspendSource)return new Resolved(day,true,false);
        return new Resolved(day,false,false);
    }
    public static boolean matches(JSONObject course,int week)throws JSONException{
        String type=course.getString("weekType");
        if(!"custom".equals(type))return LessonClock.matches(week,course.getInt("startWeek"),course.getInt("endWeek"),type);
        JSONArray weeks=course.getJSONArray("weeks");
        for(int i=0;i<weeks.length();i++)if(weeks.getInt(i)==week)return true;
        return false;
    }
    private ScheduleDay(){}
}
