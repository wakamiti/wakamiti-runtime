@echo off


if "%TRG_DIR%"=="" (
  echo TRG_DIR is not set
  exit /b 2
)

set "LOG=%TRG_DIR%\wakamitid.log"
set "PIDFILE=%TRG_DIR%\wakamitid.pid"

del "%LOG%" 2>nul
del "%PIDFILE%" 2>nul

powershell -Command "$p = Start-Process -FilePath 'cmd.exe' " ^
    "-ArgumentList '/c ""%TRG_DIR%\bin\wakamitid.bat"" > ""%LOG%"" 2>&1' " ^
    "-WindowStyle Hidden -PassThru; " ^
    "$p.Id | Out-File -Encoding ASCII '%PIDFILE%'"


:WAIT_FILE_CREATION
if not exist "%LOG%" (
    timeout /t 1 /nobreak >nul
    goto WAIT_FILE_CREATION
)

powershell -Command "$timeout = 30; $sw = [System.Diagnostics.Stopwatch]::StartNew(); " ^
    "Get-Content '%LOG%' -Wait -Force | ForEach-Object { " ^
    "  Write-Host $_; " ^
    "  if ($_ -match 'Server started on') { exit 0 }; " ^
    "  if ($sw.Elapsed.TotalSeconds -gt $timeout) { Write-Host 'Timeout reached'; exit 1 } " ^
    "}"

if %errorlevel% neq 0 (
    echo Timeout waiting for server start
    exit /b 1
)
