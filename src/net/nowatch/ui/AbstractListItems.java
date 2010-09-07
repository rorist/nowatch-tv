package net.nowatch.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.HashMap;
import java.util.Map;

import net.nowatch.R;
import net.nowatch.Main;
import net.nowatch.utils.Db;
import net.nowatch.utils.Item;
import android.app.Activity;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public abstract class AbstractListItems extends Activity implements OnItemClickListener {

    // private static final String TAG = Main.TAG + "AbstractListItems";
    private static final int ITEMS_NB = 16;
    private static final int ENDLESS_OFFSET = 3;
    private static final String REQ_FEEDS_IMAGE = "SELECT _id, image FROM feeds WHERE type=";
    private Map<Integer, byte[]> podcasts_images;

    protected int podcast_type;
    protected int image_size;
    protected Context ctxt;
    protected List<Item> items = null;
    protected ListView list;
    protected ItemsAdapter adapter;

    protected abstract int addToList(int offset, int limit);

    protected abstract int addToList(int offset, int limit, boolean update);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        podcast_type = getIntent().getExtras().getInt(Main.EXTRA_TYPE);
        setContentView(R.layout.activity_list_items);

        // Get Podcasts images
        SQLiteDatabase db = (new Db(ctxt)).openDb();
        Cursor c = db.rawQuery(REQ_FEEDS_IMAGE + podcast_type, null);
        if (c.moveToFirst()) {
            podcasts_images = new HashMap<Integer, byte[]>();
            do {
                podcasts_images.put(c.getInt(0), c.getBlob(1));
            } while(c.moveToNext());
        }
        c.close();
        db.close();

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        image_size = (int) (64 * dm.density + 0.5f);

        // Set list adapter
        items = new ArrayList<Item>();
        adapter = new ItemsAdapter();
        list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(false);
        list.setOnItemClickListener(this);
        list.setEmptyView(findViewById(R.id.list_empty));
        findViewById(R.id.loading).setVisibility(View.INVISIBLE);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // TODO: Handle configuration state save
        return super.onRetainNonConfigurationInstance();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i = new Intent(ctxt, ItemInfo.class);
        i.putExtra(Item.EXTRA_ITEM_ID, items.get(position).id);
        startActivity(i);
    }

    protected Item updateItemStatus(Item item, Cursor c) {
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
            case Item.STATUS_DL_UNREAD:
                item.status = getString(R.string.status_unread);
                break;
            case Item.STATUS_DL_READ:
                item.status = getString(R.string.status_read);
                break;
            case Item.STATUS_INCOMPLETE:
                item.status = getString(R.string.status_incomplete);
                break;
            default:
                item.status = getString(R.string.status_new);
        }
        return item;
    }

    private Bitmap createImage(int feed_id, byte[] logo_item_byte, int width, int height) {
        final int min_size = 200;
        if (logo_item_byte != null && logo_item_byte.length > min_size) {
            return Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_item_byte, 0,
                    logo_item_byte.length), image_size, image_size, true);
        } else {
            byte[] logo_podcast_byte = podcasts_images.get(feed_id);
            if (logo_podcast_byte != null && logo_podcast_byte.length > min_size) {
                return Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_podcast_byte,
                        0, logo_podcast_byte.length), image_size, image_size, true);
            } else {
                return BitmapFactory.decodeResource(getResources(), R.drawable.icon);
            }
        }
    }

    protected Item createItem(Cursor c) {
        final Item item = new Item();
        item.id = c.getInt(0);
        item.title = c.getString(1);
        // Status
        item.status = updateItemStatus(item, c).status;
        // Icon
        item.logo = createImage(c.getInt(4), c.getBlob(5), image_size, image_size);
        // Date
        long date = c.getLong(3);
        long diff = System.currentTimeMillis() / 1000 - date / 1000;
        if (diff < 3600) { // 1h
            item.date = getString(R.string.date_hour);
        } else if (diff < 86400) { // 24h
            item.date = String.format(getString(R.string.date_hours), (diff / 60 / 60));
        } else if (diff < 2678400) { // 31 days
            item.date = String.format(getString(R.string.date_days), (diff / 60 / 60 / 24));
            /*
             * } else if (diff < 7776000) { // 3 monthes item.date =
             * String.format(getString(R.string.date_monthes), (diff / 60 / 60 /
             * 24 / 30));
             */
        } else {
            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");
            formatter.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
            item.date = formatter.format(new Date(date));
        }
        // Actions
        // item.action = new View.OnClickListener() {
        // public void onClick(View v) {
        // Intent i = new Intent(ctxt, InfoActivity.class);
        // i.putExtra("item_id", item.id);
        // startActivity(i);
        // }
        // };
        return item;
    }

    public void resetList() {
        items.clear();
        adapter.clear();
        addToList(0, ITEMS_NB);
        updateList();
        list.setSelection(0);
    }

    private void updateList() {
        int len = items.size();
        for (int i = adapter.getCount(); i < len; i++) {
            adapter.add(null);
        }
    }

    static class ViewHolder {
        TextView title;
        TextView status;
        TextView date;
        ImageView logo;
        // ImageButton action;
    }

    protected class ItemsAdapter extends ArrayAdapter<Item> implements Filterable {

        private LayoutInflater inflater;

        public ItemsAdapter() {
            super(ctxt, R.layout.list_items, R.id.title);
            inflater = LayoutInflater.from(ctxt);
        }

        // @Override
        // public Filter getFilter() {
        // Log.v(TAG, "getFilter()");
        // if (mFilter == null) {
        // mFilter = new ItemFilter();
        // }
        // return mFilter;
        // }

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
                // vh.action = (ImageButton)
                // convertView.findViewById(R.id.btn_actions);
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
            // vh.action.setOnClickListener(item.action);
            // Set endless loader
            int size = items.size();
            if (position == size - ENDLESS_OFFSET) {
                new EndlessTask().execute(size);
            }
            return convertView;
        }
    }

    // private class ItemFilter extends Filter {
    // @Override
    // protected FilterResults performFiltering(CharSequence prefix) {
    // FilterResults results = new FilterResults();
    // String prefixString = prefix.toString().toLowerCase();
    // final int count = items.size();
    // if (prefix == null || prefix.length() == 0) {
    // ArrayList<Item> list = new ArrayList<Item>(items);
    // results.values = list;
    // results.count = list.size();
    // } else {
    // final List<Item> values = new ArrayList<Item>(items);
    // final List<Item> newValues = new ArrayList<Item>(count);
    // for (int i = 0; i < count; i++) {
    // final Item item = values.get(i);
    // String value = item.title.toLowerCase();
    // // First match against the whole, non-splitted value
    // if (value.startsWith(prefixString)) {
    // newValues.add(item);
    // } else {
    // final String[] words = value.split(" ");
    // final int wordCount = words.length;
    // for (int k = 0; k < wordCount; k++) {
    // if (words[k].startsWith(prefixString)) {
    // newValues.add(item);
    // break;
    // }
    // }
    // }
    // }
    // results.values = newValues;
    // results.count = newValues.size();
    // }
    // return results;
    // }
    //
    // @SuppressWarnings("unchecked")
    // @Override
    // protected void publishResults(CharSequence constraint, FilterResults
    // results) {
    // items = (ArrayList<Item>) results.values;
    // adapter.clear();
    // updateList();
    // /*
    // * if (results.count > 0) { adapter.notifyDataSetChanged(); } else {
    // * adapter.notifyDataSetInvalidated(); }
    // */
    // }
    // }

    class EndlessTask extends AsyncTask<Integer, Void, Void> {

        @Override
        protected void onPreExecute() {
            findViewById(R.id.loading).setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Integer... params) {
            addToList(params[0], ITEMS_NB);
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            updateList();
            findViewById(R.id.loading).setVisibility(View.INVISIBLE);
        }

    }
}
