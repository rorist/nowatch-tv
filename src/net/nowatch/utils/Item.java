package net.nowatch.utils;

import android.graphics.Bitmap;
import android.view.View.OnClickListener;

public class Item {

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_ITEM_POSITION = "extra_item_position";
    public static final String EXTRA_ITEM_TYPE = "extra_item_type";
    public static final int STATUS_NEW = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_UNREAD = 3;
    public static final int STATUS_READ = 4;
    public static final int STATUS_DL_READ = 5;
    public static final int STATUS_DL_UNREAD = 6;
    public static final int STATUS_UNCOMPLETE = 7;

    public int id;
    public Bitmap image;
    public String title;
    public String status;
    public String date;
    public Bitmap logo;
    public OnClickListener action;

    public Item() {
    }

    public String toString() {
        return title;
    }

}
