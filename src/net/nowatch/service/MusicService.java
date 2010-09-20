package net.nowatch.service;

import java.io.IOException;

import net.nowatch.Main;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class MusicService extends Service {

    private static final String TAG = Main.TAG + "MusicService";
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
        Log.v(TAG, "onStartCommand()");
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

        @Override
        public void openFile(String path) throws RemoteException {
            try {
                mp.reset();
                mp.setDataSource(path);
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void play(long position) throws RemoteException {
            currentPosition = position;
            mp.start();
            // mp.setOnCompletionListener(new OnCompletionListener() {
            // public void onCompletion(MediaPlayer mp) {
            // }
            // });
        }

        @Override
        public void pause() throws RemoteException {
            mp.pause();
        }

        @Override
        public long getPosition() throws RemoteException {
            return currentPosition;
        }

    };

}
