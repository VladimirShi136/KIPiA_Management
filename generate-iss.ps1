<#
.SYNOPSIS
Генерирует ISS-файл для Inno Setup на основе шаблонов и параметров.
Поддерживает режим обновления: предупреждает о резервной копии БД и удаляет старую версию.
#>

param(
    [string]$Mode = "normal",
    [string]$Version = "2.0.1",
    [string]$OutputDir = "$PSScriptRoot\Output",
    [string]$InstallerDir = "$PSScriptRoot\KIPiA_Installer"
)

# Создаем папку Output если не существует
if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force
}

# Получаем абсолютный путь к папке с ресурсами
$ResourcesDir = "$PSScriptRoot\installer_resources"

# --- Генерация WizardImage из иконки приложения ---
# Inno Setup ожидает BMP-файл для WizardImageFile (левая панель, ~164x314 px)
# и WizardSmallImageFile (верхний правый угол, ~55x58 px).
# Мы конвертируем iconApp.ico в нужные BMP через PowerShell + System.Drawing.
$iconSrc = "$ResourcesDir\iconApp.ico"
$wizardImageBmp   = "$ResourcesDir\WizardImage.bmp"
$wizardSmallBmp   = "$ResourcesDir\WizardSmallImage.bmp"

function Convert-IconToBmp {
    param([string]$IconPath, [string]$OutPath, [int]$Width, [int]$Height)
    try {
        Add-Type -AssemblyName System.Drawing

        # Читаем .ico напрямую и ищем самый большой вариант иконки
        # (ExtractAssociatedIcon берёт минимальный размер — даёт мутную картинку)
        $bestBmp = $null
        $bestSize = 0
        $iconSizes = @(256, 128, 64, 48, 32)
        foreach ($size in $iconSizes) {
            try {
                $tryIcon = New-Object System.Drawing.Icon($IconPath, $size, $size)
                $tryBmp  = $tryIcon.ToBitmap()
                if ($tryBmp.Width -gt $bestSize) {
                    if ($bestBmp -ne $null) { $bestBmp.Dispose() }
                    $bestBmp  = $tryBmp
                    $bestSize = $tryBmp.Width
                }
                $tryIcon.Dispose()
            } catch { }
        }
        # Если ничего не нашли — fallback
        if ($bestBmp -eq $null) {
            $bestBmp = [System.Drawing.Icon]::ExtractAssociatedIcon($IconPath).ToBitmap()
        }
        Write-Host "  Иконка загружена: $($bestBmp.Width)x$($bestBmp.Height) px"

        $bmp = New-Object System.Drawing.Bitmap($Width, $Height)
        $g   = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

        # Белый фон
        $g.Clear([System.Drawing.Color]::White)

        # Центрируем с отступом, сохраняем пропорции
        $padding = [int]([Math]::Min($Width, $Height) * 0.12)
        $availW  = $Width  - $padding * 2
        $availH  = $Height - $padding * 2
        $ratio   = [Math]::Min($availW / $bestBmp.Width, $availH / $bestBmp.Height)
        $drawW   = [int]($bestBmp.Width  * $ratio)
        $drawH   = [int]($bestBmp.Height * $ratio)
        $x       = [int](($Width  - $drawW) / 2)
        $y       = [int](($Height - $drawH) / 2)
        $g.DrawImage($bestBmp, $x, $y, $drawW, $drawH)

        $bestBmp.Dispose()
        $g.Dispose()
        $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Bmp)
        $bmp.Dispose()
        Write-Host "✅ Сгенерирован: $OutPath (исходник ${bestSize}px -> ${Width}x${Height})"
    } catch {
        Write-Host "⚠️  Не удалось сгенерировать $OutPath : $_"
    }
}

if (Test-Path $iconSrc) {
    Convert-IconToBmp -IconPath $iconSrc -OutPath $wizardImageBmp   -Width 164 -Height 164
    Convert-IconToBmp -IconPath $iconSrc -OutPath $wizardSmallBmp   -Width 55  -Height 58
} else {
    Write-Host "❌ Иконка не найдена: $iconSrc"
    Write-Host "   WizardImage не будет использован (останется дискета по умолчанию)"
    $wizardImageBmp  = ""
    $wizardSmallBmp  = ""
}

# --- Строки для WizardImageFile в ISS ---
$wizardImageLine = if ($wizardImageBmp -and (Test-Path $wizardImageBmp)) {
    "WizardImageFile=$wizardImageBmp"
} else { "; WizardImageFile= (иконка не найдена)" }

$wizardSmallLine = if ($wizardSmallBmp -and (Test-Path $wizardSmallBmp)) {
    "WizardSmallImageFile=$wizardSmallBmp"
} else { "; WizardSmallImageFile= (иконка не найдена)" }

# --- Pascal Script для проверки уже установленной версии и предупреждения о БД ---
$pascalScript = @'
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

'@

# --- Шаблон ISS-файла ---
$issTemplate = @"
[Setup]
AppName=KIPiA_Management
AppVersion=$Version
AppVerName=KIPiA_Management $Version
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}}
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
UninstallDisplayName=KIPiA_Management $Version
UninstallDisplayIcon={app}\iconApp.ico
DirExistsWarning=no
SetupIconFile=$ResourcesDir\iconApp.ico
UsedUserAreasWarning=no

; --- Иконка приложения вместо дискеты ---
$wizardImageLine
$wizardSmallLine
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
ru.SetupWindowTitle=Установка KIPiA_Management $Version
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

$pascalScript
"@

# --- Директива препроцессора для Pascal Script (подставляет версию в строку) ---
$issHeader = "#define AppVersion `"$Version`"`n`n"
$issFullContent = $issHeader + $issTemplate

# Сохранение ISS-файла
$issPath = "$PSScriptRoot\installer_resources\KIPiA_Setup.iss"
Set-Content -Path $issPath -Value $issFullContent -Encoding UTF8
Write-Host "ISS-файл сохранён: $issPath"

# Копируем ISS файл также в Output для проверки
$issOutputPath = "$OutputDir\KIPiA_Setup.iss"
Set-Content -Path $issOutputPath -Value $issFullContent -Encoding UTF8
Write-Host "Копия ISS-файла сохранена: $issOutputPath"

# Проверяем существование иконки
if (Test-Path $iconSrc) {
    Write-Host "✅ Иконка найдена: $iconSrc"
} else {
    Write-Host "❌ Иконка не найдена: $iconSrc"
    Write-Host "Проверьте наличие файла iconApp.ico в папке installer_resources"
}