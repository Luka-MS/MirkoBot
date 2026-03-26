package com.musicbot;

import com.musicbot.counting.CountingManager;
import com.musicbot.music.GuildMusicManager;
import com.musicbot.music.MusicManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class CommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);

    private static final Color COLOR_SUCCESS = new Color(0x1DB954);
    private static final Color COLOR_ERROR   = new Color(0xFF4444);
    private static final Color COLOR_INFO    = new Color(0x5865F2);
    private static final Color COLOR_ORANGE  = new Color(0xFFA500);

    private final MusicManager musicManager;
    private final CountingManager countingManager = new CountingManager();
    private final Random random = new Random();

    public CommandListener(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String prefix = Config.getPrefix();
        String raw = event.getMessage().getContentRaw();

        // Counting channel logic (runs regardless of prefix)
        CountingManager.State countState = countingManager.get(event.getGuild().getIdLong());
        if (countState != null
                && event.getChannel().getIdLong() == countState.channelId
                && !raw.startsWith(prefix)) {
            handleCountingMessage(event, countState);
            return;
        }

        if (!raw.startsWith(prefix)) return;

        // Split off command name and the rest
        String withoutPrefix = raw.substring(prefix.length()).trim();
        String[] parts = withoutPrefix.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args    = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "play"          -> handlePlay(event, args);
            case "pause"         -> handlePause(event);
            case "resume"        -> handleResume(event);
            case "stop"          -> handleStop(event);
            case "skip"          -> handleSkip(event);
            case "queue"         -> handleQueue(event);
            case "leave"         -> handleLeave(event);
            case "penis"         -> handlePenis(event);
            case "clearchat",
                 "clear",
                 "purge"         -> handleClearchat(event, args);
            case "setcounting"   -> handleSetCounting(event, args);
            case "countingscore" -> handleCountingScore(event);
            case "commands",
                 "help"          -> handleHelp(event);
        }
    }

    // ── Music commands ────────────────────────────────────────────────────────

    private void handlePlay(MessageReceivedEvent event, String query) {
        if (query.isBlank()) {
            reply(event, COLOR_ERROR, "Please provide a song name or URL. Example: `" + Config.getPrefix() + "play never gonna give you up`");
            return;
        }

        Member member = event.getMember();
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            reply(event, COLOR_ERROR, "You need to be in a voice channel first!");
            return;
        }

        AudioChannel voiceChannel = member.getVoiceState().getChannel();
        Guild guild = event.getGuild();
        AudioManager audioManager = guild.getAudioManager();

        // Join or move to the user's voice channel
        if (!audioManager.isConnected()) {
            audioManager.openAudioConnection(voiceChannel);
            audioManager.setSelfDeafened(true);
        } else if (!audioManager.getConnectedChannel().equals(voiceChannel)) {
            audioManager.openAudioConnection(voiceChannel);
        }

        GuildMusicManager gmm = musicManager.get(guild);
        gmm.scheduler.setLastChannel(event.getChannel().asTextChannel());

        // Wire up callbacks once
        if (gmm.scheduler.getNowPlayingCallback() == null) {
            gmm.scheduler.setNowPlayingCallback((track, ch) -> {
                if (ch == null) return;
                sendNowPlaying(ch, track);
                guild.getJDA().getPresence().setActivity(
                        Activity.listening(track.getInfo().title));
            });
            gmm.scheduler.setOnQueueEnd(() ->
                    guild.getJDA().getPresence().setActivity(
                            Activity.listening("music requests")));
        }

        // Search prefix for plain text queries
        String searchQuery = (query.startsWith("http://") || query.startsWith("https://"))
                ? query
                : "ytsearch:" + query;

        reply(event, COLOR_INFO, "Searching for **" + query + "**\u2026");

        musicManager.getPlayerManager().loadItemOrdered(gmm, searchQuery,
                new PlayResultHandler(event, gmm, query));
    }

    private void sendNowPlaying(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel ch, AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Now Playing")
                .setDescription("**" + info.title + "**")
                .setColor(COLOR_SUCCESS)
                .addField("Duration", info.isStream ? "Live" : formatDuration(info.length / 1000), true)
                .addField("Author", info.author, true);
        if (info.artworkUrl != null) eb.setThumbnail(info.artworkUrl);
        ch.sendMessageEmbeds(eb.build()).queue();
    }

    private void handlePause(MessageReceivedEvent event) {
        GuildMusicManager gmm = musicManager.get(event.getGuild());
        if (gmm.player.getPlayingTrack() == null || gmm.player.isPaused()) {
            reply(event, COLOR_ERROR, "Nothing is playing right now.");
            return;
        }
        gmm.player.setPaused(true);
        String title = gmm.player.getPlayingTrack().getInfo().title;
        event.getGuild().getJDA().getPresence().setActivity(
                Activity.listening(title + " (paused)"));
        reply(event, COLOR_ORANGE, "Paused the music.");
    }

    private void handleResume(MessageReceivedEvent event) {
        GuildMusicManager gmm = musicManager.get(event.getGuild());
        if (gmm.player.getPlayingTrack() == null || !gmm.player.isPaused()) {
            reply(event, COLOR_ERROR, "Nothing is paused right now.");
            return;
        }
        gmm.player.setPaused(false);
        event.getGuild().getJDA().getPresence().setActivity(
                Activity.listening(gmm.player.getPlayingTrack().getInfo().title));
        reply(event, COLOR_SUCCESS, "Resumed the music.");
    }

    private void handleStop(MessageReceivedEvent event) {
        GuildMusicManager gmm = musicManager.get(event.getGuild());
        if (!gmm.hasTrack()) {
            reply(event, COLOR_ERROR, "Nothing is playing right now.");
            return;
        }
        gmm.scheduler.clear();
        event.getGuild().getJDA().getPresence().setActivity(Activity.listening("music requests"));
        reply(event, COLOR_ERROR, "Stopped the music and cleared the queue.");
    }

    private void handleSkip(MessageReceivedEvent event) {
        GuildMusicManager gmm = musicManager.get(event.getGuild());
        if (!gmm.hasTrack()) {
            reply(event, COLOR_ERROR, "Nothing is playing right now.");
            return;
        }
        String title = gmm.player.getPlayingTrack().getInfo().title;
        gmm.scheduler.skip();
        reply(event, COLOR_INFO, "Skipped **" + title + "**.");
    }

    private void handleQueue(MessageReceivedEvent event) {
        GuildMusicManager gmm = musicManager.get(event.getGuild());
        AudioTrack current = gmm.player.getPlayingTrack();
        Queue<AudioTrack> queue = gmm.scheduler.queue;

        if (current == null && queue.isEmpty()) {
            reply(event, COLOR_INFO, "The queue is empty.");
            return;
        }

        EmbedBuilder eb = new EmbedBuilder().setTitle("Queue").setColor(COLOR_INFO);

        if (current != null) {
            AudioTrackInfo info = current.getInfo();
            String dur = info.isStream ? "Live" : formatDuration(info.length / 1000);
            eb.addField("Now Playing", info.title + " `" + dur + "`", false);
        }

        if (!queue.isEmpty()) {
            List<AudioTrack> list = queue.stream().toList();
            StringBuilder sb = new StringBuilder();
            int shown = Math.min(list.size(), 10);
            for (int i = 0; i < shown; i++) {
                AudioTrackInfo info = list.get(i).getInfo();
                String dur = info.isStream ? "Live" : formatDuration(info.length / 1000);
                sb.append("`").append(i + 1).append(".` ").append(info.title)
                  .append(" `").append(dur).append("`\n");
            }
            eb.addField("Up Next", sb.toString(), false);
            if (list.size() > 10) {
                eb.setFooter("... and " + (list.size() - 10) + " more songs");
            }
        }

        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleLeave(MessageReceivedEvent event) {
        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            reply(event, COLOR_ERROR, "I'm not in a voice channel.");
            return;
        }
        GuildMusicManager gmm = musicManager.get(event.getGuild());
        gmm.scheduler.clear();
        audioManager.closeAudioConnection();
        event.getGuild().getJDA().getPresence().setActivity(Activity.listening("music requests"));
        reply(event, COLOR_INFO, "Disconnected and cleared the queue.");
    }

    // ── Misc commands ─────────────────────────────────────────────────────────

    private void handlePenis(MessageReceivedEvent event) {
        int length = random.nextInt(20) + 1;
        String shaft = "=".repeat(length);
        event.getChannel().sendMessage(event.getMember().getEffectiveName() + ": 8" + shaft + ">").queue();
    }

    private void handleClearchat(MessageReceivedEvent event, String args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MESSAGE_MANAGE)) {
            reply(event, COLOR_ERROR, "You need the **Manage Messages** permission to use this command.");
            return;
        }

        int amount = 10;
        if (!args.isBlank()) {
            try {
                amount = Integer.parseInt(args.trim());
            } catch (NumberFormatException e) {
                reply(event, COLOR_ERROR, "Please provide a valid number. Example: `" + Config.getPrefix() + "clearchat 10`");
                return;
            }
        }

        if (amount < 1 || amount > 100) {
            reply(event, COLOR_ERROR, "Please specify a number between 1 and 100.");
            return;
        }

        final int toDelete = amount;
        // Retrieve messages then bulk-delete (includes the command message itself)
        event.getChannel().getHistory().retrievePast(toDelete + 1).queue(messages -> {
            if (messages.size() == 1) {
                messages.get(0).delete().queue();
            } else {
                event.getChannel().asTextChannel().deleteMessages(messages).queue();
            }
            reply(event, COLOR_INFO, "Deleted **" + (messages.size() - 1) + "** message(s).");
        });
    }

    // ── Counting commands ─────────────────────────────────────────────────────

    private void handleSetCounting(MessageReceivedEvent event, String args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_CHANNEL)) {
            reply(event, COLOR_ERROR, "You need the **Manage Channels** permission to use this command.");
            return;
        }

        long channelId = event.getChannel().getIdLong();

        // Parse an optional #channel mention
        if (!args.isBlank()) {
            String stripped = args.trim().replaceAll("[^0-9]", "");
            if (!stripped.isEmpty()) {
                try {
                    channelId = Long.parseLong(stripped);
                } catch (NumberFormatException ignored) {
                    // fall back to current channel
                }
            }
        }

        countingManager.setChannel(event.getGuild().getIdLong(), channelId);

        String mention = "<#" + channelId + ">";
        reply(event, COLOR_SUCCESS, mention + " is now the counting channel. Start counting from **1**!");
    }

    private void handleCountingScore(MessageReceivedEvent event) {
        CountingManager.State state = countingManager.get(event.getGuild().getIdLong());
        if (state == null) {
            reply(event, COLOR_ERROR, "No counting channel set. Use `" + Config.getPrefix() + "setcounting` first.");
            return;
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Counting")
                .setColor(COLOR_INFO)
                .addField("Current count", String.valueOf(state.count), true)
                .addField("High score", String.valueOf(state.highScore), true);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleCountingMessage(MessageReceivedEvent event, CountingManager.State state) {
        String content = event.getMessage().getContentRaw().trim();
        Long number = countingManager.tryEvaluate(content);

        if (number == null) {
            event.getMessage().delete().queue();
            return;
        }

        long expected = state.count + 1;

        if (event.getAuthor().getIdLong() == state.lastUserId) {
            state.count = 0;
            state.lastUserId = 0L;
            event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❌")).queue();
            event.getChannel().sendMessage(
                    event.getAuthor().getAsMention() + " ruined it! You can't count twice in a row. Start again from **1**."
            ).queue();
            return;
        }

        if (number == expected) {
            state.count = expected;
            state.lastUserId = event.getAuthor().getIdLong();
            if (expected > state.highScore) {
                state.highScore = expected;
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⭐")).queue();
            } else {
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue();
            }
        } else {
            state.count = 0;
            state.lastUserId = 0L;
            event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❌")).queue();
            event.getChannel().sendMessage(
                    event.getAuthor().getAsMention() + " ruined it! The next number was **" + expected + "**. Start again from **1**."
            ).queue();
        }
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void handleHelp(MessageReceivedEvent event) {
        String p = Config.getPrefix();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Music Bot Commands")
                .setDescription("Prefix: `" + p + "`")
                .setColor(COLOR_SUCCESS)
                .addField("── Music ──", "\u200b", false)
                .addField("`" + p + "play <query>`",   "Play a song or YouTube URL — URL or search term", false)
                .addField("`" + p + "pause`",           "Pause the current song", false)
                .addField("`" + p + "resume`",          "Resume a paused song", false)
                .addField("`" + p + "stop`",            "Stop and clear the entire queue", false)
                .addField("`" + p + "skip`",            "Skip to the next song", false)
                .addField("`" + p + "queue`",           "Show the current queue", false)
                .addField("`" + p + "leave`",           "Disconnect from the voice channel", false)
                .addField("── Counting ──", "\u200b", false)
                .addField("`" + p + "setcounting [#channel]`", "Set the counting channel (requires Manage Channels)", false)
                .addField("`" + p + "countingscore`",   "Show the current count and high score", false)
                .addField("── Misc ──", "\u200b", false)
                .addField("`" + p + "penis`",           "Very important command", false)
                .addField("`" + p + "clearchat [amount]`", "Delete messages (default: 10, max: 100, requires Manage Messages)", false)
                .addField("`" + p + "commands`",        "Show this help message", false)
                .setFooter("MirkoBot • Music for your server");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void reply(MessageReceivedEvent event, Color color, String message) {
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
