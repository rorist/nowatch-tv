package nowatch.tv;

// http://developer.android.com/intl/fr/guide/appendix/media-formats.html

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.app.Activity;
import android.os.Bundle;

public class Main extends Activity {

    // private final String TAG = "NowatchTV";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        XMLReader xr;
        try {
            xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            RSSReader handler = new RSSReader(this);
            xr.setContentHandler(handler);
            xr.setErrorHandler(handler);
            URL url = new URL(getString(R.string.feed_cinefuzz));
            URLConnection c = url.openConnection();
            c.setRequestProperty("User-Agent", "Android/Nowatch.TV");
            xr.parse(new InputSource(c.getInputStream()));
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
