@echo off
chcp 65001
title KIPiA Management - Debug Mode

echo ========================================
echo    KIPiA MANAGEMENT - DEBUG MODE
echo ========================================
echo.

set "INSTALL_DIR=%~dp0"
set "USER_DATA=%APPDATA%\KIPiA_Management"
set "JAVAFX_DIR=%INSTALL_DIR%javafx"

echo Setting up environment...
if not exist "%USER_DATA%" mkdir "%USER_DATA%"
if not exist "%USER_DATA%\logs" mkdir "%USER_DATA%\logs"
if not exist "%USER_DATA%\data" mkdir "%USER_DATA%\data"

if not exist "%USER_DATA%\log4j2.xml" if exist "%INSTALL_DIR%log4j2.xml" (
    copy "%INSTALL_DIR%log4j2.xml" "%USER_DATA%\"
)

echo Starting application with console...
echo Logs will appear below. Press Ctrl+C to stop.
echo.

java -Djava.library.path="%JAVAFX_DIR%" ^
     --module-path "%JAVAFX_DIR%" ^
     --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^
     -Dproduction=true ^
     -Dlog4j2.debug=true ^
     -Dlog4j.configurationFile="%USER_DATA%\log4j2.xml" ^
     -cp "%INSTALL_DIR%KIPiA_Management-1.0.0.jar;%INSTALL_DIR%dependencies\*;%JAVAFX_DIR%\*" ^
     com.kipia.management.kipia_management.Main

echo.
echo Application exited with code: %ERRORLEVEL%
pause