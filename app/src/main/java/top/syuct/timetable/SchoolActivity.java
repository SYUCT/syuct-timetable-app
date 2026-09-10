package top.syuct.timetable;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.http.SslError;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** School content gets no JavascriptInterface, clipboard access or local file access. */
public class SchoolActivity extends Activity {
    private WebView web;
    private TextView status;
    private Button read;
    private String host, kind;
    private int navigation = 0;
    private boolean reading = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        kind = "graduate".equals(getIntent().getStringExtra("kind")) ? "graduate" : "undergraduate";
        host = kind.equals("graduate") ? "geims.syuct.edu.cn" : "jws.syuct.edu.cn";
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(243,246,250));
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v,i)-> {
            android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()); v.setPadding(b.left,b.top,b.right,b.bottom); return i;
        }); else root.setFitsSystemWindows(true);
        TextView title = new TextView(this); title.setText((kind.equals("graduate") ? "硕士教务" : "本科教务") + " · " + host);
        title.setTextSize(17); title.setTextColor(Color.rgb(20,61,99)); title.setPadding(16,14,16,6); root.addView(title);
        status = new TextView(this); status.setTextSize(13); status.setPadding(16,4,16,8);
        status.setText(kind.equals("graduate") ? "登录后进入「我的课程表」，选择学期，再点底部读取。" : "登录后进入「信息查询 → 学生个人课表」，不要读取首页摘要。"); root.addView(status);
        web = new WebView(this); root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons = new LinearLayout(this);
        Button back = new Button(this); back.setText("返回"); back.setOnClickListener(v->onBackPressed()); buttons.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh = new Button(this); refresh.setText("刷新"); refresh.setOnClickListener(v->web.reload()); buttons.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
        read = new Button(this); read.setText("读取课表"); read.setOnClickListener(v->capture()); buttons.addView(read,new LinearLayout.LayoutParams(0,-2,2)); root.addView(buttons);
        setContentView(root);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setSafeBrowsingEnabled(true);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if(Policy.school(r.getUrl().toString(),host)) return false;
                status.setText("已拦截非当前教务域名的跳转。如需统一认证，请反馈该域名后适配。"); return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
                navigation++; reading=false; read.setEnabled(false);
                if(!Policy.school(url,host)) { v.stopLoading(); status.setText("该地址不在教务白名单内。"); }
            }
            @Override public void onPageFinished(WebView v,String url) { read.setEnabled(Policy.school(url,host)); }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e) { h.cancel(); read.setEnabled(false); status.setText("教务网站证书验证失败，已停止连接。请勿忽略证书错误。"); }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame()) { status.setText("教务页面未加载成功，请检查网络后刷新。校内限制或认证跳转需另行适配。"); read.setEnabled(false); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
        });
        web.setDownloadListener((u,a,b,c,d)->status.setText("首版仅支持读取网页课表；文件下载请使用系统浏览器。"));
        web.loadUrl("https://"+host+"/");
    }
    private void capture() {
        if(reading || !Policy.school(web.getUrl(),host)) return;
        reading=true; read.setEnabled(false); status.setText("正在读取当前课表，请勿切换页面…");
        final int epoch=navigation;
        try {
            String script;
            try(InputStream in=getAssets().open("collector.js"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                script=out.toString("UTF-8");
            }
            web.evaluateJavascript(script, raw -> {
                if(epoch!=navigation || !reading || !Policy.school(web.getUrl(),host)) return;
                reading=false; read.setEnabled(true);
                try {
                    if(raw==null || raw.length()>1_600_000) throw new Exception("课表内容过大，请打开单独课表页。");
                    Object v=new JSONTokener(raw).nextValue();
                    if(!(v instanceof String)) throw new Exception("无法读取当前页面，请刷新后重试。");
                    JSONObject result=new JSONObject((String)v);
                    if(result.has("error")) throw new Exception(result.getString("error"));
                    result.put("kind",kind);
                    if(!getSharedPreferences("timetable",MODE_PRIVATE).edit().putString("capture",result.toString()).commit()) throw new Exception("读取结果保存失败，请检查存储空间。");
                    setResult(RESULT_OK); finish();
                } catch(Exception e) { status.setText(e.getMessage()==null ? "读取失败，请重试。" : e.getMessage()); }
            });
            handler.postDelayed(()-> {
                if(reading && epoch==navigation) { reading=false; read.setEnabled(true); status.setText("读取超时，请等待页面加载后重试。"); }
            },12000);
        } catch(IOException e) { reading=false; read.setEnabled(true); status.setText("读取组件缺失，请重新安装。"); }
    }
    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else finish();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); web.stopLoading(); web.destroy(); super.onDestroy(); }
}
