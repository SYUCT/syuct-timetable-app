package top.syuct.timetable;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Only packaged UI has a bridge. The school WebView lives in a separate Activity. */
public class MainActivity extends Activity {
    private WebView web;
    private String pending;
    private boolean ready;
    private boolean pendingOverview;
    private static final int SCHOOL = 20;
    private static final int MAX_STATE = 400_000;
    private android.content.SharedPreferences prefs;
    private final java.util.concurrent.atomic.AtomicBoolean checkingUpdate=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile int availableUpdateCode;
    private final Handler widgetFeedbackHandler=new Handler(Looper.getMainLooper());
    private final Runnable widgetFeedback=()->showWidgetStatus();
    private void showWidgetStatus(){
        if(!ready||isFinishing()||isDestroyed())return;
        web.evaluateJavascript("window.receiveWidgetPinStatus && window.receiveWidgetPinStatus("+JSONObject.quote(WidgetPinRequest.status(this))+")",null);
    }

    private void updateResult(JSONObject result,int requestId){
        try{result.put("requestId",requestId);}catch(JSONException ignored){return;}
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed()||!ready)return;
            web.evaluateJavascript("window.receiveUpdateCheck && window.receiveUpdateCheck(JSON.parse("+JSONObject.quote(result.toString())+"))",null);
        });
    }
    private void updateFailure(String text,int requestId){
        try{updateResult(new JSONObject().put("status","error").put("message",text),requestId);}catch(JSONException ignored){}
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        prefs = getSharedPreferences("timetable", MODE_PRIVATE);
        pendingOverview=TodayWidget.OPEN.equals(getIntent().getAction());
        web = new WebView(this);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        web.setBackgroundColor(Color.rgb(243,246,250));
        // Inset the parent: WebView padding does not reliably inset its HTML viewport.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(243,246,250));
        root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom); return WindowInsets.CONSUMED;
        });
        if (Build.VERSION.SDK_INT < 30) root.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(), i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom()); return i;
        });
        root.requestApplyInsets();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setDomStorageEnabled(false);
        web.addJavascriptInterface(new LocalBridge(), "Native");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsConfirm(WebView v, String u, String msg, JsResult result) {
                if(!Policy.local(u)) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setNegativeButton("取消",(d,w)->result.cancel())
                    .setPositiveButton("确认",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show(); return true;
            }
            @Override public boolean onJsAlert(WebView v,String u,String msg,JsResult result) {
                if(!Policy.local(u)) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setPositiveButton("确定",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show(); return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                String path = r.getUrl().getPath();
                if (!Policy.local(url) || path == null || !path.matches("/[a-zA-Z0-9_.-]+")) return blocked();
                try {
                    String type = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".png") ? "image/png" : "text/html";
                    return new WebResourceResponse(type, "UTF-8", getAssets().open(path.substring(1)));
                } catch (IOException e) { return blocked(); }
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (Policy.local(url)) { ready = true; deliver();offerReminders();showWidgetStatus(); }
            }
        });
        web.loadUrl(Policy.LOCAL + "index.html");
    }
    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
    private void deliver() {
        if (ready && pending != null) {
            web.evaluateJavascript("window.receiveCapture(" + pending + ")", null); pending = null;
        }
        if(ready)web.evaluateJavascript("window.setWidgetEntry("+getIntent().getBooleanExtra("fromWidget",false)+")",null);
        if(ready&&pendingOverview){pendingOverview=false;web.evaluateJavascript("window.openOverview("+getIntent().getBooleanExtra("fromWidget",false)+")",null);}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);pendingOverview=TodayWidget.OPEN.equals(intent.getAction());deliver();}
    private void refreshWidget(){runOnUiThread(()->{try{TodayWidget.refreshAll(this);CourseReminder.schedule(this);}catch(RuntimeException ignored){Toast.makeText(this,"课表已保存，请重新打开应用更新提醒与小组件",Toast.LENGTH_SHORT).show();}});}
    private void offerReminders(){
        if(CourseReminder.prefs(this).contains("offered"))return;
        CourseReminder.prefs(this).edit().putBoolean("offered",true).apply();
        new AlertDialog.Builder(this).setTitle("开启上课提醒？").setMessage("每次上课前15分钟通知，使用系统默认提示音。可在设置中关闭。")
            .setNegativeButton("暂不开启",null).setPositiveButton("开启",(d,w)->{CourseReminder.enable(this,true);requestReminderPermissions();}).show();
    }
    private void requestReminderPermissions(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},31);return;}
        if(!CourseReminder.notifications(this)){
            new AlertDialog.Builder(this).setTitle("允许上课通知").setMessage("请在系统通知设置中允许「上课提醒」。")
                .setNegativeButton("取消",null).setPositiveButton("打开设置",(d,w)->openReminderSystemSettings()).show();return;
        }
        CourseReminder.schedule(this);
    }
    private void openReminderSystemSettings(){
        Intent intent=new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,CourseReminder.CHANNEL);
        try{startActivity(intent);}catch(ActivityNotFoundException e){Toast.makeText(this,"请在系统应用设置中开启通知",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==31){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)requestReminderPermissions();else Toast.makeText(this,"未允许通知，上课提醒暂不可用。可在设置中开启。",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onResume(){super.onResume();refreshWidget();if(ready)web.evaluateJavascript("window.refreshClock()",null);showWidgetStatus();}
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SCHOOL && result == RESULT_OK) {
            // Payload travels through private preferences, not Binder's limited transaction buffer.
            pending = prefs.getString("capture", null);
            prefs.edit().remove("capture").apply(); deliver();
        }
    }
    private String checkState(String value) throws JSONException {
        if (value == null || value.length() > MAX_STATE) throw new JSONException("数据过长");
        JSONObject o = new JSONObject(value);
        JSONArray list = o.getJSONArray("courses");
        JSONObject s = o.getJSONObject("settings");
        int total = s.getInt("totalWeeks");
        if(s.has("periodTimes"))WidgetState.readTimes(s.getJSONArray("periodTimes"));
        if (total < 1 || total > 30 || list.length() > 200) throw new JSONException("数量超出范围");
        for (int i=0; i<list.length(); i++) {
            JSONObject c=list.getJSONObject(i);
            if(c.getString("name").trim().isEmpty()) throw new JSONException("课程名为空");
            for(String field : new String[]{"name", "teacher", "room"}) if(c.optString(field).length()>500) throw new JSONException("字段过长");
            int d=c.getInt("weekday"), a=c.getInt("startSection"), b=c.getInt("endSection"), x=c.getInt("startWeek"), y=c.getInt("endWeek");
            if(d<1||d>7||a<1||b>12||a>b||x<1||x>y||y>total) throw new JSONException("时间范围无效");
            if(!java.util.Arrays.asList("all","odd","even").contains(c.getString("weekType"))) throw new JSONException("单双周无效");
        }
        return o.toString();
    }
    public class LocalBridge {
        @JavascriptInterface public String appVersion(){
            try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "未知版本";}
        }
        @JavascriptInterface public void checkUpdate(int requestId){
            if(requestId<=0)return;
            if(!checkingUpdate.compareAndSet(false,true)){updateFailure("上一次检测尚未结束，请稍后重试。",requestId);return;}
            availableUpdateCode=0;
            new Thread(()->{
                try{
                    android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
                    long installed=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
                    JSONObject result=UpdateChecker.check(installed);
                    if("available".equals(result.getString("status")))availableUpdateCode=result.getInt("versionCode");
                    updateResult(result,requestId);
                }catch(java.net.SocketTimeoutException e){updateFailure("检测超时，请稍后重试。",requestId);}
                catch(java.io.IOException e){updateFailure("暂时无法连接官网，请检查网络后重试。",requestId);}
                catch(Exception e){updateFailure("官网版本信息暂不可用，请稍后重试。",requestId);}
                finally{checkingUpdate.set(false);}
            },"syuct-update-check").start();
        }
        @JavascriptInterface public void downloadUpdate(int expectedCode){
            final int code=availableUpdateCode;if(code<=0||code!=expectedCode)return;
            runOnUiThread(()->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(UpdatePolicy.downloadFor(code))).addCategory(Intent.CATEGORY_BROWSABLE));}
                catch(ActivityNotFoundException e){if(!isDestroyed())web.evaluateJavascript("window.updateDownloadFailed && window.updateDownloadFailed()",null);}
            });
        }
        @JavascriptInterface public void backToDesktop(){runOnUiThread(()->{getIntent().removeExtra("fromWidget");moveTaskToBack(true);});}
        @JavascriptInterface public void reminderSettings(){runOnUiThread(()->{
            boolean enabled=CourseReminder.enabled(MainActivity.this);
            LinearLayout content=new LinearLayout(MainActivity.this);content.setOrientation(LinearLayout.VERTICAL);
            int padding=Math.round(24*getResources().getDisplayMetrics().density);content.setPadding(padding,padding/2,padding,padding/2);
            TextView description=new TextView(MainActivity.this);description.setTextSize(16);
            description.setText(CourseReminder.status(MainActivity.this)+"\n\n遵守单双周和开课周次；未设置开学日期或节次时间的课程不提醒。声音遵循手机静音、勿扰和通知设置。");
            content.addView(description);
            Button test=new Button(MainActivity.this);test.setText("发送测试通知");
            test.setOnClickListener(v->Toast.makeText(MainActivity.this,CourseReminder.testNotification(MainActivity.this),Toast.LENGTH_LONG).show());content.addView(test);
            Switch live=new Switch(MainActivity.this);live.setText("课前实时倒计时（试验）");live.setTextSize(16);
            live.setChecked(LiveCourseNotice.enabled(MainActivity.this));live.setEnabled(LiveCourseNotice.supported());content.addView(live);
            TextView liveStatus=new TextView(MainActivity.this);liveStatus.setTextSize(15);liveStatus.setText(LiveCourseNotice.status(MainActivity.this));content.addView(liveStatus);
            live.setOnCheckedChangeListener((v,checked)->{LiveCourseNotice.enable(MainActivity.this,checked);liveStatus.setText(LiveCourseNotice.status(MainActivity.this));});
            if(LiveCourseNotice.supported()){
                Button preview=new Button(MainActivity.this);preview.setText("预览倒计时效果");
                preview.setOnClickListener(v->Toast.makeText(MainActivity.this,LiveCourseNotice.preview(MainActivity.this),Toast.LENGTH_LONG).show());content.addView(preview);
            }
            ScrollView scroll=new ScrollView(MainActivity.this);scroll.addView(content);
            new AlertDialog.Builder(MainActivity.this).setTitle("上课提醒 · 提前15分钟")
                .setView(scroll)
                .setNegativeButton("关闭窗口",null).setNeutralButton("权限与声音",(d,w)->{
                    if(!CourseReminder.notifications(MainActivity.this))requestReminderPermissions();else openReminderSystemSettings();
                }).setPositiveButton(enabled?"关闭提醒":"开启提醒",(d,w)->{CourseReminder.enable(MainActivity.this,!enabled);if(!enabled)requestReminderPermissions();}).show();
        });}
        @JavascriptInterface public void community(String target) {
            if(!"github".equals(target)&&!"website".equals(target)&&!"group".equals(target))return;
            runOnUiThread(()->{
                if("group".equals(target)){
                    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("新生交流群号","1170264357"));
                    Toast.makeText(MainActivity.this,"群号已复制",Toast.LENGTH_SHORT).show();return;
                }
                String url="github".equals(target)?"https://github.com/SYUCT":"https://www.syuct.top/";
                try {startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
                catch(ActivityNotFoundException e){Toast.makeText(MainActivity.this,"未找到可打开链接的浏览器",Toast.LENGTH_LONG).show();}
            });
        }
        @JavascriptInterface public String defaultTimes(){return WidgetState.defaults(MainActivity.this);}
        @JavascriptInterface public void addWidget(){runOnUiThread(()->{
            WidgetPinRequest.request(MainActivity.this);showWidgetStatus();
            widgetFeedbackHandler.removeCallbacks(widgetFeedback);widgetFeedbackHandler.postDelayed(widgetFeedback,10500);
        });}
        @JavascriptInterface public String load() { return prefs.getString("state", ""); }
        @JavascriptInterface public String save(String value) {
            try {
                value = checkState(value);
                boolean ok=prefs.edit().putString("backup", prefs.getString("state", "")).putString("state", value).commit();
                if(ok)refreshWidget();
                return ok ? "" : "保存失败，请检查存储空间";
            } catch(Exception e) { return "未保存：课表数据无效"; }
        }
        @JavascriptInterface public String restore() {
            String old=prefs.getString("backup", "");
            if(old.isEmpty()) return "没有上一版课表";
            try { checkState(old);boolean ok=prefs.edit().putString("backup",prefs.getString("state", "")).putString("state",old).commit();if(ok)refreshWidget();return ok ? "" : "恢复失败"; }
            catch(Exception e) { return "备份数据无效"; }
        }
        @JavascriptInterface public void school(String kind) {
            if (!kind.equals("undergraduate") && !kind.equals("graduate")) return;
            runOnUiThread(() -> startActivityForResult(new Intent(MainActivity.this, SchoolActivity.class).putExtra("kind", kind), SCHOOL));
        }
        @JavascriptInterface public void copy(String text) {
            if(text==null || text.length()>200000) return;
            runOnUiThread(() -> {
                android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                c.setPrimaryClip(ClipData.newPlainText("化大课表码", text));
                Toast.makeText(MainActivity.this,"课表码已复制",Toast.LENGTH_SHORT).show();
            });
        }
        @JavascriptInterface public void clearLogin() {
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("清除教务登录状态？")
                .setMessage("将退出本科和硕士教务登录，不影响已保存课表。")
                .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)-> {
                    CookieManager.getInstance().removeAllCookies(v -> {CookieManager.getInstance().flush(); Toast.makeText(MainActivity.this,"登录状态已清除",Toast.LENGTH_SHORT).show();});
                    WebStorage.getInstance().deleteAllData(); web.clearCache(true);
                }).show());
        }
    }
    @Override public void onBackPressed() { web.evaluateJavascript("window.goHome()", null); }
    @Override protected void onDestroy() { widgetFeedbackHandler.removeCallbacks(widgetFeedback);web.removeJavascriptInterface("Native"); web.destroy(); super.onDestroy(); }
}
