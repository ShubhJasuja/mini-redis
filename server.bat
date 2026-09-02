@echo off
REM Compiles and starts the MiniRedis server. Double-click or run: server.bat
cd /d "%~dp0"
call "%~dp0env.bat" || exit /b 1
echo [build] javac *.java
javac *.java || exit /b 1
echo [build] ok - starting server (Ctrl-C to stop)
echo.
java MiniRedis
