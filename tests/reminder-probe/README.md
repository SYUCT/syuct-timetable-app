# 原生提醒回归（仅独立模拟器）

使用 API 35 模拟器。测试会清空独立测试包自己的课表和偏好，不得改为正式包名或在个人手机运行。时间注入只用于计算，不修改模拟器时钟；不是实际 Doze 调度验收。

初始化脚本仅为独立 Debug 构建加载本目录测试 Activity 与清单；无需复制到正式源码目录。Release 不包含测试入口。

配置 JDK 17 和 compile SDK 36 后（设备 API 35/36）：

```sh
./gradlew -I tests/reminder-probe/probe.init.gradle assembleDebug --offline --no-daemon
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm grant top.syuct.timetable.reminderprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.reminderprobe/top.syuct.timetable.NativeReminderProbe
adb -s emulator-5554 logcat -d -s NativeReminderProbe:I AndroidRuntime:E
```

应输出 `PASS 30 native checks; blocked=false`，覆盖正式通知中的课程、时间、教师和教室及缺教师旧数据。点击“测试添加小组件”，在桌面弹出的确认面板点添加，再返回检查“查看添加结果”显示已添加。取消或不确认时不得声称成功。

```sh
adb -s emulator-5554 shell pm revoke top.syuct.timetable.reminderprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.reminderprobe/top.syuct.timetable.NativeReminderProbe --ez blocked true
adb -s emulator-5554 logcat -d -s NativeReminderProbe:I AndroidRuntime:E
```

应输出 `PASS 8 native checks; blocked=true`。结束后只卸载测试包：

```sh
adb -s emulator-5554 uninstall top.syuct.timetable.reminderprobe
```

正式 Release 不使用该初始化脚本或测试 Activity。
