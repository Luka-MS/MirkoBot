@echo off
title Discord Music Bot
echo ========================================
echo    Discord Music Bot Launcher
echo ========================================
echo.

REM Use venv Python if available, otherwise fall back to system python
set PYTHON_EXE=%~dp0.venv\Scripts\python.exe
if not exist "%PYTHON_EXE%" set PYTHON_EXE=python

REM Check if Python is installed
"%PYTHON_EXE%" --version >nul 2>&1
if errorlevel 1 (
    echo.
    echo ERROR: Python is not installed or not in PATH
    echo.
    echo SOLUTION:
    echo 1. Download Python 3.8+ from https://www.python.org/downloads/
    echo 2. During installation, CHECK "Add Python to PATH"
    echo 3. Restart your computer
    echo 4. Run this script again
    echo.
    pause
    exit /b 1
)

echo Python found:
"%PYTHON_EXE%" --version
echo.

REM Check if .env file exists
if not exist ".env" (
    echo ERROR: .env file not found!
    echo.
    echo Please create a .env file in this directory with:
    echo.
    echo DISCORD_TOKEN=your_bot_token_here
    echo PREFIX=!
    echo.
    pause
    exit /b 1
)

REM Check if requirements are installed
echo Checking dependencies...
"%PYTHON_EXE%" -m pip show discord.py >nul 2>&1
if errorlevel 1 (
    echo Installing required packages...
    echo This may take a minute...
    echo.
    "%PYTHON_EXE%" -m pip install --upgrade pip
    "%PYTHON_EXE%" -m pip install -r requirements.txt
    if errorlevel 1 (
        echo.
        echo ERROR: Failed to install requirements!
        echo.
        echo SOLUTION:
        echo Try running this command manually:
        echo python -m pip install -r requirements.txt
        echo.
        pause
        exit /b 1
    )
)

echo Dependencies OK!
echo.
echo Starting Music Bot...
echo Press Ctrl+C to stop the bot
echo.
"%PYTHON_EXE%" main.py
pause
