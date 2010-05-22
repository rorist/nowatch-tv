package nowatch.tv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import javax.net.ssl.SSLException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;

public class GetFile {

    private final String TAG = "GetFile";
    private final String USERAGENT = "Android/Nowatch.TV/0.1";
    private DefaultHttpClient httpclient;
    protected int buffer_size = 16 * 1024; // in Bytes
    protected String etag;

    public String getChannel(String src, String dst, String etag) throws IOException {
        File dstFile;
        if (dst != null) {
            dstFile = new File(dst);
        } else {
            dstFile = File.createTempFile(".nowatchtv", "");
        }
        httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter("http.useragent", USERAGENT);

        InputStream in = openURL(src, etag);
        if ( in != null) {
            OutputStream out = new FileOutputStream(dstFile);
            final ReadableByteChannel inputChannel = Channels.newChannel(in);
            final WritableByteChannel outputChannel = Channels.newChannel(out);
            try {
                fastChannelCopy(inputChannel, outputChannel);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            } finally {
                inputChannel.close();
                outputChannel.close();
                in.close();
                out.close();
                finish(dstFile.getAbsolutePath());
            }
            return dstFile.getAbsolutePath();
        }
        finish(null);
        return null;
    }

    private InputStream openURL(String url, String etag) {
        HttpGet httpget = new HttpGet(url);
        HttpResponse response;
        // Add headers
        // TODO: We don't need Last-Modified unless new feeds do
        if (etag != null) {
            httpget.addHeader("If-None-Match", etag);
        }
        // Execute request
        try {
            try {
                response = httpclient.execute(httpget);
            } catch (SSLException e) {
                Log.i(TAG, "SSL Certificate is not trusted");
                response = httpclient.execute(httpget);
            }
            Log.i(TAG, "Status:[" + response.getStatusLine().toString() + "] " + url);

            // Exit if content not modified
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
                return null;
            }
            // Save etag
            else if (response.getLastHeader("ETag") != null) {
                this.etag = response.getLastHeader("ETag").getValue();
                Log.v(TAG, "ETag3="+etag);
            }

            // Retrieve content
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return entity.getContent();
            }
        } catch (ClientProtocolException e) {
            Log.e(TAG, "There was a protocol based error", e);
        } catch (UnknownHostException e) {
            Log.e(TAG, "Connectivity errror", e);
        } catch (IOException e) {
            Log.e(TAG, "There was an IO Stream related error", e);
        }
        return null;
    }

    private void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest)
            throws IOException, NullPointerException {
        if (src != null && dest != null) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(buffer_size);
            int count;
            while ((count = src.read(buffer)) != -1) {
                buffer.flip();
                dest.write(buffer);
                buffer.compact();
                update(count);
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
        }
    }

    protected void update(int count) {
        // Nothing to do here
    }

    protected void finish(String file) {
        // Nothing to do here
    }
}
