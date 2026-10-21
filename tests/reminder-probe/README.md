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

应输出 `PASS 47 native checks; blocked=false`，覆盖正式通知中的课程、时间、教师和教室及缺教师旧数据；还验证实际预约已取消但凭证仍在时重新登记、小组件服务和实时通知查询/取消报错不影响普通提醒，以及前后台触发记录的区别。点击“测试添加小组件”，在桌面弹出的确认面板点添加，再返回检查“查看添加结果”显示已添加。取消或不确认时不得声称成功。

```sh
adb -s emulator-5554 shell pm revoke top.syuct.timetable.reminderprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.reminderprobe/top.syuct.timetable.NativeReminderProbe --ez blocked true
adb -s emulator-5554 logcat -d -s NativeReminderProbe:I AndroidRuntime:E
```

应输出 `PASS 10 native checks; blocked=true`。

## 真实后台触发回归

仅限上述隔离测试包和专用模拟器。`background` 模式使用匿名课程，自动预约到约 20–80 秒后的课前提醒，先取消实际预约，再保留旧凭证重新恢复；不修改设备时钟。启动后自动退到后台。

```sh
adb -s emulator-5554 shell pm grant top.syuct.timetable.reminderprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell appops set top.syuct.timetable.reminderprobe SCHEDULE_EXACT_ALARM allow
adb -s emulator-5554 shell am start -S -W -n top.syuct.timetable.reminderprobe/top.syuct.timetable.NativeReminderProbe --ez background true
adb -s emulator-5554 shell am kill top.syuct.timetable.reminderprobe
```

可进一步在专用模拟器中断开模拟电源、关屏并强制深度休眠；测试结束必须恢复 `deviceidle unforce` 和 `battery reset`。等待到预约时间后，通过 `run-as` 读取本测试包私有 `course_reminders.xml`，确认 `lastAlarmAt` 与 `lastPostedAt` 都已更新且不早于 `scheduledAt`。这里只用 `am kill` 结束进程；不能在预约后 `force-stop`，后者会按系统规则取消提醒。

此模式不包含在 Release 中，也不代表华为/卓易通宿主已验收。

结束后只卸载测试包：

```sh
adb -s emulator-5554 uninstall top.syuct.timetable.reminderprobe
```

正式 Release 不使用该初始化脚本或测试 Activity。
