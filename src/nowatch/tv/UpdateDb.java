package nowatch.tv;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class UpdateDb {

    private static final String TAG = "UpdateDb";
    private static SQLiteDatabase db;
    private static Context ctxt;
    private static String feed_id;
    private static String etag = null;
    private static Date lastPub;
    private static SimpleDateFormat formatter;

    public static void update(Context ct, String fid, int feed_xml) throws IOException {
        ctxt = ct;
        feed_id = fid;
        Cursor c = null;
        try {
            // Get lastpub and etag
            db = (new DB(ctxt)).getWritableDatabase();

            // FIXME Probleme parsing date !!!! and get local timezone ?
            // Wed, 14 Apr 2010 18:18:07 +0200
            formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZ");
            formatter.setTimeZone(TimeZone.getDefault());

            c = db.rawQuery("select etag,pubDate from feeds where _id=? limit 1",
                    new String[] { fid });
            c.moveToFirst();

            // etag
            if (c.getString(0) != null) {
                etag = c.getString(0);
            }

            // pubDate
            if (c.getString(1) != null) {
                lastPub = formatter.parse(c.getString(1));
            } else {
                lastPub = formatter.parse("Wed, 31 Mar 1999 00:00:00 +0200");
            }

            // Try to download feed
            new GetFeed().getChannel(ctxt.getString(feed_xml), etag);

        } catch (ParseException e) {
            Log.e(TAG, e.getMessage());
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                c.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }

    private static class GetFeed extends GetFile {

        public void getChannel(String src, String etag) throws IOException {
            getChannel(src, null, etag, true);
        }

        @Override
        protected void finish(String file) {
            if (file != null) {
                // Save etag
                if (etag != null) {
                    ContentValues etag_value = new ContentValues();
                    etag_value.put("etag", etag);
                    db.update("feeds", etag_value, "_id=?", new String[] { feed_id });
                }

                // Start the parser
                try {
                    XMLReader xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
                    RSS handler = new RSS();
                    xr.setContentHandler(handler);
                    xr.setErrorHandler(handler);
                    xr.parse(new InputSource(new FileReader(file)));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SAXException e) {
                    Log.e(TAG, e.getMessage());
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                } finally {
                    if (db != null) {
                        db.close();
                    }
                }
            }
            if (db != null) {
                db.close();
            }
            super.finish(file);
        }
    }

    private static class RSS extends RSSReader {

        private Date item_date;
        private Calendar cal = Calendar.getInstance();

        @Override
        public void startDocument() {
            super.startDocument();
        }

        @Override
        public void endDocument() {
            super.endDocument();
        }

        @Override
        public void endElement(String uri, String name, String qName) throws SAXException {
            super.endElement(uri, name, qName);
            if (name == "channel") {
                // Update channel info, always
                if (db != null) {
                    db.update("feeds", feedMap, "_id=?", new String[] { feed_id });
                }
            } else if (!in_items && name == "image"
                    && uri != "http://www.itunes.com/dtds/podcast-1.0.dtd") {
                // Get image bits
                try {
                    new GetImage().getChannel(feedMap.getAsString("image"));
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if (!in_items && name == "pubDate") {
                try {
                    // Check publication date of channel
                    if (!formatter.parse(feedMap.getAsString("pubDate")).after(lastPub)) {
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
                        item_date = formatter.parse(itemMap.getAsString("pubDate"));
                        if (item_date.after(lastPub)) {
                            cal.setTime(item_date);
                            itemMap.put("pubDate", cal.getTimeInMillis());
                            itemMap.put("feed_id", feed_id);
                            db.insert("items", null, itemMap);
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }

        // TODO: Being static
        class GetImage extends GetFile {

            public void getChannel(String src) throws IOException {
                getChannel(src, null, null, true);
            }

            @Override
            protected void finish(String file) {
                Bitmap file_bitmap = null;
                try {
                    if (file_size != null && Integer.parseInt(file_size) > 150000) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 3;
                        options.inPurgeable = true;
                        options.inInputShareable = true;
                        options.inDensity = 160;
                        options.inTargetDensity = 160;
                        file_bitmap = BitmapFactory.decodeFile(file, options);
                    } else {
                        file_bitmap = BitmapFactory.decodeFile(file);
                    }
                    if (file_bitmap != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        file_bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        feedMap.put("image", out.toByteArray());
                        // Save memory from Bitmap allocation
                        file_bitmap.recycle();
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, e.getMessage());
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, e.getMessage());
                }
                super.finish(file);
            }
        }
    }
}
