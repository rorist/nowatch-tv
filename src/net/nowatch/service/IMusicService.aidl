package net.nowatch.service;


interface IMusicService {

    boolean isPlaying();
    void openFile(String msg, String file, String type, int item_id);
    void openFileId(int id);
    void play(int position);
    void pause();
    long getPosition();
    
}
