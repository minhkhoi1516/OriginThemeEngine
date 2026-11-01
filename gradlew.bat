@echo off
rem OTE custom bootstrap, not the official Gradle Wrapper binary.
where py >nul 2>nul
if errorlevel 1 goto use_python
py -3 "%~dp0scripts\gradle_bootstrap.py" %*
exit /b %errorlevel%
:use_python
python "%~dp0scripts\gradle_bootstrap.py" %*
exit /b %errorlevel%
