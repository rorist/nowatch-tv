package net.nowatch.utils;

import net.nowatch.Main;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DB extends SQLiteOpenHelper {
    private final static String TAG = Main.TAG + "Db";
    private final static String DB_NAME = "nowatch.db";
    private final static int DB_VERSION = 2;
    private final String CREATE_FEEDS = "create table feeds (" + "_id INTEGER PRIMARY KEY,"
            + "title TEXT," + "description TEXT," + "link TEXT," + "pubDate NUMERIC,"
            + "etag TEXT," + "image BLOB);";
    private final String CREATE_ITEMS = "create table items (" + "_id INTEGER PRIMARY KEY,"
            + "feed_id INTEGER," + "status INTEGER,"
            + "title TEXT UNIQUE ON CONFLICT IGNORE,"
            // TODO: Update existing items with new values instead of ignoring
            // Needs PRAGMA recursive_triggers=true;
            + "description TEXT," + "link TEXT," + "pubDate NUMERIC," + "file_uri TEXT,"
            + "file_size INTEGER," + "file_type TEXT," + "bookmark INTEGER," + "image BLOB);";
    public static final String[] podcasts = new String[] { "Cine Fuzz", "Geek Inc.", "SCUDS.TV",
            "ZapCast.tv", "Tonight On Mars", "La Revue Tech" };
    public static final int podcasts_len = podcasts.length;

    public DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase Db) {
        createTable(Db, "feeds", CREATE_FEEDS);
        createTable(Db, "items", CREATE_ITEMS);
        for (String podcast : podcasts) {
            Db.execSQL("insert into feeds (\"title\") values (\"" + podcast + "\");");
        }
        Db.execSQL("CREATE INDEX pubDateIndex on items (pubDate);");
        Db.execSQL("CREATE INDEX etagIndex on feeds(etag);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase Db, int Old, int New) {
        for (int id = 1; id <= podcasts_len; id++) {
            Db.execSQL("insert or ignore into feeds (\"_id\", \"title\") values (\"" + id + "\",\""
                    + podcasts[id - 1] + "\");");
        }
    }

    private void createTable(SQLiteDatabase Db, String table_name, String create) {
        Log.v(TAG, "createTable " + table_name);
        Cursor c = Db.rawQuery("select name from sqlite_master where type='table' and name='"
                + table_name + "'", null);
        try {
            if (c.getCount() == 0) {
                Db.execSQL(create);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
    }
}
