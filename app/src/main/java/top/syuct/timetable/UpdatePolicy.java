package top.syuct.timetable;

/** Only our published Android package may be offered; never trust a remote intent URL. */
public final class UpdatePolicy {
    public static final String MANIFEST = "https://www.syuct.top/downloads/app-version.json";
    public static final String APK = "https://www.syuct.top/downloads/SYUCT-Timetable.apk";
    public static final String APPLICATION = "top.syuct.timetable";
    public static final int MAX_BYTES = 32768;
    public static int version(Object value) {
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException("版本号无效");
        long n=((Number)value).longValue();
        if(n<1 || n>Integer.MAX_VALUE)throw new IllegalArgumentException("版本号超出范围");
        return (int)n;
    }
    public static boolean download(String value){return APK.equals(value);}
    public static boolean newer(long installed,int remote){return remote>installed;}
    public static String downloadFor(int version){return APK+"?version="+version;}
    public static byte[] read(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[2048];int n;
        while((n=input.read(buffer))!=-1){
            if(out.size()+n>MAX_BYTES)throw new java.io.IOException("版本文件过大");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
    private UpdatePolicy(){}
}
