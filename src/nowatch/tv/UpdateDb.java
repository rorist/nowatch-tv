package nowatch.tv;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.Context;
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
            e.printStackTrace();
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

        SQLiteDatabase db;

        @Override
        public void startDocument() {
            super.startDocument();
            try {
                db = (new DB(ctxt)).getWritableDatabase();
            } catch (SQLiteException e) {
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
        public void endElement(String uri, String name, String qName) {
            super.endElement(uri, name, qName);
            if (name == "channel") {
                try {
                    // Get image bits
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
            } else if (name == "item") {
                if (db != null) {
                    itemMap.put("feed_id", feed_id);
                    db.insert("items", null, itemMap);
                }
            }
        }
    }
}
