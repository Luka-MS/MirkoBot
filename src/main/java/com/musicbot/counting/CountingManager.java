package com.musicbot.counting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the counting-game state for each guild.
 * All state is in memory only (resets on bot restart).
 */
public class CountingManager {

    private static final Logger logger = LoggerFactory.getLogger(CountingManager.class);

    public static class State {
        public long channelId;
        public long count = 0;
        public long lastUserId = 0L;
        public long highScore = 0;

        State(long channelId) {
            this.channelId = channelId;
        }
    }

    private final Map<Long, State> data = new ConcurrentHashMap<>();
    private final MathEvaluator evaluator = new MathEvaluator();

    /** Returns the state for a guild, or null if no counting channel is set. */
    public State get(long guildId) {
        return data.get(guildId);
    }

    /** Sets (or resets) the counting channel for a guild. */
    public void setChannel(long guildId, long channelId) {
        data.put(guildId, new State(channelId));
        logger.info("Counting channel set for guild {} -> channel {}", guildId, channelId);
    }

    /**
     * Tries to evaluate the given text as a math expression.
     *
     * @return the integer result, or {@code null} if parsing fails
     */
    public Long tryEvaluate(String text) {
        try {
            long result = evaluator.evaluate(text);
            logger.debug("Evaluated '{}' = {}", text, result);
            return result;
        } catch (Exception e) {
            logger.debug("Failed to evaluate '{}': {}", text, e.getMessage());
            return null;
        }
    }
}
