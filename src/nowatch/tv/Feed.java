package nowatch.tv;

public class Feed {

    public static final int STATUS_NEW = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_UNREAD = 2;
    public static final int STATUS_READ = 3;

    public int _id;
    public int _resource;

    public Feed(int id, int resource) {
        _id = id;
        _resource = resource;
    }

}
