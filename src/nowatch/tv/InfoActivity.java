package nowatch.tv;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.TextView;

public class InfoActivity extends Activity {

    // private final String TAG = "InfoActivity";
    private final String REQ = "SELECT feeds.title, items.title, items.description, "
            + "items.link, feeds.link, image, file_uri, file_size, file_type "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private final int IMG_DIP = 64;
    private DisplayMetrics displayMetrics;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_activity);

        // Screen metrics (for dip to px conversion)
        displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Get item information
        Bundle extra = getIntent().getExtras();
        SQLiteDatabase db = (new DB(getApplicationContext())).getWritableDatabase();
        Cursor c = db.rawQuery(REQ + extra.getLong("item_id"), null);
        c.moveToFirst();
        ((TextView) findViewById(R.id.title)).setText(c.getString(1));
        ((TextView) findViewById(R.id.desc)).setText(c.getString(2));
        ((TextView) findViewById(R.id.link)).setText(c.getString(3));
        ImageView logo = (ImageView) findViewById(R.id.logo);
        byte[] logo_byte = c.getBlob(5);
        if (logo_byte != null && logo_byte.length > 200) {
            logo.setImageBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_byte,
                    0, logo_byte.length), (int) (IMG_DIP * displayMetrics.density + 0.5f),
                    (int) (IMG_DIP * displayMetrics.density + 0.5f), true));
        } else {
            logo.setImageResource(R.drawable.icon);
        }
        c.close();
        db.close();
    }
}
