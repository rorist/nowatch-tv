package nowatch.tv;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class DownloadManager extends Activity {

    private final String TAG = "DownloadManager";
    private DlAdapter adapterCurrent = null;
    private DlAdapter adapterPending = null;
    private List<Item> downloadCurrent;
    private List<Item> downloadPending;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(new Intent(DownloadManager.this, DownloadService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        Context ctxt = getApplicationContext();
        if (mService != null) {
            try {
                downloadCurrent = getDownloads(mService._getCurrentDownloads());
                downloadPending = getDownloads(mService._getPendingDownloads());
                adapterCurrent = new DlAdapter(ctxt, R.layout.list_current, downloadCurrent);
                adapterPending = new DlAdapter(ctxt, R.layout.list_current, downloadPending);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            Log.d(TAG, "Service is null");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
    }

    private List<Item> getDownloads(int[] data) {
        List<Item> list = new ArrayList<Item>();
        int len = data.length;
        for (int i = 0; i < len; i++) {
            list.add(new Item(data[i]));
        }
        return list;
    }

    /**
     * Service Binding
     */
    private DownloadInterface mService = null;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = DownloadInterface.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    /**
     * Adapters
     */
    private class DlAdapter extends ArrayAdapter<Item> {

        public DlAdapter(Context ctxt, int layout, List<Item> items) {
            super(ctxt, layout, items);
        }

        public DlAdapter(Context ctxt, int layout) {
            super(ctxt, layout, R.id.title);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            return convertView;
        }
    }
}
