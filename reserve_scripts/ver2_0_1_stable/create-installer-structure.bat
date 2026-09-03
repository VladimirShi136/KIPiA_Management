@echo off
chcp 65001
title Create Installer Structure

cd /d "%~dp0"

set APP_VERSION=%1

if not defined APP_VERSION (
    echo ERROR: Version not provided!
    exit /b 1
)

echo ========================================
echo    CREATING INSTALLER STRUCTURE
echo    Version: %APP_VERSION%
echo ========================================
echo.

:: Шаг 1. Создание папки
if exist "KIPiA_Installer" rmdir /s /q "KIPiA_Installer"
mkdir "KIPiA_Installer"

:: Шаг 2. Копирование JAR
echo Step 1: Copying JAR file...
copy "target\KIPiA_Management-%APP_VERSION%.jar" "KIPiA_Installer\"

:: Шаг 3. Копирование зависимостей
echo Step 2: Copying dependencies...
if exist "target\lib" (
    xcopy "target\lib\*" "KIPiA_Installer\dependencies\" /E /I /Y /Q
    echo Dependencies copied from target\lib
) else if exist "target\dependency" (
    xcopy "target\dependency\*" "KIPiA_Installer\dependencies\" /E /I /Y /Q
    echo Dependencies copied from target\dependency
) else (
    echo WARNING: No dependencies folder found!
)

:: Шаг 4. Копирование JavaFX
echo Step 3: Copying JavaFX SDK...
set JAVAFX_SOURCE=C:\javafx-sdk-25.0.1
if exist "%JAVAFX_SOURCE%" (
    if not exist "KIPiA_Installer\javafx" mkdir "KIPiA_Installer\javafx"
    xcopy "%JAVAFX_SOURCE%\lib\*.jar" "KIPiA_Installer\javafx\" /Y /Q
    xcopy "%JAVAFX_SOURCE%\bin\*.dll" "KIPiA_Installer\javafx\" /Y /Q
    echo JavaFX SDK copied successfully
) else (
    echo ERROR: JavaFX not found at %JAVAFX_SOURCE%
    exit /b 1
)

:: Шаг 5. Копирование ресурсов
echo Step 4: Copying resources...
copy "installer_resources\README_RU.txt" "KIPiA_Installer\README.txt" >nul 2>&1
copy "installer_resources\log4j2.xml" "KIPiA_Installer\" >nul 2>&1
copy "installer_resources\ViewLogs.bat" "KIPiA_Installer\" >nul 2>&1
copy "installer_resources\CheckEnvironment.bat" "KIPiA_Installer\" >nul 2>&1
if exist "installer_resources\iconApp.ico" copy "installer_resources\iconApp.ico" "KIPiA_Installer\" >nul 2>&1

:: ChangeLog и отладочные файлы
if exist "installer_resources\ChangeLog.txt" (
    copy "installer_resources\ChangeLog.txt" "KIPiA_Installer\" >nul 2>&1
    echo ChangeLog.txt copied
) else (
    echo WARNING: ChangeLog.txt not found
)

copy "installer_resources\DebugLauncher.bat" "KIPiA_Installer\" >nul 2>&1
if exist "installer_resources\RunWithConsole.bat" copy "installer_resources\RunWithConsole.bat" "KIPiA_Installer\" >nul 2>&1
echo Debug utilities copied

:: Шаг 6. Создание батника-лаунчера
echo Step 5: Creating launcher...

> "KIPiA_Installer\KIPiA_Management.exe.bat" (
    echo @echo off
    echo setlocal
    echo set "INSTALL_DIR=%%~dp0"
    echo set "USER_DATA=%%APPDATA%%\KIPiA_Management"
    echo set "JAVAFX_DIR=%%INSTALL_DIR%%\javafx"
    
    echo DEBUG: Checking USER_DATA directory existence...
    echo if not exist "%%USER_DATA%%" mkdir "%%USER_DATA%%"
    
    echo DEBUG: Checking logs subdirectory...
    echo if not exist "%%USER_DATA%%\logs" mkdir "%%USER_DATA%%\logs"
    
    echo DEBUG: Checking data subdirectory...
    echo if not exist "%%USER_DATA%%\data" mkdir "%%USER_DATA%%\data"
    
    echo DEBUG: Checking log4j2.xml configuration...
    echo if not exist "%%USER_DATA%%\log4j2.xml" copy "%%INSTALL_DIR%%\log4j2.xml" "%%USER_DATA%%\" ^>nul
    
    echo DEBUG: Checking ViewLogs.bat utility...
    echo if not exist "%%USER_DATA%%\ViewLogs.bat" copy "%%INSTALL_DIR%%\ViewLogs.bat" "%%USER_DATA%%\" ^>nul
    
    echo DEBUG: Adding PATH variable...
    echo set "PATH=%%JAVAFX_DIR%%;%%PATH%%"
    
    echo DEBUG: Starting application...
    echo start "" javaw ^
        -Djava.library.path="%%JAVAFX_DIR%%" ^
        --module-path "%%JAVAFX_DIR%%" ^
        --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^
        -Dproduction=true ^
        -Dlog4j.configurationFile="%%USER_DATA%%\log4j2.xml" ^
        -cp "%%INSTALL_DIR%%\KIPiA_Management-%APP_VERSION%.jar;%%INSTALL_DIR%%\dependencies\*;%%JAVAFX_DIR%%\*" ^
        com.kipia.management.kipia_management.Main
        
    echo DEBUG: Ending local scope...
    echo endlocal
)

echo Launcher script created successfully!