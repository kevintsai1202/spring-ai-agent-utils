@echo off
setlocal

REM Set Java 21 path
set "JAVA_HOME=D:\java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ===================================================
echo Using Java from: %JAVA_HOME%
java -version
echo ===================================================

REM Check for OpenAI API Key
if "%OPENAI_API_KEY%"=="" (
    echo [WARNING] OPENAI_API_KEY environment variable is not set.
    set /p OPENAI_API_KEY="Please enter your OpenAI API Key: "
)

REM Run the application
echo Starting Skills Demo Application...
call mvn spring-boot:run

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Application failed to start.
)

pause
