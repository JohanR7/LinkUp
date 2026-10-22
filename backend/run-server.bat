@echo off
rem Starts the LinkUp server on port 7070.
rem Javalin needs Java 11+, so prefer the JDK bundled with Android Studio (the system Java may be 8).
rem Extra arguments are passed through, e.g.:  run-server.bat --seed 12.9716,77.5946
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0"
rem Gradle rejects an empty --args, so only pass it when there are arguments
if "%~1"=="" goto noargs
call "%~dp0gradlew.bat" run --args="%*"
goto :eof
:noargs
call "%~dp0gradlew.bat" run
