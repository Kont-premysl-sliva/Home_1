@echo off
chcp 65001 >nul
title Kontejnery Ostrava - Nastaveni ikony na Plose
cls
echo ======================================================================
echo    KONTEJNERY OSTRAVA - Vytvoreni zastupce na Plose s modrou ikonou K
echo ======================================================================
echo.

set "APPDIR=%LOCALAPPDATA%\KontejneryOstrava"
if not exist "%APPDIR%" mkdir "%APPDIR%"
set "ICOPATH=%APPDIR%\Kontejnery_Ostrava.ico"

echo [1/3] Pripravuji soubor ikony K...
if exist "%~dp0Kontejnery_Ostrava.ico" (
    copy /y "%~dp0Kontejnery_Ostrava.ico" "%ICOPATH%" >nul
) else if exist "%USERPROFILE%\Downloads\Kontejnery_Ostrava.ico" (
    copy /y "%USERPROFILE%\Downloads\Kontejnery_Ostrava.ico" "%ICOPATH%" >nul
) else if exist "%USERPROFILE%\Desktop\Kontejnery_Ostrava.ico" (
    copy /y "%USERPROFILE%\Desktop\Kontejnery_Ostrava.ico" "%ICOPATH%" >nul
) else (
    echo Stahuji ikonu z GitHubu...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('https://raw.githubusercontent.com/Kont-premysl-sliva/Kont_2026/main/Kontejnery_Ostrava.ico', $env:ICOPATH)"
)

echo [2/3] Vytvarim zastupce na Plose Windows s modrou ikonou K...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'SilentlyContinue'; $desktop = [Environment]::GetFolderPath('Desktop'); $ico = $env:LOCALAPPDATA + '\KontejneryOstrava\Kontejnery_Ostrava.ico'; $target = ''; $searchDirs = @($desktop, (Join-Path $env:USERPROFILE 'Downloads'), '%~dp0'); foreach ($dir in $searchDirs) { if ($dir -and (Test-Path $dir)) { $c = Get-ChildItem -Path $dir -Filter '*.html' | Where-Object { $_.Name -like '*POHODA*' -or $_.Name -like '*Kontejnery*' -or $_.Name -like 'index*.html' } | Sort-Object LastWriteTime -Descending; if ($c) { $target = $c[0].FullName; break; } } }; if (!$target) { $target = Join-Path $desktop 'index.html'; }; $browsers = @((Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'), (Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe'), (Join-Path $env:LOCALAPPDATA 'Google\Chrome\Application\chrome.exe'), (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'), (Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe')); $bExe = ''; foreach ($b in $browsers) { if ($b -and (Test-Path $b)) { $bExe = $b; break; } }; $ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut((Join-Path $desktop 'Kontejnery Ostrava.lnk')); if ($bExe) { $s.TargetPath = $bExe; $s.Arguments = '--app=\"' + $target + '\"'; } else { $s.TargetPath = $target; }; $s.WorkingDirectory = $desktop; if (Test-Path $ico) { $s.IconLocation = $ico + ',0'; }; $s.Description = 'Kontejnery Ostrava - PC Terminal ERP'; $s.Save(); Write-Host '[3/3] Zastupce byl uspesne vytvoren!' -ForegroundColor Green; [System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; [System.Windows.Forms.MessageBox]::Show('Zástupce \"Kontejnery Ostrava\" s modrou ikonou K byl úspěšně vytvořen na vaší Ploše!' + [Environment]::NewLine + [Environment]::NewLine + 'Cíl: ' + $target, 'Kontejnery Ostrava - Hotovo', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null;"

echo.
echo ======================================================================
echo    Hotovo! Zástupce s modrou ikonou K byl vytvořen na Ploše.
echo ======================================================================
timeout /t 5 >nul
