package top.syuct.timetable;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Map;

/** Tests the real immutable notification PendingIntent and read-only detail screen. */
public final class DetailsScreenProbe extends Activity implements Application.ActivityLifecycleCallbacks {
    int checks,stage; boolean launched; PendingIntent first,second; Map<String,?> before;
    final Handler handler=new Handler(Looper.getMainLooper());
    final String course="机械结构有限元分析与现代制造技术综合实践课程";
    final String room="瑞师楼（原3号教学楼）222教室，课程地点补充信息用于验证长文本自动换行";
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    TextView find(View v,String value){
        if(v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return (TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView hit=find(((ViewGroup)v).getChildAt(i),value);if(hit!=null)return hit;}
        return null;
    }
    void fail(Throwable e){android.util.Log.e("DetailsScreenProbe","FAIL",e);getApplication().unregisterActivityLifecycleCallbacks(this);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        if(!getPackageName().endsWith(".liveprobe"))throw new SecurityException("Isolated package only");
        TextView report=new TextView(this);report.setText("Reminder details QA");setContentView(report);
        before=CourseReminder.prefs(this).getAll();getApplication().registerActivityLifecycleCallbacks(this);
        first=CourseNoticeDetails.open(this,"qa-first",course,0,"王老师、李老师（联合授课）",room,true);
        second=CourseNoticeDetails.open(this,"qa-second","电工学",0,null,null,false);
        check(!first.equals(second),"different courses have independent snapshots");
    }
    @Override protected void onResume(){super.onResume();if(!launched){launched=true;handler.postDelayed(()->{try{first.send();}catch(Exception e){fail(e);}},400);}}
    @Override public void onActivityResumed(Activity a){if(!(a instanceof CourseNoticeDetails))return;
        handler.postDelayed(()->{try{
            View root=a.getWindow().getDecorView();
            check(!getPackageManager().getActivityInfo(a.getComponentName(),0).exported,"detail screen not exported");
            for(String label:new String[]{"课程名称","开课时间","授课教师","课程地点"})check(find(root,label)!=null,"separate field "+label);
            check(find(root,"08:00")!=null,"time is readable");
            int[] position=new int[2];find(root,"返回").getLocationOnScreen(position);
            check(position[1]>=root.getRootWindowInsets().getInsets(WindowInsets.Type.statusBars()).top,"back button clears status bar");
            if(stage==0){
                TextView title=find(root,course),location=find(root,room);
                check(title!=null&&location!=null,"full long course and room preserved");
                check(location.getLineCount()>1&&location.getEllipsize()==null,"long room wraps without truncation");
                check(find(root,"王老师、李老师（联合授课）")!=null,"teacher retained");
                check(find(root,"提醒效果预览")!=null,"preview explicit");
                if(getIntent().getBooleanExtra("visual",false)){android.util.Log.i("DetailsScreenProbe","VISUAL "+checks);return;}
                stage=1;find(root,"返回").performClick();handler.postDelayed(()->{try{second.send();}catch(Exception e){fail(e);}},300);
            }else{
                check(find(root,"电工学")!=null&&find(root,course)==null,"second course does not overwrite first snapshot");
                check(find(root,"未提供")!=null,"missing fields explicit");
                check(find(root,"上课提醒")!=null,"formal header");
                check(before.equals(CourseReminder.prefs(this).getAll()),"opening details does not change reminder preferences");
                find(root,"返回").performClick();getApplication().unregisterActivityLifecycleCallbacks(this);
                android.util.Log.i("DetailsScreenProbe","PASS "+checks);
            }
        }catch(Exception|AssertionError e){fail(e);}},500);
    }
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
    public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){}
    public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
}
