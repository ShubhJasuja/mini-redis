@echo off
REM Puts java/javac on the PATH for THIS Command Prompt window only.
REM Usage:  call env.bat
if exist "%USERPROFILE%\.jdks\temurin-17.0.17\bin\javac.exe" (
    set "JAVA_HOME=%USERPROFILE%\.jdks\temurin-17.0.17"
) else if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.2\jbr\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.2\jbr"
) else if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.4.1\jbr\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.4.1\jbr"
) else (
    echo [env] No JDK found. Install Temurin 17 or 21 and set JAVA_HOME.
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
exit /b 0
