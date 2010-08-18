package nowatch.tv.network;

import java.lang.ref.WeakReference;

import nowatch.tv.service.NWService;
import nowatch.tv.utils.Prefs;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.preference.PreferenceManager;

public class Network {

    // private final static String TAG = Main.TAG + "Network";
    private ConnectivityManager manager;
    private WeakReference<Activity> mActivity = null;
    private WeakReference<Context> mContext = null;
    private WeakReference<NWService> mService = null;

    public Network(Activity activity) {
        mActivity = new WeakReference<Activity>(activity);
        final Activity a = (Activity) mActivity.get();
        if (a != null) {
            manager = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    public Network(NWService service) {
        mService = new WeakReference<NWService>(service);
        final NWService s = mService.get();
        if (s != null) {
            manager = (ConnectivityManager) s.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    public Network(Context ctxt) {
        mContext = new WeakReference<Context>(ctxt);
        final Context c = mContext.get();
        if (c != null) {
            manager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    public boolean isConnected() {
        NetworkInfo nfo = manager.getActiveNetworkInfo();
        if (nfo != null) {
            if (nfo.isConnected()) {
                return true;
            }
        }
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
        } else if (mContext != null) {
            final Context c = mContext.get();
            if (c != null) {
                return c.getApplicationContext();
            }
        }
        return null;
    }

}
