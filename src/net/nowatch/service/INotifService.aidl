package net.nowatch.service;

import net.nowatch.service.INotifServiceCallback;

interface INotifService {

    void _registerCallback(INotifServiceCallback cb);
    void _unregisterCallback(INotifServiceCallback cb);
    int[] _getCurrentDownloads();
    int[] _getPendingDownloads(); 
    
}
