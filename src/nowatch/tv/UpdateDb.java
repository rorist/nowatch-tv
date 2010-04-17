package nowatch.tv;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
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

public class UpdateDb {

    private static final String TAG = "UpdateDb";
    private static Context ctxt;
    private static String feed_id;

    public static void update(Context ct, String fid, int feed_xml) {
        ctxt = ct;
        feed_id = fid;
        XMLReader xr;
        try {
            xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            RSS handler = new RSS();
            xr.setContentHandler(handler);
            xr.setErrorHandler(handler);
            xr.parse(new InputSource(handler.getFile(ctxt.getString(feed_xml))));
        } catch (SAXException e) {
            Log.e(TAG, e.getMessage());
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
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
            formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZ");
            formatter.setTimeZone(TimeZone.getDefault()); // FIXME: Get device
            // timezone
            try {
                db = (new DB(ctxt)).getWritableDatabase();
                Cursor c = db.rawQuery("select pubDate from feeds where _id=? limit 1",
                        new String[] { feed_id });
                c.moveToFirst();
                if (c.getString(0) != null) {
                    lastPub = formatter.parse(c.getString(0));
                } else {
                    lastPub = formatter.parse("Wed, 31 Mar 1999 00:00:00 +0200");
                }
                c.close();
            } catch (SQLiteException e) {
                Log.e(TAG, e.getMessage());
            } catch (ParseException e) {
                Log.e(TAG, e.getMessage());
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
                // Get image bits
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap file = BitmapFactory.decodeStream(getFile(channelMap
                            .getAsString("image")), null, options);
                    if (file != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        file.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        channelMap.put("image", out.toByteArray());
                        file.recycle(); // Save memory from Bitmap allocation
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, e.getMessage());
                } finally {
                    // Update channel info, always
                    if (db != null) {
                        db.update("feeds", channelMap, "_id=?", new String[] { feed_id });
                    }
                }
            } else if (!in_items && name == "pubDate") {
                // Check publication date of channel
                try {
                    if (!formatter.parse(channelMap.getAsString("pubDate")).after(lastPub)) {
                        // Stop the parser
                        db.close();
                        throw new SAXException("\nNothing to update for feed_id=" + feed_id);
                    }
                } catch (ParseException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if (name == "item") {
                if (db != null) {
                    itemMap.put("feed_id", feed_id);
                    // Format date simplier for later SQL queries
                    try {
                        itemMap.put("pubDate", formatter_item.format(formatter.parse(itemMap
                                .getAsString("pubDate"))));
                    } catch (ParseException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    db.insert("items", null, itemMap);
                }
            }
        }
    }
}
