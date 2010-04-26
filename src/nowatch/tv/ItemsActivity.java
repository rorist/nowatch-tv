package nowatch.tv;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ItemsActivity extends Activity implements OnItemClickListener {

    // private final String TAG = "ItemsActivity";
    private final String QUERY_ITEMS = "SELECT items._id, items.title, feeds.title, image "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id "
            + "ORDER BY items.pubDate DESC LIMIT ";
    private static final int MENU_UPDATE_ALL = 1;
    private int image_size;
    private ItemsAdapter adapter;
    private Context ctxt;
    private List<Items> items = null;
    private ListView list;

    class Items {
        public long id;
        public Bitmap image;
        public String title;
        public String podcast;
        public Bitmap logo;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        setContentView(R.layout.items_activity);

        // Screen metrics (for dip to px conversion)
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        image_size = (int) (64 * displayMetrics.density + 0.5f);

        // Set list adapter
        items = new ArrayList<Items>();
        adapter = new ItemsAdapter();
        list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(true);
        list.setOnItemClickListener(this);
        ((TextView) findViewById(R.id.loading)).setVisibility(View.INVISIBLE);

        if (addToList(0, 12) == 0) {
            (new UpdateTask(ItemsActivity.this)).execute();
        }
        updateList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_UPDATE_ALL, 0, "Mettre a jour").setIcon(android.R.drawable.ic_menu_upload);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UPDATE_ALL:
                (new UpdateTask(ItemsActivity.this)).execute();
                return true;
        }
        return false;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i = new Intent(ctxt, InfoActivity.class);
        i.putExtra("item_id", items.get(position).id);
        startActivity(i);
    }

    private int addToList(int offset, int limit) {
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        Cursor c = db.rawQuery(QUERY_ITEMS + offset + "," + limit, null);
        Items item;
        byte[] logo_byte;
        int cnt = 0;
        try {
            cnt = c.getCount();
            if (cnt > 0) {
                c.moveToFirst();
                do {
                    item = new Items();
                    item.id = c.getInt(0);
                    item.title = c.getString(1);
                    item.podcast = c.getString(2);
                    logo_byte = c.getBlob(3);
                    if (logo_byte != null && logo_byte.length > 200) {
                        item.logo = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(
                                logo_byte, 0, logo_byte.length), image_size, image_size, true);
                    } else {
                        item.logo = BitmapFactory.decodeResource(getResources(), R.drawable.icon);
                    }
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

    private void updateList() {
        for (int i = adapter.getCount(); i < items.size(); i++) {
            adapter.add(null);
        }
    }

    private class ItemsAdapter extends ArrayAdapter<Items> {

        private LayoutInflater inflater;

        public ItemsAdapter() {
            super(ctxt, R.layout.list_items, R.id.title);
            inflater = LayoutInflater.from(ctxt);
        }

        class ViewHolder {
            TextView title;
            TextView podcast;
            ImageView logo;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_items, parent, false);
                vh = new ViewHolder();
                vh.title = (TextView) convertView.findViewById(R.id.title);
                vh.podcast = (TextView) convertView.findViewById(R.id.podcast);
                vh.logo = (ImageView) convertView.findViewById(R.id.logo);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            // Set information
            final Items item = items.get(position);
            vh.title.setText(item.title);
            vh.podcast.setText(item.podcast);
            vh.logo.setImageBitmap(item.logo);
            // Set endless loader
            if (position == items.size() - 1) {
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
            progress = ProgressDialog.show(a, "", "Mise a jour des fluxxx ... ");
        }

        @Override
        protected Void doInBackground(Void... params) {
            final Activity a = mActivity.get();
            // UpdateDb.update(a.getApplicationContext(), "4",
            // R.string.feed_test);
            try {
                UpdateDb.update(a.getApplicationContext(), "1", R.string.feed_cinefuzz);
                UpdateDb.update(a.getApplicationContext(), "2", R.string.feed_geekinc);
                UpdateDb.update(a.getApplicationContext(), "3", R.string.feed_scuds);
                UpdateDb.update(a.getApplicationContext(), "4", R.string.feed_zapcast);
            } catch (IOException e) {
                Log.e("UpdateTask", e.getMessage());
                sdcarderror = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            items.clear();
            adapter.clear();
            addToList(0, 12);
            updateList();
            list.setSelection(0);
            ((TextView) findViewById(R.id.loading)).setVisibility(View.INVISIBLE);
            progress.dismiss();
            if (sdcarderror) {
                Toast.makeText(ctxt, "SDCard is not accessible !", Toast.LENGTH_LONG).show();
            }
        }
    }
}
