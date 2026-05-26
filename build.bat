@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
::  CiteRight — Portable Build Script
::  Produces a self-contained folder that runs on ANY Windows PC
::  without requiring Java, JavaFX, or any other installation.
::
::  Output:  target\CiteRight-portable\CiteRight\CiteRight.exe
::           CiteRight-Portable.zip
::
::  Requirements (on YOUR build machine only, NOT on the target PC):
::    - Java 21 JDK
::    - Maven (mvn on PATH)
:: ============================================================================

echo.
echo  =====================================================
echo   CiteRight ^| Portable Build
echo  =====================================================
echo.

:: ── 1. Verify Prerequisites ─────────────────────────────────────────────────

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] java not found on PATH. Please install Java 21 JDK.
    pause & exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] mvn not found on PATH. Please install Maven.
    pause & exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
    echo [ERROR] jpackage not found on PATH. Ensure you have a full JDK 21 (not just JRE).
    pause & exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%v
)
echo [INFO] Using Java: !JAVA_VER!

:: ── 2. Clean + Build Fat JAR ─────────────────────────────────────────────────

echo.
echo [STEP 1/3] Building fat JAR with Maven...
echo.

call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed. Check the output above for errors.
    pause & exit /b 1
)

set STANDALONE_JAR=target\citeright-1.0-SNAPSHOT-standalone.jar
if not exist "%STANDALONE_JAR%" (
    echo [ERROR] Expected JAR not found: %STANDALONE_JAR%
    pause & exit /b 1
)
echo [OK] Fat JAR built: %STANDALONE_JAR%

:: ── 3. Run jpackage to create Self-Contained App ─────────────────────────────

echo.
echo [STEP 2/3] Creating portable app with bundled JRE (this takes 1-2 minutes)...
echo.

:: Remove old output if it exists
if exist "target\CiteRight-portable" (
    rmdir /s /q "target\CiteRight-portable"
)

:: Create clean input directory containing only the standalone JAR to avoid recursive packaging loops
if exist "target\jpackage-input" rmdir /s /q "target\jpackage-input"
mkdir "target\jpackage-input"
copy "%STANDALONE_JAR%" "target\jpackage-input\" >nul

jpackage ^
    --type app-image ^
    --name CiteRight ^
    --app-version 1.0.0 ^
    --vendor "CiteRight" ^
    --description "Smart Citation Manager" ^
    --input target\jpackage-input ^
    --main-jar citeright-1.0-SNAPSHOT-standalone.jar ^
    --main-class com.citeright.Launcher ^
    --dest target\CiteRight-portable ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Xmx512m" ^
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" ^
    --icon src\main\resources\icon.ico ^
    --add-modules java.base,java.desktop,java.logging,java.sql,java.net.http,java.xml,java.naming,jdk.crypto.ec,jdk.unsupported

if errorlevel 1 (
    echo [ERROR] jpackage failed. See output above.
    pause & exit /b 1
)

echo [OK] Portable app created at: target\CiteRight-portable\CiteRight\

:: ── 4. Create Distributable ZIP ──────────────────────────────────────────────

echo.
echo [STEP 3/3] Creating ZIP archive...
echo.

set ZIP_NAME=CiteRight-Portable.zip

:: Remove old zip
if exist "%ZIP_NAME%" del /f /q "%ZIP_NAME%"

:: Use PowerShell to create zip (available on all Windows 8+)
powershell -NoProfile -Command ^
    "Compress-Archive -Path 'target\CiteRight-portable\CiteRight' -DestinationPath '%ZIP_NAME%' -Force"

if errorlevel 1 (
    echo [WARN] Could not create ZIP. The portable folder is still usable at:
    echo        target\CiteRight-portable\CiteRight\
) else (
    echo [OK] ZIP created: %ZIP_NAME%
)

:: ── Done ─────────────────────────────────────────────────────────────────────

echo.
echo  =====================================================
echo   BUILD COMPLETE
echo  =====================================================
echo.
echo  Portable folder : target\CiteRight-portable\CiteRight\CiteRight.exe
if exist "%ZIP_NAME%" (
echo  Distributable   : %ZIP_NAME%
)
echo.
echo  To run the app:  target\CiteRight-portable\CiteRight\CiteRight.exe
echo  To distribute:   Copy CiteRight-Portable.zip to any Windows PC and unzip.
echo                   Double-click CiteRight.exe — no Java install needed!
echo.

pause
