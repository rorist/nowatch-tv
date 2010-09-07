package net.nowatch.utils;

public class Feed {

    public int id;
    public int type;
    public String link_rss;
    public String etag;
    public String pubDate;

    public Feed(int _id, int _type, String _link_rss, String _etag, String _pubDate) {
        id = _id;
        type = _type;
        link_rss = _link_rss;
        etag = _etag;
        pubDate = _pubDate; 
    }

}
