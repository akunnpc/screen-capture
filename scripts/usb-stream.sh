#!/usr/bin/env bash
# Pasang APK lewat USB, lalu buka jalur RTMP HP -> PC lewat kabel (adb reverse).
# Pemakaian: ./scripts/usb-stream.sh [path/ke/app-debug.apk]
set -euo pipefail

APK="${1:-app-debug.apk}"
PKG="app.kirimlayar.capture"
PORT="${PORT:-1935}"

command -v adb >/dev/null 2>&1 || { echo "adb tidak ditemukan. Pasang Android platform-tools dulu."; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "Tidak ada perangkat. Aktifkan Debug USB dan setujui dialog di HP."; exit 1; }
[ -f "$APK" ] || { echo "APK tidak ditemukan: $APK"; exit 1; }

echo "[1/3] Memasang $APK (izin runtime ikut diberikan)"
if ! adb install -r -g "$APK"; then
  echo
  echo "Pemasangan gagal. Jika pesannya INSTALL_FAILED_UPDATE_INCOMPATIBLE,"
  echo "jalankan: adb uninstall $PKG   lalu ulangi."
  exit 1
fi

echo "[2/3] Jalur USB: 127.0.0.1:$PORT di HP -> localhost:$PORT di PC"
adb reverse "tcp:$PORT" "tcp:$PORT"

echo "[3/3] Membuka aplikasi"
adb shell am start -n "$PKG/.MainActivity" >/dev/null

echo
echo "Selesai. Di aplikasi isi URL: rtmp://127.0.0.1:$PORT/live/test"
echo "Pastikan server RTMP sudah berjalan di PC pada port $PORT."
echo "Lihat hasil dengan: ffplay rtmp://localhost:$PORT/live/test"
