package nowatch.tv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class UpdateRunnable implements Runnable {

    private final String TAG = "UpdateRunnable";
    private final String USERAGENT = "Android/Nowatch.TV/0.1";
    private Context ctxt;
    private int feed_xml;
    private String feed_id;

    public UpdateRunnable(Context ctxt, int feed_xml, String feed_id) {
        this.feed_xml = feed_xml;
        this.feed_id = feed_id;
        this.ctxt = ctxt;
    }

    public void run() {
        XMLReader xr;
        try {
            xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            RSS handler = new RSS();
            xr.setContentHandler(handler);
            xr.setErrorHandler(handler);
            URLConnection c = (new URL(ctxt.getString(feed_xml))).openConnection();
            c.setRequestProperty("User-Agent", USERAGENT);
            xr.parse(new InputSource(c.getInputStream()));
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

    private class RSS extends RSSReader {

        @Override
        public void endElement(String uri, String name, String qName) {
            super.endElement(uri, name, qName);
            SQLiteDatabase db = null;
            try {
                db = (new DB(ctxt)).getWritableDatabase();
                if (name == "channel") {
                    // Update channel info, always
                    db.update("feeds", channelMap, "_id=?", new String[] { feed_id });
                    Log.v(TAG, "CHANNEL");
                    Log.v(TAG, "title=" + channelMap.get("title"));
                    Log.v(TAG, "link=" + channelMap.get("link"));
                    Log.v(TAG, "pubDate=" + channelMap.get("pubDate"));
                    Log.v(TAG, "feed_id=" + feed_id);
                } else if (name == "item") {
                    // Log.v(TAG, "ITEM");
                    // Log.v(TAG, "title=" + itemMap.get("title"));
                    // Log.v(TAG, "file_uri=" + itemMap.get("file_uri"));
                }
            } catch (java.lang.IllegalStateException e) {
                Log.e(TAG, e.getMessage());
            } catch (android.database.sqlite.SQLiteException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            } finally {
                if (db != null) {
                    db.close();
                }
            }
        }

    }
}
