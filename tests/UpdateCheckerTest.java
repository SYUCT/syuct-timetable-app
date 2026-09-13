package top.syuct.timetable;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Test-only JSON library plus a fake HTTPS connection: no real network or Android account. */
public class UpdateCheckerTest {
    static int assertions,connections,status=200;static String payload;static Fake latest;
    static void check(boolean ok){if(!ok)throw new AssertionError("Update checker failed");assertions++;}
    static JSONObject valid()throws Exception{return new JSONObject().put("schemaVersion",1).put("applicationId",UpdatePolicy.APPLICATION).put("versionCode",6).put("versionName","0.2.4-alpha1").put("apkUrl",UpdatePolicy.APK).put("notes","手动检测更新");}
    static void invalid(String text)throws Exception{try{UpdateChecker.parse(text,5);throw new AssertionError("Accepted malformed manifest");}catch(JSONException|IllegalArgumentException expected){assertions++;}}
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){connections++;return latest=new Fake(url);}}:null);
        check(connections==0);
        check(UpdateChecker.parse(valid().toString(),5).getString("status").equals("available"));
        check(UpdateChecker.parse(valid().toString(),6).getString("status").equals("current"));
        check(UpdateChecker.parse(valid().toString(),7).getString("status").equals("current"));
        for(String field:new String[]{"schemaVersion","applicationId","versionCode","versionName","apkUrl"}){JSONObject o=valid();o.remove(field);invalid(o.toString());}
        invalid("<html>error</html>");invalid("[]");invalid(valid().put("schemaVersion",2).toString());
        invalid(valid().put("applicationId","another.app").toString());invalid(valid().put("versionCode","7").toString());
        invalid(valid().put("versionCode",7.5).toString());invalid(valid().put("versionCode",-1).toString());
        invalid(valid().put("apkUrl","intent://evil").toString());invalid(valid().put("versionName","<img>").toString());
        invalid(valid().put("notes",new JSONArray()).toString());invalid(valid().put("notes","x".repeat(1001)).toString());
        check(UpdateChecker.parse(valid().put("notes","<img src=x>").toString(),5).getString("notes").equals("<img src=x>"));
        payload=valid().toString();check(UpdateChecker.check(5).getString("status").equals("available"));
        check(connections==1&&latest.disconnected);check(latest.getURL().toString().startsWith(UpdatePolicy.MANIFEST+"?check="));
        check(!latest.getInstanceFollowRedirects()&&!latest.getUseCaches());check(latest.getConnectTimeout()==8000&&latest.getReadTimeout()==8000);
        check(latest.getRequestProperty("Cookie")==null&&latest.getRequestProperty("Authorization")==null);
        check(latest.getRequestProperty("Cache-Control").contains("no-cache"));
        for(int response:new int[]{301,302,404,500}){status=response;try{UpdateChecker.check(5);throw new AssertionError("Accepted HTTP status");}catch(IOException e){check(latest.disconnected);}}
        status=200;payload="x".repeat(32769);try{UpdateChecker.check(5);throw new AssertionError("Accepted large body");}catch(IOException e){check(latest.disconnected);}
        System.out.println("PASS "+assertions+" native update manifest/transport checks");
    }
    static class Fake extends HttpsURLConnection{
        boolean disconnected;
        Fake(URL url){super(url);}
        public int getResponseCode(){return status;}
        public InputStream getInputStream(){return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));}
        public long getContentLengthLong(){return -1;}
        public void disconnect(){disconnected=true;}
        public boolean usingProxy(){return false;}
        public void connect(){}
        public String getCipherSuite(){return "test";}
        public java.security.cert.Certificate[] getLocalCertificates(){return null;}
        public java.security.cert.Certificate[] getServerCertificates(){return null;}
    }
}
