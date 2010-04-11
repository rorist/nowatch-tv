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
import android.util.Log;

public class UpdateRunnable implements Runnable {

    private final String TAG = "UpdateRunnable";
    private Context ctxt;
    private int host;

    public UpdateRunnable(Context ctxt, int host) {
        this.host = host;
        this.ctxt = ctxt;
    }

    @Override
    public void run() {
        XMLReader xr;
        try {
            xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            RSS handler = new RSS(ctxt);
            xr.setContentHandler(handler);
            xr.setErrorHandler(handler);
            URLConnection c = (new URL(ctxt.getString(host))).openConnection();
            c.setRequestProperty("User-Agent", "Android/Nowatch.TV/" + Main.VERSION);
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

        public RSS(Context ctxt) {
            super(ctxt);
        }

        @Override
        public void endElement(String uri, String name, String qName) {
            super.endElement(uri, name, qName);
            if (name == "item") {
                // Log.v(TAG, "ITEM");
                // Log.v(TAG, "title=" + itemMap.get("title"));
                // Log.v(TAG, "file_uri=" + itemMap.get("file_uri"));
            } else if (name == "channel") {
                Log.v(TAG, "CHANNEL");
                Log.v(TAG, "title=" + channelMap.get("title"));
                Log.v(TAG, "link=" + channelMap.get("link"));
                Log.v(TAG, "desc=" + channelMap.get("description"));
            }
        }
    }
}
