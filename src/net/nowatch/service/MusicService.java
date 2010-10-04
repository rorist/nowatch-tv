package net.nowatch.service;

import java.io.IOException;

import net.nowatch.Main;
import net.nowatch.R;
import net.nowatch.ui.Player;
import net.nowatch.utils.Db;
import net.nowatch.utils.Item;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class MusicService extends Service {

    private static final String TAG = Main.TAG + "MusicService";
    private static final String REQ = "SELECT feeds.title, items.title, items.file_uri, items.file_type FROM items "
        + "INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    protected static final int SERVICE_ID = 1;
    private MediaPlayer mp = new MediaPlayer();
    private NotificationManager nm;
    private boolean isPaused = false;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public void onDestroy() {
        mp.stop();
        mp.release();
        // mp = null;
        // TODO: Deregister callback
        nm.cancelAll();
    }

    private void handleCommand(Intent intent) {
        Log.v(TAG, "handleCommand()");
    }

    /**
     * Service control (IPC) using AIDL interface
     */
    private final IMusicService.Stub mBinder = new IMusicService.Stub() {

        public void openFileId(int id) throws RemoteException {
            // Get podcast info
            SQLiteDatabase db = (new Db(getApplicationContext())).openDb();
            Cursor c = db.rawQuery(REQ + id, null);
            c.moveToFirst();
            String msg = c.getString(0) + " - " + c.getString(1);
            String url = c.getString(2);
            String type = c.getString(3);
            c.close();
            db.close();
            openFile(msg, url, type, id);
        }

        public void openFile(String msg, String path, String type, int item_id) throws RemoteException {
            // Notification
            Intent i = new Intent(MusicService.this, Player.class).putExtra(Item.EXTRA_ITEM_ID, item_id)
                    .setDataAndType(Uri.parse(path), type).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Notification nf = new Notification(R.drawable.stat_notify_musicplayer,
                    "Lecture en cours ... (FIXME)", System.currentTimeMillis());
            nf.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;
            nf.setLatestEventInfo(MusicService.this, "Lecture en cours", msg, PendingIntent
                    .getActivity(MusicService.this, 0, i, 0));
            nm.notify(SERVICE_ID, nf);
            try {
                // Auto-play
                mp.setOnPreparedListener(new OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        try {
                            play(0);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
                // On complete
                mp.setOnCompletionListener(new OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        isPaused = false;
                        mp.stop();
                    }
                });
                // Preparation
                mp.reset();
                mp.setDataSource(path);
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void play(int position) throws RemoteException {
            mp.seekTo(position * 1000);
            mp.start();
            isPaused = false;
        }

        public void pause() throws RemoteException {
            mp.pause();
            isPaused = true;
        }

        public long getPosition() throws RemoteException {
            return mp.getCurrentPosition() / 1000;
        }

        public boolean isPlaying() throws RemoteException {
            return mp.isPlaying() || isPaused;
        }

    };

}
