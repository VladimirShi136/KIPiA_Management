@echo off
chcp 65001
title Create KIPiA Installer

echo ========================================
echo    Building KIPiA Management System
echo ========================================
echo.

echo Step 1: Cleaning and building...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Step 2: Creating installer structure...

:: Удаляем старый установщик
if exist "KIPiA_Installer" rmdir /s /q "KIPiA_Installer"
mkdir "KIPiA_Installer"
mkdir "KIPiA_Installer\app"
mkdir "KIPiA_Installer\app\dependencies"
mkdir "KIPiA_Installer\app\javafx"

:: Копируем JAR
echo Copying JAR file...
copy "target\KIPiA_Management-1.0.0.jar" "KIPiA_Installer\app\"

:: Копируем зависимости из ПРАВИЛЬНОЙ папки
echo Copying dependencies...
if exist "target\lib" (
    xcopy "target\lib\*" "KIPiA_Installer\app\dependencies\" /E /I /Y /Q
) else if exist "target\dependency" (
    xcopy "target\dependency\*" "KIPiA_Installer\app\dependencies\" /E /I /Y /Q
) else (
    echo WARNING: No dependencies folder found!
)

:: Копируем ВЕСЬ JavaFX SDK
echo Copying JavaFX SDK...
set JAVAFX_SOURCE=C:\javafx-sdk-25.0.1
if exist "%JAVAFX_SOURCE%" (
    xcopy "%JAVAFX_SOURCE%\lib\*.jar" "KIPiA_Installer\app\javafx\" /E /I /Y /Q
    xcopy "%JAVAFX_SOURCE%\bin\*.dll" "KIPiA_Installer\app\javafx\" /E /I /Y /Q
    echo JavaFX SDK copied successfully
) else (
    echo ERROR: JavaFX not found at %JAVAFX_SOURCE%
    pause
    exit /b 1
)

:: Создаем запускающий скрипт для пользовательской папки (БЕЗ КОНСОЛИ)
echo Creating launcher...
echo @echo off > "KIPiA_Installer\KIPiA_Management.bat"
echo chcp 65001 >> "KIPiA_Installer\KIPiA_Management.bat"
echo title KIPiA Management System >> "KIPiA_Installer\KIPiA_Management.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.bat"
echo setlocal >> "KIPiA_Installer\KIPiA_Management.bat"
echo set "INSTALL_DIR=%%~dp0" >> "KIPiA_Installer\KIPiA_Management.bat"
echo set "USER_DATA=%%APPDATA%%\KIPiA_Management" >> "KIPiA_Installer\KIPiA_Management.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.bat"
echo :: Создаем папки в пользовательском пространстве >> "KIPiA_Installer\KIPiA_Management.bat"
echo if not exist "%%USER_DATA%%" mkdir "%%USER_DATA%%" >> "KIPiA_Installer\KIPiA_Management.bat"
echo if not exist "%%USER_DATA%%\logs" mkdir "%%USER_DATA%%\logs" >> "KIPiA_Installer\KIPiA_Management.bat"
echo if not exist "%%USER_DATA%%\data" mkdir "%%USER_DATA%%\data" >> "KIPiA_Installer\KIPiA_Management.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.bat"
echo :: Копируем конфиги если их нет >> "KIPiA_Installer\KIPiA_Management.bat"
echo if not exist "%%USER_DATA%%\log4j2.xml" copy "%%INSTALL_DIR%%\log4j2.xml" "%%USER_DATA%%\" ^>nul >> "KIPiA_Installer\KIPiA_Management.bat"
echo if not exist "%%USER_DATA%%\ViewLogs.bat" copy "%%INSTALL_DIR%%\ViewLogs.bat" "%%USER_DATA%%\" ^>nul >> "KIPiA_Installer\KIPiA_Management.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.bat"
echo :: Добавляем JavaFX DLL в PATH >> "KIPiA_Installer\KIPiA_Management.bat"
echo set "PATH=%%INSTALL_DIR%%\app\javafx;%%PATH%%" >> "KIPiA_Installer\KIPiA_Management.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.bat"
echo :: Запускаем приложение БЕЗ КОНСОЛИ >> "KIPiA_Installer\KIPiA_Management.bat"
echo start "KIPiA Management System" /B java --module-path "%%INSTALL_DIR%%\app\javafx" ^^>> "KIPiA_Installer\KIPiA_Management.bat"
echo      --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^^>> "KIPiA_Installer\KIPiA_Management.bat"
echo      -Dprism.order=d3d,sw ^^>> "KIPiA_Installer\KIPiA_Management.bat"
echo      -Dlog4j.configurationFile=file:%%USER_DATA%%\log4j2.xml ^^>> "KIPiA_Installer\KIPiA_Management.bat"
echo      -cp "%%INSTALL_DIR%%\app\KIPiA_Management-1.0.0.jar;%%INSTALL_DIR%%\app\dependencies\*" ^^>> "KIPiA_Installer\KIPiA_Management.bat"
echo      com.kipia.management.kipia_management.Main ^^>> "KIPiA_Installer\KIPiA_Management.bat"
echo. >> "KIPiA_Installer\KIPiA_Management.bat"
echo exit >> "KIPiA_Installer\KIPiA_Management.bat"
echo endlocal >> "KIPiA_Installer\KIPiA_Management.bat"

:: Создаем VBS скрипт для запуска без консоли
echo Creating VBS launcher...
echo Set WshShell = CreateObject("WScript.Shell") > "KIPiA_Installer\KIPiA_Management.vbs"
echo WshShell.Run "cmd /c KIPiA_Management.bat", 0, False >> "KIPiA_Installer\KIPiA_Management.vbs"
echo Set WshShell = Nothing >> "KIPiA_Installer\KIPiA_Management.vbs"

:: Создаем инструкцию
echo Creating README...
echo KIPiA Management System - Установка > "KIPiA_Installer\README.txt"
echo. >> "KIPiA_Installer\README.txt"
echo 1. Запустите KIPiA_Management.vbs для запуска без консоли >> "KIPiA_Installer\README.txt"
echo 2. Или KIPiA_Management.bat для запуска с консолью (для отладки) >> "KIPiA_Installer\README.txt"
echo 3. Убедитесь что установлена Java 23+ >> "KIPiA_Installer\README.txt"
echo. >> "KIPiA_Installer\README.txt"
echo Требования: >> "KIPiA_Installer\README.txt"
echo - Java 23 или выше >> "KIPiA_Installer\README.txt"
echo - Windows 10/11 >> "KIPiA_Installer\README.txt"
echo. >> "KIPiA_Installer\README.txt"
echo Для просмотра логов используйте ViewLogs.bat >> "KIPiA_Installer\README.txt"

echo.
echo ========================================
echo    INSTALLER CREATED SUCCESSFULLY!
echo ========================================
echo.
echo Installer folder: KIPiA_Installer
echo.
echo Checking installer contents...
echo JavaFX JAR files:
dir "KIPiA_Installer\app\javafx\*.jar" /B
echo.
echo JavaFX DLL files: 
dir "KIPiA_Installer\app\javafx\*.dll" /B
echo.
echo Dependencies:
dir "KIPiA_Installer\app\dependencies\*.jar" /B | find /c ".jar"
echo JAR files found
echo.
pause