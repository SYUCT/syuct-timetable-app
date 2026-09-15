package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Local reminder controls. System permission screens only open on explicit taps. */
final class ReminderSettings extends Dialog {
    private static final int INK=0xff203753,MUTED=0xff62738a,BLUE=CourseNoticeStyle.BLUE;
    private final Activity activity;
    private final Runnable permission,sound;
    private Switch master,precise,live;
    private TextView plan,precision,liveStatus,feedback;
    private Button authorize,livePreview;
    private boolean refreshing;
    ReminderSettings(Activity activity,Runnable permission,Runnable sound){
        super(activity);this.activity=activity;this.permission=permission;this.sound=sound;
    }
    private int dp(int n){return Math.round(n*getContext().getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private void add(LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);
    }
    private LinearLayout card(LinearLayout root){
        LinearLayout c=new LinearLayout(getContext());c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));
        GradientDrawable b=bg(Color.WHITE,18);b.setStroke(dp(1),0xffdfe8f5);c.setBackground(b);add(root,c,12);return c;
    }
    private Switch toggle(String title){
        Switch s=new Switch(getContext());s.setText(title);s.setTextSize(17);s.setTextColor(INK);s.setTypeface(null,Typeface.BOLD);s.setMinHeight(dp(48));s.setSwitchPadding(dp(12));
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{BLUE,0xff8392a7}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{0xffc6d6f5,0xffe1e7ef}));return s;
    }
    private Button button(String label,boolean primary,Runnable action){
        Button b=new Button(getContext());b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?Color.WHITE:BLUE);
        b.setMinHeight(dp(48));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackgroundTintList(null);b.setBackground(bg(primary?BLUE:0xffedf3fd,12));
        b.setOnClickListener(v->action.run());return b;
    }
    @Override protected void onCreate(android.os.Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(20),dp(16),dp(16));root.setBackground(bg(0xfff3f6fc,24));
        LinearLayout header=new LinearLayout(getContext());header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge=new ImageView(getContext());badge.setImageResource(R.drawable.campus_badge);badge.setContentDescription("化大课表校徽");
        header.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout heading=new LinearLayout(getContext());heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(dp(12),0,0,0);
        heading.addView(text("上课提醒",23,INK,true));heading.addView(text("提前 15 分钟 · 只提醒，不常驻",13,MUTED,false));header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(false);
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        LinearLayout.LayoutParams sc=new LinearLayout.LayoutParams(-1,0,1);sc.topMargin=dp(8);root.addView(scroll,sc);
        LinearLayout basic=card(content);master=toggle("上课提醒");basic.addView(master);
        plan=text("",14,MUTED,false);add(basic,plan,8);
        add(basic,button("通知权限与声音",false,()->{if(!CourseReminder.notifications(activity))permission.run();else sound.run();}),12);
        LinearLayout exact=card(content);precise=toggle("准时提醒");exact.addView(precise);
        precision=text("",14,BLUE,false);add(exact,precision,6);
        add(exact,text("只在提醒时唤醒。系统权限名为“闹钟和提醒”，不会添加时钟闹钟，也不会持续响铃。",14,MUTED,false),8);
        authorize=button("去授权准时提醒",false,this::requestExact);add(exact,authorize,12);
        LinearLayout island=card(content);live=toggle("课前实时倒计时");island.addView(live);
        add(island,text("试验功能 · 展示样式由手机系统决定",12,BLUE,false),4);
        liveStatus=text("",14,MUTED,false);add(island,liveStatus,6);
        LinearLayout preview=card(content);preview.addView(text("看看提醒长什么样",18,INK,true));
        NoticePreview sample=NoticePreview.sample(activity);
        add(preview,text(sample.source(),13,MUTED,false),8);
        LinearLayout sampleCard=new LinearLayout(getContext());sampleCard.setOrientation(LinearLayout.VERTICAL);sampleCard.setPadding(dp(14),dp(12),dp(14),dp(12));
        sampleCard.setBackground(bg(0xffedf3fd,14));
        sampleCard.addView(text("效果预览",12,BLUE,true));add(sampleCard,text(sample.name,18,INK,true),6);
        add(sampleCard,text("课程地点："+sample.location(),14,MUTED,false),5);add(sampleCard,text("倒计时演示：从 3 分钟开始",14,MUTED,false),5);add(preview,sampleCard,12);
        add(preview,button("查看通知效果",true,()->feedback.setText(CourseReminder.testNotification(activity))),12);
        livePreview=button("预览 3 分钟倒计时",false,()->feedback.setText(LiveCourseNotice.preview(activity)));add(preview,livePreview,8);
        feedback=text("预览是可选体验，不影响正式提醒，也不代表后台触发一定准时。",13,MUTED,false);feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(preview,feedback,10);
        add(content,text("请设置第一周日期与节次时间。系统强行停止、关机或撤销授权仍可能影响提醒；撤销准时提醒权限后，请重新打开 App 恢复普通预约。",12,MUTED,false),12);
        add(root,button("完成",false,this::dismiss),12);setContentView(root);
        master.setOnCheckedChangeListener((v,on)->{if(refreshing)return;CourseReminder.enable(activity,on);if(on&&!CourseReminder.notifications(activity))permission.run();refresh();});
        precise.setOnCheckedChangeListener((v,on)->{if(refreshing)return;ExactReminder.enable(activity,on);refresh();if(on&&!ExactReminder.permitted(activity))explainExact();});
        live.setOnCheckedChangeListener((v,on)->{if(refreshing)return;LiveCourseNotice.enable(activity,on);refresh();});refresh();
    }
    @Override protected void onStart(){
        super.onStart();Window window=getWindow();if(window==null)return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        android.util.DisplayMetrics m=getContext().getResources().getDisplayMetrics();
        window.setLayout(Math.min(m.widthPixels-dp(24),dp(480)),(int)(m.heightPixels*.88));
        refresh();
    }
    void refresh(){
        if(master==null)return;refreshing=true;
        master.setChecked(CourseReminder.enabled(activity));precise.setChecked(ExactReminder.requested(activity));
        live.setChecked(LiveCourseNotice.enabled(activity));live.setEnabled(LiveCourseNotice.supported());
        plan.setText(CourseReminder.status(activity));precision.setText(ExactReminder.status(activity));liveStatus.setText(LiveCourseNotice.status(activity));
        authorize.setVisibility(ExactReminder.requested(activity)&&!ExactReminder.permitted(activity)?View.VISIBLE:View.GONE);
        livePreview.setVisibility(LiveCourseNotice.supported()?View.VISIBLE:View.GONE);refreshing=false;
    }
    private void explainExact(){
        new AlertDialog.Builder(activity).setTitle("让课前提醒更准时")
            .setMessage("请允许系统的“闹钟和提醒”权限，用于课前15分钟精确定时。仍只发送普通通知，不添加闹钟、不常驻后台。不授权也可继续使用普通提醒，但可能延迟。")
            .setNegativeButton("暂用普通提醒",(d,w)->{ExactReminder.enable(activity,false);refresh();})
            .setPositiveButton("去授权",(d,w)->requestExact()).show();
    }
    private void requestExact(){
        if(Build.VERSION.SDK_INT<31||ExactReminder.permitted(activity)){CourseReminder.schedule(activity);refresh();return;}
        try{activity.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+activity.getPackageName())));}
        catch(ActivityNotFoundException|SecurityException e){precision.setText("无法直达授权页。请到系统设置 → 应用 → 特殊权限 → 闹钟和提醒中允许化大课表。");}
    }
}
