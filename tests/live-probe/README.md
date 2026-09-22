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

覆盖：默认关闭、非 ongoing 降级、计时字段、剩余分钟文本、完整详情、实际通知静默更新、结束操作、删除课程清理通知、关闭功能、预览不改课程发送记录、到时移除。额外仅向测试包授权精确定时后，会验证真实非唤醒定时更新：`adb -s emulator-5554 shell appops set top.syuct.timetable.liveprobe SCHEDULE_EXACT_ALARM allow`。未授权时不运行这一特定定时用例，其余测试仍运行。

`--ez visual true` 可显示由同一兼容构造函数创建的合成通知。AOSP 模拟器只用于验证内容与生命周期，不证明小米岛渲染通过。测试已移除不参与生产调用的厂商模板断言，避免出现“废弃代码测试通过、实际功能未覆盖”。

关闭通知权限路径：

权限按钮用例入口为 `top.syuct.timetable.PermissionEntryProbe`。仅对隔离包使用 `appops set top.syuct.timetable.liveprobe SCHEDULE_EXACT_ALARM deny` 后运行，预期11项通过；改为 `allow` 并重启该测试Activity、传入 `--ez allowed true`，预期再11项通过。测试截获自身导航Intent，不会操作真实系统授权页面或替正式应用授权。

```sh
adb -s emulator-5554 shell pm revoke top.syuct.timetable.liveprobe android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am start -n top.syuct.timetable.liveprobe/top.syuct.timetable.NativeLiveProbe --ez denied true
adb -s emulator-5554 logcat -d -s NativeLiveProbe:I AndroidRuntime:E
```

结束后卸载 `top.syuct.timetable.liveprobe`。不能把模拟器结果当作小米、OPPO、vivo 等厂商真机验收，也不能证明后台提醒准时。
