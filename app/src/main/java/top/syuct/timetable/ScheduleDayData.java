package top.syuct.timetable;

import org.json.*;
import java.time.*;
import java.util.*;

/** Android JSON adapter; scheduling rules in ScheduleDay stay independent of Android. */
final class ScheduleDayData {
    static List<ScheduleDay.Adjustment> read(JSONObject state,LocalDate first,int total)throws JSONException{
        JSONArray raw=state.optJSONArray("adjustments");
        if(raw==null){if(state.has("adjustments"))throw new JSONException("调课记录无效");return Collections.emptyList();}
        if(raw.length()>50)throw new JSONException("调课记录过多");
        if(raw.length()>0&&(first==null||first.getDayOfWeek()!=DayOfWeek.MONDAY))throw new JSONException("调课缺少第一周周一");
        LocalDate end=first==null?null:first.plusWeeks(total);
        Set<LocalDate> used=new HashSet<>();List<ScheduleDay.Adjustment> out=new ArrayList<>();
        for(int i=0;i<raw.length();i++){
            JSONObject item=raw.getJSONObject(i);
            try{
                LocalDate source=LocalDate.parse(item.getString("sourceDate")),target=LocalDate.parse(item.getString("targetDate"));
                if(source.equals(target)||source.isBefore(first)||!source.isBefore(end)||target.isBefore(first)||!target.isBefore(end))throw new JSONException("调课日期超出本学期");
                if(!used.add(source)||!used.add(target))throw new JSONException("调课日期重复");
                if(!item.has("suspendSource")||!(item.get("suspendSource") instanceof Boolean))throw new JSONException("原日期停课设置无效");
                out.add(new ScheduleDay.Adjustment(source,target,item.getBoolean("suspendSource")));
            }catch(DateTimeException e){throw new JSONException("调课日期无效");}
        }
        return out;
    }
    static boolean matches(JSONObject course,int week)throws JSONException{
        String type=course.getString("weekType");
        if(!"custom".equals(type))return LessonClock.matches(week,course.getInt("startWeek"),course.getInt("endWeek"),type);
        JSONArray weeks=course.getJSONArray("weeks");
        for(int i=0;i<weeks.length();i++)if(weeks.getInt(i)==week)return true;
        return false;
    }
    private ScheduleDayData(){}
}
