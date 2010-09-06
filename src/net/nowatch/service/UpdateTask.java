package net.nowatch.service;

import java.io.IOException;
import java.lang.ref.WeakReference;

import net.nowatch.Main;
import net.nowatch.R;
import net.nowatch.network.Network;
import net.nowatch.ui.ListItems;
import net.nowatch.utils.Db;
import net.nowatch.utils.UpdateDb;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class UpdateTask extends AsyncTask<Void, Void, Void> {

    private static final String TAG = Main.TAG + "UpdateTask";
    private static final String REQ = "SELECT _id, type, link_rss FROM feeds";
    private boolean sdcarderror = false;
    protected WeakReference<ListItems> mActivity = null;
    protected WeakReference<NWService> mService = null;

    public UpdateTask(ListItems activity) {
        mActivity = new WeakReference<ListItems>(activity);
    }

    public UpdateTask(NWService service) {
        mService = new WeakReference<NWService>(service);
    }

    @Override
    protected void onPreExecute() {
        final Context ctxt = getContext();
        final Network net = new Network(ctxt);
        if (!net.isConnected()) {
            Toast.makeText(ctxt, R.string.toast_notconnected, Toast.LENGTH_SHORT).show();
            cancel(false);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        final Context ctxt = getContext();
        SQLiteDatabase db = new Db(ctxt).openDb();
        Cursor c = db.rawQuery(REQ, null);
        c.moveToFirst();
        try {
            if (c.getCount() > 0) {
                do {
                    // FIXME: Pass all information to UpdateDb.update() so this
                    // method does not need to reopen a db cursor
                    UpdateDb.update(ctxt, c.getInt(0), c.getInt(1), c.getString(2));
                } while (c.moveToNext());
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            sdcarderror = true;
        } finally {
            c.close();
            db.close();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void unused) {
        if (sdcarderror) {
            final Context ctxt = getContext();
            Toast.makeText(ctxt, R.string.toast_sdcard, Toast.LENGTH_SHORT).show();
        }
    }

    protected Context getContext() {
        if (mActivity != null) {
            final Activity a = mActivity.get();
            if (a != null) {
                return a.getApplicationContext();
            }
        } else if (mService != null) {
            final Service s = mService.get();
            if (s != null) {
                return s.getApplicationContext();
            }
        }
        return null;
    }

    protected ListItems getActivity() {
        if (mActivity != null) {
            final ListItems a = mActivity.get();
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    protected NWService getService() {
        if (mService != null) {
            final NWService s = mService.get();
            if (s != null) {
                return s;
            }
        }
        return null;
    }
}
