@echo off
chcp 65001
title KIPiA Environment Check

echo ========================================
echo    KIPiA ENVIRONMENT CHECK
echo ========================================
echo.

set "INSTALL_DIR=%~dp0"
set "USER_DATA=%APPDATA%\KIPiA_Management"

echo [1] System Information:
echo    Username: %USERNAME%
echo    Computer: %COMPUTERNAME%
echo    Install Dir: %INSTALL_DIR%
echo    User Data: %USER_DATA%
echo.

echo [2] Java Information:
java -version 2>&1 | find "version"
echo.

echo [3] Directory Structure:
echo    Installation directory:
dir "%INSTALL_DIR%" /B
echo.
echo    User data directory:
if exist "%USER_DATA%" (
    dir "%USER_DATA%" /B
) else (
    echo Not exists
)
echo.

echo [4] Log Files:
if exist "%USER_DATA%\logs" (
    echo Log directory exists
    dir "%USER_DATA%\logs\*.log" /B 2>nul || echo No log files
) else (
    echo Log directory not exists
)

echo.
pause