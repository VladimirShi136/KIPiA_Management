@echo off
chcp 65001
title Create ISS File - Fixed

cd /d "%~dp0"

echo Creating corrected ISS file...

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
echo [Files]
echo Source: "..\KIPiA_Installer\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs
echo.
echo [Icons]
echo Name: "{group}\KIPiA Management System"; Filename: "{app}\KIPiA_Management_Silent.bat"; IconFilename: "{app}\iconApp.ico"
echo Name: "{autodesktop}\KIPiA Management System"; Filename: "{app}\KIPiA_Management_Silent.bat"; IconFilename: "{app}\iconApp.ico"
echo.
echo [Run]
echo Filename: "{app}\KIPiA_Management_Silent.bat"; Description: "Launch KIPiA Management System"; Flags: nowait postinstall skipifsilent runhidden
) > "installer_resources\KIPiA_Setup.iss"

echo ISS file created successfully with runhidden flag!