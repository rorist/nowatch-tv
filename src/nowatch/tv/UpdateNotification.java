package nowatch.tv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class UpdateNotification {

    private final int REQUEST = 0;
    private AlarmManager am;
    private PendingIntent pi;

    public UpdateNotification(final Context ctxt) {
        am = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctxt, DownloadService.class);
        i.setAction(DownloadService.ACTION_UPDATE);
        pi = PendingIntent.getService(ctxt, REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void startNotification(final long interval) {
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                interval, pi);
    }

    public void cancelNotification() {
        am.cancel(pi);
    }

}
