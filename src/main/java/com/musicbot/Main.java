package com.musicbot;

import com.musicbot.music.MusicManager;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Load .env file; fall back to real environment variables if file is absent
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String token = dotenv.get("DISCORD_TOKEN", System.getenv("DISCORD_TOKEN") != null
                ? System.getenv("DISCORD_TOKEN") : "");

        if (token == null || token.isBlank()) {
            System.err.println("ERROR: DISCORD_TOKEN not found in .env file!");
            System.err.println("Please create a .env file with: DISCORD_TOKEN=your_token_here");
            System.exit(1);
        }

        String prefix = dotenv.get("PREFIX", "!");
        Config.initialize(prefix);

        MusicManager musicManager = new MusicManager();

        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_VOICE_STATES
                    )
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.listening("music requests"))
                    .addEventListeners(new CommandListener(musicManager))
                    .build();

            jda.awaitReady();

            logger.info("Bot logged in as {}", jda.getSelfUser().getName());
            System.out.println("\n=== Music Bot Ready ===");
            System.out.println("Logged in as: " + jda.getSelfUser().getName());
            System.out.println("Command Prefix: " + Config.getPrefix());
            System.out.println("Use '" + Config.getPrefix() + "commands' to see all commands\n");

        } catch (net.dv8tion.jda.api.exceptions.InvalidTokenException e) {
            logger.error("Invalid Discord token provided!");
            System.exit(1);
        } catch (Exception e) {
            logger.error("Failed to start bot: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
