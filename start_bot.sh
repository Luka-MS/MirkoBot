#!/bin/bash

echo "========================================"
echo "    Discord Music Bot Launcher"
echo "========================================"
echo ""

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is not installed"
    echo "Please install Python 3.8+ from https://www.python.org/downloads/"
    exit 1
fi

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "Error: .env file not found!"
    echo ""
    echo "Please create a .env file in this directory with the following content:"
    echo ""
    echo "DISCORD_TOKEN=your_bot_token_here"
    echo "PREFIX=!"
    echo ""
    exit 1
fi

# Check if requirements are installed
echo "Checking dependencies..."
if ! pip3 show discord.py &> /dev/null; then
    echo "Installing required packages..."
    pip3 install -r requirements.txt
    if [ $? -ne 0 ]; then
        echo "Failed to install requirements"
        exit 1
    fi
fi

echo ""
echo "Starting Music Bot..."
echo "Press Ctrl+C to stop the bot"
echo ""

python3 main.py
