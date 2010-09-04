package net.nowatch.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctxt, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
            if (prefs.getBoolean(Prefs.KEY_NOTIFICATION, Prefs.DEFAULT_NOTIFICATION)) {
                new Notify(ctxt).startNotification(Long.parseLong(prefs.getString(
                        Prefs.KEY_NOTIFICATION_INTV, Prefs.DEFAULT_NOTIFICATION_INTV)));
            }
        }
    }

}
