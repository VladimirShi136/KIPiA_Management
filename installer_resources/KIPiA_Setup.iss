[Setup]
AppName=KIPiA_Management
AppVersion=1.0.3
AppVerName=KIPiA_Management 1.0.3
DefaultDirName={commonpf}\KIPiA_Management
DefaultGroupName=KIPiA_Management
OutputBaseFilename=KIPiA_Management_1.0.3
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin
FlatComponentsList=false
ShowComponentSizes=yes
AllowNoIcons=yes
LanguageDetectionMethod=locale
UninstallDisplayName=KIPiA_Management 1.0.3 - Удаление
UninstallDisplayIcon={app}\iconApp.ico
DirExistsWarning=no
SetupIconFile=C:\Users\kalba\IdeaProjects\KIPiA_Management\installer_resources\iconApp.ico
UsedUserAreasWarning=no

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
ru.WelcomeLabel1=Вас приветствует мастер установки %n%nНажмите «Далее», чтобы продолжить.
ru.WelcomeLabel2=Это приложение установит KIPiA_Management на ваш компьютер.%n%nПеред продолжением убедитесь, что у вас есть права администратора.
ru.ButtonNext=Далее >
ru.ButtonBack=< Назад
ru.ButtonFinish=Завершить
ru.SetupWindowTitle=Установка KIPiA_Management
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
Name: "{group}\KIPiA_Management"; Filename: "{app}\KIPiA_Management.exe.bat"; IconFilename: "{app}\iconApp.ico"
Name: "{group}\Просмотр логов"; Filename: "{app}\ViewLogs.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"
Name: "{group}\Удалить KIPiA_Management"; Filename: "{uninstallexe}"; IconFilename: "{app}\iconApp.ico"
Name: "{group}\Проверка окружения"; Filename: "{app}\CheckEnvironment.bat"; WorkingDir: "{app}"; IconFilename: "{app}\iconApp.ico"
Name: "{autodesktop}\KIPiA_Management"; Filename: "{app}\KIPiA_Management.exe.bat"; Tasks: desktopicon; IconFilename: "{app}\iconApp.ico"
Name: "{userappdata}\Microsoft\Internet Explorer\Quick Launch\KIPiA_Management"; Filename: "{app}\KIPiA_Management.exe.bat"; Tasks: quicklaunchicon; IconFilename: "{app}\iconApp.ico"

[Run]
Filename: "{app}\KIPiA_Management.exe.bat"; Description: "Запустить KIPiA_Management"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: files; Name: "{commonappdata}\KIPiA_Management\log4j2.xml"
Type: files; Name: "{commonappdata}\KIPiA_Management\ViewLogs.bat"
Type: dirifempty; Name: "{commonappdata}\KIPiA_Management\logs"
Type: dirifempty; Name: "{commonappdata}\KIPiA_Management\data"
Type: dirifempty; Name: "{commonappdata}\KIPiA_Management"
