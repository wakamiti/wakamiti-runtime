@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%TRG_DIR%"=="" (
  echo TRG_DIR is not set
  exit /b 2
)

pushd "%~1" || (echo Failed to cd into "%~1" & exit /b 1)

set "TESTS=version help"
set max_errorlevel=0

for %%n in (%TESTS%) do (
    <nul set /p ".=--- %%n: "
    %TRG_DIR%\bin\waka.exe %%n > %TRG_DIR%\%%n.log 2>&1
    set error=!errorlevel!
    if !error! == 0 (
        echo SUCCESS
    ) else (
        echo ERROR
        type %TRG_DIR%\%%n.log
    )
    if !error! gtr !max_errorlevel! (
        set max_errorlevel=!error!
    )
)

<nul set /p ".=--- exec: "
%TRG_DIR%\bin\waka.exe run something > %TRG_DIR%\exec.log 2>&1
set error=!errorlevel!
if !error! == 0 (
    findstr /C:"Executing command: run something" %TRG_DIR%\exec.log >nul || set error=1
    findstr /C:"One line" %TRG_DIR%\exec.log >nul || set error=1
    findstr /C:"Another line" %TRG_DIR%\exec.log >nul || set error=1
)
if !error! == 0 (
    echo SUCCESS
) else (
    echo ERROR
    type %TRG_DIR%\exec.log
)
if !error! gtr !max_errorlevel! (
    set max_errorlevel=!error!
)

if !max_errorlevel! == 0 (
        echo Result: SUCCESS
    ) else (
        echo Result: ERROR
        echo - type %TRG_DIR%\wakamitid.log
        type %TRG_DIR%\wakamitid.log
    )
exit /b %max_errorlevel%
