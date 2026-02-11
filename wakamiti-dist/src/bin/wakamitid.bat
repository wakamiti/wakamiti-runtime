@echo off

:: ------------------------------------------------------------------------
:: Ensure WAKAMITI_HOME points to the directory where WAKAMITI is installed.
:: ------------------------------------------------------------------------
SET "WAKAMITI_BIN_DIR=%~dp0"
PUSHD %WAKAMITI_BIN_DIR%
SET "WAKAMITI_BIN_DIR=%CD%"
POPD
FOR /F "delims=" %%i in ("%WAKAMITI_BIN_DIR%\..") DO SET "WAKAMITI_HOME=%%~fi"

:: ------------------------------------------------------------------------
:: Locate a JRE installation directory which will be used to run Wakamiti.
:: Try (in order): %WAKAMITI_JDK%, ..\jri, JDK_HOME, JAVA_HOME.
:: ------------------------------------------------------------------------
SET JRE=

IF NOT "%WAKAMITI_JDK%" == "" (
  IF EXIST "%WAKAMITI_JDK%" SET "JRE=%WAKAMITI_JDK%"
)

IF "%JRE%" == "" (
  IF EXIST "%WAKAMITI_HOME%\jri" SET "JRE=%WAKAMITI_HOME%\jri"
)

IF "%JRE%" == "" (
  IF EXIST "%JDK_HOME%" (
    SET "JRE=%JDK_HOME%"
  ) ELSE IF EXIST "%JAVA_HOME%" (
    SET "JRE=%JAVA_HOME%"
  )
)

SET "JAVA_EXE=%JRE%\bin\java.exe"
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start Wakamiti.
  ECHO No JRE found. Please make sure WAKAMITI_JDK, JDK_HOME, or JAVA_HOME point to a valid JRE installation.
  EXIT /B
)

:: ------------------------------------------------------------------------
:: Collect JVM options.
:: ------------------------------------------------------------------------
IF NOT "%WAKAMITI_PROPERTIES%" == "" SET WAKAMITI_PROPERTIES_PROPERTY="-Dwakamiti.properties.file=%WAKAMITI_PROPERTIES%"

SET WAKAMITI_CACHE_DIR=%LOCALAPPDATA%\Wakamiti

:: <WAKAMITI_HOME>\bin\<exe_name>.vmoptions ...
SET VM_OPTIONS_FILE=
IF EXIST "%WAKAMITI_BIN_DIR%\wakamiti.vmoptions" (
  SET "VM_OPTIONS_FILE=%WAKAMITI_BIN_DIR%\wakamiti.vmoptions"
)

:: ... [+ %WAKAMITI_VM_OPTIONS% || <config_directory>\<exe_name>.vmoptions]
SET USER_VM_OPTIONS_FILE=
IF NOT "%WAKAMITI_VM_OPTIONS%" == "" (
  IF EXIST "%WAKAMITI_VM_OPTIONS%" SET "USER_VM_OPTIONS_FILE=%WAKAMITI_VM_OPTIONS%"
)
IF "%USER_VM_OPTIONS_FILE%" == "" (
  IF EXIST "%APPDATA%\Wakamiti\wakamiti.vmoptions" (
    SET "USER_VM_OPTIONS_FILE=%APPDATA%\Wakamiti\wakamiti.vmoptions"
  )
)

SET ACC=
SET USER_GC=
SET USER_PCT_INI=
SET USER_PCT_MAX=
SET FILTERS=%TMP%\wakamiti-launcher-filters-%RANDOM%.txt
IF NOT "%USER_VM_OPTIONS_FILE%" == "" (
  SET ACC="-Dwakamiti.vmOptionsFile=%USER_VM_OPTIONS_FILE%"
  FINDSTR /R /C:"-XX:\+.*GC" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_GC=yes
  FINDSTR /R /C:"-XX:InitialRAMPercentage=" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_PCT_INI=yes
  FINDSTR /R /C:"-XX:M[ia][nx]RAMPercentage=" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_PCT_MAX=yes
) ELSE IF NOT "%VM_OPTIONS_FILE%" == "" (
  SET ACC="-Dwakamiti.vmOptionsFile=%VM_OPTIONS_FILE%"
)
IF NOT "%VM_OPTIONS_FILE%" == "" (
  IF "%USER_GC%%USER_PCT_INI%%USER_PCT_MAX%" == "" (
    FOR /F "eol=# usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL SET ACC=%%ACC%% "%%i"
  ) ELSE (
    IF NOT "%USER_GC%" == "" ECHO -XX:\+.*GC>> "%FILTERS%"
    IF NOT "%USER_PCT_INI%" == "" ECHO -Xms>> "%FILTERS%"
    IF NOT "%USER_PCT_MAX%" == "" ECHO -Xmx>> "%FILTERS%"
    FOR /F "eol=# usebackq delims=" %%i IN (`FINDSTR /R /V /G:"%FILTERS%" "%VM_OPTIONS_FILE%"`) DO CALL SET ACC=%%ACC%% "%%i"
    DEL "%FILTERS%"
  )
)
IF NOT "%USER_VM_OPTIONS_FILE%" == "" (
  FOR /F "eol=# usebackq delims=" %%i IN ("%USER_VM_OPTIONS_FILE%") DO CALL SET ACC=%%ACC%% "%%i"
)
IF "%VM_OPTIONS_FILE%%USER_VM_OPTIONS_FILE%" == "" (
  ECHO ERROR: cannot find a VM options file
)


:: -----------------------------------
:: Run Wakamiti
:: -----------------------------------
"%JAVA_EXE%" ^
  -classpath "%WAKAMITI_HOME%\lib\*" ^
  "-XX:ErrorFile=%USERPROFILE%\java_error_in_wakamiti_%%p.log" ^
  "-XX:HeapDumpPath=%USERPROFILE%\java_error_in_wakamiti.hprof" ^
  %ACC% ^
  %WAKAMITI_PROPERTIES_PROPERTY% ^
  es.wakamiti.service.WakamitiServiceApplication %*
