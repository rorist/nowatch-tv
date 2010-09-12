package net.nowatch.ui;

import net.nowatch.Main;
import net.nowatch.R;
import net.nowatch.utils.Db;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class BookmarkItems extends AbstractListItems {

    private static final String TAG = Main.TAG + "BookmarkItems";
    private final String REQ_START = "SELECT _id, title, status, pubDate, feed_id, image FROM items WHERE bookmark=1 AND type=";
    private final String REQ_END = " ORDER BY pubDate DESC LIMIT ";
    private String req;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        req = REQ_START + podcast_type + REQ_END;

        // Buttons
        findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        ((TextView) findViewById(R.id.list_empty)).setText(R.string.list_empty);
    }

    @Override
    public void onStart() {
        super.onStart();
        resetList();
    }

    protected int addToList(int offset, int limit) {
        return addToList(offset, limit, false);
    }

    protected int addToList(int offset, int limit, boolean update) {
        SQLiteDatabase db = null;
        Cursor c = null;
        int cnt = 0;
        try {
            db = (new Db(ctxt)).openDb();
            c = db.rawQuery(req + offset + "," + limit, null);
            if (c.moveToFirst()) {
                cnt = c.getCount();
                do {
                    items.add(createItem(c));
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
}
