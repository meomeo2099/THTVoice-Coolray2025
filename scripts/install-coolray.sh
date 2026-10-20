#!/usr/bin/env bash
# Cài THT Voice lên head unit Coolray (IHU624G hoặc IHU689G) qua ADB.
# Dùng: install-coolray.sh root|nonroot|uninstall [đường-dẫn-apk] [thư-mục-.so-bổ-sung]
# Cảnh báo: can thiệp phân vùng hệ thống. Chỉ chạy khi hiểu rủi ro.
set -euo pipefail

MODE="${1:-}"
APK="${2:-app-release.apk}"
LIBDIR="${3:-}"  # tuỳ chọn: thư mục .so bổ sung; mặc định tự tách từ APK
PKG="com.tht.voice.coolray2025"
PRIV_DIR="/system/priv-app/THTVoiceCoolray"
PRIVAPP_XML="$(dirname "$0")/privapp-permissions-com.tht.voice.coolray2025.xml"
STOCK_ASSISTANTS=(com.baidu.iov.dueros.activate com.baidu.iov.sal)

die() { echo "Lỗi: $*" >&2; exit 1; }

check_device() {
  adb get-state >/dev/null 2>&1 || die "không thấy thiết bị ADB"
  local model
  model="$(adb shell 'getprop ro.product.name; getprop ro.product.model; getprop ro.product.device' | tr -d '\r')"
  if ! grep -qiE "IHU624G|IHU689G" <<<"$model"; then
    echo "Cảnh báo: không thấy IHU624G hoặc IHU689G trong ro.product.* ($model)."
    read -r -p "Vẫn tiếp tục? [y/N] " ok
    [[ "$ok" == "y" ]] || exit 1
  fi
}

disable_stock() {
  for p in "${STOCK_ASSISTANTS[@]}"; do
    adb shell pm disable-user "$p" || echo "Bỏ qua $p"
  done
}

case "$MODE" in
  root)
    [[ -f "$APK" ]] || die "không thấy $APK"
    [[ -f "$PRIVAPP_XML" ]] || die "không thấy $PRIVAPP_XML"
    command -v unzip >/dev/null || die "cần lệnh unzip để tách thư viện native từ APK"
    check_device
    adb root; sleep 2
    adb remount | tee /dev/stderr | grep -qi "succeeded" \
      || die "remount thất bại — Coolray có thể cần patch framework; thử chế độ nonroot"
    echo "ro.control_privapp_permissions = $(adb shell getprop ro.control_privapp_permissions | tr -d '\r')"

    # Ứng dụng hệ thống không tự giải nén thư viện native: tách lib/arm64-v8a từ APK
    # và đặt vào $PRIV_DIR/lib/arm64 (thư mục Android tìm cho priv-app).
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    unzip -q -o "$APK" 'lib/arm64-v8a/*.so' -d "$tmp" || die "APK không có lib/arm64-v8a"
    adb shell mkdir -p "$PRIV_DIR/lib/arm64"
    adb push "$APK" "$PRIV_DIR/THTVoice.apk"
    for so in "$tmp"/lib/arm64-v8a/*.so; do adb push "$so" "$PRIV_DIR/lib/arm64/"; done
    if [[ -n "$LIBDIR" && -d "$LIBDIR" ]]; then
      for so in "$LIBDIR"/*.so; do adb push "$so" "$PRIV_DIR/lib/arm64/"; done
    fi
    # Whitelist quyền đặc quyền: thiếu file này khi enforce có thể làm xe không khởi động.
    adb push "$PRIVAPP_XML" "/system/etc/permissions/$(basename "$PRIVAPP_XML")"
    adb shell "chmod 755 $PRIV_DIR $PRIV_DIR/lib $PRIV_DIR/lib/arm64 && chmod 644 $PRIV_DIR/THTVoice.apk $PRIV_DIR/lib/arm64/*.so /system/etc/permissions/$(basename "$PRIVAPP_XML")"
    disable_stock
    adb reboot
    ;;
  nonroot)
    [[ -f "$APK" ]] || die "không thấy $APK"
    check_device
    adb push "$APK" /data/local/tmp/THTVoice.apk
    adb shell pm install -d -r -g --user current /data/local/tmp/THTVoice.apk
    adb shell rm -f /data/local/tmp/THTVoice.apk
    disable_stock
    ;;
  uninstall)
    check_device
    adb shell pm uninstall --user 0 "$PKG" || true
    if adb root >/dev/null 2>&1 && adb remount >/dev/null 2>&1; then
      adb shell rm -rf "$PRIV_DIR" "/system/etc/permissions/$(basename "$PRIVAPP_XML")"
    fi
    for p in "${STOCK_ASSISTANTS[@]}"; do adb shell pm enable "$p" || true; done
    ;;
  *)
    die "dùng: $0 root|nonroot|uninstall [apk] [thư-mục-lib]"
    ;;
esac
echo "Xong: $MODE"
