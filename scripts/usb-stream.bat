@echo off
rem Pasang APK lewat USB, lalu buka jalur RTMP HP -> PC (adb reverse).
rem Pemakaian: scripts\usb-stream.bat [path\ke\app-debug.apk]
setlocal
set APK=%~1
if "%APK%"=="" set APK=app-debug.apk
set PKG=app.kirimlayar.capture
set PORT=1935

where adb >nul 2>nul || (echo adb tidak ditemukan. Pasang Android platform-tools dulu. & exit /b 1)
if not exist "%APK%" (echo APK tidak ditemukan: %APK% & exit /b 1)

echo [1/3] Memasang %APK%
adb install -r -g "%APK%"
if errorlevel 1 (
  echo Pemasangan gagal. Jika pesannya INSTALL_FAILED_UPDATE_INCOMPATIBLE, jalankan: adb uninstall %PKG%
  exit /b 1
)

echo [2/3] Jalur USB: 127.0.0.1:%PORT% di HP ke localhost:%PORT% di PC
adb reverse tcp:%PORT% tcp:%PORT%

echo [3/3] Membuka aplikasi
adb shell am start -n %PKG%/.MainActivity >nul

echo.
echo Selesai. Di aplikasi isi URL: rtmp://127.0.0.1:%PORT%/live/test
echo Pastikan server RTMP sudah berjalan di PC pada port %PORT%.
endlocal
