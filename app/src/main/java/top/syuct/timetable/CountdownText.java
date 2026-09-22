package top.syuct.timetable;

/** Minute precision fallback; never pretend a static string is a ticking second timer. */
final class CountdownText {
    static String label(long start,long now){
        long remaining=Math.max(0,start-now);
        return remaining==0?"即将上课":"剩余"+((remaining+59999)/60000)+"分钟";
    }
    static long next(long start,long now){
        long remaining=start-now;
        return remaining<=0?0:now+((remaining-1)%60000)+1;
    }
}
