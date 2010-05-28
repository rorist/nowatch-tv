package nowatch.tv;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class DownloadService extends Service {

    private final static String TAG = "DownloadService";
    private final String REQ = "select title,file_uri,file_size from items where _id=? limit 1";
    private final int SIMULTANEOUS_DOWNLOAD = 2;
    private Context ctxt;
    private ConcurrentLinkedQueue<Integer> downloadQueue = new ConcurrentLinkedQueue<Integer>();
    private List<DownloadTask> downloadTasks;
    private int downloadCurrent = 0;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        ctxt = getApplicationContext();
        downloadTasks = new ArrayList<DownloadTask>();
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
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");
        handleCommand(intent);
        return START_NOT_STICKY;
    }

    private void handleCommand(Intent intent) {
        // Add item to download queue
        if (intent != null) {
            if (intent.hasExtra("item_id")) {
                addItem(intent.getExtras().getInt("item_id"));
            }
        }
    }

    private void addItem(int item_id) {
        if (!downloadQueue.contains(new Integer(item_id))) {
            downloadQueue.add(new Integer(item_id));
            Log.i(TAG, "Item added to queue=" + item_id);
        }
        startDownloadTask();
        InfoActivity.changeStatus(ctxt, item_id, Item.STATUS_DOWNLOADING);
    }

    private void startDownloadTask() {
        Log.v(TAG, "StopOrContinue: " + downloadCurrent + " < " + SIMULTANEOUS_DOWNLOAD);
        if (downloadCurrent < SIMULTANEOUS_DOWNLOAD) {
            Integer itemId = downloadQueue.poll();
            if (itemId != null) {
                // TODO: Check if there is enough space on device
                // Get item information and start DownloadTask
                SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
                Cursor c = db.rawQuery(REQ, new String[] { "" + itemId });
                c.moveToFirst();
                DownloadTask task = new DownloadTask(DownloadService.this, c.getString(0), itemId);
                task.execute(c.getString(1), c.getString(2));
                downloadTasks.add(task);
                c.close();
                db.close();
            } else {
                Log.i(TAG, "download queue is empty");
            }
        } else {
            Toast.makeText(ctxt, R.string.toast_dl_added, Toast.LENGTH_SHORT);
        }
    }

    private void stopOrContinue() {
        if (downloadQueue.peek() == null && downloadCurrent == 0) {
            stopSelf();
        } else {
            startDownloadTask();
        }
    }

    static class DownloadTask extends AsyncTask<String, Integer, Void> {

        private NotificationManager mNotificationManager;
        private RemoteViews rv;
        private Notification nf;
        private int item_id;
        private String download_title;
        private WeakReference<DownloadService> mService;

        public DownloadTask(DownloadService activity, String title, int itemId) {
            super();
            mService = new WeakReference<DownloadService>(activity);

            final DownloadService service = mService.get();
            mNotificationManager = (NotificationManager) service
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            download_title = title;
            item_id = itemId;
        }

        @Override
        protected void onPreExecute() {
            final DownloadService service = mService.get();

            nf = new Notification(android.R.drawable.stat_sys_download, service
                    .getString(R.string.notif_dl_started), System.currentTimeMillis());
            rv = new RemoteViews(service.getPackageName(), R.layout.notification_download);
            rv.setImageViewResource(R.id.download_icon, R.drawable.icon);
            rv.setTextViewText(R.id.download_title, download_title);
            rv.setProgressBar(R.id.download_progress, 0, 0, true);
            nf.contentIntent = PendingIntent.getActivity(service, 0, new Intent(service,
                    DownloadManager.class), 0);
            nf.contentView = rv;
            nf.flags |= Notification.FLAG_ONGOING_EVENT;
            nf.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(item_id, nf);

            service.downloadCurrent++;
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
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    // TODO: Use getExternalStoragePublicDirectory() (API L8)
                    File dst = new File(Environment.getExternalStorageDirectory()
                            .getCanonicalPath()
                            + "/Podcasts/Nowatch.TV");
                    dst.mkdirs();
                    new getPodcastFile(fs).getChannel(str[0], dst.getCanonicalPath() + "/"
                            + new File(str[0]).getName());
                } else {
                    // FIXME: Propagate error or exception
                    cancel(false);
                }
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
            super.onPostExecute(unused);
            final DownloadService service = mService.get();
            InfoActivity.changeStatus(service, item_id, Item.STATUS_DL_UNREAD);
            // FIXME: Use Activity.getString()
            finishNotification(service.getString(R.string.notif_dl_complete));
            service.downloadCurrent--;
            service.stopOrContinue();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            final DownloadService service = mService.get();
            InfoActivity.changeStatus(service, item_id, Item.STATUS_UNREAD);
            // FIXME: Use Activity.getString()
            finishNotification(service.getString(R.string.notif_dl_canceled));
            service.downloadCurrent--;
            service.stopOrContinue();
        }

        private void finishNotification(String msg) {
            final DownloadService service = mService.get();
            try {
                mNotificationManager.cancel(item_id);
            } catch (Exception e) {
            }
            nf = new Notification(android.R.drawable.stat_sys_download_done, "", System
                    .currentTimeMillis());
            nf.setLatestEventInfo(service, download_title, msg, PendingIntent.getActivity(service,
                    0, new Intent(service, DownloadManager.class), 0));
            mNotificationManager.notify(item_id, nf);
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

            public void getChannel(String src, String dst) throws IOException {
                getChannel(src, dst, null, false);
            }

            @Override
            protected void update(int count) {
                current_bytes += count;
                if (file_size > 0
                        && progress != (progress = (int) (current_bytes * 100 / file_size))) {
                    publishProgress(progress, (int) (current_bytes / Math
                            .abs((System.nanoTime() - start) / 1000000)));
                }
            }
        }
    }

    /**
     * Service control (IPC) using AIDL interface
     */
    private final DownloadInterface.Stub mBinder = new DownloadInterface.Stub() {

        public void _startDownload(int id) throws RemoteException {
            addItem(id);
        }

        public void _cancelDownload(int id) throws RemoteException {
            // Search current dl
            if (downloadQueue.contains(new Integer(id))) {
                downloadQueue.remove(new Integer(id));
                InfoActivity.changeStatus(ctxt, id, Item.STATUS_UNREAD);
                return;
            }
            // Search pending dl
            for (DownloadTask task : downloadTasks) {
                if (task.item_id == id) {
                    task.cancel(true);
                    InfoActivity.changeStatus(ctxt, id, Item.STATUS_UNREAD);
                    return;
                }
            }
            return;
        }

        public void _stopOrContinue() throws RemoteException {
            stopOrContinue();
        }

        public int[] _getCurrentDownloads() throws RemoteException {
            int[] current = new int[downloadCurrent];
            for (int i = 0; i < downloadCurrent; i++) {
                current[i] = downloadTasks.get(i).item_id;
            }
            return current;
        }

        public int[] _getPendingDownloads() throws RemoteException {
            int[] pending = new int[downloadQueue.size()];
            Iterator<Integer> iterator = downloadQueue.iterator();
            int i = 0;
            while (iterator.hasNext()) {
                pending[i] = iterator.next();
                i++;
            }
            return pending;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "onUnbind()");
        return true;
    }
}
