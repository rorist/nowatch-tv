package nowatch.tv;

import nowatch.tv.DownloadInterfaceCallback;

interface DownloadInterface {

    void _registerCallback(DownloadInterfaceCallback cb);
    void _unregisterCallback(DownloadInterfaceCallback cb);
    int[] _getCurrentDownloads();
    int[] _getPendingDownloads(); 
    
}
