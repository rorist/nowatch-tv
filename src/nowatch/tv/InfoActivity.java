package nowatch.tv;

import android.app.Activity;
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
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.File;

public class InfoActivity extends Activity {

    private final String TAG = "InfoActivity";
    private final String REQ = "SELECT feeds.title, items.title, items.description, "
            + "items.link, feeds.link, image, file_uri, file_size, file_type "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private final String PRE = "<meta http-equiv=\"Content-Type\" content=\"application/xhtml+xml; charset=UTF-8\"/>";
    private final String STYLE = "<style>*{color: white;}</style>";
    private final int IMG_DIP = 64;
    private DisplayMetrics displayMetrics;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);
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
        ((WebView) findViewById(R.id.desc)).loadData(PRE + c.getString(2) + STYLE, "text/html", "utf-8");
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
                i.setDataAndType(Uri.parse(file_uri), file_type);
                startActivity(i);
            }
        });
        final DownloadTask dl = new DownloadTask();
        ((Button) findViewById(R.id.btn_download)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dl.execute(file_uri, file_size);
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

        private long file_size = 0;
        private long progress_current = 0;

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
            setProgressBarVisibility(true);
            setProgress(0);
        }

        @Override
        protected Void doInBackground(String... str) {
            try {
                file_size = Integer.parseInt(str[1]);
                //new getPodcastFile().get(str[0], Environment.getExternalStorageDirectory().toString() + "/" + new URL(str[0]).getFile());
                new getPodcastFile().getChannel(str[0],  "/sdcard/" + new File(str[0]).getName());
            } catch (MalformedURLException e){
                Log.e(TAG, e.getMessage());
            } catch (IOException e){
                Log.e(TAG, e.getMessage());
            } catch (NumberFormatException e){
                Log.e(TAG, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            setProgressBarIndeterminateVisibility(false);
            setProgressBarVisibility(false);
            setProgress(10000);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progress_current += values[0];
            setProgress((int)(progress_current * 10000 / file_size));
        }


        class getPodcastFile extends GetFile {

            public getPodcastFile(){
                super();
                buffer_size = 32 * 1024;
            }

            @Override
            protected void update(int count){
                publishProgress(count);
            }
        }

    }
}
