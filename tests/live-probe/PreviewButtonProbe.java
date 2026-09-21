package top.syuct.timetable;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.service.notification.StatusBarNotification;

/** Calls the actual dialog buttons and observes system records. Isolated QA APK only. */
public final class PreviewButtonProbe extends Activity {
    final Handler handler=new Handler(Looper.getMainLooper());
    int checks; ReminderSettings dialog; TextView report;
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    NotificationManager manager(){return getSystemService(NotificationManager.class);}
    View find(View root,java.util.function.Predicate<View> match){
        if(match.test(root))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),match);if(found!=null)return found;}}
        return null;
    }
    Button button(){return (Button)find(dialog.getWindow().getDecorView(),v->v instanceof Button&&((Button)v).getText().toString().equals("预览 15 分钟倒计时"));}
    TextView feedback(){return (TextView)find(dialog.getWindow().getDecorView(),v->v instanceof TextView&&((TextView)v).getText().toString().startsWith("预览是可选体验"));}
    void await(String label,java.util.function.BooleanSupplier ready,Runnable next){
        long deadline=SystemClock.uptimeMillis()+7000;
        handler.post(new Runnable(){public void run(){
            if(ready.getAsBoolean()){check(true,label);next.run();return;}
            if(SystemClock.uptimeMillis()>deadline)throw new AssertionError(label);
            handler.postDelayed(this,50);
        }});
    }
    StatusBarNotification current(){for(StatusBarNotification n:manager().getActiveNotifications())if(n.getId()==153&&LiveCourseNotice.PREVIEW.equals(n.getTag()))return n;return null;}
    void finishChecks(){
        dialog.dismiss();manager().cancelAll();
        report.setText("PASS "+checks+" preview button checks");
        android.util.Log.i("PreviewButtonProbe","PASS "+checks+" preview button checks");
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!getPackageName().endsWith(".liveprobe"))throw new SecurityException("QA only");
        report=new TextView(this);setContentView(report);
        manager().cancelAll();CourseReminder.prefs(this).edit().clear().commit();
        dialog=new ReminderSettings(this,()->{},()->{});dialog.show();
        if(getIntent().getBooleanExtra("visual",false)){
            LiveCourseNotice.enable(this,true);dialog.refresh();return;
        }
        Button start=button();TextView feedback=feedback();
        check(start!=null&&feedback!=null,"real dialog controls exist");
        boolean denied=getIntent().getBooleanExtra("denied",false);
        if(denied)LiveCourseNotice.enable(this,true);
        start.performClick();start.performClick();
        check(!start.isEnabled(),"immediate busy state and repeat guard");
        check(feedback.getText().toString().startsWith("正在发送"),"immediate feedback before worker result");
        await("disabled/permission failure shown",()->start.isEnabled(),()->{
            check(feedback.getText().toString().contains(denied?"请先允许":"请先开启"),"actionable failure not original help text");
            check(current()==null,"disabled case posts nothing");
            if(denied){finishChecks();return;}
            LiveCourseNotice.enable(this,true);start.performClick();
            await("actual click completed",()->start.isEnabled(),()->{
                check(feedback.getText().toString().startsWith("系统已接收"),"result based on active system record");
                check(current()!=null,"notification actually posted");
                Notification first=current().getNotification();long target=first.extras.getLong("syuct.preview.target");
                check(target>System.currentTimeMillis(),"this preview has future token");
                check(first.getTimeoutAfter()==ReminderPlanner.LEAD,"fifteen minute system expiry");
                check(!XiaomiIsland.hasPayload(first),"actual button does not select unverified native template");
                check((first.flags&Notification.FLAG_ONLY_ALERT_ONCE)==0,"explicit repeat preview can alert again");
                check(!CourseReminder.enabled(this),"preview does not enable real reminders");
                check(CourseReminder.prefs(this).getStringSet("sent",java.util.Collections.emptySet()).isEmpty(),"preview does not mark real courses sent");
                start.performClick();
                await("second click completed",()->start.isEnabled(),()->{
                    check(current()!=null&&current().getNotification().extras.getLong("syuct.preview.target")>target,"fresh preview replaces old token");
                    check(manager().getActiveNotifications().length==1,"repeat click does not duplicate notifications");
                    LiveCourseNotice.enable(this,false);
                    await("turn off cancels even ordinary preview",()->current()==null,this::finishChecks);
                });
            });
        });
    }
}
