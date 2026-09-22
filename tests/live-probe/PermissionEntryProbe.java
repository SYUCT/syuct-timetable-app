package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

/** Only intercepts navigation in this isolated test Activity, never in production. */
public final class PermissionEntryProbe extends Activity {
    Intent opened;int checks;
    @Override public void startActivity(Intent intent){opened=intent;}
    void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!getPackageName().endsWith(".liveprobe"))throw new SecurityException("Isolated test only");
        CourseReminder.prefs(this).edit().clear().commit();
        ReminderSettings dialog=new ReminderSettings(this,()->{},()->{});dialog.show();
        Button entry=dialog.getWindow().getDecorView().findViewWithTag("exact-permission");
        boolean allowed=getIntent().getBooleanExtra("allowed",false);
        check(ExactReminder.permitted(this)==allowed,"expected system permission state");
        for(boolean enabled:new boolean[]{false,true}){
            CourseReminder.prefs(this).edit().putBoolean("preciseReminder",enabled).commit();dialog.refresh();
            check(entry!=null&&entry.getVisibility()==View.VISIBLE&&entry.isEnabled(),"entry visible with feature off/on");
            check(entry.getText().toString().contains(allowed?"已允许":"去授权"),"label matches permission");
            opened=null;entry.performClick();
            check(opened!=null&&Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM.equals(opened.getAction()),"tap opens exact permission even when already granted");
            check(("package:"+getPackageName()).equals(opened.getDataString()),"settings targets this app only");
            check(ExactReminder.requested(this)==enabled&&!CourseReminder.enabled(this),"navigation does not opt user into reminders");
        }
        dialog.dismiss();CourseReminder.prefs(this).edit().clear().commit();
        String result="PASS "+checks+" permission entry; allowed="+allowed;
        android.util.Log.i("PermissionEntryProbe",result);TextView report=new TextView(this);report.setText(result);setContentView(report);
    }
}
