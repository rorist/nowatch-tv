package nowatch.tv;

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
    private SQLiteDatabase db;
    private int current_item = 0;

    private static String channel_title = null; // 1
    private static String channel_description; // 2
    private static String channel_link = null; // 3
    private static String channel_pubDate; // 4
    private static String channel_image; // 5

    private String item_title; // 6
    private String item_description; // 7
    private String item_link; // 8
    private String item_pubDate; // 9
    private String item_file_uri;
    private String item_file_type;
    private int item_file_size;

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
        // Get items info
        if (in_items) {
            if (name == "title") {
                current_item = 6;
            } else if (name == "description") {
                current_item = 7;
            } else if (name == "link") {
                current_item = 8;
            } else if (name == "enclosure") {
                String attr_name;
                for (int i = 0; i < attrs.getLength(); i++) {
                    logi(attrs.getLocalName(i) + "=" + attrs.getValue(i));
                    attr_name = attrs.getLocalName(i);
                    if (attr_name == "url") {
                    } else if (attr_name == "length") {
                    } else if (attr_name == "type") {
                    }
                }
            } else if (name == "pubDate") {
                current_item = 9;
            } else {
                current_item = 0;
            }
        }
        // Get channel info
        else {
            // TODO: Use switch case and byte comparison with integers (more
            // efficient) or not automatic fill of channel info anyhow
            if (name == "title" && channel_title == null) {
                current_item = 1;
            } else if (name == "description") {
                current_item = 2;
            } else if (name == "link" && channel_link == null) {
                current_item = 3;
            } else if (name == "pubDate") {
                current_item = 4;
            } else if (name == "image") {
                current_item = 5;
            } else {
                current_item = 0;
            }
        }
    }

    @Override
    public void endElement(String uri, String name, String qName) {
        logi("END=" + name);
        if (name == "item") {
            // Process item and save in db
            Log.v(TAG, "ITEM");
            Log.v(TAG, "title=" + item_title);
            Log.v(TAG, "link=" + item_link);
        } else if (name == "channel") {
            Log.v(TAG, "CHANNEL");
            Log.v(TAG, "title=" + channel_title);
            Log.v(TAG, "link=" + channel_link);
        }
    }

    @Override
    public void characters(char ch[], int start, int length) {
        switch (current_item) {
            case 1:
                channel_title = new String(ch, start, length);
                Log.v(TAG, "TITLE=" + channel_title);
                break;
            case 2:
                channel_description = new String(ch, start, length);
                break;
            case 3:
                channel_link = new String(ch, start, length);
                break;
            case 4:
                channel_pubDate = new String(ch, start, length);
                break;
            case 5:
                // TODO: get image as binary blob
                break;
            case 6:
                item_title = new String(ch, start, length);
                break;
            case 7:
                item_description = new String(ch, start, length);
                break;
            case 8:
                item_link = new String(ch, start, length);
                break;
            case 9:
                item_pubDate = new String(ch, start, length);
                break;
            default:
                break;
        }
        logi("CHAR=" + new String(ch, start, length));
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        Log.e(TAG, e.getMessage());
        super.error(e);
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        Log.e(TAG, e.getMessage());
        super.fatalError(e);
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        Log.e(TAG, e.getMessage());
        super.warning(e);
    }
}
