package nowatch.tv;

interface DownloadInterface {

    boolean startDownload(int id);
    boolean cancelDownload(int id);
    int[] getCurrentDownloads();
    int[] getPendingDownload(); 
    void setStatus(int id);
    
}