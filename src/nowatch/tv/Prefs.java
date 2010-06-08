/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package nowatch.tv;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    public final static String KEY_MOBILE_TRAFFIC = "mobile_traffic";
    public final static boolean DEFAULT_MOBILE_TRAFFIC = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    }
}
