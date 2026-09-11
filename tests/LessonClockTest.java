import top.syuct.timetable.LessonClock;
import java.time.*;
public class LessonClockTest {
 static int checks=0;
 static void check(boolean v){checks++;if(!v)throw new AssertionError("clock check "+checks);}
 static ZonedDateTime at(String t){return ZonedDateTime.parse("2026-08-31T"+t+":00+08:00");}
 public static void main(String[] args){
  String[][] times={{"08:00","08:50"},{"09:00","09:50"},{"10:10","11:00"},{"11:10","12:00"},{"13:30","14:20"},{"14:30","15:20"},{"15:40","16:30"},{"16:40","17:30"},{"18:30","19:20"},{"19:30","20:20"},{"",""},{"",""}};
  LessonClock.validateTimes(times);
  check(!LessonClock.active(1,2,times,at("07:59")));check(LessonClock.active(1,2,times,at("08:00")));check(!LessonClock.active(1,2,times,at("08:50")));check(!LessonClock.active(1,2,times,at("08:59")));check(LessonClock.active(1,2,times,at("09:00")));check(!LessonClock.active(1,2,times,at("09:50")));
  check(LessonClock.week("2026-08-31",at("08:00"))==1);check(LessonClock.week("2026-08-31",at("08:00").plusDays(7))==2);check(LessonClock.week("",at("08:00"))==0);check(LessonClock.week("2026-09-01",at("08:00"))==0);
  check(LessonClock.matches(1,1,20,"odd"));check(!LessonClock.matches(2,1,20,"odd"));check(LessonClock.matches(2,1,20,"even"));check(!LessonClock.matches(21,1,20,"all"));
  check(!LessonClock.active(11,12,times,at("20:35")));times[10]=new String[]{"20:30","21:20"};LessonClock.validateTimes(times);check(LessonClock.active(11,12,times,at("20:35")));
  check(LessonClock.nextBoundary(times,at("08:00"))==at("08:50").toInstant().toEpochMilli()+1000);
  check(LessonClock.nextBoundary(times,at("23:59"))==at("00:00").plusDays(1).toInstant().toEpochMilli()+1000);
  check(LessonClock.week("2026-08-31",ZonedDateTime.parse("2026-08-30T16:30:00Z"))==1);
  boolean rejected=false;times[0]=new String[]{"08:50","08:00"};try{LessonClock.validateTimes(times);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  System.out.println("PASS "+checks+" native clock checks");
 }
}
