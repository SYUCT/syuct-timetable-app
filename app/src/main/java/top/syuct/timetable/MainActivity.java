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
    private static final int SCHOOL = 20;
    private static final int MAX_STATE = 400_000;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("timetable", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(243,246,250));
        web.setFitsSystemWindows(true);
        setContentView(web);
        if (Build.VERSION.SDK_INT >= 30) web.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom); return insets;
        });
        if (Build.VERSION.SDK_INT < 30) web.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(), i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom()); return i;
        });
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
                    String type = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                    return new WebResourceResponse(type, "UTF-8", getAssets().open(path.substring(1)));
                } catch (IOException e) { return blocked(); }
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (Policy.local(url)) { ready = true; deliver(); }
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
    }
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
        @JavascriptInterface public String load() { return prefs.getString("state", ""); }
        @JavascriptInterface public String save(String value) {
            try {
                value = checkState(value);
                boolean ok=prefs.edit().putString("backup", prefs.getString("state", "")).putString("state", value).commit();
                return ok ? "" : "保存失败，请检查存储空间";
            } catch(Exception e) { return "未保存：课表数据无效"; }
        }
        @JavascriptInterface public String restore() {
            String old=prefs.getString("backup", "");
            if(old.isEmpty()) return "没有上一版课表";
            try { checkState(old); return prefs.edit().putString("backup",prefs.getString("state", "")).putString("state",old).commit() ? "" : "恢复失败"; }
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
    @Override protected void onDestroy() { web.removeJavascriptInterface("Native"); web.destroy(); super.onDestroy(); }
}
