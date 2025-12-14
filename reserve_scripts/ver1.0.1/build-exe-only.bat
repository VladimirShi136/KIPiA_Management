@echo off
chcp 65001
title KIPiA - Build EXE Installer Only

cd /d "%~dp0"

echo ========================================
echo    BUILDING EXE INSTALLER ONLY
echo ========================================
echo.

echo [1/4] Cleaning previous builds...
if exist "KIPiA_Installer" (
    echo Removing old installer folder...
    rmdir /s /q "KIPiA_Installer"
)
if exist "Output" (
    echo Removing old output folder...
    rmdir /s /q "Output"
)

echo.
echo [2/4] Building JAR with Maven...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)

echo.
echo [3/4] Creating installer structure...
call create-installer-exe.bat

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Installer creation failed!
    pause
    exit /b 1
)

echo.
echo [4/4] Creating EXE installer...
call create-inno-exe.bat

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: EXE creation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo    EXE INSTALLER BUILD COMPLETED!
echo ========================================
echo.
if exist "Output\KIPiA_Management_Setup_1.0.1.exe" (
    echo ✓ SUCCESS: Output\KIPiA_Management_Setup_1.0.1.exe
    for %%F in ("Output\KIPiA_Management_Setup_1.0.1.exe") do echo   Size: %%~zF bytes
) else (
    echo ✗ FAILED: EXE not created
)

echo.
echo Ready for testing!
pause