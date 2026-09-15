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
        // Small notification icons are alpha masks: a full-colour opaque image would
        // become a white disc. Retain the badge's dark artwork as a monochrome stencil.
        for(int i=0;i<pixels.length;i++){
            int p=pixels[i],luma=(Color.red(p)*54+Color.green(p)*183+Color.blue(p)*19)/256;
            int strength=Math.max(0,Math.min(255,(215-luma)*3));
            pixels[i]=Color.argb(Color.alpha(p)*strength/255,255,255,255);
        }
        Bitmap mask=Bitmap.createBitmap(pixels,96,96,Bitmap.Config.ARGB_8888);
        small=Icon.createWithBitmap(mask);
        if(scaled!=source)scaled.recycle();source.recycle();
        return small;
    }
    static Notification.Builder apply(Context context,Notification.Builder builder){
        return builder.setSmallIcon(smallIcon(context))
            .setLargeIcon(Icon.createWithResource(context,R.drawable.campus_badge))
            .setColor(BLUE).setColorized(false);
    }
    static String time(long start){return Instant.ofEpochMilli(start).atZone(LessonClock.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));}
    static String title(String name){
        String text=name==null?"":name.replaceAll("\\s+"," ").trim();
        if(text.isEmpty())return "即将上课";
        return text.codePointCount(0,text.length())<=8?text:text.substring(0,text.offsetByCodePoints(0,7))+"…";
    }
    static String details(long start,String room){return "开课时间："+time(start)+"\n课程地点："+(room==null||room.trim().isEmpty()?"待定":room.trim());}
}
