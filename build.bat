@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
::  CiteRight - Build Script
::
::  Usage:
::    build.bat              Build portable app (folder + ZIP)
::    build.bat portable     Same as above
::    build.bat installer    Build Windows installer (.exe setup wizard)
::    build.bat all          Build both portable and installer
::
::  Output:
::    Portable:   target\CiteRight-portable\CiteRight\CiteRight.exe
::                CiteRight-Portable.zip
::    Installer:  target\CiteRight-installer\CiteRight-1.0.0.exe
::
::  Requirements (on YOUR build machine only, NOT on the target PC):
::    - Java 21 JDK (includes jpackage)
::    - Maven (mvn on PATH)
::    - WiX Toolset v3 (for installer mode only)
:: ============================================================================

:: -- Parse Arguments --

set BUILD_MODE=portable
if /i "%~1"=="installer" set BUILD_MODE=installer
if /i "%~1"=="all"       set BUILD_MODE=all

set APP_NAME=CiteRight
set APP_VERSION=1.0.0
set APP_VENDOR=CiteRight
set "APP_DESCRIPTION=Smart Citation Manager"
set STANDALONE_JAR=target\citeright-1.0-SNAPSHOT-standalone.jar
set JPACKAGE_INPUT=target\jpackage-input
set JPACKAGE_MODULES=java.base,java.desktop,java.logging,java.sql,java.net.http,java.xml,java.naming,jdk.crypto.ec,jdk.unsupported

echo.
echo  =====================================================
echo   %APP_NAME% ^| %BUILD_MODE% Build
echo  =====================================================
echo.

:: -- 1. Verify Prerequisites --

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
    echo [ERROR] jpackage not found on PATH. Ensure you have a full JDK 21.
    pause & exit /b 1
)

:: Check WiX for installer mode
if "%BUILD_MODE%"=="installer" call :check_wix
if "%BUILD_MODE%"=="all"       call :check_wix

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "openjdk version"') do (
    set JAVA_VER=%%v
)
echo [INFO] Using Java: !JAVA_VER!
echo [INFO] Build mode: %BUILD_MODE%

:: -- 2. Clean + Build Fat JAR --

echo.
echo [STEP 1] Building fat JAR with Maven...
echo.

call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed. Check the output above for errors.
    pause & exit /b 1
)

if not exist "%STANDALONE_JAR%" (
    echo [ERROR] Expected JAR not found: %STANDALONE_JAR%
    pause & exit /b 1
)
echo [OK] Fat JAR built: %STANDALONE_JAR%

:: -- 3. Prepare jpackage Input --

if exist "%JPACKAGE_INPUT%" rmdir /s /q "%JPACKAGE_INPUT%"
mkdir "%JPACKAGE_INPUT%"
copy "%STANDALONE_JAR%" "%JPACKAGE_INPUT%\" >nul

:: -- 4. Build Portable (if requested) --

if "%BUILD_MODE%"=="portable" call :build_portable
if "%BUILD_MODE%"=="all"      call :build_portable

:: -- 5. Build Installer (if requested) --

if "%BUILD_MODE%"=="installer" call :build_installer
if "%BUILD_MODE%"=="all"       call :build_installer

:: -- Done --

echo.
echo  =====================================================
echo   BUILD COMPLETE
echo  =====================================================
echo.

if "%BUILD_MODE%"=="portable" (
    echo  Portable folder : target\CiteRight-portable\%APP_NAME%\%APP_NAME%.exe
    echo  To run:  target\CiteRight-portable\%APP_NAME%\%APP_NAME%.exe
    echo  To distribute: Copy CiteRight-Portable.zip to any Windows PC and unzip.
)

if "%BUILD_MODE%"=="installer" (
    echo  Installer : target\CiteRight-installer\%APP_NAME%-%APP_VERSION%.exe
    echo  To distribute: Upload the .exe to your website or GitHub Releases.
    echo  Users double-click to install. No Java needed!
)

if "%BUILD_MODE%"=="all" (
    echo  Portable  : target\CiteRight-portable\%APP_NAME%\%APP_NAME%.exe
    echo  Installer : target\CiteRight-installer\%APP_NAME%-%APP_VERSION%.exe
)

echo.
pause
exit /b 0

:: ============================================================================
::  SUBROUTINES
:: ============================================================================

:: -- Build Portable App Image --
:build_portable
echo.
echo [STEP] Creating portable app with bundled JRE...
echo.

if exist "target\CiteRight-portable" (
    rmdir /s /q "target\CiteRight-portable"
)

jpackage ^
    --type app-image ^
    --name %APP_NAME% ^
    --app-version %APP_VERSION% ^
    --vendor "%APP_VENDOR%" ^
    --description "%APP_DESCRIPTION%" ^
    --input %JPACKAGE_INPUT% ^
    --main-jar citeright-1.0-SNAPSHOT-standalone.jar ^
    --main-class com.citeright.Launcher ^
    --dest target\CiteRight-portable ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Xmx512m" ^
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" ^
    --icon src\main\resources\icon.ico

if errorlevel 1 (
    echo [ERROR] jpackage portable build failed. See output above.
    pause & exit /b 1
)

echo [OK] Portable app created at: target\CiteRight-portable\%APP_NAME%\

:: Create ZIP
echo.
echo [STEP] Creating ZIP archive...

set ZIP_NAME=CiteRight-Portable.zip
if exist "%ZIP_NAME%" del /f /q "%ZIP_NAME%"

powershell -NoProfile -Command ^
    "Compress-Archive -Path 'target\CiteRight-portable\%APP_NAME%' -DestinationPath '%ZIP_NAME%' -Force"

if errorlevel 1 (
    echo [WARN] Could not create ZIP. The portable folder is still usable.
) else (
    echo [OK] ZIP created: %ZIP_NAME%
)

goto :eof

:: -- Build Windows Installer (.exe) --
:build_installer
echo.
echo [STEP] Creating Windows installer with bundled JRE...
echo        (This takes 2-4 minutes)
echo.

if exist "target\CiteRight-installer" (
    rmdir /s /q "target\CiteRight-installer"
)

jpackage ^
    --type exe ^
    --name %APP_NAME% ^
    --app-version %APP_VERSION% ^
    --vendor "%APP_VENDOR%" ^
    --description "%APP_DESCRIPTION%" ^
    --copyright "Copyright (c) 2024-2026 Aatif Muneeb Khan" ^
    --input %JPACKAGE_INPUT% ^
    --main-jar citeright-1.0-SNAPSHOT-standalone.jar ^
    --main-class com.citeright.Launcher ^
    --dest target\CiteRight-installer ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Xmx512m" ^
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" ^
    --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" ^
    --icon src\main\resources\icon.ico ^
    --license-file LICENSE.txt ^
    --win-dir-chooser ^
    --win-menu ^
    --win-menu-group "%APP_NAME%" ^
    --win-shortcut ^
    --win-shortcut-prompt ^
    --win-per-user-install ^
    --file-associations src\main\resources\fa-bib.properties ^
    --file-associations src\main\resources\fa-bibtex.properties ^
    --file-associations src\main\resources\fa-ris.properties ^
    --resource-dir src\main\resources\wix ^
    --about-url "https://citeright.app"

if errorlevel 1 (
    echo [ERROR] jpackage installer build failed.
    echo.
    echo [HINT] Common fixes:
    echo   - Ensure WiX Toolset v3 is installed and candle.exe is on PATH
    echo   - Restart your terminal after installing WiX so PATH updates take effect
    echo.
    pause & exit /b 1
)

echo [OK] Installer created at: target\CiteRight-installer\%APP_NAME%-%APP_VERSION%.exe
goto :eof

:: -- Check WiX Toolset --
:check_wix

:: Try to find candle.exe (WiX compiler) on PATH
where candle.exe >nul 2>&1
if not errorlevel 1 (
    echo [INFO] WiX Toolset found on PATH.
    goto :eof
)

:: Check portable download location
if exist "%USERPROFILE%\tools\wix3\candle.exe" (
    echo [INFO] WiX Toolset found at: %USERPROFILE%\tools\wix3
    set "PATH=%USERPROFILE%\tools\wix3;!PATH!"
    goto :eof
)
if exist "C:\Users\atifm\tools\wix3\candle.exe" (
    echo [INFO] WiX Toolset found at: C:\Users\atifm\tools\wix3
    set "PATH=C:\Users\atifm\tools\wix3;!PATH!"
    goto :eof
)

:: Check standard WiX 3 install location
set "WIX_STD=C:\Program Files (x86)\WiX Toolset v3.14\bin"
if exist "!WIX_STD!\candle.exe" (
    echo [INFO] WiX Toolset found at: !WIX_STD!
    set "PATH=!WIX_STD!;!PATH!"
    goto :eof
)

echo [ERROR] WiX Toolset not found. Required for installer builds.
echo.
echo   Install it with:  winget install WiXToolset.WiXToolset
echo   Or download portable binaries from: https://github.com/wixtoolset/wix3/releases
echo   Then restart your terminal.
echo.
pause & exit /b 1

