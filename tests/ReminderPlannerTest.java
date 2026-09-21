import top.syuct.timetable.ReminderPlanner;
import java.time.*;
import java.util.*;
public class ReminderPlannerTest {
 static int checks;
 static void check(boolean ok){if(!ok)throw new AssertionError("reminder check "+(checks+1));checks++;}
 static long at(String date){return OffsetDateTime.parse(date+"+08:00").toInstant().toEpochMilli();}
 static ReminderPlanner.Course course(String name,int day,int section,String type){return new ReminderPlanner.Course(name,"测试楼101",type,day,section,1,20);}
 public static void main(String[] args){
  String[][] times={{"08:00","08:50"},{"09:00","09:50"},{"10:10","11:00"},{"11:10","12:00"},{"13:30","14:20"},{"14:30","15:20"},{"15:40","16:30"},{"16:40","17:30"},{"18:30","19:20"},{"19:30","20:20"},{"",""},{"",""}};
  var c=course("课程甲",1,1,"all");var events=ReminderPlanner.events("2026-08-31",20,times,List.of(c));
  check(events.size()==20);check(events.get(0).start==at("2026-08-31T08:00:00"));check(events.get(0).remind==at("2026-08-31T07:45:00"));
  check(c.teacher.equals(""));
  var withTeacher=new ReminderPlanner.Course(c.name,c.room,"张老师",c.type,c.day,c.start,c.first,c.last);
  var teacherEvents=ReminderPlanner.events("2026-08-31",20,times,List.of(withTeacher));
  check(teacherEvents.get(0).course.teacher.equals("张老师"));
  check(teacherEvents.get(0).key.equals(events.get(0).key)); // Keep pre-upgrade sent journal stable.
  check(teacherEvents.get(0).start-teacherEvents.get(0).remind==900000);
  check(ReminderPlanner.events("2026-08-31",20,times,List.of(c,c)).size()==20);
  check(ReminderPlanner.events("",20,times,List.of(c)).isEmpty());check(ReminderPlanner.events("2026-09-01",20,times,List.of(c)).isEmpty());
  var odd=ReminderPlanner.events("2026-08-31",20,times,List.of(course("单周",1,1,"odd")));check(odd.size()==10);check(odd.get(1).start==at("2026-09-14T08:00:00"));
  var even=ReminderPlanner.events("2026-08-31",20,times,List.of(course("双周",1,1,"even")));check(even.size()==10);check(even.get(0).start==at("2026-09-07T08:00:00"));
  check(ReminderPlanner.next(events,at("2026-08-31T07:44:59"))==events.get(0).remind);
  check(ReminderPlanner.next(events,at("2026-08-31T07:45:00"))==events.get(1).remind);
  check(ReminderPlanner.next(events,at("2027-03-01T00:00:00"))==0);
  long due=events.get(0).remind;check(ReminderPlanner.due(events,due,due,Set.of()).size()==1);
  check(ReminderPlanner.due(events,due,due-1,Set.of()).isEmpty());
  check(ReminderPlanner.due(events,due,at("2026-08-31T08:00:00"),Set.of()).isEmpty());
  check(ReminderPlanner.due(events,due,due,Set.of(events.get(0).key)).isEmpty());
  var simultaneous=ReminderPlanner.events("2026-08-31",20,times,List.of(c,course("课程乙",1,1,"all")));check(ReminderPlanner.due(simultaneous,due,due,Set.of()).size()==2);
  check(ReminderPlanner.due(Collections.emptyList(),due,due,Set.of()).isEmpty());
  check(ReminderPlanner.events("2026-08-31",20,times,List.of(course("未知",1,11,"all"))).isEmpty());
  var afternoon=ReminderPlanner.events("2026-08-31",20,times,List.of(course("下午",1,5,"all")));check(afternoon.get(0).remind==at("2026-08-31T13:15:00"));
  long reopen=at("2026-08-31T13:20:00"),start=at("2026-08-31T13:30:00");
  check(ReminderPlanner.pending(afternoon,reopen,Set.of()).size()==1);
  check(ReminderPlanner.pending(afternoon,at("2026-08-31T13:15:00"),Set.of()).size()==1);
  check(ReminderPlanner.pending(afternoon,at("2026-08-31T13:14:59"),Set.of()).isEmpty());
  check(ReminderPlanner.pending(afternoon,start,Set.of()).isEmpty());
  check(ReminderPlanner.pending(afternoon,start+1,Set.of()).isEmpty());
  var journal=Set.of(afternoon.get(0).key);
  check(ReminderPlanner.pending(afternoon,reopen,journal).isEmpty());
  check(ReminderPlanner.pending(afternoon,at("2026-08-31T13:29:59"),journal).isEmpty());
  check(ReminderPlanner.next(afternoon,reopen,journal)==afternoon.get(1).remind);
  check(ReminderPlanner.next(afternoon,afternoon.get(0).remind-1,journal)==afternoon.get(1).remind);
  check(ReminderPlanner.pending(List.of(),reopen,Set.of()).isEmpty());
  check(ReminderPlanner.pending(simultaneous,due,Set.of()).size()==2);
  check(ReminderPlanner.pending(simultaneous,due,Set.of(simultaneous.get(0).key)).size()==1);
  var mixed=new ArrayList<>(afternoon);mixed.addAll(events);
  check(ReminderPlanner.pending(mixed,reopen,Set.of()).size()==1);
  c.first=2;c.last=17;check(ReminderPlanner.events("2026-08-31",20,times,List.of(c)).size()==16);
  times[0]=new String[]{"08:20","08:50"};check(ReminderPlanner.events("2026-08-31",20,times,List.of(c)).get(0).remind==at("2026-09-07T08:05:00"));
  times[0]=new String[]{"00:10","00:50"};c.first=1;check(ReminderPlanner.events("2026-08-31",20,times,List.of(c)).get(0).remind==at("2026-08-30T23:55:00"));
  var midnight=ReminderPlanner.events("2026-08-31",20,times,List.of(c));
  check(ReminderPlanner.pending(midnight,at("2026-08-31T00:05:00"),Set.of()).size()==1);
  check(ReminderPlanner.pending(midnight,at("2026-08-31T00:05:00"),Set.of(midnight.get(0).key)).isEmpty());
  check(ReminderPlanner.events("2026-08-31",0,times,List.of(c)).isEmpty());
  System.out.println("PASS "+checks+" reminder planner checks");
 }
}
