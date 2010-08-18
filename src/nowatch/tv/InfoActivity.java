package nowatch.tv;

// TODO: Do not bind to service, just send IntentService

import java.io.File;

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
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends Activity {

    private final String TAG = Main.TAG + "InfoActivity";
    private final String REQ = "SELECT feeds.title, items.title, items.description, "
            + "items.link, feeds.link, image, file_uri, file_size, file_type, items.status "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private final String STYLE = "<style>*{color: black;}</style>";
    private final String PRE = "<meta http-equiv=\"Content-Type\" content=\"application/xhtml+text; charset=UTF-8\"/>"
            + STYLE;
    private final Context ctxt = this;
    private final int IMG_DIP = 96;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_activity);

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int image_size = (int) (IMG_DIP * dm.density + 0.5f);

        // Get item information
        Bundle extra = getIntent().getExtras();
        final int item_id = extra.getInt(Item.EXTRA_ITEM_ID);
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        Cursor c = db.rawQuery(REQ + item_id, null);
        c.moveToFirst();
        final String title = c.getString(1);
        ((TextView) findViewById(R.id.title)).setText(title);
        ((WebView) findViewById(R.id.desc)).loadData(PRE + c.getString(2), "text/html", "utf-8");
        ((WebView) findViewById(R.id.desc)).setBackgroundColor(0);
        ImageView logo = (ImageView) findViewById(R.id.logo);
        byte[] logo_byte = c.getBlob(5);
        if (logo_byte != null && logo_byte.length > 200) {
            logo.setImageBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_byte,
                    0, logo_byte.length), image_size, image_size, true));
        } else {
            logo.setImageResource(R.drawable.icon);
        }
        // File
        final String file_uri = c.getString(6);
        final String file_type = c.getString(8);
        // final String file_size = c.getString(7);
        final int status = c.getInt(9);

        // Close db
        c.close();
        db.close();

        // Set status
        if (status == Item.STATUS_NEW) {
            changeStatus(ctxt, item_id, Item.STATUS_UNREAD);
        }

        // Menu buttons
        findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_play).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_download).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        // Buttons
        ((ImageButton) findViewById(R.id.btn_logo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        if (status == Item.STATUS_DOWNLOADING) {
            changeButton(R.id.btn_download, getString(R.string.btn_download), false, null);
            changeButton(R.id.btn_play, getString(R.string.btn_stream), true,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            // Stream the file
                            if (new Network(ctxt).isMobileAllowed()) {
                                viewVideo(file_uri, file_type, item_id);
                            }
                        }
                    });
        } else if (status == Item.STATUS_DL_READ || status == Item.STATUS_DL_UNREAD) {
            changeButton(R.id.btn_download, getString(R.string.btn_download), false, null);
            changeButton(R.id.btn_play, getString(R.string.btn_play), true,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            // Read the local file
                            viewVideo(GetFile.PATH_PODCASTS + "/" + new File(file_uri).getName(),
                                    file_type, item_id);
                        }
                    });
        } else {
            changeButton(R.id.btn_play, getString(R.string.btn_stream), true,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            // Stream the file
                            if (new Network(ctxt).isMobileAllowed()) {
                                viewVideo(file_uri, file_type, item_id);
                            }
                        }
                    });
            changeButton(R.id.btn_download, getString(R.string.btn_download), true,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            // Download the file
                            if (new Network(ctxt).isMobileAllowed()) {
                                downloadVideo(item_id);
                            }
                        }
                    });
        }
    }

    public static void changeStatus(Context ctxt, int id, int status) {
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        ContentValues value = new ContentValues();
        value.put("status", status);
        db.update("items", value, "_id=?", new String[] { id + "" });
        db.close();
    }

    private void downloadVideo(int item_id) {
        changeStatus(ctxt, item_id, Item.STATUS_DOWNLOADING);
        Intent intent = new Intent(InfoActivity.this, DownloadService.class);
        intent.setAction(DownloadService.ACTION_ADD);
        intent.putExtra(Item.EXTRA_ITEM_ID, item_id);
        startService(intent);
    }

    private void viewVideo(String file, String type, int item_id) {
        // Hack type for Apple's format
        if (type.equals(new String("video/x-m4v"))) {
            type = "video/mp4";
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        try {
            i.setDataAndType(Uri.parse(file), type);
            startActivity(i);
            changeStatus(ctxt, item_id, Item.STATUS_READ);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, e.getMessage());
            try {
                i.setType("video/*");
                startActivity(i);
                changeStatus(ctxt, item_id, Item.STATUS_READ);
            } catch (ActivityNotFoundException e1) {
                Log.e(TAG, e1.getMessage());
                Toast.makeText(ctxt, R.string.toast_notsupported, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void changeButton(int id, String txt, boolean clickable, OnClickListener onClickListener) {
        Button btn = (Button) findViewById(id);
        btn.setClickable(clickable);
        btn.setEnabled(clickable);
        btn.setText(txt);
        if (onClickListener != null) {
            btn.setOnClickListener(onClickListener);
        }
    }

}
