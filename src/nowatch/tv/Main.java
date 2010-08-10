package nowatch.tv;

// http://developer.android.com/intl/fr/guide/appendix/media-formats.html
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class Main extends Activity {

    private final static String TAG = "NWTV:";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(Main.this, DownloadService.class));
        startActivity(new Intent(Main.this, ItemsActivity.class));
        finish();
    }
}
