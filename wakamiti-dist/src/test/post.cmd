@echo off

if "%TRG_DIR%"=="" (
  echo TRG_DIR is not set
  exit /b 2
)

set "PIDFILE=%TRG_DIR%\wakamitid.pid"

if exist "%PIDFILE%" (
  for /f "usebackq delims=" %%A in ("%PIDFILE%") do (
    echo killing task PID: %%A
    taskkill /F /PID %%A /T >nul
  )
  echo server stopped
)
