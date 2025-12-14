@echo off
chcp 65001
title KIPiA Management System - View Logs

echo ========================================
echo    KIPiA Management System - Log Viewer
echo ========================================
echo.

set "LOG_DIR=%APPDATA%\KIPiA_Management\logs"
set "LOG_FILE=%LOG_DIR%\kipia-management.log"

if not exist "%LOG_DIR%" (
    echo Log directory not found: %LOG_DIR%
    echo.
    echo Please run the application first to generate logs.
    echo.
    pause
    exit /b 1
)

if not exist "%LOG_FILE%" (
    echo Log file not found: %LOG_FILE%
    echo.
    echo Available log files in %LOG_DIR%:
    dir "%LOG_DIR%\*.log" /B 2>nul || echo No log files found
    echo.
    echo Please run the application first to generate logs.
    pause
    exit /b 1
)

echo Opening log file: %LOG_FILE%
for %%F in ("%LOG_FILE%") do echo File size: %%~zF bytes
echo Last modified: 
for %%F in ("%LOG_FILE%") do echo   %%~tF
echo.
notepad "%LOG_FILE%"