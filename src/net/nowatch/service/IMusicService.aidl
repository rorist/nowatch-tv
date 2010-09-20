package net.nowatch.service;


interface IMusicService {

    void openFile(String file);
    void play(long position);
    void pause();
    long getPosition();
    
}
