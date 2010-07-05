package nowatch.tv;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class DownloadManager extends Activity {

    private final String TAG = "DownloadManager";
    private static LayoutInflater mInflater;
    private DlAdapter adapterCurrent = null;
    private DlAdapter adapterPending = null;
    private List<Item> downloadCurrent;
    private List<Item> downloadPending;
    private int image_size;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(DownloadManager.this, DownloadService.class));
        setContentView(R.layout.manage_activity);
        mInflater = LayoutInflater.from(getApplicationContext());

        // Title button
        ((ImageButton) findViewById(R.id.btn_logo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        image_size = (int) (48 * dm.density + 0.5f);
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(DownloadManager.this, DownloadService.class), mConnection, 0);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
    }

    private OnItemClickListener listenerCurrent = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id){
            Log.v("current", "position="+position);
        }
    };

    private OnItemClickListener listenerPending = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id){
            Log.v("pending", "position="+position);
        }
    };

    private List<Item> getDownloads(int[] data) {
        SQLiteDatabase db = (new DB(getApplicationContext())).getWritableDatabase();
        List<Item> list = new ArrayList<Item>();
        int len = data.length;
        for (int i = 0; i < len; i++) {
            Cursor c = db.rawQuery("select items.title, feeds.image from items inner join feeds on items.feed_id=feeds._id where items._id=" + data[i] + " limit 1", null);
            c.moveToFirst();
            if(c.getCount() > 0){
                Item item = new Item();
                item.id = data[i];
                item.title = c.getString(0);
                byte[] logo_byte = c.getBlob(1);
                if (logo_byte != null && logo_byte.length > 200) {
                    item.logo = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_byte,
                            0, logo_byte.length), image_size, image_size, true);
                } else {
                    item.logo = BitmapFactory.decodeResource(getResources(), R.drawable.icon);
                }
                list.add(item);
            }
            c.close();
        }
        db.close();
        return list;
    }

    private void populateLists(){
        try {
            // Get data
            downloadCurrent = getDownloads(mService._getCurrentDownloads());
            downloadPending = getDownloads(mService._getPendingDownloads());

            Log.i(TAG, "downloadCurrent=" + downloadCurrent.size());
            Log.i(TAG, "downloadPending=" + downloadPending.size());

            if (downloadCurrent.size() > 0) {
                // Populate Lists
                final Context ctxt = getApplicationContext();
                adapterCurrent = new DlAdapter(ctxt, downloadCurrent);
                adapterPending = new DlAdapter(ctxt, downloadPending);
                ListView listCurrent = (ListView) findViewById(R.id.list_current);
                ListView listPending = (ListView) findViewById(R.id.list_pending);
                listCurrent.setAdapter(adapterCurrent);
                listPending.setAdapter(adapterPending);
                listCurrent.setOnItemClickListener(listenerCurrent);
                listPending.setOnItemClickListener(listenerPending);
            } else {
                // No download
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Service Binding
     */
    private DownloadInterface mService = null;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = DownloadInterface.Stub.asInterface(service);
            if (mService != null) {
                populateLists();
            } else {
                Log.d(TAG, "Service is null");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    /**
     * Adapters
     */

    static class ViewHolder {
        ImageView logo;
        TextView title;
    }

    private static class DlAdapter extends ArrayAdapter<Item> {

        private List<Item> items;

        public DlAdapter(Context ctxt, List<Item> items) {
            super(ctxt, R.layout.list_download, R.id.title, items);
            this.items = items;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_download, null);
                holder = new ViewHolder();
                holder.logo = (ImageView) convertView.findViewById(R.id.logo);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Item item = items.get(position);
            holder.title.setText(item.title);
            holder.logo.setImageBitmap(item.logo);
            return convertView;
        }
    }
}
