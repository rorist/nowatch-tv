package net.nowatch.ui;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.nowatch.Main;
import net.nowatch.R;
import net.nowatch.service.UpdateTask;
import net.nowatch.utils.DB;
import net.nowatch.utils.Item;
import net.nowatch.utils.Prefs;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class ListItems extends AbstractListItems {

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
    private String current_request;
    private String current_request_status;
    private String current_filter = "items.feed_id=2"; // SCUDS ftw
    private UpdateTaskBtn updateTask = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                DialogfilterByPodcast();
            }
        });
        findViewById(R.id.btn_manage).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                DialogManage();
            }
        });

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
        }
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

    private void DialogManage() {
        final Resources res = getResources();
        final QuickAction qa = new QuickAction(findViewById(R.id.btn_manage));
        qa.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);

        final ActionItem first = new ActionItem();
        first.setTitle("Téléchargements");
        first.setIcon(res.getDrawable(R.drawable.action_download));
        first.setOnClickListener(new View.OnClickListener() {
            // @Override
            public void onClick(View v) {
                qa.dismiss();
                startActivity(new Intent(ListItems.this, Manage.class));
            }
        });

        final ActionItem second = new ActionItem();
        second.setTitle("Favoris");
        second.setIcon(res.getDrawable(R.drawable.action_bookmark));
        second.setOnClickListener(new View.OnClickListener() {
            // @Override
            public void onClick(View v) {
                startActivity(new Intent(ListItems.this, BookmarkItems.class));
                qa.dismiss();
            }
        });

        qa.addActionItem(first);
        qa.addActionItem(second);
        qa.show();
    }

    private void DialogfilterByPodcast() {
        // TODO: Filter without requerying ?
        // adapter.getFilter().filter("SCUDS");
        final Resources res = getResources();
        final QuickAction qa = new QuickAction(findViewById(R.id.btn_filter_podcast));
        qa.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);
        // Show all
        final ActionItem first = new ActionItem();
        first.setTitle("Tous");
        first.setIcon(res.getDrawable(R.drawable.action_icon));
        first.setOnClickListener(new View.OnClickListener() {
            // @Override
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
                // @Override
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

    protected int addToList(int offset, int limit) {
        return addToList(offset, limit, false);
    }

    protected int addToList(int offset, int limit, boolean update) {
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

    @Override
    public void resetList() {
        if (updateTask != null) {
            updateTask.cancel(true);
        }
        super.resetList();
    }

    public void refreshListVisible() {
        addToList(list.getFirstVisiblePosition(), list.getLastVisiblePosition()
                - list.getFirstVisiblePosition() + 1, true);
        adapter.notifyDataSetChanged();
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
                    btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh_a, 0, 0,
                            0);
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
                    btn_ref
                            .setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0,
                                    0);
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
                    btn_refresh.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0,
                            0, 0);
                    btn_refresh.setEnabled(true);
                    btn_refresh.setClickable(true);
                }
            }
        }

    }
}
