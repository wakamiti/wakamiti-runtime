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
    if !errorlevel! == 0 (
        echo SUCCESS
    ) else (
        echo ERROR
        type %TRG_DIR%\%%n.log
    )
    if !errorlevel! gtr !max_errorlevel! (
        set max_errorlevel=!errorlevel!
    )
)

if !max_errorlevel! == 0 (
        echo Result: SUCCESS
    ) else (
        echo Result: ERROR
    )
exit /b %max_errorlevel%
