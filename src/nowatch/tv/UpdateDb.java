package nowatch.tv;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

// TODO: DB is not closed when parsing error (tag not closed), and I cannot find where to catch this

public class UpdateDb {

    private static final String TAG = "UpdateDb";
    private static Context ctxt;
    private static String feed_id;

    public static void update(Context ct, String fid, int feed_xml) {
        ctxt = ct;
        feed_id = fid;
        String file = null;
        try {
            XMLReader xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            RSS handler = new RSS();
            file = handler.getFile(ctxt.getString(feed_xml));
            xr.setContentHandler(handler);
            xr.setErrorHandler(handler);
            xr.parse(new InputSource(new FileReader(file)));
        } catch (SAXException e) {
            Log.e(TAG, e.getMessage());
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            Log.v(TAG, "SD Card not accessible ?");
            e.printStackTrace();
        } finally {
            if (file != null) {
                new File(file).delete();
            }
        }
    }

    private static class RSS extends RSSReader {

        private SQLiteDatabase db;
        private SimpleDateFormat formatter;
        private SimpleDateFormat formatter_item;
        private Date lastPub;

        @Override
        public void startDocument() {
            super.startDocument();
            // Wed, 14 Apr 2010 18:18:07 +0200
            formatter_item = new SimpleDateFormat("yyyy-MM-dd");
            // FIXME Probleme parsing date !!!!
            formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZ");
            // FIXME: Get device timezone
            formatter.setTimeZone(TimeZone.getDefault());
            Cursor c = null;
            try {
                db = (new DB(ctxt)).getWritableDatabase();
                c = db.rawQuery("select pubDate from feeds where _id=? limit 1",
                        new String[] { feed_id });
                c.moveToFirst();
                if (c.getString(0) != null) {
                    lastPub = formatter.parse(c.getString(0));
                } else {
                    lastPub = formatter.parse("Wed, 31 Mar 1999 00:00:00 +0200");
                }
            } catch (SQLiteException e) {
                Log.e(TAG, e.getMessage());
            } catch (ParseException e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public void endDocument() {
            super.endDocument();
            if (db != null) {
                db.close();
            }
        }

        @Override
        public void endElement(String uri, String name, String qName) throws SAXException {
            super.endElement(uri, name, qName);
            if (name == "channel") {
                // Update channel info, always
                if (db != null) {
                    db.update("feeds", channelMap, "_id=?", new String[] { feed_id });
                }
            } else if (!in_items && name == "image"
                    && uri != "http://www.itunes.com/dtds/podcast-1.0.dtd") {
                // Get image bits
                try {
                    String file = getFile(channelMap.getAsString("image"));
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap file_bitmap = BitmapFactory.decodeFile(file, options);
                    if (file_bitmap != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        file_bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        channelMap.put("image", out.toByteArray());
                        // Save memory from Bitmap allocation
                        file_bitmap.recycle();
                        new File(file).delete();
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, e.getMessage());
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, e.getMessage());
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                }
            } else if (!in_items && name == "pubDate") {
                try {
                    // Check publication date of channel
                    if (formatter.parse(channelMap.getAsString("pubDate")).before(lastPub)) {
                        // Stop the parser
                        db.close();
                        throw new SAXException("Nothing to update for feed_id=" + feed_id);
                    }
                } catch (ParseException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if (name == "item") {
                if (db != null) {
                    try {
                        Date item_date = formatter.parse(itemMap.getAsString("pubDate"));
                        if (item_date.after(lastPub)) {
                            // Simplier date format for later SQL queries
                            itemMap.put("pubDate", formatter_item.format(item_date));
                            itemMap.put("feed_id", feed_id);
                            db.insert("items", null, itemMap);
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }
    }
}
