package nowatch.tv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
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
    private boolean in_image = false;
    protected boolean in_items = false;
    private String current_tag;
    protected ContentValues channelMap;
    protected ContentValues itemMap;
    private DefaultHttpClient httpclient;

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
        } else if (name == "image" && uri != "http://www.itunes.com/dtds/podcast-1.0.dtd") {
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
    public void endElement(String uri, String name, String qName) throws SAXException {
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

    protected String getFile(String url) throws IOException {
        File dst = File.createTempFile("nowatchtv", "");
        dst.deleteOnExit();
        httpclient = new DefaultHttpClient();
        InputStream in = openURL(url);
        OutputStream out = new FileOutputStream(dst);
        final ReadableByteChannel inputChannel = Channels.newChannel(in);
        final WritableByteChannel outputChannel = Channels.newChannel(out);

        try {
            fastChannelCopy(inputChannel, outputChannel);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            inputChannel.close();
            outputChannel.close();
            in.close();
            out.close();
        }
        return dst.getAbsolutePath();
    }

    private InputStream openURL(String url) {
        HttpGet httpget = new HttpGet(url);
        HttpResponse response;
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

    private static void fastChannelCopy(final ReadableByteChannel src,
            final WritableByteChannel dest) throws IOException, NullPointerException {
        if (src != null) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
            while (src.read(buffer) != -1) {
                buffer.flip();
                dest.write(buffer);
                buffer.compact();
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
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
