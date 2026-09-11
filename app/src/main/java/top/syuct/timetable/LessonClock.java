package top.syuct.timetable;

import java.time.*;
import java.time.temporal.ChronoUnit;

/** Same Beijing-time, half-open period intervals as app-core.js. No invented periods. */
public final class LessonClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static int week(String firstMonday, ZonedDateTime now) {
        try {
            LocalDate start=LocalDate.parse(firstMonday);
            if(start.getDayOfWeek()!=DayOfWeek.MONDAY)return 0;
            return Math.toIntExact(Math.floorDiv(ChronoUnit.DAYS.between(start,now.withZoneSameInstant(ZONE).toLocalDate()),7)+1);
        } catch(Exception e) { return 0; }
    }
    public static boolean matches(int week,int first,int last,String type) {
        return week>=first&&week<=last&&("all".equals(type)||"odd".equals(type)&&week%2==1||"even".equals(type)&&week%2==0);
    }
    public static int minutes(String time) {
        if(time==null||!time.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new IllegalArgumentException("时间格式无效");
        LocalTime t=LocalTime.parse(time);return t.getHour()*60+t.getMinute();
    }
    public static void validateTimes(String[][] times) {
        if(times==null||times.length!=12)throw new IllegalArgumentException("需要12节时间");
        int previous=-1;
        for(String[] t:times) {
            if(t==null||t.length!=2)throw new IllegalArgumentException("时间不完整");
            if("".equals(t[0])&&"".equals(t[1]))continue;
            int a=minutes(t[0]),b=minutes(t[1]);
            if(a>=b||a<previous)throw new IllegalArgumentException("时间重叠或顺序无效");previous=b;
        }
    }
    public static boolean active(int startSection,int endSection,String[][] times,ZonedDateTime now) {
        int minute=now.withZoneSameInstant(ZONE).getHour()*60+now.withZoneSameInstant(ZONE).getMinute();
        for(int section=startSection;section<=endSection;section++){
            if(section<1||section>times.length)continue;
            String[] pair=times[section-1];
            if(!pair[0].isEmpty()&&!pair[1].isEmpty()&&minute>=minutes(pair[0])&&minute<minutes(pair[1]))return true;
        }return false;
    }
    public static long nextBoundary(String[][] times,ZonedDateTime rawNow) {
        ZonedDateTime now=rawNow.withZoneSameInstant(ZONE);
        ZonedDateTime next=now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
        for(String[] pair:times)for(String t:pair)if(t!=null&&!t.isEmpty()){
            ZonedDateTime candidate=now.toLocalDate().atTime(LocalTime.parse(t)).atZone(ZONE);
            if(candidate.isAfter(now)&&candidate.isBefore(next))next=candidate;
        }
        return next.toInstant().toEpochMilli()+1000;
    }
}
