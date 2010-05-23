package nowatch.tv;

public class Feed {

    public static final int STATUS_NEW = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_UNREAD = 3;
    public static final int STATUS_READ = 4;

    public int _id;
    public int _resource;

    public Feed(int id, int resource) {
        _id = id;
        _resource = resource;
    }

}
