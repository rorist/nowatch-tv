package nowatch.tv;

interface DownloadInterface {

    void _startDownload(int id);
    void _cancelDownload(int id);
    void _stopOrContinue();
    int[] _getCurrentDownloads();
    int[] _getPendingDownloads(); 
    
}
