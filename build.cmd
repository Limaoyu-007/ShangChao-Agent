@echo off
setlocal

if not defined JAVA21_HOME (
    echo JAVA21_HOME is not set. Point it to JDK 21. 1>&2
    exit /b 1
)
if not exist "%JAVA21_HOME%\bin\javac.exe" (
    echo JAVA21_HOME does not contain javac.exe. 1>&2
    exit /b 1
)

set "JAVA_HOME=%JAVA21_HOME%"
cd /d "%~dp0"
call mvn -DskipTests compile
exit /b %ERRORLEVEL%
