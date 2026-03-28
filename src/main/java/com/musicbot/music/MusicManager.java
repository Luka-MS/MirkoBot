package com.musicbot.music;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.TvHtml5Simply;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton-style factory that owns the shared {@link AudioPlayerManager} and
 * creates / caches a {@link GuildMusicManager} for every guild.
 */
public class MusicManager {

    private static final Logger logger = LoggerFactory.getLogger(MusicManager.class);

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> managers = new ConcurrentHashMap<>();

    public MusicManager(String youtubeRefreshToken) {
        playerManager = new DefaultAudioPlayerManager();

        // YouTube – must be registered BEFORE the generic remote sources because
        // LavaPlayer 2.x removed YouTube from the core.
        // Use remote cipher server to handle YouTube's ever-changing signature decryption.
        // TV is the OAuth-compatible client; others handle search/playlists.
        YoutubeSourceOptions ytOptions = new YoutubeSourceOptions()
                .setRemoteCipher("https://cipher.kikkia.dev/", null, "MirkoBot");
        YoutubeAudioSourceManager ytSource = new YoutubeAudioSourceManager(
                ytOptions, new Client[] { new Music(), new Tv(), new TvHtml5Simply(), new Web() });

        // Enable OAuth2 for YouTube to avoid bot-detection blocks.
        if (youtubeRefreshToken != null && !youtubeRefreshToken.isBlank()) {
            ytSource.useOauth2(youtubeRefreshToken, true);
            logger.info("YouTube OAuth2 enabled with existing refresh token.");
        } else {
            // First run: triggers device auth flow — follow the instructions in the console.
            ytSource.useOauth2(null, false);
            logger.info("YouTube OAuth2: device auth flow started. Check console for instructions.");
        }

        playerManager.registerSourceManager(ytSource);
        logger.info("YouTube audio source registered.");

        // SoundCloud, Bandcamp, Vimeo, Twitch, HTTP streams, etc.
        AudioSourceManagers.registerRemoteSources(
                playerManager, MediaContainerRegistry.DEFAULT_REGISTRY);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    /**
     * Returns the {@link GuildMusicManager} for the given guild, creating one
     * on first call.
     */
    public synchronized GuildMusicManager get(Guild guild) {
        return managers.computeIfAbsent(guild.getIdLong(), id -> {
            GuildMusicManager gmm = new GuildMusicManager(playerManager);
            // Wire up the JDA audio send handler
            guild.getAudioManager().setSendingHandler(gmm.getSendHandler());
            return gmm;
        });
    }

    public AudioPlayerManager getPlayerManager() {
        return playerManager;
    }
}
