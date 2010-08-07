package nowatch.tv;

// TODO: Use IntentService in startService() (queuing model)

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.client.ClientProtocolException;

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
    private HashMap<Integer, DownloadTask> downloadTasks;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        ctxt = getApplicationContext();
        downloadTasks = new HashMap<Integer, DownloadTask>();
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
        // FIXME: onStart() is deprecated, but used for backward compatibility!
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");
        handleCommand(intent);
        return START_NOT_STICKY;
    }

    private void handleCommand(Intent intent) {
        // Clean failed downloads
        if (downloadTasks.size() == 0) {
            SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
            db.execSQL("update items set status=3 where status=2");
            db.close();
        }
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
        Log.v(TAG, "StopOrContinue: " + downloadTasks.size() + " < " + SIMULTANEOUS_DOWNLOAD);
        Network net = new Network(this);
        if (net.isConnected()) {
            if (net.isMobileAllowed()) {
                if (downloadTasks.size() < SIMULTANEOUS_DOWNLOAD) {
                    Integer itemId = downloadQueue.poll();
                    if (itemId != null) {
                        // TODO: Check if there is enough space on device
                        // Get item information and start DownloadTask
                        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
                        Cursor c = db.rawQuery(REQ, new String[] { "" + itemId });
                        c.moveToFirst();
                        DownloadTask task = new DownloadTask(DownloadService.this, c.getString(0),
                                itemId);
                        task.execute(c.getString(1), c.getString(2));
                        downloadTasks.put(itemId, task);
                        c.close();
                        db.close();
                    } else {
                        Log.i(TAG, "download queue is empty");
                    }
                } else {
                    Toast.makeText(ctxt, R.string.toast_dl_added, Toast.LENGTH_SHORT);
                }
            } else {
                Toast.makeText(ctxt, R.string.toast_nomobiletraffic, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(ctxt, R.string.toast_notconnected, Toast.LENGTH_LONG).show();
        }
    }

    private void stopOrContinue() {
        if (downloadQueue.peek() == null && downloadTasks.size() < 1) {
            Log.i(TAG, "stopping service");
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
        private String error_msg = null;
        private getPodcastFile task = null;

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
            // FIXME: Add an action by Intent instead of null
            // nf.contentIntent = PendingIntent.getActivity(service, 0, new
            // Intent(DownloadService.this, DownloadManager.class), 0);
            nf.contentIntent = PendingIntent.getActivity(service, 0, null, 0);
            nf.contentView = rv;
            nf.flags |= Notification.FLAG_ONGOING_EVENT;
            nf.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(item_id, nf);
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
                            + "/" + GetFile.PATH_PODCASTS);
                    dst.mkdirs();
                    task = new getPodcastFile(fs);
                    task.getChannel(str[0], dst.getCanonicalPath() + "/"
                            + new File(str[0]).getName());
                } else {
                    // FIXME: Propagate error or exception
                    cancel(false);
                }
            } catch (MalformedURLException e) {
                error_msg = e.getLocalizedMessage();
                Log.e(TAG, e.getMessage());
            } catch (ClientProtocolException e) {
                error_msg = e.getLocalizedMessage();
                Log.e(TAG, e.getMessage());
            } catch (UnknownHostException e) {
                error_msg = e.getLocalizedMessage();
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                error_msg = e.getLocalizedMessage();
                Log.e(TAG, e.getMessage());

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
            Log.v(TAG, "onPostExecute()");
            super.onPostExecute(unused);
            if (mService != null) {
                final DownloadService service = mService.get();
                if (service != null) {
                    if (error_msg != null) {
                        Toast.makeText(service.getApplicationContext(), error_msg,
                                Toast.LENGTH_LONG);
                    }
                    InfoActivity.changeStatus(service, item_id, Item.STATUS_DL_UNREAD);
                    finishNotification(service.getString(R.string.notif_dl_complete));
                    service.stopOrContinue();
                }
            }
        }

        @Override
        protected void onCancelled() {
            Log.v(TAG, "onCancelled()");
            super.onCancelled();
            if (mService != null) {
                final DownloadService service = mService.get();
                if (service != null) {
                    if (error_msg != null) {
                        Toast.makeText(service.getApplicationContext(), error_msg,
                                Toast.LENGTH_LONG);
                    }
                    InfoActivity.changeStatus(service, item_id, Item.STATUS_UNREAD);
                    finishNotification(service.getString(R.string.notif_dl_canceled));
                    service.stopOrContinue();
                }
            }
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
            // Search pending dl
            Log.v(TAG, "queue size (before remove)=" + downloadQueue.size());
            if (downloadQueue.contains(new Integer(id))) {
                InfoActivity.changeStatus(ctxt, id, Item.STATUS_UNREAD);
                downloadQueue.remove(new Integer(id));
                Log.v(TAG, "queue size=" + downloadQueue.size());
                return;
            }
            // Search current dl
            Log.v(TAG, "current tasks (before remove)=" + downloadTasks.size());
            if (downloadTasks.containsKey(id)) {
                InfoActivity.changeStatus(ctxt, id, Item.STATUS_UNREAD);
                DownloadTask task = downloadTasks.get(id);
                task.task.cancel = true;
                if (task.cancel(true)) {
                    downloadTasks.remove(id);
                    Log.v(TAG, "current tasks=" + downloadTasks.size());
                }
            }
            return;
        }

        public void _stopOrContinue() throws RemoteException {
            stopOrContinue();
        }

        public int[] _getCurrentDownloads() throws RemoteException {
            int[] current = new int[downloadTasks.size()];
            Iterator<Integer> iterator = downloadTasks.keySet().iterator();
            int i = 0;
            while (iterator.hasNext()) {
                current[i] = iterator.next();
                i++;
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
