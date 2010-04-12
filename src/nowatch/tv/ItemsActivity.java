package nowatch.tv;

import android.app.Activity;
import android.os.Bundle;

public class ItemsActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        new Thread(new UpdateRunnable(getApplicationContext(), R.string.feed_scuds, "3")).start();
    }
}
