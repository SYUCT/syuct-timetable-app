# Android 实时倒计时原生验证

仅使用独立模拟器和合成课程。测试初始化脚本为 Debug 添加 `.liveprobe` 包名后缀，并仅在该构建中加载测试 Activity；不写入正式应用数据，不修改系统时间。Release 不含测试 Activity。

```sh
# JDK 17、compile SDK 36；真实上岛需支持实时通知的系统镜像。
./gradlew -I tests/live-probe/probe.init.gradle assembleDebug --offline --no-daemon
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm grant top.syuct.timetable.liveprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.liveprobe/top.syuct.timetable.NativeLiveProbe
# 等待约24秒，包含真实通知超时移除。
adb -s emulator-5554 logcat -d -s NativeLiveProbe:I AndroidRuntime:E
```

覆盖：默认关闭、主动开启、非 ongoing 降级、可提升特征、系统倒计时、结束操作、删除课程清理通知、关闭功能、预览不更改课程发送记录、到时移除。日志中的 `promoted=true` 才表示系统提升，构建特征合法不等于实际提升。应另外人工检查状态栏和用户滑动关闭。

关闭通知权限路径：

```sh
adb -s emulator-5554 shell pm revoke top.syuct.timetable.liveprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.liveprobe/top.syuct.timetable.NativeLiveProbe --ez denied true
adb -s emulator-5554 logcat -d -s NativeLiveProbe:I AndroidRuntime:E
```

结束后卸载 `top.syuct.timetable.liveprobe`。不能把模拟器结果当作小米、OPPO、vivo 等厂商真机验收，也不能证明后台提醒准时。
