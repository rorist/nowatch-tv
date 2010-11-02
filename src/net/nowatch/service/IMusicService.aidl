package net.nowatch.service;

import net.nowatch.service.IMusicServiceCallback;

interface IMusicService {

    boolean isPlaying();
    void openFile(String msg, String file, String type, int item_id, boolean autoplay);
    void openFileId(int id, boolean autoplay);
    void seek(int position);
    void play();
    void pause();
    long getPosition();
    int getItemId();
    int getBufferPercent();
    void registerCallback(IMusicServiceCallback cb);
    void unregisterCallback(IMusicServiceCallback cb);
    
}
