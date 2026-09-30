@echo off
rem Windows: double-click to install the Local Push Mock plugin into Android Studio.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-plugin.ps1" %*
pause
