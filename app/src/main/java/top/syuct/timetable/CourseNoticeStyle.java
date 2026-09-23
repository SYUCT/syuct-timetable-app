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
    static final String LINE_BREAK="\n";
    private static Icon small;
    private CourseNoticeStyle(){}
    static synchronized Icon smallIcon(Context context){
        if(small!=null)return small;
        Bitmap source=BitmapFactory.decodeResource(context.getResources(),R.drawable.campus_badge);
        Bitmap scaled=Bitmap.createScaledBitmap(source,96,96,true);
        int[] pixels=new int[96*96];scaled.getPixels(pixels,0,96,0,0,96,96);
        // System small icons are alpha masks, not colour images. Preserve the white
        // paper/ring as the foreground instead of inverting the dark artwork.
        // Colour-capable surfaces use badgeIcon(), never this fallback mask.
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
        if(NoticeCompat.xiaomi())applyXiaomiIcon(context,builder);
        return builder;
    }
    // MIUI compatibility hint, not an Android colour-icon guarantee.
    static void applyXiaomiIcon(Context context,Notification.Builder builder){
        android.os.Bundle extras=new android.os.Bundle();extras.putBoolean("miui.isGrayscaleIcon",false);
        builder.setSmallIcon(badgeIcon(context)).addExtras(extras);
    }
    static String time(long start){return Instant.ofEpochMilli(start).atZone(LessonClock.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));}
    static String field(String value){return value==null||value.trim().isEmpty()?"未提供":value.trim();}
    static String chipName(String name){
        String text=name==null?"":name.replaceAll("[\\s\\u00a0\\u3000]+","");
        if(text.isEmpty())return "课程提醒";
        return text.substring(0,text.offsetByCodePoints(0,Math.min(4,text.codePointCount(0,text.length()))));
    }
    static Notification.Builder chip(Notification.Builder builder,String name){
        // The promoted chip has a separate short-text field. Never truncate
        // EXTRA_TITLE: the same notification also appears in the shade.
        if(android.os.Build.VERSION.SDK_INT>=36)builder.setShortCriticalText(chipName(name));
        return builder;
    }
    static String compactRoom(String room){return field(room).replaceAll("[（(]原[^）)]*[）)]"," ").replaceAll("\\s+"," ").trim();}
    static String summary(long start,String teacher,String room){
        return "教室："+compactRoom(room)+LINE_BREAK
            +"开课时间："+time(start)+LINE_BREAK
            +"授课教师："+field(teacher);
    }
    static Notification.InboxStyle rows(long start,String teacher,String room){
        return new Notification.InboxStyle()
            .addLine("教室："+compactRoom(room))
            .addLine("开课时间："+time(start))
            .addLine("授课教师："+field(teacher));
    }
    // Promoted live updates require a supported system template. BigTextStyle
    // preserves real newline characters in its expanded detail text. Put the
    // classroom first because some OEM cards reveal only the first detail row.
    static String liveDetails(long start,String teacher,String room){
        return "课程地点："+field(room)+LINE_BREAK
            +"开课时间："+time(start)+LINE_BREAK
            +"授课教师："+field(teacher);
    }
}
