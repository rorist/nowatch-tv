package nowatch.tv;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ItemsActivity extends Activity {

    // private final String TAG = "ItemsActivity";
    private final String QUERY_ITEMS = "SELECT items._id, items.title, feeds.title, image "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id "
            + "ORDER BY date(items.pubDate) DESC LIMIT 16";
    private static final int MENU_UPDATE_ALL = 1;
    private ItemsAdapter adapter;
    private Context ctxt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        setContentView(R.layout.items_activity);

        // Podcasts/Items from DB
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        adapter = new ItemsAdapter(ctxt, db.rawQuery(QUERY_ITEMS, null));
        ListView list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(true);
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

        @Override
        public void bindView(View view, Context context, Cursor c) {
            TextView title = (TextView) view.findViewById(R.id.title);
            TextView podcast = (TextView) view.findViewById(R.id.podcast);
            ImageView logo = (ImageView) view.findViewById(R.id.logo);
            title.setText(c.getString(1));
            podcast.setText(c.getString(2));
            byte[] logo_byte = c.getBlob(3);
            if (logo_byte != null && logo_byte.length > 200) {
                logo.setImageBitmap(BitmapFactory.decodeByteArray(logo_byte, 0, logo_byte.length));
            } else {
                logo.setImageResource(R.drawable.icon);
            }
        }

        @Override
        public View newView(Context context, Cursor c, ViewGroup parent) {
            // Do not use Cursor here, bindView() is called anyway!
            return inflater.inflate(R.layout.list_items, parent, false);
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
            UpdateDb.update(a.getApplicationContext(), "1", R.string.feed_cinefuzz);
            UpdateDb.update(a.getApplicationContext(), "2", R.string.feed_geekinc);
            UpdateDb.update(a.getApplicationContext(), "3", R.string.feed_scuds);
            UpdateDb.update(a.getApplicationContext(), "4", R.string.feed_zapcast);
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            progress.dismiss();
            adapter.notifyDataSetChanged();
        }
    }
}
