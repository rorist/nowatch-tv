package nowatch.tv;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class Network {

    private ConnectivityManager manager;
    private WeakReference<Activity> mActivity = null;
    private WeakReference<Context> mContext = null;
    private WeakReference<DownloadService> mService = null;

    public Network(Activity activity) {
        mActivity = new WeakReference<Activity>(activity);
        final Activity a = (Activity) mActivity.get();
        manager = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public Network(DownloadService service) {
        mService = new WeakReference<DownloadService>(service);
        final DownloadService s = mService.get();
        manager = (ConnectivityManager) s.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public Network(Context ctxt) {
        mContext = new WeakReference<Context>(ctxt);
        final Context c = mContext.get();
        if(c != null){
            manager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    public boolean isConnected() {
        NetworkInfo nfo = manager.getActiveNetworkInfo();
        if (nfo != null) {
            if(nfo.isConnected()){
                return true;
            }
        }
        Toast.makeText(getContext(), R.string.toast_notconnected, Toast.LENGTH_LONG).show();
        return false;
    }

    public boolean isMobileAllowed() {
        // Return connection status if not in mobile mode
        if (getConnectionType() == ConnectivityManager.TYPE_MOBILE) {
            final Context ctxt = getContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
            if (prefs.getBoolean(Prefs.KEY_MOBILE_TRAFFIC, Prefs.DEFAULT_MOBILE_TRAFFIC)) {
                return true;
            }
            Toast.makeText(ctxt, R.string.toast_nomobiletraffic, Toast.LENGTH_LONG).show();
            return false;
        }
        return isConnected();
    }

    private int getConnectionType() {
        State mobile = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
        if (mobile == NetworkInfo.State.CONNECTED || mobile == NetworkInfo.State.CONNECTING) {
            return ConnectivityManager.TYPE_MOBILE;
        }
        State wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
        if (wifi == NetworkInfo.State.CONNECTED || wifi == NetworkInfo.State.CONNECTING) {
            return ConnectivityManager.TYPE_WIFI;
        }
        return -1;
    }

    private Context getContext() {
        // FIXME: It's not safe to use get() without checking for null
        if (mActivity != null) {
            return mActivity.get().getApplicationContext();
        } else if (mService != null) {
            return mService.get().getApplicationContext();
        } else if (mContext != null) {
            return mContext.get().getApplicationContext();
        }
        return null;
    }

}
