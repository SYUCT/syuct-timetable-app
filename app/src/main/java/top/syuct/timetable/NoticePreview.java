package top.syuct.timetable;

import android.app.*;
import android.content.*;

/** Borrow course presentation only. Synthetic time never touches timetable/sent state. */
final class NoticePreview {
    final String name,room,teacher;
    final boolean borrowed;
    NoticePreview(String name,String room,String teacher,boolean borrowed){this.name=name;this.room=room;this.teacher=teacher;this.borrowed=borrowed;}
    static NoticePreview sample(Context c){
        long now=System.currentTimeMillis();
        for(ReminderPlanner.Event e:CourseReminder.events(c))if(e.start>now)return new NoticePreview(e.course.name,e.course.room,e.course.teacher,true);
        return new NoticePreview("现代设计方法","示例教室","示例教师",false);
    }
    String source(){return borrowed?"使用下一门课的名称、教师与教室，仅演示，不改变课程时间。":"示例课程，仅用于演示，不会加入你的课表。";}
    String location(){return room==null||room.trim().isEmpty()?"地点待定":room.trim();}
    Notification.Builder builder(Context c,long start,boolean countdown){
        PendingIntent pi=CourseNoticeDetails.open(c,countdown?LiveCourseNotice.PREVIEW:"reminder_test",name,start,teacher,room,true);
        return new Notification.Builder(c,CourseReminder.CHANNEL)
            .setContentTitle(name).setSubText("效果预览").setContentText(CourseNoticeStyle.summary(start,teacher,room))
            .setStyle(CourseNoticeStyle.rows(start,teacher,room).setBigContentTitle(name))
            .addAction(new Notification.Action.Builder(null,"查看详情",pi).build())
            .setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(CourseNoticeStyle.apply(c,new Notification.Builder(c,CourseReminder.CHANNEL)).setContentTitle("提醒效果预览").setContentText("解锁查看预览内容").build())
            .setTimeoutAfter(countdown?ReminderPlanner.LEAD:60000);
    }
}
