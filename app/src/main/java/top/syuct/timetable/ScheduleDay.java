package top.syuct.timetable;

import java.time.*;
import java.util.*;

/** Date-specific timetable overrides. The recurring course is never mutated. */
public final class ScheduleDay {
    // 国办发明电〔2025〕7号。2027 年尚无已公布的年度安排，不猜测日期。
    private static final String[][] PUBLIC_REST_2026={
        {"2026-01-01","2026-01-03"},{"2026-02-15","2026-02-23"},
        {"2026-04-04","2026-04-06"},{"2026-05-01","2026-05-05"},
        {"2026-06-19","2026-06-21"},{"2026-09-25","2026-09-27"},
        {"2026-10-01","2026-10-07"}
    };
    public static boolean isPublicRestDay(LocalDate day){
        String date=day.toString();
        for(String[] range:PUBLIC_REST_2026)if(date.compareTo(range[0])>=0&&date.compareTo(range[1])<=0)return true;
        return false;
    }
    public static final class Adjustment {
        public final LocalDate source,target;
        public final boolean suspendSource;
        public Adjustment(LocalDate source,LocalDate target,boolean suspendSource){
            this.source=source;this.target=target;this.suspendSource=suspendSource;
        }
    }
    public static final class Resolved {
        public final LocalDate source;
        public final boolean suspended,adjusted,holiday;
        Resolved(LocalDate source,boolean suspended,boolean adjusted,boolean holiday){
            this.source=source;this.suspended=suspended;this.adjusted=adjusted;this.holiday=holiday;
        }
    }
    public static Resolved resolve(List<Adjustment> adjustments,LocalDate day){
        for(Adjustment a:adjustments)if(a.target.equals(day))return new Resolved(a.source,false,true,false);
        if(isPublicRestDay(day))return new Resolved(day,true,false,true);
        for(Adjustment a:adjustments)if(a.source.equals(day)&&a.suspendSource)return new Resolved(day,true,false,false);
        return new Resolved(day,false,false,false);
    }
    private ScheduleDay(){}
}
