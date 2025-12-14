<#
.SYNOPSIS
Генерирует ISS-файл для Inno Setup на основе шаблонов и параметров.
#>

param(
    [string]$Version = "1.0.3",
    [string]$OutputDir = "$PSScriptRoot\Output",
    [string]$InstallerDir = "$PSScriptRoot\KIPiA_Installer"
)

# Создаем папку Output если не существует
if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force
}

# Получаем абсолютный путь к папке с ресурсами
$ResourcesDir = "$PSScriptRoot\installer_resources"

# Шаблон ISS-файла
$issTemplate = @"
[Setup]
AppName=KIPiA_Management
AppVersion=$Version
AppVerName=KIPiA_Management $Version
DefaultDirName={commonpf}\KIPiA_Management
DefaultGroupName=KIPiA_Management
OutputBaseFilename=KIPiA_Management_$Version
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin
FlatComponentsList=false
ShowComponentSizes=yes
AllowNoIcons=yes
LanguageDetectionMethod=locale
UninstallDisplayName=KIPiA_Management $Version - Удаление
UninstallDisplayIcon={app}\iconApp.ico
DirExistsWarning=no
SetupIconFile=$ResourcesDir\iconApp.ico
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
Source: "$InstallerDir\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs; Components: main

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
"@

# Сохранение ISS-файла
$issPath = "$PSScriptRoot\installer_resources\KIPiA_Setup.iss"
Set-Content -Path $issPath -Value $issTemplate -Encoding UTF8
Write-Host "ISS-файл сохранён: $issPath"

# Копируем ISS файл также в Output для проверки
$issOutputPath = "$OutputDir\KIPiA_Setup.iss"
Set-Content -Path $issOutputPath -Value $issTemplate -Encoding UTF8
Write-Host "Копия ISS-файла сохранена: $issOutputPath"

# Проверяем существование иконки
$iconPath = "$ResourcesDir\iconApp.ico"
if (Test-Path $iconPath) {
    Write-Host "✅ Иконка найдена: $iconPath"
} else {
    Write-Host "❌ Иконка не найдена: $iconPath"
    Write-Host "Проверьте наличие файла iconApp.ico в папке installer_resources"
}