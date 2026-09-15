package top.syuct.timetable;
import android.content.*;
public final class ExactDeliveryProbe extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        long late=System.currentTimeMillis()-i.getLongExtra("due",0);
        c.getSharedPreferences("exact_qa",0).edit().putLong("late",late).putBoolean("delivered",true).commit();
        android.util.Log.i("NativeExactProbe","DELIVERED idle exact lateMs="+late);
    }
}
