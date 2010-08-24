package net.nowatch.network;

import java.util.Arrays;
import java.util.List;

import net.nowatch.Main;
import net.nowatch.utils.Item;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentValues;
import android.util.Log;

public class RSSReader extends DefaultHandler {

    private final String TAG = Main.TAG + "RSSReader";
    private final boolean LOG_INFO = false;
    private final List<String> feeds_fields = Arrays.asList("_id", "title", "description", "link",
            "pubDate", "image");
    private final List<String> items_fields = Arrays.asList("_id", "feed_id", "title",
            "description", "link", "pubDate", "file_uri", "file_size", "file_type");
    private boolean in_image = false;
    private String current_tag;
    private StringBuffer itemBuf;
    protected boolean in_items = false;
    protected ContentValues feedMap;
    protected ContentValues itemMap;

    private void logi(String str) {
        if (LOG_INFO) {
            Log.i(TAG, str);
        }
    }

    private void initMaps() {
        feedMap = new ContentValues();
        feedMap.put("title", "");
        feedMap.put("description", "");
        feedMap.put("link", "");
        feedMap.put("pubDate", "");
        feedMap.put("image", "");

        itemMap = new ContentValues();
        itemMap.put("feed_id", 0);
        itemMap.put("file_uri", "");
        itemMap.put("file_type", "");
        itemMap.put("file_size", "");
        itemMap.put("status", Item.STATUS_NEW);
    }

    @Override
    public void startDocument() {
        logi("Start parsing of the file!");
        initMaps();
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
        } else if (name == "image" && uri != "http://www.itunes.com/dtds/podcast-1.0.dtd") {
            in_image = true;
        }
        current_tag = name;
        itemBuf = new StringBuffer();

        // Get attributes of enclosure tags
        if (current_tag == "enclosure") {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
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
    public void endElement(String uri, String name, String qName) throws SAXException {
        logi("END=" + name);
        if (name == "image") {
            in_image = false;
        }
        if (in_items && items_fields.contains(current_tag) && current_tag != null) {
            itemMap.put(current_tag, itemBuf.toString());
            itemBuf.setLength(0);
        }
        current_tag = null;
    }

    @Override
    public void characters(char ch[], int start, int length) {
        logi("CHAR=" + new String(ch, start, length));
        // Get items info
        if (in_items && items_fields.contains(current_tag) && current_tag != null) {
            itemBuf.append(new String(ch, start, length));
        }
        // Get channel info (First IN)
        else if (feeds_fields.contains(current_tag) && current_tag != null
                && feedMap.get(current_tag) == "") {
            feedMap.put(current_tag, new String(ch, start, length));
        }
        // Get channel image url
        else if (in_image && current_tag == "url") {
            feedMap.put("image", new String(ch, start, length));
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
