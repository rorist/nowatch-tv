package nowatch.tv;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentValues;
import android.util.Log;

public class RSSReader extends DefaultHandler {

    private final String TAG = "RSSReader";
    protected final static String USERAGENT = "Android/Nowatch.TV/0.1";
    private final boolean LOG_INFO = false;
    private final List<String> feeds_fields = Arrays.asList("_id", "title", "description", "link",
            "pubDate", "image");
    private final List<String> items_fields = Arrays.asList("_id", "feed_id", "title",
            "description", "link", "pubDate");
    private boolean in_items = false;
    private boolean in_image = false;
    private String current_tag;
    protected ContentValues channelMap;
    protected ContentValues itemMap;

    private void logi(String str) {
        if (LOG_INFO) {
            Log.i(TAG, str);
        }
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
        if (in_items && items_fields.contains(current_tag) && current_tag != null) {
            itemMap.put(current_tag, new String(ch, start, length));
        }
        // Get channel info (First IN)
        else if (feeds_fields.contains(current_tag) && current_tag != null
                && channelMap.get(current_tag) == "") {
            channelMap.put(current_tag, new String(ch, start, length));
        }
        // Get channel image url
        else if (in_image && current_tag == "url") {
            channelMap.put("image", new String(ch, start, length));
        }
    }

    private void initMaps() {
        channelMap = new ContentValues();
        channelMap.put("title", "");
        channelMap.put("description", "");
        channelMap.put("link", "");
        channelMap.put("pubDate", "");
        channelMap.put("image", "");
        itemMap = new ContentValues();
        itemMap.put("feed_id", 0);
        itemMap.put("file_uri", "");
        itemMap.put("file_type", "");
        itemMap.put("file_size", "");
    }

    protected InputStream getFile(String url) {
        HttpGet httpget = new HttpGet(url);
        HttpClient httpclient = new DefaultHttpClient();
        HttpResponse response;
        httpget.getParams().setParameter("http.useragent", USERAGENT);
        httpget.getParams().setParameter("http.protocol.content-charset", "UTF-8");
        httpget.getParams().setParameter("http.protocol.expect-continue", true);
        httpget.getParams().setParameter("http.protocol.wait-for-continue", 5000);
        try {
            try {
                response = httpclient.execute(httpget);
            } catch (SSLException e) {
                Log.i(TAG, "SSL Certificate is not trusted");
                response = httpclient.execute(httpget);
            }
            Log.i(TAG, "Status:[" + response.getStatusLine().toString() + "] " + url);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return entity.getContent();
            }
        } catch (ClientProtocolException e) {
            Log.e(TAG, "There was a protocol based error", e);
        } catch (IOException e) {
            Log.e(TAG, "There was an IO Stream related error", e);
        }
        return null;
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
