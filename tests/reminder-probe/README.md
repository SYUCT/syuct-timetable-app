# 原生提醒回归（仅独立模拟器）

使用 API 35 模拟器。测试会清空独立测试包自己的课表和偏好，不得改为正式包名或在个人手机运行。时间注入只用于计算，不修改模拟器时钟；不是实际 Doze 调度验收。

在一次性源码副本内，将本目录 `AndroidManifest.xml` 放到 `app/src/debug/AndroidManifest.xml`，将 `NativeReminderProbe.java` 放到 `app/src/debug/java/top/syuct/timetable/NativeReminderProbe.java`。不要提交这些 debug 副本。

配置 JDK 17 和 SDK 35 后：

```sh
./gradlew -I tests/reminder-probe/probe.init.gradle assembleDebug --offline --no-daemon
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm grant top.syuct.timetable.reminderprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.reminderprobe/top.syuct.timetable.NativeReminderProbe
adb -s emulator-5554 logcat -d -s NativeReminderProbe:I AndroidRuntime:E
```

应输出 `PASS 21 native checks; blocked=false`。点击“测试添加小组件”，在桌面弹出的确认面板点添加，再返回检查“查看添加结果”显示已添加。取消或不确认时不得声称成功。

```sh
adb -s emulator-5554 shell pm revoke top.syuct.timetable.reminderprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.reminderprobe/top.syuct.timetable.NativeReminderProbe --ez blocked true
adb -s emulator-5554 logcat -d -s NativeReminderProbe:I AndroidRuntime:E
```

应输出 `PASS 4 native checks; blocked=true`。结束后只卸载测试包：

```sh
adb -s emulator-5554 uninstall top.syuct.timetable.reminderprobe
```

正式 Release 不使用该初始化脚本或测试 Activity。
