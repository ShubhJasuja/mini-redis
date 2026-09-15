@echo off
REM Compiles the whole project (production + tests) and runs every test suite
REM in a single JVM. Run:  test.bat
REM Exit code is 0 only if every assertion in every suite passed.
cd /d "%~dp0"
call "%~dp0env.bat" || exit /b 1

echo [build] compiling production sources...
if not exist target\classes mkdir target\classes
if not exist target\test-classes mkdir target\test-classes

dir /s /b src\main\java\*.java > "%TEMP%\miniredis_main_src.txt"
javac -d target\classes @"%TEMP%\miniredis_main_src.txt" || (del "%TEMP%\miniredis_main_src.txt" & exit /b 1)
del "%TEMP%\miniredis_main_src.txt"

echo [build] compiling test sources...
dir /s /b src\test\java\*.java | findstr /v Jupiter > "%TEMP%\miniredis_test_src.txt"
javac -d target\test-classes -cp target\classes @"%TEMP%\miniredis_test_src.txt" || (del "%TEMP%\miniredis_test_src.txt" & exit /b 1)
del "%TEMP%\miniredis_test_src.txt"
echo.

java -cp target\classes;target\test-classes com.miniredis.RunAllTests
if errorlevel 1 (
    echo.
    echo [test] TESTS FAILED
    exit /b 1
)
exit /b 0
