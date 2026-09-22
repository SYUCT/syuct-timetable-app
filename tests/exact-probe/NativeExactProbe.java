package top.syuct.timetable;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.TextView;
import org.json.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Isolated QA package: no production account or timetable state. */
public final class NativeExactProbe extends Activity {
    int checks;
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    void report(String phase){String value="PASS "+checks+" "+phase;android.util.Log.i("NativeExactProbe",value);TextView t=new TextView(this);t.setText(value);t.setTextSize(18);t.setPadding(24,96,24,24);setContentView(t);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!getPackageName().endsWith(".exactprobe"))throw new SecurityException("Isolated package only");
        String phase=getIntent().getStringExtra("phase");if(phase==null)phase="denied";
        try{
            if(phase.equals("resume")){
                check(ExactReminder.requested(this),"selection survives revoke");check(!ExactReminder.permitted(this),"permission revoked");
                CourseReminder.schedule(this);check(!CourseReminder.prefs(this).getBoolean("scheduledExact",true),"reopen after revoke restores ordinary");
                check(CourseReminder.prefs(this).getLong("scheduledAt",0)>0,"ordinary appointment restored");report(phase);return;
            }
            CourseReminder.enable(this,false);getSystemService(NotificationManager.class).cancelAll();CourseReminder.prefs(this).edit().clear().commit();
            check(!ExactReminder.requested(this),"default ordinary");
            LocalDate monday=LocalDate.now(LessonClock.ZONE).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
            JSONObject course=new JSONObject().put("name","现代设计方法").put("room","瑞师楼222").put("weekType","all").put("weekday",1).put("startSection",1).put("startWeek",1).put("endWeek",20);
            String saved=new JSONObject().put("settings",new JSONObject().put("firstWeekDate",monday.toString()).put("totalWeeks",20)).put("courses",new JSONArray().put(course)).toString();
            getSharedPreferences("timetable",0).edit().putString("state",saved).commit();
            CourseReminder.enable(this,true);check(!CourseReminder.prefs(this).getBoolean("scheduledExact",true),"ordinary initially reserved");
            long at=CourseReminder.prefs(this).getLong("scheduledAt",0);check(at>System.currentTimeMillis(),"valid next reminder");
            boolean granted=phase.equals("granted")||phase.equals("delivery");
            check(ExactReminder.permitted(this)==granted,"expected permission state");
            ExactReminder.enable(this,true);check(ExactReminder.requested(this),"explicit choice stored");
            check(CourseReminder.prefs(this).getBoolean("scheduledExact",false)==granted,"correct reservation mode");
            check(CourseReminder.prefs(this).getLong("scheduledAt",0)==at,"permission change preserves due time");
            CourseReminder.schedule(this);check(CourseReminder.prefs(this).getBoolean("scheduledExact",false)==granted,"reopen preserves mode");
            NoticePreview p=NoticePreview.sample(this);check(p.borrowed&&p.name.equals("现代设计方法"),"preview borrows next course");
            Notification n=p.builder(this,System.currentTimeMillis()+180000,true).build();
            check(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString().equals("效果预览"),"synthetic time labelled");
            check(n.publicVersion!=null&&!n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(p.name),"locked preview redacted");
            CourseReminder.testNotification(this);check(saved.equals(getSharedPreferences("timetable",0).getString("state","")),"preview preserves timetable");
            check(CourseReminder.prefs(this).getStringSet("sent",Set.of()).isEmpty(),"preview leaves sent journal untouched");
            ExactReminder.enable(this,false);check(!CourseReminder.prefs(this).getBoolean("scheduledExact",true),"opt out rearms ordinary");
            // Force a mismatch at an identical timestamp: schedule() must not keep old mode.
            CourseReminder.prefs(this).edit().putBoolean("scheduledExact",true).commit();CourseReminder.schedule(this);
            check(!CourseReminder.prefs(this).getBoolean("scheduledExact",true),"same timestamp rearmed when mode differs");
            CourseReminder.enable(this,false);check(CourseReminder.prefs(this).getLong("scheduledAt",0)==0,"disabled clears appointment");
            if(!granted){
                PendingIntent intent=PendingIntent.getBroadcast(this,777,new Intent(this,ExactDeliveryProbe.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                check(!ExactReminder.reserve(getSystemService(AlarmManager.class),System.currentTimeMillis()+600000,intent,true),"denied exact call safely falls back");
                getSystemService(AlarmManager.class).cancel(intent);
            }
            CourseReminder.enable(this,true);ExactReminder.enable(this,true);
            new CourseReminder().onReceive(this,new Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
            check(CourseReminder.prefs(this).getBoolean("scheduledExact",false)==granted,"permission broadcast reconciles actual grant");
            report(phase);
            if(phase.equals("visual"))new ReminderSettings(this,()->{},()->{}).show();
            if(phase.equals("delivery")){
                CourseReminder.enable(this,false);long due=System.currentTimeMillis()+20000;
                PendingIntent pi=PendingIntent.getBroadcast(this,778,new Intent(this,ExactDeliveryProbe.class).putExtra("due",due),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                check(ExactReminder.reserve(getSystemService(AlarmManager.class),due,pi,true),"delivery exact reserved");
                report(phase);moveTaskToBack(true);
            }
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
