package top.syuct.timetable;

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
    public static Resolved resolve(List<Adjustment> adjustments,LocalDate day){
        for(Adjustment a:adjustments)if(a.target.equals(day))return new Resolved(a.source,false,true);
        for(Adjustment a:adjustments)if(a.source.equals(day)&&a.suspendSource)return new Resolved(day,true,false);
        return new Resolved(day,false,false);
    }
    private ScheduleDay(){}
}
