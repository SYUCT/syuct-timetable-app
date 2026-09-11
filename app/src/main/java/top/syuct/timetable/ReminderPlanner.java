package top.syuct.timetable;

import java.time.*;
import java.util.*;

/** Pure scheduling logic: one reminder per course occurrence, not per section. */
public final class ReminderPlanner {
    public static final long LEAD=15*60_000L;
    public static final class Course {
        public String name,room,type;public int day,start,first,last;
        public Course(String n,String r,String t,int d,int s,int f,int l){name=n;room=r;type=t;day=d;start=s;first=f;last=l;}
    }
    public static final class Event {
        public final Course course;public final long start,remind;public final String key;
        Event(Course c,long at){course=c;start=at;remind=at-LEAD;key=at+"|"+c.name.length()+":"+c.name+"|"+c.room;}
    }
    public static List<Event> events(String firstMonday,int total,String[][] times,List<Course> courses){
        List<Event> out=new ArrayList<>();LocalDate first;
        try{first=LocalDate.parse(firstMonday);LessonClock.validateTimes(times);}catch(Exception e){return out;}
        if(first.getDayOfWeek()!=DayOfWeek.MONDAY||total<1||total>30)return out;
        Set<String> seen=new HashSet<>();
        for(Course c:courses){
            if(c.day<1||c.day>7||c.start<1||c.start>12||times[c.start-1][0].isEmpty())continue;
            for(int week=1;week<=total;week++)if(LessonClock.matches(week,c.first,c.last,c.type)){
                long start=first.plusDays((week-1)*7L+c.day-1).atTime(LocalTime.parse(times[c.start-1][0])).atZone(LessonClock.ZONE).toInstant().toEpochMilli();
                Event event=new Event(c,start);if(seen.add(event.key))out.add(event);
            }
        }
        out.sort(Comparator.comparingLong(e->e.remind));return out;
    }
    public static long next(List<Event> events,long now){
        return events.stream().filter(e->e.remind>now).mapToLong(e->e.remind).min().orElse(0);
    }
    public static List<Event> due(List<Event> events,long scheduled,long now,Set<String> sent){
        List<Event> out=new ArrayList<>();
        for(Event e:events)if(e.remind==scheduled&&now>=e.remind&&now<e.start&&!sent.contains(e.key))out.add(e);
        return out;
    }
}
