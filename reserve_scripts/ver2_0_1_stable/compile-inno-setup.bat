@echo off
chcp 65001
title Compile Inno Setup

cd /d "%~dp0"

echo ========================================
echo    COMPILING INNO SETUP EXE
echo ========================================
echo.

set ISS_FILE="installer_resources\KIPiA_Setup.iss"
set INNO_PATH="C:\Program Files (x86)\Inno Setup 6\ISCC.exe"

if not exist %INNO_PATH% (
    echo ERROR: Inno Setup compiler not found at %INNO_PATH%
    pause
    exit /b 1
)

if not exist "Output" mkdir "Output"

echo Compiling %ISS_FILE%...
%INNO_PATH% "/OOutput" %ISS_FILE%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ EXE INSTALLER CREATED SUCCESSFULLY!
    echo File: Output\KIPiA_Management_Setup_*.exe
) else (
    echo.
    echo ❌ EXE CREATION FAILED!
)

echo.
pause
