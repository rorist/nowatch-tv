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
    private SQLiteDatabase db;
    private String current_tag;
    private boolean in_items = false;

    private static Map<String, String> channelMap;
    static {
        channelMap = new HashMap<String, String>();
        channelMap.put("title", "");
        channelMap.put("description", "");
        channelMap.put("link", "");
        channelMap.put("pubDate", "");
    }

    private static Map<String, String> itemMap;
    static {
        itemMap = new HashMap<String, String>();
        itemMap.put("title", "");
        itemMap.put("description", "");
        itemMap.put("link", "");
        itemMap.put("pubDate", "");
        itemMap.put("file_uri", "");
        itemMap.put("file_type", "");
        itemMap.put("file_size", "");
    }

    public RSSReader(Context ctxt) {
        super();
        db = (new DB(ctxt)).getWritableDatabase();
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
        }
        current_tag = name; // Set tag name (FIXME: HACKISH ? Better use int ?)
    }

    @Override
    public void endElement(String uri, String name, String qName) {
        logi("END=" + name);
        if (name == "item") {
            // Process item and save in db
            Log.v(TAG, "ITEM");
            Log.v(TAG, "title=" + itemMap.get("title"));
            Log.v(TAG, "link=" + itemMap.get("link"));
        } else if (name == "channel") {
            Log.v(TAG, "CHANNEL");
            Log.v(TAG, "title=" + channelMap.get("title"));
            Log.v(TAG, "link=" + channelMap.get("link"));
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
        else if (channelMap.get(current_tag) == "" && current_tag != null) {
            channelMap.put(current_tag, new String(ch, start, length));
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
