package nowatch.tv;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

class UpdateTask extends AsyncTask<Void, Void, Void> {

    private static final String TAG = Main.TAG + "UpdateTask";
    private WeakReference<ItemsActivity> mActivity = null;
    private WeakReference<DownloadService> mService = null;
    private boolean sdcarderror = false;
    private List<Feed> feeds;

    public UpdateTask(ItemsActivity activity) {
        mActivity = new WeakReference<ItemsActivity>(activity);
        initFeeds();
    }

    public UpdateTask(DownloadService service) {
        mService = new WeakReference<DownloadService>(service);
        initFeeds();
    }

    private void initFeeds(){
        // Add all feeds
        feeds = new ArrayList<Feed>();
        feeds.add(new Feed(1, R.string.feed_cinefuzz));
        feeds.add(new Feed(2, R.string.feed_geekinc));
        feeds.add(new Feed(3, R.string.feed_scuds));
        feeds.add(new Feed(4, R.string.feed_zapcast));
        feeds.add(new Feed(5, R.string.feed_tom));
        feeds.add(new Feed(6, R.string.feed_revuetech));
    }

    @Override
    protected void onPreExecute() {
        final Context ctxt = getContext();
        final Network net = new Network(ctxt);
        if (net.isConnected()) {
            if (mActivity != null) {
                Button btn_ref = (Button) getActivity().findViewById(R.id.btn_refresh);
                btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh_a, 0, 0, 0);
                ((AnimationDrawable) btn_ref.getCompoundDrawables()[0]).start();
                btn_ref.setEnabled(false);
                btn_ref.setClickable(false);
            }
        } else {
            Toast.makeText(ctxt, R.string.toast_notconnected, Toast.LENGTH_SHORT).show();
            cancel(false);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        final Context ctxt = getContext();
        try {
            for (Feed f : feeds) {
                UpdateDb.update(ctxt, "" + f._id, f._resource);
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            sdcarderror = true;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void unused) {
        final Context ctxt = getContext();
        if (mActivity != null) {
            ItemsActivity a = getActivity();
            Button btn_ref = (Button) a.findViewById(R.id.btn_refresh);
            btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0, 0);
            btn_ref.setEnabled(true);
            btn_ref.setClickable(true);
            a.findViewById(R.id.loading).setVisibility(View.INVISIBLE);
            a.resetList();
        }
        if (sdcarderror) {
            Toast.makeText(ctxt, R.string.toast_sdcard, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (mActivity != null) {
            final Context ctxt = getContext();
            Button btn_refresh = (Button) getActivity().findViewById(R.id.btn_refresh);
            btn_refresh.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0, 0);
            btn_refresh.setEnabled(true);
            btn_refresh.setClickable(true);
        }
    }

    protected Context getContext(){
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

    protected ItemsActivity getActivity(){
        if (mActivity != null) {
            final ItemsActivity a = mActivity.get();
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    protected DownloadService getService(){
        if (mService != null) {
            final DownloadService s = mService.get();
            if (s != null) {
                return s;
            }
        }
        return null;
    }

}

