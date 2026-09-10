import top.syuct.timetable.Policy;
public class PolicyTest {
  static void check(boolean ok){if(!ok)throw new AssertionError("URL policy failed");}
  public static void main(String[] args){
    check(Policy.school("https://jws.syuct.edu.cn/(session)/xs_main.aspx?xh=demo","jws.syuct.edu.cn"));
    check(Policy.school("https://geims.syuct.edu.cn:443/MainFrame.htm","geims.syuct.edu.cn"));
    for(String u:new String[]{"http://jws.syuct.edu.cn/","https://jws.syuct.edu.cn.evil.test/","https://evil.test/?jws.syuct.edu.cn","javascript:alert(1)","file:///sdcard/test.html","https://user@jws.syuct.edu.cn/","https://jws.syuct.edu.cn:8443/","https://geims.syuct.edu.cn/"})check(!Policy.school(u,"jws.syuct.edu.cn"));
    check(Policy.local("https://appassets.androidplatform.net/index.html"));
    check(!Policy.local("https://appassets.androidplatform.net.evil.test/index.html"));
    check(!Policy.local("file:///android_asset/index.html"));
    check(!Policy.school("https://evil.test/","evil.test"));
    System.out.println("PASS 14 native URL policy checks");
  }
}
