@echo off
set JAVA_HOME=C:\Users\LukaS\.jdk\jdk-21.0.8
set MVN=C:\Users\LukaS\.maven\maven-3.9.14\bin\mvn.cmd

echo === Building MirkoBot ===
call "%MVN%" clean package -q
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. Fix the errors above and try again.
    pause
    exit /b 1
)

echo === Build successful - Starting bot ===
echo.
"%JAVA_HOME%\bin\java.exe" -jar "target\discord-music-bot-1.0.0.jar"
