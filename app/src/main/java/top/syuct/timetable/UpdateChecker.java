package top.syuct.timetable;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Called only by the settings button. No accounts, timetable, WebView cookies or device IDs. */
public final class UpdateChecker {
    public static JSONObject check(long installed) throws IOException,JSONException {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(UpdatePolicy.MANIFEST+"?check="+System.currentTimeMillis()).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        connection.setUseCaches(false);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Cache-Control","no-cache, no-store");
        connection.setRequestProperty("User-Agent","SYUCT-Timetable-Update");
        java.util.Timer deadline=new java.util.Timer("syuct-update-timeout",true);
        deadline.schedule(new java.util.TimerTask(){public void run(){connection.disconnect();}},16000);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("无法获取版本信息");
            if(connection.getContentLengthLong()>UpdatePolicy.MAX_BYTES)throw new IOException("版本文件过大");
            try(InputStream input=connection.getInputStream()){
                return parse(new String(UpdatePolicy.read(input),StandardCharsets.UTF_8),installed);
            }
        }finally{deadline.cancel();connection.disconnect();}
    }
    static JSONObject parse(String text,long installed) throws JSONException {
        JSONObject data=new JSONObject(text);
        if(UpdatePolicy.version(data.get("schemaVersion"))!=1 || !UpdatePolicy.APPLICATION.equals(data.get("applicationId")))throw new JSONException("版本文件不匹配");
        int code=UpdatePolicy.version(data.get("versionCode"));
        Object name=data.get("versionName"),url=data.get("apkUrl"),notes=data.opt("notes");
        if(!(name instanceof String)||!((String)name).matches("[0-9][0-9A-Za-z.+-]{0,39}")||!(url instanceof String)||!UpdatePolicy.download((String)url))throw new JSONException("版本文件无效");
        if(notes!=null&&(!(notes instanceof String)||((String)notes).length()>1000))throw new JSONException("更新说明无效");
        return new JSONObject().put("status",UpdatePolicy.newer(installed,code)?"available":"current")
            .put("versionCode",code).put("versionName",name).put("notes",notes==null?"":notes);
    }
    private UpdateChecker(){}
}
