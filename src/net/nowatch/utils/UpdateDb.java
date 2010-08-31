package net.nowatch.utils;

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

import net.nowatch.Main;
import net.nowatch.network.GetFile;
import net.nowatch.network.RSSReader;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.Log;

public class UpdateDb {

    private final static String TAG = Main.TAG + "UpdateDb";
    private final static String PUBDATE = "Wed, 31 Mar 1999 00:00:00 +0200";
    private static SQLiteDatabase db;
    private static String feed_id;
    private static String etag = null;

    public static void update(final Context ctxt, String fid, int feed_xml) throws IOException {
        feed_id = fid;
        Cursor c = null;
        try {
            // Get lastpub and etag
            db = (new DB(ctxt)).getWritableDatabase();

            c = db.rawQuery("select etag,pubDate from feeds where _id=? limit 1",
                    new String[] { fid });
            c.moveToFirst();

            // etag
            if (c.getString(0) != null) {
                etag = c.getString(0);
            }

            // pubDate
            String pubDate = PUBDATE;
            if (c.getString(1) != null) {
                pubDate = c.getString(1);
            }

            // Try to download feed
            new GetFeed(ctxt, pubDate).getChannel(ctxt.getString(feed_xml), etag);

        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
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

        private String pubDate;
        private Context ctxt;

        public GetFeed(final Context ctxt, String pubDate) {
            super(ctxt);
            this.ctxt = ctxt;
            this.pubDate = pubDate;
        }

        public void getChannel(String src, String etag) throws IOException {
            getChannel(src, null, etag, true, false);
        }

        @Override
        protected void finish(boolean delete, String file) {
            try {
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
                        RSS handler = new RSS(ctxt, pubDate);
                        xr.setContentHandler(handler);
                        xr.setErrorHandler(handler);
                        xr.parse(new InputSource(new FileReader(file)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (SAXException e) {
                        Log.e(TAG, e.getMessage());
                    } catch (ParserConfigurationException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                if (db != null) {
                    db.close();
                }
            }
            super.finish(delete, file);
        }
    }

    private static class RSS extends RSSReader {

        private SimpleDateFormat formatter;
        private Date item_date;
        private Calendar cal = Calendar.getInstance();
        private Context ctxt;

        public RSS(final Context ctxt, String pubDate) {
            this.ctxt = ctxt;
            formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZ");
            formatter.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        }

        @Override
        public void endElement(String uri, String name, String qName) throws SAXException {
            super.endElement(uri, name, qName);
            if (name == "channel") {
                // Update channel info, always
                if (db != null) {
                    db.update("feeds", feedMap, "_id=?", new String[] { feed_id });
                }
            } else if (!in_items && name == "image" && uri != RSSReader.ITUNES_DTD) {
                // Get feed image
                try {
                    feedMap.put("image", new GetImage(ctxt)
                            .getChannel(feedMap.getAsString("image")));
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if (name == "item") {
                if (db != null) {
                    try {
                        item_date = formatter.parse(itemMap.getAsString("pubDate"));
                    } catch (ParseException e) {
                        Log.e(TAG, e.getMessage());
                        try {
                            item_date = formatter.parse(PUBDATE);
                        } catch (ParseException e1) {
                        }
                    } finally {
                        // Update some values
                        cal.setTime(item_date);
                        itemMap.put("pubDate", cal.getTimeInMillis());
                        itemMap.put("feed_id", feed_id);
                        // Get item image
                        String image = itemMap.getAsString("image");
                        if (!new String("").equals(image)) {
                            try {
                                itemMap.put("image", new GetImage(ctxt).getChannel(image));
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }
                        // Insert in DB
                        db.insert("items", null, itemMap);
                    }
                }
            }
        }
    }

    private static class GetImage extends GetFile {

        private byte[] image;
        Context ctxt;

        public GetImage(Context ctxt) {
            super(ctxt);
            this.ctxt = ctxt;
        }

        public byte[] getChannel(String src) throws IOException {
            getChannel(src, null, null, true, false);
            return image;
        }

        @Override
        protected void finish(boolean delete, String file) {
            Bitmap file_bitmap = null;
            try {
                if (file_remote_size > 0 && file_remote_size > 150000L) {
                    // Get density from prefs
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
                    int density = prefs.getInt(Prefs.KEY_DENSITY, Prefs.DEFAULT_DENSITY);
                    // Image options
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 3;
                    // FIXME: Not compatible with 1.5
                    options.inPurgeable = true;
                    options.inInputShareable = true;
                    options.inDensity = density;
                    options.inTargetDensity = density;
                    // End
                    file_bitmap = BitmapFactory.decodeFile(file, options);
                } else {
                    file_bitmap = BitmapFactory.decodeFile(file);
                }
                if (file_bitmap != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    file_bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    image = out.toByteArray();
                    // Save memory from Bitmap allocation
                    file_bitmap.recycle();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, e.getMessage());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.getMessage());
            }
            super.finish(delete, file);
        }
    }
}
