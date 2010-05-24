package nowatch.tv;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ItemsActivity extends Activity {

    // private final String TAG = "ItemsActivity";
    private final String QUERY_ITEMS = "SELECT items._id, items.title, items.status, feeds.image, items.pubDate "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id "
            + "ORDER BY items.pubDate DESC LIMIT ";
    private static final int MENU_UPDATE_ALL = 1;
    private static final int MENU_MANAGE = 2;
    private int image_size;
    private ItemsAdapter adapter;
    private UpdateTask updateTask = null;
    private Context ctxt;
    private List<Feed> feeds;
    private List<Item> items = null;
    private ListView list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        setContentView(R.layout.items_activity);

        // Add all feeds
        feeds = new ArrayList<Feed>();
        feeds.add(new Feed(1, R.string.feed_cinefuzz));
        feeds.add(new Feed(2, R.string.feed_geekinc));
        feeds.add(new Feed(3, R.string.feed_scuds));
        feeds.add(new Feed(4, R.string.feed_zapcast));
        feeds.add(new Feed(5, R.string.feed_tom));

        // Screen metrics (for dip to px conversion)
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        image_size = (int) (48 * displayMetrics.density + 0.5f);

        // Title button
        ((ImageButton) findViewById(R.id.btn_logo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                resetList();
            }
        });

        // Set list adapter
        items = new ArrayList<Item>();
        adapter = new ItemsAdapter();
        list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(false);
        ((TextView) findViewById(R.id.loading)).setVisibility(View.INVISIBLE);

        if (addToList(0, 12) == 0) {
            updateTask = new UpdateTask(ItemsActivity.this);
            updateTask.execute();
        }
        updateList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_UPDATE_ALL, 0, R.string.menu_update_all).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_MANAGE, 0, R.string.menu_manage)
                .setIcon(android.R.drawable.ic_menu_manage);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UPDATE_ALL:
                updateTask = new UpdateTask(ItemsActivity.this);
                updateTask.execute();
                return true;
            case MENU_MANAGE:
                startActivity(new Intent(ItemsActivity.this, DownloadManager.class));
                return true;
        }
        return false;
    }

    class UpdateTask extends AsyncTask<Void, Void, Void> {

        private ProgressDialog progress;
        private WeakReference<Activity> mActivity;
        private boolean sdcarderror = false;

        public UpdateTask(Activity activity) {
            mActivity = new WeakReference<Activity>(activity);
        }

        @Override
        protected void onPreExecute() {
            final Activity a = mActivity.get();
            progress = ProgressDialog.show(a, "", getString(R.string.dialog_update_all));
        }

        @Override
        protected Void doInBackground(Void... params) {
            final Activity a = mActivity.get();
            try {
                for (Feed f : feeds) {
                    UpdateDb.update(a.getApplicationContext(), "" + f._id, f._resource);
                }
            } catch (IOException e) {
                Log.e("UpdateTask", e.getMessage());
                sdcarderror = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            resetList();
            ((TextView) findViewById(R.id.loading)).setVisibility(View.INVISIBLE);
            progress.dismiss();
            if (sdcarderror) {
                Toast.makeText(ctxt, R.string.toast_sdcard, Toast.LENGTH_LONG).show();
            }
        }
    }

    private int addToList(int offset, int limit) {
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        Cursor c = db.rawQuery(QUERY_ITEMS + offset + "," + limit, null);
        byte[] logo_byte;
        int cnt = 0;
        try {
            cnt = c.getCount();
            if (cnt > 0) {
                c.moveToFirst();
                do {
                    final Item item = new Item();
                    item.id = c.getInt(0);
                    item.title = c.getString(1);
                    // Status
                    switch (c.getInt(2)) {
                        case Item.STATUS_NEW:
                            item.status = getString(R.string.status_new);
                            break;
                        case Item.STATUS_DOWNLOADING:
                            item.status = getString(R.string.status_downloading);
                            break;
                        case Item.STATUS_UNREAD:
                            item.status = getString(R.string.status_unread);
                            break;
                        case Item.STATUS_READ:
                            item.status = getString(R.string.status_read);
                            break;
                        default:
                            item.status = getString(R.string.status_new);
                    }
                    // Icon
                    logo_byte = c.getBlob(3);
                    if (logo_byte != null && logo_byte.length > 200) {
                        item.logo = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(
                                logo_byte, 0, logo_byte.length), image_size, image_size, true);
                    } else {
                        item.logo = BitmapFactory.decodeResource(getResources(), R.drawable.icon);
                    }
                    // Date
                    SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");
                    formatter.setTimeZone(TimeZone.getDefault());
                    item.date = formatter.format(new Date(c.getLong(4)));
                    // Actions
                    item.action = new View.OnClickListener() {
                        public void onClick(View v) {
                            Intent i = new Intent(ctxt, InfoActivity.class);
                            i.putExtra("item_id", item.id);
                            startActivity(i);
                        }
                    };

                    // Add the item
                    items.add(item);
                } while (c.moveToNext());
            }
        } catch (SQLiteDiskIOException e) {
            // sqlite_stmt_journals partition is too small (4MB)
            e.printStackTrace();
        }
        c.close();
        db.close();
        return cnt;
    }

    private void resetList() {
        if (updateTask != null) {
            updateTask.cancel(true);
        }
        items.clear();
        adapter.clear();
        addToList(0, 12);
        updateList();
        list.setSelection(0);
    }

    private void updateList() {
        int len = items.size();
        for (int i = adapter.getCount(); i < len; i++) {
            adapter.add(null);
        }
    }

    private class ItemsAdapter extends ArrayAdapter<Item> {

        private LayoutInflater inflater;

        private class ViewHolder {
            TextView title;
            TextView status;
            TextView date;
            ImageView logo;
            ImageButton action;
        }

        public ItemsAdapter() {
            super(ctxt, R.layout.list_items, R.id.title);
            inflater = LayoutInflater.from(ctxt);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_items, parent, false);
                vh = new ViewHolder();
                vh.title = (TextView) convertView.findViewById(R.id.title);
                vh.status = (TextView) convertView.findViewById(R.id.status);
                vh.date = (TextView) convertView.findViewById(R.id.date);
                vh.logo = (ImageView) convertView.findViewById(R.id.logo);
                vh.action = (ImageButton) convertView.findViewById(R.id.btn_actions);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            // Set information
            final Item item = items.get(position);
            vh.title.setText(item.title);
            vh.status.setText(item.status);
            vh.date.setText(item.date);
            vh.logo.setImageBitmap(item.logo);
            vh.action.setOnClickListener(item.action);
            // Set endless loader
            if (position == items.size() - 3) {
                new EndlessTask().execute(position + 1);
            }
            return convertView;
        }
    }

    class EndlessTask extends AsyncTask<Integer, Void, Void> {

        @Override
        protected void onPreExecute() {
            ((TextView) findViewById(R.id.loading)).setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Integer... params) {
            addToList(params[0], 12);
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            updateList();
            ((TextView) findViewById(R.id.loading)).setVisibility(View.INVISIBLE);
        }

    }
}
