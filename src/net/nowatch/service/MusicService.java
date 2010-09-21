package net.nowatch.service;

import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.RemoteException;

public class MusicService extends Service {

    // private static final String TAG = Main.TAG + "MusicService";
    private MediaPlayer mp = new MediaPlayer();
    private long currentPosition = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mp.stop();
        mp.release();
    }

    /**
     * Service control (IPC) using AIDL interface
     */
    private final IMusicService.Stub mBinder = new IMusicService.Stub() {

        public void openFile(String path) throws RemoteException {
            try {
                mp.reset();
                mp.setDataSource(path);
                mp.prepare();
                // FIXME: Use a callback (or broadcast intent) to notify
                // activity it's loaded
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void play(long position) throws RemoteException {
            currentPosition = position;
            mp.start();
            // mp.setOnCompletionListener(new OnCompletionListener() {
            // public void onCompletion(MediaPlayer mp) {
            // }
            // });
        }

        public void pause() throws RemoteException {
            mp.pause();
        }

        public long getPosition() throws RemoteException {
            return currentPosition;
        }

    };

}
