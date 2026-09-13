package top.syuct.timetable;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.*;

/** Local-only native layout check; copy into an isolated checkout's debug source set. */
public class WidgetLayoutProbe extends Activity {
  private View widget(Context context) {
    View view = android.view.LayoutInflater.from(context).inflate(R.layout.widget_today, null);
    ((TextView)view.findViewById(R.id.widget_title)).setText("今日课表 · 周日");
    ((TextView)view.findViewById(R.id.widget_date)).setText("9月13日 · 第2周");
    return view;
  }
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    int tested = 0, reproduced = 0;
    for (float font : new float[]{1f, 1.3f, 1.6f}) for (int width : new int[]{220, 280, 340, 400}) {
      Configuration config = new Configuration(getResources().getConfiguration());
      config.fontScale = font;
      Context context = createConfigurationContext(config);
      float density = context.getResources().getDisplayMetrics().density;
      for (boolean old : new boolean[]{true, false}) {
        LinearLayout root = (LinearLayout)widget(context);
        LinearLayout row = (LinearLayout)root.getChildAt(0);
        View label = row.getChildAt(0), badge = row.getChildAt(1);
        if (old) {
          row.setPadding(0,0,0,0); row.setBaselineAligned(true);
          label.setPadding(0,0,0,Math.round(10*density));
          ((LinearLayout.LayoutParams)badge.getLayoutParams()).bottomMargin = Math.round(10*density);
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*density), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Math.round(300*density), View.MeasureSpec.EXACTLY));
        root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
        if (old) { if (badge.getTop()<0) reproduced++; }
        else {
          if (badge.getTop()<0 || badge.getBottom()>row.getHeight()-row.getPaddingBottom()) throw new AssertionError("Badge clipped: "+width+" font "+font);
          if (badge.getHeight()!=Math.round(44*density)) throw new AssertionError("Badge size changed");
          tested++;
        }
      }
    }
    if (reproduced==0) throw new AssertionError("Original clipping not reproduced");
    android.util.Log.i("WidgetLayoutProbe", "PASS "+tested+" native layouts; original clipping reproduced in "+reproduced+" cases");
    FrameLayout background = new FrameLayout(this); background.setBackgroundColor(0xff486fa7);
    View view = widget(this);
    ((TextView)view.findViewById(R.id.widget_empty)).setText("今日暂无课程");
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1,Math.round(300*getResources().getDisplayMetrics().density),android.view.Gravity.CENTER);
    lp.setMargins(24,0,24,0); background.addView(view,lp); setContentView(background);
  }
}
