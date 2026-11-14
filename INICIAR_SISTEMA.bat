@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out (
    call COMPILAR.bat
    if %errorlevel% neq 0 exit /b 1
)
set "JAVA_CMD=java"
where java >nul 2>nul || (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    ) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe" (
        set "JAVA_CMD=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe"
    )
)
echo Iniciando QuindioStay en Java 21 (http://localhost:8080)...
start "" http://localhost:8080
"%JAVA_CMD%" -cp "out;lib\*" co.edu.uniquindio.quindiostay.Main
pause
