package nowatch.tv.service;

// TODO: Use IntentService in startService() (queuing model)

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import nowatch.tv.IService;
import nowatch.tv.IServiceCallback;
import nowatch.tv.Main;
import nowatch.tv.R;
import nowatch.tv.network.GetFile;
import nowatch.tv.network.Network;
import nowatch.tv.ui.ItemInfo;
import nowatch.tv.ui.ListItems;
import nowatch.tv.utils.DB;
import nowatch.tv.utils.Item;
import nowatch.tv.utils.Prefs;

import org.apache.http.client.ClientProtocolException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class NWService extends Service {

    private final static String TAG = Main.TAG + "DownloadService";
    private final static int NOTIFICATION_UPDATE = -1;
    private final static String REQ_NEW = "select count(_id) from items where status="
            + Item.STATUS_NEW;
    private final String REQ_ITEM = "select title,file_uri,file_size from items where _id=? limit 1";
    private final String REQ_CLEAN = "update items set status=" + Item.STATUS_UNREAD
            + " where status=" + Item.STATUS_DOWNLOADING;
    private final RemoteCallbackList<IServiceCallback> mCallbacks = new RemoteCallbackList<IServiceCallback>();
    private final ConcurrentLinkedQueue<Integer> downloadQueue = new ConcurrentLinkedQueue<Integer>();
    private final HashMap<Integer, DownloadTask> downloadTasks = new HashMap<Integer, DownloadTask>();
    private Context ctxt;

    public static final String ACTION_UPDATE = "action_update";
    public static final String ACTION_ADD = "action_add";
    public static final String ACTION_CANCEL = "action_cancel";
    public static final String ACTION_PAUSE = "action_pause";
    public static final int TYPE_CURRENT = 1;
    public static final int TYPE_PENDING = 2;
    public NotificationManager notificationManager;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        ctxt = getApplicationContext();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        cancelAll();
        clean();
        mCallbacks.kill();
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory()");
        cancelAll();
        clean();
        mCallbacks.kill();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        // onStart() is deprecated, but used for backward compatibility!
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        return START_NOT_STICKY;
    }

    private void handleCommand(Intent intent) {
        // Clean stuff
        clean();
        // Handle intentions
        String action = null;
        if (intent != null && (action = intent.getAction()) != null) {
            Log.v(TAG, "action=" + action);
            if (ACTION_ADD.equals(action)) {
                // Add item to download queue
                Integer id = new Integer(intent.getExtras().getInt(Item.EXTRA_ITEM_ID));
                if (!downloadQueue.contains(id)) {
                    downloadQueue.add(id);
                }
                stopOrContinue();
            } else if (ACTION_CANCEL.equals(action)) {
                // Cancel download
                Bundle extras = intent.getExtras();
                cancelDownload(extras.getInt(Item.EXTRA_ITEM_TYPE), extras
                        .getInt(Item.EXTRA_ITEM_ID));
                stopOrContinue();
            } else if (ACTION_PAUSE.equals(action)) {
                pauseDownload(intent.getExtras().getInt(Item.EXTRA_ITEM_ID));
                stopOrContinue();
            } else if (ACTION_UPDATE.equals(action)) {
                // Check for updates
                UpdateTaskNotif updateTask = new UpdateTaskNotif(NWService.this);
                updateTask.execute();
            } else {
                // Nothing to do
                stopOrContinue();
            }
        } else {
            // Nothing to do
            stopOrContinue();
        }
    }

    private void clean() {
        // Clean failed downloads
        if (downloadTasks.size() == 0) {
            // Reset state
            SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
            db.execSQL(REQ_CLEAN);
            db.close();
            // Remove notifications
            // try {
            // notificationManager.cancelAll();
            // } catch (Exception e) {
            // Log.v(TAG, e.getMessage());
            // }
        }
    }

    private void cancelAll() {
        // Cancel current downloads
        if (downloadTasks != null && downloadTasks.size() > 0) {
            Collection<DownloadTask> tasks = downloadTasks.values();
            try {
                synchronized (tasks) {
                    for (DownloadTask task : tasks) {
                        if (AsyncTask.Status.RUNNING.equals(task.getStatus()) && task.cancel(true)) {
                            downloadTasks.remove(task.item_id);
                            ItemInfo.changeStatus(ctxt, task.item_id, Item.STATUS_UNREAD);
                        }
                    }
                }
            } catch (ConcurrentModificationException e) {
                Log.e(TAG, "FIXME: ConcurrentModificationException");
            }
        }
    }

    private void cancelDownload(int type, Integer id) {
        if (type == TYPE_PENDING) {
            if (downloadQueue.contains(id)) {
                ItemInfo.changeStatus(ctxt, id, Item.STATUS_UNREAD);
                downloadQueue.remove(id);
            }
        } else if (type == TYPE_CURRENT) {
            if (downloadTasks.containsKey(id)) {
                DownloadTask task = downloadTasks.get(id);
                synchronized (task.task) {
                    task.task.isCancelled = true;
                }
                if (AsyncTask.Status.RUNNING.equals(task.getStatus()) && task.cancel(true)) {
                    // FIXME: Delete the file
                    downloadTasks.remove(id);
                    ItemInfo.changeStatus(ctxt, id, Item.STATUS_UNREAD);
                }
            }
        }
        clientCallback();
    }

    private void pauseDownload(int id) {
        if (downloadTasks.containsKey(id)) {
            DownloadTask task = downloadTasks.get(id);
            synchronized (task.task) {
                task.task.isCancelled = true;
            }
            if (AsyncTask.Status.RUNNING.equals(task.getStatus()) && task.cancel(true)) {
                downloadTasks.remove(id);
                ItemInfo.changeStatus(ctxt, id, Item.STATUS_UNCOMPLETE);
                clientCallback();
            }
        }
    }

    private void clientCallback() {
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i = 0; i < N; i++) {
            try {
                mCallbacks.getBroadcastItem(i)._valueChanged();
            } catch (RemoteException e) {
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void stopOrContinue() {
        if (downloadTasks.size() < 1 && downloadQueue.peek() == null) {
            stopSelf();
        } else {
            startDownloadTask();
        }
    }

    private void startDownloadTask() {
        Network net = new Network(this);
        if (net.isConnected()) {
            if (net.isMobileAllowed()) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
                if (downloadTasks.size() < Integer.parseInt(prefs.getString(
                        Prefs.KEY_SIMULTANEOUS_DL, Prefs.DEFAULT_SIMULTANEOUS_DL))
                        && downloadQueue.size() > 0) {
                    Integer itemId = downloadQueue.poll();
                    if (itemId != null) {
                        // TODO: Check if there is enough space on device
                        // Get item information and start DownloadTask
                        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
                        Cursor c = db.rawQuery(REQ_ITEM, new String[] { "" + itemId });
                        c.moveToFirst();
                        DownloadTask task = new DownloadTask(NWService.this, c.getString(0), itemId);
                        task.execute(c.getString(1), c.getString(2));
                        downloadTasks.put(itemId, task);
                        c.close();
                        db.close();
                        ItemInfo.changeStatus(ctxt, itemId, Item.STATUS_DOWNLOADING);
                        clientCallback();
                    }
                }
            } else {
                Toast.makeText(ctxt, R.string.toast_nomobiletraffic, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(ctxt, R.string.toast_notconnected, Toast.LENGTH_LONG).show();
        }
    }

    static class DownloadTask extends AsyncTask<String, Integer, Void> {

        private RemoteViews rv;
        private Notification nf;
        private int item_id;
        private String download_title;
        private WeakReference<NWService> mService;
        private String error_msg = null;
        private getPodcastFile task = null;

        public DownloadTask(NWService activity, String title, int itemId) {
            super();
            mService = new WeakReference<NWService>(activity);
            if (mService != null) {
                final NWService service = mService.get();
                if (service != null) {
                    service.notificationManager = (NotificationManager) service
                            .getSystemService(Context.NOTIFICATION_SERVICE);
                }
            }
            download_title = title;
            item_id = itemId;
        }

        @Override
        protected void onPreExecute() {
            final NWService service = mService.get();
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
            service.notificationManager.notify(item_id, nf);
        }

        @Override
        protected Void doInBackground(String... str) {
            int fs = 1;
            try {
                fs = Integer.parseInt(str[1]);
            } catch (NumberFormatException e) {
            }
            // Get Context
            Context ctxt = null;
            if (mService != null) {
                final NWService service = mService.get();
                if (service != null) {
                    ctxt = service.getApplicationContext();
                }
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
                    task = new getPodcastFile(ctxt, fs);
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
            if (mService != null) {
                final NWService service = mService.get();
                if (service != null) {
                    rv.setProgressBar(R.id.download_progress, 100, values[0], false);
                    rv.setTextViewText(R.id.download_status, values[0] + "% " + values[1] + "kB/s");
                    service.notificationManager.notify(item_id, nf);
                }
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            Log.v(TAG, "onPostExecute()");
            super.onPostExecute(unused);
            if (mService != null) {
                final NWService service = mService.get();
                if (service != null) {
                    if (error_msg != null) {
                        Toast.makeText(service.getApplicationContext(), error_msg,
                                Toast.LENGTH_LONG).show();
                    }
                    ItemInfo.changeStatus(service, item_id, Item.STATUS_DL_UNREAD);
                    finishNotification("Téléchargement terminé!", service
                            .getString(R.string.notif_dl_complete));
                    service.stopOrContinue();
                }
            }
        }

        @Override
        protected void onCancelled() {
            Log.v(TAG, "onCancelled()");
            if (mService != null) {
                final NWService service = mService.get();
                if (service != null) {
                    finishNotification("Téléchargement annulé!", service
                            .getString(R.string.notif_dl_canceled));
                    if (error_msg != null) {
                        Toast.makeText(service.getApplicationContext(), error_msg,
                                Toast.LENGTH_LONG).show();
                    }
                    ItemInfo.changeStatus(service, item_id, Item.STATUS_UNCOMPLETE);
                    service.stopOrContinue();
                }
            }
            super.onCancelled();
        }

        private void finishNotification(String title, String msg) {
            if (mService != null) {
                final NWService service = mService.get();
                if (service != null) {
                    try {
                        service.notificationManager.cancel(item_id);
                    } catch (Exception e) {
                        Log.v(TAG, e.getMessage());
                    } finally {
                        nf = new Notification(android.R.drawable.stat_sys_download_done, title,
                                System.currentTimeMillis());
                        nf.flags = Notification.FLAG_AUTO_CANCEL;
                        nf.setLatestEventInfo(service, download_title, msg, PendingIntent
                                .getActivity(service, 0, new Intent(service, ListItems.class), 0));
                        service.notificationManager.notify(item_id, nf);
                    }
                }
            }
        }

        class getPodcastFile extends GetFile {

            private long current_bytes = 0;
            private long file_size = 1;
            private int progress = 0;
            private long start;

            public getPodcastFile(final Context ctxt, long file_size) {
                super(ctxt);
                if (file_size > 0) {
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
     * Update
     */
    private static class UpdateTaskNotif extends UpdateTask {

        public UpdateTaskNotif(NWService s) {
            super(s);
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            final NWService service = getService();
            if (service != null) {
                SQLiteDatabase db = (new DB(service.getApplicationContext())).getWritableDatabase();
                Cursor c = db.rawQuery(REQ_NEW, null);
                c.moveToFirst();
                int nb = c.getInt(0);
                c.close();
                db.close();
                if (nb > 0) {
                    // Show notification about new items
                    Notification nf = new Notification(R.drawable.icon_scream_48,
                            "Nouveaux podcasts", System.currentTimeMillis());
                    nf.flags = Notification.FLAG_AUTO_CANCEL;
                    nf.setLatestEventInfo(service, "Podcasts disponibles", nb
                            + " nouveaux éléments", PendingIntent.getActivity(service, 0,
                            new Intent(service, ListItems.class), 0));
                    service.notificationManager.notify(NOTIFICATION_UPDATE, nf);
                    // TODO: Auto-download items
                }
                service.stopOrContinue();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            final NWService service = getService();
            if (service != null) {
                service.stopOrContinue();
            }
        }

    }

    /**
     * Service control (IPC) using AIDL interface
     */
    private final IService.Stub mBinder = new IService.Stub() {

        public void _registerCallback(IServiceCallback cb) {
            if (cb != null) {
                mCallbacks.register(cb);
            }
        }

        public void _unregisterCallback(IServiceCallback cb) {
            if (cb != null) {
                mCallbacks.unregister(cb);
            }
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