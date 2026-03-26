package com.musicbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;

/**
 * Manages the song queue and auto-advances to the next track.
 * A {@code nowPlayingCallback} is invoked whenever a track starts playing
 * (both on initial play and auto-advance), allowing the command handler to
 * send "Now Playing" embeds without coupling this class to JDA message types.
 */
public class TrackScheduler extends AudioEventAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TrackScheduler.class);

    private final AudioPlayer player;
    /** Songs waiting to be played. */
    public final Queue<AudioTrack> queue = new LinkedList<>();

    /** The text channel where the most recent !play command was issued. */
    private MessageChannel lastChannel;

    /**
     * Called when a track starts playing.
     * Parameters: (track, lastChannel).
     * Null-safe – set to null to disable.
     */
    private BiConsumer<AudioTrack, MessageChannel> nowPlayingCallback;

    /** Called when the queue is exhausted and playback stops. */
    private Runnable onQueueEnd;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enqueues a track. If nothing is currently playing the track starts
     * immediately and returns {@code true}. Otherwise it is added to the queue
     * and {@code false} is returned so the caller can send an "Added to Queue"
     * embed.
     */
    public boolean queue(AudioTrack track) {
        if (player.startTrack(track, /* noInterrupt= */ true)) {
            return true;   // started immediately
        }
        queue.offer(track);
        return false;      // added to queue
    }

    /** Skips the current track and starts the next one (or stops if empty). */
    public void skip() {
        // stop() -> onTrackEnd fires -> nextTrack() handles the queue
        player.stopTrack();
    }

    /** Stops playback and clears the queue. */
    public void clear() {
        queue.clear();
        player.stopTrack();
    }

    public void setLastChannel(MessageChannel channel) {
        this.lastChannel = channel;
    }

    public MessageChannel getLastChannel() {
        return lastChannel;
    }

    public void setNowPlayingCallback(BiConsumer<AudioTrack, MessageChannel> callback) {
        this.nowPlayingCallback = callback;
    }

    public BiConsumer<AudioTrack, MessageChannel> getNowPlayingCallback() {
        return nowPlayingCallback;
    }

    public void setOnQueueEnd(Runnable onQueueEnd) {
        this.onQueueEnd = onQueueEnd;
    }

    // ── AudioEventAdapter ─────────────────────────────────────────────────────

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        if (nowPlayingCallback != null) {
            nowPlayingCallback.accept(track, lastChannel);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (!endReason.mayStartNext) return;

        AudioTrack next = queue.poll();
        if (next != null) {
            player.startTrack(next, false);
        } else {
            if (onQueueEnd != null) {
                onQueueEnd.run();
            }
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track,
                                 com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
        logger.error("Track exception for '{}': {}", track.getInfo().title, exception.getMessage());
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        logger.warn("Track stuck '{}', skipping.", track.getInfo().title);
        skip();
    }
}
