@echo off
REM Compiles and starts the interactive MiniRedis client. Run: client.bat
REM Optional args are passed straight through:
REM     client.bat            -> connect to 127.0.0.1:6379
REM     client.bat 6380       -> another port on this machine
REM     client.bat host 6379  -> another machine
cd /d "%~dp0"
call "%~dp0env.bat" || exit /b 1

if not exist target\classes mkdir target\classes
dir /s /b src\main\java\*.java > "%TEMP%\miniredis_main_src.txt"
javac -d target\classes @"%TEMP%\miniredis_main_src.txt" || (del "%TEMP%\miniredis_main_src.txt" & exit /b 1)
del "%TEMP%\miniredis_main_src.txt"

java -cp target\classes com.miniredis.MiniRedisClient %*
