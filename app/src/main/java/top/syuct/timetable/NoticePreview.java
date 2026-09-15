package top.syuct.timetable;

import android.app.*;
import android.content.*;

/** Borrow course presentation only. Synthetic time never touches timetable/sent state. */
final class NoticePreview {
    final String name,room;
    final boolean borrowed;
    NoticePreview(String name,String room,boolean borrowed){this.name=name;this.room=room;this.borrowed=borrowed;}
    static NoticePreview sample(Context c){
        long now=System.currentTimeMillis();
        for(ReminderPlanner.Event e:CourseReminder.events(c))if(e.start>now)return new NoticePreview(e.course.name,e.course.room,true);
        return new NoticePreview("现代设计方法","瑞师楼222",false);
    }
    String source(){return borrowed?"使用下一门课的名称与地点，仅演示，不改变课程时间。":"示例课程，仅用于演示，不会加入你的课表。";}
    String location(){return room==null||room.trim().isEmpty()?"地点待定":room.trim();}
    Notification.Builder builder(Context c,long start,boolean countdown){
        Intent open=new Intent(c,MainActivity.class).setAction(TodayWidget.OPEN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,173,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String detail=countdown?"演示开课："+CourseNoticeStyle.time(start)+"（3分钟后结束）":"通知样式预览 · 非正式上课提醒";
        return new Notification.Builder(c,CourseReminder.CHANNEL)
            .setContentTitle(CourseNoticeStyle.title("预览·"+name)).setContentText(location()+" · "+(countdown?"3分钟效果预览":"通知效果预览"))
            .setStyle(new Notification.BigTextStyle().setBigContentTitle("效果预览｜"+name)
                .bigText(detail+"\n课程地点："+location()+"\n"+(countdown?"倒计时为演示时间，不是实际课程时间。":"声音遵循手机静音、勿扰和通知设置。")))
            .setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(CourseNoticeStyle.apply(c,new Notification.Builder(c,CourseReminder.CHANNEL)).setContentTitle("提醒效果预览").setContentText("解锁查看预览内容").build())
            .setTimeoutAfter(countdown?180000:60000);
    }
}
