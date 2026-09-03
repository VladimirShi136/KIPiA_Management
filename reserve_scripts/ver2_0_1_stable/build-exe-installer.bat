@echo off
chcp 65001
title KIPiA - Build EXE Installer


cd /d "%~dp0"

echo ========================================
echo    BUILDING EXE INSTALLER
echo ========================================
echo.

set APP_VERSION=
FOR /F "usebackq tokens=*" %%V IN (`powershell "(Get-Content 'pom.xml' | Select-Xml '//version')[0].Node.InnerText"`) DO SET APP_VERSION=%%V


if not defined APP_VERSION (
    echo ERROR: Version not found in pom.xml!
    pause
    exit /b 1
)

echo Using version: %APP_VERSION%
echo.

:: Шаг 1. Очистка
echo [1/4] Cleaning previous builds...
if exist "KIPiA_Installer" rmdir /s /q "KIPiA_Installer"
if exist "Output" rmdir /s /q "Output"


:: Шаг 2. Сборка JAR
echo.
echo [2/4] Building JAR with Maven...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)

:: Шаг 3. Подготовка структуры инсталлятора
echo.
echo [3/4] Creating installer structure...
call create-installer-structure.bat %APP_VERSION%
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Installer structure creation failed!
    pause
    exit /b 1
)

:: Шаг 4. Генерация ISS и компиляция EXE
echo.
echo [4/4] Generating ISS and compiling EXE...
call generate-iss.ps1 normal %APP_VERSION%
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: ISS generation failed!
    pause
    exit /b 1
)

call compile-inno-setup.bat
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: EXE creation failed!
    pause
    exit /b 1
)

:: Финальная проверка
echo.
echo ========================================
echo    EXE INSTALLER BUILD COMPLETED!
echo ========================================
echo.

set EXE_FILE="Output\KIPiA_Management_%APP_VERSION%.exe"
if exist %EXE_FILE% (
    echo ✓ SUCCESS: %EXE_FILE%
    for %%F in (%EXE_FILE%) do echo   Size: %%~zF bytes
) else (
    echo ✗ FAILED: EXE not created
)

echo.
echo Ready for testing!
pause
