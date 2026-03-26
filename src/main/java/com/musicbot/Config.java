package com.musicbot;

public class Config {

    private static String prefix = "!";

    public static void initialize(String prefix) {
        Config.prefix = prefix;
    }

    public static String getPrefix() {
        return prefix;
    }
}
