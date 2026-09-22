package top.syuct.timetable;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.*;

/** Read-only reminder snapshot. Non-exported; does not load WebView or edit courses. */
public final class CourseNoticeDetails extends Activity {
    static PendingIntent open(Context c,String key,String name,long start,String teacher,String room,boolean preview){
        Intent intent=new Intent(c,CourseNoticeDetails.class)
            .setData(Uri.parse("syuct-notice://details/"+Uri.encode(key)))
            .putExtra("name",name).putExtra("start",start).putExtra("teacher",teacher).putExtra("room",room).putExtra("preview",preview);
        return PendingIntent.getActivity(c,174,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    TextView text(String value,int size,int color,boolean bold){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        view.setLineSpacing(dp(4),1);if(bold)view.setTypeface(null,Typeface.BOLD);return view;
    }
    void row(LinearLayout root,String label,String value){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(16));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));box.setBackground(bg);
        box.addView(text(label,13,0xff62738a,false));
        TextView content=text(CourseNoticeStyle.field(value),19,0xff203753,true);content.setTextIsSelectable(true);content.setPadding(0,dp(7),0,0);box.addView(content);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);root.addView(box,lp);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Intent data=getIntent();if(!data.hasExtra("start")){finish();return;}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff3f6fc);
        if(Build.VERSION.SDK_INT>=30)scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsets.CONSUMED;
        });
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(16),dp(20),dp(24));scroll.addView(root);
        Button back=new Button(this);back.setText(R.string.notice_back);back.setTextColor(0xff315eb4);back.setAllCaps(false);back.setMinHeight(dp(48));back.setOnClickListener(v->finish());
        GradientDrawable backBg=new GradientDrawable();backBg.setColor(0xffe7eefb);backBg.setCornerRadius(dp(16));back.setBackground(backBg);back.setStateListAnimator(null);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(88),dp(48));root.addView(back,bp);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(20),0,dp(12));
        ImageView badge=new ImageView(this);badge.setImageResource(R.drawable.campus_badge);badge.setContentDescription("化大课表校徽");header.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text(data.getBooleanExtra("preview",false)?"提醒效果预览":"上课提醒",23,0xff203753,true);title.setPadding(dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        row(root,"课程名称",data.getStringExtra("name"));
        row(root,"开课时间",CourseNoticeStyle.time(data.getLongExtra("start",0)));
        row(root,"授课教师",data.getStringExtra("teacher"));row(root,"课程地点",data.getStringExtra("room"));
        if(data.getBooleanExtra("preview",false)){
            TextView note=text("以上为提醒样式预览，开课时间为演示时间，不会修改你的课表。",14,0xff62738a,false);note.setPadding(0,dp(18),0,0);root.addView(note);
        }
        setContentView(scroll);scroll.requestApplyInsets();
    }
}
