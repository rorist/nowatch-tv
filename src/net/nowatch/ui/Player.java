package net.nowatch.ui;

// http://developer.android.com/reference/android/media/MediaPlayer.html
// Based on packages/apps/Music/src/com/android/music/MediaPlaybackActivity.java

import net.nowatch.Main;
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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class Player extends Activity {

    private static final String TAG = Main.TAG + "MusicPlayer";
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    private static final String REQ = "SELECT feeds.title, items.title, feeds.image, "
            + " items.image, items.duration FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private static final int REFRESH = 1;
    private IMusicService mService;
    private String mPath;
    private long mDuration;
    private long mPosition;
    private Intent mIntent;
    private Bundle mExtras;
    private boolean isAudio = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_load);

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

        // File type
        if (type.toLowerCase().startsWith("video/")) {
            isAudio = false;
        }
        // Start service
        startService(new Intent(Player.this, MusicService.class));
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(Player.this, MusicService.class), mConnection, 0);

    }

    @Override
    public void onStop() {
        super.onStop();
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
        setProgressBarIndeterminateVisibility(false);
        setContentView(R.layout.activity_player_video);
    }

    private void playAudio() {
        if (mIntent.hasExtra(EXTRA_ITEM_ID)) {
            int item_id = mExtras.getInt(EXTRA_ITEM_ID);
            // Open and Play the file
            if (mService != null) {
                try {
                    mService.openFileId(item_id);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // ContentResolver resolver = getContentResolver();
            // TODO: get data from resolver if we want generic player
        }

    }

    private void setUI(){
        // Get podcast, episode and image
        String podcast = "Inconnu";
        String episode = "Inconnu (TODO)";
        
        if (mIntent.hasExtra(EXTRA_ITEM_ID)) {
            int item_id = mExtras.getInt(EXTRA_ITEM_ID);
            SQLiteDatabase db = (new Db(getApplicationContext())).openDb();
            Cursor c = db.rawQuery(REQ + item_id, null);
            c.moveToFirst();
            podcast = c.getString(0);
            episode = c.getString(1);
            mDuration = c.getLong(4);
            // TODO: Get image
            c.close();
            db.close();
        }
        
        // Populate UI
        setContentView(R.layout.activity_player_audio);
        ((SeekBar) findViewById(R.id.seek)).setMax(1000);
        ((TextView) findViewById(R.id.player_time_total)).setText(getTime(mDuration));
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
                        mService.play((int) mPosition);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // Update UI
        long delay = refresh();
        queueNextRefresh(delay);
    }

    private long refresh() {
        if (mService != null) {
            try {
                mPosition = mService.getPosition();
                long remaining = 1000 - (mPosition % 1000);
                ((TextView) findViewById(R.id.player_time_current)).setText(getTime(mPosition));
                ((SeekBar) findViewById(R.id.seek))
                        .setProgress((int) (1000 * mPosition / mDuration));
                return remaining;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return 500;
    }

    private String getTime(long t) {
        return String.format("%02d:%02d:%02d", t / 3600, t % 3600 / 60, t % 3600 % 60);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IMusicService.Stub.asInterface(service);
            try {
                if (!mService.isPlaying()) {
                    if (isAudio) {
                        playAudio();
                    } else {
                        playVideo();
                    }
                }
                setUI();
            } catch (RemoteException e) {
                e.printStackTrace();
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
