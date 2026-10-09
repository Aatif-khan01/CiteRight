@echo off
:: ============================================================================
::  CiteRight — Developer Run Script
::  Runs the fat JAR directly (requires Java 21 on this machine).
::  Faster than build.bat — no packaging step.
::
::  For distribution to other PCs, use:  build.bat
:: ============================================================================

set JAR=target\citeright-1.0-SNAPSHOT-standalone.jar

if not exist "%JAR%" (
    echo [INFO] JAR not found. Building first...
    call mvn package -DskipTests -q
    if errorlevel 1 ( echo [ERROR] Build failed. & pause & exit /b 1 )
)

echo [INFO] Launching CiteRight...
java ^
    -Dfile.encoding=UTF-8 ^
    --add-opens java.base/java.lang=ALL-UNNAMED ^
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
    --add-opens java.base/java.util=ALL-UNNAMED ^
    --add-opens java.base/java.io=ALL-UNNAMED ^
    -jar %JAR%
