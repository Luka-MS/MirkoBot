package com.musicbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * Holds the per-guild AudioPlayer, TrackScheduler and JDA send handler.
 */
public class GuildMusicManager {

    public final AudioPlayer player;
    public final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(this.player);
        this.player.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.player);
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    /** Returns true if a track is currently playing (not paused). */
    public boolean isPlaying() {
        return player.getPlayingTrack() != null && !player.isPaused();
    }

    /** Returns true if a track is loaded (playing or paused). */
    public boolean hasTrack() {
        return player.getPlayingTrack() != null;
    }
}
