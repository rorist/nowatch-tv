package net.nowatch;

// http://developer.android.com/intl/fr/guide/appendix/media-formats.html
// TODO: le flux de TOM contient une image redirigée en 301, notifier Cedric

import net.nowatch.ui.ListItems;
import net.nowatch.utils.Prefs;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;

public class Main extends Activity {

    public static final String TAG = "NWTV:";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        // Save some values in prefs
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Editor edit = prefs.edit();
        edit.putInt(Prefs.KEY_DENSITY, dm.densityDpi);
        edit.commit();
        // Start list activity
        startActivity(new Intent(Main.this, ListItems.class));
        finish();
    }
}
