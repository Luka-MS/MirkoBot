package com.musicbot;

import com.musicbot.music.GuildMusicManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.Color;
import java.util.List;

/**
 * Handles the result of {@link com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager#loadItemOrdered}
 * and sends the appropriate embed to Discord.
 */
public class PlayResultHandler implements AudioLoadResultHandler {

    private static final Color COLOR_SUCCESS = new Color(0x1DB954);
    private static final Color COLOR_ERROR   = new Color(0xFF4444);
    private static final Color COLOR_INFO    = new Color(0x5865F2);

    private final MessageReceivedEvent event;
    private final GuildMusicManager gmm;
    private final String originalQuery;

    public PlayResultHandler(MessageReceivedEvent event, GuildMusicManager gmm, String originalQuery) {
        this.event         = event;
        this.gmm           = gmm;
        this.originalQuery = originalQuery;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        boolean startedNow = gmm.scheduler.queue(track);
        if (!startedNow) {
            // Track was added to the queue — send "Added to Queue" embed
            AudioTrackInfo info = track.getInfo();
            int position = gmm.scheduler.queue.size();
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Added to Queue")
                    .setDescription("**" + info.title + "**")
                    .setColor(COLOR_INFO)
                    .addField("Duration", info.isStream ? "Live" : formatDuration(info.length / 1000), true)
                    .addField("Position", "#" + position, true);
            if (info.artworkUrl != null) eb.setThumbnail(info.artworkUrl);
            event.getChannel().sendMessageEmbeds(eb.build()).queue();
        }
        // If started immediately, the TrackScheduler's nowPlayingCallback sends the "Now Playing" embed
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        List<AudioTrack> tracks = playlist.getTracks();
        if (tracks.isEmpty()) {
            noMatches();
            return;
        }

        // ytsearch: returns a SearchResult playlist — just play the first track
        if (playlist.isSearchResult()) {
            trackLoaded(tracks.get(0));
            return;
        }

        // Real playlist — queue all tracks
        boolean startedNow = false;
        int queued = 0;
        for (AudioTrack track : tracks) {
            boolean started = gmm.scheduler.queue(track);
            if (started && !startedNow) {
                startedNow = true;
            } else {
                queued++;
            }
        }

        int totalLoaded = tracks.size();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(startedNow ? "Playlist Loaded" : "Playlist Added to Queue")
                .setDescription("**" + playlist.getName() + "**")
                .setColor(COLOR_SUCCESS)
                .addField("Songs", String.valueOf(totalLoaded), true);
        if (queued > 0) eb.addField("Queued", String.valueOf(queued), true);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    @Override
    public void noMatches() {
        reply(COLOR_ERROR, "Could not find **" + originalQuery + "**. Try a different search term.");
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        reply(COLOR_ERROR, "Could not load track: " + exception.getMessage());
    }

    private void reply(Color color, String message) {
        EmbedBuilder eb = new EmbedBuilder().setDescription(message).setColor(color);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private static String formatDuration(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
