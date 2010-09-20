package net.nowatch.utils;

import net.nowatch.Main;
import net.nowatch.service.NotifService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class Notify {

    private static final String TAG = Main.TAG + "Prefs";
    private final int REQUEST = 0;
    private AlarmManager am;
    private PendingIntent pi;

    public Notify(final Context ctxt) {
        am = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctxt, NotifService.class);
        i.setAction(NotifService.ACTION_UPDATE);
        pi = PendingIntent.getService(ctxt, REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void startNotification(final long interval) {
        Log.v(TAG, "start notification every " + interval + "ms");
        cancelNotification();
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                interval, pi);
    }

    public void cancelNotification() {
        am.cancel(pi);
    }

}
