package nowatch.tv;

import android.content.Context;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceScreen;

public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private Context ctxt;
    private PreferenceScreen ps;

    public final static String KEY_MOBILE_TRAFFIC = "mobile_traffic";
    public final static boolean DEFAULT_MOBILE_TRAFFIC = false;

    public final static String KEY_NOTIFICATION = "notification";
    public final static boolean DEFAULT_NOTIFICATION = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ctxt = getApplicationContext();
        ps = getPreferenceScreen();
        ps.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if(key.equals(KEY_NOTIFICATION)){
            Notification notif = new Notification(ctxt);
            CheckBoxPreference cb = (CheckBoxPreference) ps.findPreference(KEY_NOTIFICATION);
            if(cb.isChecked()){
               notif.startNotification(); 
            } else {
               notif.cancelNotification(); 
            }
        }
    }
}
