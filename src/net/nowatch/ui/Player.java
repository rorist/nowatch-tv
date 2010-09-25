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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SeekBar;
import android.widget.TextView;

public class Player extends Activity {

    // private static final String TAG = Main.TAG + "MusicPlayer";
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    private static final String REQ = "SELECT feeds.title, items.title, feeds.image, "
            + " items.image FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private static final int REFRESH = 1;
    private IMusicService mService;
    private String mPath;
    private long mDuration;
    private long mPosition;
    private Intent mIntent;
    private Bundle mExtras;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIntent = getIntent();
        mExtras = mIntent.getExtras();
        String type = mIntent.getType();
        String scheme = mIntent.getScheme();

        // Get file data
        if (!mIntent.hasExtra(EXTRA_POSITION)) {
            mPosition = 0;
        } else {
            mPosition = mExtras.getLong(EXTRA_POSITION);
        }
        if ("file".equals(scheme)) {
            mPath = mIntent.getData().getPath();
        } else {
            mPath = mIntent.getDataString();
        }

        // Play file
        if (type.toLowerCase().startsWith("video/")) {
            playVideo();
        } else {
            playAudio();
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

    private void playVideo() {
        // Use foreground video player
        setContentView(R.layout.activity_player_video);
    }

    private void playAudio() {
        // Start music player service
        Intent service = new Intent(Player.this, MusicService.class);
        startService(service);

        // Get podcast, episode and image
        String podcast = "Inconnu";
        String episode = "Inconnu (TODO)";
        if (mIntent.hasExtra(EXTRA_ITEM_ID)) {
            SQLiteDatabase db = (new Db(getApplicationContext())).openDb();
            Cursor c = db.rawQuery(REQ + mExtras.getInt(EXTRA_ITEM_ID), null);
            c.moveToFirst();
            podcast = c.getString(0);
            episode = c.getString(1);
            // FIXME: get duration from RSS ?
            // mDuration = c.getLong(2);
            mDuration = 60 * 60; // 1h
            // TODO: Get image
            c.close();
            db.close();
        } else {
            // ContentResolver resolver = getContentResolver();
            // TODO: get data from resolver if we want generic player
        }

        // Set UI
        setContentView(R.layout.activity_player_audio);
        ((SeekBar) findViewById(R.id.seek)).setMax(1000);
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

        // Update UI
        long delay = refresh();
        queueNextRefresh(delay);
    }

    private long refresh() {
        if (mService != null) {
            try {
                long pos = mService.getPosition();
                long remaining = 1000 - (pos % 1000);
                ((TextView) findViewById(R.id.player_time_current)).setText("" + pos);
                ((SeekBar) findViewById(R.id.seek)).setProgress((int) (1000 * pos / mDuration));
                return remaining;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return 500;
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

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    long delay = refresh();
                    queueNextRefresh(delay);
                    break;
            }
        }
    };

    private void queueNextRefresh(long delay) {
        Message msg = mHandler.obtainMessage(REFRESH);
        mHandler.removeMessages(REFRESH);
        mHandler.sendMessageDelayed(msg, refresh());
    }
}
