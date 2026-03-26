# Bot Configuration File
import os
from dotenv import load_dotenv

load_dotenv()

# Bot Settings
DISCORD_TOKEN = os.getenv('DISCORD_TOKEN', '')
PREFIX = os.getenv('PREFIX', '!')
BOT_NAME = 'Music Bot'

# Audio Quality Settings
YDL_OPTIONS = {
    'format': 'bestaudio/best',
    'noplaylist': True,
    'default_search': 'ytsearch',
    'quiet': False,
    'no_warnings': False,
    'socket_timeout': 30,
}

# FFmpeg Audio Options
FFMPEG_OPTIONS = {
    'before_options': '-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5',
    'options': '-vn -af loudnorm=I=-16:TP=-1.5:LRA=11',
}

# Queue Settings
MAX_QUEUE_SIZE = 100  # Maximum songs in queue
SEARCH_RESULTS = 1    # Number of search results to consider

# Timeout Settings (in seconds)
CONNECT_TIMEOUT = 10
PLAY_TIMEOUT = 3600  # Auto disconnect after 1 hour of inactivity

# Logging
LOG_LEVEL = 'INFO'
LOG_FILE = 'bot.log'

# Embed Colors (Discord Color Codes)
COLOR_SUCCESS = 0x00ff00
COLOR_ERROR = 0xff0000
COLOR_INFO = 0x0000ff
COLOR_QUEUE = 0x9400d3
