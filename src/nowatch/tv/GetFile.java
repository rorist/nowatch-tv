// HTTP Client based on AndroidHttpClient (API level 8)

package nowatch.tv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.os.Environment;
import android.util.Log;

public class GetFile {

    private final String TAG = Main.TAG + "GetFile";
    public static final String PATH_CACHE = "Android/data/nowatch.tv/cache";
    public static final String PATH_PODCASTS = "Podcasts/Nowatch.TV";
    // FIXME: Grab a context and get real version
    public static final String USERAGENT = "Android/" + android.os.Build.VERSION.RELEASE + " ("
            + android.os.Build.MODEL + ") Nowatch.TV/1.0beta";

    private DefaultHttpClient httpclient = null;
    private int buffer_size = 8 * 1024; // in Bytes
    private boolean deleteOnFinish = false;

    protected String etag;
    protected long file_size = 0;

    private HttpEntity openUrl(String src, String etag) {
        // Set HTTP Client params
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
        HttpConnectionParams.setSoTimeout(params, 20 * 1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpClientParams.setRedirecting(params, true);
        params.setParameter("http.useragent", USERAGENT);

        // Register standard protocols
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        ClientConnectionManager manager = new ThreadSafeClientConnManager(params, schemeRegistry);

        // Init Client
        final HttpGet httpget = new HttpGet(src);
        httpclient = new DefaultHttpClient(manager, params);

        try {
            // Headers
            if (etag != null) {
                httpget.addHeader("If-None-Match", etag);
            }
            // Get response
            HttpResponse response = httpclient.execute(httpget);
            final int statusCode = response.getStatusLine().getStatusCode();
            Log.i(TAG, "Status:[" + statusCode + "] " + src);
            if (statusCode != HttpStatus.SC_OK) {
                return null;
            }
            // Save etag
            else if (response.getLastHeader("ETag") != null) {
                this.etag = response.getLastHeader("ETag").getValue();
            }
            // Save file_size
            if (response.getLastHeader("Content-Length") != null) {
                try {
                    file_size = Long.parseLong(response.getLastHeader("Content-Length").getValue());
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Length=" + response.getLastHeader("Content-Length").getValue()
                            + ", " + e.getMessage());
                }
            }
            // Retrieve content
            return response.getEntity();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            httpget.abort();
            /*
             * if (httpclient != null) { httpclient.close(); }
             */
            return null;
        }
    }

    public File getDestination(String dst) {
        try {
            if (dst != null) {
                return new File(dst);
            } else {
                File dstFile = new File(Environment.getExternalStorageDirectory()
                        .getCanonicalPath()
                        + "/" + PATH_CACHE);
                dstFile.mkdirs();
                return File.createTempFile("nowatchtv", "", dstFile);
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    // Method for small file download (images, xml, ..)

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

        // Get InputStream
        final HttpEntity entity = openUrl(src, etag);
        if (entity == null) {
            return;
        }
        if (file_size < 1) {
            file_size = entity.getContentLength();
            /*
             * if (file_size < 1){ file_size = 256000; }
             */
        }
        InputStream in = entity.getContent();
        File dstFile = getDestination(dst);
        if (in != null && dstFile != null) {
            FileOutputStream out = new FileOutputStream(dstFile);
            final ReadableByteChannel inputChannel = Channels.newChannel(in);
            final WritableByteChannel outputChannel = Channels.newChannel(out);
            // TODO: see if FileChannel.transferFrom() would be nice
            try {
                // Fast Channel Copy
                /*
                 * if (inputChannel != null && out!= null) { FileChannel
                 * filechannel = out.getChannel(); Log.v(TAG,
                 * "size="+file_size); filechannel.transferFrom(inputChannel,
                 * 0L, file_size); if (filechannel != null) {
                 * filechannel.close(); }
                 */
                if (inputChannel != null && outputChannel != null) {
                    // final ByteBuffer buffer =
                    // ByteBuffer.allocateDirect(buffer_size);
                    final ByteBuffer buffer = ByteBuffer.allocate(buffer_size);
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
                if (inputChannel != null) {
                    inputChannel.close();
                }
                if (outputChannel != null) {
                    outputChannel.close();
                }
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (entity != null) {
                    entity.consumeContent();
                }
                finish(dstFile.getAbsolutePath());
            }
            return;
        } else {
            if (dstFile != null) {
                finish(dstFile.getAbsolutePath());
            }
        }
    }

    // Method for podcast download
    public void getBlocking(String src, String dst) {
        /*
         * if
         * (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState
         * ())) { return; }
         * 
         * try { // Get InputStream final HttpEntity entity = openUrl(src,
         * etag); if (entity == null) { return; } InputStream in =
         * entity.getContent(); File dstFile = getDestination(dst); if (in !=
         * null && dstFile != null) { OutputStream out = new
         * FileOutputStream(dstFile);
         * 
         * } } catch(IOException e) { Log.e(TAG, e.getMessage()); }finally { if
         * (in != null) { in.close(); } if (out != null) { out.close(); } if
         * (entity != null) { entity.consumeContent(); } if (httpclient != null)
         * { httpclient.close(); } finish(dstFile.getAbsolutePath()); }
         */
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
