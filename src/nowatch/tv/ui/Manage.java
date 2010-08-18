package nowatch.tv.ui;

import java.util.ArrayList;
import java.util.List;

import nowatch.tv.DownloadInterface;
import nowatch.tv.DownloadInterfaceCallback;
import nowatch.tv.Main;
import nowatch.tv.R;
import nowatch.tv.service.NWService;
import nowatch.tv.utils.DB;
import nowatch.tv.utils.Item;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class Manage extends Activity {

    private final String TAG = Main.TAG + "DownloadManager";
    private static LayoutInflater mInflater;
    private DlAdapter adapterCurrent = null;
    private DlAdapter adapterPending = null;
    private List<Item> downloadCurrent;
    private List<Item> downloadPending;
    private int image_size;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(Manage.this, NWService.class));
        setContentView(R.layout.manage_activity);
        mInflater = LayoutInflater.from(getApplicationContext());

        // Buttons
        findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_logo).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        image_size = (int) (48 * dm.density + 0.5f);

        // Empty list
        ListView listCurrent = (ListView) findViewById(R.id.list_current);
        ListView listPending = (ListView) findViewById(R.id.list_pending);
        listCurrent.setEmptyView(findViewById(R.id.list_current_empty));
        listPending.setEmptyView(findViewById(R.id.list_pending_empty));
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(Manage.this, NWService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
    }

    private OnItemClickListener listenerCurrent = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            cancelDialog(position, NWService.TYPE_CURRENT);
        }
    };

    private OnItemClickListener listenerPending = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            cancelDialog(position, NWService.TYPE_PENDING);
        }
    };

    private void cancelDialog(final int position, final int type) {
        final Context ctxt = this;
        final AlertDialog.Builder dialog = new AlertDialog.Builder(ctxt);
        dialog.setMessage("Voulez-vous annuler le téléchargement ?");
        dialog.setPositiveButton("Oui", new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Item item = null;
                // Remove item from list
                if (type == NWService.TYPE_CURRENT) {
                    item = downloadCurrent.get(position);
                } else if (type == NWService.TYPE_PENDING) {
                    item = downloadPending.get(position);
                }
                // Send intent to service
                Intent intent = new Intent(ctxt, NWService.class);
                intent.setAction(NWService.ACTION_CANCEL);
                intent.putExtra(Item.EXTRA_ITEM_ID, item.id);
                intent.putExtra(Item.EXTRA_ITEM_POSITION, position);
                intent.putExtra(Item.EXTRA_ITEM_TYPE, type);
                startService(intent);
            }
        });
        dialog.setNegativeButton("Non", new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        dialog.show();
    }

    private List<Item> getDownloads(int[] data) {
        SQLiteDatabase db = (new DB(getApplicationContext())).getWritableDatabase();
        List<Item> list = new ArrayList<Item>();
        int len = data.length;
        for (int i = 0; i < len; i++) {
            Cursor c = db
                    .rawQuery(
                            "select items.title, feeds.image from items inner join feeds on items.feed_id=feeds._id where items._id="
                                    + data[i] + " limit 1", null);
            c.moveToFirst();
            if (c.getCount() > 0) {
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

    private void populateLists() {
        try {
            if (mService != null) {
                // Get data
                downloadCurrent = getDownloads(mService._getCurrentDownloads());
                downloadPending = getDownloads(mService._getPendingDownloads());

                Log.i(TAG, "downloadCurrent=" + downloadCurrent.size());
                Log.i(TAG, "downloadPending=" + downloadPending.size());

                // Populate Lists
                if (adapterCurrent == null || adapterPending == null) {
                    // Create adapters
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
                    // Update adapter
                    adapterCurrent.notifyDataSetChanged();
                    adapterPending.notifyDataSetChanged();
                }
            }
        } catch (RemoteException e) {
            if (e.getMessage() != null) {
                Log.e(TAG, e.getMessage());
            } else {
                Log.e(TAG, "RemoteException");
            }
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
                try {
                    mService._registerCallback(mCallback);
                    populateLists();
                } catch (RemoteException e) {
                }
            } else {
                Toast.makeText(getApplicationContext(), "Service inaccessible", Toast.LENGTH_SHORT)
                        .show();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            try {
                mService._unregisterCallback(mCallback);
            } catch (RemoteException e) {
            }
            mService = null;
            Toast.makeText(getApplicationContext(), "Service deconnecté", Toast.LENGTH_SHORT)
                    .show();
        }
    };

    private DownloadInterfaceCallback mCallback = new DownloadInterfaceCallback.Stub() {
        public void _valueChanged() {
            mHandler.sendMessage(mHandler.obtainMessage());
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            populateLists();
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
