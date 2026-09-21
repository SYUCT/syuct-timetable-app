package top.syuct.timetable;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Standard system template with the same badge and accent as the packaged UI. */
final class CourseNoticeStyle {
    static final int BLUE=0xff3463c9;
    private static Icon small;
    private CourseNoticeStyle(){}
    static synchronized Icon smallIcon(Context context){
        if(small!=null)return small;
        Bitmap source=BitmapFactory.decodeResource(context.getResources(),R.drawable.campus_badge);
        Bitmap scaled=Bitmap.createScaledBitmap(source,96,96,true);
        int[] pixels=new int[96*96];scaled.getPixels(pixels,0,96,0,0,96,96);
        // System small icons are alpha masks, not colour images. Preserve the white
        // paper/ring as the foreground instead of inverting the dark artwork.
        // Full-colour OEM templates must use badgeIcon(), never this fallback mask.
        for(int i=0;i<pixels.length;i++){
            int p=pixels[i],luma=(Color.red(p)*54+Color.green(p)*183+Color.blue(p)*19)/256;
            int strength=Math.max(0,Math.min(255,(luma-40)*255/200));
            pixels[i]=Color.argb(Color.alpha(p)*strength/255,255,255,255);
        }
        Bitmap mask=Bitmap.createBitmap(pixels,96,96,Bitmap.Config.ARGB_8888);
        small=Icon.createWithBitmap(mask);
        if(scaled!=source)scaled.recycle();source.recycle();
        return small;
    }
    static Icon badgeIcon(Context context){return Icon.createWithResource(context,R.drawable.campus_badge);}
    static Notification.Builder apply(Context context,Notification.Builder builder){
        builder.setSmallIcon(smallIcon(context))
            .setLargeIcon(badgeIcon(context))
            .setColor(BLUE).setColorized(false);
        if(XiaomiIsland.device())applyXiaomiIcon(context,builder);
        return builder;
    }
    // MIUI compatibility hint, not an Android colour-icon guarantee. Keep it
    // Xiaomi-only; the official focus template also uses the original resource.
    static void applyXiaomiIcon(Context context,Notification.Builder builder){
        android.os.Bundle extras=new android.os.Bundle();extras.putBoolean("miui.isGrayscaleIcon",false);
        builder.setSmallIcon(badgeIcon(context)).addExtras(extras);
    }
    static String time(long start){return Instant.ofEpochMilli(start).atZone(LessonClock.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));}
    static String compactTitle(String name){
        String text=name==null?"":name.replaceAll("\\s+","").trim();
        if(text.isEmpty())return "待上课";
        return text.substring(0,text.offsetByCodePoints(0,Math.min(3,text.codePointCount(0,text.length()))));
    }
    static String title(String name){
        String text=name==null?"":name.replaceAll("\\s+"," ").trim();
        if(text.isEmpty())return "即将上课";
        return text.codePointCount(0,text.length())<=8?text:text.substring(0,text.offsetByCodePoints(0,7))+"…";
    }
    static String field(String value){return value==null||value.trim().isEmpty()?"未提供":value.trim();}
    static String details(long start,String teacher,String room){return "开课时间："+time(start)+"\n授课教师："+field(teacher)+"\n上课教室："+field(room);}
}
