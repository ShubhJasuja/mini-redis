@echo off
REM Compiles and starts the interactive MiniRedis client. Run: client.bat
REM Optional args are passed straight through:
REM     client.bat            -> connect to 127.0.0.1:6379
REM     client.bat 6380       -> another port on this machine
REM     client.bat host 6379  -> another machine
cd /d "%~dp0"
call "%~dp0env.bat" || exit /b 1
javac -d . *.java || exit /b 1
java MiniRedisClient %*
