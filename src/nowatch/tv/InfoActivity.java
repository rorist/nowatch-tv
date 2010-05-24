package nowatch.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends Activity {

    private final String TAG = "InfoActivity";
    private final String REQ = "SELECT feeds.title, items.title, items.description, "
            + "items.link, feeds.link, image, file_uri, file_size, file_type "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private final String STYLE = "<style>*{color: black;}</style>";
    private final String PRE = "<meta http-equiv=\"Content-Type\" content=\"application/xhtml+text; charset=UTF-8\"/>"
            + STYLE;
    private final int IMG_DIP = 64;
    private DisplayMetrics displayMetrics;
    final Context ctxt = this;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_activity);

        // Screen metrics (for dip to px conversion)
        displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Get item information
        Bundle extra = getIntent().getExtras();
        final int item_id = extra.getInt("item_id");
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        Cursor c = db.rawQuery(REQ + item_id, null);
        c.moveToFirst();
        final String title = c.getString(1);
        ((TextView) findViewById(R.id.title)).setText(title);
        ((WebView) findViewById(R.id.desc)).loadData(PRE + c.getString(2), "text/html", "utf-8");
        ((WebView) findViewById(R.id.desc)).setBackgroundColor(0);
        // ((TextView) findViewById(R.id.link)).setText(c.getString(3));
        ImageView logo = (ImageView) findViewById(R.id.logo);
        byte[] logo_byte = c.getBlob(5);
        if (logo_byte != null && logo_byte.length > 200) {
            logo.setImageBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_byte,
                    0, logo_byte.length), (int) (IMG_DIP * displayMetrics.density + 0.5f),
                    (int) (IMG_DIP * displayMetrics.density + 0.5f), true));
        } else {
            logo.setImageResource(R.drawable.icon);
        }

        // File
        final String file_uri = c.getString(6);
        // final String file_size = c.getString(7);
        final String file_type = c.getString(8);

        // Close db
        c.close();
        db.close();

        // Set buttons
        ((ImageButton) findViewById(R.id.btn_logo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        ((Button) findViewById(R.id.btn_play)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                try {
                    i.setDataAndType(Uri.parse(file_uri), file_type);
                    startActivity(i);
                    changeStatus(item_id, Feed.STATUS_READ);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, e.getMessage());
                    try {
                        i.setType("video/*");
                        startActivity(i);
                        changeStatus(item_id, Feed.STATUS_READ);
                    } catch (ActivityNotFoundException e1) {
                        Log.e(TAG, e1.getMessage());
                        Toast.makeText(ctxt, R.string.toast_notsupported, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        final Intent i = new Intent(this, DownloadService.class);
        i.putExtra("item_id", item_id);
        ((Button) findViewById(R.id.btn_download)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                changeStatus(item_id, Feed.STATUS_DOWNLOADING);
                startService(i);
            }
        });
    }

    private void changeStatus(int id, int status) {
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        ContentValues value = new ContentValues();
        value.put("status", status);
        db.update("items", value, "_id=?", new String[] { id + "" });
        db.close();
    }
}
