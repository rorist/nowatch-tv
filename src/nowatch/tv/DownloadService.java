package nowatch.tv;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class DownloadService extends Service {

    private final String TAG = "DownloadService";
    private final String REQ = "select title,file_uri,file_size from items where _id=";
    private final int SIMULTANEOUS_DOWNLOAD = 2;
    private Context ctxt;
    private ConcurrentLinkedQueue<Integer> downloadQueue = new ConcurrentLinkedQueue<Integer>();
    private int downloadCurrent = 0;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        ctxt = getApplicationContext();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory()");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(TAG, "onStart()");
        // FIXME: onStrart() is deprecated, but used for backward compatibility!

        // Add item to download queue
        Bundle extra = intent.getExtras();
        int item_id = extra.getInt("item_id");
        if (!downloadQueue.contains(item_id)) {
            downloadQueue.add(item_id);
        } else {
            Log.i(TAG, "Already in download queue ...");
        }

        if (downloadCurrent < SIMULTANEOUS_DOWNLOAD) {
            startDownloadTask();
        } else {
            Toast.makeText(ctxt, "Téléchargement ajouté dans la file d'attente ...",
                    Toast.LENGTH_SHORT);
            Log.i(TAG, "maximum simlutaneous download reached");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        // Going to be used with AIDL for DownloadManager
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind()");
        return true;
    }

    private void startDownloadTask() {
        Integer itemId = downloadQueue.poll();
        if (itemId != null) {
            // TODO: Check if there is enough space on device
            // Get item information and start DownloadTask
            SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
            Cursor c = db.rawQuery(REQ + itemId, null);
            c.moveToFirst();
            new DownloadTask(c.getString(0), itemId).execute(c.getString(1), c.getString(2));
            c.close();
            db.close();
        } else {
            Log.i(TAG, "download queue is empty");
        }
    }

    class DownloadTask extends AsyncTask<String, Integer, Void> {

        private NotificationManager mNotificationManager;
        private RemoteViews rv;
        private Notification nf;
        private int item_id;
        private String download_title;

        public DownloadTask(String title, int itemId) {
            super();
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            download_title = title;
            item_id = itemId;
        }

        @Override
        protected void onPreExecute() {
            nf = new Notification(android.R.drawable.stat_sys_download,
                    "Téléchargement démarré ...", System.currentTimeMillis());
            rv = new RemoteViews(ctxt.getPackageName(), R.layout.notification_download);
            rv.setImageViewResource(R.id.download_icon, R.drawable.icon);
            rv.setTextViewText(R.id.download_title, download_title);
            rv.setProgressBar(R.id.download_progress, 0, 0, true);
            nf.contentIntent = PendingIntent.getActivity(ctxt, 0, new Intent(ctxt,
                    DownloadManager.class), 0);
            nf.contentView = rv;
            nf.flags |= Notification.FLAG_ONGOING_EVENT;
            nf.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(item_id, nf);
            downloadCurrent++;
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
                        + "/" + new File(str[0]).getName(), null);
            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            rv.setProgressBar(R.id.download_progress, 100, values[0], false);
            rv.setTextViewText(R.id.download_status, values[0] + "% " + values[1] + "kB/s");
            mNotificationManager.notify(item_id, nf);
        }

        @Override
        protected void onPostExecute(Void unused) {
            finishNotification("Téléchargement terminé!");
            stopOrContinue();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            finishNotification("Téléchargement annulé!");
            stopOrContinue();
        }

        private void finishNotification(String msg) {
            try {
                mNotificationManager.cancel(item_id);
            } catch (Exception e) {
            }
            nf = new Notification(android.R.drawable.stat_sys_download_done, "", System
                    .currentTimeMillis());
            nf.setLatestEventInfo(ctxt, download_title, msg, PendingIntent.getActivity(ctxt, 0,
                    new Intent(ctxt, DownloadManager.class), 0));
            mNotificationManager.notify(item_id, nf);
        }

        private void stopOrContinue() {
            downloadCurrent--;
            if (downloadQueue.peek() == null && downloadCurrent == 0) {
                stopSelf();
            } else {
                startDownloadTask();
            }
        }

        class getPodcastFile extends GetFile {

            private long current_bytes = 0;
            private long file_size = 1;
            private int progress = 0;
            private long start;

            public getPodcastFile(long file_size) {
                if (file_size != 0) {
                    this.file_size = file_size;
                }
                start = System.nanoTime();
            }

            @Override
            protected void update(int count) {
                current_bytes += count;
                if (file_size > 0
                        && progress != (progress = (int) (current_bytes * 100 / file_size))) {
                    publishProgress(progress, getSpeed());
                }
            }

            private int getSpeed() {
                return (int) (current_bytes / Math.abs((System.nanoTime() - start) / 1000000));
            }
        }
    }

}
