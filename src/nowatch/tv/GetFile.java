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
import android.os.Environment;

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
    private final String USERAGENT = "Android/Nowatch.TV/1.2";
    private DefaultHttpClient httpclient;
    private int buffer_size = 8 * 1024; // in Bytes
    private boolean deleteOnFinish = false;
    protected String etag;

    public void getChannel(String src, String dst, String etag) throws IOException {
        getChannel(src, dst, etag, deleteOnFinish);
    }

    public void getChannel(String src, String dst, String etag, boolean _deleteOnFinish)
            throws IOException {
        deleteOnFinish = _deleteOnFinish;

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return;
        }

        // Get destination file
        File dstFile;
        if (dst != null) {
            dstFile = new File(dst);
        } else {
            dstFile = new File(Environment.getExternalStorageDirectory().getCanonicalPath()
                    + "/Android/data/nowatch.tv/cache");
            dstFile.mkdirs();
            dstFile = File.createTempFile("nowatchtv", "", dstFile);
        }

        // Open URL
        httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter("http.useragent", USERAGENT);
        InputStream in = null;
        HttpGet httpget = new HttpGet(src);
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
            Log.i(TAG, "Status:[" + response.getStatusLine().toString() + "] " + src);

            // Exit if content not modified
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
                return;
            }
            // Save etag
            else if (response.getLastHeader("ETag") != null) {
                this.etag = response.getLastHeader("ETag").getValue();
            }

            // Retrieve content
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                in = entity.getContent();
            }
        } catch (ClientProtocolException e) {
            Log.e(TAG, "There was a protocol based error", e);
        } catch (UnknownHostException e) {
            Log.e(TAG, "Connectivity errror", e);
        } catch (IOException e) {
            Log.e(TAG, "There was an IO Stream related error", e);
        }

        // Get channel
        if (in != null) {
            OutputStream out = new FileOutputStream(dstFile);
            final ReadableByteChannel inputChannel = Channels.newChannel(in);
            final WritableByteChannel outputChannel = Channels.newChannel(out);
            try {
                // Fast Channel Copy
                if (inputChannel != null && outputChannel != null) {
                    final ByteBuffer buffer = ByteBuffer.allocateDirect(buffer_size);
                    int count;
                    while ((count = inputChannel.read(buffer)) != -1) {
                        buffer.flip();
                        outputChannel.write(buffer);
                        buffer.compact();
                        update(count);
                    }
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        outputChannel.write(buffer);
                    }
                }
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
            return;
        }
        finish(null);
    }

    protected void update(int count) {
        // Nothing to do here
    }

    protected void finish(String file) {
        if (deleteOnFinish) {
            new File(file).delete();
        }
    }
}
