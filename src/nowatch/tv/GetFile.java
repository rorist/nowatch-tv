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

    private final String TAG = Main.TAG + "GetFile";
    public static final String PATH_CACHE = "Android/data/nowatch.tv/cache";
    public static final String PATH_PODCASTS = "Podcasts/Nowatch.TV";
    public static final String USERAGENT = "Android/" + android.os.Build.VERSION.RELEASE + " ("
            + android.os.Build.MODEL + ") Nowatch.TV/1.0beta";

    private DefaultHttpClient httpclient;
    private int buffer_size = 8 * 1024; // in Bytes
    private boolean deleteOnFinish = false;

    protected boolean cancel = false;
    protected String etag;
    protected String file_size;

    public void getChannel(String src, String dst, String etag) throws ClientProtocolException,
            UnknownHostException, IOException {
        getChannel(src, dst, etag, deleteOnFinish);
    }

    public void getChannel(String src, String dst, String etag, boolean _deleteOnFinish)
            throws ClientProtocolException, UnknownHostException, IOException {
        deleteOnFinish = _deleteOnFinish;

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return;
        }

        // Get destination file
        File dstFile;
        if (dst != null) {
            dstFile = new File(dst);
        } else {
            dstFile = new File(Environment.getExternalStorageDirectory().getCanonicalPath() + "/"
                    + PATH_CACHE);
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
            response = httpclient.execute(httpget);
        } catch (SSLException e) {
            Log.i(TAG, "SSL Certificate is not trusted");
            response = httpclient.execute(httpget);
        }
        Log.i(TAG, "Status:[" + response.getStatusLine().toString() + "] " + src);
        Log.i("", "Useragent:[" + USERAGENT + "]");

        // Exit if content not modified
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
            return;
        }
        // Save etag
        else if (response.getLastHeader("ETag") != null) {
            this.etag = response.getLastHeader("ETag").getValue();
        }
        // Save file_size
        if (response.getLastHeader("Content-Length") != null) {
            this.file_size = response.getLastHeader("Content-Length").getValue();
        }

        // Retrieve content
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            in = entity.getContent();
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
                        if (cancel) {
                            Log.v(TAG, "cancelling download");
                            inputChannel.close();
                            outputChannel.close();
                            in.close();
                            out.close();
                            finish(dstFile.getAbsolutePath());
                            return;
                        }
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
    }

    protected void update(int count) {
        // Nothing to do here
    }

    protected void finish(String file) {
        if (deleteOnFinish && file != null) {
            new File(file).delete();
        }
    }
}
