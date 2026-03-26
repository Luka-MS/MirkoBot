import discord
from discord.ext import commands
import yt_dlp
import asyncio
import os
import shutil
import datetime
import random
import ast
import operator
from dotenv import load_dotenv
import logging
import sys

# Load environment variables
load_dotenv()
TOKEN = os.getenv('DISCORD_TOKEN')
PREFIX = os.getenv('PREFIX', '!')

# Validate token at startup
if not TOKEN:
    print("ERROR: DISCORD_TOKEN not found in .env file!")
    print("Please create a .env file with: DISCORD_TOKEN=your_token_here")
    sys.exit(1)

# Setup logging with better format
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Configure intents - Only use intents available in Discord
intents = discord.Intents.default()
intents.message_content = True  # Required for prefix command detection
# Note: Voice requires GUILD_VOICE_STATES which is included in default()

# Create bot
bot = commands.Bot(command_prefix=PREFIX, intents=intents)

# yt-dlp options for downloading audio
YDL_OPTIONS = {
    'format': 'bestaudio/best',
    'noplaylist': True,
    'default_search': 'ytsearch',
    'quiet': False,
    'no_warnings': False,
}

FFMPEG_OPTIONS = {
    'before_options': '-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5',
    'options': '-vn -af loudnorm=I=-16:TP=-1.5:LRA=11',
}

# Auto-detect FFmpeg path
FFMPEG_PATH = shutil.which('ffmpeg')
if not FFMPEG_PATH:
    # Check common Windows install locations
    common_paths = [
        os.path.join(os.environ.get('LOCALAPPDATA', ''), 'Microsoft', 'WinGet', 'Packages'),
        r'C:\ffmpeg\bin\ffmpeg.exe',
        r'C:\Program Files\ffmpeg\bin\ffmpeg.exe',
        r'C:\ProgramData\chocolatey\bin\ffmpeg.exe',
    ]
    for path in common_paths:
        if os.path.isdir(path):
            for root, dirs, files in os.walk(path):
                if 'ffmpeg.exe' in files:
                    FFMPEG_PATH = os.path.join(root, 'ffmpeg.exe')
                    break
        elif os.path.isfile(path):
            FFMPEG_PATH = path
            break

if FFMPEG_PATH:
    logger.info(f"FFmpeg found: {FFMPEG_PATH}")
else:
    logger.warning("FFmpeg NOT found! Music playback will not work.")
    logger.warning("Install FFmpeg: winget install FFmpeg")
    FFMPEG_PATH = 'ffmpeg'  # fallback, hope it's in PATH

class Music(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.queue = []           # list of (url, title, thumbnail, duration)
        self.current_song = None  # (url, title, thumbnail, duration)
        self.is_playing = False
        self.voice_client = None
        self.current_ctx = None

    @staticmethod
    def format_duration(seconds):
        if not seconds:
            return "Live"
        m, s = divmod(int(seconds), 60)
        h, m = divmod(m, 60)
        return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"

    async def get_audio_url(self, search_query):
        """Extract audio info from video using yt-dlp"""
        try:
            with yt_dlp.YoutubeDL(YDL_OPTIONS) as ydl:
                info = ydl.extract_info(search_query, download=False)
                if info:
                    return (
                        info['url'],
                        info.get('title', 'Unknown'),
                        info.get('thumbnail'),
                        info.get('duration'),
                    )
        except Exception as e:
            logger.error(f"Error extracting audio: {e}")
        return None, None, None, None

    async def get_playlist_entries(self, url):
        """Extract all video entries from a YouTube playlist using flat extraction."""
        opts = {
            'extract_flat': 'in_playlist',
            'quiet': True,
            'no_warnings': True,
            'ignoreerrors': True,
        }
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
        except Exception as e:
            logger.error(f"Playlist extraction error: {e}")
            return [], 'Unknown Playlist'

        entries = []
        for entry in (info.get('entries') or []):
            if not entry or not entry.get('id'):
                continue
            video_url = f"https://www.youtube.com/watch?v={entry['id']}"
            entries.append((
                video_url,
                entry.get('title', 'Unknown'),
                entry.get('thumbnail'),
                entry.get('duration'),
            ))
        return entries, info.get('title', 'Unknown Playlist')

    async def set_presence_playing(self, title):
        await self.bot.change_presence(activity=discord.Activity(
            type=discord.ActivityType.listening,
            name=title,
            start=datetime.datetime.now(datetime.timezone.utc)
        ))

    async def set_presence_idle(self):
        await self.bot.change_presence(activity=discord.Activity(
            type=discord.ActivityType.listening,
            name="music requests"
        ))

    def play_next(self, error):
        """Callback to play next song in queue"""
        if error:
            logger.error(f"Playback error: {error}")

        if self.queue:
            self.current_song = self.queue.pop(0)
            asyncio.run_coroutine_threadsafe(
                self.play_song(self.current_ctx, *self.current_song),
                self.bot.loop
            )
        else:
            self.is_playing = False
            self.current_song = None
            asyncio.run_coroutine_threadsafe(
                self.set_presence_idle(),
                self.bot.loop
            )

    async def play_song(self, ctx, url, title, thumbnail, duration):
        """Play a song using ffmpeg"""
        try:
            # Playlist entries store YouTube watch URLs that must be resolved to a stream URL first
            if 'youtube.com/watch' in url or 'youtu.be/' in url:
                resolved_url, resolved_title, resolved_thumbnail, resolved_duration = await self.get_audio_url(url)
                if not resolved_url:
                    embed = discord.Embed(description=f"Could not load **{title}**. Skipping...", color=0xFFA500)
                    await ctx.send(embed=embed)
                    self.play_next(None)
                    return
                url = resolved_url
                if resolved_title:
                    title = resolved_title
                if resolved_thumbnail:
                    thumbnail = resolved_thumbnail
                if resolved_duration:
                    duration = resolved_duration

            self.is_playing = True
            self.current_song = (url, title, thumbnail, duration)

            audio_source = discord.FFmpegOpusAudio(url, executable=FFMPEG_PATH, **FFMPEG_OPTIONS)
            self.voice_client.play(audio_source, after=self.play_next)

            await self.set_presence_playing(title)

            embed = discord.Embed(title="Now Playing", description=f"**{title}**", color=0x1DB954)
            if thumbnail:
                embed.set_thumbnail(url=thumbnail)
            if duration:
                embed.add_field(name="Duration", value=self.format_duration(duration), inline=True)
            if self.queue:
                embed.add_field(name="Up Next", value=self.queue[0][1], inline=True)
            embed.set_footer(
                text=f"Requested by {ctx.author.display_name}",
                icon_url=ctx.author.display_avatar.url
            )
            await ctx.send(embed=embed)
            logger.info(f"Playing: {title}")
        except Exception as e:
            logger.error(f"Error playing song: {e}")
            embed = discord.Embed(title="Playback Error", description=str(e)[:200], color=0xFF4444)
            await ctx.send(embed=embed)
            self.play_next(None)

    @commands.command(name='play', help='Play a song or YouTube playlist (URL or search term)')
    async def play(self, ctx, *, query):
        if not ctx.author.voice:
            embed = discord.Embed(description="You need to be in a voice channel first!", color=0xFF4444)
            await ctx.send(embed=embed)
            return

        async with ctx.typing():
            if ctx.guild.voice_client:
                self.voice_client = ctx.guild.voice_client
                if self.voice_client.channel != ctx.author.voice.channel:
                    try:
                        await self.voice_client.move_to(ctx.author.voice.channel)
                    except Exception as e:
                        embed = discord.Embed(description=f"Could not move to your channel: {e}", color=0xFF4444)
                        await ctx.send(embed=embed)
                        return
            else:
                try:
                    self.voice_client = await ctx.author.voice.channel.connect(timeout=15.0, self_deaf=True)
                except Exception as e:
                    logger.error(f"Voice connect error: {e}")
                    embed = discord.Embed(description=f"Could not connect to voice: {e}", color=0xFF4444)
                    await ctx.send(embed=embed)
                    self.voice_client = None
                    return

            self.current_ctx = ctx

            # Detect YouTube playlist URLs (e.g. ?list=... or /playlist?list=...)
            is_playlist = 'list=' in query and ('youtube.com' in query or 'youtu.be' in query)

            if is_playlist:
                status_embed = discord.Embed(description="Loading playlist...", color=0x5865F2)
                status_msg = await ctx.send(embed=status_embed)

                entries, playlist_title = await self.get_playlist_entries(query)

                if not entries:
                    embed = discord.Embed(description="Could not load that playlist. Make sure it is public.", color=0xFF4444)
                    await status_msg.edit(embed=embed)
                    return

                if not self.is_playing:
                    first = entries[0]
                    for entry in entries[1:]:
                        self.queue.append(entry)
                    embed = discord.Embed(
                        title="Playlist Loaded",
                        description=f"**{playlist_title}**",
                        color=0x1DB954
                    )
                    embed.add_field(name="Songs", value=str(len(entries)), inline=True)
                    if len(entries) > 1:
                        embed.add_field(name="Queued", value=str(len(entries) - 1), inline=True)
                    await status_msg.edit(embed=embed)
                    await self.play_song(ctx, *first)
                else:
                    for entry in entries:
                        self.queue.append(entry)
                    embed = discord.Embed(
                        title="Playlist Added to Queue",
                        description=f"**{playlist_title}**",
                        color=0x5865F2
                    )
                    embed.add_field(name="Songs Added", value=str(len(entries)), inline=True)
                    embed.add_field(name="Queue Positions", value=f"#{len(self.queue) - len(entries) + 1}–#{len(self.queue)}", inline=True)
                    await status_msg.edit(embed=embed)
            else:
                embed = discord.Embed(description=f"Searching for **{query}**...", color=0x5865F2)
                await ctx.send(embed=embed)

                url, title, thumbnail, duration = await self.get_audio_url(query)

                if not url:
                    embed = discord.Embed(description="Could not find that song. Try a different search term.", color=0xFF4444)
                    await ctx.send(embed=embed)
                    return

                if not self.is_playing:
                    await self.play_song(ctx, url, title, thumbnail, duration)
                else:
                    self.queue.append((url, title, thumbnail, duration))
                    embed = discord.Embed(title="Added to Queue", description=f"**{title}**", color=0x5865F2)
                    if thumbnail:
                        embed.set_thumbnail(url=thumbnail)
                    if duration:
                        embed.add_field(name="Duration", value=self.format_duration(duration), inline=True)
                    embed.add_field(name="Position", value=f"#{len(self.queue)}", inline=True)
                    embed.set_footer(
                        text=f"Requested by {ctx.author.display_name}",
                        icon_url=ctx.author.display_avatar.url
                    )
                    await ctx.send(embed=embed)

    @commands.command(name='pause', help='Pause the music')
    async def pause(self, ctx):
        if self.voice_client and self.voice_client.is_playing():
            self.voice_client.pause()
            title = self.current_song[1] if self.current_song else "music"
            await self.bot.change_presence(activity=discord.Activity(
                type=discord.ActivityType.listening,
                name=f"{title} (paused)"
            ))
            embed = discord.Embed(description="Paused the music.", color=0xFFA500)
            await ctx.send(embed=embed)
        else:
            embed = discord.Embed(description="Nothing is playing right now.", color=0xFF4444)
            await ctx.send(embed=embed)

    @commands.command(name='resume', help='Resume the music')
    async def resume(self, ctx):
        if self.voice_client and self.voice_client.is_paused():
            self.voice_client.resume()
            if self.current_song:
                await self.set_presence_playing(self.current_song[1])
            embed = discord.Embed(description="Resumed the music.", color=0x1DB954)
            await ctx.send(embed=embed)
        else:
            embed = discord.Embed(description="Nothing is paused right now.", color=0xFF4444)
            await ctx.send(embed=embed)

    @commands.command(name='stop', help='Stop music and clear the queue')
    async def stop(self, ctx):
        if self.voice_client and (self.voice_client.is_playing() or self.voice_client.is_paused()):
            self.voice_client.stop()
            self.queue.clear()
            self.is_playing = False
            self.current_song = None
            await self.set_presence_idle()
            embed = discord.Embed(description="Stopped the music and cleared the queue.", color=0xFF4444)
            await ctx.send(embed=embed)
        else:
            embed = discord.Embed(description="Nothing is playing right now.", color=0xFF4444)
            await ctx.send(embed=embed)

    @commands.command(name='skip', help='Skip to the next song')
    async def skip(self, ctx):
        if self.is_playing and self.voice_client:
            title = self.current_song[1] if self.current_song else "current song"
            self.voice_client.stop()
            embed = discord.Embed(description=f"Skipped **{title}**.", color=0x5865F2)
            await ctx.send(embed=embed)
        else:
            embed = discord.Embed(description="Nothing is playing right now.", color=0xFF4444)
            await ctx.send(embed=embed)

    @commands.command(name='queue', help='Show the current queue')
    async def show_queue(self, ctx):
        if not self.queue and not self.is_playing:
            embed = discord.Embed(description="The queue is empty.", color=0x5865F2)
            await ctx.send(embed=embed)
            return

        embed = discord.Embed(title="Queue", color=0x5865F2)

        if self.current_song:
            dur = f" `{self.format_duration(self.current_song[3])}`" if self.current_song[3] else ""
            embed.add_field(name="Now Playing", value=f"{self.current_song[1]}{dur}", inline=False)

        if self.queue:
            lines = []
            for i, song in enumerate(self.queue[:10]):
                dur = f" `{self.format_duration(song[3])}`" if song[3] else ""
                lines.append(f"`{i+1}.` {song[1]}{dur}")
            embed.add_field(name="Up Next", value="\n".join(lines), inline=False)
            if len(self.queue) > 10:
                embed.set_footer(text=f"... and {len(self.queue) - 10} more songs")

        await ctx.send(embed=embed)

    @commands.command(name='penis')
    async def penis(self, ctx):
        length = random.randint(1, 20)
        shaft = '=' * length
        await ctx.send(f"{ctx.author.display_name}: 8{shaft}>")

    @commands.command(name='leave', help='Disconnect from voice channel')
    async def leave(self, ctx):
        if self.voice_client and self.voice_client.is_connected():
            await self.voice_client.disconnect()
            self.queue.clear()
            self.is_playing = False
            self.current_song = None
            await self.set_presence_idle()
            embed = discord.Embed(description="Disconnected and cleared the queue.", color=0x5865F2)
            await ctx.send(embed=embed)
        else:
            embed = discord.Embed(description="I'm not in a voice channel.", color=0xFF4444)
            await ctx.send(embed=embed)

    @commands.command(name='clearchat', aliases=['clear', 'purge'], help='Delete messages from the chat (default: 10)')
    @commands.has_permissions(manage_messages=True)
    async def clearchat(self, ctx, amount: int = 10):
        if amount < 1 or amount > 100:
            embed = discord.Embed(description="Please specify a number between 1 and 100.", color=0xFF4444)
            await ctx.send(embed=embed, delete_after=5)
            return
        deleted = await ctx.channel.purge(limit=amount + 1)  # +1 to include the command message
        embed = discord.Embed(
            description=f"Deleted **{len(deleted) - 1}** message(s).",
            color=0x5865F2
        )
        await ctx.send(embed=embed, delete_after=5)

    @clearchat.error
    async def clearchat_error(self, ctx, error):
        if isinstance(error, commands.MissingPermissions):
            embed = discord.Embed(description="You need the **Manage Messages** permission to use this command.", color=0xFF4444)
            await ctx.send(embed=embed, delete_after=5)
        elif isinstance(error, commands.BadArgument):
            embed = discord.Embed(description="Please provide a valid number. Example: `!clearchat 10`", color=0xFF4444)
            await ctx.send(embed=embed, delete_after=5)

    @commands.command(name='commands', help='Show all available commands')
    async def help_command(self, ctx):
        embed = discord.Embed(
            title="Music Bot Commands",
            description=f"Prefix: `{PREFIX}`",
            color=0x1DB954
        )
        commands_list = [
            ("── Music ──",        ""),
            ("play <query>",       "Play a song or YouTube playlist — URL or search term"),
            ("pause",              "Pause the current song"),
            ("resume",             "Resume a paused song"),
            ("stop",               "Stop and clear the entire queue"),
            ("skip",               "Skip to the next song"),
            ("queue",              "Show the current queue"),
            ("leave",              "Disconnect from the voice channel"),
            ("── Counting ──",     ""),
            ("setcounting [#channel]", "Set the counting channel (requires Manage Channels)"),
            ("countingscore",      "Show the current count and high score"),
            ("── Misc ──",         ""),
            ("penis",              "Very important command"),
            ("clearchat [amount]", "Delete messages (default: 10, max: 100, requires Manage Messages)"),
            ("commands",           "Show this help message"),
        ]
        for cmd, desc in commands_list:
            if desc == "":
                embed.add_field(name=f"**{cmd}**", value="\u200b", inline=False)
            else:
                embed.add_field(name=f"`{PREFIX}{cmd}`", value=desc, inline=False)
        embed.set_footer(text="MirkoBot • Music for your server")
        await ctx.send(embed=embed)


class Counting(commands.Cog):
    # Safe operators allowed in counting expressions
    _OPERATORS = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.Div: operator.truediv,
        ast.FloorDiv: operator.floordiv,
        ast.Mod: operator.mod,
        ast.Pow: operator.pow,
        ast.USub: operator.neg,
        ast.UAdd: operator.pos,
    }

    def _safe_eval(self, node):
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value
        if isinstance(node, ast.BinOp) and type(node.op) in self._OPERATORS:
            left = self._safe_eval(node.left)
            right = self._safe_eval(node.right)
            # Guard against huge exponents
            if isinstance(node.op, ast.Pow) and abs(right) > 100:
                raise ValueError("Exponent too large")
            return self._OPERATORS[type(node.op)](left, right)
        if isinstance(node, ast.UnaryOp) and type(node.op) in self._OPERATORS:
            return self._OPERATORS[type(node.op)](self._safe_eval(node.operand))
        raise ValueError("Unsupported expression")

    def evaluate(self, expr):
        """Safely evaluate a math expression and return an int, or raise ValueError."""
        tree = ast.parse(expr, mode='eval')
        result = self._safe_eval(tree.body)
        if not isinstance(result, (int, float)):
            raise ValueError("Not a number")
        if result != int(result):
            raise ValueError("Result is not an integer")
        return int(result)

    def __init__(self, bot):
        self.bot = bot
        # guild_id -> { channel_id, count, last_user_id, high_score }
        self.data = {}

    def get(self, guild_id):
        return self.data.get(guild_id)

    @commands.command(name='setcounting', help='Set the counting channel')
    @commands.has_permissions(manage_channels=True)
    async def setcounting(self, ctx, channel: discord.TextChannel = None):
        channel = channel or ctx.channel
        self.data[ctx.guild.id] = {
            'channel_id': channel.id,
            'count': 0,
            'last_user_id': None,
            'high_score': 0,
        }
        embed = discord.Embed(
            description=f"{channel.mention} is now the counting channel. Start counting from **1**!",
            color=0x1DB954
        )
        await ctx.send(embed=embed)

    @commands.command(name='countingscore', help='Show the current count and high score')
    async def countingscore(self, ctx):
        state = self.get(ctx.guild.id)
        if not state:
            embed = discord.Embed(description="No counting channel set. Use `!setcounting` first.", color=0xFF4444)
            await ctx.send(embed=embed)
            return
        embed = discord.Embed(title="Counting", color=0x5865F2)
        embed.add_field(name="Current count", value=str(state['count']), inline=True)
        embed.add_field(name="High score", value=str(state['high_score']), inline=True)
        await ctx.send(embed=embed)

    @commands.Cog.listener()
    async def on_message(self, message):
        if message.author.bot:
            return
        state = self.get(message.guild.id if message.guild else None)
        if not state or message.channel.id != state['channel_id']:
            return
        # Ignore bot commands
        if message.content.startswith(PREFIX):
            return

        expected = state['count'] + 1
        content = message.content.strip()

        try:
            number = self.evaluate(content)
        except Exception:
            await message.delete()
            return

        if message.author.id == state['last_user_id']:
            state['count'] = 0
            state['last_user_id'] = None
            await message.add_reaction('❌')
            await message.channel.send(
                f"{message.author.mention} ruined it at **{number - 1 if number > 1 else number}**! "
                f"You can't count twice in a row. Start again from **1**."
            )
            return

        if number == expected:
            state['count'] = expected
            state['last_user_id'] = message.author.id
            if expected > state['high_score']:
                state['high_score'] = expected
                await message.add_reaction('⭐')
            else:
                await message.add_reaction('✅')
        else:
            state['count'] = 0
            state['last_user_id'] = None
            await message.add_reaction('❌')
            await message.channel.send(
                f"{message.author.mention} ruined it! The next number was **{expected}**. "
                f"Start again from **1**."
            )

async def setup():
    await bot.add_cog(Music(bot))
    await bot.add_cog(Counting(bot))

@bot.event
async def on_ready():
    logger.info(f"Bot logged in as {bot.user}")
    logger.info(f"Prefix: {PREFIX}")
    await bot.change_presence(activity=discord.Activity(type=discord.ActivityType.listening, name="music requests"))
    print(f"\n=== Music Bot Ready ===")
    print(f"Logged in as: {bot.user}")
    print(f"Command Prefix: {PREFIX}")
    print(f"Use '{PREFIX}help' to see all commands\n")

async def main():
    try:
        await setup()
        async with bot:
            await bot.start(TOKEN)
    except discord.LoginFailure:
        logger.error("Invalid Discord token provided!")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nBot shutdown by user.")
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        sys.exit(1)
