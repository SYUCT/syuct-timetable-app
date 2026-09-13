import top.syuct.timetable.UpdatePolicy;
import java.io.*;
public class UpdatePolicyTest {
  static int count=0;
  static void check(boolean ok){if(!ok)throw new AssertionError("Update policy failed");count++;}
  public static void main(String[] args)throws Exception{
    check(UpdatePolicy.newer(5,6));check(!UpdatePolicy.newer(6,6));check(!UpdatePolicy.newer(7,6));
    check(UpdatePolicy.version(6)==6);check(UpdatePolicy.version(2147483647L)==2147483647);
    for(Object value:new Object[]{0,-1,2147483648L,6.0,"6",true,null}){try{UpdatePolicy.version(value);throw new AssertionError("Accepted invalid version");}catch(IllegalArgumentException e){count++;}}
    check(UpdatePolicy.download(UpdatePolicy.APK));
    for(String value:new String[]{"http://www.syuct.top/downloads/SYUCT-Timetable.apk","https://www.syuct.top.evil.test/downloads/SYUCT-Timetable.apk","https://www.syuct.top@evil.test/downloads/SYUCT-Timetable.apk","javascript:alert(1)","intent://anything","file:///sdcard/a.apk",UpdatePolicy.APK+"?redirect=evil","https://www.syuct.top/other.apk",null})check(!UpdatePolicy.download(value));
    check(UpdatePolicy.downloadFor(6).equals(UpdatePolicy.APK+"?version=6"));
    check(UpdatePolicy.read(new ByteArrayInputStream(new byte[32768])).length==32768);
    try{UpdatePolicy.read(new ByteArrayInputStream(new byte[32769]));throw new AssertionError("Accepted oversized response");}catch(IOException e){count++;}
    System.out.println("PASS "+count+" manual update policy checks");
  }
}
