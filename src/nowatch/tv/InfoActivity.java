package nowatch.tv;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends Activity {

    private final String TAG = "InfoActivity";
    private final String REQ = "SELECT feeds.title, items.title, items.description, "
            + "items.link, feeds.link, image, file_uri, file_size, file_type "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private final String PRE = "<meta http-equiv=\"Content-Type\" content=\"application/xhtml+xml; charset=UTF-8\"/>";
    private final String STYLE = "<style>*{color: white;}</style>";
    private final int IMG_DIP = 64;
    private DisplayMetrics displayMetrics;
    private Context ctxt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_activity);
        final Context ctxt = getApplicationContext();
        this.ctxt = ctxt;

        // Screen metrics (for dip to px conversion)
        displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Get item information
        Bundle extra = getIntent().getExtras();
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        Cursor c = db.rawQuery(REQ + extra.getLong("item_id"), null);
        c.moveToFirst();
        final String title = c.getString(1);
        ((TextView) findViewById(R.id.title)).setText(title);
        ((WebView) findViewById(R.id.desc)).loadData(PRE + c.getString(2) + STYLE, "text/html",
                "utf-8");
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
        final String file_size = c.getString(7);
        final String file_type = c.getString(8);

        // Set buttons
        ((Button) findViewById(R.id.btn_play)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                try {
                    i.setDataAndType(Uri.parse(file_uri), file_type);
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, e.getMessage());
                    try {
                        i.setType("video/*");
                        startActivity(i);
                    } catch (ActivityNotFoundException e1) {
                        Log.e(TAG, e1.getMessage());
                        Toast.makeText(ctxt, "Format de fichier non supporté !", Toast.LENGTH_LONG)
                                .show();
                    }
                }
            }
        });
        ((Button) findViewById(R.id.btn_download)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new DownloadTask(title).execute(file_uri, file_size);
            }
        });

        // Close stuff
        c.close();
        db.close();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Cancel DL here
    }

    class DownloadTask extends AsyncTask<String, Integer, Void> {

        private NotificationManager mNotificationManager;
        private RemoteViews rv;
        private Notification nf;
        private final int ID = 1;
        private String download_title;

        public DownloadTask(String title) {
            super();
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            download_title = title;
        }

        @Override
        protected void onPreExecute() {
            nf = new Notification(R.drawable.icon, "Download started!", System.currentTimeMillis());
            rv = new RemoteViews(ctxt.getPackageName(), R.layout.notification_download);
            rv.setImageViewResource(R.id.download_icon, R.drawable.icon);
            rv.setTextViewText(R.id.download_title, download_title);
            rv.setProgressBar(R.id.download_progress, 0, 0, true);
            // Null must be replaced by a DownloadManager Activity
            nf.contentIntent = PendingIntent.getActivity(ctxt, 0, null, 0);
            nf.contentView = rv;
            nf.flags |= Notification.FLAG_ONGOING_EVENT;
            nf.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(ID, nf);
        }

        @Override
        protected Void doInBackground(String... str) {
            int fs = 1;
            try {
                fs = Integer.parseInt(str[1]);
            } catch (NumberFormatException e) {
            }
            // Download file
            try {
                new getPodcastFile(fs).getChannel(str[0], Environment.getExternalStorageDirectory()
                        .toString()
                        + "/" + new File(str[0]).getName());
            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            rv.setProgressBar(R.id.download_progress, 100, values[0], false);
            mNotificationManager.notify(ID, nf);
        }

        @Override
        protected void onPostExecute(Void unused) {
            nf.flags = Notification.FLAG_SHOW_LIGHTS;
            mNotificationManager.notify(ID, nf);
        }

        class getPodcastFile extends GetFile {

            private long current_bytes = 0;
            private long file_size = 1;
            private int progress = 0;

            public getPodcastFile(long file_size) {
                if (file_size != 0) {
                    this.file_size = file_size;
                }
            }

            @Override
            protected void update(int count) {
                current_bytes += count;
                if (file_size > 1
                        && progress != (progress = (int) (current_bytes * 100 / file_size))) {
                    publishProgress(progress);
                }
            }
        }
    }
}
