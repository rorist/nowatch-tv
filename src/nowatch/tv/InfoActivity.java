package nowatch.tv;

import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.TextView;

public class InfoActivity extends Activity {

    // private final String TAG = "InfoActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_activity);
        
        Bundle extra = getIntent().getExtras();
        SQLiteDatabase db = (new DB(getApplicationContext())).getWritableDatabase();
        Cursor c  = db.rawQuery("select _id, title, description, link from items where _id=" + extra.getInt("item_id"), null);
        c.moveToFirst();
        ((TextView) findViewById(R.id.title)).setText(c.getString(1));
        ((TextView) findViewById(R.id.desc)).setText(c.getString(2));
        ((TextView) findViewById(R.id.link)).setText(c.getString(3));
        c.close();
        db.close();
    }
}
