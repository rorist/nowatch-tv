package net.nowatch.service;


interface IMusicService {

    void openFile(String file);
    void play(int position);
    void pause();
    long getPosition();
    
}
