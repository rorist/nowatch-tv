package net.nowatch.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import net.nowatch.R;
import net.nowatch.network.GetFile;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

public class Db {

    private final String TAG = "Db";
    public static final String DB_PATH = "/data/data/net.nowatch/files/";
    public static final String DB_NAME = "nowatch.db";
    private Context ctxt = null;

    public Db(Context ctxt) {
        this.ctxt = ctxt;
    }

    public SQLiteDatabase openDb() {
        try {
            return SQLiteDatabase.openDatabase(DB_PATH + DB_NAME, null,
                    SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    public void copyDbToDevice() {
        Log.v(TAG, "copyDbToDevice()");
        try {
            InputStream in = ctxt.getResources().openRawResource(R.raw.nowatch);
            OutputStream out = ctxt.openFileOutput(DB_NAME, Context.MODE_PRIVATE);
            final ReadableByteChannel ic = Channels.newChannel(in);
            final WritableByteChannel oc = Channels.newChannel(out);
            GetFile.fastChannelCopy(ic, oc);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
