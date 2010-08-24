package net.nowatch;

import net.nowatch.IServiceCallback;

interface IService {

    void _registerCallback(IServiceCallback cb);
    void _unregisterCallback(IServiceCallback cb);
    int[] _getCurrentDownloads();
    int[] _getPendingDownloads(); 
    
}
