@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Compilando QuindioStay (Java 21 + JDBC + OpenPDF)...
if not exist out mkdir out
set "JAVAC_CMD=javac"
where javac >nul 2>nul || (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
    ) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\javac.exe" (
        set "JAVAC_CMD=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\javac.exe"
    )
)
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib\*" -d out src\co\edu\uniquindio\quindiostay\Main.java src\co\edu\uniquindio\quindiostay\config\ConexionBD.java src\co\edu\uniquindio\quindiostay\controller\ApiController.java src\co\edu\uniquindio\quindiostay\dao\AuthDAO.java src\co\edu\uniquindio\quindiostay\dao\CrudDAO.java src\co\edu\uniquindio\quindiostay\dao\NegocioDAO.java src\co\edu\uniquindio\quindiostay\dao\ReporteDAO.java src\co\edu\uniquindio\quindiostay\pdf\GeneradorPDF.java src\co\edu\uniquindio\quindiostay\util\JsonUtil.java
if %errorlevel% neq 0 (
    echo [ERROR] Fallo la compilacion Java. Verifique JDK 21 en PATH o JAVA_HOME.
    if /i not "%~1"=="nopause" pause
    exit /b 1
)
echo [OK] Compilacion finalizada en carpeta .\out\
