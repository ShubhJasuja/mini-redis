@echo off
REM Compiles and starts the MiniRedis server. Double-click or run: server.bat
cd /d "%~dp0"
call "%~dp0env.bat" || exit /b 1

if not exist target\classes mkdir target\classes
echo [build] compiling MiniRedis...
dir /s /b src\main\java\*.java > "%TEMP%\miniredis_main_src.txt"
javac -d target\classes @"%TEMP%\miniredis_main_src.txt" || (del "%TEMP%\miniredis_main_src.txt" & exit /b 1)
del "%TEMP%\miniredis_main_src.txt"

echo [build] ok - starting server (Ctrl-C to stop)
echo.
java -cp target\classes com.miniredis.MiniRedis %*
