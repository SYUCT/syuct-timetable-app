#!/bin/zsh
set -eu
project_dir="${0:A:h}"
tool_dir="${HOME}/.local/share/syuct-android-tools"
sdk_dir="${ANDROID_HOME:-${tool_dir}/sdk}"
adb_bin="${sdk_dir}/platform-tools/adb"
emulator_bin="${sdk_dir}/emulator/emulator"
avd_name="SYUCT_Preview_API35"
apk_path="${project_dir}/artifacts/SYUCT-Timetable-0.2.6-alpha1.apk"
if [[ ! -x "$adb_bin" || ! -x "$emulator_bin" || ! -f "$apk_path" ]]; then
  print '未找到模拟器或 APK。请先按 README 配置工具，并将安装包放入 artifacts。'
  exit 1
fi
export ANDROID_HOME="$sdk_dir"
export ANDROID_SDK_ROOT="$sdk_dir"
"$adb_bin" start-server
device_id=""
for candidate in $("$adb_bin" devices | awk '/^emulator-.*device$/ {print $1}'); do
  current_name=$("$adb_bin" -s "$candidate" emu avd name | head -n 1 | tr -d '\r')
  if [[ "$current_name" == "$avd_name" ]]; then device_id="$candidate"; break; fi
done
if [[ -z "$device_id" ]]; then
  device_id="emulator-5560"
  if "$adb_bin" devices | awk '{print $1}' | /usr/bin/grep -qx "$device_id"; then
    print '预览端口已被其他模拟设备占用，请先关闭该设备后重试。'
    exit 1
  fi
  mkdir -p "$tool_dir/logs"
  nohup "$emulator_bin" -avd "$avd_name" -port 5560 \
    -sysdir "$sdk_dir/system-images/android-35/google_apis/arm64-v8a" \
    -no-boot-anim -gpu auto -memory 2048 -cores 2 \
    > "$tool_dir/logs/preview.log" 2>&1 < /dev/null &!
fi
print '正在打开安卓预览，首次启动可能需要一两分钟。'
ready=0
for attempt in {1..120}; do
  if [[ $("$adb_bin" -s "$device_id" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]]; then ready=1; break; fi
  sleep 2
done
if [[ "$ready" != 1 ]]; then print '启动超时，请查看模拟器窗口或工具目录 logs/preview.log。'; exit 1; fi
"$adb_bin" -s "$device_id" install -r "$apk_path"
"$adb_bin" -s "$device_id" shell am start -n top.syuct.timetable/.MainActivity
print '预览已打开。用鼠标点击、拖动模拟手机；关闭模拟器窗口即可停止运行。'
