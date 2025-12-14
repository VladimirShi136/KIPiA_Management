@echo off
chcp 65001
title Create Installer for EXE

cd /d "%~dp0"

echo ========================================
echo    CREATING INSTALLER STRUCTURE FOR EXE
echo ========================================
echo.

echo Step 1: Creating folder structure...
if exist "KIPiA_Installer" rmdir /s /q "KIPiA_Installer"
mkdir "KIPiA_Installer"

echo Step 2: Copying JAR file...
copy "target\KIPiA_Management-1.0.1.jar" "KIPiA_Installer\"

echo Step 3: Copying dependencies...
if exist "target\lib" (
    xcopy "target\lib\*" "KIPiA_Installer\dependencies\" /E /I /Y /Q
    echo Dependencies copied from target\lib
) else if exist "target\dependency" (
    xcopy "target\dependency\*" "KIPiA_Installer\dependencies\" /E /I /Y /Q
    echo Dependencies copied from target\dependency
) else (
    echo WARNING: No dependencies folder found!
)

echo Step 4: Copying JavaFX SDK...
set JAVAFX_SOURCE=C:\javafx-sdk-25.0.1
if exist "%JAVAFX_SOURCE%" (
    if not exist "KIPiA_Installer\javafx" mkdir "KIPiA_Installer\javafx"
    xcopy "%JAVAFX_SOURCE%\lib\*.jar" "KIPiA_Installer\javafx\" /Y /Q
    xcopy "%JAVAFX_SOURCE%\bin\*.dll" "KIPiA_Installer\javafx\" /Y /Q
    echo JavaFX SDK copied successfully
) else (
    echo ERROR: JavaFX not found at %JAVAFX_SOURCE%
    pause
    exit /b 1
)

echo Step 5: Copying resources...
copy "installer_resources\README_RU.txt" "KIPiA_Installer\README.txt" >nul 2>&1
copy "installer_resources\log4j2.xml" "KIPiA_Installer\" >nul 2>&1
copy "installer_resources\ViewLogs.bat" "KIPiA_Installer\" >nul 2>&1
if exist "installer_resources\iconApp.ico" copy "installer_resources\iconApp.ico" "KIPiA_Installer\" >nul 2>&1

echo Step 5.1: Copying ChangeLog.txt...
if exist "installer_resources\ChangeLog.txt" (
    copy "installer_resources\ChangeLog.txt" "KIPiA_Installer\" >nul 2>&1
    echo ChangeLog.txt copied to installer root
) else (
    echo WARNING: ChangeLog.txt not found in installer_resources!
)

echo Step 5.2: Copying debug utilities...
copy "installer_resources\DebugLauncher.bat" "KIPiA_Installer\" >nul 2>&1
if exist "installer_resources\RunWithConsole.bat" copy "installer_resources\RunWithConsole.bat" "KIPiA_Installer\" >nul 2>&1
echo Debug utilities copied

echo Step 6: Creating EXE launcher...
echo @echo off > "KIPiA_Installer\KIPiA_Management.exe.bat"
echo setlocal >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo set "INSTALL_DIR=%%~dp0" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo set "USER_DATA=%%APPDATA%%\KIPiA_Management" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo set "JAVAFX_DIR=%%INSTALL_DIR%%\javafx" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo if not exist "%%USER_DATA%%" mkdir "%%USER_DATA%%" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo if not exist "%%USER_DATA%%\logs" mkdir "%%USER_DATA%%\logs" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo if not exist "%%USER_DATA%%\data" mkdir "%%USER_DATA%%\data" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo REM Копируем конфиг логов в AppData при первом запуске >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo if not exist "%%USER_DATA%%\log4j2.xml" copy "%%INSTALL_DIR%%\log4j2.xml" "%%USER_DATA%%\" ^>nul >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo if not exist "%%USER_DATA%%\ViewLogs.bat" copy "%%INSTALL_DIR%%\ViewLogs.bat" "%%USER_DATA%%\" ^>nul >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo REM Указываем правильный путь к библиотекам и конфигу >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo set "PATH=%%JAVAFX_DIR%%;%%PATH%%" >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo start "" javaw -Djava.library.path="%%JAVAFX_DIR%%" ^^>> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo      --module-path "%%JAVAFX_DIR%%" ^^>> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo      --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^^>> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo      -Dproduction=true ^^>> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo      -Dlog4j.configurationFile="%%USER_DATA%%\log4j2.xml" ^^>> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo      -cp "%%INSTALL_DIR%%\KIPiA_Management-1.0.1.jar;%%INSTALL_DIR%%\dependencies\*;%%JAVAFX_DIR%%\*" ^^>> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo      com.kipia.management.kipia_management.Main >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo endlocal >> "KIPiA_Installer\KIPiA_Management.exe.bat"
echo exit >> "KIPiA_Installer\KIPiA_Management.exe.bat"

echo.
echo ========================================
echo    INSTALLER STRUCTURE READY FOR EXE
echo ========================================
echo.
echo Files in KIPiA_Installer:
dir "KIPiA_Installer" /B
echo.
echo JavaFX DLLs:
dir "KIPiA_Installer\javafx\*.dll" /B | find /c ".dll"
echo Dependencies JARs:
dir "KIPiA_Installer\dependencies\*.jar" /B | find /c ".jar"
echo.
pause