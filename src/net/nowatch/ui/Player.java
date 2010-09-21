package net.nowatch.ui;

//http://developer.android.com/reference/android/media/MediaPlayer.html

import net.nowatch.R;
import net.nowatch.service.IMusicService;
import net.nowatch.service.MusicService;
import net.nowatch.utils.Db;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class Player extends Activity {

    // private static final String TAG = Main.TAG + "MusicPlayer";
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    private static final String REQ = "SELECT feeds.title, items.title, feeds.image, "
            + " items.image FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private IMusicService mService;
    private String mPath;
    private long mPosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String type = intent.getType();
        String scheme = intent.getScheme();
        Bundle extras = intent.getExtras();

        // Get file data
        if (!intent.hasExtra(EXTRA_POSITION)) {
            mPosition = 0;
        } else {
            mPosition = extras.getLong(EXTRA_POSITION);
        }
        if ("file".equals(scheme)) {
            mPath = intent.getData().getPath();
        } else {
            mPath = intent.getDataString();
        }

        if (type.toLowerCase().startsWith("video/")) {
            // Use foreground video player
            setContentView(R.layout.activity_player_video);
        } else {
            // Start music player service
            Intent service = new Intent(Player.this, MusicService.class);
            startService(service);

            // Get podcast, episode and image
            String podcast = "Inconnu";
            String episode = "Inconnu (TODO)";
            if (intent.hasExtra(EXTRA_ITEM_ID)) {
                SQLiteDatabase db = (new Db(getApplicationContext())).openDb();
                Cursor c = db.rawQuery(REQ + extras.getInt(EXTRA_ITEM_ID), null);
                c.moveToFirst();
                podcast = c.getString(0);
                episode = c.getString(1);
                // TODO: Get image
            } else {
                // ContentResolver resolver = getContentResolver();
                // FIXME: get data from resolver
            }

            // Set UI
            setContentView(R.layout.activity_player_audio);
            ((TextView) findViewById(R.id.player_show)).setText(podcast);
            ((TextView) findViewById(R.id.player_episode)).setText(episode);
            findViewById(R.id.btn_pause).setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (mService != null) {
                        try {
                            mService.pause();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            findViewById(R.id.btn_play).setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (mService != null) {
                        try {
                            mService.play(0L);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            // Read file
            bindService(service, mConnection, 0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mConnection);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // FIXME: Start new file
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IMusicService.Stub.asInterface(service);
            if (mService != null) {
                try {
                    // Start the player
                    mService.openFile(mPath);
                    mService.play(mPosition);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }

    };
}
