package net.nowatch.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.nowatch.Main;
import net.nowatch.R;
import net.nowatch.service.UpdateTask;
import net.nowatch.utils.DB;
import net.nowatch.utils.Item;
import net.nowatch.utils.Prefs;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
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
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class ListItems extends Activity implements OnItemClickListener {

    private static final String TAG = Main.TAG + "ItemsActivity";
    private final String REQ_ITEMS_STATUS = "SELECT items._id, '', items.status FROM items";
    private final String REQ_ITEMS_SELECT = "SELECT items._id, items.title, items.status, feeds.image, items.pubDate, items.image "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id";
    private final String REQ_ITEMS_END = " ORDER BY items.pubDate DESC LIMIT ";
    private final String REQ_ITEMS = REQ_ITEMS_SELECT + REQ_ITEMS_END;
    private final String REQ_MARK_ALL = "update items set status=" + Item.STATUS_UNREAD
            + " where status=" + Item.STATUS_NEW;
    private static final int MENU_MARK_ALL = 1;
    private static final int MENU_OPTIONS = 2;
    private static final int ITEMS_NB = 16;
    private static final int ENDLESS_OFFSET = 3;
    private int image_size;
    private String current_request;
    private String current_request_status;
    private String current_filter = "items.feed_id=2"; // SCUDS ftw
    private ItemsAdapter adapter;
    private UpdateTaskBtn updateTask = null;
    private Context ctxt;
    private List<Item> items = null;
    private ListView list;
    private ItemFilter mFilter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        setContentView(R.layout.items_activity);

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        image_size = (int) (48 * dm.density + 0.5f);

        // Title button
        findViewById(R.id.btn_logo).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                resetList();
            }
        });
        // Menu buttons
        findViewById(R.id.btn_manage).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_filter_podcast).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_refresh).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateTask = new UpdateTaskBtn(ListItems.this);
                updateTask.execute();
            }
        });
        findViewById(R.id.btn_filter_podcast).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterByPodcast();
            }
        });
        findViewById(R.id.btn_manage).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ListItems.this, Manage.class));
            }
        });

        // Set list adapter
        items = new ArrayList<Item>();
        adapter = new ItemsAdapter();
        list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(false);
        list.setOnItemClickListener(this);
        list.setEmptyView(findViewById(R.id.list_empty));
        findViewById(R.id.loading).setVisibility(View.INVISIBLE);

        // Current request
        // TODO: From Prefs
        current_request = REQ_ITEMS;
        current_request_status = REQ_ITEMS_STATUS + REQ_ITEMS_END;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (items.size() > 0) {
            refreshListVisible();
        } else {
            resetList();
            // adapter.getFilter().filter("SCUDS");
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // TODO: Handle configuration state save
        return super.onRetainNonConfigurationInstance();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_MARK_ALL, 0, R.string.menu_mark_all).setIcon(
                android.R.drawable.ic_menu_agenda);
        menu.add(0, MENU_OPTIONS, 0, R.string.menu_options).setIcon(
                android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_MARK_ALL:
                SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
                db.execSQL(REQ_MARK_ALL);
                db.close();
                refreshListVisible();
                return true;
            case MENU_OPTIONS:
                startActivity(new Intent(ListItems.this, Prefs.class));
                return true;
        }
        return false;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i = new Intent(ctxt, ItemInfo.class);
        i.putExtra(Item.EXTRA_ITEM_ID, items.get(position).id);
        startActivity(i);
    }

    private void filterByPodcast() {
        final Resources res = getResources();
        final QuickAction qa = new QuickAction(findViewById(R.id.btn_filter_podcast));
        qa.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);
        // Show all
        final ActionItem first = new ActionItem();
        first.setTitle("Tous");
        first.setIcon(res.getDrawable(R.drawable.action_icon));
        first.setOnClickListener(new View.OnClickListener() {
            //@Override
            public void onClick(View v) {
                current_request = REQ_ITEMS;
                current_request_status = REQ_ITEMS_STATUS + REQ_ITEMS_END;
                qa.dismiss();
                resetList();
            }
        });
        qa.addActionItem(first);

        // Set podcasts entries
        for (int i = 0; i < DB.podcasts_len; i++) {
            final ActionItem p = new ActionItem();
            p.setTitle(DB.podcasts[i]);
            p.setIcon(res.getDrawable(R.drawable.action_icon));
            final int item_id = i + 1;
            p.setOnClickListener(new View.OnClickListener() {
                //@Override
                public void onClick(View v) {
                    current_filter = "items.feed_id=" + item_id;
                    current_request = REQ_ITEMS_SELECT + " WHERE " + current_filter + REQ_ITEMS_END;
                    current_request_status = REQ_ITEMS_STATUS + " WHERE " + current_filter
                            + REQ_ITEMS_END;
                    qa.dismiss();
                    resetList();
                }
            });
            qa.addActionItem(p);
        }

        qa.show();
    }

    private Item updateItemStatus(Item item, Cursor c) {
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
            case Item.STATUS_UNCOMPLETE:
                item.status = getString(R.string.status_uncomplete);
                break;
            default:
                item.status = getString(R.string.status_new);
        }
        return item;
    }

    private Bitmap createImage(byte[] logo_podcast_byte, byte[] logo_item_byte, int width, int height) {
        final int min_size = 200;
        if (logo_item_byte != null && logo_item_byte.length > min_size) {
            return Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_item_byte, 0, logo_item_byte.length), image_size, image_size, true);
        } else {
            if (logo_podcast_byte != null && logo_podcast_byte.length > min_size) {
                return Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_podcast_byte, 0, logo_podcast_byte.length), image_size, image_size, true);
            } else {
                return BitmapFactory.decodeResource(getResources(), R.drawable.icon);
            }
        }
    }

    private Item createItem(Cursor c) {
        final Item item = new Item();
        item.id = c.getInt(0);
        item.title = c.getString(1);
        // Status
        item.status = updateItemStatus(item, c).status;
        // Icon
        item.logo = createImage(c.getBlob(3), c.getBlob(5), image_size, image_size);
        // Date
        long date = c.getLong(4);
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
            formatter.setTimeZone(TimeZone.getDefault());
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

    private int addToList(int offset, int limit) {
        return addToList(offset, limit, false);
    }

    private int addToList(int offset, int limit, boolean update) {
        SQLiteDatabase db = null;
        Cursor c = null;
        int cnt = 0;
        try {
            db = (new DB(ctxt)).getWritableDatabase();
            if (update) {
                c = db.rawQuery(current_request_status + offset + "," + limit, null);
            } else {
                c = db.rawQuery(current_request + offset + "," + limit, null);
            }
            if (c.moveToFirst()) {
                cnt = c.getCount();
                do {
                    if (update) {
                        int pos = offset + c.getPosition();
                        items.set(pos, updateItemStatus(items.get(pos), c));
                    } else {
                        items.add(createItem(c));
                    }
                } while (c.moveToNext());
            }
        } catch (SQLiteDiskIOException e) {
            // sqlite_stmt_journals partition is too small (4MB)
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return cnt;
    }

    public void resetList() {
        if (updateTask != null) {
            updateTask.cancel(true);
        }
        items.clear();
        adapter.clear();
        addToList(0, ITEMS_NB);
        updateList();
        list.setSelection(0);
    }

    public void refreshListVisible() {
        addToList(list.getFirstVisiblePosition(), list.getLastVisiblePosition()
                - list.getFirstVisiblePosition() + 1, true);
        adapter.notifyDataSetChanged();
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

    private class ItemsAdapter extends ArrayAdapter<Item> implements Filterable {

        private LayoutInflater inflater;

        public ItemsAdapter() {
            super(ctxt, R.layout.list_items, R.id.title);
            inflater = LayoutInflater.from(ctxt);
        }

        @Override
        public Filter getFilter() {
            Log.v(TAG, "getFilter()");
            if (mFilter == null) {
                mFilter = new ItemFilter();
            }
            return mFilter;
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

    private class ItemFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            String prefixString = prefix.toString().toLowerCase();
            final int count = items.size();
            if (prefix == null || prefix.length() == 0) {
                ArrayList<Item> list = new ArrayList<Item>(items);
                results.values = list;
                results.count = list.size();
            } else {
                final List<Item> values = new ArrayList<Item>(items);
                final List<Item> newValues = new ArrayList<Item>(count);
                for (int i = 0; i < count; i++) {
                    final Item item = values.get(i);
                    String value = item.title.toLowerCase();
                    // First match against the whole, non-splitted value
                    if (value.startsWith(prefixString)) {
                        newValues.add(item);
                    } else {
                        final String[] words = value.split(" ");
                        final int wordCount = words.length;
                        for (int k = 0; k < wordCount; k++) {
                            if (words[k].startsWith(prefixString)) {
                                newValues.add(item);
                                break;
                            }
                        }
                    }
                }
                results.values = newValues;
                results.count = newValues.size();
            }
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            items = (ArrayList<Item>) results.values;
            adapter.clear();
            updateList();
            /*
             * if (results.count > 0) { adapter.notifyDataSetChanged(); } else {
             * adapter.notifyDataSetInvalidated(); }
             */
        }
    }

    private static class UpdateTaskBtn extends UpdateTask {

        public UpdateTaskBtn(ListItems a) {
            super(a);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mActivity != null) {
                ListItems a = getActivity();
                if (a != null) {
                    Button btn_ref = (Button) a.findViewById(R.id.btn_refresh);
                    btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh_a, 0, 0, 0);
                    ((AnimationDrawable) btn_ref.getCompoundDrawables()[0]).start();
                    btn_ref.setEnabled(false);
                    btn_ref.setClickable(false);
                }
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            if (mActivity != null) {
                ListItems a = getActivity();
                if (a != null) {
                    Button btn_ref = (Button) a.findViewById(R.id.btn_refresh);
                    btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0, 0);
                    btn_ref.setEnabled(true);
                    btn_ref.setClickable(true);
                    a.findViewById(R.id.loading).setVisibility(View.INVISIBLE);
                    a.resetList();
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mActivity != null) {
                ListItems a = getActivity();
                if (a != null) {
                    Button btn_refresh = (Button) a.findViewById(R.id.btn_refresh);
                    btn_refresh.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0, 0);
                    btn_refresh.setEnabled(true);
                    btn_refresh.setClickable(true);
                }
            }
        }

    }

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
