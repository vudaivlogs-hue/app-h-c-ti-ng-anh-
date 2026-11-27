@echo off
set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
echo Dang khoi dong may ao Android 14 (Enzii_Pixel_API34)...
start "" "%ANDROID_SDK_ROOT%\emulator\emulator.exe" -avd Enzii_Pixel_API34 -gpu auto
