# MirkoBot

A Discord music & fun bot built with Java, JDA, and LavaPlayer.

## Features

- Play music from YouTube (search or URL)
- Song queue with now-playing display
- Counting mini-game with math expression support
- Chat moderation (bulk delete)

## Setup

1. Create a `.env` file in the project root:
   ```
   DISCORD_TOKEN=your_bot_token
   PREFIX=!
   ```
2. Build with Maven:
   ```
   mvn clean package
   ```
3. Run the bot:
   ```
   java_start_bot.bat
   ```

## Commands

Default prefix: `!` (configurable via `.env`)

### Music

| Command | Description |
|---------|-------------|
| `!play <query or URL>` | Play a song — supports YouTube URLs and search terms |
| `!pause` | Pause the current song |
| `!resume` | Resume a paused song |
| `!stop` | Stop playback and clear the entire queue |
| `!skip` | Skip to the next song in the queue |
| `!playnow` / `!nowplaying` / `!np` | Show what's currently playing |
| `!queue` | Show the current song queue |
| `!leave` | Disconnect from the voice channel and clear the queue |

### Counting Game

| Command | Description |
|---------|-------------|
| `!setcounting [#channel]` | Set the counting channel (requires **Manage Channels**) |
| `!countingscore` | Show the current count and high score |

Once a counting channel is set, users take turns typing the next number. The same person can't count twice in a row. If someone sends the wrong number, the count resets to 0.

**Math expressions are supported!** Instead of typing a plain number you can type an expression that equals it:

| Operator / Function | Example |
|---------------------|---------|
| `+` `-` `*` `/` | `10+5` → 15 |
| `%` (modulo) | `17%10` → 7 |
| `**` or `^` (power) | `2^3` → 8 |
| `//` (floor division) | `7//2` → 3 |
| `()` (parentheses) | `(2+3)*4` → 20 |
| `sqrt()` | `sqrt(144)` → 12 |
| `log()` / `log10()` | `log(1000)` → 3 |
| `ln()` | `ln(1)` → 0 |
| `log2()` | `log2(64)` → 6 |

> Exponents are capped at 100 and results must be whole numbers.

### Misc

| Command | Description |
|---------|-------------|
| `!penis` | Very important command |
| `!clearchat [amount]` | Delete messages (default: 10, max: 100, requires **Manage Messages**) |
| `!commands` / `!help` | Show the help message |

## Required Bot Permissions

- Send Messages / Embed Links
- Connect / Speak (voice)
- Manage Messages (for `!clearchat` and counting channel cleanup)
- Add Reactions (for counting game responses)
