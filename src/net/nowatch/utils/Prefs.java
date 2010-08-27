package net.nowatch.utils;

import net.nowatch.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;

public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private Context ctxt;
    private PreferenceScreen ps;

    public final static String KEY_MOBILE_TRAFFIC = "mobile_traffic";
    public final static boolean DEFAULT_MOBILE_TRAFFIC = false;

    public final static String KEY_AUTO_DL = "auto_dl";
    public final static boolean DEFAULT_AUTO_DL = false;

    public final static String KEY_NOTIFICATION = "notification";
    public final static boolean DEFAULT_NOTIFICATION = false;

    public final static String KEY_NOTIFICATION_INTV = "notification_interval";
    public final static long DEFAULT_NOTIFICATION_INTV = 60000; // [ms]

    public final static String KEY_SIMULTANEOUS_DL = "simultaneous_download";
    public final static String DEFAULT_SIMULTANEOUS_DL = "3";
    
    public final static String KEY_DENSITY = "density";
    public final static int DEFAULT_DENSITY = DisplayMetrics.DENSITY_DEFAULT;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ctxt = getApplicationContext();
        ps = getPreferenceScreen();
        ps.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (KEY_NOTIFICATION.equals(key)) {
            Notify notif = new Notify(ctxt);
            CheckBoxPreference cb = (CheckBoxPreference) ps.findPreference(key);
            if (cb.isChecked()) {
                notif.startNotification(prefs.getLong(KEY_NOTIFICATION_INTV,
                        DEFAULT_NOTIFICATION_INTV));
            } else {
                notif.cancelNotification();
            }
        } else if (KEY_SIMULTANEOUS_DL.equals(key)) {
            try {
                Integer.parseInt(prefs.getString(key, DEFAULT_SIMULTANEOUS_DL));
            } catch (NumberFormatException e) {
                ((EditTextPreference) ps.findPreference(key)).setText(DEFAULT_SIMULTANEOUS_DL);
            }
        }
    }
}
