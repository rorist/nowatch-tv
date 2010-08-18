package nowatch.tv;

// http://developer.android.com/intl/fr/guide/appendix/media-formats.html
// TODO: le flux de TOM contient une image redirigée en 301, notifier Cedric

import nowatch.tv.ui.ListItems;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class Main extends Activity {

    public static final String TAG = "NWTV:";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //startService(new Intent(Main.this, DownloadService.class));
        startActivity(new Intent(Main.this, ListItems.class));
        finish();
    }
}
