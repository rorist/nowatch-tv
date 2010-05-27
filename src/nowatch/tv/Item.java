package nowatch.tv;

import android.graphics.Bitmap;
import android.view.View.OnClickListener;

public class Item {

    public static final int STATUS_NEW = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_UNREAD = 3;
    public static final int STATUS_READ = 4;
    public static final int STATUS_DL_READ = 5;
    public static final int STATUS_DL_UNREAD = 6;

    public int id;
    public Bitmap image;
    public String title;
    public String status;
    public String date;
    public Bitmap logo;
    public OnClickListener action;

    public Item() {
    }

    public Item(int _id) {
        id = _id;
    }

    public String toString() {
        return title;
    }

}
