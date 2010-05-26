package nowatch.tv;

interface DownloadInterface {

    void startDownload(int id);
    void cancelDownload(int id);
    int[] getCurrentDownloads();
    int[] getPendingDownload(); 
    
}
