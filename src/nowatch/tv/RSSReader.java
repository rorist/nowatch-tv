package nowatch.tv;

import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class RSSReader extends DefaultHandler {

    private final String TAG = "RSSReader";
    private final boolean LOG_INFO = false;
    private boolean in_items = false;
    private boolean in_image = false;
    private String current_tag;
    private SQLiteDatabase db;

    protected static Map<String, String> channelMap;
    static {
        channelMap = new HashMap<String, String>();
        channelMap.put("title", null);
        channelMap.put("description", null);
        channelMap.put("link", null);
        channelMap.put("pubDate", null);
        channelMap.put("image_url", null);
    }

    protected static Map<String, String> itemMap;
    static {
        itemMap = new HashMap<String, String>();
        itemMap.put("file_uri", null);
        itemMap.put("file_type", null);
        itemMap.put("file_size", null);
    }

    public RSSReader(Context ctxt) {
        super();
        db = (new DB(ctxt)).getWritableDatabase();
        db.close();
    }

    private void logi(String str) {
        if (LOG_INFO) {
            Log.i(TAG, str);
        }
    }

    @Override
    public void startDocument() {
        logi("Start parsing of the file!");
    }

    @Override
    public void endDocument() {
        logi("Parsing done!");
    }

    @Override
    public void startElement(String uri, String name, String qName, Attributes attrs) {
        logi("START=" + name);
        if (name == "item") {
            in_items = true;
            return;
        } else if (name == "image") {
            in_image = true;
        }
        current_tag = name;

        // Get attributes of enclosure tags
        if (current_tag == "enclosure") {
            for (int i = 0; i < attrs.getLength(); i++) {
                logi(attrs.getLocalName(i) + "=" + attrs.getValue(i));
                if (attrs.getLocalName(i) == "url") {
                    itemMap.put("file_uri", attrs.getValue(i));
                } else if (attrs.getLocalName(i) == "length") {
                    itemMap.put("file_size", attrs.getValue(i));
                } else if (attrs.getLocalName(i) == "type") {
                    itemMap.put("file_type", attrs.getValue(i));
                }
            }
        }
    }

    @Override
    public void endElement(String uri, String name, String qName) {
        logi("END=" + name);
        if (name == "image") {
            in_image = false;
        }
        current_tag = null;
    }

    @Override
    public void characters(char ch[], int start, int length) {
        logi("CHAR=" + new String(ch, start, length));
        // Get items info
        if (in_items && current_tag != null) {
            itemMap.put(current_tag, new String(ch, start, length));
        }
        // Get channel info (First IN)
        else if (channelMap.get(current_tag) == null && current_tag != null) {
            channelMap.put(current_tag, new String(ch, start, length));
        }
        // Get channel image url
        else if (in_image && current_tag == "url") {
            channelMap.put("image_url", new String(ch, start, length));
        }
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        Log.e(TAG + ":ErrorHandler", e.getMessage());
        super.error(e);
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        Log.e(TAG + ":ErrorHandler", e.getMessage());
        super.fatalError(e);
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        Log.e(TAG + ":ErrorHandler", e.getMessage());
        super.warning(e);
    }
}
