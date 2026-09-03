#define AppVersion "2.1.1"

[Setup]
AppName=KIPiA_Management
AppVersion=2.1.1
AppVerName=KIPiA_Management 2.1.1
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}}
DefaultDirName={commonpf}\KIPiA_Management
DefaultGroupName=KIPiA_Management
OutputBaseFilename=KIPiA_Management_2.1.1
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin
FlatComponentsList=false
ShowComponentSizes=yes
AllowNoIcons=yes
LanguageDetectionMethod=locale
UninstallDisplayName=KIPiA_Management 2.1.1
UninstallDisplayIcon={app}\iconApp.ico
DirExistsWarning=no
SetupIconFile=C:\Users\kalba\IdeaProjects\KIPiA_Management\installer_resources\iconApp.ico
UsedUserAreasWarning=no

; --- Иконка приложения вместо дискеты ---
WizardImageFile=C:\Users\kalba\IdeaProjects\KIPiA_Management\installer_resources\WizardImage.bmp
WizardSmallImageFile=C:\Users\kalba\IdeaProjects\KIPiA_Management\installer_resources\WizardSmallImage.bmp
WizardImageStretch=no

; ВИЗУАЛЬНЫЕ НАСТРОЙКИ ДЛЯ ОТОБРАЖЕНИЯ WELCOME-ОКНА
WizardStyle=modern
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=no
DisableReadyPage=no
DisableFinishedPage=no

[Languages]
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"

[Messages]
ru.WelcomeLabel1=Вас приветствует мастер установки%n%nKIPiA_Management
ru.WelcomeLabel2=Это приложение установит KIPiA_Management {#AppVersion} на ваш компьютер.%n%nЕсли у вас уже установлена предыдущая версия — она будет автоматически удалена.%n%nПеред продолжением убедитесь, что сделали резервную копию базы данных.
ru.ButtonNext=Далее >
ru.ButtonBack=< Назад
ru.ButtonFinish=Завершить
ru.SetupWindowTitle=Установка KIPiA_Management 2.1.1
ru.FinishedLabel=Программа успешно установлена!
ru.ConfirmUninstall=Вы действительно хотите удалить KIPiA_Management?

[Types]
Name: "full"; Description: "Полная установка"

[Components]
Name: "main"; Description: "Основные файлы"; Types: full; Flags: fixed

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Дополнительные ярлыки:"
Name: "quicklaunchicon"; Description: "Создать ярлык в панели быстрого запуска"; GroupDescription: "Дополнительные ярлыки:"; Flags: unchecked

[Files]
Source: "C:\Users\kalba\IdeaProjects\KIPiA_Management\KIPiA_Installer\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs; Components: main

[Icons]
; Ярлыки на батник-лаунчер
Name: "{group}\KIPiA_Management"; Filename: "{app}\KIPiA_Management.exe.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"
Name: "{group}\Просмотр логов"; Filename: "{app}\ViewLogs.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"
Name: "{group}\Удалить KIPiA_Management"; Filename: "{uninstallexe}"; IconFilename: "{app}\iconApp.ico"
Name: "{group}\Проверка окружения"; Filename: "{app}\CheckEnvironment.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"
Name: "{autodesktop}\KIPiA_Management"; Filename: "{app}\KIPiA_Management.exe.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"; Tasks: desktopicon
Name: "{userappdata}\Microsoft\Internet Explorer\Quick Launch\KIPiA_Management"; Filename: "{app}\KIPiA_Management.exe.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"; Tasks: quicklaunchicon

[Run]
Filename: "{app}\KIPiA_Management.exe.bat"; WorkingDir: "{app}"; Description: "Запустить KIPiA_Management"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: files; Name: "{commonappdata}\KIPiA_Management\log4j2.xml"
Type: files; Name: "{commonappdata}\KIPiA_Management\ViewLogs.bat"
Type: dirifempty; Name: "{commonappdata}\KIPiA_Management\logs"
Type: dirifempty; Name: "{commonappdata}\KIPiA_Management\data"
Type: dirifempty; Name: "{commonappdata}\KIPiA_Management"

[Code]

// Ключ реестра, куда Inno Setup записывает информацию об установке
const
  REG_KEY_32 = 'SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}}_is1';
  REG_KEY_64 = 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}}_is1';

// Возвращает строку установленной версии или '', если не установлено
function GetInstalledVersion: String;
var
  Version: String;
begin
  Result := '';
  if RegQueryStringValue(HKLM, REG_KEY_32, 'DisplayVersion', Version) then
    Result := Version
  else if RegQueryStringValue(HKLM, REG_KEY_64, 'DisplayVersion', Version) then
    Result := Version
  else if RegQueryStringValue(HKCU, REG_KEY_32, 'DisplayVersion', Version) then
    Result := Version
  else if RegQueryStringValue(HKCU, REG_KEY_64, 'DisplayVersion', Version) then
    Result := Version;
end;

// Возвращает путь к папке с данными пользователя (%APPDATA%\KIPiA_Management)
function GetUserDataDir: String;
begin
  Result := ExpandConstant('{userappdata}\KIPiA_Management');
end;

// Инициализация установщика: проверяем наличие старой версии
function InitializeSetup: Boolean;
var
  InstalledVer: String;
  UserDataDir:  String;
  Msg:          String;
  Answer:       Integer;
begin
  Result := True;

  InstalledVer := GetInstalledVersion;
  if InstalledVer <> '' then
  begin
    UserDataDir := GetUserDataDir;

    Msg := 'Обнаружена установленная версия KIPiA_Management: ' + InstalledVer + '.' + #13#10 + #13#10 +
           '⚠️  ВАЖНО: Перед обновлением сделайте резервную копию базы данных!' + #13#10 +
           'Папка с данными: ' + UserDataDir + '\data' + #13#10 + #13#10 +
           'Старая версия будет удалена, новая (' + '{#AppVersion}' + ') установлена на её место.' + #13#10 + #13#10 +
           'Вы сделали резервную копию БД и готовы продолжить обновление?';

    Answer := MsgBox(Msg,
                     mbConfirmation,
                     MB_YESNO or MB_DEFBUTTON2);

    if Answer <> IDYES then
    begin
      MsgBox('Установка отменена. Пожалуйста, сделайте резервную копию папки:' + #13#10 +
             UserDataDir + '\data' + #13#10 +
             'и запустите установщик снова.',
             mbInformation, MB_OK);
      Result := False;
      Exit;
    end;
  end;
end;

// Перед установкой файлов — запускаем деинсталлятор старой версии (тихо)
procedure CurStepChanged(CurStep: TSetupStep);
var
  UninstPath:  String;
  ResultCode:  Integer;
  InstalledVer: String;
begin
  if CurStep = ssInstall then
  begin
    InstalledVer := GetInstalledVersion;
    if InstalledVer <> '' then
    begin
      if RegQueryStringValue(HKLM, REG_KEY_32, 'UninstallString', UninstPath) or
         RegQueryStringValue(HKLM, REG_KEY_64, 'UninstallString', UninstPath) or
         RegQueryStringValue(HKCU, REG_KEY_32, 'UninstallString', UninstPath) or
         RegQueryStringValue(HKCU, REG_KEY_64, 'UninstallString', UninstPath) then
      begin
        // Удаляем кавычки, если есть
        UninstPath := RemoveQuotes(UninstPath);
        WizardForm.StatusLabel.Caption := 'Удаление предыдущей версии...';
        // /VERYSILENT — тихое удаление; /NORESTART — без перезагрузки
        Exec(UninstPath, '/VERYSILENT /NORESTART', '', SW_HIDE,
             ewWaitUntilTerminated, ResultCode);
      end;
    end;
  end;
end;

