package nowatch.tv;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class ItemsActivity extends Activity implements OnItemClickListener {

    // private final String TAG = "ItemsActivity";
    private final String QUERY_ITEMS = "SELECT items._id, items.title, feeds.title, image "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id "
            + "ORDER BY date(items.pubDate) DESC LIMIT 16";
    private static final int MENU_UPDATE_ALL = 1;
    private final int IMG_DIP = 64;
    private DisplayMetrics displayMetrics;
    private ItemsAdapter adapter;
    private Context ctxt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        setContentView(R.layout.items_activity);

        // Screen metrics (for dip to px conversion)
        displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Podcasts/Items from DB
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        adapter = new ItemsAdapter(ctxt, db.rawQuery(QUERY_ITEMS, null));
        ListView list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(true);
        list.setOnItemClickListener(this);
        db.close();
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

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i = new Intent(ctxt, InfoActivity.class);
        i.putExtra("item_id", id);
        startActivity(i);
    }

    private void updateQuery() {
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        adapter.changeCursor(db.rawQuery(QUERY_ITEMS, null));
        adapter.notifyDataSetChanged();
        db.close();
    }

    class Items {
        public Bitmap image;
        public String title;
        public String podcast;
    }

    private class ItemsAdapter extends CursorAdapter {

        private LayoutInflater inflater;

        public ItemsAdapter(Context context, Cursor c) {
            super(context, c, false);
            inflater = LayoutInflater.from(ctxt);
            if (c.getCount() == 0) {
                (new UpdateTask(ItemsActivity.this)).execute();
            }
        }

        class ViewHolder {
            TextView title;
            TextView podcast;
            ImageView logo;
        }

        @Override
        public void bindView(View view, Context context, Cursor c) {
            // FIXME: Is it worth using a Viewholder ?
            ViewHolder vh = (ViewHolder) view.getTag();
            vh.title.setText(c.getString(1));
            vh.podcast.setText(c.getString(2));
            byte[] logo_byte = c.getBlob(3);
            if (logo_byte != null && logo_byte.length > 200) {
                vh.logo.setImageBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(
                        logo_byte, 0, logo_byte.length),
                        (int) (IMG_DIP * displayMetrics.density + 0.5f), (int) (IMG_DIP
                                * displayMetrics.density + 0.5f), true));
            } else {
                vh.logo.setImageResource(R.drawable.icon);
            }
        }

        @Override
        public View newView(Context context, Cursor c, ViewGroup parent) {
            // Do not use Cursor here, bindView() is called anyway!
            View v = inflater.inflate(R.layout.list_items, parent, false);
            ViewHolder vh = new ViewHolder();
            vh.title = (TextView) v.findViewById(R.id.title);
            vh.podcast = (TextView) v.findViewById(R.id.podcast);
            vh.logo = (ImageView) v.findViewById(R.id.logo);
            v.setTag(vh);
            return v;
        }
    }

    class UpdateTask extends AsyncTask<Void, Void, Void> {

        private ProgressDialog progress;
        private WeakReference<Activity> mActivity;

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
            UpdateDb.update(a.getApplicationContext(), "1", R.string.feed_cinefuzz);
            UpdateDb.update(a.getApplicationContext(), "2", R.string.feed_geekinc);
            UpdateDb.update(a.getApplicationContext(), "3", R.string.feed_scuds);
            UpdateDb.update(a.getApplicationContext(), "4", R.string.feed_zapcast);
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            updateQuery();
            progress.dismiss();
        }
    }
}
