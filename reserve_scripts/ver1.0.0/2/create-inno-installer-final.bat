@echo off
chcp 65001
title Create KIPiA EXE Installer - Final

echo ========================================
echo    Creating Professional EXE Installer
echo ========================================
echo.

:: Проверяем что установщик собран
if not exist "KIPiA_Installer" (
    echo ERROR: Installer folder not found!
    echo Run create-installer.bat first.
    pause
    exit /b 1
)

:: Создаем папку для ресурсов
if not exist "installer_resources" mkdir "installer_resources"

echo Step 1: Checking required files...
if not exist "installer_resources\iconApp.ico" (
    echo ERROR: iconApp.ico not found in installer_resources folder!
    echo Please copy your icon to installer_resources\iconApp.ico
    pause
    exit /b 1
)

if not exist "installer_resources\README_RU.txt" (
    echo ERROR: README_RU.txt not found in installer_resources folder!
    echo Please copy your README to installer_resources\README_RU.txt
    pause
    exit /b 1
)

:: Создаем улучшенный конфиг log4j2.xml
if not exist "installer_resources\log4j2.xml" (
    echo Creating enhanced log4j2.xml for user directory...
    echo ^<?xml version="1.0" encoding="UTF-8"?^> > "installer_resources\log4j2.xml"
    echo ^<Configuration status="WARN"^> >> "installer_resources\log4j2.xml"
    echo     ^<Properties^> >> "installer_resources\log4j2.xml"
    echo         ^<Property name="LOG_PATTERN"^>%%d{yyyy-MM-dd HH:mm:ss.SSS} [%%t] %%-5level %%logger{36} - %%msg%%n^</Property^> >> "installer_resources\log4j2.xml"
    echo     ^</Properties^> >> "installer_resources\log4j2.xml"
    echo. >> "installer_resources\log4j2.xml"
    echo     ^<Appenders^> >> "installer_resources\log4j2.xml"
    echo         ^<RollingFile name="FileAppender" fileName="${sys:user.home}/AppData/Roaming/KIPiA_Management/logs/kipia_management.log" ^> >> "installer_resources\log4j2.xml"
    echo             filePattern="${sys:user.home}/AppData/Roaming/KIPiA_Management/logs/kipia_management-%%d{yyyy-MM-dd}-%%i.log.gz"^> >> "installer_resources\log4j2.xml"
    echo             ^<PatternLayout pattern="${LOG_PATTERN}"/^> >> "installer_resources\log4j2.xml"
    echo             ^<Policies^> >> "installer_resources\log4j2.xml"
    echo                 ^<TimeBasedTriggeringPolicy /^> >> "installer_resources\log4j2.xml"
    echo                 ^<SizeBasedTriggeringPolicy size="10 MB"/^> >> "installer_resources\log4j2.xml"
    echo             ^</Policies^> >> "installer_resources\log4j2.xml"
    echo             ^<DefaultRolloverStrategy max="10"/^> >> "installer_resources\log4j2.xml"
    echo         ^</RollingFile^> >> "installer_resources\log4j2.xml"
    echo         ^<Console name="Console" target="SYSTEM_OUT"^> >> "installer_resources\log4j2.xml"
    echo             ^<PatternLayout pattern="${LOG_PATTERN}"/^> >> "installer_resources\log4j2.xml"
    echo         ^</Console^> >> "installer_resources\log4j2.xml"
    echo     ^</Appenders^> >> "installer_resources\log4j2.xml"
    echo. >> "installer_resources\log4j2.xml"
    echo     ^<Loggers^> >> "installer_resources\log4j2.xml"
    echo         ^<Logger name="com.kipia.management" level="DEBUG" additivity="false"^> >> "installer_resources\log4j2.xml"
    echo             ^<AppenderRef ref="FileAppender"/^> >> "installer_resources\log4j2.xml"
    echo         ^</Logger^> >> "installer_resources\log4j2.xml"
    echo. >> "installer_resources\log4j2.xml"
    echo         ^<Root level="INFO"^> >> "installer_resources\log4j2.xml"
    echo             ^<AppenderRef ref="FileAppender"/^> >> "installer_resources\log4j2.xml"
    echo         ^</Root^> >> "installer_resources\log4j2.xml"
    echo     ^</Loggers^> >> "installer_resources\log4j2.xml"
    echo ^</Configuration^> >> "installer_resources\log4j2.xml"
)

:: Создаем улучшенный ViewLogs.bat
if not exist "installer_resources\ViewLogs.bat" (
    echo Creating enhanced ViewLogs.bat for user directory...
    echo @echo off > "installer_resources\ViewLogs.bat"
    echo chcp 65001 >> "installer_resources\ViewLogs.bat"
    echo title KIPiA Management System - Log Viewer >> "installer_resources\ViewLogs.bat"
    echo. >> "installer_resources\ViewLogs.bat"
    echo echo ======================================== >> "installer_resources\ViewLogs.bat"
    echo echo    KIPiA Management System - Log Viewer >> "installer_resources\ViewLogs.bat"
    echo echo ======================================== >> "installer_resources\ViewLogs.bat"
    echo echo. >> "installer_resources\ViewLogs.bat"
    echo set "LOG_DIR=%%APPDATA%%\KIPiA_Management\logs" >> "installer_resources\ViewLogs.bat"
    echo set "LOG_FILE=%%LOG_DIR%%\kipia_management.log" >> "installer_resources\ViewLogs.bat"
    echo. >> "installer_resources\ViewLogs.bat"
    echo if not exist "%%LOG_DIR%%" ( >> "installer_resources\ViewLogs.bat"
    echo     echo Log directory not found: %%LOG_DIR%% >> "installer_resources\ViewLogs.bat"
    echo     echo. >> "installer_resources\ViewLogs.bat"
    echo     echo The application will create log files when running. >> "installer_resources\ViewLogs.bat"
    echo     echo Please run the application first. >> "installer_resources\ViewLogs.bat"
    echo     pause >> "installer_resources\ViewLogs.bat"
    echo     exit /b 1 >> "installer_resources\ViewLogs.bat"
    echo ) >> "installer_resources\ViewLogs.bat"
    echo. >> "installer_resources\ViewLogs.bat"
    echo if not exist "%%LOG_FILE%%" ( >> "installer_resources\ViewLogs.bat"
    echo     echo Log file not found: %%LOG_FILE%% >> "installer_resources\ViewLogs.bat"
    echo     echo. >> "installer_resources\ViewLogs.bat"
    echo     echo Available log files: >> "installer_resources\ViewLogs.bat"
    echo     dir "%%LOG_DIR%%\*.log" /B >> "installer_resources\ViewLogs.bat"
    echo     echo. >> "installer_resources\ViewLogs.bat"
    echo     echo Please run the application first to generate logs. >> "installer_resources\ViewLogs.bat"
    echo     pause >> "installer_resources\ViewLogs.bat"
    echo     exit /b 1 >> "installer_resources\ViewLogs.bat"
    echo ) >> "installer_resources\ViewLogs.bat"
    echo. >> "installer_resources\ViewLogs.bat"
    echo echo Opening log file: %%LOG_FILE%% >> "installer_resources\ViewLogs.bat"
    echo echo File size: >> "installer_resources\ViewLogs.bat"
    echo for %%F in ("%%LOG_FILE%%") do echo   %%~zF bytes >> "installer_resources\ViewLogs.bat"
    echo echo. >> "installer_resources\ViewLogs.bat"
    echo notepad "%%LOG_FILE%%" >> "installer_resources\ViewLogs.bat"
)

echo All required files found!
echo.

echo Step 2: Creating Inno Setup script...

:: Создаем ISS файл
echo [Setup] > "KIPiA_Setup_Final.iss"
echo AppId={{KIPiA-Management-System-1.0}} >> "KIPiA_Setup_Final.iss"
echo AppName=KIPiA Management System >> "KIPiA_Setup_Final.iss"
echo AppVerName=KIPiA Management System 1.0.0 >> "KIPiA_Setup_Final.iss"
echo AppVersion=1.0.0 >> "KIPiA_Setup_Final.iss"
echo AppPublisher=KIPiA >> "KIPiA_Setup_Final.iss"
echo AppPublisherURL=https://github.com/VladimirShi136/KIPiA_Management.git >> "KIPiA_Setup_Final.iss"
echo AppSupportURL=https://github.com/VladimirShi136/KIPiA_Management.git >> "KIPiA_Setup_Final.iss"
echo AppUpdatesURL=https://github.com/VladimirShi136/KIPiA_Management.git >> "KIPiA_Setup_Final.iss"
echo DefaultDirName={autopf}\KIPiA Management System >> "KIPiA_Setup_Final.iss"
echo DefaultGroupName=KIPiA Management System >> "KIPiA_Setup_Final.iss"
echo OutputBaseFilename=KIPiA_Management_Setup >> "KIPiA_Setup_Final.iss"
echo Compression=lzma2 >> "KIPiA_Setup_Final.iss"
echo SolidCompression=yes >> "KIPiA_Setup_Final.iss"
echo OutputDir=InnoOutput >> "KIPiA_Setup_Final.iss"
echo SetupIconFile=installer_resources\iconApp.ico >> "KIPiA_Setup_Final.iss"
echo UninstallDisplayIcon={app}\iconApp.ico >> "KIPiA_Setup_Final.iss"
echo WizardStyle=modern >> "KIPiA_Setup_Final.iss"
echo VersionInfoVersion=1.0.0.0 >> "KIPiA_Setup_Final.iss"
echo VersionInfoCompany=KIPiA >> "KIPiA_Setup_Final.iss"
echo VersionInfoDescription=KIPiA Management System >> "KIPiA_Setup_Final.iss"
echo. >> "KIPiA_Setup_Final.iss"

echo [Languages] >> "KIPiA_Setup_Final.iss"
echo Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl" >> "KIPiA_Setup_Final.iss"
echo. >> "KIPiA_Setup_Final.iss"

echo [Tasks] >> "KIPiA_Setup_Final.iss"
echo Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked >> "KIPiA_Setup_Final.iss"
echo Name: "quicklaunchicon"; Description: "{cm:CreateQuickLaunchIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked; OnlyBelowVersion: 0,6.1 >> "KIPiA_Setup_Final.iss"
echo. >> "KIPiA_Setup_Final.iss"

echo [Files] >> "KIPiA_Setup_Final.iss"
echo Source: "KIPiA_Installer\KIPiA_Management.bat"; DestDir: "{app}"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo Source: "KIPiA_Installer\KIPiA_Management.vbs"; DestDir: "{app}"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo Source: "KIPiA_Installer\app\KIPiA_Management-1.0.0.jar"; DestDir: "{app}\app"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo Source: "KIPiA_Installer\app\javafx\*"; DestDir: "{app}\app\javafx"; Flags: ignoreversion recursesubdirs createallsubdirs >> "KIPiA_Setup_Final.iss"
echo Source: "KIPiA_Installer\app\dependencies\*"; DestDir: "{app}\app\dependencies"; Flags: ignoreversion recursesubdirs createallsubdirs >> "KIPiA_Setup_Final.iss"
echo Source: "installer_resources\README_RU.txt"; DestDir: "{app}"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo Source: "installer_resources\iconApp.ico"; DestDir: "{app}"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo Source: "installer_resources\log4j2.xml"; DestDir: "{app}"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo Source: "installer_resources\ViewLogs.bat"; DestDir: "{app}"; Flags: ignoreversion >> "KIPiA_Setup_Final.iss"
echo. >> "KIPiA_Setup_Final.iss"

echo [Icons] >> "KIPiA_Setup_Final.iss"
echo Name: "{group}\KIPiA Management System"; Filename: "{app}\KIPiA_Management.vbs"; IconFilename: "{app}\iconApp.ico" >> "KIPiA_Setup_Final.iss"
echo Name: "{group}\KIPiA Management System (Debug)"; Filename: "{app}\KIPiA_Management.bat"; IconFilename: "{app}\iconApp.ico" >> "KIPiA_Setup_Final.iss"
echo Name: "{group}\Инструкция"; Filename: "{app}\README_RU.txt" >> "KIPiA_Setup_Final.iss"
echo Name: "{group}\Просмотр логов"; Filename: "{app}\ViewLogs.bat" >> "KIPiA_Setup_Final.iss"
echo Name: "{group}\{cm:UninstallProgram,KIPiA Management System}"; Filename: "{uninstallexe}" >> "KIPiA_Setup_Final.iss"
echo Name: "{autodesktop}\KIPiA Management System"; Filename: "{app}\KIPiA_Management.vbs"; Tasks: desktopicon; IconFilename: "{app}\iconApp.ico" >> "KIPiA_Setup_Final.iss"
echo. >> "KIPiA_Setup_Final.iss"

echo [Run] >> "KIPiA_Setup_Final.iss"
echo Filename: "{app}\KIPiA_Management.vbs"; Description: "{cm:LaunchProgram,KIPiA Management System}"; Flags: nowait postinstall skipifsilent >> "KIPiA_Setup_Final.iss"
echo. >> "KIPiA_Setup_Final.iss"

echo [Code] >> "KIPiA_Setup_Final.iss"
echo function InitializeSetup(): Boolean; >> "KIPiA_Setup_Final.iss"
echo begin >> "KIPiA_Setup_Final.iss"
echo   Result := True; >> "KIPiA_Setup_Final.iss"
echo   if not RegKeyExists(HKEY_LOCAL_MACHINE, 'SOFTWARE\JavaSoft\Java Runtime Environment') then begin >> "KIPiA_Setup_Final.iss"
echo     MsgBox('KIPiA Management System requires Java 23 or higher.' + #13#10 + #13#10 + 'Please install Java from official website:' + #13#10 + 'https://www.oracle.com/java/technologies/downloads/', mbInformation, MB_OK); >> "KIPiA_Setup_Final.iss"
echo   end; >> "KIPiA_Setup_Final.iss"
echo end; >> "KIPiA_Setup_Final.iss"

echo Step 3: Checking Inno Setup installation...
set "INNO_PATH=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist "%INNO_PATH%" (
    echo.
    echo ERROR: Inno Setup not found at default path!
    echo Please install Inno Setup from: https://jrsoftware.org/isdl.php
    echo.
    pause
    exit /b 1
)

echo Step 4: Compiling EXE installer...
if not exist "InnoOutput" mkdir "InnoOutput"
echo Compiling with Inno Setup...
"%INNO_PATH%" "KIPiA_Setup_Final.iss"

if exist "InnoOutput\KIPiA_Management_Setup.exe" (
    echo.
    echo ========================================
    echo    EXE INSTALLER CREATED SUCCESSFULLY!
    echo ========================================
    echo.
    echo Installer: InnoOutput\KIPiA_Management_Setup.exe
    echo.
    echo Features:
    echo - Professional Russian installation wizard
    echo - Your custom icon (iconApp.ico)
    echo - Desktop shortcut with icon (launches without console)
    echo - Start Menu entry with documentation
    echo - Control Panel uninstall entry
    echo - Java requirement check
    echo - File logging to user AppData folder
    echo - Enhanced log viewer utility
    echo - All config files in user space with write permissions
    echo - Database in user AppData folder
    echo - TWO launch options:
    echo "   1. Normal (no console) - KIPiA_Management.vbs"
    echo "   2. Debug (with console) - KIPiA_Management.bat"
    echo.
    echo Ready for distribution!
    exit /b 0
) else (
    echo.
    echo ERROR: Failed to create EXE installer!
    echo Please check the script and try again.
    exit /b 1
)

echo.
pause