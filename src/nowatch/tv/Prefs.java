package nowatch.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private Context ctxt;
    private PreferenceScreen ps;
    // private SharedPreferences prefs;

    public final static String KEY_MOBILE_TRAFFIC = "mobile_traffic";
    public final static boolean DEFAULT_MOBILE_TRAFFIC = false;

    public final static String KEY_NOTIFICATION = "notification";
    public final static boolean DEFAULT_NOTIFICATION = false;

    public final static String KEY_NOTIFICATION_INTV = "notification_interval";
    public final static long DEFAULT_NOTIFICATION_INTV = 60000; // [ms]

    public final static String KEY_SIMULTANEOUS_DL = "simultaneous_download";
    public final static int DEFAULT_SIMULTANEOUS_DL = 3;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ctxt = getApplicationContext();
        ps = getPreferenceScreen();
        ps.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        // prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(KEY_NOTIFICATION)) {
            UpdateNotification notif = new UpdateNotification(ctxt);
            CheckBoxPreference cb = (CheckBoxPreference) ps.findPreference(KEY_NOTIFICATION);
            if (cb.isChecked()) {
                notif.startNotification(prefs.getLong(KEY_NOTIFICATION_INTV,
                        DEFAULT_NOTIFICATION_INTV));
            } else {
                notif.cancelNotification();
            }
        }
    }
}
