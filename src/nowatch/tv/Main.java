package nowatch.tv;

// http://developer.android.com/intl/fr/guide/appendix/media-formats.html

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class Main extends Activity {

    // private final String TAG = "NowatchTV";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView(R.layout.main);
        startActivity(new Intent(getApplicationContext(), ItemsActivity.class));
        finish();
    }
}
