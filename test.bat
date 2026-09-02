@echo off
REM Compiles the whole project (production + tests) and runs every test suite
REM in a single JVM. Run:  test.bat
REM Exit code is 0 only if every assertion in every suite passed.
cd /d "%~dp0"
call "%~dp0env.bat" || exit /b 1

echo [build] javac production + test sources
javac -d . *.java test\*.java || exit /b 1
echo.

java RunAllTests
if errorlevel 1 (
    echo.
    echo [test] TESTS FAILED
    exit /b 1
)
exit /b 0
