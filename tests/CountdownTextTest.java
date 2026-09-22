package top.syuct.timetable;
public final class CountdownTextTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args){
        check(CountdownText.label(900000,0).equals("剩余15分钟"));
        check(CountdownText.label(900000,300000).equals("剩余10分钟"));
        check(CountdownText.label(60000,0).equals("剩余1分钟"));
        check(CountdownText.label(60001,0).equals("剩余2分钟"));
        check(CountdownText.label(1,0).equals("剩余1分钟"));
        check(CountdownText.label(0,0).equals("即将上课"));
        check(CountdownText.next(0,0)==0&&CountdownText.next(0,1)==0);
        for(long remaining=1;remaining<=900000;remaining+=173){
            long now=900000-remaining,next=CountdownText.next(900000,now);
            check(next>now&&next<=900000&&next-now<=60000);
            check(!CountdownText.label(900000,now).equals(CountdownText.label(900000,next)));
        }
        long tick=0;int updates=0;while(tick<900000){tick=CountdownText.next(900000,tick);updates++;}
        check(updates==15);
        System.out.println("PASS "+checks+" countdown boundaries; at most 15 updates");
    }
}
