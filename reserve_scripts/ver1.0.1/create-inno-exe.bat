@echo off
chcp 65001
title Create Inno Setup EXE

cd /d "%~dp0"

echo ========================================
echo    CREATING INNO SETUP EXE
echo ========================================
echo.

echo Step 1: Creating ISS file...
(
echo [Setup]
echo AppName=KIPiA Management System
echo AppVersion=1.0.1
echo AppVerName=KIPiA Management System 1.0.1
echo VersionInfoVersion=1.0.1.0
echo VersionInfoTextVersion=1.0.1
echo VersionInfoCompany=KIPiA
echo VersionInfoProductName=KIPiA Management System
echo VersionInfoProductVersion=1.0.1.0
echo VersionInfoProductTextVersion=1.0.1
echo AppPublisher=KIPiA
echo DefaultDirName={commonpf}\KIPiA_Management
echo DefaultGroupName=KIPiA Management
echo OutputDir=Output
echo OutputBaseFilename=KIPiA_Management_Setup_1.0.1
echo SetupIconFile=iconApp.ico
echo Compression=lzma
echo SolidCompression=yes
echo PrivilegesRequired=admin
echo AppId={{KIPiA-Management-System-1.0.1}
echo AppContact=support@kipia.ru
echo AppComments=Система управления KIPiA
echo AppSupportURL=https://kipia.ru
echo AppCopyright=Copyright © 2025 KIPiA
echo UninstallDisplayName=KIPiA Management System 1.0.1
echo UninstallDisplayIcon={app}\iconApp.ico
echo.
echo [Languages]
echo Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
echo.
echo [Messages]
echo WelcomeLabel1=Добро пожаловать в мастер установки KIPiA Management System
echo WelcomeLabel2=Этот мастер проведет вас через процесс установки [name/ver].%n%nРекомендуется закрыть все остальные приложения перед началом установки. Это позволит обновить соответствующие системные файлы без перезагрузки компьютера.%n%nНажмите "Далее" для продолжения или "Отмена" для выхода из мастера установки.
echo.
echo [Files]
echo Source: "..\KIPiA_Installer\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs
echo.
echo [Icons]
echo Name: "{group}\KIPiA Management System"; Filename: "{app}\KIPiA_Management.exe.bat"; IconFilename: "{app}\iconApp.ico"
echo Name: "{autodesktop}\KIPiA Management System"; Filename: "{app}\KIPiA_Management.exe.bat"; IconFilename: "{app}\iconApp.ico"
echo.
echo [Run]
echo Filename: "{app}\KIPiA_Management.exe.bat"; Description: "Запустить KIPiA Management System"; Flags: nowait postinstall skipifsilent
) > "installer_resources\KIPiA_Setup.iss"

echo Step 2: Compiling EXE installer...
set INNO_PATH="C:\Program Files (x86)\Inno Setup 6\ISCC.exe"

if not exist %INNO_PATH% (
    echo ERROR: Inno Setup not found at %INNO_PATH%
    pause
    exit /b 1
)

if not exist "Output" mkdir "Output"

%INNO_PATH% "/OOutput" "installer_resources\KIPiA_Setup.iss"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ EXE INSTALLER CREATED SUCCESSFULLY!
    echo.
    echo File: Output\KIPiA_Management_Setup_1.0.1.exe
) else (
    echo.
    echo ❌ EXE CREATION FAILED!
)

echo.
pause