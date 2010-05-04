package nowatch.tv;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

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
import android.view.View;
import android.widget.RemoteViews;

public class DownloadService extends Service {

    private final String TAG = "DownloadService";
    private final String REQ = "select _id, title,file_uri,file_size from items where _id=";
    private Context ctxt;
    private List<Integer> currentDownloads = new ArrayList<Integer>();

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

        // TODO: Check if there is enough space on device
        // Get item information and start DownloadTask
        if (!currentDownloads.contains(startId)) {
            currentDownloads.add(startId);
            Bundle extra = intent.getExtras();
            SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
            Cursor c = db.rawQuery(REQ + extra.getLong("item_id"), null);
            c.moveToFirst();
            new DownloadTask(c.getString(1), c.getInt(0)).execute(c.getString(2), c.getString(3));
            c.close();
            db.close();
        } else {
            Log.v(TAG, "Already downloaded");
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

    class DownloadTask extends AsyncTask<String, Integer, Void> {

        private NotificationManager mNotificationManager;
        private RemoteViews rv;
        private Notification nf;
        private int notification_id;
        private String download_title;

        public DownloadTask(String title, int id) {
            super();
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            download_title = title;
            notification_id = id;
        }

        @Override
        protected void onPreExecute() {
            nf = new Notification(R.drawable.icon, "Téléchargement démarré ...", System
                    .currentTimeMillis());
            rv = new RemoteViews(ctxt.getPackageName(), R.layout.notification_download);
            rv.setImageViewResource(R.id.download_icon, R.drawable.icon);
            rv.setTextViewText(R.id.download_title, download_title);
            rv.setProgressBar(R.id.download_progress, 0, 0, true);
            nf.contentIntent = PendingIntent.getActivity(ctxt, 0, new Intent(ctxt,
                    DownloadManager.class), 0);
            nf.contentView = rv;
            nf.flags |= Notification.FLAG_ONGOING_EVENT;
            nf.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(notification_id, nf);
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
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            rv.setProgressBar(R.id.download_progress, 100, values[0], false);
            rv.setTextViewText(R.id.download_status, values[0] + "% " + values[1] + "kB/s");
            mNotificationManager.notify(notification_id, nf);
        }

        @Override
        protected void onPostExecute(Void unused) {
            rv.setViewVisibility(R.id.download_progress, View.GONE);
            rv.setTextViewText(R.id.download_status, "Téléchargement terminé!");
            // nf.contentView = rv;
            nf.flags = Notification.FLAG_SHOW_LIGHTS;
            mNotificationManager.notify(notification_id, nf);
            stopSelf();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            nf.flags = 0;
            mNotificationManager.notify(notification_id, nf);
            stopSelf();
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
                if (file_size > 1
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
